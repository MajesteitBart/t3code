---
id: T-011
name: Implement active/passive environment supervision
status: planned
workstream: WS-C
created: 2026-08-07T13:17:00Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-009, T-010, T-021]
conflicts_with: [core-data connection lifecycle]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-003
acceptance_criteria_ids: [AC-004]
---

# Task: Implement active/passive environment supervision

## Description

Create lifecycle-aware active/passive supervision that owns reachability, bounded jittered backoff, active session replacement/resubscription, passive polling, and deterministic cancellation. Snapshot reconciliation is T-022.

## Acceptance Criteria

- [ ] Exactly one active environment owns WebSocket subscriptions and at most one reconnect attempt; passive environments own no live socket and use bounded, low-frequency HTTP refresh.
- [ ] Socket closure triggers bounded jittered backoff, a fresh ticket/session, and one long-lived resubscription set with fresh request IDs.
- [ ] Deactivation, environment removal, and app lifecycle release cancel transports and collectors deterministically.
- [ ] Replacing an endpoint/session publishes the replacement owner before releasing the stale session, and a passive one-shot probe cannot close or mutate a shared active session.
- [ ] Every emitted result carries enough environment/session/refresh ownership for T-022 to reject superseded publication.
- [ ] Focused tests prove no retry logic exists below the supervisor.

## Traceability

- Story: US-003
- Acceptance criteria: AC-004

## Technical Notes

- Cross-check `NativeFeatureClient.startPolling/startAggregateRefresh` and `EnvironmentRuntime.client/ephemeralClient`; keep the Android ownership boundary from D-005 rather than copying the Swift actor.
- Suggested reference defaults are a 20-second passive cadence, 6-second passive read timeout, and bounded exponential backoff with jitter. Final values are configurable, lifecycle-aware, and tested with injected time/randomness.
- Only long-lived subscription intents resubscribe. Sent unary calls and one-shot side-effecting streams are never placed in the reconnect set.
- A fresh active HTTP read may continue while the socket is reconnecting. T-022 owns how that data and source state reconcile.
- Direct-bearer 401 from HTTP or ticket mint transitions only the affected owner to revoked/re-pair-required and stops auth retry. Do not import managed DPoP refresh into foundation.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:00Z: Created from .project/templates/task.md by `delano task add`.
