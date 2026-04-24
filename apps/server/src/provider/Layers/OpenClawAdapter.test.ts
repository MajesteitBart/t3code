import assert from "node:assert/strict";

import * as NodeServices from "@effect/platform-node/NodeServices";
import { it } from "@effect/vitest";
import { Effect, Fiber, Layer, Option, Stream } from "effect";

import { ApprovalRequestId, ThreadId } from "@t3tools/contracts";
import { ServerConfig } from "../../config.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import { ProviderSessionDirectory } from "../Services/ProviderSessionDirectory.ts";
import { OpenClawAdapter } from "../Services/OpenClawAdapter.ts";
import {
  startMockOpenClawGateway,
  type MockOpenClawGatewayOptions,
  type MockOpenClawGatewayServer,
} from "../openclawGatewayTestServer.ts";
import { makeOpenClawAdapterLive } from "./OpenClawAdapter.ts";

const withGateway = <A, E = never, R = never>(
  effect: (gateway: MockOpenClawGatewayServer) => Effect.Effect<A, E, R>,
  options?: MockOpenClawGatewayOptions,
) =>
  Effect.acquireUseRelease(
    Effect.promise(() => startMockOpenClawGateway(options)),
    effect,
    (gateway) => Effect.sync(() => gateway.stop()),
  );

const providerSessionDirectoryTestLayer = Layer.succeed(ProviderSessionDirectory, {
  upsert: () => Effect.void,
  getProvider: () =>
    Effect.die(new Error("ProviderSessionDirectory.getProvider is not used in test")),
  getBinding: () => Effect.succeed(Option.none()),
  listThreadIds: () => Effect.succeed([]),
  listBindings: () => Effect.succeed([]),
});

const OpenClawAdapterTestLayer = makeOpenClawAdapterLive().pipe(
  Layer.provideMerge(ServerConfig.layerTest(process.cwd(), process.cwd())),
  Layer.provideMerge(ServerSettingsService.layerTest()),
  Layer.provideMerge(providerSessionDirectoryTestLayer),
  Layer.provideMerge(NodeServices.layer),
);

it.layer(OpenClawAdapterTestLayer)("OpenClawAdapterLive", (it) => {
  it.effect(
    "starts a session and maps gateway turn notifications to canonical runtime events",
    () =>
      withGateway((gateway) =>
        Effect.gen(function* () {
          const settings = yield* ServerSettingsService;
          yield* settings.updateSettings({
            providers: {
              openclaw: {
                gatewayUrl: gateway.url,
              },
            },
          });

          const adapter = yield* OpenClawAdapter;
          const threadId = ThreadId.make("openclaw-thread-1");
          const runtimeEventsFiber = yield* Stream.take(adapter.streamEvents, 7).pipe(
            Stream.runCollect,
            Effect.forkChild,
          );

          const session = yield* adapter.startSession({
            provider: "openclaw",
            threadId,
            runtimeMode: "full-access",
            modelSelection: {
              provider: "openclaw",
              model: "openai-codex/gpt-5.4",
            },
          });

          yield* adapter.sendTurn({
            threadId,
            input: "hello openclaw",
          });

          const events = Array.from(yield* Fiber.join(runtimeEventsFiber));
          assert.equal(session.provider, "openclaw");
          assert.equal(session.threadId, threadId);
          assert.equal(
            events.some((event) => event.type === "session.started"),
            true,
          );
          assert.equal(
            events.some((event) => event.type === "turn.started"),
            true,
          );
          assert.equal(
            events.some((event) => event.type === "content.delta"),
            true,
          );
          assert.equal(
            events.some((event) => event.type === "turn.completed"),
            true,
          );
          assert.deepEqual(
            gateway.calls.map((call) => call.method),
            [
              "connect",
              "sessions.create",
              "sessions.subscribe",
              "sessions.messages.subscribe",
              "sessions.send",
            ],
          );
        }),
      ),
  );

  it.effect("responds to approval and structured user-input requests", () =>
    withGateway(
      (gateway) =>
        Effect.gen(function* () {
          const settings = yield* ServerSettingsService;
          yield* settings.updateSettings({
            providers: {
              openclaw: {
                gatewayUrl: gateway.url,
              },
            },
          });

          const adapter = yield* OpenClawAdapter;
          const threadId = ThreadId.make("openclaw-thread-2");
          const runtimeEventsFiber = yield* Stream.take(adapter.streamEvents, 7).pipe(
            Stream.runCollect,
            Effect.forkChild,
          );

          yield* adapter.startSession({
            provider: "openclaw",
            threadId,
            runtimeMode: "approval-required",
          });
          yield* adapter.sendTurn({
            threadId,
            input: "needs approval",
          });
          yield* adapter.respondToRequest(threadId, ApprovalRequestId.make("approval-1"), "accept");
          yield* adapter.respondToUserInput(threadId, ApprovalRequestId.make("input-1"), {
            mode: "yes",
          });

          const events = Array.from(yield* Fiber.join(runtimeEventsFiber));
          assert.equal(
            events.some((event) => event.type === "request.opened"),
            true,
          );
          assert.equal(
            events.some((event) => event.type === "request.resolved"),
            true,
          );
          assert.equal(
            events.some((event) => event.type === "user-input.requested"),
            true,
          );
          assert.equal(
            events.some((event) => event.type === "user-input.resolved"),
            true,
          );
        }),
      {
        handleRequest(request) {
          if (request.method === "sessions.send") {
            return {
              payload: { runId: "turn-openclaw-approval", status: "accepted" },
              events: [
                {
                  event: "approval.requested",
                  payload: {
                    turnId: "turn-openclaw-approval",
                    requestId: "approval-1",
                    requestKind: "command",
                    detail: "bun lint",
                  },
                },
                {
                  event: "user-input.requested",
                  payload: {
                    turnId: "turn-openclaw-approval",
                    requestId: "input-1",
                    questions: [
                      {
                        id: "mode",
                        header: "Mode",
                        question: "Continue?",
                        options: [{ label: "yes", description: "Continue execution" }],
                      },
                    ],
                  },
                },
              ],
            };
          }
          return undefined;
        },
      },
    ),
  );
});
