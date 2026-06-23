// @effect-diagnostics nodeBuiltinImport:off
import { describe, expect, it } from "@effect/vitest";
import * as NodeServices from "@effect/platform-node/NodeServices";
import * as Effect from "effect/Effect";
import * as Fiber from "effect/Fiber";
import * as Layer from "effect/Layer";
import * as Stream from "effect/Stream";

import {
  buildPiRpcArgs,
  parsePiModelSlug,
  PiRuntime,
  PiRuntimeError,
  PiRuntimeLive,
} from "./piRpcRuntime.ts";

const runtimeLayer = PiRuntimeLive.pipe(Layer.provide(NodeServices.layer));

const fakeRpcPeer = `
process.stdin.setEncoding("utf8");
let buffer = "";
function write(value) {
  process.stdout.write(JSON.stringify(value) + "\\n");
}
process.stdin.on("data", (chunk) => {
  buffer += chunk;
  let index = buffer.indexOf("\\n");
  while (index >= 0) {
    const line = buffer.slice(0, index).replace(/\\r$/, "");
    buffer = buffer.slice(index + 1);
    if (line.trim().length > 0) {
      const command = JSON.parse(line);
      write({ type: "agent_start" });
      write({
        id: command.id,
        type: "response",
        command: command.type,
        success: true,
        data: {
          sessionId: "test-session",
          thinkingLevel: "off",
          isStreaming: false,
          isCompacting: false,
          steeringMode: "all",
          followUpMode: "all",
          autoCompactionEnabled: false,
          messageCount: 0,
          pendingMessageCount: 0
        }
      });
    }
    index = buffer.indexOf("\\n");
  }
});
`;

describe("Pi RPC helpers", () => {
  it("builds Pi RPC startup args without fake model defaults", () => {
    expect(
      buildPiRpcArgs({
        provider: "baseten",
        modelId: "zai-org/GLM-5.2",
        thinkingLevel: "high",
        noSession: true,
      }),
    ).toEqual([
      "--mode",
      "rpc",
      "--no-session",
      "--provider",
      "baseten",
      "--model",
      "zai-org/GLM-5.2",
      "--thinking",
      "high",
    ]);
  });

  it("adds Pi extension hooks for MCP config and appended system prompts", () => {
    expect(
      buildPiRpcArgs({
        appendSystemPrompt: "Use T3 preview MCP tools.",
        mcpConfigPath: "/tmp/t3-pi-mcp.json",
        noSession: true,
      }),
    ).toEqual([
      "--mode",
      "rpc",
      "--no-session",
      "--append-system-prompt",
      "Use T3 preview MCP tools.",
      "--mcp-config",
      "/tmp/t3-pi-mcp.json",
    ]);
  });

  it("parses Pi model slugs on the first slash", () => {
    expect(parsePiModelSlug("baseten/zai-org/GLM-5.2")).toEqual({
      provider: "baseten",
      modelId: "zai-org/GLM-5.2",
    });
    expect(parsePiModelSlug("missing-provider-separator")).toBeNull();
  });
});

it.layer(runtimeLayer)("PiRuntime", (it) => {
  it.effect("starts a JSONL RPC process, correlates responses, and streams events", () =>
    Effect.gen(function* () {
      const runtime = yield* PiRuntime;
      const session = yield* runtime.startPiRpcSession({
        binaryPath: process.execPath,
        args: ["-e", fakeRpcPeer],
        requestTimeoutMs: 2_000,
      });
      const eventFiber = yield* Effect.forkScoped(
        Stream.runCollect(session.events.pipe(Stream.take(1))),
      );

      const response = yield* session.request({ type: "get_state" });
      const events = yield* Fiber.join(eventFiber);

      expect(response).toMatchObject({
        type: "response",
        command: "get_state",
        success: true,
      });
      expect(Array.from(events)).toEqual([{ type: "agent_start" }]);

      yield* session.stop;
    }),
  );

  it.effect("returns a structured error for a missing Pi binary", () =>
    Effect.gen(function* () {
      const runtime = yield* PiRuntime;
      const error = yield* runtime
        .startPiRpcSession({
          binaryPath: "definitely-missing-pi-binary-for-test",
          args: [],
          requestTimeoutMs: 100,
        })
        .pipe(Effect.flip);

      expect(PiRuntimeError.is(error)).toBe(true);
      expect(error.detail).toContain("Failed to spawn Pi RPC process");
    }),
  );
});
