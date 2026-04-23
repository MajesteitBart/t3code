import { randomUUID } from "node:crypto";

export const OPENCLAW_GATEWAY_PROTOCOL_VERSION = 3;
export const DEFAULT_OPENCLAW_GATEWAY_URL = "ws://127.0.0.1:18789";
const DEFAULT_REQUEST_TIMEOUT_MS = 15_000;

type JsonRpcId = string;

type JsonRpcRequest = {
  readonly jsonrpc: "2.0";
  readonly id: JsonRpcId;
  readonly method: string;
  readonly params?: unknown;
};

type JsonRpcSuccessResponse = {
  readonly jsonrpc?: "2.0";
  readonly id: JsonRpcId;
  readonly result?: unknown;
};

type JsonRpcErrorResponse = {
  readonly jsonrpc?: "2.0";
  readonly id: JsonRpcId;
  readonly error?: {
    readonly code?: number;
    readonly message?: string;
    readonly data?: unknown;
  };
};

type JsonRpcNotification = {
  readonly jsonrpc?: "2.0";
  readonly method: string;
  readonly params?: unknown;
};

type PendingRequest = {
  readonly timeout: ReturnType<typeof setTimeout>;
  readonly resolve: (value: unknown) => void;
  readonly reject: (reason?: unknown) => void;
};

export interface OpenClawGatewayNotification {
  readonly method: string;
  readonly params: unknown;
}

export interface OpenClawGatewayConnectOptions {
  readonly url?: string | null | undefined;
  readonly token?: string | null | undefined;
  readonly password?: string | null | undefined;
  readonly requestTimeoutMs?: number;
}

export interface OpenClawGatewayConnection {
  readonly url: string;
  readonly notifications: AsyncIterable<OpenClawGatewayNotification>;
  call<TResult = unknown>(method: string, params?: unknown): Promise<TResult>;
  close(code?: number, reason?: string): Promise<void>;
}

class OpenClawGatewayError extends Error {
  readonly detail: unknown;

  constructor(message: string, detail?: unknown) {
    super(message);
    this.name = "OpenClawGatewayError";
    this.detail = detail;
  }
}

class AsyncQueue<T> implements AsyncIterable<T> {
  private readonly items: Array<T> = [];
  private readonly waiters: Array<(result: IteratorResult<T>) => void> = [];
  private closed = false;

  push(item: T): void {
    if (this.closed) {
      return;
    }
    const waiter = this.waiters.shift();
    if (waiter) {
      waiter({ done: false, value: item });
      return;
    }
    this.items.push(item);
  }

  end(): void {
    if (this.closed) {
      return;
    }
    this.closed = true;
    while (this.waiters.length > 0) {
      this.waiters.shift()?.({ done: true, value: undefined });
    }
  }

  [Symbol.asyncIterator](): AsyncIterator<T> {
    return {
      next: () => {
        const item = this.items.shift();
        if (item !== undefined) {
          return Promise.resolve({ done: false, value: item });
        }
        if (this.closed) {
          return Promise.resolve({ done: true, value: undefined });
        }
        return new Promise<IteratorResult<T>>((resolve) => {
          this.waiters.push(resolve);
        });
      },
    };
  }
}

function trimOrNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed && trimmed.length > 0 ? trimmed : null;
}

function normalizeGatewayUrl(url: string | null | undefined): string {
  const trimmed = trimOrNull(url) ?? DEFAULT_OPENCLAW_GATEWAY_URL;
  if (trimmed.startsWith("http://")) {
    return `ws://${trimmed.slice("http://".length)}`;
  }
  if (trimmed.startsWith("https://")) {
    return `wss://${trimmed.slice("https://".length)}`;
  }
  if (/^wss?:\/\//i.test(trimmed)) {
    return trimmed;
  }
  return `ws://${trimmed}`;
}

function gatewayErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof OpenClawGatewayError) {
    return error.message;
  }
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}

export function getOpenClawGatewayErrorMessage(error: unknown, fallback: string): string {
  return gatewayErrorMessage(error, fallback);
}

function isSuccessResponse(value: unknown): value is JsonRpcSuccessResponse {
  return Boolean(
    value &&
      typeof value === "object" &&
      "id" in value &&
      typeof (value as { id?: unknown }).id === "string" &&
      !("method" in value),
  );
}

function isErrorResponse(value: unknown): value is JsonRpcErrorResponse {
  return Boolean(
    value &&
      typeof value === "object" &&
      "id" in value &&
      typeof (value as { id?: unknown }).id === "string" &&
      "error" in value,
  );
}

function isNotification(value: unknown): value is JsonRpcNotification {
  return Boolean(
    value &&
      typeof value === "object" &&
      "method" in value &&
      typeof (value as { method?: unknown }).method === "string",
  );
}

export async function connectOpenClawGateway(
  options: OpenClawGatewayConnectOptions = {},
): Promise<OpenClawGatewayConnection> {
  const url = normalizeGatewayUrl(options.url);
  const requestTimeoutMs =
    typeof options.requestTimeoutMs === "number" && Number.isFinite(options.requestTimeoutMs)
      ? Math.max(1_000, Math.floor(options.requestTimeoutMs))
      : DEFAULT_REQUEST_TIMEOUT_MS;
  const notifications = new AsyncQueue<OpenClawGatewayNotification>();
  const pendingRequests = new Map<JsonRpcId, PendingRequest>();

  const socket = await new Promise<WebSocket>((resolve, reject) => {
    const ws = new WebSocket(url);
    const onOpen = () => {
      cleanup();
      resolve(ws);
    };
    const onError = (event: Event) => {
      cleanup();
      reject(
        new OpenClawGatewayError(`Couldn't connect to OpenClaw gateway at ${url}.`, event),
      );
    };
    const onClose = (event: CloseEvent) => {
      cleanup();
      reject(
        new OpenClawGatewayError(
          `OpenClaw gateway closed during connect (${event.code}${event.reason ? `: ${event.reason}` : ""}).`,
          event,
        ),
      );
    };
    const cleanup = () => {
      ws.removeEventListener("open", onOpen);
      ws.removeEventListener("error", onError);
      ws.removeEventListener("close", onClose);
    };
    ws.addEventListener("open", onOpen);
    ws.addEventListener("error", onError);
    ws.addEventListener("close", onClose);
  });

  const rejectPendingRequests = (error: unknown) => {
    for (const [id, pending] of pendingRequests) {
      clearTimeout(pending.timeout);
      pending.reject(error);
      pendingRequests.delete(id);
    }
  };

  socket.addEventListener("message", (event) => {
    let parsed: unknown;
    try {
      parsed = typeof event.data === "string" ? JSON.parse(event.data) : undefined;
    } catch (error) {
      rejectPendingRequests(
        new OpenClawGatewayError("Received invalid JSON from OpenClaw gateway.", error),
      );
      notifications.end();
      return;
    }

    if (isErrorResponse(parsed)) {
      const pending = pendingRequests.get(parsed.id);
      if (!pending) {
        return;
      }
      clearTimeout(pending.timeout);
      pendingRequests.delete(parsed.id);
      const message = trimOrNull(parsed.error?.message) ?? `OpenClaw RPC ${parsed.id} failed.`;
      pending.reject(new OpenClawGatewayError(message, parsed.error?.data));
      return;
    }

    if (isSuccessResponse(parsed)) {
      const pending = pendingRequests.get(parsed.id);
      if (!pending) {
        return;
      }
      clearTimeout(pending.timeout);
      pendingRequests.delete(parsed.id);
      pending.resolve(parsed.result);
      return;
    }

    if (isNotification(parsed)) {
      notifications.push({
        method: parsed.method,
        params: parsed.params,
      });
    }
  });

  socket.addEventListener("close", (event) => {
    notifications.end();
    rejectPendingRequests(
      new OpenClawGatewayError(
        `OpenClaw gateway connection closed (${event.code}${event.reason ? `: ${event.reason}` : ""}).`,
        event,
      ),
    );
  });

  socket.addEventListener("error", (event) => {
    rejectPendingRequests(new OpenClawGatewayError("OpenClaw gateway transport error.", event));
  });

  const connection: OpenClawGatewayConnection = {
    url,
    notifications,
    call<TResult>(method: string, params?: unknown): Promise<TResult> {
      const id = randomUUID();
      const request: JsonRpcRequest = {
        jsonrpc: "2.0",
        id,
        method,
        ...(params !== undefined ? { params } : {}),
      };

      return new Promise<TResult>((resolve, reject) => {
        const timeout = setTimeout(() => {
          pendingRequests.delete(id);
          reject(new OpenClawGatewayError(`OpenClaw RPC ${method} timed out.`));
        }, requestTimeoutMs);

        pendingRequests.set(id, { resolve, reject, timeout });

        try {
          socket.send(JSON.stringify(request));
        } catch (error) {
          clearTimeout(timeout);
          pendingRequests.delete(id);
          reject(
            new OpenClawGatewayError(
              `Failed to send OpenClaw RPC ${method}: ${gatewayErrorMessage(error, "send failed")}`,
              error,
            ),
          );
        }
      });
    },
    close(code?: number, reason?: string): Promise<void> {
      notifications.end();
      rejectPendingRequests(new OpenClawGatewayError("OpenClaw gateway connection closed."));
      if (socket.readyState === WebSocket.CLOSING || socket.readyState === WebSocket.CLOSED) {
        return Promise.resolve();
      }
      return new Promise<void>((resolve) => {
        const onClose = () => {
          socket.removeEventListener("close", onClose);
          resolve();
        };
        socket.addEventListener("close", onClose);
        socket.close(code, reason);
      });
    },
  };

  try {
    await connection.call("auth.authenticate", {
      protocol: OPENCLAW_GATEWAY_PROTOCOL_VERSION,
      ...(trimOrNull(options.token) ? { token: trimOrNull(options.token) } : {}),
      ...(trimOrNull(options.password) ? { password: trimOrNull(options.password) } : {}),
    });
  } catch (error) {
    await connection.close(1000, "auth_failed").catch(() => undefined);
    throw new OpenClawGatewayError(
      gatewayErrorMessage(error, "OpenClaw authentication failed."),
      error,
    );
  }

  return connection;
}
