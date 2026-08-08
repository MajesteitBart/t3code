---
timestamp: 2026-08-08T15:18:07Z
status: in-progress
task: T-011
stream: WS-C
---

# Progress Update

## Completed

- Added a single lifecycle-aware supervisor that gives only the active environment a live session while passive environments perform isolated 20-second HTTP refreshes with six-second request bounds.
- Added capped, injected exponential jitter; fresh ticket/session and request ownership; long-lived-only subscription recreation; environment-scoped revocation; and exact worker/session cancellation.
- Added environment, owner, client, session, and refresh permits to every asynchronous publication, with replacement authority installed before stale work is cancelled.
- Passed `foundationConnectionState` and debug/release core-data lint; the gate also runs WS-B protocol tests that prove HTTP, unary RPC, and one-shot streams remain one-attempt-only.

## In Progress

- None.

## Blockers

- None.

## Next Actions

- Open T-022 and consume these ownership permits in serialized reconciliation and stale-publication guards.
