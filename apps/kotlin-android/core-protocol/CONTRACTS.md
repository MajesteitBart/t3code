# Native Android foundation contract inventory

`packages/contracts` is the only wire-contract authority. The Kotlin models in this module are a native representation checked against deterministic fixtures; they do not define a second protocol.

## Foundation HTTP subset

| Request                                    | Canonical contract                              | Foundation use                                              |
| ------------------------------------------ | ----------------------------------------------- | ----------------------------------------------------------- |
| `GET /.well-known/t3/environment`          | `ExecutionEnvironmentDescriptor`                | Public endpoint and capability discovery before pairing     |
| `POST /oauth/token`                        | `AuthAccessTokenResult`                         | RFC 8693 one-time bootstrap exchange                        |
| `GET /api/orchestration/shell`             | `OrchestrationShellSnapshot`                    | Initial and bounded passive shell refresh                   |
| `GET /api/orchestration/threads/:threadId` | `OrchestrationThreadDetailSnapshot`             | Fresh-state ambiguity resolution in T-013                   |
| `POST /api/orchestration/dispatch`         | `ClientOrchestrationCommand` / `DispatchResult` | HTTP fallback only when work is provably unsent             |
| `POST /api/auth/websocket-ticket`          | `AuthWebSocketTicketResult`                     | Fresh one-use ticket immediately before each socket attempt |

## Foundation Effect RPC subset

| Method                          | Canonical payload/result                                            | Lifecycle policy                                                                                 |
| ------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `orchestration.dispatchCommand` | `ClientOrchestrationCommand` / `DispatchResult`                     | Unary; a sent request is never replayed by transport                                             |
| `orchestration.subscribeShell`  | `OrchestrationSubscribeShellInput` / `OrchestrationShellStreamItem` | Long-lived intent; a supervisor may recreate it with a fresh request ID on a replacement session |

The wider RPC catalog is intentionally excluded until a follow-on project consumes it. One-shot/side-effecting streams are non-resubscribable even when later added.

`EffectRpcCodec` is deliberately transport-independent. It encodes `Request` with a positive monotonically allocated integer ID and exactly `headers: []`, and it covers `Ping`, `Pong`, `Ack`, `Interrupt`, `Chunk`, `Exit`, `Defect`, and `ClientProtocolError`. Known shell variants decode to typed union members; an unknown `kind` returns `RefreshRequired` so callers can replace state from a fresh snapshot. The codec does not open sockets, schedule timeouts, retry, reconnect, or resubscribe.

## One-attempt session ownership

`TicketedRpcSessionFactory.start` mints one fresh ticket immediately before constructing one OkHttp WebSocket. The ticket exists only in the request URL; public diagnostics expose `wsTicket=<redacted>`. The connector disables transport retries and redirects, and the session has no reconnect or backoff loop.

Request ownership is installed and marked sent before `WebSocket.send`. A disconnect therefore completes sent unary work with `RpcDisconnectedFailure(ambiguous=true)` and never replays it. Work still proven unsent expires with `RpcConnectionUnavailableFailure` after `connectionWait`, which is the only result an upper policy may consider for explicit HTTP fallback. Cancellation and response timeout send `Interrupt` only while that request ID still belongs to the open generation. Chunk `Ack` is sent after downstream consumption. Every stream terminates with its one-attempt session; `LONG_LIVED_INTENT` is metadata for WS-C to recreate on a new session, while `ONE_SHOT` is terminal.

`closure` completes once with the generation, whether the socket ever opened, and the terminal reason. `openInfo` exposes the negotiated WebSocket extension header for the controlled compression proof without exposing the ticket URL.

Dispatch metadata keeps policy above transport: a `thread.turn.start` command carrying `bootstrap` is WebSocket-only even while provably unsent, because the HTTP endpoint cannot expand it. Other supported commands may be considered for explicit HTTP fallback only after `RpcConnectionUnavailableFailure`. `ONE_SHOT.supervisorMayRecreate` is false.

## Provenance and drift

Fixtures live under `src/test/resources/contracts/foundation`. `manifest.json` records:

- the last Git revision touching the exact canonical TypeScript source set;
- a normalized SHA-256 content hash over those files;
- the installed Effect RPC version and `RpcMessage.ts` content hash;
- every fixture's expected accept, reject, or refresh-required behavior.

Regenerate intentionally from the repository root with:

```powershell
node scripts/export-native-android-contract-fixtures.ts --write
```

Verify TypeScript provenance and Kotlin decoding together from `apps/kotlin-android` with:

```powershell
./gradlew.bat foundationContractConformance
```

Run the transport ownership/race matrix and real bidirectional compressed WebSocket proof with:

```powershell
./gradlew.bat foundationTransportIntegration
```

Unknown JSON fields are tolerated. Unknown shell stream item kinds do not partially mutate state; the codec returns an explicit refresh-required result for T-022. Incompatible enums remain hard failures. JSON integers travel directly to Kotlin `Long` values and never pass through `Double`.
