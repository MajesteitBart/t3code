# Native Android shell-state contract

`ShellState.kt` is a pure Kotlin boundary. It imports no Android UI, Room, HTTP, WebSocket, or clock API.

## Scoped identity

Project and thread UI keys use the canonical length-prefixed form:

```text
<kind>:<UTF-8 byte count of environment ID>:<environment ID><wire ID>
```

The byte count makes Unicode and delimiter-rich environment IDs unambiguous. Every project and thread is scoped before entering aggregate state. A raw wire ID is accepted only when exactly one saved environment owns it; a collision is an explicit `Ambiguous` result.

## Action-time routing

Rows retain both their scoped UI ID and raw wire ID. `ShellAggregateState` resolves an operation immediately before dispatch into an immutable route containing:

- scoped UI ID;
- raw wire ID;
- environment ID;
- current owner epoch, client epoch, and optional one-attempt session generation.

If the current environment has no owner, routing returns `OwnerUnavailable`. Archived and provisional rows remain indexed and routable until a canonical removal event deletes them. Callers must resolve again for a later action rather than cache an old route or transport.

## Pure reduction

The reducer preserves the canonical raw project/thread JSON plus the lifecycle, status, archive, provisional, interaction-mode, and environment-capability fields needed by later product policy. It keeps data freshness separate from reachability/source state and covers restored, loading, passive, live, reconnecting, offline, revoked, and removed transitions.

Every asynchronous mutation carries its owner handle. A result from a superseded owner is ignored even if cancellation was not observed, and snapshots/deltas older than the accepted canonical sequence are rejected.

## Active/passive supervision

`EnvironmentConnectionSupervisor` is the only retry owner above WS-B's policy-free attempts:

- exactly one saved environment is active and may construct one `TicketedRpcSession`; all others use independent, bounded HTTP snapshot attempts;
- each session construction mints a fresh ticket through WS-B, and only the long-lived shell subscription intent is recreated on a replacement session;
- sent unary calls and one-shot streams never enter the supervisor's recreation set;
- configurable passive cadence/timeouts and capped exponential jitter use injected time/randomness in tests;
- an active socket failure closes that exact session before a bounded HTTP fallback and backoff; a direct-bearer authorization rejection revokes only that environment and stops retry;
- configuration/lifecycle changes publish replacement authority before cancelling stale work, but wait for the exact old job/session to release before starting a new socket;
- every authority transition has a monotonic epoch, and every snapshot, stream item, and reachability result carries environment, owner/client/session, and refresh epochs for publication fencing.

## Snapshot reconciliation

`ShellStateReconciler` is the serialized publication boundary between restored state and the supervisor:

- authority epochs fence reordered install and release events, while exact owner handles reject results from replaced clients or sessions;
- refresh epochs reject late HTTP, reachability, and stream publications even when their work ignored cancellation;
- an active HTTP fallback replaces canonical rows and freshness but deliberately keeps the source `RECONNECTING` until the socket stream synchronizes;
- a failed passive read updates only reachability and its safe error, retaining all last-known rows;
- revocation clears the routable owner for only that environment and keeps a monotonic authority floor so old work cannot resurrect it;
- an unknown future stream union member never mutates partial state. It starts one timeout-bounded canonical refresh for the exact authority window, coalesces later unknown members, and checks the original authority/owner/refresh guard again before applying the snapshot;
- malformed canonical snapshots fail without replacing the previous aggregate, and removal leaves a tombstone floor that rejects late authority or result publication if the same environment ID is later restored.

Callers register each catalog environment and optional persisted snapshot before collecting supervisor publications. They must call `removeEnvironment` when catalog removal commits and `release` with the owning lifecycle.

## Process-death and multi-environment recovery

The real recovery fixture starts two isolated T3 servers whose project and thread wire IDs deliberately collide. It verifies that process recreation restores both protected credentials, the active selection, and each environment's last-known scoped rows before networking begins. The supervisor then exercises independent passive failure, server restart on the same home and port, active ownership transfer, reconnecting HTTP fallback, direct-session revocation, and scoped removal.

WS-B transport failures remain typed even when their hierarchy includes `CancellationException`. The supervisor treats those failures as retryable while its own coroutine is active; actual lifecycle cancellation is rethrown. Tests observe exact session and collector counts, and reconciliation authority guards reject delayed publications from superseded owners.

## Ambiguous turn recovery

`PreparedStableTurn` freezes the logical turn's `commandId`, `messageId`, `createdAt`, immutable JSON object, and canonical encoded payload before its first send. `StableTurnRecovery` calls a one-attempt transport exactly once. If the response is lost, it loads a fresh thread snapshot and treats the turn as committed only when that snapshot contains the stable `messageId`; an absent or unavailable answer remains explicitly ambiguous with the original turn intact.

An explicit retry always performs that fresh verification again. It sends only after a confirmed absence and reuses the original payload verbatim. It never introduces transport-level replay. The disposable server fixture proves the lost-after-acceptance path and also sends the identical command as a server-dedupe backstop: the accepted receipt sequence is reused and only one matching user-message and turn-start domain effect exists.

Run the disposable two-server recovery evidence from `apps/kotlin-android`:

```powershell
./gradlew.bat foundationIntegration
```

Run the pure state/supervision gate from `apps/kotlin-android`:

```powershell
./gradlew.bat foundationConnectionState
```
