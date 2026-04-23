import { createRequire } from "node:module";

import { Effect, FileSystem, Layer, Path } from "effect";
import { PtyAdapter } from "../Services/PTY.ts";
import {
  PtySpawnError,
  type PtyAdapterShape,
  type PtyExitEvent,
  type PtyProcess,
} from "../Services/PTY.ts";

let didEnsureSpawnHelperExecutable = false;

const resolveNodePtySpawnHelperPath = Effect.gen(function* () {
  const requireForNodePty = createRequire(import.meta.url);
  const path = yield* Path.Path;
  const fs = yield* FileSystem.FileSystem;

  const packageJsonPath = requireForNodePty.resolve("node-pty/package.json");
  const packageDir = path.dirname(packageJsonPath);
  const candidates = [
    path.join(packageDir, "build", "Release", "spawn-helper"),
    path.join(packageDir, "build", "Debug", "spawn-helper"),
    path.join(packageDir, "prebuilds", `${process.platform}-${process.arch}`, "spawn-helper"),
  ];

  for (const candidate of candidates) {
    if (yield* fs.exists(candidate)) {
      return candidate;
    }
  }
  return null;
}).pipe(Effect.orElseSucceed(() => null));

export const ensureNodePtySpawnHelperExecutable = Effect.fn(function* (explicitPath?: string) {
  const fs = yield* FileSystem.FileSystem;
  if (process.platform === "win32") return;
  if (!explicitPath && didEnsureSpawnHelperExecutable) return;

  const helperPath = explicitPath ?? (yield* resolveNodePtySpawnHelperPath);
  if (!helperPath) return;
  if (!explicitPath) {
    didEnsureSpawnHelperExecutable = true;
  }

  if (!(yield* fs.exists(helperPath))) {
    return;
  }

  // Best-effort: avoid FileSystem.stat in packaged mode where some fs metadata can be missing.
  yield* fs.chmod(helperPath, 0o755).pipe(Effect.orElseSucceed(() => undefined));
});

interface NodePtyDisposable {
  dispose(): void;
}

interface NodePtyHandle {
  readonly pid: number;
  write(data: string): void;
  resize(cols: number, rows: number): void;
  kill(signal?: string): void;
  onData(callback: (data: string) => void): NodePtyDisposable;
  onExit(callback: (event: { exitCode: number; signal?: number }) => void): NodePtyDisposable;
}

interface NodePtyModule {
  spawn(
    file: string,
    args?: ReadonlyArray<string>,
    options?: {
      cwd?: string;
      cols?: number;
      rows?: number;
      env?: Record<string, string | undefined>;
      name?: string;
    },
  ): NodePtyHandle;
}

class NodePtyProcess implements PtyProcess {
  private readonly process: NodePtyHandle;

  constructor(process: NodePtyHandle) {
    this.process = process;
  }

  get pid(): number {
    return this.process.pid;
  }

  write(data: string): void {
    this.process.write(data);
  }

  resize(cols: number, rows: number): void {
    this.process.resize(cols, rows);
  }

  kill(signal?: string): void {
    this.process.kill(signal);
  }

  onData(callback: (data: string) => void): () => void {
    const disposable = this.process.onData(callback);
    return () => {
      disposable.dispose();
    };
  }

  onExit(callback: (event: PtyExitEvent) => void): () => void {
    const disposable = this.process.onExit((event) => {
      callback({
        exitCode: event.exitCode,
        signal: event.signal ?? null,
      });
    });
    return () => {
      disposable.dispose();
    };
  }
}

export const layer = Layer.effect(
  PtyAdapter,
  Effect.gen(function* () {
    const fs = yield* FileSystem.FileSystem;
    const path = yield* Path.Path;
    const requireForNodePty = createRequire(import.meta.url);

    const nodePty = yield* Effect.sync(() => {
      try {
        return requireForNodePty("node-pty") as NodePtyModule;
      } catch {
        return null;
      }
    });

    const ensureNodePtySpawnHelperExecutableCached = yield* Effect.cached(
      ensureNodePtySpawnHelperExecutable().pipe(
        Effect.provideService(FileSystem.FileSystem, fs),
        Effect.provideService(Path.Path, path),
        Effect.orElseSucceed(() => undefined),
      ),
    );

    return {
      spawn: Effect.fn(function* (input) {
        yield* ensureNodePtySpawnHelperExecutableCached;

        if (!nodePty) {
          return yield* Effect.fail(
            new PtySpawnError({
              adapter: "node-pty",
              message:
                'Optional dependency "node-pty" is not installed. Install dependencies in an environment with native build tools, or run the server with Bun so it can use the Bun PTY adapter instead.',
            }),
          );
        }

        const ptyProcess = yield* Effect.try({
          try: () =>
            nodePty.spawn(input.shell, input.args ?? [], {
              cwd: input.cwd,
              cols: input.cols,
              rows: input.rows,
              env: input.env,
              name: globalThis.process.platform === "win32" ? "xterm-color" : "xterm-256color",
            }),
          catch: (cause) =>
            new PtySpawnError({
              adapter: "node-pty",
              message: cause instanceof Error ? cause.message : "Failed to spawn PTY process",
              cause,
            }),
        });
        return new NodePtyProcess(ptyProcess);
      }),
    } satisfies PtyAdapterShape;
  }),
);
