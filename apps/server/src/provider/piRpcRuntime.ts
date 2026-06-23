// @effect-diagnostics nodeBuiltinImport:off
import * as NodeCrypto from "node:crypto";

import * as Context from "effect/Context";
import * as Data from "effect/Data";
import * as Deferred from "effect/Deferred";
import * as Effect from "effect/Effect";
import * as Fiber from "effect/Fiber";
import * as Layer from "effect/Layer";
import * as P from "effect/Predicate";
import * as Queue from "effect/Queue";
import * as Ref from "effect/Ref";
import * as Schema from "effect/Schema";
import * as Scope from "effect/Scope";
import * as Stream from "effect/Stream";
import { ChildProcess, ChildProcessSpawner } from "effect/unstable/process";

import { isWindowsCommandNotFound } from "../processRunner.ts";
import { collectStreamAsString } from "./providerSnapshot.ts";
import { resolveSpawnCommand } from "@t3tools/shared/shell";

const PI_RUNTIME_ERROR_TAG = "PiRuntimeError";
const DEFAULT_RPC_REQUEST_TIMEOUT_MS = 30_000;
const encodeUnknownJsonString = Schema.encodeUnknownSync(Schema.UnknownFromJsonString);
const decodeUnknownJsonString = Schema.decodeUnknownSync(Schema.UnknownFromJsonString);

export type PiThinkingLevel = "off" | "minimal" | "low" | "medium" | "high" | "xhigh";

export interface PiRpcCommand {
  readonly id?: string;
  readonly type: string;
  readonly [key: string]: unknown;
}

export interface PiRpcResponse {
  readonly id?: string;
  readonly type: "response";
  readonly command: string;
  readonly success: boolean;
  readonly data?: unknown;
  readonly error?: string;
}

export interface PiRpcEvent {
  readonly type: string;
  readonly [key: string]: unknown;
}

export interface PiRpcModel {
  readonly id?: string;
  readonly name?: string;
  readonly provider?: string;
  readonly reasoning?: boolean;
  readonly contextWindow?: number;
  readonly maxTokens?: number;
  readonly [key: string]: unknown;
}

export interface PiCommandResult {
  readonly stdout: string;
  readonly stderr: string;
  readonly code: number;
}

export interface ParsedPiModelSlug {
  readonly provider: string;
  readonly modelId: string;
}

export interface PiRpcSession {
  readonly pid: number;
  readonly events: Stream.Stream<PiRpcEvent>;
  readonly exitCode: Effect.Effect<number, never>;
  readonly request: (
    command: PiRpcCommand,
    options?: { readonly timeoutMs?: number },
  ) => Effect.Effect<PiRpcResponse, PiRuntimeError>;
  readonly stop: Effect.Effect<void, never>;
}

export interface PiRuntimeShape {
  readonly runPiCommand: (input: {
    readonly binaryPath: string;
    readonly args: ReadonlyArray<string>;
    readonly environment?: NodeJS.ProcessEnv;
    readonly cwd?: string;
  }) => Effect.Effect<PiCommandResult, PiRuntimeError>;
  readonly startPiRpcSession: (input: {
    readonly binaryPath: string;
    readonly args?: ReadonlyArray<string>;
    readonly environment?: NodeJS.ProcessEnv;
    readonly cwd?: string;
    readonly requestTimeoutMs?: number;
  }) => Effect.Effect<PiRpcSession, PiRuntimeError, Scope.Scope>;
}

export class PiRuntimeError extends Data.TaggedError(PI_RUNTIME_ERROR_TAG)<{
  readonly operation: string;
  readonly detail: string;
  readonly cause?: unknown;
}> {
  static readonly is = (u: unknown): u is PiRuntimeError => P.isTagged(u, PI_RUNTIME_ERROR_TAG);
}

export function piRuntimeErrorDetail(cause: unknown): string {
  if (PiRuntimeError.is(cause)) return cause.detail;
  if (cause instanceof Error && cause.message.trim().length > 0) return cause.message.trim();
  return String(cause);
}

export function buildPiRpcArgs(input?: {
  readonly provider?: string | null;
  readonly modelId?: string | null;
  readonly thinkingLevel?: PiThinkingLevel | null;
  readonly appendSystemPrompt?: string | null;
  readonly mcpConfigPath?: string | null;
  readonly noSession?: boolean;
  readonly offline?: boolean;
  readonly noTools?: boolean;
}): ReadonlyArray<string> {
  const args = ["--mode", "rpc"];
  if (input?.noSession) args.push("--no-session");
  if (input?.offline) args.push("--offline");
  if (input?.noTools) args.push("--no-tools");
  if (input?.appendSystemPrompt) args.push("--append-system-prompt", input.appendSystemPrompt);
  if (input?.mcpConfigPath) args.push("--mcp-config", input.mcpConfigPath);
  if (input?.provider) args.push("--provider", input.provider);
  if (input?.modelId) args.push("--model", input.modelId);
  if (input?.thinkingLevel) args.push("--thinking", input.thinkingLevel);
  return args;
}

export function parsePiModelSlug(slug: string | null | undefined): ParsedPiModelSlug | null {
  if (typeof slug !== "string") {
    return null;
  }
  const trimmed = slug.trim();
  const separator = trimmed.indexOf("/");
  if (separator <= 0 || separator === trimmed.length - 1) {
    return null;
  }
  return {
    provider: trimmed.slice(0, separator),
    modelId: trimmed.slice(separator + 1),
  };
}

function ensureRuntimeError(operation: string, detail: string, cause: unknown): PiRuntimeError {
  return PiRuntimeError.is(cause) ? cause : new PiRuntimeError({ operation, detail, cause });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isPiRpcResponse(message: PiRpcResponse | PiRpcEvent): message is PiRpcResponse {
  return message.type === "response" && "command" in message && "success" in message;
}

function parseRpcLine(line: string): PiRpcResponse | PiRpcEvent | null {
  const trimmed = line.replace(/\r$/u, "").trim();
  if (trimmed.length === 0) {
    return null;
  }
  const parsed: unknown = decodeUnknownJsonString(trimmed);
  if (!isRecord(parsed) || typeof parsed.type !== "string") {
    throw new Error(`Pi RPC emitted non-object JSON line: ${trimmed}`);
  }
  if (parsed.type === "response") {
    return parsed as unknown as PiRpcResponse;
  }
  return parsed as PiRpcEvent;
}

function failPendingResponses(
  pendingRef: Ref.Ref<ReadonlyMap<string, Deferred.Deferred<PiRpcResponse, PiRuntimeError>>>,
  error: PiRuntimeError,
): Effect.Effect<void> {
  return Ref.getAndSet(pendingRef, new Map()).pipe(
    Effect.flatMap((pending) =>
      Effect.forEach(
        pending.values(),
        (deferred) => Deferred.fail(deferred, error).pipe(Effect.ignore),
        { discard: true },
      ),
    ),
  );
}

const makePiRuntime = Effect.gen(function* () {
  const spawner = yield* ChildProcessSpawner.ChildProcessSpawner;

  const resolveCommand = (command: string, args: ReadonlyArray<string>, env?: NodeJS.ProcessEnv) =>
    resolveSpawnCommand(command, args, env ? { env } : {});

  const runPiCommand: PiRuntimeShape["runPiCommand"] = (input) =>
    Effect.gen(function* () {
      const spawnCommand = yield* resolveCommand(input.binaryPath, input.args, input.environment);
      const child = yield* spawner.spawn(
        ChildProcess.make(spawnCommand.command, spawnCommand.args, {
          ...(input.cwd ? { cwd: input.cwd } : {}),
          shell: spawnCommand.shell,
          ...(input.environment ? { env: input.environment } : { extendEnv: true }),
        }),
      );
      const [stdout, stderr, code] = yield* Effect.all(
        [collectStreamAsString(child.stdout), collectStreamAsString(child.stderr), child.exitCode],
        { concurrency: "unbounded" },
      );
      const exitCode = Number(code);
      if (yield* isWindowsCommandNotFound(exitCode, stderr)) {
        return yield* new PiRuntimeError({
          operation: "runPiCommand",
          detail: `spawn ${input.binaryPath} ENOENT`,
        });
      }
      return {
        stdout,
        stderr,
        code: exitCode,
      } satisfies PiCommandResult;
    }).pipe(
      Effect.scoped,
      Effect.mapError((cause) =>
        ensureRuntimeError(
          "runPiCommand",
          `Failed to execute '${input.binaryPath} ${input.args.join(" ")}': ${piRuntimeErrorDetail(cause)}`,
          cause,
        ),
      ),
    );

  const startPiRpcSession: PiRuntimeShape["startPiRpcSession"] = (input) =>
    Effect.gen(function* () {
      const runtimeScope = yield* Scope.Scope;
      const args = input.args ?? buildPiRpcArgs();
      const spawnCommand = yield* resolveCommand(input.binaryPath, args, input.environment);
      const stdinQueue = yield* Queue.unbounded<string>();
      const eventQueue = yield* Queue.unbounded<PiRpcEvent>();
      const pendingRef = yield* Ref.make<
        ReadonlyMap<string, Deferred.Deferred<PiRpcResponse, PiRuntimeError>>
      >(new Map());
      const stderrRef = yield* Ref.make("");

      const child = yield* spawner
        .spawn(
          ChildProcess.make(spawnCommand.command, spawnCommand.args, {
            ...(input.cwd ? { cwd: input.cwd } : {}),
            shell: spawnCommand.shell,
            stdin: {
              stream: Stream.fromQueue(stdinQueue).pipe(Stream.encodeText),
              endOnDone: false,
            },
            ...(input.environment ? { env: input.environment } : { extendEnv: true }),
          }),
        )
        .pipe(
          Effect.provideService(Scope.Scope, runtimeScope),
          Effect.mapError(
            (cause) =>
              new PiRuntimeError({
                operation: "startPiRpcSession",
                detail: `Failed to spawn Pi RPC process: ${piRuntimeErrorDetail(cause)}`,
                cause,
              }),
          ),
        );

      const stopChild = child
        .kill({ killSignal: "SIGTERM", forceKillAfter: "1 second" })
        .pipe(
          Effect.ignore,
          Effect.andThen(Queue.shutdown(stdinQueue).pipe(Effect.ignore)),
          Effect.andThen(Queue.shutdown(eventQueue).pipe(Effect.ignore)),
        );
      yield* Scope.addFinalizer(runtimeScope, stopChild);

      const stdoutBufferRef = yield* Ref.make("");
      const handleMessage = (message: PiRpcResponse | PiRpcEvent) => {
        if (isPiRpcResponse(message) && typeof message.id === "string") {
          const id = message.id;
          return Ref.modify(pendingRef, (pending) => {
            const deferred = pending.get(id);
            if (!deferred) {
              return [Effect.void, pending] as const;
            }
            const next = new Map(pending);
            next.delete(id);
            return [Deferred.succeed(deferred, message).pipe(Effect.ignore), next] as const;
          }).pipe(Effect.flatten);
        }
        return Queue.offer(eventQueue, message as PiRpcEvent).pipe(Effect.ignore);
      };
      const handleStdoutChunk = (chunk: string) =>
        Ref.modify(stdoutBufferRef, (buffer) => {
          const combined = `${buffer}${chunk}`;
          const lines: string[] = [];
          let start = 0;
          let separator = combined.indexOf("\n", start);
          while (separator >= 0) {
            lines.push(combined.slice(start, separator));
            start = separator + 1;
            separator = combined.indexOf("\n", start);
          }
          return [lines, combined.slice(start)] as const;
        }).pipe(
          Effect.flatMap((lines) =>
            Effect.forEach(
              lines,
              (line) =>
                Effect.try({
                  try: () => parseRpcLine(line),
                  catch: (cause) =>
                    new PiRuntimeError({
                      operation: "readRpcLine",
                      detail: `Failed to parse Pi RPC JSONL output: ${piRuntimeErrorDetail(cause)}`,
                      cause,
                    }),
                }).pipe(
                  Effect.flatMap((message) => (message ? handleMessage(message) : Effect.void)),
                ),
              { discard: true },
            ),
          ),
        );

      const stdoutFiber = yield* child.stdout.pipe(
        Stream.decodeText(),
        Stream.runForEach(handleStdoutChunk),
        Effect.catchCause((cause) =>
          failPendingResponses(
            pendingRef,
            new PiRuntimeError({
              operation: "readRpcStdout",
              detail: `Failed to read Pi RPC stdout: ${piRuntimeErrorDetail(cause)}`,
              cause,
            }),
          ),
        ),
        Effect.forkIn(runtimeScope),
      );
      const stderrFiber = yield* child.stderr.pipe(
        Stream.decodeText(),
        Stream.runForEach((chunk) => Ref.update(stderrRef, (stderr) => `${stderr}${chunk}`)),
        Effect.ignore,
        Effect.forkIn(runtimeScope),
      );
      const exitFiber = yield* child.exitCode.pipe(
        Effect.flatMap((code) =>
          Effect.gen(function* () {
            const stderr = yield* Ref.get(stderrRef);
            yield* failPendingResponses(
              pendingRef,
              new PiRuntimeError({
                operation: "processExit",
                detail: `Pi RPC process exited before responding (code: ${Number(code)}).${
                  stderr.trim() ? ` stderr:\n${stderr.trim()}` : ""
                }`,
                cause: { exitCode: Number(code), stderr },
              }),
            );
            yield* Queue.shutdown(eventQueue).pipe(Effect.ignore);
          }),
        ),
        Effect.ignore,
        Effect.forkIn(runtimeScope),
      );
      yield* Scope.addFinalizer(
        runtimeScope,
        Effect.all(
          [Fiber.interrupt(stdoutFiber), Fiber.interrupt(stderrFiber), Fiber.interrupt(exitFiber)],
          { discard: true },
        ).pipe(Effect.ignore),
      );

      const request: PiRpcSession["request"] = (command, options) =>
        Effect.gen(function* () {
          const id = command.id ?? NodeCrypto.randomUUID();
          const deferred = yield* Deferred.make<PiRpcResponse, PiRuntimeError>();
          yield* Ref.update(pendingRef, (pending) => {
            const next = new Map(pending);
            next.set(id, deferred);
            return next;
          });
          const line = `${encodeUnknownJsonString({ ...command, id })}\n`;
          const offered = yield* Queue.offer(stdinQueue, line);
          if (!offered) {
            yield* Ref.update(pendingRef, (pending) => {
              const next = new Map(pending);
              next.delete(id);
              return next;
            });
            return yield* new PiRuntimeError({
              operation: "writeRpcStdin",
              detail: `Failed to write Pi RPC command '${command.type}': stdin queue is closed.`,
            });
          }
          const timeoutMs =
            options?.timeoutMs ?? input.requestTimeoutMs ?? DEFAULT_RPC_REQUEST_TIMEOUT_MS;
          return yield* Deferred.await(deferred).pipe(
            Effect.timeoutOrElse({
              duration: `${timeoutMs} millis`,
              orElse: () =>
                Effect.fail(
                  new PiRuntimeError({
                    operation: "requestTimeout",
                    detail: `Pi RPC command '${command.type}' timed out after ${timeoutMs}ms.`,
                  }),
                ),
            }),
            Effect.tapError(() =>
              Ref.update(pendingRef, (pending) => {
                const next = new Map(pending);
                next.delete(id);
                return next;
              }),
            ),
          );
        });

      return {
        pid: Number(child.pid),
        events: Stream.fromQueue(eventQueue),
        exitCode: child.exitCode.pipe(
          Effect.map(Number),
          Effect.catchCause(() => Effect.succeed(-1)),
        ),
        request,
        stop: stopChild,
      } satisfies PiRpcSession;
    }).pipe(
      Effect.mapError((cause) =>
        ensureRuntimeError(
          "startPiRpcSession",
          `Failed to start Pi RPC session: ${piRuntimeErrorDetail(cause)}`,
          cause,
        ),
      ),
    );

  return {
    runPiCommand,
    startPiRpcSession,
  } satisfies PiRuntimeShape;
});

export class PiRuntime extends Context.Service<PiRuntime, PiRuntimeShape>()(
  "t3/provider/piRpcRuntime/PiRuntime",
) {}

export const PiRuntimeLive = Layer.effect(PiRuntime, makePiRuntime);
