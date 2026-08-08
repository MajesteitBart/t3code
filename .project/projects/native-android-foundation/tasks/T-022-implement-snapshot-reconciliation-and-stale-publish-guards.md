---
id: T-022
name: Implement snapshot reconciliation and stale-publish guards
status: planned
workstream: WS-C
created: 2026-08-08T10:32:32Z
updated: 2026-08-08T10:32:44Z
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

- [ ] Fresh HTTP snapshots remain visible with a reconnecting source state while the active socket is unavailable.
- [ ] A failed passive refresh changes reachability but retains that environment's last-known rows.
- [ ] A session, environment, or refresh that has been superseded cannot publish late state, regardless of whether cancellation was observed.
- [ ] Unknown stream items trigger one bounded canonical refresh instead of corrupting state or terminating unrelated environments.

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

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-08T10:32:32Z: Created from .project/templates/task.md by `delano task add`.
