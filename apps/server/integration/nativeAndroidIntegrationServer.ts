// @effect-diagnostics nodeBuiltinImport:off
import * as NodeReadline from "node:readline";

import * as NodeRuntime from "@effect/platform-node/NodeRuntime";
import * as NodeServices from "@effect/platform-node/NodeServices";
import { CommandId, ThreadId } from "@t3tools/contracts";
import * as NetService from "@t3tools/shared/Net";
import { decodeJsonResult } from "@t3tools/shared/schemaJson";
import * as Effect from "effect/Effect";
import * as Layer from "effect/Layer";
import * as Option from "effect/Option";
import * as Queue from "effect/Queue";
import * as Result from "effect/Result";
import * as Schema from "effect/Schema";
import * as Stream from "effect/Stream";
import { HttpServer } from "effect/unstable/http";
import { Command, GlobalFlag } from "effect/unstable/cli";

import * as EnvironmentAuth from "../src/auth/EnvironmentAuth.ts";
import { resolveServerConfig, sharedServerCommandFlags } from "../src/cli/config.ts";
import * as ServerConfig from "../src/config.ts";
import { CheckpointReactor } from "../src/orchestration/Services/CheckpointReactor.ts";
import { OrchestrationEngineService } from "../src/orchestration/Services/OrchestrationEngine.ts";
import { ProjectionSnapshotQuery } from "../src/orchestration/Services/ProjectionSnapshotQuery.ts";
import { ProviderCommandReactor } from "../src/orchestration/Services/ProviderCommandReactor.ts";
import { ProviderRuntimeIngestionService } from "../src/orchestration/Services/ProviderRuntimeIngestion.ts";
import { ThreadDeletionReactor } from "../src/orchestration/Services/ThreadDeletionReactor.ts";
import { OrchestrationCommandReceiptRepository } from "../src/persistence/Services/OrchestrationCommandReceipts.ts";
import * as ServerRuntimeStartup from "../src/serverRuntimeStartup.ts";
import { makeServerLayerWithApplication } from "../src/server.ts";

const CONTROL_PREFIX = "T3_NATIVE_ANDROID_CONTROL ";
const encodeControlJson = Schema.encodeUnknownSync(Schema.fromJsonString(Schema.Unknown));

const ControlRequestBase = {
  requestId: Schema.String,
} as const;

const ControlRequest = Schema.Union([
  Schema.Struct({ ...ControlRequestBase, operation: Schema.Literal("describe") }),
  Schema.Struct({ ...ControlRequestBase, operation: Schema.Literal("snapshot") }),
  Schema.Struct({
    ...ControlRequestBase,
    operation: Schema.Literal("threadSnapshot"),
    threadId: ThreadId,
  }),
  Schema.Struct({
    ...ControlRequestBase,
    operation: Schema.Literal("receipt"),
    commandId: CommandId,
  }),
  Schema.Struct({
    ...ControlRequestBase,
    operation: Schema.Literal("events"),
    commandId: CommandId,
  }),
  Schema.Struct({ ...ControlRequestBase, operation: Schema.Literal("drain") }),
  Schema.Struct({ ...ControlRequestBase, operation: Schema.Literal("revokeMobileSessions") }),
]);
type ControlRequest = typeof ControlRequest.Type;

const writeControlValue = (value: unknown) =>
  Effect.sync(() => {
    process.stdout.write(`${CONTROL_PREFIX}${encodeControlJson(value)}\n`);
  });

const integrationControlLayer = Layer.effectDiscard(
  Effect.gen(function* () {
    const config = yield* ServerConfig.ServerConfig;
    const server = yield* HttpServer.HttpServer;
    const startup = yield* ServerRuntimeStartup.ServerRuntimeStartup;
    const environmentAuth = yield* EnvironmentAuth.EnvironmentAuth;
    const engine = yield* OrchestrationEngineService;
    const snapshotQuery = yield* ProjectionSnapshotQuery;
    const commandReceipts = yield* OrchestrationCommandReceiptRepository;
    const providerCommands = yield* ProviderCommandReactor;
    const providerRuntime = yield* ProviderRuntimeIngestionService;
    const checkpoints = yield* CheckpointReactor;
    const threadDeletion = yield* ThreadDeletionReactor;

    const address = server.address;
    if (typeof address === "string" || !("port" in address)) {
      return yield* Effect.die(new Error("Native Android integration server has no TCP port."));
    }

    const description = {
      pid: process.pid,
      port: address.port,
      workingDirectory: process.cwd(),
      baseDirectory: config.baseDir,
    } as const;

    const drainWorkers = Effect.gen(function* () {
      // The second pass closes causal work enqueued by an earlier reactor while
      // retaining a finite, deterministic barrier with no sleeps or polling.
      for (let pass = 0; pass < 2; pass += 1) {
        yield* providerCommands.drain;
        yield* providerRuntime.drain;
        yield* checkpoints.drain;
        yield* threadDeletion.drain;
      }
      return {
        latestSequence: yield* engine.latestSequence,
        snapshotSequence: (yield* snapshotQuery.getSnapshotSequence()).snapshotSequence,
      };
    });

    const handleRequest = (request: ControlRequest) =>
      Effect.gen(function* () {
        let value: unknown;
        switch (request.operation) {
          case "describe":
            value = description;
            break;
          case "snapshot":
            value = yield* snapshotQuery.getShellSnapshot();
            break;
          case "threadSnapshot":
            value = Option.getOrNull(
              yield* snapshotQuery.getThreadDetailSnapshot(request.threadId),
            );
            break;
          case "receipt":
            value = Option.getOrNull(
              yield* commandReceipts.getByCommandId({ commandId: request.commandId }),
            );
            break;
          case "events": {
            const events = yield* engine.readEvents(0, 10_000).pipe(
              Stream.filter((event) => event.commandId === request.commandId),
              Stream.runCollect,
            );
            value = events;
            break;
          }
          case "drain":
            value = yield* drainWorkers;
            break;
          case "revokeMobileSessions": {
            const sessions = yield* environmentAuth.listSessions();
            const revoked = yield* Effect.forEach(
              sessions.filter((session) => session.client.deviceType === "mobile"),
              (session) => environmentAuth.revokeSession(session.sessionId),
            );
            value = { revokedCount: revoked.filter(Boolean).length };
            break;
          }
        }
        yield* writeControlValue({ requestId: request.requestId, ok: true, value });
      }).pipe(
        Effect.catchCause(() =>
          writeControlValue({
            requestId: request.requestId,
            ok: false,
            error: { code: "CONTROL_REQUEST_FAILED" },
          }),
        ),
      );

    const handleLine = (line: string) => {
      const decoded = decodeJsonResult(ControlRequest)(line);
      return Result.isSuccess(decoded)
        ? handleRequest(decoded.success)
        : writeControlValue({
            requestId: null,
            ok: false,
            error: { code: "INVALID_CONTROL_REQUEST" },
          });
    };

    yield* startup.awaitCommandReady.pipe(Effect.orDie);
    yield* writeControlValue({ kind: "ready", ...description });

    const input = NodeReadline.createInterface({
      input: process.stdin,
      crlfDelay: Infinity,
    });
    const lines = yield* Queue.unbounded<string>();
    const context = yield* Effect.context<never>();
    const runFork = Effect.runForkWith(context);
    const handleInputLine = (line: string) => {
      runFork(Queue.offer(lines, line));
    };
    const handleInputClose = () => {
      runFork(Queue.shutdown(lines));
    };
    input.on("line", handleInputLine);
    input.once("close", handleInputClose);
    yield* Effect.addFinalizer(() =>
      Effect.sync(() => {
        input.removeListener("line", handleInputLine);
        input.removeListener("close", handleInputClose);
        input.close();
      }),
    );
    yield* Stream.fromQueue(lines).pipe(Stream.runForEach(handleLine), Effect.forkScoped);
  }),
);

const cliRuntimeLayer = Layer.mergeAll(NodeServices.layer, NetService.layer);

const command = Command.make("native-android-integration-server", {
  ...sharedServerCommandFlags,
}).pipe(
  Command.withDescription("Run a disposable T3 server with the native Android test control seam."),
  Command.withHandler((flags) =>
    Effect.gen(function* () {
      const logLevel = yield* GlobalFlag.LogLevel;
      const config = yield* resolveServerConfig(flags, logLevel, {
        startupPresentation: "headless",
        forceAutoBootstrapProjectFromCwd: false,
      });
      return yield* Layer.launch(makeServerLayerWithApplication(integrationControlLayer)).pipe(
        Effect.provideService(ServerConfig.ServerConfig, config),
      );
    }),
  ),
);

if (import.meta.main) {
  Command.run(command, { version: "0.0.0" }).pipe(
    Effect.scoped,
    Effect.provide(cliRuntimeLayer),
    NodeRuntime.runMain,
  );
}
