import assert from "node:assert/strict";

import * as NodeServices from "@effect/platform-node/NodeServices";
import { it } from "@effect/vitest";
import { Effect, Layer } from "effect";

import { ServerConfig } from "../../config.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import {
  startMockOpenClawGateway,
  type MockOpenClawGatewayServer,
} from "../../provider/openclawGatewayTestServer.ts";
import { TextGeneration } from "../Services/TextGeneration.ts";
import { OpenClawTextGenerationLive } from "./OpenClawTextGeneration.ts";

const withGateway = <A, E = never, R = never>(
  effect: (gateway: MockOpenClawGatewayServer) => Effect.Effect<A, E, R>,
) =>
  Effect.acquireUseRelease(
    Effect.promise(() =>
      startMockOpenClawGateway({
        handleRequest(request) {
          if (request.method === "sessions.send") {
            return {
              payload: { runId: "turn-text-generation", status: "accepted" },
              events: [
                {
                  event: "session.message",
                  payload: {
                    sessionKey: "openclaw-thread-generation",
                    messageId: "assistant:turn-text-generation",
                    message: {
                      id: "assistant:turn-text-generation",
                      role: "assistant",
                      runId: "turn-text-generation",
                      content: [
                        {
                          type: "text",
                          text: JSON.stringify({
                            subject: "Improve OpenClaw support",
                            body: "Wire the gateway-backed provider into the monorepo.",
                          }),
                        },
                      ],
                    },
                  },
                },
              ],
            };
          }
          if (request.method === "sessions.create") {
            return {
              payload: {
                ok: true,
                key: "openclaw-thread-generation",
                sessionId: "session-text-generation",
              },
            };
          }
          return undefined;
        },
      }),
    ),
    effect,
    (gateway) => Effect.sync(() => gateway.stop()),
  );

const OpenClawTextGenerationTestLayer = OpenClawTextGenerationLive.pipe(
  Layer.provideMerge(ServerConfig.layerTest(process.cwd(), process.cwd())),
  Layer.provideMerge(ServerSettingsService.layerTest()),
  Layer.provideMerge(NodeServices.layer),
);

it.layer(OpenClawTextGenerationTestLayer)("OpenClawTextGenerationLive", (it) => {
  it.effect("generates commit messages through the OpenClaw gateway", () =>
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

        const textGeneration = yield* TextGeneration;
        const result = yield* textGeneration.generateCommitMessage({
          cwd: process.cwd(),
          branch: "feature/openclaw",
          stagedSummary: "M apps/server/src/provider/Layers/OpenClawAdapter.ts",
          stagedPatch:
            "diff --git a/apps/server/src/provider/Layers/OpenClawAdapter.ts b/apps/server/src/provider/Layers/OpenClawAdapter.ts",
          modelSelection: {
            provider: "openclaw",
            model: "openai-codex/gpt-5.4",
          },
        });

        assert.equal(result.subject, "Improve OpenClaw support");
        assert.equal(result.body, "Wire the gateway-backed provider into the monorepo.");
        assert.deepEqual(
          gateway.calls.map((call) => call.method),
          ["connect", "sessions.create", "sessions.messages.subscribe", "sessions.send"],
        );
      }),
    ),
  );
});
