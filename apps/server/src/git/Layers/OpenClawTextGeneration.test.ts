import assert from "node:assert/strict";

import * as NodeServices from "@effect/platform-node/NodeServices";
import { it } from "@effect/vitest";
import { Effect, Layer } from "effect";
import { afterEach, beforeEach } from "vitest";

import { ServerConfig } from "../../config.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import {
  startMockOpenClawGateway,
  type MockOpenClawGatewayServer,
} from "../../provider/openclawGatewayTestServer.ts";
import { TextGeneration } from "../Services/TextGeneration.ts";
import { OpenClawTextGenerationLive } from "./OpenClawTextGeneration.ts";

let gateway: MockOpenClawGatewayServer | null = null;

beforeEach(async () => {
  gateway = await startMockOpenClawGateway({
    handleRequest(request) {
      if (request.method === "session.sendTurn") {
        return {
          result: { turnId: "turn-text-generation" },
          notifications: [
            {
              method: "content.delta",
              params: {
                turnId: "turn-text-generation",
                itemId: "assistant:turn-text-generation",
                streamKind: "assistant_text",
                delta: JSON.stringify({
                  subject: "Improve OpenClaw support",
                  body: "Wire the gateway-backed provider into the monorepo.",
                }),
              },
            },
            {
              method: "turn.completed",
              params: {
                turnId: "turn-text-generation",
                state: "completed",
              },
            },
          ],
        };
      }
      return undefined;
    },
  });
});

afterEach(() => {
  gateway?.stop();
  gateway = null;
});

const OpenClawTextGenerationTestLayer = OpenClawTextGenerationLive.pipe(
  Layer.provideMerge(ServerConfig.layerTest(process.cwd(), process.cwd())),
  Layer.provideMerge(ServerSettingsService.layerTest()),
  Layer.provideMerge(NodeServices.layer),
);

it.layer(OpenClawTextGenerationTestLayer)("OpenClawTextGenerationLive", (it) => {
  it.effect("generates commit messages through the OpenClaw gateway", () =>
    Effect.gen(function* () {
      if (!gateway) {
        throw new Error("gateway not started");
      }
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
    }),
  );
});
