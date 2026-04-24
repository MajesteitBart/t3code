import { randomUUID } from "node:crypto";

import packageJson from "../../package.json" with { type: "json" };

export const OPENCLAW_GATEWAY_PROTOCOL_VERSION = 3;
export const DEFAULT_OPENCLAW_GATEWAY_URL = "ws://127.0.0.1:18789";
const DEFAULT_REQUEST_TIMEOUT_MS = 15_000;
const CONNECT_CHALLENGE_EVENT = "connect.challenge";

type GatewayRequestId = string;

type GatewayRequestFrame = {
  readonly type: "req";
  readonly id: GatewayRequestId;
  readonly method: string;
  readonly params?: unknown;
};

type GatewayResponseError = {
  readonly code?: string;
  readonly message?: string;
  readonly details?: unknown;
  readonly retryable?: boolean;
  readonly retryAfterMs?: number;
};

type GatewayResponseFrame = {
  readonly type: "res";
  readonly id: GatewayRequestId;
  readonly ok: boolean;
  readonly payload?: unknown;
  readonly error?: GatewayResponseError;
};

type GatewayEventFrame = {
  readonly type: "event";
  readonly event: string;
  readonly payload?: unknown;
  readonly seq?: number;
  readonly stateVersion?: unknown;
};

type PendingRequest = {
  readonly method: string;
  readonly timeout: ReturnType<typeof setTimeout>;
  readonly resolve: (value: unknown) => void;
  readonly reject: (reason?: unknown) => void;
};

type ChallengeWaiter = {
  readonly timeout: ReturnType<typeof setTimeout>;
  readonly resolve: (nonce: string) => void;
  readonly reject: (reason?: unknown) => void;
};

export interface OpenClawGatewayNotification {
  readonly method: string;
  readonly params: unknown;
}

export interface OpenClawGatewayHelloOk {
  readonly type?: "hello-ok";
  readonly protocol?: number;
  readonly server?: {
    readonly version?: string;
    readonly connId?: string;
  };
  readonly features?: {
    readonly methods?: ReadonlyArray<string>;
    readonly events?: ReadonlyArray<string>;
  };
  readonly snapshot?: unknown;
  readonly auth?: {
    readonly deviceToken?: string;
    readonly role?: string;
    readonly scopes?: ReadonlyArray<string>;
    readonly issuedAtMs?: number;
  };
  readonly policy?: {
    readonly maxPayload?: number;
    readonly maxBufferedBytes?: number;
    readonly tickIntervalMs?: number;
  };
}

export interface OpenClawGatewayConnectOptions {
  readonly url?: string | null | undefined;
  readonly token?: string | null | undefined;
  readonly password?: string | null | undefined;
  readonly requestTimeoutMs?: number;
}

export interface OpenClawGatewayConnection {
  readonly url: string;
  readonly hello: OpenClawGatewayHelloOk;
  readonly notifications: AsyncIterable<OpenClawGatewayNotification>;
  call<TResult = unknown>(method: string, params?: unknown): Promise<TResult>;
  close(code?: number, reason?: string): Promise<void>;
}

export interface OpenClawGatewayErrorClassification {
  readonly kind: "connection" | "auth" | "pairing" | "missing_scope" | "protocol" | "other";
  readonly message: string;
  readonly code: string | null;
  readonly detailCode: string | null;
  readonly missingScope: string | null;
}

class OpenClawGatewayError extends Error {
  readonly detail: unknown;
  readonly code: string | null;
  readonly detailCode: string | null;
  readonly missingScope: string | null;
  readonly method: string | null;
  readonly phase: "connection" | "transport" | "protocol" | "request";
  readonly retryable: boolean | null;
  readonly retryAfterMs: number | null;

  constructor(
    message: string,
    options: {
      readonly detail?: unknown;
      readonly code?: string | null | undefined;
      readonly detailCode?: string | null | undefined;
      readonly missingScope?: string | null | undefined;
      readonly method?: string | null | undefined;
      readonly phase: "connection" | "transport" | "protocol" | "request";
      readonly retryable?: boolean | null | undefined;
      readonly retryAfterMs?: number | null | undefined;
    },
  ) {
    super(message);
    this.name = "OpenClawGatewayError";
    this.detail = options.detail;
    this.code = options.code ?? null;
    this.detailCode = options.detailCode ?? null;
    this.missingScope = options.missingScope ?? null;
    this.method = options.method ?? null;
    this.phase = options.phase;
    this.retryable = options.retryable ?? null;
    this.retryAfterMs = options.retryAfterMs ?? null;
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

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
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

function readDetailCode(details: unknown): string | null {
  const code = asRecord(details)?.code;
  return typeof code === "string" && code.trim().length > 0 ? code.trim() : null;
}

function readMissingScope(error: {
  readonly message?: string;
  readonly details?: unknown;
}): string | null {
  const fromDetails = asRecord(error.details)?.missingScope;
  if (typeof fromDetails === "string" && fromDetails.trim().length > 0) {
    return fromDetails.trim();
  }

  const message = trimOrNull(error.message);
  if (!message) {
    return null;
  }
  const match = /\bmissing scope:\s*([a-z0-9._-]+)/i.exec(message);
  return match?.[1]?.trim() ?? null;
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

function isResponseFrame(value: unknown): value is GatewayResponseFrame {
  return Boolean(
    value &&
    typeof value === "object" &&
    (value as { type?: unknown }).type === "res" &&
    typeof (value as { id?: unknown }).id === "string" &&
    typeof (value as { ok?: unknown }).ok === "boolean",
  );
}

function isEventFrame(value: unknown): value is GatewayEventFrame {
  return Boolean(
    value &&
    typeof value === "object" &&
    (value as { type?: unknown }).type === "event" &&
    typeof (value as { event?: unknown }).event === "string",
  );
}

function toGatewayRequestError(
  method: string,
  responseError: GatewayResponseError | undefined,
): OpenClawGatewayError {
  const message = trimOrNull(responseError?.message) ?? `OpenClaw request ${method} failed.`;
  const missingScope = readMissingScope(
    responseError?.message !== undefined
      ? {
          message: responseError.message,
          details: responseError?.details,
        }
      : { details: responseError?.details },
  );
  return new OpenClawGatewayError(message, {
    detail: responseError?.details,
    code: trimOrNull(responseError?.code),
    detailCode: readDetailCode(responseError?.details),
    missingScope,
    method,
    phase: "request",
    retryable: responseError?.retryable ?? null,
    retryAfterMs:
      typeof responseError?.retryAfterMs === "number" ? responseError.retryAfterMs : null,
  });
}

function isAuthDetailCode(detailCode: string | null): boolean {
  return (
    detailCode !== null && (detailCode.startsWith("AUTH_") || detailCode.startsWith("DEVICE_AUTH_"))
  );
}

function isPairingDetailCode(detailCode: string | null): boolean {
  return (
    detailCode === "PAIRING_REQUIRED" ||
    detailCode === "DEVICE_IDENTITY_REQUIRED" ||
    detailCode === "CONTROL_UI_DEVICE_IDENTITY_REQUIRED"
  );
}

export function classifyOpenClawGatewayError(error: unknown): OpenClawGatewayErrorClassification {
  if (error instanceof OpenClawGatewayError) {
    if (error.missingScope) {
      return {
        kind: "missing_scope",
        message: error.message,
        code: error.code,
        detailCode: error.detailCode,
        missingScope: error.missingScope,
      };
    }

    if (isPairingDetailCode(error.detailCode) || error.code === "NOT_PAIRED") {
      return {
        kind: "pairing",
        message: error.message,
        code: error.code,
        detailCode: error.detailCode,
        missingScope: null,
      };
    }

    if (isAuthDetailCode(error.detailCode) || error.code === "UNAUTHORIZED") {
      return {
        kind: "auth",
        message: error.message,
        code: error.code,
        detailCode: error.detailCode,
        missingScope: null,
      };
    }

    if (error.phase === "connection" || error.phase === "transport") {
      return {
        kind: "connection",
        message: error.message,
        code: error.code,
        detailCode: error.detailCode,
        missingScope: null,
      };
    }

    if (error.phase === "protocol") {
      return {
        kind: "protocol",
        message: error.message,
        code: error.code,
        detailCode: error.detailCode,
        missingScope: null,
      };
    }
  }

  const message = gatewayErrorMessage(error, "OpenClaw gateway request failed.");
  const lower = message.toLowerCase();
  if (
    lower.includes("couldn't connect") ||
    lower.includes("connect challenge timeout") ||
    lower.includes("econnrefused") ||
    lower.includes("enotfound") ||
    lower.includes("timed out")
  ) {
    return {
      kind: "connection",
      message,
      code: null,
      detailCode: null,
      missingScope: null,
    };
  }
  if (lower.includes("missing scope:")) {
    return {
      kind: "missing_scope",
      message,
      code: null,
      detailCode: null,
      missingScope: /\bmissing scope:\s*([a-z0-9._-]+)/i.exec(message)?.[1] ?? null,
    };
  }
  return {
    kind: "other",
    message,
    code: null,
    detailCode: null,
    missingScope: null,
  };
}

export function getOpenClawGatewayErrorMessage(error: unknown, fallback: string): string {
  return gatewayErrorMessage(error, fallback);
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
  const pendingRequests = new Map<GatewayRequestId, PendingRequest>();
  let challengeWaiter: ChallengeWaiter | null = null;
  let pendingChallengeNonce: string | null = null;
  let hello: OpenClawGatewayHelloOk | null = null;
  let terminalErrorBeforeHello: OpenClawGatewayError | null = null;

  const socket = new WebSocket(url);

  const rejectPendingRequests = (error: unknown) => {
    for (const [id, pending] of pendingRequests) {
      clearTimeout(pending.timeout);
      pending.reject(error);
      pendingRequests.delete(id);
    }
  };

  const rejectChallengeWaiter = (error: unknown) => {
    if (!challengeWaiter) {
      return;
    }
    clearTimeout(challengeWaiter.timeout);
    challengeWaiter.reject(error);
    challengeWaiter = null;
  };

  const sendRequest = <TResult>(method: string, params?: unknown): Promise<TResult> => {
    const id = randomUUID();
    const request: GatewayRequestFrame = {
      type: "req",
      id,
      method,
      ...(params !== undefined ? { params } : {}),
    };

    return new Promise<TResult>((resolve, reject) => {
      const timeout = setTimeout(() => {
        pendingRequests.delete(id);
        reject(
          new OpenClawGatewayError(`OpenClaw request ${method} timed out.`, {
            method,
            phase: "request",
          }),
        );
      }, requestTimeoutMs);

      pendingRequests.set(id, {
        method,
        resolve: (value) => resolve(value as TResult),
        reject,
        timeout,
      });

      try {
        socket.send(JSON.stringify(request));
      } catch (error) {
        clearTimeout(timeout);
        pendingRequests.delete(id);
        reject(
          new OpenClawGatewayError(
            `Failed to send OpenClaw request ${method}: ${gatewayErrorMessage(error, "send failed")}`,
            {
              detail: error,
              method,
              phase: "transport",
            },
          ),
        );
      }
    });
  };

  socket.addEventListener("message", (event) => {
    let parsed: unknown;
    try {
      parsed = typeof event.data === "string" ? JSON.parse(event.data) : undefined;
    } catch (error) {
      const gatewayError = new OpenClawGatewayError(
        "Received invalid JSON from OpenClaw gateway.",
        {
          detail: error,
          phase: "protocol",
        },
      );
      rejectChallengeWaiter(gatewayError);
      rejectPendingRequests(gatewayError);
      notifications.end();
      return;
    }

    if (isEventFrame(parsed)) {
      if (parsed.event === CONNECT_CHALLENGE_EVENT) {
        const nonce = trimOrNull(
          typeof parsed.payload === "object" && parsed.payload !== null
            ? ((parsed.payload as { nonce?: unknown }).nonce as string | undefined)
            : undefined,
        );
        if (!nonce) {
          rejectChallengeWaiter(
            new OpenClawGatewayError("OpenClaw gateway connect challenge missing nonce.", {
              detail: parsed,
              phase: "protocol",
            }),
          );
          return;
        }
        if (challengeWaiter) {
          clearTimeout(challengeWaiter.timeout);
          challengeWaiter.resolve(nonce);
          challengeWaiter = null;
        } else {
          pendingChallengeNonce = nonce;
        }
        return;
      }

      notifications.push({
        method: parsed.event,
        params: parsed.payload,
      });
      return;
    }

    if (isResponseFrame(parsed)) {
      const pending = pendingRequests.get(parsed.id);
      if (!pending) {
        return;
      }
      clearTimeout(pending.timeout);
      pendingRequests.delete(parsed.id);

      if (parsed.ok) {
        pending.resolve(parsed.payload);
        return;
      }

      pending.reject(toGatewayRequestError(pending.method, parsed.error));
    }
  });

  socket.addEventListener("close", (event) => {
    const closeError = new OpenClawGatewayError(
      `OpenClaw gateway connection closed (${event.code}${event.reason ? `: ${event.reason}` : ""}).`,
      {
        detail: event,
        phase: "transport",
      },
    );
    terminalErrorBeforeHello = closeError;
    notifications.end();
    rejectChallengeWaiter(closeError);
    rejectPendingRequests(closeError);
  });

  socket.addEventListener("error", (event) => {
    const transportError = new OpenClawGatewayError("OpenClaw gateway transport error.", {
      detail: event,
      phase: "transport",
    });
    terminalErrorBeforeHello = transportError;
    rejectChallengeWaiter(transportError);
    rejectPendingRequests(transportError);
  });

  await new Promise<void>((resolve, reject) => {
    const onOpen = () => {
      cleanup();
      resolve();
    };
    const onError = (event: Event) => {
      cleanup();
      reject(
        new OpenClawGatewayError(`Couldn't connect to OpenClaw gateway at ${url}.`, {
          detail: event,
          phase: "connection",
        }),
      );
    };
    const onClose = (event: CloseEvent) => {
      cleanup();
      reject(
        new OpenClawGatewayError(
          `OpenClaw gateway closed during connect (${event.code}${event.reason ? `: ${event.reason}` : ""}).`,
          {
            detail: event,
            phase: "connection",
          },
        ),
      );
    };
    const cleanup = () => {
      socket.removeEventListener("open", onOpen);
      socket.removeEventListener("error", onError);
      socket.removeEventListener("close", onClose);
    };
    socket.addEventListener("open", onOpen);
    socket.addEventListener("error", onError);
    socket.addEventListener("close", onClose);
  });

  try {
    await new Promise<string>((resolve, reject) => {
      if (pendingChallengeNonce) {
        const bufferedNonce = pendingChallengeNonce;
        pendingChallengeNonce = null;
        resolve(bufferedNonce);
        return;
      }
      if (terminalErrorBeforeHello) {
        reject(terminalErrorBeforeHello);
        return;
      }
      const timeout = setTimeout(() => {
        challengeWaiter = null;
        reject(
          new OpenClawGatewayError("OpenClaw gateway connect challenge timeout.", {
            phase: "connection",
          }),
        );
      }, requestTimeoutMs);
      challengeWaiter = { resolve, reject, timeout };
    });
  } catch (error) {
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      socket.close(1008, "connect_failed");
    }
    throw error;
  }

  try {
    hello = await sendRequest<OpenClawGatewayHelloOk>("connect", {
      minProtocol: OPENCLAW_GATEWAY_PROTOCOL_VERSION,
      maxProtocol: OPENCLAW_GATEWAY_PROTOCOL_VERSION,
      client: {
        id: "gateway-client",
        displayName: "T3 Code",
        version: packageJson.version,
        platform: process.platform,
        mode: "backend",
      },
      role: "operator",
      scopes: ["operator.admin"],
      auth: {
        ...(trimOrNull(options.token) ? { token: trimOrNull(options.token) } : {}),
        ...(trimOrNull(options.password) ? { password: trimOrNull(options.password) } : {}),
      },
      caps: [],
    });
  } catch (error) {
    await new Promise<void>((resolve) => {
      if (socket.readyState === WebSocket.CLOSING || socket.readyState === WebSocket.CLOSED) {
        resolve();
        return;
      }
      const onClose = () => {
        socket.removeEventListener("close", onClose);
        resolve();
      };
      socket.addEventListener("close", onClose);
      socket.close(1008, "connect_failed");
    }).catch(() => undefined);
    throw error;
  }

  const connection: OpenClawGatewayConnection = {
    url,
    hello,
    notifications,
    call<TResult>(method: string, params?: unknown): Promise<TResult> {
      return sendRequest<TResult>(method, params);
    },
    close(code?: number, reason?: string): Promise<void> {
      notifications.end();
      rejectPendingRequests(
        new OpenClawGatewayError("OpenClaw gateway connection closed.", {
          phase: "transport",
        }),
      );
      rejectChallengeWaiter(
        new OpenClawGatewayError("OpenClaw gateway connection closed.", {
          phase: "transport",
        }),
      );
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

  return connection;
}
