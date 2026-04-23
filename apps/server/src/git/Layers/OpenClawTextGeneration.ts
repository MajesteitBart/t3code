import { randomUUID } from "node:crypto";

import { Effect, Layer, Schema } from "effect";

import { TextGenerationError, type ChatAttachment } from "@t3tools/contracts";
import { sanitizeFeatureBranchName } from "@t3tools/shared/git";

import { resolveAttachmentPath } from "../../attachmentStore.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import {
  buildBranchNamePrompt,
  buildCommitMessagePrompt,
  buildPrContentPrompt,
  buildThreadTitlePrompt,
} from "../Prompts.ts";
import { TextGeneration, type TextGenerationShape } from "../Services/TextGeneration.ts";
import {
  extractJsonObject,
  sanitizeCommitSubject,
  sanitizePrTitle,
  sanitizeThreadTitle,
} from "../Utils.ts";
import {
  connectOpenClawGateway,
  getOpenClawGatewayErrorMessage,
  type OpenClawGatewayNotification,
} from "../../provider/openclawGateway.ts";

const OPENCLAW_TEXT_GENERATION_TIMEOUT_MS = 60_000;

const CommitMessageResponse = Schema.Struct({
  subject: Schema.String,
  body: Schema.String,
  branch: Schema.optional(Schema.String),
});

const PrContentResponse = Schema.Struct({
  title: Schema.String,
  body: Schema.String,
});

const BranchNameResponse = Schema.Struct({
  branch: Schema.String,
});

const ThreadTitleResponse = Schema.Struct({
  title: Schema.String,
});

function trimOrNull(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

async function resolveAttachmentPayload(
  attachments: ReadonlyArray<ChatAttachment> | undefined,
): Promise<ReadonlyArray<Record<string, unknown>>> {
  if (!attachments || attachments.length === 0) {
    return [];
  }

  const entries = await Promise.all(
    attachments.map(async (attachment) => {
      const path = await Effect.runPromise(
        resolveAttachmentPath(attachment.id).pipe(Effect.orElseSucceed(() => undefined)),
      );
      return {
        id: attachment.id,
        name: attachment.name,
        mimeType: attachment.mimeType,
        sizeBytes: attachment.sizeBytes,
        ...(path ? { path } : {}),
      };
    }),
  );
  return entries;
}

async function collectAssistantText(input: {
  readonly notifications: AsyncIterable<OpenClawGatewayNotification>;
  readonly fallbackResult: unknown;
}): Promise<string> {
  const directText =
    trimOrNull(asRecord(input.fallbackResult)?.text) ??
    trimOrNull(asRecord(input.fallbackResult)?.output) ??
    trimOrNull(asRecord(asRecord(input.fallbackResult)?.result)?.text);
  if (directText) {
    return directText;
  }

  let assistantText = "";

  await Promise.race([
    (async () => {
      for await (const notification of input.notifications) {
        const payload = asRecord(notification.params) ?? {};
        if (notification.method === "content.delta") {
          assistantText += typeof payload.delta === "string" ? payload.delta : "";
          continue;
        }
        if (notification.method === "runtime.error") {
          throw new Error(trimOrNull(payload.message) ?? "OpenClaw text generation failed.");
        }
        if (
          notification.method === "turn.completed" ||
          notification.method === "turn.aborted" ||
          notification.method === "session.exited"
        ) {
          break;
        }
      }
    })(),
    new Promise<void>((_, reject) =>
      setTimeout(() => reject(new Error("OpenClaw text generation timed out.")), OPENCLAW_TEXT_GENERATION_TIMEOUT_MS),
    ),
  ]);

  return assistantText.trim();
}

const makeOpenClawTextGeneration = Effect.gen(function* () {
  const serverSettings = yield* ServerSettingsService;

  const runPrompt = Effect.fn("runPrompt")(function* (input: {
    readonly operation:
      | "generateCommitMessage"
      | "generatePrContent"
      | "generateBranchName"
      | "generateThreadTitle";
    readonly prompt: string;
    readonly model: string;
    readonly attachments?: ReadonlyArray<ChatAttachment>;
  }) {
    const settings = yield* serverSettings.getSettings;
    const providerSettings = settings.providers.openclaw;
    const attachments = yield* Effect.promise(() => resolveAttachmentPayload(input.attachments));

    return yield* Effect.acquireUseRelease(
      Effect.promise(() =>
        connectOpenClawGateway({
          url: providerSettings.gatewayUrl,
          token: providerSettings.gatewayToken,
          password: providerSettings.gatewayPassword,
        }),
      ),
      (connection) =>
        Effect.tryPromise({
          try: async () => {
            const sessionResult = await connection.call("session.create", {
              sessionLabel: `t3-git-${input.operation}-${randomUUID()}`,
              runtimeMode: "full-access",
              modelSelection: {
                model: input.model,
              },
            });
            const sessionId =
              trimOrNull(asRecord(sessionResult)?.sessionId) ??
              trimOrNull(asRecord(sessionResult)?.id) ??
              randomUUID();
            const sendResult = await connection.call("session.sendTurn", {
              sessionId,
              input: input.prompt,
              ...(attachments.length > 0 ? { attachments } : {}),
            });
            const text = await collectAssistantText({
              notifications: connection.notifications,
              fallbackResult: sendResult,
            });
            await connection.call("session.stop", { sessionId }).catch(() => undefined);
            return text;
          },
          catch: (error) =>
            new TextGenerationError({
              operation: input.operation,
              detail: getOpenClawGatewayErrorMessage(error, "OpenClaw text generation failed."),
              cause: error,
            }),
        }),
      (connection) => Effect.promise(() => connection.close()).pipe(Effect.ignore),
    );
  });

  const decodeJson = <T>(schema: Schema.Schema<T>, raw: string, operation: string): T => {
    try {
      return Schema.decodeUnknownSync(schema)(JSON.parse(extractJsonObject(raw)));
    } catch (error) {
      throw new TextGenerationError({
        operation,
        detail: "OpenClaw returned invalid JSON.",
        cause: error,
      });
    }
  };

  return {
    generateCommitMessage: (input) =>
      runPrompt({
        operation: "generateCommitMessage",
        prompt: buildCommitMessagePrompt(input),
        model: input.modelSelection.model,
      }).pipe(
        Effect.map((raw) => decodeJson(CommitMessageResponse, raw, "generateCommitMessage")),
        Effect.map((result) => ({
          subject: sanitizeCommitSubject(result.subject),
          body: result.body.trim(),
          ...(trimOrNull(result.branch) ? { branch: result.branch.trim() } : {}),
        })),
      ),
    generatePrContent: (input) =>
      runPrompt({
        operation: "generatePrContent",
        prompt: buildPrContentPrompt(input),
        model: input.modelSelection.model,
      }).pipe(
        Effect.map((raw) => decodeJson(PrContentResponse, raw, "generatePrContent")),
        Effect.map((result) => ({
          title: sanitizePrTitle(result.title),
          body: result.body.trim(),
        })),
      ),
    generateBranchName: (input) =>
      runPrompt({
        operation: "generateBranchName",
        prompt: buildBranchNamePrompt(input),
        model: input.modelSelection.model,
        attachments: input.attachments,
      }).pipe(
        Effect.map((raw) => decodeJson(BranchNameResponse, raw, "generateBranchName")),
        Effect.map((result) => ({
          branch: sanitizeFeatureBranchName(result.branch),
        })),
      ),
    generateThreadTitle: (input) =>
      runPrompt({
        operation: "generateThreadTitle",
        prompt: buildThreadTitlePrompt(input),
        model: input.modelSelection.model,
        attachments: input.attachments,
      }).pipe(
        Effect.map((raw) => decodeJson(ThreadTitleResponse, raw, "generateThreadTitle")),
        Effect.map((result) => ({
          title: sanitizeThreadTitle(result.title),
        })),
      ),
  } satisfies TextGenerationShape;
});

export const OpenClawTextGenerationLive = Layer.effect(
  TextGeneration,
  makeOpenClawTextGeneration,
);
