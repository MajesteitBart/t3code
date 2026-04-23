import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";

import { WebSocketServer } from "ws";

type JsonRpcRequest = {
  readonly id: string;
  readonly method: string;
  readonly params?: unknown;
};

type JsonRpcNotification = {
  readonly method: string;
  readonly params?: unknown;
};

type JsonRpcResponse = {
  readonly result?: unknown;
  readonly error?: {
    readonly code: number;
    readonly message: string;
    readonly data?: unknown;
  };
  readonly notifications?: ReadonlyArray<JsonRpcNotification>;
};

export interface MockOpenClawGatewayContext {
  readonly calls: Array<JsonRpcRequest>;
}

export interface MockOpenClawGatewayOptions {
  readonly handleRequest?: (
    request: JsonRpcRequest,
    context: MockOpenClawGatewayContext,
  ) => JsonRpcResponse | undefined | Promise<JsonRpcResponse | undefined>;
}

export interface MockOpenClawGatewayServer {
  readonly url: string;
  readonly calls: Array<JsonRpcRequest>;
  stop(): void;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function trimOrNull(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function defaultResponse(request: JsonRpcRequest): JsonRpcResponse {
  const params = asRecord(request.params) ?? {};
  switch (request.method) {
    case "auth.authenticate":
      return {
        result: {
          authenticated: true,
          label: "Test Gateway User",
        },
      };
    case "status":
      return {
        result: {
          version: "2026.4.5",
          account: "Test Gateway User",
        },
      };
    case "models.list":
      return {
        result: {
          models: [
            {
              id: "openai-codex/gpt-5.4",
              name: "GPT-5.4",
              provider: "openai-codex",
            },
            {
              id: "openai/gpt-5-mini",
              name: "GPT-5 Mini",
              provider: "openai",
            },
          ],
        },
      };
    case "session.create": {
      const sessionId = randomUUID();
      return {
        result: { sessionId },
        notifications: [
          { method: "session.started", params: { sessionId } },
          { method: "session.configured", params: params.modelSelection ?? {} },
          { method: "session.state.changed", params: { state: "ready" } },
        ],
      };
    }
    case "session.resume": {
      const sessionId = trimOrNull(params.sessionId) ?? randomUUID();
      return {
        result: { sessionId },
        notifications: [
          { method: "session.started", params: { sessionId, resumed: true } },
          { method: "session.state.changed", params: { state: "ready" } },
        ],
      };
    }
    case "session.sendTurn": {
      const turnId = randomUUID();
      const itemId = `assistant:${turnId}`;
      return {
        result: { turnId },
        notifications: [
          {
            method: "turn.started",
            params: {
              turnId,
              model: trimOrNull(asRecord(params.modelSelection)?.model) ?? "openai-codex/gpt-5.4",
            },
          },
          {
            method: "item.started",
            params: {
              turnId,
              itemId,
              itemType: "assistant_message",
              status: "inProgress",
              title: "Assistant",
            },
          },
          {
            method: "content.delta",
            params: {
              turnId,
              itemId,
              streamKind: "assistant_text",
              delta: "hello from openclaw",
            },
          },
          {
            method: "item.completed",
            params: {
              turnId,
              itemId,
              itemType: "assistant_message",
              status: "completed",
              title: "Assistant",
            },
          },
          {
            method: "turn.completed",
            params: {
              turnId,
              state: "completed",
            },
          },
        ],
      };
    }
    case "session.interrupt":
      return {
        result: { ok: true },
        notifications: [{ method: "turn.aborted", params: { reason: "interrupted" } }],
      };
    case "session.stop":
      return {
        result: { ok: true },
        notifications: [{ method: "session.exited", params: { reason: "stopped" } }],
      };
    case "approval.respond":
      return {
        result: { ok: true },
        notifications: [
          {
            method: "approval.resolved",
            params: {
              requestId: trimOrNull(params.requestId),
              decision: trimOrNull(params.decision) ?? "accept",
            },
          },
        ],
      };
    case "user-input.respond":
      return {
        result: { ok: true },
        notifications: [
          {
            method: "user-input.resolved",
            params: {
              requestId: trimOrNull(params.requestId),
              answers: asRecord(params.answers) ?? {},
            },
          },
        ],
      };
    default:
      return {
        error: {
          code: -32601,
          message: `Method not found: ${request.method}`,
        },
      };
  }
}

export async function startMockOpenClawGateway(
  options: MockOpenClawGatewayOptions = {},
): Promise<MockOpenClawGatewayServer> {
  const calls: Array<JsonRpcRequest> = [];
  const context: MockOpenClawGatewayContext = { calls };

  if (typeof Bun !== "undefined") {
    const server = Bun.serve({
      port: 0,
      fetch(request, bunServer) {
        if (bunServer.upgrade(request)) {
          return;
        }
        return new Response("not found", { status: 404 });
      },
      websocket: {
        async message(ws, message) {
          const request = JSON.parse(String(message)) as JsonRpcRequest;
          calls.push(request);
          const response =
            (await options.handleRequest?.(request, context)) ?? defaultResponse(request);

          if (response.error) {
            ws.send(
              JSON.stringify({
                jsonrpc: "2.0",
                id: request.id,
                error: response.error,
              }),
            );
            return;
          }

          ws.send(
            JSON.stringify({
              jsonrpc: "2.0",
              id: request.id,
              result: response.result ?? null,
            }),
          );
          for (const notification of response.notifications ?? []) {
            ws.send(
              JSON.stringify({
                jsonrpc: "2.0",
                method: notification.method,
                params: notification.params ?? {},
              }),
            );
          }
        },
      },
    });

    return {
      url: `ws://127.0.0.1:${server.port}`,
      calls,
      stop() {
        server.stop(true);
      },
    };
  }

  const httpServer = createServer((_request, response) => {
    response.statusCode = 404;
    response.end("not found");
  });
  const webSocketServer = new WebSocketServer({ noServer: true });

  webSocketServer.on("connection", (socket) => {
    socket.on("message", async (message) => {
      const request = JSON.parse(String(message)) as JsonRpcRequest;
      calls.push(request);
      const response = (await options.handleRequest?.(request, context)) ?? defaultResponse(request);

      if (response.error) {
        socket.send(
          JSON.stringify({
            jsonrpc: "2.0",
            id: request.id,
            error: response.error,
          }),
        );
        return;
      }

      socket.send(
        JSON.stringify({
          jsonrpc: "2.0",
          id: request.id,
          result: response.result ?? null,
        }),
      );
      for (const notification of response.notifications ?? []) {
        socket.send(
          JSON.stringify({
            jsonrpc: "2.0",
            method: notification.method,
            params: notification.params ?? {},
          }),
        );
      }
    });
  });

  httpServer.on("upgrade", (request, socket, head) => {
    webSocketServer.handleUpgrade(request, socket, head, (webSocket) => {
      webSocketServer.emit("connection", webSocket, request);
    });
  });

  await new Promise<void>((resolve, reject) => {
    httpServer.once("error", reject);
    httpServer.listen(0, "127.0.0.1", () => {
      httpServer.off("error", reject);
      resolve();
    });
  });

  const address = httpServer.address();
  if (!address || typeof address === "string") {
    throw new Error("Failed to determine mock OpenClaw gateway address");
  }

  return {
    url: `ws://127.0.0.1:${(address as AddressInfo).port}`,
    calls,
    stop() {
      for (const client of webSocketServer.clients) {
        client.close();
      }
      webSocketServer.close();
      void httpServer.close();
    },
  };
}
