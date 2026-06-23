import {
  type ModelCapabilities,
  type PiSettings,
  ProviderDriverKind,
  type ServerProviderSkill,
  type ServerProviderModel,
  type ServerProviderSlashCommand,
} from "@t3tools/contracts";
import { createModelCapabilities } from "@t3tools/shared/model";
import * as Cause from "effect/Cause";
import * as DateTime from "effect/DateTime";
import * as Effect from "effect/Effect";

import {
  buildSelectOptionDescriptor,
  buildServerProvider,
  parseGenericCliVersion,
  providerModelsFromSettings,
  type ServerProviderDraft,
} from "../providerSnapshot.ts";
import {
  buildPiRpcArgs,
  PiRuntime,
  piRuntimeErrorDetail,
  type PiRpcModel,
  type PiRpcResponse,
} from "../piRpcRuntime.ts";

const PROVIDER = ProviderDriverKind.make("pi");
const PI_PRESENTATION = {
  displayName: "Pi",
  badgeLabel: "Early Access",
  showInteractionModeToggle: false,
} as const;
const PI_MODEL_DISCOVERY_TIMEOUT_MS = 15_000;
const PI_PROVIDER_SLASH_COMMANDS: ReadonlyArray<ServerProviderSlashCommand> = [];
const PI_PROVIDER_SKILLS: ReadonlyArray<ServerProviderSkill> = [];
const PI_UNSUPPORTED_EXECUTABLE_METADATA = {
  slashCommands: PI_PROVIDER_SLASH_COMMANDS,
  skills: PI_PROVIDER_SKILLS,
} as const;

const EMPTY_CAPABILITIES: ModelCapabilities = createModelCapabilities({
  optionDescriptors: [],
});

const PI_THINKING_LEVELS = [
  { value: "off", label: "Off", isDefault: true },
  { value: "minimal", label: "Minimal" },
  { value: "low", label: "Low" },
  { value: "medium", label: "Medium" },
  { value: "high", label: "High" },
] as const;

const PI_CODEX_THINKING_LEVELS = [
  ...PI_THINKING_LEVELS,
  { value: "xhigh", label: "Extra High" },
] as const;

function isCommandMissingDetail(detail: string): boolean {
  const normalized = detail.toLowerCase();
  return (
    normalized.includes("enoent") ||
    normalized.includes("notfound") ||
    normalized.includes("not found") ||
    normalized.includes("is not recognized")
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function responseModels(response: PiRpcResponse): ReadonlyArray<PiRpcModel> {
  if (!response.success) {
    throw new Error(response.error ?? "Pi model discovery failed.");
  }
  const data = response.data;
  if (!isRecord(data) || !Array.isArray(data.models)) {
    throw new Error("Pi model discovery returned an unexpected payload.");
  }
  return data.models.filter(isRecord) as ReadonlyArray<PiRpcModel>;
}

function titleCaseSlug(value: string): string {
  return value
    .split(/[-_/]+/u)
    .filter(Boolean)
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

function capabilitiesForPiModel(model: PiRpcModel): ModelCapabilities {
  if (model.reasoning !== true) {
    return EMPTY_CAPABILITIES;
  }
  const provider = model.provider?.trim() ?? "";
  const id = model.id?.trim() ?? "";
  const supportsExtraHigh = provider === "openai-codex" || id.includes("codex");
  return createModelCapabilities({
    optionDescriptors: [
      buildSelectOptionDescriptor({
        id: "thinking",
        label: "Thinking",
        options: supportsExtraHigh ? PI_CODEX_THINKING_LEVELS : PI_THINKING_LEVELS,
      }),
    ],
  });
}

export function flattenPiModels(
  models: ReadonlyArray<PiRpcModel>,
): ReadonlyArray<ServerProviderModel> {
  const seen = new Set<string>();
  const out: Array<ServerProviderModel> = [];
  for (const model of models) {
    const provider = model.provider?.trim();
    const id = model.id?.trim();
    if (!provider || !id) {
      continue;
    }
    const slug = `${provider}/${id}`;
    if (seen.has(slug)) {
      continue;
    }
    seen.add(slug);
    out.push({
      slug,
      name: model.name?.trim() || titleCaseSlug(id),
      shortName: model.name?.trim() || id,
      subProvider: titleCaseSlug(provider),
      isCustom: false,
      capabilities: capabilitiesForPiModel(model),
    });
  }
  return out.toSorted((left, right) => left.name.localeCompare(right.name));
}

function piModelsFromSettings(
  customModels: ReadonlyArray<string> | undefined,
  builtInModels: ReadonlyArray<ServerProviderModel> = [],
): ReadonlyArray<ServerProviderModel> {
  return providerModelsFromSettings(
    builtInModels,
    PROVIDER,
    customModels ?? [],
    EMPTY_CAPABILITIES,
  );
}

export const makePendingPiProvider = (piSettings: PiSettings): Effect.Effect<ServerProviderDraft> =>
  Effect.gen(function* () {
    const checkedAt = DateTime.formatIso(yield* DateTime.now);
    const models = piModelsFromSettings(piSettings.customModels);
    if (!piSettings.enabled) {
      return buildServerProvider({
        presentation: PI_PRESENTATION,
        enabled: false,
        checkedAt,
        models,
        ...PI_UNSUPPORTED_EXECUTABLE_METADATA,
        probe: {
          installed: false,
          version: null,
          status: "warning",
          auth: { status: "unknown" },
          message: "Pi is disabled in T3 Code settings.",
        },
      });
    }
    return buildServerProvider({
      presentation: PI_PRESENTATION,
      enabled: true,
      checkedAt,
      models,
      ...PI_UNSUPPORTED_EXECUTABLE_METADATA,
      probe: {
        installed: false,
        version: null,
        status: "warning",
        auth: { status: "unknown" },
        message: "Pi provider status has not been checked in this session yet.",
      },
    });
  });

export const checkPiProviderStatus = Effect.fn("checkPiProviderStatus")(function* (
  piSettings: PiSettings,
  cwd: string,
  environment?: NodeJS.ProcessEnv,
): Effect.fn.Return<ServerProviderDraft, never, PiRuntime> {
  const piRuntime = yield* PiRuntime;
  const resolvedEnvironment = environment ?? process.env;
  const checkedAt = DateTime.formatIso(yield* DateTime.now);
  const fallbackModels = piModelsFromSettings(piSettings.customModels);

  if (!piSettings.enabled) {
    return buildServerProvider({
      presentation: PI_PRESENTATION,
      enabled: false,
      checkedAt,
      models: fallbackModels,
      ...PI_UNSUPPORTED_EXECUTABLE_METADATA,
      probe: {
        installed: false,
        version: null,
        status: "warning",
        auth: { status: "unknown" },
        message: "Pi is disabled in T3 Code settings.",
      },
    });
  }

  const versionExit = yield* Effect.exit(
    piRuntime.runPiCommand({
      binaryPath: piSettings.binaryPath,
      args: ["--version"],
      environment: resolvedEnvironment,
      cwd,
    }),
  );
  if (versionExit._tag === "Failure") {
    const detail = piRuntimeErrorDetail(Cause.squash(versionExit.cause));
    return buildServerProvider({
      presentation: PI_PRESENTATION,
      enabled: true,
      checkedAt,
      models: fallbackModels,
      ...PI_UNSUPPORTED_EXECUTABLE_METADATA,
      probe: {
        installed: !isCommandMissingDetail(detail),
        version: null,
        status: "error",
        auth: { status: "unknown" },
        message: isCommandMissingDetail(detail)
          ? "Pi CLI (`pi`) is not installed or not on PATH."
          : `Failed to execute Pi CLI health check: ${detail}`,
      },
    });
  }
  const version = parseGenericCliVersion(versionExit.value.stdout) ?? null;

  const discoveryExit = yield* Effect.exit(
    Effect.scoped(
      Effect.gen(function* () {
        const session = yield* piRuntime.startPiRpcSession({
          binaryPath: piSettings.binaryPath,
          args: buildPiRpcArgs({ noSession: true, noTools: true }),
          environment: resolvedEnvironment,
          cwd,
          requestTimeoutMs: PI_MODEL_DISCOVERY_TIMEOUT_MS,
        });
        const response = yield* session.request(
          { type: "get_available_models" },
          { timeoutMs: PI_MODEL_DISCOVERY_TIMEOUT_MS },
        );
        yield* session.stop;
        return responseModels(response);
      }),
    ),
  );

  if (discoveryExit._tag === "Failure") {
    const detail = piRuntimeErrorDetail(Cause.squash(discoveryExit.cause));
    return buildServerProvider({
      presentation: PI_PRESENTATION,
      enabled: true,
      checkedAt,
      models: fallbackModels,
      ...PI_UNSUPPORTED_EXECUTABLE_METADATA,
      probe: {
        installed: true,
        version,
        status: "error",
        auth: { status: "unknown" },
        message: `Failed to discover Pi models: ${detail}`,
      },
    });
  }

  const models = piModelsFromSettings(
    piSettings.customModels,
    flattenPiModels(discoveryExit.value),
  );
  return buildServerProvider({
    presentation: PI_PRESENTATION,
    enabled: true,
    checkedAt,
    models,
    ...PI_UNSUPPORTED_EXECUTABLE_METADATA,
    probe: {
      installed: true,
      version,
      status: models.length > 0 ? "ready" : "warning",
      auth: {
        status: models.length > 0 ? "authenticated" : "unknown",
        type: "pi",
      },
      message:
        models.length > 0
          ? `${models.length} model${models.length === 1 ? "" : "s"} discovered through Pi.`
          : "Pi is available, but it did not report any models.",
    },
  });
});
