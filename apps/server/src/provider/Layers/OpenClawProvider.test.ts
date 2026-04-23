import assert from "node:assert/strict";

import * as NodeServices from "@effect/platform-node/NodeServices";
import { it } from "@effect/vitest";
import { Effect, Layer } from "effect";
import { afterEach, beforeEach } from "vitest";

import { ServerConfig } from "../../config.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import { OpenClawProvider } from "../Services/OpenClawProvider.ts";
import {
  startMockOpenClawGateway,
  type MockOpenClawGatewayServer,
} from "../openclawGatewayTestServer.ts";
import { OpenClawProviderLive } from "./OpenClawProvider.ts";

let gateway: MockOpenClawGatewayServer | null = null;

beforeEach(async () => {
  gateway = await startMockOpenClawGateway();
});

afterEach(() => {
  gateway?.stop();
  gateway = null;
});

const makeTestLayer = (settingsOverrides?: Parameters<typeof ServerSettingsService.layerTest>[0]) =>
  OpenClawProviderLive.pipe(
    Layer.provideMerge(ServerConfig.layerTest(process.cwd(), process.cwd())),
    Layer.provideMerge(ServerSettingsService.layerTest(settingsOverrides)),
    Layer.provideMerge(NodeServices.layer),
  );

it.layer(makeTestLayer())("OpenClawProviderLive", (it) => {
  it.effect("loads provider status and model catalog from the gateway", () =>
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

      const provider = yield* OpenClawProvider;
      const snapshot = yield* provider.refresh;

      assert.equal(snapshot.provider, "openclaw");
      assert.equal(snapshot.status, "ready");
      assert.equal(snapshot.installed, true);
      assert.equal(snapshot.auth.status, "authenticated");
      assert.equal(snapshot.version, "2026.4.5");
      assert.deepEqual(
        snapshot.models.map((model) => model.slug),
        ["openai-codex/gpt-5.4", "openai/gpt-5-mini"],
      );
    }),
  );

  it.effect("surfaces gateway auth failures as unauthenticated", () =>
    Effect.gen(function* () {
      gateway?.stop();
      const nextGateway = yield* Effect.promise(() =>
        startMockOpenClawGateway({
          handleRequest(request) {
            if (request.method === "auth.authenticate") {
              return {
                error: {
                  code: 401,
                  message: "Unauthorized",
                },
              };
            }
            return undefined;
          },
        }),
      );
      gateway = nextGateway;

      const settings = yield* ServerSettingsService;
      yield* settings.updateSettings({
        providers: {
          openclaw: {
            gatewayUrl: nextGateway.url,
          },
        },
      });

      const provider = yield* OpenClawProvider;
      const snapshot = yield* provider.refresh;

      assert.equal(snapshot.status, "error");
      assert.equal(snapshot.auth.status, "unauthenticated");
      assert.equal(
        snapshot.message,
        "OpenClaw gateway rejected authentication. Check the configured token or password.",
      );
    }),
  );
});

it.layer(
  makeTestLayer({
    providers: {
      openclaw: {
        gatewayUrl: "ws://127.0.0.1:1",
      },
    },
  }),
)("OpenClawProviderLive unreachable gateway", (it) => {
  it.effect("surfaces connection failures with a friendly message", () =>
    Effect.gen(function* () {
      const provider = yield* OpenClawProvider;
      const snapshot = yield* provider.refresh;

      assert.equal(snapshot.status, "error");
      assert.equal(
        snapshot.message,
        "Couldn't reach the configured OpenClaw gateway at ws://127.0.0.1:1.",
      );
    }),
  );
});
