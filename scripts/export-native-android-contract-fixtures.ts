// @effect-diagnostics nodeBuiltinImport:off - deterministic host-side contract fixture exporter.
import * as NodeChildProcess from "node:child_process";
import * as NodeCrypto from "node:crypto";
import * as NodeFS from "node:fs";
import * as NodeModule from "node:module";
import * as NodePath from "node:path";

import * as DateTime from "effect/DateTime";
import * as Schema from "effect/Schema";
import type * as RpcMessage from "effect/unstable/rpc/RpcMessage";

import {
  AuthAccessTokenResult,
  AuthAccessTokenType,
  AuthWebSocketTicketResult,
  ClientOrchestrationCommand,
  DispatchResult,
  EnvironmentHttpCommonError,
  ExecutionEnvironmentDescriptor,
  ModelSelection,
  OrchestrationShellSnapshot,
  OrchestrationShellStreamItem,
  OrchestrationSubscribeShellInput,
  OrchestrationThreadDetailSnapshot,
  ProviderInteractionMode,
} from "@t3tools/contracts";

const repositoryRoot = NodePath.resolve(import.meta.dirname, "..");
const outputDirectory = NodePath.join(
  repositoryRoot,
  "apps/kotlin-android/core-protocol/src/test/resources/contracts/foundation",
);

const canonicalContractSources = [
  "packages/contracts/src/auth.ts",
  "packages/contracts/src/baseSchemas.ts",
  "packages/contracts/src/environment.ts",
  "packages/contracts/src/environmentHttp.ts",
  "packages/contracts/src/model.ts",
  "packages/contracts/src/orchestration.ts",
  "packages/contracts/src/providerInstance.ts",
  "packages/contracts/src/rpc.ts",
] as const;

const maxExactJsonInteger = Number.MAX_SAFE_INTEGER;
const fixtureTimestamp = "2026-08-08T00:00:00.000Z";
const decodeWebSocketTicketResult = Schema.decodeUnknownSync(AuthWebSocketTicketResult);

type FixtureExpectation = "accept" | "reject" | "refresh-required";

interface FixtureDefinition {
  readonly path: string;
  readonly contract: string;
  readonly expectation: FixtureExpectation;
  readonly value: unknown;
}

function normalizedSource(path: string): string {
  return NodeFS.readFileSync(NodePath.join(repositoryRoot, path), "utf8").replaceAll("\r\n", "\n");
}

function hashEntries(entries: ReadonlyArray<readonly [string, string]>): string {
  const hash = NodeCrypto.createHash("sha256");
  for (const [path, contents] of entries) {
    hash.update(path);
    hash.update("\0");
    hash.update(contents);
    hash.update("\0");
  }
  return hash.digest("hex");
}

function hashFile(path: string): string {
  return NodeCrypto.createHash("sha256").update(NodeFS.readFileSync(path)).digest("hex");
}

function stableJson(value: unknown): string {
  const normalize = (input: unknown): unknown => {
    if (Array.isArray(input)) return input.map(normalize);
    if (input !== null && typeof input === "object") {
      return Object.fromEntries(
        Object.entries(input)
          .sort(([left], [right]) => left.localeCompare(right))
          .map(([key, entry]) => [key, normalize(entry)]),
      );
    }
    return input;
  };
  return `${JSON.stringify(normalize(value), undefined, 2)}\n`;
}

function decode(schema: Schema.Codec<unknown, unknown, never, never>, value: unknown): void {
  Schema.decodeUnknownSync(Schema.fromJsonString(schema))(JSON.stringify(value));
}

function expectRejected(
  schema: Schema.Codec<unknown, unknown, never, never>,
  value: unknown,
  name: string,
): void {
  try {
    Schema.decodeUnknownSync(Schema.fromJsonString(schema))(JSON.stringify(value));
  } catch {
    return;
  }
  throw new Error(`${name} unexpectedly satisfies its canonical TypeScript schema.`);
}

function findEffectPackageRoot(): string {
  const require = NodeModule.createRequire(import.meta.url);
  let current = NodePath.dirname(require.resolve("effect/Schema"));
  while (current !== NodePath.dirname(current)) {
    if (NodeFS.existsSync(NodePath.join(current, "package.json"))) return current;
    current = NodePath.dirname(current);
  }
  throw new Error("Unable to resolve the installed Effect package for RPC provenance.");
}

const descriptor = {
  environmentId: "environment-fixture",
  label: "Fixture environment",
  platform: { os: "linux", arch: "x64" },
  serverVersion: "0.0.0-fixture",
  capabilities: {
    repositoryIdentity: true,
    connectionProbe: true,
    threadSettlement: false,
  },
};

const descriptorWithoutOptionalCapabilities = {
  environmentId: "environment-minimal",
  label: "Minimal fixture environment",
  platform: { os: "unknown", arch: "other" },
  serverVersion: "0.0.0-fixture",
  capabilities: {},
};

const descriptorWithUnknownFields = {
  ...descriptor,
  futureDescriptorField: { enabled: true },
  capabilities: {
    ...descriptor.capabilities,
    futureCapability: true,
  },
};

const descriptorWithIncompatibleEnum = {
  ...descriptor,
  platform: { os: "android", arch: "x64" },
};

const accessTokenResult = {
  access_token: "<redacted>",
  issued_token_type: AuthAccessTokenType,
  token_type: "Bearer",
  expires_in: 3600,
  scope: "orchestration:read orchestration:operate",
};

const webSocketTicketResult = {
  ticket: "<redacted>",
  expiresAt: fixtureTimestamp,
};

const shellSnapshot = {
  snapshotSequence: maxExactJsonInteger,
  projects: [],
  threads: [],
  updatedAt: fixtureTimestamp,
};

const projectShell = {
  id: "project-fixture",
  title: "Fixture project",
  workspaceRoot: "fixture-workspace",
  defaultModelSelection: null,
  scripts: [],
  createdAt: fixtureTimestamp,
  updatedAt: fixtureTimestamp,
};

const threadShell = {
  id: "thread-fixture",
  projectId: "project-fixture",
  title: "Fixture thread",
  modelSelection: { instanceId: "codex", model: "fixture-model" },
  runtimeMode: "full-access",
  interactionMode: "default",
  branch: "main",
  worktreePath: null,
  latestTurn: null,
  createdAt: fixtureTimestamp,
  updatedAt: fixtureTimestamp,
  archivedAt: null,
  settledOverride: null,
  settledAt: null,
  session: null,
  latestUserMessageAt: null,
  hasPendingApprovals: false,
  hasPendingUserInput: false,
  hasActionableProposedPlan: false,
};

const shellStreamVariants = [
  { kind: "synchronized" },
  { kind: "snapshot", snapshot: shellSnapshot },
  { kind: "project-upserted", sequence: maxExactJsonInteger, project: projectShell },
  { kind: "project-removed", sequence: maxExactJsonInteger - 1, projectId: projectShell.id },
  { kind: "thread-upserted", sequence: maxExactJsonInteger - 2, thread: threadShell },
  { kind: "thread-removed", sequence: maxExactJsonInteger - 3, threadId: threadShell.id },
] as const;

const threadSnapshot = {
  snapshotSequence: maxExactJsonInteger,
  thread: {
    id: "thread-fixture",
    projectId: "project-fixture",
    title: "Fixture thread",
    modelSelection: { provider: "codex", model: "fixture-model" },
    runtimeMode: "full-access",
    interactionMode: "default",
    branch: "main",
    worktreePath: null,
    latestTurn: null,
    createdAt: fixtureTimestamp,
    updatedAt: fixtureTimestamp,
    archivedAt: null,
    settledOverride: null,
    settledAt: null,
    deletedAt: null,
    messages: [
      {
        id: "message-fixture",
        role: "user",
        text: "Synthetic fixture message",
        turnId: "turn-fixture",
        streaming: false,
        createdAt: fixtureTimestamp,
        updatedAt: fixtureTimestamp,
      },
    ],
    proposedPlans: [],
    activities: [],
    checkpoints: [],
    session: null,
  },
};

const dispatchCommand = {
  type: "thread.archive",
  commandId: "command-fixture",
  threadId: "thread-fixture",
};

const dispatchResult = { sequence: maxExactJsonInteger };
const subscribeShellInput = {
  afterSequence: maxExactJsonInteger,
  requestCompletionMarker: true,
};

const errorWithTrace = {
  _tag: "EnvironmentAuthInvalidError",
  code: "auth_invalid",
  reason: "invalid_credential",
  traceId: "trace-fixture",
};

const canonicalModelSelection = { instanceId: "codex", model: "fixture-model" };
const legacyModelSelection = { provider: "codex", model: "fixture-model" };

const requestDispatch = {
  _tag: "Request",
  id: 1,
  tag: "orchestration.dispatchCommand",
  payload: dispatchCommand,
  headers: [],
} satisfies RpcMessage.RequestEncoded;

const requestSubscribeShell = {
  _tag: "Request",
  id: 2,
  tag: "orchestration.subscribeShell",
  payload: subscribeShellInput,
  headers: [],
} satisfies RpcMessage.RequestEncoded;

const chunkSnapshot = {
  _tag: "Chunk",
  requestId: 2,
  values: [{ kind: "snapshot", snapshot: shellSnapshot }],
} satisfies RpcMessage.ResponseChunkEncoded;

const chunkShellVariants = {
  _tag: "Chunk",
  requestId: 2,
  values: shellStreamVariants,
} satisfies RpcMessage.ResponseChunkEncoded;

const chunkUnknownShellItem = {
  _tag: "Chunk",
  requestId: 2,
  values: [{ kind: "future-shell-item", sequence: maxExactJsonInteger - 1 }],
} satisfies RpcMessage.ResponseChunkEncoded;

const exitSuccess = {
  _tag: "Exit",
  requestId: 1,
  exit: { _tag: "Success", value: dispatchResult },
} satisfies RpcMessage.ResponseExitEncoded;

const exitRemoteFailure = {
  _tag: "Exit",
  requestId: 2,
  exit: {
    _tag: "Failure",
    cause: [
      {
        _tag: "Fail",
        error: {
          _tag: "OrchestrationGetSnapshotError",
          message: "Synthetic fixture rejection",
        },
      },
    ],
  },
} satisfies RpcMessage.ResponseExitEncoded;

const defect = {
  _tag: "Defect",
  defect: { name: "Error", message: "Synthetic fixture defect" },
} satisfies RpcMessage.ResponseDefectEncoded;

const clientProtocolError = {
  _tag: "ClientProtocolError",
  error: {
    _tag: "RpcClientError",
    reason: {
      _tag: "RpcClientDefect",
      message: "Synthetic fixture protocol error",
      cause: "Synthetic fixture cause",
    },
  },
};

const fixtures: ReadonlyArray<FixtureDefinition> = [
  {
    path: "http/environment-descriptor.json",
    contract: "ExecutionEnvironmentDescriptor",
    expectation: "accept",
    value: descriptor,
  },
  {
    path: "http/environment-descriptor-omitted-optionals.json",
    contract: "ExecutionEnvironmentDescriptor",
    expectation: "accept",
    value: descriptorWithoutOptionalCapabilities,
  },
  {
    path: "http/environment-descriptor-unknown-fields.json",
    contract: "ExecutionEnvironmentDescriptor",
    expectation: "accept",
    value: descriptorWithUnknownFields,
  },
  {
    path: "http/environment-descriptor-incompatible-enum.json",
    contract: "ExecutionEnvironmentDescriptor",
    expectation: "reject",
    value: descriptorWithIncompatibleEnum,
  },
  {
    path: "http/access-token-result.json",
    contract: "AuthAccessTokenResult",
    expectation: "accept",
    value: accessTokenResult,
  },
  {
    path: "http/websocket-ticket-result.json",
    contract: "AuthWebSocketTicketResult",
    expectation: "accept",
    value: webSocketTicketResult,
  },
  {
    path: "http/shell-snapshot.json",
    contract: "OrchestrationShellSnapshot",
    expectation: "accept",
    value: shellSnapshot,
  },
  {
    path: "http/thread-snapshot.json",
    contract: "OrchestrationThreadDetailSnapshot",
    expectation: "accept",
    value: threadSnapshot,
  },
  {
    path: "http/dispatch-result.json",
    contract: "DispatchResult",
    expectation: "accept",
    value: dispatchResult,
  },
  {
    path: "http/error-with-trace.json",
    contract: "EnvironmentHttpCommonError",
    expectation: "accept",
    value: errorWithTrace,
  },
  {
    path: "model/model-selection.json",
    contract: "ModelSelection",
    expectation: "accept",
    value: canonicalModelSelection,
  },
  {
    path: "model/model-selection-legacy-alias.json",
    contract: "ModelSelection",
    expectation: "accept",
    value: legacyModelSelection,
  },
  {
    path: "model/interaction-mode-incompatible.json",
    contract: "ProviderInteractionMode",
    expectation: "reject",
    value: "standard",
  },
  {
    path: "rpc/request-dispatch.json",
    contract: "Effect RPC Request/orchestration.dispatchCommand",
    expectation: "accept",
    value: requestDispatch,
  },
  {
    path: "rpc/request-subscribe-shell.json",
    contract: "Effect RPC Request/orchestration.subscribeShell",
    expectation: "accept",
    value: requestSubscribeShell,
  },
  {
    path: "rpc/ack.json",
    contract: "Effect RPC Ack",
    expectation: "accept",
    value: { _tag: "Ack", requestId: 2 } satisfies RpcMessage.AckEncoded,
  },
  {
    path: "rpc/interrupt.json",
    contract: "Effect RPC Interrupt",
    expectation: "accept",
    value: { _tag: "Interrupt", requestId: 2 } satisfies RpcMessage.InterruptEncoded,
  },
  {
    path: "rpc/ping.json",
    contract: "Effect RPC Ping",
    expectation: "accept",
    value: { _tag: "Ping" } satisfies RpcMessage.Ping,
  },
  {
    path: "rpc/pong.json",
    contract: "Effect RPC Pong",
    expectation: "accept",
    value: { _tag: "Pong" } satisfies RpcMessage.Pong,
  },
  {
    path: "rpc/chunk-shell-snapshot.json",
    contract: "Effect RPC Chunk/OrchestrationShellStreamItem",
    expectation: "accept",
    value: chunkSnapshot,
  },
  {
    path: "rpc/chunk-shell-variants.json",
    contract: "Effect RPC Chunk/OrchestrationShellStreamItem variants",
    expectation: "accept",
    value: chunkShellVariants,
  },
  {
    path: "rpc/chunk-unknown-shell-item.json",
    contract: "Effect RPC Chunk/OrchestrationShellStreamItem",
    expectation: "refresh-required",
    value: chunkUnknownShellItem,
  },
  {
    path: "rpc/exit-success.json",
    contract: "Effect RPC Exit.Success/DispatchResult",
    expectation: "accept",
    value: exitSuccess,
  },
  {
    path: "rpc/exit-remote-failure.json",
    contract: "Effect RPC Exit.Failure",
    expectation: "accept",
    value: exitRemoteFailure,
  },
  {
    path: "rpc/defect.json",
    contract: "Effect RPC Defect",
    expectation: "accept",
    value: defect,
  },
  {
    path: "rpc/client-protocol-error.json",
    contract: "Effect RPC ClientProtocolError",
    expectation: "accept",
    value: clientProtocolError,
  },
];

function validateCanonicalFixtures(): void {
  decode(ExecutionEnvironmentDescriptor, descriptor);
  decode(ExecutionEnvironmentDescriptor, descriptorWithoutOptionalCapabilities);
  decode(ExecutionEnvironmentDescriptor, descriptorWithUnknownFields);
  expectRejected(
    ExecutionEnvironmentDescriptor,
    descriptorWithIncompatibleEnum,
    "environment-descriptor-incompatible-enum",
  );
  decode(AuthAccessTokenResult, accessTokenResult);
  const decodedTicket = decodeWebSocketTicketResult({
    ...webSocketTicketResult,
    expiresAt: DateTime.makeUnsafe(webSocketTicketResult.expiresAt),
  });
  if (DateTime.formatIso(decodedTicket.expiresAt) !== webSocketTicketResult.expiresAt) {
    throw new Error("WebSocket ticket expiry did not retain its canonical UTC representation.");
  }
  decode(OrchestrationShellSnapshot, shellSnapshot);
  for (const item of shellStreamVariants) decode(OrchestrationShellStreamItem, item);
  decode(OrchestrationThreadDetailSnapshot, threadSnapshot);
  decode(DispatchResult, dispatchResult);
  decode(EnvironmentHttpCommonError, errorWithTrace);
  decode(ModelSelection, canonicalModelSelection);
  decode(ModelSelection, legacyModelSelection);
  expectRejected(ProviderInteractionMode, "standard", "interaction-mode-incompatible");
  decode(ClientOrchestrationCommand, requestDispatch.payload);
  decode(OrchestrationSubscribeShellInput, requestSubscribeShell.payload);
  decode(OrchestrationShellStreamItem, chunkSnapshot.values[0]);
  expectRejected(
    OrchestrationShellStreamItem,
    chunkUnknownShellItem.values[0],
    "chunk-unknown-shell-item",
  );
  decode(DispatchResult, exitSuccess.exit.value);

  if (requestDispatch.headers.length !== 0 || requestSubscribeShell.headers.length !== 0) {
    throw new Error('Effect RPC requests without headers must encode exactly as "headers": [].');
  }
  if (!Number.isSafeInteger(shellSnapshot.snapshotSequence)) {
    throw new Error("The large integer fixture must remain exact in canonical JSON.");
  }
}

function collectJsonFiles(directory: string): ReadonlyArray<string> {
  if (!NodeFS.existsSync(directory)) return [];
  return NodeFS.readdirSync(directory)
    .flatMap((entry) => {
      const path = NodePath.join(directory, entry);
      return NodeFS.statSync(path).isDirectory() ? collectJsonFiles(path) : [path];
    })
    .filter((path) => path.endsWith(".json"));
}

function assertFixtureHygiene(files: ReadonlyMap<string, string>): void {
  const forbidden = [
    /(?:[A-Za-z]:\\|\/Users\/|\/home\/)[^\s"]+/u,
    /(?:pairingUrl|wsTicket|subject_token)\s*[=:]\s*(?!<redacted>)/iu,
    /-----BEGIN [A-Z ]+PRIVATE KEY-----/u,
  ];
  for (const [path, contents] of files) {
    for (const pattern of forbidden) {
      if (pattern.test(contents)) {
        throw new Error(`${path} contains forbidden credential or private-path material.`);
      }
    }
  }
}

function buildExpectedFiles(): ReadonlyMap<string, string> {
  validateCanonicalFixtures();

  const sourceEntries = canonicalContractSources.map(
    (path) => [path, normalizedSource(path)] as const,
  );
  const sourceRevision = NodeChildProcess.execFileSync(
    "git",
    ["log", "-1", "--format=%H", "--", ...canonicalContractSources],
    { cwd: repositoryRoot, encoding: "utf8" },
  ).trim();
  if (!/^[0-9a-f]{40}$/u.test(sourceRevision)) {
    throw new Error(`Unable to resolve canonical contracts source revision: ${sourceRevision}`);
  }

  const effectPackageRoot = findEffectPackageRoot();
  const effectPackage = JSON.parse(
    NodeFS.readFileSync(NodePath.join(effectPackageRoot, "package.json"), "utf8"),
  ) as {
    readonly version?: unknown;
  };
  if (typeof effectPackage.version !== "string") {
    throw new Error("Unable to resolve the installed Effect package version.");
  }
  const effectProtocolSource = NodePath.join(effectPackageRoot, "src/unstable/rpc/RpcMessage.ts");
  if (!NodeFS.existsSync(effectProtocolSource)) {
    throw new Error("The installed Effect package does not contain RpcMessage.ts provenance.");
  }

  const manifest = {
    schemaVersion: 1,
    provenance: {
      canonicalContracts: {
        revision: sourceRevision,
        contentHash: hashEntries(sourceEntries),
        algorithm: "sha256-path-lf-v1",
        files: canonicalContractSources,
      },
      effectRpc: {
        version: effectPackage.version,
        contentHash: hashFile(effectProtocolSource),
        algorithm: "sha256-bytes-v1",
        source: "effect/src/unstable/rpc/RpcMessage.ts",
      },
    },
    inventory: {
      http: [
        "GET /.well-known/t3/environment -> ExecutionEnvironmentDescriptor",
        "POST /oauth/token -> AuthAccessTokenResult",
        "GET /api/orchestration/shell -> OrchestrationShellSnapshot",
        "GET /api/orchestration/threads/:threadId -> OrchestrationThreadDetailSnapshot",
        "POST /api/orchestration/dispatch -> ClientOrchestrationCommand / DispatchResult",
        "POST /api/auth/websocket-ticket -> AuthWebSocketTicketResult",
      ],
      rpc: [
        "orchestration.dispatchCommand -> ClientOrchestrationCommand / DispatchResult",
        "orchestration.subscribeShell -> OrchestrationSubscribeShellInput / OrchestrationShellStreamItem",
      ],
    },
    fixtures: fixtures.map(({ path, contract, expectation }) => ({ path, contract, expectation })),
  };

  const expected = new Map<string, string>([["manifest.json", stableJson(manifest)]]);
  for (const fixture of fixtures) expected.set(fixture.path, stableJson(fixture.value));
  assertFixtureHygiene(expected);
  return expected;
}

function writeFixtures(expected: ReadonlyMap<string, string>): void {
  for (const [path, contents] of expected) {
    const absolutePath = NodePath.join(outputDirectory, path);
    NodeFS.mkdirSync(NodePath.dirname(absolutePath), { recursive: true });
    NodeFS.writeFileSync(absolutePath, contents, "utf8");
  }
  process.stdout.write(`Wrote ${expected.size} native Android contract fixture files.\n`);
}

function checkFixtures(expected: ReadonlyMap<string, string>): void {
  const failures: Array<string> = [];
  for (const [path, expectedContents] of expected) {
    const absolutePath = NodePath.join(outputDirectory, path);
    if (!NodeFS.existsSync(absolutePath)) {
      failures.push(`missing ${path}`);
      continue;
    }
    const actualContents = NodeFS.readFileSync(absolutePath, "utf8").replaceAll("\r\n", "\n");
    if (actualContents !== expectedContents) failures.push(`stale ${path}`);
  }

  const expectedPaths = new Set(expected.keys());
  for (const absolutePath of collectJsonFiles(outputDirectory)) {
    const path = NodePath.relative(outputDirectory, absolutePath).replaceAll("\\", "/");
    if (!expectedPaths.has(path)) failures.push(`unexpected ${path}`);
  }

  if (failures.length > 0) {
    throw new Error(
      `Native Android contract fixtures drifted from canonical sources:\n${failures
        .map((failure) => `- ${failure}`)
        .join("\n")}\nRun: node scripts/export-native-android-contract-fixtures.ts --write`,
    );
  }
  process.stdout.write(
    `Verified ${expected.size} native Android contract fixture files against canonical sources.\n`,
  );
}

const mode = process.argv[2] ?? "--check";
const expectedFiles = buildExpectedFiles();
if (mode === "--write") writeFixtures(expectedFiles);
else if (mode === "--check") checkFixtures(expectedFiles);
else throw new Error(`Unknown mode ${mode}; expected --check or --write.`);
