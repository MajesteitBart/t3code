import { randomUUID } from "node:crypto";

import { Effect, Layer, Schema } from "effect";

import { TextGenerationError, type ChatAttachment } from "@t3tools/contracts";
import { sanitizeFeatureBranchName } from "@t3tools/shared/git";

import { resolveAttachmentPath } from "../../attachmentStore.ts";
import { ServerConfig } from "../../config.ts";
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
import { getOpenClawMessageRole, getOpenClawMessageText } from "../../provider/openclawMessages.ts";

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
        if (notification.method === "session.message") {
          const message = asRecord(payload.message) ?? payload;
          if (getOpenClawMessageRole(message) === "assistant") {
            assistantText += getOpenClawMessageText(message);
            break;
          }
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
      setTimeout(
        () => reject(new Error("OpenClaw text generation timed out.")),
        OPENCLAW_TEXT_GENERATION_TIMEOUT_MS,
      ),
    ),
  ]);

  return assistantText.trim();
}

const makeOpenClawTextGeneration = Effect.gen(function* () {
  const serverConfig = yield* ServerConfig;
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
    const settings = yield* serverSettings.getSettings.pipe(
      Effect.map((value) => value.providers.openclaw),
      Effect.orElseSucceed(() => ({
        enabled: true,
        gatewayUrl: "",
        gatewayToken: "",
        gatewayPassword: "",
        customModels: [],
      })),
    );
    const attachments = Array.isArray(input.attachments)
      ? input.attachments.map((attachment) => ({
          id: attachment.id,
          name: attachment.name,
          mimeType: attachment.mimeType,
          sizeBytes: attachment.sizeBytes,
          ...(() => {
            const path = resolveAttachmentPath({
              attachmentsDir: serverConfig.attachmentsDir,
              attachment,
            });
            return path ? { path } : {};
          })(),
        }))
      : [];

    return yield* Effect.acquireUseRelease(
      Effect.promise(() =>
        connectOpenClawGateway({
          url: settings.gatewayUrl,
          token: settings.gatewayToken,
          password: settings.gatewayPassword,
        }),
      ),
      (connection) =>
        Effect.tryPromise({
          try: async () => {
            const sessionResult = await connection.call("sessions.create", {
              label: `t3-git-${input.operation}-${randomUUID()}`,
              model: input.model,
            });
            const sessionKey =
              trimOrNull(asRecord(sessionResult)?.key) ??
              trimOrNull(asRecord(sessionResult)?.sessionKey) ??
              randomUUID();
            await connection.call("sessions.messages.subscribe", {
              key: sessionKey,
            });
            const sendResult = await connection.call("sessions.send", {
              key: sessionKey,
              message: input.prompt,
              ...(attachments.length > 0 ? { attachments } : {}),
            });
            const text = await collectAssistantText({
              notifications: connection.notifications,
              fallbackResult: sendResult,
            });
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
      return Schema.decodeUnknownSync(schema as never)(JSON.parse(extractJsonObject(raw))) as T;
    } catch (error) {
      throw new TextGenerationError({
        operation,
        detail: "OpenClaw returned invalid JSON.",
        cause: error,
      });
    }
  };

  const generateCommitMessage: TextGenerationShape["generateCommitMessage"] = Effect.fn(
    "OpenClawTextGeneration.generateCommitMessage",
  )(function* (input) {
    const { prompt } = buildCommitMessagePrompt({
      branch: input.branch,
      stagedSummary: input.stagedSummary,
      stagedPatch: input.stagedPatch,
      includeBranch: input.includeBranch === true,
    });

    const raw = yield* runPrompt({
      operation: "generateCommitMessage",
      prompt,
      model: input.modelSelection.model,
    });
    const result = decodeJson(CommitMessageResponse, raw, "generateCommitMessage");
    const branch = trimOrNull(result.branch);

    return {
      subject: sanitizeCommitSubject(result.subject),
      body: result.body.trim(),
      ...(branch ? { branch: branch.trim() } : {}),
    };
  });

  const generatePrContent: TextGenerationShape["generatePrContent"] = Effect.fn(
    "OpenClawTextGeneration.generatePrContent",
  )(function* (input) {
    const { prompt } = buildPrContentPrompt({
      baseBranch: input.baseBranch,
      headBranch: input.headBranch,
      commitSummary: input.commitSummary,
      diffSummary: input.diffSummary,
      diffPatch: input.diffPatch,
    });

    const raw = yield* runPrompt({
      operation: "generatePrContent",
      prompt,
      model: input.modelSelection.model,
    });
    const result = decodeJson(PrContentResponse, raw, "generatePrContent");

    return {
      title: sanitizePrTitle(result.title),
      body: result.body.trim(),
    };
  });

  const generateBranchName: TextGenerationShape["generateBranchName"] = Effect.fn(
    "OpenClawTextGeneration.generateBranchName",
  )(function* (input) {
    const { prompt } = buildBranchNamePrompt({
      message: input.message,
      attachments: input.attachments,
    });

    const raw = yield* runPrompt({
      operation: "generateBranchName",
      prompt,
      model: input.modelSelection.model,
      ...(input.attachments ? { attachments: input.attachments } : {}),
    });
    const result = decodeJson(BranchNameResponse, raw, "generateBranchName");

    return {
      branch: sanitizeFeatureBranchName(result.branch),
    };
  });

  const generateThreadTitle: TextGenerationShape["generateThreadTitle"] = Effect.fn(
    "OpenClawTextGeneration.generateThreadTitle",
  )(function* (input) {
    const { prompt } = buildThreadTitlePrompt({
      message: input.message,
      attachments: input.attachments,
    });

    const raw = yield* runPrompt({
      operation: "generateThreadTitle",
      prompt,
      model: input.modelSelection.model,
      ...(input.attachments ? { attachments: input.attachments } : {}),
    });
    const result = decodeJson(ThreadTitleResponse, raw, "generateThreadTitle");

    return {
      title: sanitizeThreadTitle(result.title),
    };
  });

  return {
    generateCommitMessage,
    generatePrContent,
    generateBranchName,
    generateThreadTitle,
  } satisfies TextGenerationShape;
});

export const OpenClawTextGenerationLive = Layer.effect(TextGeneration, makeOpenClawTextGeneration);
