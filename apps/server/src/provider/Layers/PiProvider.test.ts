import { describe, expect, it } from "@effect/vitest";
import { PiSettings, type ServerProviderModel } from "@t3tools/contracts";
import * as Effect from "effect/Effect";
import * as Layer from "effect/Layer";
import * as Schema from "effect/Schema";
import * as Stream from "effect/Stream";

import { checkPiProviderStatus, flattenPiModels, makePendingPiProvider } from "./PiProvider.ts";
import { PiRuntime, PiRuntimeError, type PiRuntimeShape } from "../piRpcRuntime.ts";

const decodePiSettings = Schema.decodeSync(PiSettings);

function firstSelectOptionIds(model: ServerProviderModel | undefined): ReadonlyArray<string> {
  const descriptor = model?.capabilities?.optionDescriptors?.[0];
  return descriptor?.type === "select" ? descriptor.options.map((option) => option.id) : [];
}

function makePiRuntimeLayer(
  overrides: Partial<PiRuntimeShape> = {},
): Layer.Layer<PiRuntime, never, never> {
  const runtime: PiRuntimeShape = {
    runPiCommand: () => Effect.succeed({ stdout: "0.79.10\n", stderr: "", code: 0 }),
    startPiRpcSession: () =>
      Effect.succeed({
        pid: 1,
        events: Stream.empty,
        exitCode: Effect.never,
        request: () =>
          Effect.succeed({
            type: "response",
            command: "get_available_models",
            success: true,
            data: {
              models: [
                {
                  provider: "baseten",
                  id: "zai-org/GLM-5.2",
                  name: "Baseten GLM-5.2",
                  reasoning: true,
                },
                {
                  provider: "openai-codex",
                  id: "codex-max",
                  name: "Codex Max",
                  reasoning: true,
                },
              ],
            },
          }),
        stop: Effect.void,
      }),
    ...overrides,
  };
  return Layer.succeed(PiRuntime, runtime);
}

describe("PiProvider", () => {
  it("flattens discovered Pi models and exposes native thinking options", () => {
    const models = flattenPiModels([
      {
        provider: "baseten",
        id: "zai-org/GLM-5.2",
        name: "Baseten GLM-5.2",
        reasoning: true,
      },
      {
        provider: "baseten",
        id: "zai-org/GLM-5.2",
        name: "Duplicate",
      },
      {
        provider: "openai-codex",
        id: "codex-max",
        name: "Codex Max",
        reasoning: true,
      },
    ]);

    expect(models.map((model) => model.slug).toSorted()).toEqual([
      "baseten/zai-org/GLM-5.2",
      "openai-codex/codex-max",
    ]);
    const baseten = models.find((model) => model.slug === "baseten/zai-org/GLM-5.2");
    expect(firstSelectOptionIds(baseten)).toEqual(["off", "minimal", "low", "medium", "high"]);
    const codex = models.find((model) => model.slug === "openai-codex/codex-max");
    expect(firstSelectOptionIds(codex)).toContain("xhigh");
  });

  it.effect("represents disabled Pi without probing the runtime", () =>
    Effect.gen(function* () {
      const snapshot = yield* makePendingPiProvider(
        decodePiSettings({ enabled: false, customModels: ["baseten/zai-org/GLM-5.2"] }),
      );

      expect(snapshot.enabled).toBe(false);
      expect(snapshot.status).toBe("disabled");
      expect(snapshot.models.map((model) => model.slug)).toEqual(["baseten/zai-org/GLM-5.2"]);
      expect(snapshot.slashCommands).toEqual([]);
      expect(snapshot.skills).toEqual([]);
    }),
  );

  it.effect("discovers Pi models dynamically through RPC", () =>
    Effect.gen(function* () {
      const snapshot = yield* checkPiProviderStatus(
        decodePiSettings({ enabled: true }),
        process.cwd(),
        {},
      );

      expect(snapshot.enabled).toBe(true);
      expect(snapshot.status).toBe("ready");
      expect(snapshot.version).toBe("0.79.10");
      expect(snapshot.models.map((model) => model.slug).toSorted()).toEqual([
        "baseten/zai-org/GLM-5.2",
        "openai-codex/codex-max",
      ]);
      expect(snapshot.slashCommands).toEqual([]);
      expect(snapshot.skills).toEqual([]);
      expect(snapshot.message).toBe("2 models discovered through Pi.");
    }).pipe(Effect.provide(makePiRuntimeLayer())),
  );

  it.effect("keeps discovery failures scoped and does not invent fallback models", () =>
    Effect.gen(function* () {
      const snapshot = yield* checkPiProviderStatus(
        decodePiSettings({
          enabled: true,
          customModels: ["baseten/zai-org/GLM-5.2"],
        }),
        process.cwd(),
        {},
      );

      expect(snapshot.status).toBe("error");
      expect(snapshot.installed).toBe(true);
      expect(snapshot.models.map((model) => model.slug)).toEqual(["baseten/zai-org/GLM-5.2"]);
      expect(snapshot.message).toContain("Failed to discover Pi models");
    }).pipe(
      Effect.provide(
        makePiRuntimeLayer({
          startPiRpcSession: () =>
            Effect.fail(
              new PiRuntimeError({
                operation: "startPiRpcSession",
                detail: "RPC unavailable",
              }),
            ),
        }),
      ),
    ),
  );

  it.effect("reports a missing Pi binary as a provider snapshot error", () =>
    Effect.gen(function* () {
      const snapshot = yield* checkPiProviderStatus(
        decodePiSettings({ enabled: true }),
        process.cwd(),
        {},
      );

      expect(snapshot.status).toBe("error");
      expect(snapshot.installed).toBe(false);
      expect(snapshot.models).toEqual([]);
      expect(snapshot.message).toBe("Pi CLI (`pi`) is not installed or not on PATH.");
    }).pipe(
      Effect.provide(
        makePiRuntimeLayer({
          runPiCommand: () =>
            Effect.fail(
              new PiRuntimeError({
                operation: "runPiCommand",
                detail: "spawn pi ENOENT",
              }),
            ),
        }),
      ),
    ),
  );
});
