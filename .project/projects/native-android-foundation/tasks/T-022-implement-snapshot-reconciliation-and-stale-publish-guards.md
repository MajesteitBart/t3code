---
id: T-022
name: Implement snapshot reconciliation and stale-publish guards
status: done
workstream: WS-C
created: 2026-08-08T10:32:32Z
updated: 2026-08-08T15:28:21Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-010, T-011]
conflicts_with: [core-data snapshot reconciliation]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-003
acceptance_criteria_ids: [AC-003, AC-004]
---

# Task: Implement snapshot reconciliation and stale-publish guards

## Description

Reconcile active subscriptions, active HTTP fallback snapshots, passive environment polls, and persisted last-known rows while preventing superseded asynchronous work from publishing.

## Acceptance Criteria

- [x] Fresh HTTP snapshots remain visible with a reconnecting source state while the active socket is unavailable.
- [x] A failed passive refresh changes reachability but retains that environment's last-known rows.
- [x] A session, environment, or refresh that has been superseded cannot publish late state, regardless of whether cancellation was observed.
- [x] Unknown stream items trigger one bounded canonical refresh instead of corrupting state or terminating unrelated environments.

## Traceability

- Story: US-003
- Acceptance criteria: AC-003, AC-004

## Technical Notes

- Cross-check `NativeFeatureClient.startPolling`, `startAggregateRefresh`, `reconcileEnvironmentLoads`, and its generation/refresh-ID guards. Transfer the invariants, not the Swift `@MainActor` mechanism.
- State carries data freshness separately from live-source connectivity. An HTTP snapshot obtained while the socket is down may advance rows while connection presentation remains `reconnecting`.
- A failed passive read updates only reachability/error metadata. It must not replace that environment's cached shell with an empty snapshot.
- Kotlin may use session generations, refresh epochs, serialized reducer ownership, or an equivalent tested mechanism. The acceptance invariant is that superseded async work cannot publish, not a literal generation check after every suspension.
- Unknown stream items request at most one coalesced canonical refresh per reconciliation window; they do not terminate other environment owners.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T15:28:21Z: foundationConnectionState plus core-data debug/release lint passed; tests cover reconnecting active snapshots, retained passive rows, authority/session/environment/refresh fencing, ignored cancellation, one bounded coalesced canonical refresh, and malformed-snapshot isolation.

- 2026-08-08T15:27:49Z: `./gradlew.bat foundationConnectionState :core-data:lintDebug :core-data:lintRelease --no-daemon` passed debug/release reconciliation, supervisor, reducer, persistence-compensation, and WS-B one-attempt protocol tests plus warnings-as-errors lint.

- 2026-08-08T15:27:49Z: Deterministic tests prove active fallback rows remain `RECONNECTING`, passive failures retain last-known rows, superseded session/environment/refresh publications are rejected, cancellation-resistant canonical work cannot land late, unknown items coalesce to one six-second-bounded refresh, and malformed snapshots do not partially replace or stop unrelated state.

- 2026-08-08T15:18:41Z: Implement canonical snapshot reconciliation, stale permit rejection, retained passive rows, and coalesced unknown-item refresh.

- 2026-08-08T15:18:41Z: T-010 and T-011 are done; scoped reducers and publication permits are verified, so serialized reconciliation is dependency-safe.

- 2026-08-08T10:32:32Z: Created from .project/templates/task.md by `delano task add`.
