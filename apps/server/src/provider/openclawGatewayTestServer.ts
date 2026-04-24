import { randomUUID } from "node:crypto";

type GatewayRequest = {
  readonly id: string;
  readonly method: string;
  readonly params?: unknown;
};

type GatewayEvent = {
  readonly event: string;
  readonly payload?: unknown;
};

type GatewayResponse = {
  readonly payload?: unknown;
  readonly error?: {
    readonly code: string;
    readonly message: string;
    readonly details?: unknown;
    readonly retryable?: boolean;
    readonly retryAfterMs?: number;
  };
  readonly events?: ReadonlyArray<GatewayEvent>;
};

interface MockSocketState {
  sessionSubscription: boolean;
  readonly sessionMessageSubscriptions: Set<string>;
}

export interface MockOpenClawGatewayContext {
  readonly calls: Array<GatewayRequest>;
}

export interface MockOpenClawGatewayOptions {
  readonly authScopes?: ReadonlyArray<string>;
  readonly handleRequest?: (
    request: GatewayRequest,
    context: MockOpenClawGatewayContext,
  ) => GatewayResponse | undefined | Promise<GatewayResponse | undefined>;
}

export interface MockOpenClawGatewayServer {
  readonly url: string;
  readonly calls: Array<GatewayRequest>;
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

function defaultHelloOk(scopes: ReadonlyArray<string>) {
  return {
    type: "hello-ok" as const,
    protocol: 3,
    server: {
      version: "2026.4.5",
      connId: randomUUID(),
    },
    features: {
      methods: [
        "status",
        "models.list",
        "models.authStatus",
        "sessions.create",
        "sessions.resolve",
        "sessions.send",
        "sessions.abort",
        "sessions.list",
        "sessions.preview",
        "sessions.subscribe",
        "sessions.messages.subscribe",
      ],
      events: ["connect.challenge", "session.message", "sessions.changed", "tick"],
    },
    snapshot: {
      presence: [],
      health: {},
      stateVersion: {
        presence: 1,
        health: 1,
      },
      uptimeMs: 1_000,
    },
    auth: {
      role: "operator",
      scopes: [...scopes],
    },
    policy: {
      maxPayload: 1_000_000,
      maxBufferedBytes: 1_000_000,
      tickIntervalMs: 30_000,
    },
  };
}

function defaultResponse(
  request: GatewayRequest,
  socketState: MockSocketState,
  options: MockOpenClawGatewayOptions,
): GatewayResponse {
  const params = asRecord(request.params) ?? {};
  const authScopes = options.authScopes ?? ["operator.admin", "operator.read", "operator.write"];

  switch (request.method) {
    case "connect":
      return {
        payload: defaultHelloOk(authScopes),
      };
    case "status":
      return {
        payload: {
          version: "2026.4.5",
          account: "Test Gateway User",
        },
      };
    case "models.list":
      return {
        payload: {
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
    case "sessions.create": {
      const key = trimOrNull(params.key) ?? `session:${randomUUID()}`;
      return {
        payload: {
          ok: true,
          key,
          sessionId: randomUUID(),
        },
        events: socketState.sessionSubscription
          ? [
              {
                event: "sessions.changed",
                payload: {
                  sessionKey: key,
                  reason: "create",
                  ts: Date.now(),
                },
              },
            ]
          : [],
      };
    }
    case "sessions.resolve": {
      const key =
        trimOrNull(params.key) ?? trimOrNull(params.sessionId) ?? `session:${randomUUID()}`;
      return {
        payload: {
          ok: true,
          key,
          sessionId: randomUUID(),
        },
      };
    }
    case "sessions.subscribe":
      socketState.sessionSubscription = true;
      return {
        payload: {
          ok: true,
        },
      };
    case "sessions.messages.subscribe": {
      const key = trimOrNull(params.key) ?? `session:${randomUUID()}`;
      socketState.sessionMessageSubscriptions.add(key);
      return {
        payload: {
          ok: true,
          key,
        },
      };
    }
    case "sessions.send": {
      const key = trimOrNull(params.key) ?? `session:${randomUUID()}`;
      const runId = trimOrNull(params.idempotencyKey) ?? randomUUID();
      const responseEvents: Array<GatewayEvent> = [];

      if (socketState.sessionMessageSubscriptions.has(key)) {
        responseEvents.push({
          event: "session.message",
          payload: {
            sessionKey: key,
            messageId: `assistant:${runId}`,
            message: {
              id: `assistant:${runId}`,
              role: "assistant",
              runId,
              content: [
                {
                  type: "text",
                  text: "hello from openclaw",
                },
              ],
            },
          },
        });
      }

      if (socketState.sessionSubscription) {
        responseEvents.push({
          event: "sessions.changed",
          payload: {
            sessionKey: key,
            reason: "send",
            ts: Date.now(),
          },
        });
      }

      return {
        payload: {
          ok: true,
          runId,
          status: "accepted",
        },
        events: responseEvents,
      };
    }
    case "sessions.abort":
      return {
        payload: {
          ok: true,
          abortedRunId: trimOrNull(params.runId),
          status: "aborted",
        },
      };
    case "approval.respond":
      return {
        payload: { ok: true },
        events: [
          {
            event: "approval.resolved",
            payload: {
              requestId: trimOrNull(params.requestId),
              decision: trimOrNull(params.decision) ?? "accept",
            },
          },
        ],
      };
    case "user-input.respond":
      return {
        payload: { ok: true },
        events: [
          {
            event: "user-input.resolved",
            payload: {
              requestId: trimOrNull(params.requestId),
              answers: asRecord(params.answers) ?? {},
            },
          },
        ],
      };
    default:
      return {
        error: {
          code: "METHOD_NOT_FOUND",
          message: `Method not found: ${request.method}`,
        },
      };
  }
}

function sendChallenge(socket: { send(data: string): void }) {
  socket.send(
    JSON.stringify({
      type: "event",
      event: "connect.challenge",
      payload: {
        nonce: randomUUID(),
        ts: Date.now(),
      },
    }),
  );
}

function sendResponse(
  socket: {
    send(data: string): void;
  },
  requestId: string,
  response: GatewayResponse,
) {
  if (response.error) {
    socket.send(
      JSON.stringify({
        type: "res",
        id: requestId,
        ok: false,
        error: response.error,
      }),
    );
    return;
  }

  socket.send(
    JSON.stringify({
      type: "res",
      id: requestId,
      ok: true,
      payload: response.payload ?? null,
    }),
  );

  for (const event of response.events ?? []) {
    socket.send(
      JSON.stringify({
        type: "event",
        event: event.event,
        payload: event.payload ?? {},
      }),
    );
  }
}

export async function startMockOpenClawGateway(
  options: MockOpenClawGatewayOptions = {},
): Promise<MockOpenClawGatewayServer> {
  const calls: Array<GatewayRequest> = [];
  const context: MockOpenClawGatewayContext = { calls };

  if (typeof Bun !== "undefined") {
    const socketStates = new WeakMap<object, MockSocketState>();
    const server = Bun.serve({
      port: 0,
      fetch(request, bunServer) {
        if (bunServer.upgrade(request)) {
          return;
        }
        return new Response("not found", { status: 404 });
      },
      websocket: {
        open(ws: object & { send(data: string): void }) {
          socketStates.set(ws, {
            sessionSubscription: false,
            sessionMessageSubscriptions: new Set(),
          });
          sendChallenge(ws);
        },
        async message(ws: object & { send(data: string): void }, message: string | Buffer) {
          const request = JSON.parse(String(message)) as {
            type: "req";
            id: string;
            method: string;
            params?: unknown;
          };
          const normalizedRequest: GatewayRequest = {
            id: request.id,
            method: request.method,
            params: request.params,
          };
          calls.push(normalizedRequest);
          const socketState =
            socketStates.get(ws) ??
            ({
              sessionSubscription: false,
              sessionMessageSubscriptions: new Set(),
            } satisfies MockSocketState);
          const response =
            (await options.handleRequest?.(normalizedRequest, context)) ??
            defaultResponse(normalizedRequest, socketState, options);
          sendResponse(ws, request.id, response);
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

  const [{ createServer }, { WebSocketServer }] = await Promise.all([
    import("node:http"),
    import("ws"),
  ]);
  type NodeWebSocket = import("ws").WebSocket;
  type NodeWebSocketMessage = import("ws").RawData;
  const socketStates = new WeakMap<object, MockSocketState>();
  const httpServer = createServer((_request, response) => {
    response.writeHead(404, { "content-type": "text/plain" });
    response.end("not found");
  });
  const wsServer = new WebSocketServer({ server: httpServer });

  wsServer.on("connection", (socket: NodeWebSocket) => {
    socketStates.set(socket, {
      sessionSubscription: false,
      sessionMessageSubscriptions: new Set(),
    });
    sendChallenge({
      send(data: string) {
        socket.send(data);
      },
    });

    socket.on("message", async (message: NodeWebSocketMessage) => {
      const request = JSON.parse(String(message)) as {
        type: "req";
        id: string;
        method: string;
        params?: unknown;
      };
      const normalizedRequest: GatewayRequest = {
        id: request.id,
        method: request.method,
        params: request.params,
      };
      calls.push(normalizedRequest);
      const socketState =
        socketStates.get(socket) ??
        ({
          sessionSubscription: false,
          sessionMessageSubscriptions: new Set(),
        } satisfies MockSocketState);
      const response =
        (await options.handleRequest?.(normalizedRequest, context)) ??
        defaultResponse(normalizedRequest, socketState, options);
      sendResponse(
        {
          send(data: string) {
            socket.send(data);
          },
        },
        request.id,
        response,
      );
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
    throw new Error("Mock OpenClaw gateway failed to bind a TCP port.");
  }

  return {
    url: `ws://127.0.0.1:${address.port}`,
    calls,
    stop() {
      for (const client of wsServer.clients) {
        client.close();
      }
      wsServer.close();
      httpServer.close();
    },
  };
}
