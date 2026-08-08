---
id: T-015
name: Build the adaptive live home shell
status: done
workstream: WS-D
created: 2026-08-07T13:17:00Z
updated: 2026-08-08T17:50:20Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-010, T-014, T-022]
conflicts_with: [Compose home navigation]
parallel: false
priority: high
estimate: XL
operating_mode: multi-stream
story_id: US-003
acceptance_criteria_ids: [AC-003, AC-006]
---

# Task: Build the adaptive live home shell

## Description

Render collision-safe, capability-aware environment/project/thread shell state with adaptive phone/tablet navigation and data-freshness-aware loading, empty, offline, reconnecting, revoked, and error presentation.

## Acceptance Criteria

- [x] Phone width uses a conventional single-pane navigation flow and expanded tablet width preserves list/detail context.
- [x] Selecting colliding rows routes to the correct environment and raw entity.
- [x] Loading, empty, offline, reconnecting, revoked, and error states are visible and have appropriate actions; fresh HTTP rows remain usable while live subscriptions reconnect.
- [x] A failed passive environment retains last-known rows with visible reachability, and one offline machine cannot empty the aggregate home shell.
- [x] Rows preserve canonical status/lifecycle/capability data and deterministic identity/color inputs while the foundation renders one flat list rather than follow-on shelf policy.
- [x] State collection is lifecycle-aware and stable keys prevent unnecessary row recreation.
- [x] No continuously repainting animation or unbounded UI cache is introduced.

## Traceability

- Story: US-003
- Acceptance criteria: AC-003, AC-006

## Technical Notes

- Consume T-022's separate data-freshness and live-source state. Do not infer that `reconnecting` means the displayed snapshot is stale.
- Use Material semantic color/typography roles, provider branding with deterministic fallback, and deterministic project badges. Do not transplant the review's pure-black, 44dp, or fixed 760dp iOS constants.
- Interactive Android targets follow the platform's 48dp minimum; content width and pane behavior follow the selected adaptive Material components/window classes.
- Preserve capability and lifecycle fields for later Pinned/Active/Snoozed/Settled work, but do not implement shelf ordering, auto-wake, or auto-settle policy here.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T17:55:00Z: After removing the unused app test dependency, the final debug/release app unit, warnings-as-errors lint, and assembly sweep passed in `.agents/logs/tests/20260808T175347Z.log`; no production or UI behavior changed after the 7/7 instrumentation run.

- 2026-08-08T17:50:20Z: Final app unit/lint/debug-release assembly passed (20260808T174711Z); 7 API-35 Compose tests passed (20260808T174835Z); forced contract/transport/connection/two-server integration passed (20260808T174333Z); 4 device persistence tests passed (20260808T174532Z); real phone, expanded tablet, and process-recreation smoke passed with isolated state and no credential disclosure.

- 2026-08-08T17:50:00Z: Final app debug/release unit tests, warnings-as-errors lint, and assemblies passed in `.agents/logs/tests/20260808T174711Z.log`; the final API-35 Compose suite passed 7/7 after the v2 test-rule repair in `.agents/logs/tests/20260808T174835Z.log`.
- 2026-08-08T17:50:00Z: Forced contract conformance, real compressed WebSocket transport, scoped connection-state/ambiguity, and disposable two-server integration all executed and passed in `.agents/logs/tests/20260808T174333Z.log`; device-backed Room/Keystore process-recreation coverage executed 4/4 and passed in `.agents/logs/tests/20260808T174532Z.log`.
- 2026-08-08T17:50:00Z: Real API-35 smoke paired to captured PID 153864 on isolated worktree-local state without emitting credentials, restored `Android Smoke` after app process recreation, and verified phone plus 2560x1600/240-dpi expanded layouts. Screenshots remain in the ignored `.t3/ws-d-smoke-20260808/` evidence directory.
- 2026-08-08T17:50:00Z: Review repaired a transient catalog/aggregate replacement crash, cancellation swallowing, incomplete capability projection, unstable action allocation, and a deprecated Compose test rule. Mapper/UI tests prove scoped collision routing, owner-unavailable routing, aggregate offline retention, fresh-reconnecting interaction, deterministic keys, and all canonical capability fields; composables contain no transport, persistence, supervision, or retry implementation.

- 2026-08-08T17:49:49Z: Implementation and review complete: adaptive phone/tablet shell, aggregate collision-safe routing, retained offline rows, fresh-reconnecting usability, complete capability preservation, lifecycle-aware collection, stable keys, and bounded rendering are verified by app unit/UI/runtime and focused foundation gates.

- 2026-08-08T17:29:01Z: Complete and verify the adaptive live environment/project/thread shell with correct scoped routing, reverse states, lifecycle-aware collection, stable keys, and bounded presentation.

- 2026-08-08T17:29:01Z: Readiness review passed: T-010, T-014, and T-022 are done; collision-safe state, reviewed onboarding, and serialized reconciliation are available; the app debug/release and API-35 baselines pass.

- 2026-08-07T13:17:00Z: Created from .project/templates/task.md by `delano task add`.
