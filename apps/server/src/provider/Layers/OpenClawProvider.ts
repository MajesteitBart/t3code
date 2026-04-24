import type { OpenClawSettings, ServerProvider, ServerProviderModel } from "@t3tools/contracts";
import { Data, Effect, Equal, Layer, Stream } from "effect";

import { createModelCapabilities } from "@t3tools/shared/model";

import { ServerSettingsService } from "../../serverSettings.ts";
import { makeManagedServerProvider } from "../makeManagedServerProvider.ts";
import {
  buildServerProvider,
  nonEmptyTrimmed,
  providerModelsFromSettings,
  type ProviderProbeResult,
} from "../providerSnapshot.ts";
import { OpenClawProvider } from "../Services/OpenClawProvider.ts";
import {
  classifyOpenClawGatewayError,
  connectOpenClawGateway,
  DEFAULT_OPENCLAW_GATEWAY_URL,
  getOpenClawGatewayErrorMessage,
} from "../openclawGateway.ts";

const PROVIDER = "openclaw" as const;
const OPENCLAW_PRESENTATION = {
  displayName: "OpenClaw",
} as const;
const DEFAULT_OPENCLAW_MODEL_CAPABILITIES = createModelCapabilities({
  optionDescriptors: [],
});

class OpenClawProbeError extends Data.TaggedError("OpenClawProbeError")<{
  readonly detail: string;
  readonly cause: unknown;
}> {}

function parseOpenClawScopes(value: unknown): ReadonlyArray<string> {
  if (!Array.isArray(value)) {
    return [];
  }

  return Array.from(
    new Set(
      value
        .map((entry) => nonEmptyTrimmed(typeof entry === "string" ? entry : undefined))
        .filter((entry): entry is string => entry !== null),
    ),
  );
}

function hasOpenClawReadScope(scopes: ReadonlyArray<string>): boolean {
  const scopeSet = new Set(scopes);
  return (
    scopeSet.has("operator.admin") ||
    scopeSet.has("operator.write") ||
    scopeSet.has("operator.read")
  );
}

function parseOpenClawVersion(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const record = payload as Record<string, unknown>;
  for (const candidate of [
    record.version,
    record.gatewayVersion,
    record.serverVersion,
    record.gateway && typeof record.gateway === "object"
      ? (record.gateway as Record<string, unknown>).version
      : undefined,
  ]) {
    const version = nonEmptyTrimmed(typeof candidate === "string" ? candidate : undefined);
    if (version) {
      return version;
    }
  }

  return null;
}

function parseOpenClawAuthLabel(payload: unknown): string | undefined {
  if (!payload || typeof payload !== "object") {
    return undefined;
  }

  const record = payload as Record<string, unknown>;
  for (const candidate of [record.label, record.accountLabel, record.account, record.user]) {
    const label = nonEmptyTrimmed(typeof candidate === "string" ? candidate : undefined);
    if (label) {
      return label;
    }
  }

  return undefined;
}

function parseOpenClawModels(payload: unknown): ReadonlyArray<ServerProviderModel> {
  const rawModels = Array.isArray(payload)
    ? payload
    : payload && typeof payload === "object"
      ? Array.isArray((payload as { models?: unknown }).models)
        ? ((payload as { models: unknown[] }).models ?? [])
        : Array.isArray((payload as { items?: unknown }).items)
          ? ((payload as { items: unknown[] }).items ?? [])
          : []
      : [];

  const models: Array<ServerProviderModel> = [];
  const seen = new Set<string>();

  for (const entry of rawModels) {
    if (typeof entry === "string") {
      const slug = nonEmptyTrimmed(entry);
      if (!slug || seen.has(slug)) {
        continue;
      }
      seen.add(slug);
      models.push({
        slug,
        name: slug,
        isCustom: false,
        capabilities: DEFAULT_OPENCLAW_MODEL_CAPABILITIES,
      });
      continue;
    }

    if (!entry || typeof entry !== "object") {
      continue;
    }

    const record = entry as Record<string, unknown>;
    const slug =
      nonEmptyTrimmed(typeof record.slug === "string" ? record.slug : undefined) ??
      nonEmptyTrimmed(typeof record.id === "string" ? record.id : undefined) ??
      nonEmptyTrimmed(typeof record.model === "string" ? record.model : undefined);
    if (!slug || seen.has(slug)) {
      continue;
    }
    seen.add(slug);

    const name =
      nonEmptyTrimmed(typeof record.name === "string" ? record.name : undefined) ??
      nonEmptyTrimmed(typeof record.label === "string" ? record.label : undefined) ??
      slug;
    const subProvider =
      nonEmptyTrimmed(typeof record.provider === "string" ? record.provider : undefined) ??
      nonEmptyTrimmed(typeof record.subProvider === "string" ? record.subProvider : undefined);

    models.push({
      slug,
      name,
      ...(subProvider ? { subProvider } : {}),
      isCustom: false,
      capabilities: DEFAULT_OPENCLAW_MODEL_CAPABILITIES,
    });
  }

  return models;
}

function formatOpenClawProbeError(input: {
  readonly error: unknown;
  readonly gatewayUrl: string;
}): ProviderProbeResult {
  const cause = input.error instanceof OpenClawProbeError ? input.error.cause : input.error;
  const classified = classifyOpenClawGatewayError(cause);

  switch (classified.kind) {
    case "auth":
      return {
        installed: true,
        version: null,
        status: "error",
        auth: { status: "unauthenticated" },
        message:
          "OpenClaw gateway rejected authentication. Check the configured token or password.",
      };
    case "pairing":
      return {
        installed: true,
        version: null,
        status: "error",
        auth: { status: "unknown" },
        message:
          classified.detailCode === "DEVICE_IDENTITY_REQUIRED"
            ? "OpenClaw gateway requires a paired device identity for T3 Code. Re-pair this client or update the configured token."
            : "OpenClaw gateway requires device pairing or approval for T3 Code before it can connect.",
      };
    case "missing_scope":
      return {
        installed: true,
        version: null,
        status: "warning",
        auth: { status: "authenticated" },
        message: `Connected to the OpenClaw gateway, but the current credentials are missing ${classified.missingScope ?? "operator.read"}.`,
      };
    case "connection":
      return {
        installed: true,
        version: null,
        status: "error",
        auth: { status: "unknown" },
        message: `Couldn't reach the configured OpenClaw gateway at ${input.gatewayUrl}.`,
      };
    default:
      return {
        installed: true,
        version: null,
        status: "error",
        auth: { status: "unknown" },
        message:
          input.error instanceof OpenClawProbeError
            ? input.error.detail
            : getOpenClawGatewayErrorMessage(input.error, "OpenClaw gateway probe failed."),
      };
  }
}

function makePendingOpenClawProvider(settings: OpenClawSettings): ServerProvider {
  const checkedAt = new Date().toISOString();
  const models = providerModelsFromSettings(
    [],
    PROVIDER,
    settings.customModels,
    DEFAULT_OPENCLAW_MODEL_CAPABILITIES,
  );

  if (!settings.enabled) {
    return buildServerProvider({
      provider: PROVIDER,
      presentation: OPENCLAW_PRESENTATION,
      enabled: false,
      checkedAt,
      models,
      probe: {
        installed: true,
        version: null,
        status: "warning",
        auth: { status: "unknown" },
        message: "OpenClaw is disabled in T3 Code settings.",
      },
    });
  }

  return buildServerProvider({
    provider: PROVIDER,
    presentation: OPENCLAW_PRESENTATION,
    enabled: true,
    checkedAt,
    models,
    probe: {
      installed: true,
      version: null,
      status: "warning",
      auth: { status: "unknown" },
      message: "OpenClaw provider status has not been checked in this session yet.",
    },
  });
}

const checkOpenClawProviderStatus = Effect.fn("checkOpenClawProviderStatus")(function* (
  settings: OpenClawSettings,
) {
  const checkedAt = new Date().toISOString();
  const gatewayUrl = nonEmptyTrimmed(settings.gatewayUrl) ?? DEFAULT_OPENCLAW_GATEWAY_URL;

  if (!settings.enabled) {
    return makePendingOpenClawProvider(settings);
  }

  const probe = yield* Effect.tryPromise({
    try: async () => {
      const connection = await connectOpenClawGateway({
        url: gatewayUrl,
        token: settings.gatewayToken,
        password: settings.gatewayPassword,
      });

      try {
        const grantedScopes = parseOpenClawScopes(connection.hello.auth?.scopes);
        if (!hasOpenClawReadScope(grantedScopes)) {
          return {
            version: nonEmptyTrimmed(connection.hello.server?.version) ?? null,
            authLabel: undefined,
            builtInModels: [],
            status: "warning" as const,
            statusMessage:
              "Connected to the OpenClaw gateway, but the current credentials do not include operator.read.",
          };
        }

        const [statusPayload, modelsPayload] = await Promise.all([
          connection.call("status"),
          connection.call("models.list"),
        ]);
        const version =
          parseOpenClawVersion(statusPayload) ??
          nonEmptyTrimmed(connection.hello.server?.version) ??
          null;
        const authLabel = parseOpenClawAuthLabel(statusPayload);
        const builtInModels = parseOpenClawModels(modelsPayload);
        const statusMessage =
          builtInModels.length === 0
            ? "Connected to the OpenClaw gateway. No models were reported yet."
            : undefined;

        return {
          version,
          authLabel,
          builtInModels,
          status: "ready" as const,
          ...(statusMessage ? { statusMessage } : {}),
        };
      } finally {
        await connection.close().catch(() => undefined);
      }
    },
    catch: (cause) =>
      new OpenClawProbeError({
        detail: getOpenClawGatewayErrorMessage(cause, "OpenClaw gateway probe failed."),
        cause,
      }),
  }).pipe(
    Effect.catchTag("OpenClawProbeError", (error) =>
      Effect.succeed({
        error,
      } as const),
    ),
  );

  const providerModels = providerModelsFromSettings(
    "error" in probe ? [] : probe.builtInModels,
    PROVIDER,
    settings.customModels,
    DEFAULT_OPENCLAW_MODEL_CAPABILITIES,
  );

  if ("error" in probe) {
    return buildServerProvider({
      provider: PROVIDER,
      presentation: OPENCLAW_PRESENTATION,
      enabled: true,
      checkedAt,
      models: providerModels,
      probe: formatOpenClawProbeError({
        error: probe.error,
        gatewayUrl,
      }),
    });
  }

  return buildServerProvider({
    provider: PROVIDER,
    presentation: OPENCLAW_PRESENTATION,
    enabled: true,
    checkedAt,
    models: providerModels,
    probe: {
      installed: true,
      version: probe.version,
      status: probe.status,
      auth: {
        status: "authenticated",
        ...(probe.authLabel ? { label: probe.authLabel } : {}),
      },
      ...(probe.statusMessage ? { message: probe.statusMessage } : {}),
    },
  });
});

export const OpenClawProviderLive = Layer.effect(
  OpenClawProvider,
  Effect.gen(function* () {
    const serverSettings = yield* ServerSettingsService;

    const getSettings = serverSettings.getSettings.pipe(
      Effect.map((settings) => settings.providers.openclaw),
      Effect.orElseSucceed(() => ({
        enabled: true,
        gatewayUrl: DEFAULT_OPENCLAW_GATEWAY_URL,
        gatewayToken: "",
        gatewayPassword: "",
        customModels: [],
      })),
    );
    const streamSettings = serverSettings.streamChanges.pipe(
      Stream.map((settings) => settings.providers.openclaw),
    );

    return yield* makeManagedServerProvider({
      getSettings,
      streamSettings,
      haveSettingsChanged: (previous, next) => !Equal.equals(previous, next),
      initialSnapshot: makePendingOpenClawProvider,
      checkProvider: getSettings.pipe(Effect.flatMap(checkOpenClawProviderStatus)),
    });
  }),
);
