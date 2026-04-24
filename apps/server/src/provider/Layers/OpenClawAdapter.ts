import { randomUUID } from "node:crypto";

import {
  type CanonicalItemType,
  EventId,
  type ProviderRuntimeEvent,
  type ProviderSession,
  type RuntimeErrorClass,
  RuntimeItemId,
  type RuntimeItemStatus,
  RuntimeRequestId,
  ThreadId,
  TurnId,
  type UserInputQuestion,
} from "@t3tools/contracts";
import { Effect, Layer, PubSub, Semaphore, Stream } from "effect";

import { resolveAttachmentPath } from "../../attachmentStore.ts";
import { ServerConfig } from "../../config.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import { ProviderAdapterRequestError, ProviderAdapterSessionNotFoundError } from "../Errors.ts";
import {
  getOpenClawMessageId,
  getOpenClawMessageRole,
  getOpenClawMessageRunId,
  getOpenClawMessageText,
} from "../openclawMessages.ts";
import { OpenClawAdapter, type OpenClawAdapterShape } from "../Services/OpenClawAdapter.ts";
import {
  connectOpenClawGateway,
  getOpenClawGatewayErrorMessage,
  type OpenClawGatewayConnection,
  type OpenClawGatewayNotification,
} from "../openclawGateway.ts";

const PROVIDER = "openclaw" as const;
const OPENCLAW_RESUME_SCHEMA_VERSION = 2 as const;

interface OpenClawTurnSnapshot {
  readonly id: TurnId;
  readonly items: Array<unknown>;
}

interface OpenClawSessionContext {
  session: ProviderSession;
  readonly connection: OpenClawGatewayConnection;
  readonly sessionKey: string;
  readonly turns: Array<OpenClawTurnSnapshot>;
  readonly pendingApprovals: Set<string>;
  readonly pendingUserInputs: Set<string>;
  activeTurnId: TurnId | undefined;
  stopped: boolean;
}

export interface OpenClawAdapterLiveOptions {}

function nowIso(): string {
  return new Date().toISOString();
}

function trimOrNull(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function parseResumeCursor(value: unknown): {
  readonly sessionKey: string;
} | null {
  const record = asRecord(value);
  if (!record) {
    return null;
  }

  const sessionKey = trimOrNull(record.sessionKey);
  if (record.schemaVersion === OPENCLAW_RESUME_SCHEMA_VERSION && sessionKey) {
    return { sessionKey };
  }

  const legacySessionId = trimOrNull(record.sessionId);
  if (record.schemaVersion === 1 && legacySessionId) {
    return { sessionKey: legacySessionId };
  }

  return null;
}

function sessionResumeCursor(sessionKey: string) {
  return {
    schemaVersion: OPENCLAW_RESUME_SCHEMA_VERSION,
    sessionKey,
  };
}

function getTurnSnapshot(context: OpenClawSessionContext, turnId: TurnId): OpenClawTurnSnapshot {
  const existing = context.turns.find((turn) => turn.id === turnId);
  if (existing) {
    return existing;
  }
  const created: OpenClawTurnSnapshot = { id: turnId, items: [] };
  context.turns.push(created);
  return created;
}

function appendTurnItem(
  context: OpenClawSessionContext,
  turnId: TurnId | undefined,
  item: unknown,
): void {
  if (!turnId) {
    return;
  }
  getTurnSnapshot(context, turnId).items.push(item);
}

function toRequestType(
  value: unknown,
):
  | "command_execution_approval"
  | "file_read_approval"
  | "file_change_approval"
  | "tool_user_input"
  | "unknown" {
  switch (trimOrNull(value)) {
    case "command":
    case "command_execution_approval":
      return "command_execution_approval";
    case "file-read":
    case "file_read_approval":
      return "file_read_approval";
    case "file-change":
    case "file_change_approval":
      return "file_change_approval";
    case "tool_user_input":
      return "tool_user_input";
    default:
      return "unknown";
  }
}

function toCanonicalItemType(value: unknown): CanonicalItemType {
  switch (trimOrNull(value)) {
    case "assistant_message":
    case "assistant":
      return "assistant_message";
    case "user_message":
    case "user":
      return "user_message";
    case "reasoning":
      return "reasoning";
    case "plan":
      return "plan";
    case "command_execution":
      return "command_execution";
    case "file_change":
      return "file_change";
    case "mcp_tool_call":
      return "mcp_tool_call";
    case "dynamic_tool_call":
      return "dynamic_tool_call";
    case "collab_agent_tool_call":
      return "collab_agent_tool_call";
    case "web_search":
      return "web_search";
    case "image_view":
      return "image_view";
    case "review_entered":
      return "review_entered";
    case "review_exited":
      return "review_exited";
    case "context_compaction":
      return "context_compaction";
    case "error":
      return "error";
    default:
      return "unknown";
  }
}

function toRuntimeItemStatus(value: unknown): RuntimeItemStatus | undefined {
  switch (trimOrNull(value)) {
    case "inProgress":
    case "running":
      return "inProgress";
    case "completed":
      return "completed";
    case "failed":
      return "failed";
    case "declined":
      return "declined";
    default:
      return undefined;
  }
}

function toRuntimeErrorClass(value: unknown): RuntimeErrorClass | undefined {
  switch (trimOrNull(value)) {
    case "provider_error":
      return "provider_error";
    case "transport_error":
      return "transport_error";
    case "permission_error":
      return "permission_error";
    case "validation_error":
      return "validation_error";
    case "unknown":
      return "unknown";
    default:
      return undefined;
  }
}

function parseQuestions(value: unknown): ReadonlyArray<UserInputQuestion> {
  if (!Array.isArray(value)) {
    return [];
  }
  const questions: Array<UserInputQuestion> = [];
  for (const entry of value) {
    const record = asRecord(entry);
    const id = trimOrNull(record?.id);
    const header = trimOrNull(record?.header);
    const question = trimOrNull(record?.question);
    const options = Array.isArray(record?.options)
      ? record.options
          .map((option) => {
            const optionRecord = asRecord(option);
            const label = trimOrNull(optionRecord?.label);
            const description = trimOrNull(optionRecord?.description);
            return label && description ? { label, description } : null;
          })
          .filter((option): option is { label: string; description: string } => option !== null)
      : [];
    if (!id || !header || !question) {
      continue;
    }
    questions.push({
      id,
      header,
      question,
      options,
      ...(record?.multiSelect === true ? { multiSelect: true } : {}),
    });
  }
  return questions;
}

function parseTurnId(value: unknown, fallbackThreadId: ThreadId): TurnId {
  const record = asRecord(value);
  return TurnId.make(
    trimOrNull(record?.turnId) ??
      trimOrNull(record?.id) ??
      trimOrNull(record?.turn && asRecord(record.turn)?.id) ??
      `${fallbackThreadId}:${randomUUID()}`,
  );
}

function parseItemId(value: unknown): string | undefined {
  const record = asRecord(value);
  return (
    trimOrNull(record?.itemId) ??
    trimOrNull(record?.id) ??
    trimOrNull(record?.item && asRecord(record.item)?.id) ??
    undefined
  );
}

function parseSessionState(value: unknown): ProviderSession["status"] | undefined {
  switch (trimOrNull(value)) {
    case "ready":
      return "ready";
    case "running":
    case "waiting":
      return "running";
    case "error":
      return "error";
    case "stopped":
    case "closed":
      return "closed";
    default:
      return undefined;
  }
}

function buildEventBase(input: {
  readonly threadId: ThreadId;
  readonly turnId?: TurnId | undefined;
  readonly itemId?: string | undefined;
  readonly requestId?: string | undefined;
  readonly raw?: OpenClawGatewayNotification | undefined;
}) {
  return {
    eventId: EventId.make(randomUUID()),
    provider: PROVIDER,
    threadId: input.threadId,
    createdAt: nowIso(),
    ...(input.turnId ? { turnId: input.turnId } : {}),
    ...(input.itemId ? { itemId: RuntimeItemId.make(input.itemId) } : {}),
    ...(input.requestId ? { requestId: RuntimeRequestId.make(input.requestId) } : {}),
    ...(input.raw
      ? {
          raw: {
            source: "openclaw.gateway.notification" as const,
            method: input.raw.method,
            payload: input.raw.params,
          },
        }
      : {}),
  } satisfies Pick<
    ProviderRuntimeEvent,
    "eventId" | "provider" | "threadId" | "createdAt" | "turnId" | "itemId" | "requestId" | "raw"
  >;
}

function buildSessionLifecycleEvents(
  context: OpenClawSessionContext,
): ReadonlyArray<ProviderRuntimeEvent> {
  return [
    {
      ...buildEventBase({
        threadId: context.session.threadId,
      }),
      type: "session.started",
      payload: {
        resume: sessionResumeCursor(context.sessionKey),
      },
    },
    {
      ...buildEventBase({
        threadId: context.session.threadId,
      }),
      type: "session.configured",
      payload: {
        config: {
          key: context.sessionKey,
          ...(context.session.model ? { model: context.session.model } : {}),
        },
      },
    },
  ];
}

function mapNotificationToEvents(
  context: OpenClawSessionContext,
  notification: OpenClawGatewayNotification,
): ReadonlyArray<ProviderRuntimeEvent> {
  const payload = asRecord(notification.params) ?? {};
  const requestId =
    trimOrNull(payload.requestId) ?? trimOrNull(payload.id) ?? trimOrNull(payload.approvalId);
  const itemId = parseItemId(payload);
  const turnId =
    notification.method === "session.started" || notification.method === "session.configured"
      ? undefined
      : parseTurnId(payload, context.session.threadId);

  switch (notification.method) {
    case "session.started":
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            raw: notification,
          }),
          type: "session.started",
          payload: {
            resume: sessionResumeCursor(context.sessionKey),
          },
        },
      ];
    case "session.configured":
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            raw: notification,
          }),
          type: "session.configured",
          payload: {
            config: payload,
          },
        },
      ];
    case "session.state.changed": {
      const nextStatus = parseSessionState(payload.state);
      if (nextStatus) {
        context.session = {
          ...context.session,
          status: nextStatus,
          updatedAt: nowIso(),
          ...(nextStatus === "running" ? { activeTurnId: context.activeTurnId } : {}),
        };
      }

      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            raw: notification,
          }),
          type: "session.state.changed",
          payload: {
            state:
              trimOrNull(payload.state) === "waiting"
                ? "waiting"
                : trimOrNull(payload.state) === "error"
                  ? "error"
                  : trimOrNull(payload.state) === "stopped"
                    ? "stopped"
                    : trimOrNull(payload.state) === "running"
                      ? "running"
                      : "ready",
            ...(trimOrNull(payload.reason) ? { reason: trimOrNull(payload.reason)! } : {}),
            ...(Object.keys(payload).length > 0 ? { detail: payload } : {}),
          },
        },
      ];
    }
    case "session.exited":
      context.stopped = true;
      context.session = {
        ...context.session,
        status: "closed",
        activeTurnId: undefined,
        updatedAt: nowIso(),
      };
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            raw: notification,
          }),
          type: "session.exited",
          payload: {
            ...(trimOrNull(payload.reason) ? { reason: trimOrNull(payload.reason)! } : {}),
            exitKind: trimOrNull(payload.exitKind) === "error" ? "error" : "graceful",
          },
        },
      ];
    case "session.message": {
      const message = asRecord(payload.message) ?? payload;
      const role = getOpenClawMessageRole(message);
      const messageText = getOpenClawMessageText(message);
      const messageTurnId = TurnId.make(
        getOpenClawMessageRunId(message) ??
          trimOrNull(payload.runId) ??
          trimOrNull(payload.messageId) ??
          context.activeTurnId ??
          `${context.session.threadId}:${randomUUID()}`,
      );
      const messageItemId =
        getOpenClawMessageId(message) ??
        trimOrNull(payload.messageId) ??
        `assistant:${messageTurnId}`;

      appendTurnItem(context, messageTurnId, message);

      if (role !== "assistant") {
        return [];
      }

      const events: Array<ProviderRuntimeEvent> = [];
      if (context.activeTurnId !== messageTurnId) {
        context.activeTurnId = messageTurnId;
        context.session = {
          ...context.session,
          status: "running",
          activeTurnId: messageTurnId,
          updatedAt: nowIso(),
        };
        events.push({
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId: messageTurnId,
            raw: notification,
          }),
          type: "turn.started",
          payload: {
            ...(context.session.model ? { model: context.session.model } : {}),
          },
        });
      }

      events.push({
        ...buildEventBase({
          threadId: context.session.threadId,
          turnId: messageTurnId,
          itemId: messageItemId,
          raw: notification,
        }),
        type: "item.started",
        payload: {
          itemType: "assistant_message",
          status: "inProgress",
          title: "Assistant",
          data: message,
        },
      });

      if (messageText.length > 0) {
        events.push({
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId: messageTurnId,
            itemId: messageItemId,
            raw: notification,
          }),
          type: "content.delta",
          payload: {
            streamKind: "assistant_text",
            delta: messageText,
          },
        });
      }

      events.push({
        ...buildEventBase({
          threadId: context.session.threadId,
          turnId: messageTurnId,
          itemId: messageItemId,
          raw: notification,
        }),
        type: "item.completed",
        payload: {
          itemType: "assistant_message",
          status: "completed",
          title: "Assistant",
          data: message,
        },
      });

      context.activeTurnId = undefined;
      context.session = {
        ...context.session,
        status: "ready",
        activeTurnId: undefined,
        updatedAt: nowIso(),
      };
      events.push({
        ...buildEventBase({
          threadId: context.session.threadId,
          turnId: messageTurnId,
          raw: notification,
        }),
        type: "turn.completed",
        payload: {
          state: "completed",
        },
      });

      return events;
    }
    case "turn.started":
      context.activeTurnId = turnId;
      context.session = {
        ...context.session,
        status: "running",
        activeTurnId: turnId,
        updatedAt: nowIso(),
      };
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: notification,
          }),
          type: "turn.started",
          payload: {
            ...(trimOrNull(payload.model) ? { model: trimOrNull(payload.model)! } : {}),
            ...(trimOrNull(payload.effort) ? { effort: trimOrNull(payload.effort)! } : {}),
          },
        },
      ];
    case "turn.completed":
      context.activeTurnId = undefined;
      context.session = {
        ...context.session,
        status: "ready",
        activeTurnId: undefined,
        updatedAt: nowIso(),
      };
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: notification,
          }),
          type: "turn.completed",
          payload: {
            state:
              trimOrNull(payload.state) === "failed"
                ? "failed"
                : trimOrNull(payload.state) === "interrupted"
                  ? "interrupted"
                  : trimOrNull(payload.state) === "cancelled"
                    ? "cancelled"
                    : "completed",
            ...(trimOrNull(payload.stopReason)
              ? { stopReason: trimOrNull(payload.stopReason)! }
              : {}),
            ...(payload.usage !== undefined ? { usage: payload.usage } : {}),
            ...(trimOrNull(payload.errorMessage)
              ? { errorMessage: trimOrNull(payload.errorMessage)! }
              : {}),
          },
        },
      ];
    case "turn.aborted":
      context.activeTurnId = undefined;
      context.session = {
        ...context.session,
        status: "ready",
        activeTurnId: undefined,
        updatedAt: nowIso(),
      };
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: notification,
          }),
          type: "turn.aborted",
          payload: {
            reason: trimOrNull(payload.reason) ?? "aborted",
          },
        },
      ];
    case "thread.token-usage.updated":
      const usageRecord = asRecord(payload.usage);
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: notification,
          }),
          type: "thread.token-usage.updated",
          payload: {
            usage: {
              usedTokens:
                typeof usageRecord?.usedTokens === "number"
                  ? Math.max(0, Math.floor(usageRecord.usedTokens))
                  : 0,
            },
          },
        },
      ];
    case "approval.requested":
    case "request.opened":
      if (requestId) {
        context.pendingApprovals.add(requestId);
      }
      return requestId
        ? [
            {
              ...buildEventBase({
                threadId: context.session.threadId,
                turnId,
                requestId,
                raw: notification,
              }),
              type: "request.opened",
              payload: {
                requestType: toRequestType(payload.requestType ?? payload.requestKind),
                ...(trimOrNull(payload.detail) ? { detail: trimOrNull(payload.detail)! } : {}),
                ...(payload.args !== undefined ? { args: payload.args } : {}),
              },
            },
          ]
        : [];
    case "approval.resolved":
    case "request.resolved":
      if (requestId) {
        context.pendingApprovals.delete(requestId);
      }
      return requestId
        ? [
            {
              ...buildEventBase({
                threadId: context.session.threadId,
                turnId,
                requestId,
                raw: notification,
              }),
              type: "request.resolved",
              payload: {
                requestType: toRequestType(payload.requestType ?? payload.requestKind),
                ...(trimOrNull(payload.decision)
                  ? { decision: trimOrNull(payload.decision)! }
                  : {}),
                ...(payload.resolution !== undefined ? { resolution: payload.resolution } : {}),
              },
            },
          ]
        : [];
    case "user-input.requested":
      if (requestId) {
        context.pendingUserInputs.add(requestId);
      }
      return requestId
        ? [
            {
              ...buildEventBase({
                threadId: context.session.threadId,
                turnId,
                requestId,
                raw: notification,
              }),
              type: "user-input.requested",
              payload: {
                questions: parseQuestions(payload.questions),
              },
            },
          ]
        : [];
    case "user-input.resolved":
      if (requestId) {
        context.pendingUserInputs.delete(requestId);
      }
      return requestId
        ? [
            {
              ...buildEventBase({
                threadId: context.session.threadId,
                turnId,
                requestId,
                raw: notification,
              }),
              type: "user-input.resolved",
              payload: {
                answers: asRecord(payload.answers) ?? {},
              },
            },
          ]
        : [];
    case "item.started":
    case "item.updated":
    case "item.completed": {
      if (turnId) {
        appendTurnItem(context, turnId, payload.item ?? payload);
      }
      return itemId
        ? [
            {
              ...buildEventBase({
                threadId: context.session.threadId,
                turnId,
                itemId,
                raw: notification,
              }),
              type: notification.method,
              payload: {
                itemType: toCanonicalItemType(payload.itemType),
                ...(toRuntimeItemStatus(payload.status)
                  ? { status: toRuntimeItemStatus(payload.status) }
                  : {}),
                ...(trimOrNull(payload.title) ? { title: trimOrNull(payload.title)! } : {}),
                ...(trimOrNull(payload.detail) ? { detail: trimOrNull(payload.detail)! } : {}),
                ...(payload.data !== undefined ? { data: payload.data } : {}),
              },
            },
          ]
        : [];
    }
    case "content.delta":
      return itemId
        ? [
            {
              ...buildEventBase({
                threadId: context.session.threadId,
                turnId,
                itemId,
                raw: notification,
              }),
              type: "content.delta",
              payload: {
                streamKind:
                  trimOrNull(payload.streamKind) === "reasoning_text"
                    ? "reasoning_text"
                    : trimOrNull(payload.streamKind) === "reasoning_summary_text"
                      ? "reasoning_summary_text"
                      : trimOrNull(payload.streamKind) === "plan_text"
                        ? "plan_text"
                        : trimOrNull(payload.streamKind) === "command_output"
                          ? "command_output"
                          : trimOrNull(payload.streamKind) === "file_change_output"
                            ? "file_change_output"
                            : "assistant_text",
                delta: typeof payload.delta === "string" ? payload.delta : "",
                ...(typeof payload.contentIndex === "number"
                  ? { contentIndex: payload.contentIndex }
                  : {}),
              },
            },
          ]
        : [];
    case "runtime.warning":
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: notification,
          }),
          type: "runtime.warning",
          payload: {
            message: trimOrNull(payload.message) ?? "OpenClaw runtime warning",
            ...(Object.keys(payload).length > 0 ? { detail: payload } : {}),
          },
        },
      ];
    case "runtime.error":
      return [
        {
          ...buildEventBase({
            threadId: context.session.threadId,
            turnId,
            raw: notification,
          }),
          type: "runtime.error",
          payload: {
            message: trimOrNull(payload.message) ?? "OpenClaw runtime error",
            ...(toRuntimeErrorClass(payload.class)
              ? { class: toRuntimeErrorClass(payload.class) }
              : {}),
            ...(Object.keys(payload).length > 0 ? { detail: payload } : {}),
          },
        },
      ];
    default:
      return [];
  }
}

const toRequestError = (method: string, error: unknown): ProviderAdapterRequestError =>
  new ProviderAdapterRequestError({
    provider: PROVIDER,
    method,
    detail: getOpenClawGatewayErrorMessage(error, `OpenClaw ${method} failed.`),
    cause: error,
  });

export const makeOpenClawAdapterLive = (_options?: OpenClawAdapterLiveOptions) =>
  Layer.effect(
    OpenClawAdapter,
    Effect.gen(function* () {
      const serverConfig = yield* ServerConfig;
      const serverSettings = yield* ServerSettingsService;
      const layerScope = yield* Effect.scope;
      const runtimeEvents = yield* Effect.acquireRelease(
        PubSub.unbounded<ProviderRuntimeEvent>(),
        PubSub.shutdown,
      );
      const mutationSemaphore = yield* Semaphore.make(1);
      const sessions = new Map<ThreadId, OpenClawSessionContext>();

      const publishEvents = (events: ReadonlyArray<ProviderRuntimeEvent>) =>
        Effect.forEach(events, (event) => PubSub.publish(runtimeEvents, event), {
          discard: true,
        });

      const getContext = (threadId: ThreadId) => {
        const context = sessions.get(threadId);
        if (!context) {
          return Effect.fail(
            new ProviderAdapterSessionNotFoundError({ provider: PROVIDER, threadId }),
          );
        }
        return Effect.succeed(context);
      };

      const stopContext = (context: OpenClawSessionContext) =>
        Effect.promise(() => context.connection.close()).pipe(
          Effect.ignore,
          Effect.andThen(
            Effect.sync(() => {
              context.stopped = true;
              context.session = {
                ...context.session,
                status: "closed",
                activeTurnId: undefined,
                updatedAt: nowIso(),
              };
              sessions.delete(context.session.threadId);
            }),
          ),
        );

      const startNotificationLoop = (context: OpenClawSessionContext) =>
        Effect.promise(async () => {
          try {
            for await (const notification of context.connection.notifications) {
              const events = mapNotificationToEvents(context, notification);
              for (const event of events) {
                await Effect.runPromise(PubSub.publish(runtimeEvents, event));
              }
            }
          } catch (error) {
            if (!context.stopped) {
              await Effect.runPromise(
                publishEvents([
                  {
                    ...buildEventBase({
                      threadId: context.session.threadId,
                    }),
                    type: "runtime.error",
                    payload: {
                      message: getOpenClawGatewayErrorMessage(
                        error,
                        "OpenClaw notification loop failed.",
                      ),
                      class: "transport_error",
                    },
                  },
                ]),
              );
            }
          }
        }).pipe(Effect.forkIn(layerScope));

      const adapter: OpenClawAdapterShape = {
        provider: PROVIDER,
        capabilities: {
          sessionModelSwitch: "in-session",
        },
        startSession: (input) =>
          mutationSemaphore.withPermit(
            Effect.gen(function* () {
              const providerSettings = yield* serverSettings.getSettings.pipe(
                Effect.map((settings) => settings.providers.openclaw),
                Effect.orElseSucceed(() => ({
                  enabled: true,
                  gatewayUrl: "",
                  gatewayToken: "",
                  gatewayPassword: "",
                  customModels: [],
                })),
              );
              const existing = sessions.get(input.threadId);
              if (existing) {
                yield* stopContext(existing);
              }

              const resumed = parseResumeCursor(input.resumeCursor);
              const connection = yield* Effect.promise(() =>
                connectOpenClawGateway({
                  url: providerSettings.gatewayUrl,
                  token: providerSettings.gatewayToken,
                  password: providerSettings.gatewayPassword,
                }),
              ).pipe(Effect.mapError((error) => toRequestError("connect", error)));

              const result = yield* Effect.promise(() =>
                resumed
                  ? connection.call("sessions.resolve", {
                      key: resumed.sessionKey,
                      sessionId: resumed.sessionKey,
                    })
                  : connection.call("sessions.create", {
                      key: String(input.threadId),
                      label: String(input.threadId),
                      ...(input.modelSelection?.model ? { model: input.modelSelection.model } : {}),
                    }),
              ).pipe(
                Effect.mapError((error) =>
                  toRequestError(resumed ? "sessions.resolve" : "sessions.create", error),
                ),
              );
              const resultRecord = asRecord(result) ?? {};
              const sessionKey =
                trimOrNull(resultRecord.key) ??
                trimOrNull(resultRecord.sessionKey) ??
                resumed?.sessionKey ??
                String(input.threadId);

              yield* Effect.promise(() => connection.call("sessions.subscribe", {})).pipe(
                Effect.mapError((error) => toRequestError("sessions.subscribe", error)),
              );
              yield* Effect.promise(() =>
                connection.call("sessions.messages.subscribe", {
                  key: sessionKey,
                }),
              ).pipe(
                Effect.mapError((error) => toRequestError("sessions.messages.subscribe", error)),
              );

              const now = nowIso();
              const session: ProviderSession = {
                provider: PROVIDER,
                status: "ready",
                runtimeMode: input.runtimeMode,
                ...(trimOrNull(input.cwd) ? { cwd: trimOrNull(input.cwd)! } : {}),
                ...(input.modelSelection?.model ? { model: input.modelSelection.model } : {}),
                threadId: input.threadId,
                resumeCursor: sessionResumeCursor(sessionKey),
                createdAt: now,
                updatedAt: now,
              };
              const context: OpenClawSessionContext = {
                session,
                connection,
                sessionKey,
                turns: [],
                pendingApprovals: new Set(),
                pendingUserInputs: new Set(),
                activeTurnId: undefined,
                stopped: false,
              };
              sessions.set(input.threadId, context);
              yield* startNotificationLoop(context);
              yield* publishEvents(buildSessionLifecycleEvents(context));
              return session;
            }),
          ),
        sendTurn: (input) =>
          mutationSemaphore.withPermit(
            Effect.gen(function* () {
              const context = yield* getContext(input.threadId);
              const attachments = yield* Effect.forEach(input.attachments ?? [], (attachment) =>
                Effect.gen(function* () {
                  const path =
                    resolveAttachmentPath({
                      attachmentsDir: serverConfig.attachmentsDir,
                      attachment,
                    }) ?? undefined;
                  return {
                    id: attachment.id,
                    name: attachment.name,
                    mimeType: attachment.mimeType,
                    sizeBytes: attachment.sizeBytes,
                    ...(path ? { path } : {}),
                  };
                }),
              );
              const requestedTurnId = TurnId.make(`${input.threadId}:${randomUUID()}`);
              const result = yield* Effect.promise(() =>
                context.connection.call("sessions.send", {
                  key: context.sessionKey,
                  message: trimOrNull(input.input) ?? "",
                  ...(attachments.length > 0 ? { attachments } : {}),
                  idempotencyKey: requestedTurnId,
                }),
              ).pipe(Effect.mapError((error) => toRequestError("sessions.send", error)));
              const resultRecord = asRecord(result) ?? {};
              const turnId = TurnId.make(
                trimOrNull(resultRecord.runId) ??
                  trimOrNull(resultRecord.turnId) ??
                  trimOrNull(resultRecord.id) ??
                  requestedTurnId,
              );
              context.activeTurnId = turnId;
              context.session = {
                ...context.session,
                status: "running",
                activeTurnId: turnId,
                updatedAt: nowIso(),
                ...(input.modelSelection?.model ? { model: input.modelSelection.model } : {}),
              };
              yield* publishEvents([
                {
                  ...buildEventBase({
                    threadId: context.session.threadId,
                    turnId,
                  }),
                  type: "turn.started",
                  payload: {
                    ...(context.session.model ? { model: context.session.model } : {}),
                  },
                },
              ]);
              return {
                threadId: input.threadId,
                turnId,
                resumeCursor: sessionResumeCursor(context.sessionKey),
              };
            }),
          ),
        interruptTurn: (threadId, turnId) =>
          mutationSemaphore.withPermit(
            Effect.gen(function* () {
              const context = yield* getContext(threadId);
              yield* Effect.promise(() =>
                context.connection.call("sessions.abort", {
                  key: context.sessionKey,
                  ...(turnId ? { runId: turnId } : {}),
                }),
              ).pipe(Effect.mapError((error) => toRequestError("sessions.abort", error)));
            }),
          ),
        respondToRequest: (threadId, requestId, decision) =>
          mutationSemaphore.withPermit(
            Effect.gen(function* () {
              const context = yield* getContext(threadId);
              yield* Effect.promise(() =>
                context.connection.call("approval.respond", {
                  sessionId: context.sessionKey,
                  requestId,
                  decision,
                }),
              ).pipe(Effect.mapError((error) => toRequestError("approval.respond", error)));
              context.pendingApprovals.delete(requestId);
            }),
          ),
        respondToUserInput: (threadId, requestId, answers) =>
          mutationSemaphore.withPermit(
            Effect.gen(function* () {
              const context = yield* getContext(threadId);
              yield* Effect.promise(() =>
                context.connection.call("user-input.respond", {
                  sessionId: context.sessionKey,
                  requestId,
                  answers,
                }),
              ).pipe(Effect.mapError((error) => toRequestError("user-input.respond", error)));
              context.pendingUserInputs.delete(requestId);
            }),
          ),
        stopSession: (threadId) =>
          mutationSemaphore.withPermit(
            Effect.gen(function* () {
              const context = yield* getContext(threadId);
              if (context.activeTurnId) {
                yield* Effect.promise(() =>
                  context.connection.call("sessions.abort", {
                    key: context.sessionKey,
                    runId: context.activeTurnId,
                  }),
                ).pipe(
                  Effect.mapError((error) => toRequestError("sessions.abort", error)),
                  Effect.ignore,
                );
              }
              yield* stopContext(context);
            }),
          ),
        listSessions: () =>
          Effect.sync(() =>
            [...sessions.values()]
              .filter((context) => !context.stopped)
              .map((context) => context.session),
          ),
        hasSession: (threadId) => Effect.sync(() => sessions.has(threadId)),
        readThread: (threadId) =>
          Effect.gen(function* () {
            const context = yield* getContext(threadId);
            return {
              threadId,
              turns: context.turns.map((turn) => ({
                id: turn.id,
                items: [...turn.items],
              })),
            };
          }),
        rollbackThread: (threadId, numTurns) =>
          Effect.gen(function* () {
            if (numTurns === 0) {
              return yield* adapter.readThread(threadId);
            }
            return yield* toRequestError(
              "session.rollback",
              new Error("OpenClaw gateway rollback is not implemented."),
            );
          }),
        stopAll: () =>
          mutationSemaphore.withPermit(
            Effect.forEach([...sessions.values()], (context) => stopContext(context), {
              discard: true,
            }),
          ),
        get streamEvents() {
          return Stream.fromPubSub(runtimeEvents);
        },
      };

      yield* Effect.addFinalizer(() =>
        Effect.forEach([...sessions.values()], (context) => stopContext(context), {
          discard: true,
        }),
      );

      return adapter;
    }),
  );
