import assert from "node:assert/strict";

import * as NodeServices from "@effect/platform-node/NodeServices";
import { it } from "@effect/vitest";
import { Effect, Layer } from "effect";

import { ServerConfig } from "../../config.ts";
import { ServerSettingsService } from "../../serverSettings.ts";
import { OpenClawProvider } from "../Services/OpenClawProvider.ts";
import {
  startMockOpenClawGateway,
  type MockOpenClawGatewayOptions,
  type MockOpenClawGatewayServer,
} from "../openclawGatewayTestServer.ts";
import { OpenClawProviderLive } from "./OpenClawProvider.ts";

const withGateway = <A, E = never, R = never>(
  effect: (gateway: MockOpenClawGatewayServer) => Effect.Effect<A, E, R>,
  options?: MockOpenClawGatewayOptions,
) =>
  Effect.acquireUseRelease(
    Effect.promise(() => startMockOpenClawGateway(options)),
    effect,
    (gateway) => Effect.sync(() => gateway.stop()),
  );

const makeTestLayer = (settingsOverrides?: Parameters<typeof ServerSettingsService.layerTest>[0]) =>
  OpenClawProviderLive.pipe(
    Layer.provideMerge(ServerConfig.layerTest(process.cwd(), process.cwd())),
    Layer.provideMerge(ServerSettingsService.layerTest(settingsOverrides)),
    Layer.provideMerge(NodeServices.layer),
  );

it.layer(makeTestLayer())("OpenClawProviderLive", (it) => {
  it.effect("loads provider status and model catalog from the gateway", () =>
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
    ),
  );

  it.effect("surfaces gateway auth failures as unauthenticated", () =>
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

          const provider = yield* OpenClawProvider;
          const snapshot = yield* provider.refresh;

          assert.equal(snapshot.status, "error");
          assert.equal(snapshot.auth.status, "unauthenticated");
          assert.equal(
            snapshot.message,
            "OpenClaw gateway rejected authentication. Check the configured token or password.",
          );
        }),
      {
        handleRequest(request) {
          if (request.method === "connect") {
            return {
              error: {
                code: "UNAUTHORIZED",
                message: "gateway token mismatch",
                details: {
                  code: "AUTH_TOKEN_MISMATCH",
                },
              },
            };
          }
          return undefined;
        },
      },
    ),
  );

  it.effect(
    "surfaces pairing and device identity requirements separately from connection failures",
    () =>
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

            const provider = yield* OpenClawProvider;
            const snapshot = yield* provider.refresh;

            assert.equal(snapshot.status, "error");
            assert.equal(snapshot.auth.status, "unknown");
            assert.equal(
              snapshot.message,
              "OpenClaw gateway requires a paired device identity for T3 Code. Re-pair this client or update the configured token.",
            );
          }),
        {
          handleRequest(request) {
            if (request.method === "connect") {
              return {
                error: {
                  code: "NOT_PAIRED",
                  message: "device identity required",
                  details: {
                    code: "DEVICE_IDENTITY_REQUIRED",
                  },
                },
              };
            }
            return undefined;
          },
        },
      ),
  );

  it.effect("surfaces missing operator.read scope as a permission warning", () =>
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

          const provider = yield* OpenClawProvider;
          const snapshot = yield* provider.refresh;

          assert.equal(snapshot.status, "warning");
          assert.equal(snapshot.auth.status, "authenticated");
          assert.equal(
            snapshot.message,
            "Connected to the OpenClaw gateway, but the current credentials do not include operator.read.",
          );
        }),
      {
        authScopes: [],
      },
    ),
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
