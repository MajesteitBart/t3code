import { describe, expect, it } from "@effect/vitest";
import * as NodeServices from "@effect/platform-node/NodeServices";
import {
  ApprovalRequestId,
  EnvironmentId,
  PiSettings,
  ProviderInstanceId,
  type ProviderRuntimeEvent,
  ThreadId,
} from "@t3tools/contracts";
import * as Cause from "effect/Cause";
import * as Deferred from "effect/Deferred";
import * as Effect from "effect/Effect";
import * as Exit from "effect/Exit";
import * as Fiber from "effect/Fiber";
import * as FileSystem from "effect/FileSystem";
import * as Layer from "effect/Layer";
import * as Queue from "effect/Queue";
import * as Schema from "effect/Schema";
import * as Stream from "effect/Stream";

import * as ServerConfig from "../../config.ts";
import { makePiAdapter } from "./PiAdapter.ts";
import {
  PiRuntime,
  type PiRpcCommand,
  type PiRpcEvent,
  type PiRpcResponse,
  PiRuntimeError,
  type PiRuntimeShape,
} from "../piRpcRuntime.ts";
import * as McpProviderSession from "../../mcp/McpProviderSession.ts";

const decodePiSettings = Schema.decodeSync(PiSettings);
const decodeUnknownJsonString = Schema.decodeUnknownSync(Schema.UnknownFromJsonString);

function makeRuntimeLayer(input: {
  readonly eventQueue: Queue.Queue<PiRpcEvent>;
  readonly commands: Array<PiRpcCommand>;
  readonly starts?: Array<{
    readonly args?: ReadonlyArray<string>;
    readonly environment?: NodeJS.ProcessEnv;
    readonly cwd?: string;
  }>;
  readonly startError?: PiRuntimeError;
  readonly request?: (command: PiRpcCommand) => Effect.Effect<PiRpcResponse, PiRuntimeError, never>;
}): Layer.Layer<PiRuntime, never, never> {
  const runtime: PiRuntimeShape = {
    runPiCommand: () => Effect.succeed({ stdout: "0.79.10\n", stderr: "", code: 0 }),
    startPiRpcSession: (startInput) => {
      input.starts?.push({
        ...(startInput.args ? { args: startInput.args } : {}),
        ...(startInput.environment ? { environment: startInput.environment } : {}),
        ...(startInput.cwd ? { cwd: startInput.cwd } : {}),
      });
      return input.startError
        ? Effect.fail(input.startError)
        : Effect.succeed({
            pid: 42,
            events: Stream.fromQueue(input.eventQueue),
            exitCode: Effect.never,
            request: (command) => {
              input.commands.push(command);
              return (
                input.request?.(command) ??
                Effect.succeed({
                  ...(command.id ? { id: command.id } : {}),
                  type: "response",
                  command: command.type,
                  success: true,
                })
              );
            },
            send: (command) =>
              Effect.sync(() => {
                input.commands.push(command);
              }),
            stop: Effect.void,
          });
    },
  };
  return Layer.succeed(PiRuntime, runtime);
}

function testLayer(
  eventQueue: Queue.Queue<PiRpcEvent>,
  commands: Array<PiRpcCommand>,
  options?: {
    readonly starts?: Array<{
      readonly args?: ReadonlyArray<string>;
      readonly environment?: NodeJS.ProcessEnv;
      readonly cwd?: string;
    }>;
    readonly startError?: PiRuntimeError;
    readonly request?: (
      command: PiRpcCommand,
    ) => Effect.Effect<PiRpcResponse, PiRuntimeError, never>;
  },
) {
  return ServerConfig.layerTest(process.cwd(), { prefix: "pi-adapter-test" }).pipe(
    Layer.provideMerge(NodeServices.layer),
    Layer.provideMerge(makeRuntimeLayer({ eventQueue, commands, ...options })),
  );
}

describe("PiAdapter", () => {
  it.effect("does not retain session state when Pi startup fails", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        const startError = new PiRuntimeError({
          operation: "startPiRpcSession",
          detail: "Pi binary failed to start.",
        });

        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const threadId = ThreadId.make("pi-thread-start-fails");
          const result = yield* adapter
            .startSession({ threadId, runtimeMode: "auto-accept-edits" })
            .pipe(Effect.exit);

          expect(Exit.isFailure(result)).toBe(true);
          expect(yield* adapter.hasSession(threadId)).toBe(false);
          expect(yield* adapter.listSessions()).toEqual([]);
          expect(commands).toEqual([]);
        }).pipe(Effect.provide(testLayer(eventQueue, commands, { startError })));
      }),
    ),
  );

  it.effect("maps text deltas and completes only on agent_end", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const eventsFiber = yield* Stream.take(adapter.streamEvents, 5).pipe(
            Stream.runCollect,
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-text");
          yield* adapter.startSession({
            threadId,
            runtimeMode: "auto-accept-edits",
            modelSelection: {
              instanceId: ProviderInstanceId.make("pi"),
              model: "baseten/zai-org/GLM-5.2",
              options: [{ id: "thinking", value: "high" }],
            },
          });
          yield* adapter.sendTurn({
            threadId,
            input: "Reply exactly OK.",
            attachments: [],
            modelSelection: {
              instanceId: ProviderInstanceId.make("pi"),
              model: "baseten/zai-org/GLM-5.2",
              options: [{ id: "thinking", value: "high" }],
            },
          });
          yield* Queue.offer(eventQueue, {
            type: "message_update",
            assistantMessageEvent: { type: "text_delta", contentIndex: 0, delta: "OK" },
          });
          yield* Queue.offer(eventQueue, { type: "turn_end", message: { content: [] } });
          yield* Queue.offer(eventQueue, { type: "agent_end", messages: [] });
          yield* Queue.offer(eventQueue, { type: "agent_end", messages: [] });

          const events = Array.from(yield* Fiber.join(eventsFiber));
          expect(events.map((event) => event.type)).toEqual([
            "session.started",
            "thread.started",
            "turn.started",
            "content.delta",
            "turn.completed",
          ]);
          expect(events.filter((event) => event.type === "turn.completed")).toHaveLength(1);
          expect(commands.map((command) => command.type)).toEqual([
            "set_model",
            "set_thinking_level",
            "prompt",
          ]);
        }).pipe(Effect.provide(testLayer(eventQueue, commands)));
      }),
    ),
  );

  it.effect("resets active turn state when prompt send fails", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const eventsFiber = yield* Stream.take(adapter.streamEvents, 5).pipe(
            Stream.runCollect,
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-prompt-fails");
          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
          const result = yield* adapter
            .sendTurn({
              threadId,
              input: "This prompt will fail.",
              attachments: [],
            })
            .pipe(Effect.exit);

          const events = Array.from(yield* Fiber.join(eventsFiber));
          const session = (yield* adapter.listSessions())[0];
          expect(Exit.isFailure(result)).toBe(true);
          expect(events.map((event) => event.type)).toEqual([
            "session.started",
            "thread.started",
            "turn.started",
            "turn.completed",
            "turn.aborted",
          ]);
          expect(session?.status).toBe("ready");
          expect(session?.activeTurnId).toBeUndefined();
          expect(session?.lastError).toBe("Prompt rejected.");
          expect(commands.map((command) => command.type)).toEqual(["prompt"]);
        }).pipe(
          Effect.provide(
            testLayer(eventQueue, commands, {
              request: (command) =>
                command.type === "prompt"
                  ? Effect.succeed({
                      type: "response",
                      command: command.type,
                      success: false,
                      error: "Prompt rejected.",
                    })
                  : Effect.succeed({
                      type: "response",
                      command: command.type,
                      success: true,
                    }),
            }),
          ),
        );
      }),
    ),
  );

  it.effect(
    "queues active Pi prompts as follow-ups without clearing the active turn on failure",
    () =>
      Effect.scoped(
        Effect.gen(function* () {
          const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
          const commands: Array<PiRpcCommand> = [];
          yield* Effect.gen(function* () {
            const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
              instanceId: ProviderInstanceId.make("pi"),
            });
            const eventsFiber = yield* Stream.take(adapter.streamEvents, 3).pipe(
              Stream.runCollect,
              Effect.forkScoped,
            );

            const threadId = ThreadId.make("pi-thread-active-follow-up");
            yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
            const firstTurn = yield* adapter.sendTurn({
              threadId,
              input: "Start a long task.",
              attachments: [],
            });
            const secondTurnExit = yield* adapter
              .sendTurn({
                threadId,
                input: "Queue this next.",
                attachments: [],
              })
              .pipe(Effect.exit);

            const events = Array.from(yield* Fiber.join(eventsFiber));
            const session = (yield* adapter.listSessions())[0];
            expect(Exit.isFailure(secondTurnExit)).toBe(true);
            expect(events.map((event) => event.type)).toEqual([
              "session.started",
              "thread.started",
              "turn.started",
            ]);
            expect(session?.status).toBe("running");
            expect(session?.activeTurnId).toBe(firstTurn.turnId);
            expect(commands).toMatchObject([
              { type: "prompt", message: "Start a long task." },
              {
                type: "prompt",
                message: "Queue this next.",
                streamingBehavior: "followUp",
              },
            ]);
          }).pipe(
            Effect.provide(
              testLayer(eventQueue, commands, {
                request: (command) =>
                  command.type === "prompt" && command.streamingBehavior === "followUp"
                    ? Effect.succeed({
                        type: "response",
                        command: command.type,
                        success: false,
                        error: "Follow-up rejected.",
                      })
                    : Effect.succeed({
                        type: "response",
                        command: command.type,
                        success: true,
                      }),
              }),
            ),
          );
        }),
      ),
  );

  it.effect("rejects attachment payloads before prompting Pi", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });

          const threadId = ThreadId.make("pi-thread-attachments");
          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
          const result = yield* adapter
            .sendTurn({
              threadId,
              input: "Describe this image.",
              attachments: [
                {
                  type: "image",
                  id: "screen",
                  name: "screen.png",
                  mimeType: "image/png",
                  sizeBytes: 42,
                },
              ],
            })
            .pipe(Effect.exit);

          expect(Exit.isFailure(result)).toBe(true);
          if (Exit.isFailure(result)) {
            const error = Cause.squash(result.cause);
            expect(error).toMatchObject({
              _tag: "ProviderAdapterValidationError",
              issue: "Pi provider does not support T3 Code attachments in v1.",
            });
          }
          expect(commands).toEqual([]);
        }).pipe(Effect.provide(testLayer(eventQueue, commands)));
      }),
    ),
  );

  it.effect("keeps the prior session model when an in-session model switch fails", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const eventsFiber = yield* Stream.take(adapter.streamEvents, 2).pipe(
            Stream.runCollect,
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-model-switch-fails");
          yield* adapter.startSession({
            threadId,
            runtimeMode: "auto-accept-edits",
            modelSelection: {
              instanceId: ProviderInstanceId.make("pi"),
              model: "baseten/zai-org/GLM-5.2",
            },
          });
          const result = yield* adapter
            .sendTurn({
              threadId,
              input: "Use the next model.",
              attachments: [],
              modelSelection: {
                instanceId: ProviderInstanceId.make("pi"),
                model: "openrouter/openai/gpt-5.4",
              },
            })
            .pipe(Effect.exit);

          const events = Array.from(yield* Fiber.join(eventsFiber));
          const session = (yield* adapter.listSessions())[0];
          expect(Exit.isFailure(result)).toBe(true);
          expect(events.map((event) => event.type)).toEqual(["session.started", "thread.started"]);
          expect(session?.status).toBe("ready");
          expect(session?.activeTurnId).toBeUndefined();
          expect(session?.model).toBe("baseten/zai-org/GLM-5.2");
          expect(commands.map((command) => command.type)).toEqual(["set_model"]);
        }).pipe(
          Effect.provide(
            testLayer(eventQueue, commands, {
              request: (command) =>
                command.type === "set_model"
                  ? Effect.succeed({
                      type: "response",
                      command: command.type,
                      success: false,
                      error: "Model switch rejected.",
                    })
                  : Effect.succeed({
                      type: "response",
                      command: command.type,
                      success: true,
                    }),
            }),
          ),
        );
      }),
    ),
  );

  it.effect("wires T3 preview MCP through pi-mcp-adapter config", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        const starts: Array<{
          readonly args?: ReadonlyArray<string>;
          readonly environment?: NodeJS.ProcessEnv;
          readonly cwd?: string;
        }> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const eventsFiber = yield* Stream.take(adapter.streamEvents, 2).pipe(
            Stream.runCollect,
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-mcp-adapter");
          yield* Effect.acquireRelease(
            Effect.sync(() =>
              McpProviderSession.setMcpProviderSession({
                environmentId: EnvironmentId.make("env-pi-mcp"),
                threadId,
                providerSessionId: "provider-session-pi-mcp",
                providerInstanceId: ProviderInstanceId.make("pi"),
                endpoint: "http://127.0.0.1:12345/mcp",
                authorizationHeader: "Bearer test-token",
              }),
            ),
            () => Effect.sync(() => McpProviderSession.clearMcpProviderSession(threadId)),
          );

          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });

          const events = Array.from(yield* Fiber.join(eventsFiber));
          expect(events.map((event) => event.type)).toEqual(["session.started", "thread.started"]);
          expect(commands).toEqual([]);

          const start = starts[0];
          expect(start).toBeDefined();
          const args = [...(start?.args ?? [])];
          const mcpConfigIndex = args.indexOf("--mcp-config");
          const systemPromptIndex = args.indexOf("--append-system-prompt");
          expect(mcpConfigIndex).toBeGreaterThanOrEqual(0);
          expect(systemPromptIndex).toBeGreaterThanOrEqual(0);
          expect(args[systemPromptIndex + 1]).toContain('mcp({ search: "preview" })');
          expect(start?.environment?.T3_MCP_BEARER_TOKEN).toBe("test-token");

          const configPath = args[mcpConfigIndex + 1];
          expect(configPath).toBeTruthy();
          if (!configPath) {
            throw new Error("Expected Pi MCP config path.");
          }
          const fileSystem = yield* FileSystem.FileSystem;
          const configText = yield* fileSystem.readFileString(configPath);
          expect(configText).not.toContain("test-token");
          expect(decodeUnknownJsonString(configText)).toMatchObject({
            settings: {
              toolPrefix: "none",
              disableProxyTool: false,
              directTools: false,
            },
            mcpServers: {
              "t3-code": {
                url: "http://127.0.0.1:12345/mcp",
                auth: "bearer",
                bearerTokenEnv: "T3_MCP_BEARER_TOKEN",
                lifecycle: "lazy",
                exposeResources: false,
              },
            },
          });
        }).pipe(Effect.provide(testLayer(eventQueue, commands, { starts })));
      }),
    ),
  );

  it.effect("normalizes Pi tool args and results for detailed timeline rows", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const eventsFiber = yield* Stream.take(adapter.streamEvents, 5).pipe(
            Stream.runCollect,
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-tool-details");
          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
          yield* adapter.sendTurn({
            threadId,
            input: "Run a command.",
            attachments: [],
          });
          yield* Queue.offer(eventQueue, {
            type: "tool_execution_start",
            toolCallId: "tool-bash-1",
            toolName: "Bash",
            args: {
              command: "pnpm exec vp check",
            },
          });
          yield* Queue.offer(eventQueue, {
            type: "tool_execution_end",
            toolCallId: "tool-bash-1",
            toolName: "Bash",
            result: {
              content: [{ type: "text", text: "All files are correctly formatted." }],
              details: { exitCode: 0 },
            },
            isError: false,
          });

          const events = Array.from(yield* Fiber.join(eventsFiber));
          const completed = events.find((event) => event.type === "item.completed");
          expect(completed?.payload).toMatchObject({
            itemType: "command_execution",
            status: "completed",
            title: "Bash",
            data: {
              toolCallId: "tool-bash-1",
              kind: "bash",
              command: "pnpm exec vp check",
              rawInput: {
                command: "pnpm exec vp check",
              },
              rawOutput: {
                details: { exitCode: 0 },
              },
            },
          });
        }).pipe(Effect.provide(testLayer(eventQueue, commands)));
      }),
    ),
  );

  it.effect("routes Pi extension input requests through user-input responses", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const requested =
            yield* Deferred.make<Extract<ProviderRuntimeEvent, { type: "user-input.requested" }>>();
          const resolved =
            yield* Deferred.make<Extract<ProviderRuntimeEvent, { type: "user-input.resolved" }>>();
          yield* adapter.streamEvents.pipe(
            Stream.runForEach((event) => {
              if (event.type === "user-input.requested") {
                return Deferred.succeed(requested, event).pipe(Effect.ignore);
              }
              if (event.type === "user-input.resolved") {
                return Deferred.succeed(resolved, event).pipe(Effect.ignore);
              }
              return Effect.void;
            }),
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-extension-input");
          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
          yield* adapter.sendTurn({
            threadId,
            input: "Ask me something.",
            attachments: [],
          });
          yield* Queue.offer(eventQueue, {
            type: "extension_ui_request",
            id: "ui-input-1",
            method: "input",
            title: "What should Pi do next?",
            placeholder: "Type your answer",
          });

          const requestedEvent = yield* Deferred.await(requested);
          expect(requestedEvent.requestId).toBe("ui-input-1");
          expect(requestedEvent.payload.questions).toEqual([
            {
              id: "input:ui-input-1",
              header: "Question",
              question: "What should Pi do next?",
              options: [],
              multiSelect: false,
            },
          ]);

          yield* adapter.respondToUserInput(threadId, ApprovalRequestId.make("ui-input-1"), {
            "input:ui-input-1": "Use the browser preview.",
          });

          const resolvedEvent = yield* Deferred.await(resolved);
          expect(resolvedEvent.requestId).toBe("ui-input-1");
          expect(resolvedEvent.payload.answers).toEqual({
            "input:ui-input-1": "Use the browser preview.",
          });
          expect(commands.at(-1)).toEqual({
            type: "extension_ui_response",
            id: "ui-input-1",
            value: "Use the browser preview.",
          });
        }).pipe(Effect.provide(testLayer(eventQueue, commands)));
      }),
    ),
  );

  it.effect("routes Pi extension confirm requests as boolean responses", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const requested =
            yield* Deferred.make<Extract<ProviderRuntimeEvent, { type: "user-input.requested" }>>();
          yield* adapter.streamEvents.pipe(
            Stream.runForEach((event) =>
              event.type === "user-input.requested"
                ? Deferred.succeed(requested, event).pipe(Effect.ignore)
                : Effect.void,
            ),
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-extension-confirm");
          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
          yield* adapter.sendTurn({
            threadId,
            input: "Confirm something.",
            attachments: [],
          });
          yield* Queue.offer(eventQueue, {
            type: "extension_ui_request",
            id: "ui-confirm-1",
            method: "confirm",
            title: "Continue with this plan?",
            message: "Continue with this plan?",
          });

          yield* Deferred.await(requested);
          yield* adapter.respondToUserInput(threadId, ApprovalRequestId.make("ui-confirm-1"), {
            "confirm:ui-confirm-1": "Yes",
          });

          expect(commands.at(-1)).toEqual({
            type: "extension_ui_response",
            id: "ui-confirm-1",
            confirmed: true,
          });
        }).pipe(Effect.provide(testLayer(eventQueue, commands)));
      }),
    ),
  );

  it.effect("suppresses empty intermediate message updates", () =>
    Effect.scoped(
      Effect.gen(function* () {
        const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
        const commands: Array<PiRpcCommand> = [];
        yield* Effect.gen(function* () {
          const adapter = yield* makePiAdapter(decodePiSettings({ enabled: true }), {
            instanceId: ProviderInstanceId.make("pi"),
          });
          const eventsFiber = yield* Stream.take(adapter.streamEvents, 4).pipe(
            Stream.runCollect,
            Effect.forkScoped,
          );

          const threadId = ThreadId.make("pi-thread-empty");
          yield* adapter.startSession({ threadId, runtimeMode: "auto-accept-edits" });
          yield* adapter.sendTurn({
            threadId,
            input: "Do work.",
            attachments: [],
          });
          yield* Queue.offer(eventQueue, {
            type: "extension_ui_request",
            id: "status-1",
            method: "setStatus",
            statusKey: "mcp",
            statusText: "MCP: 1/1 servers",
          });
          yield* Queue.offer(eventQueue, {
            type: "message_update",
            assistantMessageEvent: { type: "text_delta", contentIndex: 0, delta: "" },
          });
          yield* Queue.offer(eventQueue, {
            type: "tool_execution_start",
            toolCallId: "tool-1",
            toolName: "bash",
          });
          yield* Queue.offer(eventQueue, { type: "agent_end", messages: [] });

          const events = Array.from(yield* Fiber.join(eventsFiber));
          expect(events.map((event) => event.type)).toEqual([
            "session.started",
            "thread.started",
            "turn.started",
            "item.started",
          ]);
          expect(events.some((event) => event.type === "content.delta")).toBe(false);
          expect(commands.map((command) => command.type)).toEqual(["prompt"]);
        }).pipe(Effect.provide(testLayer(eventQueue, commands)));
      }),
    ),
  );
});
