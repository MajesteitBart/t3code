---
id: T-010
name: Implement scoped identities and shell reducers
status: planned
workstream: WS-C
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-005, T-009]
conflicts_with: [core-data state models]
parallel: true
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-003
acceptance_criteria_ids: [AC-003]
---

# Task: Implement scoped identities and shell reducers

## Description

Model collision-safe environment-scoped routes and pure, capability-aware reducers for saved, last-known, loading, reachable, offline, reconnecting, revoked, and error shell state.

## Acceptance Criteria

- [ ] All merged project and thread keys include the environment identity before entering UI state.
- [ ] Two fixtures with identical raw IDs reduce to distinct routable rows.
- [ ] Scoped IDs use the tested length-prefixed UTF-8 form, and raw IDs resolve only when exactly one saved environment owns the entity.
- [ ] Every routed operation resolves an immutable route containing UI ID, wire ID, environment ID, and current owner/client handle; archived and provisional entities remain indexed.
- [ ] Per-environment capability and canonical lifecycle/status fields survive reduction so later gating/shelves do not require a state rewrite, while foundation ordering remains flat and deterministic.
- [ ] Reducers are deterministic and have tests for loading, live replacement, offline fallback, revocation, removal, and stale-response rejection.
- [ ] Reducers have no Android UI, transport, database, or clock side effects.

## Traceability

- Story: US-003
- Acceptance criteria: AC-003

## Technical Notes

- Reference encoding: `"<kind>:<utf8-byte-count(environmentID)>:<environmentID><wireID>"` for project/thread (and approval/input only when later scope consumes them). The byte count, not Kotlin character count, prevents delimiter and Unicode ambiguity.
- Native snapshots always expose scoped IDs. Raw-ID compatibility is a lookup convenience only when the ownership candidate set has exactly one member; collisions fail explicitly.
- Keep `uiID -> environmentID` and `uiID -> wireID` indexes for materialized, archived, and provisional foundation rows. A route must resolve its current session/owner at action time instead of retaining a stale transport indefinitely.
- Preserve known optional capability fields leniently and retain a forward-compatible unknown-capability posture. Wire interaction mode is `default|plan`; do not emit the Swift UI label `standard`.
- Carry canonical row lifecycle/status inputs and reachability, but do not implement Pinned/Active/Snoozed/Settled shelf policy in this project.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
