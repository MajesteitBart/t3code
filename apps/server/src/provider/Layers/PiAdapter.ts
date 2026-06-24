import {
  ApprovalRequestId,
  EventId,
  type PiSettings,
  ProviderDriverKind,
  ProviderInstanceId,
  type ProviderUserInputAnswers,
  type ProviderRuntimeEvent,
  type ProviderSession,
  RuntimeItemId,
  RuntimeRequestId,
  type ThreadId,
  type ToolLifecycleItemType,
  TurnId,
  type UserInputQuestion,
} from "@t3tools/contracts";
import { getModelSelectionStringOptionValue } from "@t3tools/shared/model";
import * as Cause from "effect/Cause";
import * as Crypto from "effect/Crypto";
import * as DateTime from "effect/DateTime";
import * as Effect from "effect/Effect";
import * as Exit from "effect/Exit";
import * as FileSystem from "effect/FileSystem";
import * as Path from "effect/Path";
import * as Queue from "effect/Queue";
import * as Ref from "effect/Ref";
import * as Schema from "effect/Schema";
import * as Scope from "effect/Scope";
import * as Stream from "effect/Stream";

import { ServerConfig } from "../../config.ts";
import * as McpProviderSession from "../../mcp/McpProviderSession.ts";
import {
  ProviderAdapterProcessError,
  ProviderAdapterRequestError,
  ProviderAdapterSessionClosedError,
  ProviderAdapterSessionNotFoundError,
  ProviderAdapterValidationError,
} from "../Errors.ts";
import {
  buildPiRpcArgs,
  parsePiModelSlug,
  PiRuntime,
  PiRuntimeError,
  piRuntimeErrorDetail,
  type PiRpcCommand,
  type PiRpcEvent,
  type PiRpcResponse,
  type PiThinkingLevel,
} from "../piRpcRuntime.ts";
import type { PiAdapterShape } from "../Services/PiAdapter.ts";
import type { EventNdjsonLogger } from "./EventNdjsonLogger.ts";

const PROVIDER = ProviderDriverKind.make("pi");
const PI_TURN_ID_PREFIX = "pi-turn";
const PI_RPC_RAW_SOURCE = "pi.rpc.event" as const;
const PI_MCP_BRIDGE_TOKEN_ENV = "T3_MCP_BEARER_TOKEN";
const encodeUnknownJsonString = Schema.encodeUnknownSync(Schema.UnknownFromJsonString);
const PI_T3_BROWSER_SYSTEM_PROMPT = `
## T3 Code collaborative browser

T3 Code exposes its in-app collaborative browser through the configured MCP server named "t3-code".

For browser work, first try direct preview tools such as preview_status, preview_open, preview_navigate, and preview_snapshot if they are available.

If direct preview tools are not available, use the Pi MCP proxy tool:
- mcp({ search: "preview" }) to discover the T3 preview tools.
- mcp({ tool: "preview_status", args: "{}" }) before deciding browser automation is unavailable.
- mcp({ tool: "preview_open", args: "{}" }) if no automation-capable preview is attached.
- Use the discovered preview_navigate, preview_snapshot, and focused interaction tools through mcp({ tool, args }) for browser navigation, inspection, interaction, screenshots, and recordings.

Do not switch to Pi agent-browser, global Chrome automation, standalone Playwright, or another external browser merely because a preview MCP call fails. Inspect actionable preview MCP errors and retry with corrected arguments where appropriate.
`.trim();
const PI_THINKING_LEVELS = new Set<PiThinkingLevel>([
  "off",
  "minimal",
  "low",
  "medium",
  "high",
  "xhigh",
]);

interface PiTurnSnapshot {
  readonly id: TurnId;
  readonly items: Array<unknown>;
}

interface PiSessionContext {
  session: ProviderSession;
  readonly rpc: import("../piRpcRuntime.ts").PiRpcSession;
  readonly sessionScope: Scope.Closeable;
  readonly turns: Array<PiTurnSnapshot>;
  readonly completedTurnIds: Set<TurnId>;
  readonly toolInputById: Map<string, Record<string, unknown>>;
  readonly pendingExtensionUserInputs: Map<ApprovalRequestId, PendingPiExtensionUserInput>;
  activeTurnId: TurnId | undefined;
  readonly stopped: Ref.Ref<boolean>;
  readonly cleanup: Effect.Effect<void, never>;
}

type PiExtensionUserInputMethod = "select" | "confirm" | "input" | "editor";

interface PendingPiExtensionUserInput {
  readonly extensionRequestId: string;
  readonly method: PiExtensionUserInputMethod;
  readonly questionId: string;
  readonly question: string;
}

interface PiAdapterLiveOptions {
  readonly instanceId?: ProviderInstanceId;
  readonly environment?: NodeJS.ProcessEnv;
  readonly nativeEventLogger?: EventNdjsonLogger;
}

type EventBaseInput = {
  readonly threadId: ThreadId;
  readonly turnId?: TurnId | undefined;
  readonly itemId?: string | undefined;
  readonly requestId?: string | undefined;
  readonly createdAt?: string | undefined;
  readonly raw?: unknown;
};

type PiMcpBridge =
  | { readonly _tag: "Absent" }
  | {
      readonly _tag: "Ready";
      readonly configPath: string;
      readonly providerSessionId: string;
      readonly environment: NodeJS.ProcessEnv;
      readonly cleanup: Effect.Effect<void, never>;
    }
  | {
      readonly _tag: "Failed";
      readonly providerSessionId: string;
      readonly detail: string;
    };

const nowIso = Effect.map(DateTime.now, DateTime.formatIso);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringField(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

function numberField(record: Record<string, unknown>, key: string): number | undefined {
  const value = record[key];
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function isPiThinkingLevel(value: string | undefined): value is PiThinkingLevel {
  return value !== undefined && PI_THINKING_LEVELS.has(value as PiThinkingLevel);
}

function toToolLifecycleItemType(toolName: string): ToolLifecycleItemType {
  const normalized = toolName.toLowerCase();
  if (normalized.includes("bash") || normalized.includes("command")) {
    return "command_execution";
  }
  if (normalized.includes("edit") || normalized.includes("write") || normalized.includes("patch")) {
    return "file_change";
  }
  if (normalized.includes("web")) {
    return "web_search";
  }
  if (normalized.includes("mcp")) {
    return "mcp_tool_call";
  }
  if (normalized.includes("image")) {
    return "image_view";
  }
  if (normalized.includes("task") || normalized.includes("agent")) {
    return "collab_agent_tool_call";
  }
  return "dynamic_tool_call";
}

function resolveToolName(event: Record<string, unknown>): string {
  return stringField(event, "toolName") ?? "Pi tool";
}

function resolveToolCallId(event: Record<string, unknown>): string | undefined {
  return stringField(event, "toolCallId") ?? stringField(event, "id");
}

function formatPiToolArgs(args: ReadonlyArray<unknown>): string | undefined {
  const parts = args
    .map((part) => (typeof part === "string" ? part.trim() : ""))
    .filter((part) => part.length > 0);
  return parts.length > 0 ? parts.join(" ") : undefined;
}

function commandFromPiToolPayload(
  input: Record<string, unknown> | undefined,
  output: Record<string, unknown> | undefined,
): string | undefined {
  const direct =
    (input &&
      (stringField(input, "command") ??
        stringField(input, "cmd") ??
        stringField(input, "script"))) ??
    (output && (stringField(output, "command") ?? stringField(output, "cmd")));
  if (direct) {
    return direct;
  }

  if (input && Array.isArray(input.args)) {
    return formatPiToolArgs(input.args);
  }

  const details = output && isRecord(output.details) ? output.details : undefined;
  if (details && Array.isArray(details.args)) {
    return formatPiToolArgs(details.args);
  }

  return undefined;
}

function buildPiToolData(
  event: Record<string, unknown>,
  toolName: string,
  toolCallId: string | undefined,
  previousInput: Record<string, unknown> | undefined,
): Record<string, unknown> {
  const input = isRecord(event.args) ? event.args : previousInput;
  const output =
    event.type === "tool_execution_update"
      ? isRecord(event.partialResult)
        ? event.partialResult
        : undefined
      : event.type === "tool_execution_end"
        ? isRecord(event.result)
          ? event.result
          : undefined
        : undefined;
  const command = commandFromPiToolPayload(input, output);

  return {
    ...(toolCallId ? { toolCallId } : {}),
    kind: toolName.toLowerCase(),
    ...(input ? { rawInput: input } : {}),
    ...(output ? { rawOutput: output } : {}),
    ...(command ? { command } : {}),
  };
}

function isBenignExtensionUiRequest(event: PiRpcEvent): boolean {
  if (!isRecord(event)) {
    return false;
  }
  const method = stringField(event, "method");
  return (
    method === "notify" ||
    method === "setStatus" ||
    method === "setWidget" ||
    method === "setTitle" ||
    method === "set_editor_text"
  );
}

function piExtensionUserInputMethod(event: PiRpcEvent): PiExtensionUserInputMethod | undefined {
  if (!isRecord(event)) {
    return undefined;
  }
  const method = stringField(event, "method");
  return method === "select" || method === "confirm" || method === "input" || method === "editor"
    ? method
    : undefined;
}

function piExtensionRequestTitle(event: Record<string, unknown>): string | undefined {
  return stringField(event, "title") ?? stringField(event, "message");
}

function normalizePiExtensionOptions(
  value: unknown,
): ReadonlyArray<UserInputQuestion["options"][number]> {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.flatMap((entry) => {
    const label = typeof entry === "string" ? entry.trim() : "";
    return label.length > 0 ? [{ label, description: label }] : [];
  });
}

function buildPiExtensionUserInputQuestion(
  event: PiRpcEvent,
):
  | { readonly pending: PendingPiExtensionUserInput; readonly question: UserInputQuestion }
  | undefined {
  if (!isRecord(event)) {
    return undefined;
  }
  const method = piExtensionUserInputMethod(event);
  const extensionRequestId = stringField(event, "id");
  const title = piExtensionRequestTitle(event);
  if (!method || !extensionRequestId || !title) {
    return undefined;
  }

  const questionId = `${method}:${extensionRequestId}`;
  const question: UserInputQuestion = {
    id: questionId,
    header:
      method === "confirm"
        ? "Confirm"
        : method === "select"
          ? "Choose"
          : method === "editor"
            ? "Input"
            : "Question",
    question: title,
    options:
      method === "confirm"
        ? [
            { label: "Yes", description: "Confirm" },
            { label: "No", description: "Cancel" },
          ]
        : normalizePiExtensionOptions(event.options),
    multiSelect: false,
  };
  return {
    pending: {
      extensionRequestId,
      method,
      questionId,
      question: title,
    },
    question,
  };
}

function firstAnswerValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value[0];
  }
  return value;
}

function stringAnswerForPendingPiRequest(
  pending: PendingPiExtensionUserInput,
  answers: ProviderUserInputAnswers,
): string | undefined {
  const value = firstAnswerValue(answers[pending.questionId] ?? answers[pending.question]);
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function piConfirmAnswer(value: string | undefined): boolean | undefined {
  if (!value) {
    return undefined;
  }
  const normalized = value.trim().toLowerCase();
  if (["yes", "true", "confirm", "confirmed", "ok", "continue"].includes(normalized)) {
    return true;
  }
  if (["no", "false", "cancel", "cancelled", "deny"].includes(normalized)) {
    return false;
  }
  return undefined;
}

function buildPiExtensionUiResponse(
  pending: PendingPiExtensionUserInput,
  answers: ProviderUserInputAnswers,
): PiRpcCommand {
  const answer = stringAnswerForPendingPiRequest(pending, answers);
  if (!answer) {
    return {
      type: "extension_ui_response",
      id: pending.extensionRequestId,
      cancelled: true,
    };
  }
  if (pending.method === "confirm") {
    const confirmed = piConfirmAnswer(answer);
    return confirmed === undefined
      ? {
          type: "extension_ui_response",
          id: pending.extensionRequestId,
          cancelled: true,
        }
      : {
          type: "extension_ui_response",
          id: pending.extensionRequestId,
          confirmed,
        };
  }
  return {
    type: "extension_ui_response",
    id: pending.extensionRequestId,
    value: answer,
  };
}

function resolveTurnSnapshot(context: PiSessionContext, turnId: TurnId): PiTurnSnapshot {
  const existing = context.turns.find((turn) => turn.id === turnId);
  if (existing) {
    return existing;
  }
  const created = { id: turnId, items: [] };
  context.turns.push(created);
  return created;
}

function appendTurnItem(
  context: PiSessionContext,
  turnId: TurnId | undefined,
  item: unknown,
): void {
  if (!turnId) {
    return;
  }
  resolveTurnSnapshot(context, turnId).items.push(item);
}

function ensureSessionContext(
  sessions: ReadonlyMap<ThreadId, PiSessionContext>,
  threadId: ThreadId,
): PiSessionContext {
  const context = sessions.get(threadId);
  if (!context) {
    throw new ProviderAdapterSessionNotFoundError({ provider: PROVIDER, threadId });
  }
  if (Ref.getUnsafe(context.stopped)) {
    throw new ProviderAdapterSessionClosedError({ provider: PROVIDER, threadId });
  }
  return context;
}

function responseError(method: string, response: PiRpcResponse): ProviderAdapterRequestError {
  return new ProviderAdapterRequestError({
    provider: PROVIDER,
    method,
    detail: response.error ?? `Pi RPC command '${response.command}' failed.`,
  });
}

function toRequestError(method: string, cause: unknown): ProviderAdapterRequestError {
  return new ProviderAdapterRequestError({
    provider: PROVIDER,
    method,
    detail: PiRuntimeError.is(cause) ? cause.detail : piRuntimeErrorDetail(cause),
    cause,
  });
}

function toProcessError(threadId: ThreadId, cause: unknown): ProviderAdapterProcessError {
  return new ProviderAdapterProcessError({
    provider: PROVIDER,
    threadId,
    detail: PiRuntimeError.is(cause) ? cause.detail : piRuntimeErrorDetail(cause),
    cause,
  });
}

function stripBearerPrefix(authorizationHeader: string): string {
  return authorizationHeader.replace(/^Bearer\s+/iu, "").trim();
}

function validateModelSelectionInstance(
  operation: string,
  inputInstanceId: ProviderInstanceId | undefined,
  boundInstanceId: ProviderInstanceId,
): Effect.Effect<void, ProviderAdapterValidationError> {
  if (inputInstanceId === undefined || inputInstanceId === boundInstanceId) {
    return Effect.void;
  }
  return Effect.fail(
    new ProviderAdapterValidationError({
      provider: PROVIDER,
      operation,
      issue: `Pi model selection is bound to instance '${inputInstanceId}', expected '${boundInstanceId}'.`,
    }),
  );
}

function validatePiResponse(method: string, response: PiRpcResponse) {
  return response.success ? Effect.succeed(response) : Effect.fail(responseError(method, response));
}

function updateProviderSession(
  context: PiSessionContext,
  patch: Partial<ProviderSession>,
  options?: {
    readonly clearActiveTurnId?: boolean;
    readonly clearLastError?: boolean;
  },
): Effect.Effect<ProviderSession> {
  return Effect.gen(function* () {
    const updatedAt = yield* nowIso;
    const nextSession = {
      ...context.session,
      ...patch,
      updatedAt,
    } as ProviderSession & Record<string, unknown>;
    const mutableSession = nextSession as Record<string, unknown>;
    if (options?.clearActiveTurnId) {
      delete mutableSession.activeTurnId;
    }
    if (options?.clearLastError) {
      delete mutableSession.lastError;
    }
    context.session = nextSession;
    return nextSession;
  });
}

const stopPiContext = Effect.fn("stopPiContext")(function* (context: PiSessionContext) {
  if (yield* Ref.getAndSet(context.stopped, true)) {
    return false;
  }
  yield* context.rpc.stop;
  yield* Scope.close(context.sessionScope, Exit.void).pipe(Effect.ignoreCause);
  yield* context.cleanup;
  return true;
});

export function makePiAdapter(piSettings: PiSettings, options?: PiAdapterLiveOptions) {
  return Effect.gen(function* () {
    const boundInstanceId = options?.instanceId ?? ProviderInstanceId.make("pi");
    const serverConfig = yield* ServerConfig;
    const piRuntime = yield* PiRuntime;
    const crypto = yield* Crypto.Crypto;
    const fileSystem = yield* FileSystem.FileSystem;
    const path = yield* Path.Path;
    const runtimeEvents = yield* Queue.unbounded<ProviderRuntimeEvent>();
    const sessions = new Map<ThreadId, PiSessionContext>();

    const randomUUIDv4 = crypto.randomUUIDv4.pipe(
      Effect.mapError(
        (cause) =>
          new ProviderAdapterRequestError({
            provider: PROVIDER,
            method: "crypto/randomUUIDv4",
            detail: "Failed to generate Pi runtime identifier.",
            cause,
          }),
      ),
    );

    const buildEventBase = (input: EventBaseInput) =>
      Effect.all({
        eventId: randomUUIDv4.pipe(Effect.map(EventId.make)),
        createdAt: input.createdAt === undefined ? nowIso : Effect.succeed(input.createdAt),
      }).pipe(
        Effect.map(({ eventId, createdAt }) => ({
          eventId,
          provider: PROVIDER,
          providerInstanceId: boundInstanceId,
          threadId: input.threadId,
          createdAt,
          ...(input.turnId ? { turnId: input.turnId } : {}),
          ...(input.itemId ? { itemId: RuntimeItemId.make(input.itemId) } : {}),
          ...(input.requestId ? { requestId: RuntimeRequestId.make(input.requestId) } : {}),
          ...(input.raw !== undefined
            ? {
                raw: {
                  source: PI_RPC_RAW_SOURCE,
                  payload: input.raw,
                },
              }
            : {}),
        })),
      );

    const emit = (event: ProviderRuntimeEvent) =>
      Queue.offer(runtimeEvents, event).pipe(Effect.asVoid);

    const writeNativeEventBestEffort = (
      threadId: ThreadId,
      event: { readonly observedAt: string; readonly event: Record<string, unknown> },
    ) =>
      options?.nativeEventLogger
        ? options.nativeEventLogger
            .write(event, threadId)
            .pipe(Effect.catchCause(() => Effect.void))
        : Effect.void;

    yield* Effect.addFinalizer(() =>
      Effect.gen(function* () {
        const contexts = [...sessions.values()];
        sessions.clear();
        yield* Effect.forEach(contexts, (context) => Effect.ignoreCause(stopPiContext(context)), {
          concurrency: "unbounded",
          discard: true,
        });
      }).pipe(Effect.ensuring(Queue.shutdown(runtimeEvents))),
    );

    const makeMcpBridge = (
      threadId: ThreadId,
    ): Effect.Effect<PiMcpBridge, ProviderAdapterRequestError> =>
      Effect.gen(function* () {
        const mcpSession = McpProviderSession.readMcpProviderSession(threadId);
        if (!mcpSession) return { _tag: "Absent" } satisfies PiMcpBridge;

        const bridgeId = yield* randomUUIDv4;
        const bridgeDir = path.join(serverConfig.stateDir, "pi-mcp", bridgeId);
        const configPath = path.join(bridgeDir, "mcp.json");
        const config = {
          settings: {
            toolPrefix: "none",
            disableProxyTool: false,
            directTools: false,
          },
          mcpServers: {
            "t3-code": {
              url: mcpSession.endpoint,
              auth: "bearer",
              bearerTokenEnv: PI_MCP_BRIDGE_TOKEN_ENV,
              lifecycle: "lazy",
              exposeResources: false,
            },
          },
        };

        const writeResult = yield* Effect.gen(function* () {
          yield* fileSystem.makeDirectory(bridgeDir, { recursive: true });
          yield* fileSystem.writeFileString(configPath, `${encodeUnknownJsonString(config)}\n`);
        }).pipe(Effect.exit);

        if (Exit.isFailure(writeResult)) {
          return {
            _tag: "Failed",
            providerSessionId: mcpSession.providerSessionId,
            detail: piRuntimeErrorDetail(Cause.squash(writeResult.cause)),
          } satisfies PiMcpBridge;
        }

        const token = stripBearerPrefix(mcpSession.authorizationHeader);
        return {
          _tag: "Ready",
          configPath,
          providerSessionId: mcpSession.providerSessionId,
          environment: {
            [PI_MCP_BRIDGE_TOKEN_ENV]: token,
          },
          cleanup: fileSystem
            .remove(bridgeDir, { recursive: true, force: true })
            .pipe(Effect.catchCause(() => Effect.void)),
        } satisfies PiMcpBridge;
      });

    const emitMcpBridgeFailureWarning = Effect.fn("emitMcpBridgeFailureWarning")(function* (
      threadId: ThreadId,
      bridge: Extract<PiMcpBridge, { readonly _tag: "Failed" }>,
    ) {
      yield* emit({
        ...(yield* buildEventBase({ threadId })),
        type: "runtime.warning",
        payload: {
          message:
            "Pi MCP adapter bridge could not be configured; T3 preview MCP browser tools are unavailable for this Pi session.",
          detail: {
            providerSessionId: bridge.providerSessionId,
            capability: "preview",
            status: "config_failed",
            reason: bridge.detail,
          },
        },
      });
    });

    const emitUnexpectedExit = Effect.fn("emitUnexpectedExit")(function* (
      context: PiSessionContext,
      message: string,
    ) {
      if (yield* Ref.getAndSet(context.stopped, true)) {
        return;
      }
      const turnId = context.activeTurnId;
      sessions.delete(context.session.threadId);
      yield* emit({
        ...(yield* buildEventBase({ threadId: context.session.threadId, turnId })),
        type: "runtime.error",
        payload: {
          message,
          class: "transport_error",
        },
      }).pipe(Effect.ignore);
      yield* emit({
        ...(yield* buildEventBase({ threadId: context.session.threadId, turnId })),
        type: "session.exited",
        payload: {
          reason: message,
          recoverable: false,
          exitKind: "error",
        },
      }).pipe(Effect.ignore);
      yield* context.rpc.stop;
      yield* Scope.close(context.sessionScope, Exit.void).pipe(Effect.ignoreCause);
      yield* context.cleanup;
    });

    const completeActiveTurn = Effect.fn("completeActiveTurn")(function* (
      context: PiSessionContext,
      event: PiRpcEvent,
    ) {
      const turnId = context.activeTurnId;
      if (!turnId || context.completedTurnIds.has(turnId)) {
        return;
      }
      context.completedTurnIds.add(turnId);
      appendTurnItem(context, turnId, event);
      yield* emit({
        ...(yield* buildEventBase({
          threadId: context.session.threadId,
          turnId,
          raw: event,
        })),
        type: "turn.completed",
        payload: {
          state: "completed",
        },
      });
      context.activeTurnId = undefined;
      yield* updateProviderSession(
        context,
        {
          status: "ready",
        },
        { clearActiveTurnId: true, clearLastError: true },
      );
    });

    const failActiveTurn = Effect.fn("failActiveTurn")(function* (
      context: PiSessionContext,
      message: string,
      raw?: unknown,
    ) {
      const turnId = context.activeTurnId;
      if (turnId && !context.completedTurnIds.has(turnId)) {
        context.completedTurnIds.add(turnId);
        yield* emit({
          ...(yield* buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw,
          })),
          type: "turn.completed",
          payload: {
            state: "failed",
            errorMessage: message,
          },
        });
      }
      context.activeTurnId = undefined;
      yield* updateProviderSession(
        context,
        {
          status: "ready",
          lastError: message,
        },
        { clearActiveTurnId: true },
      );
    });

    const handleMessageUpdate = Effect.fn("handleMessageUpdate")(function* (
      context: PiSessionContext,
      event: PiRpcEvent,
    ) {
      const turnId = context.activeTurnId;
      if (!turnId || !isRecord(event.assistantMessageEvent)) {
        return;
      }
      const assistantEvent = event.assistantMessageEvent;
      const eventType = stringField(assistantEvent, "type");
      if (eventType === "text_delta" || eventType === "thinking_delta") {
        const delta = stringField(assistantEvent, "delta");
        if (!delta) {
          return;
        }
        yield* emit({
          ...(yield* buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: event,
          })),
          type: "content.delta",
          payload: {
            streamKind: eventType === "thinking_delta" ? "reasoning_text" : "assistant_text",
            delta,
            contentIndex: numberField(assistantEvent, "contentIndex") ?? 0,
          },
        });
        return;
      }
      if (eventType === "toolcall_start" || eventType === "toolcall_end") {
        const toolCall = isRecord(assistantEvent.toolCall)
          ? assistantEvent.toolCall
          : assistantEvent;
        const toolName = stringField(toolCall, "name") ?? "Pi tool call";
        const itemId = stringField(toolCall, "id");
        yield* emit({
          ...(yield* buildEventBase({
            threadId: context.session.threadId,
            turnId,
            itemId,
            raw: event,
          })),
          type: eventType === "toolcall_start" ? "item.started" : "item.completed",
          payload: {
            itemType: toToolLifecycleItemType(toolName),
            status: eventType === "toolcall_start" ? "inProgress" : "completed",
            title: toolName,
            data: toolCall,
          },
        });
        return;
      }
      if (eventType === "error") {
        const message = stringField(assistantEvent, "reason") ?? "Pi assistant message failed.";
        yield* failActiveTurn(context, message, event);
        yield* emit({
          ...(yield* buildEventBase({ threadId: context.session.threadId, turnId, raw: event })),
          type: "runtime.error",
          payload: {
            message,
            class: "provider_error",
            detail: assistantEvent,
          },
        });
      }
    });

    const handleToolEvent = Effect.fn("handleToolEvent")(function* (
      context: PiSessionContext,
      event: PiRpcEvent,
    ) {
      if (!context.activeTurnId || !isRecord(event)) {
        return;
      }
      const toolName = resolveToolName(event);
      const itemId = resolveToolCallId(event);
      const itemType = toToolLifecycleItemType(toolName);
      const currentInput = isRecord(event.args) ? event.args : undefined;
      const previousInput = itemId ? context.toolInputById.get(itemId) : undefined;
      if (itemId && currentInput) {
        context.toolInputById.set(itemId, currentInput);
      }
      const status =
        event.type === "tool_execution_end"
          ? event.isError === true
            ? "failed"
            : "completed"
          : "inProgress";
      const eventType =
        event.type === "tool_execution_start"
          ? "item.started"
          : event.type === "tool_execution_update"
            ? "item.updated"
            : "item.completed";
      yield* emit({
        ...(yield* buildEventBase({
          threadId: context.session.threadId,
          turnId: context.activeTurnId,
          itemId,
          raw: event,
        })),
        type: eventType,
        payload: {
          itemType,
          status,
          title: toolName,
          data: buildPiToolData(event, toolName, itemId, previousInput),
        },
      });
      if (itemId && event.type === "tool_execution_end") {
        context.toolInputById.delete(itemId);
      }
    });

    const handlePiEvent = Effect.fn("handlePiEvent")(function* (
      context: PiSessionContext,
      event: PiRpcEvent,
    ) {
      const observedAt = yield* nowIso;
      yield* writeNativeEventBestEffort(context.session.threadId, {
        observedAt,
        event: isRecord(event) ? event : { type: "unknown", payload: event },
      });
      appendTurnItem(context, context.activeTurnId, event);

      switch (event.type) {
        case "agent_start": {
          yield* emit({
            ...(yield* buildEventBase({
              threadId: context.session.threadId,
              turnId: context.activeTurnId,
              raw: event,
            })),
            type: "session.state.changed",
            payload: {
              state: "running",
            },
          });
          break;
        }
        case "agent_end": {
          yield* completeActiveTurn(context, event);
          break;
        }
        case "message_update": {
          yield* handleMessageUpdate(context, event);
          break;
        }
        case "tool_execution_start":
        case "tool_execution_update":
        case "tool_execution_end": {
          yield* handleToolEvent(context, event);
          break;
        }
        case "extension_error": {
          const message = isRecord(event)
            ? (stringField(event, "error") ?? "Pi extension failed.")
            : "Pi extension failed.";
          yield* emit({
            ...(yield* buildEventBase({
              threadId: context.session.threadId,
              turnId: context.activeTurnId,
              raw: event,
            })),
            type: "runtime.error",
            payload: {
              message,
              class: "provider_error",
              detail: event,
            },
          });
          break;
        }
        case "extension_ui_request": {
          if (isBenignExtensionUiRequest(event)) {
            break;
          }
          const request = buildPiExtensionUserInputQuestion(event);
          if (request) {
            const requestId = ApprovalRequestId.make(request.pending.extensionRequestId);
            context.pendingExtensionUserInputs.set(requestId, request.pending);
            yield* emit({
              ...(yield* buildEventBase({
                threadId: context.session.threadId,
                turnId: context.activeTurnId,
                requestId,
                raw: event,
              })),
              type: "user-input.requested",
              payload: {
                questions: [request.question],
              },
            });
            break;
          }
          yield* emit({
            ...(yield* buildEventBase({
              threadId: context.session.threadId,
              turnId: context.activeTurnId,
              requestId: isRecord(event) ? stringField(event, "id") : undefined,
              raw: event,
            })),
            type: "runtime.warning",
            payload: {
              message:
                "Pi extension UI requests are not supported in this T3 Code Pi integration yet.",
              detail: event,
            },
          });
          break;
        }
        default:
          break;
      }
    });

    const startEventPump = Effect.fn("startEventPump")(function* (context: PiSessionContext) {
      yield* context.rpc.events.pipe(
        Stream.runForEach((event) => handlePiEvent(context, event)),
        Effect.exit,
        Effect.flatMap((exit) =>
          Effect.gen(function* () {
            if (yield* Ref.get(context.stopped)) {
              return;
            }
            if (Exit.isFailure(exit)) {
              yield* emitUnexpectedExit(context, piRuntimeErrorDetail(Cause.squash(exit.cause)));
            }
          }),
        ),
        Effect.forkIn(context.sessionScope),
      );

      yield* context.rpc.exitCode.pipe(
        Effect.flatMap((code) =>
          Effect.gen(function* () {
            if (yield* Ref.get(context.stopped)) {
              return;
            }
            yield* emitUnexpectedExit(context, `Pi RPC process exited unexpectedly (${code}).`);
          }),
        ),
        Effect.forkIn(context.sessionScope),
      );
    });

    const parseStartModelSelection = Effect.fn("parseStartModelSelection")(function* (input: {
      readonly modelSelection: Parameters<PiAdapterShape["startSession"]>[0]["modelSelection"];
      readonly operation: string;
    }) {
      const modelSelection = input.modelSelection;
      if (!modelSelection) {
        return {};
      }
      yield* validateModelSelectionInstance(
        input.operation,
        modelSelection.instanceId,
        boundInstanceId,
      );
      const parsed = parsePiModelSlug(modelSelection.model);
      if (!parsed) {
        return yield* new ProviderAdapterValidationError({
          provider: PROVIDER,
          operation: input.operation,
          issue: "Pi model selection must use the 'provider/model' format.",
        });
      }
      const thinking = getModelSelectionStringOptionValue(modelSelection, "thinking");
      if (thinking !== undefined && !isPiThinkingLevel(thinking)) {
        return yield* new ProviderAdapterValidationError({
          provider: PROVIDER,
          operation: input.operation,
          issue: `Unsupported Pi thinking level '${thinking}'.`,
        });
      }
      return {
        provider: parsed.provider,
        modelId: parsed.modelId,
        ...(thinking ? { thinkingLevel: thinking } : {}),
      };
    });

    const applyModelSelection = Effect.fn("applyModelSelection")(function* (
      context: PiSessionContext,
      modelSelection: Parameters<PiAdapterShape["sendTurn"]>[0]["modelSelection"] | undefined,
      operation: string,
    ) {
      if (!modelSelection) {
        return context.session.model;
      }
      yield* validateModelSelectionInstance(operation, modelSelection.instanceId, boundInstanceId);
      const parsed = parsePiModelSlug(modelSelection.model);
      if (!parsed) {
        return yield* new ProviderAdapterValidationError({
          provider: PROVIDER,
          operation,
          issue: "Pi model selection must use the 'provider/model' format.",
        });
      }
      const setModelResponse = yield* context.rpc
        .request({
          type: "set_model",
          provider: parsed.provider,
          modelId: parsed.modelId,
        })
        .pipe(
          Effect.mapError((cause) => toRequestError("set_model", cause)),
          Effect.flatMap((response) => validatePiResponse("set_model", response)),
        );
      const thinking = getModelSelectionStringOptionValue(modelSelection, "thinking");
      if (thinking !== undefined) {
        if (!isPiThinkingLevel(thinking)) {
          return yield* new ProviderAdapterValidationError({
            provider: PROVIDER,
            operation,
            issue: `Unsupported Pi thinking level '${thinking}'.`,
          });
        }
        yield* context.rpc.request({ type: "set_thinking_level", level: thinking }).pipe(
          Effect.mapError((cause) => toRequestError("set_thinking_level", cause)),
          Effect.flatMap((response) => validatePiResponse("set_thinking_level", response)),
        );
      }
      return modelSelection.model ?? setModelResponse.command;
    });

    const startSession: PiAdapterShape["startSession"] = Effect.fn("startSession")(
      function* (input) {
        if (input.provider !== undefined && input.provider !== PROVIDER) {
          return yield* new ProviderAdapterValidationError({
            provider: PROVIDER,
            operation: "startSession",
            issue: `Pi adapter cannot start provider '${input.provider}'.`,
          });
        }
        if (
          input.providerInstanceId !== undefined &&
          input.providerInstanceId !== boundInstanceId
        ) {
          return yield* new ProviderAdapterValidationError({
            provider: PROVIDER,
            operation: "startSession",
            issue: `Pi adapter instance '${boundInstanceId}' cannot start '${input.providerInstanceId}'.`,
          });
        }
        const existing = sessions.get(input.threadId);
        if (existing) {
          yield* stopPiContext(existing);
          sessions.delete(input.threadId);
        }

        const directory = input.cwd ?? serverConfig.cwd;
        const modelArgs = yield* parseStartModelSelection({
          modelSelection: input.modelSelection,
          operation: "startSession",
        });
        const mcpBridge = yield* makeMcpBridge(input.threadId);
        const startEnvironment =
          mcpBridge._tag === "Ready"
            ? { ...options?.environment, ...mcpBridge.environment }
            : options?.environment;
        const sessionScope = yield* Scope.make();
        const startedExit = yield* piRuntime
          .startPiRpcSession({
            binaryPath: piSettings.binaryPath,
            args: buildPiRpcArgs({
              ...modelArgs,
              ...(mcpBridge._tag === "Ready"
                ? {
                    appendSystemPrompt: PI_T3_BROWSER_SYSTEM_PROMPT,
                    mcpConfigPath: mcpBridge.configPath,
                  }
                : {}),
              noSession: true,
            }),
            ...(startEnvironment ? { environment: startEnvironment } : {}),
            cwd: directory,
          })
          .pipe(Effect.provideService(Scope.Scope, sessionScope), Effect.exit);
        if (Exit.isFailure(startedExit)) {
          yield* Scope.close(sessionScope, Exit.void).pipe(Effect.ignoreCause);
          if (mcpBridge._tag === "Ready") {
            yield* mcpBridge.cleanup;
          }
          return yield* toProcessError(input.threadId, Cause.squash(startedExit.cause));
        }

        const createdAt = yield* nowIso;
        const session: ProviderSession = {
          provider: PROVIDER,
          providerInstanceId: boundInstanceId,
          status: "ready",
          runtimeMode: input.runtimeMode,
          cwd: directory,
          ...(input.modelSelection ? { model: input.modelSelection.model } : {}),
          threadId: input.threadId,
          createdAt,
          updatedAt: createdAt,
        };
        const context: PiSessionContext = {
          session,
          rpc: startedExit.value,
          sessionScope,
          turns: [],
          completedTurnIds: new Set(),
          activeTurnId: undefined,
          stopped: yield* Ref.make(false),
          cleanup: mcpBridge._tag === "Ready" ? mcpBridge.cleanup : Effect.void,
          toolInputById: new Map(),
          pendingExtensionUserInputs: new Map(),
        };
        sessions.set(input.threadId, context);
        yield* startEventPump(context);
        yield* emit({
          ...(yield* buildEventBase({ threadId: input.threadId })),
          type: "session.started",
          payload: {
            message: "Pi session started",
          },
        });
        yield* emit({
          ...(yield* buildEventBase({ threadId: input.threadId })),
          type: "thread.started",
          payload: {
            providerThreadId: `pi-rpc-${startedExit.value.pid}`,
          },
        });
        if (mcpBridge._tag === "Failed") {
          yield* emitMcpBridgeFailureWarning(input.threadId, mcpBridge);
        }
        return session;
      },
    );

    const sendTurn: PiAdapterShape["sendTurn"] = Effect.fn("sendTurn")(function* (input) {
      const context = ensureSessionContext(sessions, input.threadId);
      if (input.attachments && input.attachments.length > 0) {
        return yield* new ProviderAdapterValidationError({
          provider: PROVIDER,
          operation: "sendTurn",
          issue: "Pi provider does not support T3 Code attachments in v1.",
        });
      }
      const text = input.input?.trim();
      if (!text) {
        return yield* new ProviderAdapterValidationError({
          provider: PROVIDER,
          operation: "sendTurn",
          issue: "Pi turns require non-empty text input.",
        });
      }

      const steeringTurnId = context.activeTurnId;
      const turnId = steeringTurnId ?? TurnId.make(`${PI_TURN_ID_PREFIX}-${yield* randomUUIDv4}`);
      const shouldQueueFollowUp =
        steeringTurnId !== undefined || context.session.status === "running";
      const modelSelection =
        input.modelSelection ??
        (context.session.model
          ? { instanceId: boundInstanceId, model: context.session.model }
          : undefined);
      const nextModel = yield* applyModelSelection(context, modelSelection, "sendTurn");

      context.activeTurnId = turnId;
      yield* updateProviderSession(
        context,
        {
          status: "running",
          activeTurnId: turnId,
          ...(nextModel ? { model: nextModel } : {}),
        },
        { clearLastError: true },
      );

      if (steeringTurnId === undefined) {
        yield* emit({
          ...(yield* buildEventBase({ threadId: input.threadId, turnId })),
          type: "turn.started",
          payload: nextModel ? { model: nextModel } : {},
        });
      }

      yield* context.rpc
        .request({
          type: "prompt",
          message: text,
          ...(shouldQueueFollowUp ? { streamingBehavior: "followUp" } : {}),
        })
        .pipe(
          Effect.mapError((cause) => toRequestError("prompt", cause)),
          Effect.flatMap((response) => validatePiResponse("prompt", response)),
          Effect.tapError((requestError) =>
            shouldQueueFollowUp
              ? Effect.void
              : Effect.gen(function* () {
                  yield* failActiveTurn(context, requestError.detail);
                  yield* emit({
                    ...(yield* buildEventBase({ threadId: input.threadId, turnId })),
                    type: "turn.aborted",
                    payload: {
                      reason: requestError.detail,
                    },
                  });
                }),
          ),
        );

      return {
        threadId: input.threadId,
        turnId,
      };
    });

    const interruptTurn: PiAdapterShape["interruptTurn"] = Effect.fn("interruptTurn")(
      function* (threadId, turnId) {
        const context = ensureSessionContext(sessions, threadId);
        const activeTurnId = turnId ?? context.activeTurnId;
        yield* context.rpc.request({ type: "abort" }).pipe(
          Effect.mapError((cause) => toRequestError("abort", cause)),
          Effect.flatMap((response) => validatePiResponse("abort", response)),
        );
        if (activeTurnId) {
          context.completedTurnIds.add(activeTurnId);
          context.activeTurnId = undefined;
          yield* updateProviderSession(
            context,
            {
              status: "ready",
            },
            { clearActiveTurnId: true },
          );
          yield* emit({
            ...(yield* buildEventBase({ threadId, turnId: activeTurnId })),
            type: "turn.aborted",
            payload: {
              reason: "Interrupted by user.",
            },
          });
        }
      },
    );

    const unsupportedRequest = (method: string) =>
      Effect.fail(
        new ProviderAdapterRequestError({
          provider: PROVIDER,
          method,
          detail: "Pi RPC does not support this T3 Code provider operation in v1.",
        }),
      );

    const stopSession: PiAdapterShape["stopSession"] = Effect.fn("stopSession")(
      function* (threadId) {
        const context = sessions.get(threadId);
        if (!context) {
          throw new ProviderAdapterSessionNotFoundError({ provider: PROVIDER, threadId });
        }
        const stopped = yield* stopPiContext(context);
        sessions.delete(threadId);
        if (!stopped) {
          return;
        }
        yield* emit({
          ...(yield* buildEventBase({ threadId })),
          type: "session.exited",
          payload: {
            reason: "Session stopped.",
            recoverable: false,
            exitKind: "graceful",
          },
        });
      },
    );

    const listSessions: PiAdapterShape["listSessions"] = () =>
      Effect.sync(() => [...sessions.values()].map((context) => context.session));

    const hasSession: PiAdapterShape["hasSession"] = (threadId) =>
      Effect.sync(() => sessions.has(threadId));

    const readThread: PiAdapterShape["readThread"] = (threadId) =>
      Effect.sync(() => {
        const context = ensureSessionContext(sessions, threadId);
        return {
          threadId,
          turns: context.turns,
        };
      });

    const rollbackThread: PiAdapterShape["rollbackThread"] = () =>
      unsupportedRequest("rollbackThread");

    const respondToUserInput: PiAdapterShape["respondToUserInput"] = Effect.fn(
      "respondToUserInput",
    )(function* (threadId, requestId, answers) {
      const context = ensureSessionContext(sessions, threadId);
      const pending = context.pendingExtensionUserInputs.get(requestId);
      if (!pending) {
        return yield* new ProviderAdapterRequestError({
          provider: PROVIDER,
          method: "extension_ui_response",
          detail: `Unknown pending user-input request: ${requestId}`,
        });
      }
      context.pendingExtensionUserInputs.delete(requestId);
      yield* context.rpc.send(buildPiExtensionUiResponse(pending, answers)).pipe(
        Effect.mapError(
          (cause) =>
            new ProviderAdapterRequestError({
              provider: PROVIDER,
              method: "extension_ui_response",
              detail: piRuntimeErrorDetail(cause),
              cause,
            }),
        ),
      );
      yield* emit({
        ...(yield* buildEventBase({
          threadId,
          turnId: context.activeTurnId,
          requestId,
          raw: {
            source: "pi.rpc.extension_ui_response",
            method: pending.method,
            extensionRequestId: pending.extensionRequestId,
          },
        })),
        type: "user-input.resolved",
        payload: { answers },
      });
    });

    const stopAll: PiAdapterShape["stopAll"] = () =>
      Effect.gen(function* () {
        const contexts = [...sessions.values()];
        sessions.clear();
        yield* Effect.forEach(contexts, (context) => Effect.ignoreCause(stopPiContext(context)), {
          concurrency: "unbounded",
          discard: true,
        });
      });

    return {
      provider: PROVIDER,
      capabilities: {
        sessionModelSwitch: "in-session",
      },
      startSession,
      sendTurn,
      interruptTurn,
      respondToRequest: () => unsupportedRequest("respondToRequest"),
      respondToUserInput,
      stopSession,
      listSessions,
      hasSession,
      readThread,
      rollbackThread,
      stopAll,
      get streamEvents() {
        return Stream.fromQueue(runtimeEvents);
      },
    } satisfies PiAdapterShape;
  });
}
