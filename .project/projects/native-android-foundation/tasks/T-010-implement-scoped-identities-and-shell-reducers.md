---
id: T-010
name: Implement scoped identities and shell reducers
status: done
workstream: WS-C
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T15:05:58Z
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

- [x] All merged project and thread keys include the environment identity before entering UI state.
- [x] Two fixtures with identical raw IDs reduce to distinct routable rows.
- [x] Scoped IDs use the tested length-prefixed UTF-8 form, and raw IDs resolve only when exactly one saved environment owns the entity.
- [x] Every routed operation resolves an immutable route containing UI ID, wire ID, environment ID, and current owner/client handle; archived and provisional entities remain indexed.
- [x] Per-environment capability and canonical lifecycle/status fields survive reduction so later gating/shelves do not require a state rewrite, while foundation ordering remains flat and deterministic.
- [x] Reducers are deterministic and have tests for loading, live replacement, offline fallback, revocation, removal, and stale-response rejection.
- [x] Reducers have no Android UI, transport, database, or clock side effects.

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

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T15:05:58Z: Debug/release pure state tests and lint pass; scoped Unicode identities, collision-aware action-time routing, capability/lifecycle retention, reverse states, and owner/sequence stale rejection are documented and verified.

- 2026-08-08T15:05:24Z: Added strict UTF-8 byte-length-prefixed project/thread IDs, collision-aware raw lookup, immutable action-time routes carrying scoped/wire/environment/current owner-client-session identity, and aggregate indexes that retain archived/provisional rows until canonical removal.
- 2026-08-08T15:05:24Z: Added a pure shell reducer retaining raw canonical payloads, environment capabilities, lifecycle/status/interaction fields, reachability, source, and freshness. Exact owner-handle equality plus monotonic sequence checks reject superseded or stale results without relying on cancellation.
- 2026-08-08T15:05:24Z: `:core-data:testDebugUnitTest :core-data:testReleaseUnitTest :core-data:lintDebug :core-data:lintRelease --no-daemon` passed after the final source changes. Thirteen tests cover Unicode/delimiter parsing, colliding environments, unique/ambiguous/owner-unavailable routing, capability and archive/provisional preservation, loading/live/offline/revoked/removal transitions, and late-owner/old-sequence rejection.
- 2026-08-08T15:05:24Z: Self-review verified every acceptance and Definition of Done item, including that `ShellState.kt` imports no Android UI, persistence, HTTP/WebSocket, or clock API and introduces no shelf policy. `core-data/STATE.md` records the reusable identity/routing/reduction contract.

- 2026-08-08T15:01:05Z: Implement length-prefixed environment-scoped identities, immutable action-time routes, capability-preserving shell models, and deterministic side-effect-free reducers.

- 2026-08-08T15:01:05Z: T-005 and T-009 are done; canonical shell fixtures and protected persisted environment state are available; pure reducer ownership is isolated in core-data.

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
