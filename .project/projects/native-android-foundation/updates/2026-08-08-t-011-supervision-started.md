---
timestamp: 2026-08-08T15:06:20Z
status: in-progress
task: T-011
stream: WS-C
---

# Progress Update

## Completed

- T-009, T-010, and T-021 were closed with protected state, scoped reducers, and the verified one-attempt transport boundary available.
- Reviewed D-005 and the WS-B evidence before assigning retry ownership to core-data.

## In Progress

- Implement exactly one active WebSocket owner, passive HTTP refresh, bounded jittered reconnect, revocation, and lifecycle cancellation.

## Blockers

- None.

## Next Actions

- Prove fresh-session/long-lived-only replacement, no duplicate collectors, passive isolation, stale-publication fencing, and deterministic release in debug/release tests.
