---
id: T-015
name: Build the adaptive live home shell
status: planned
workstream: WS-D
created: 2026-08-07T13:17:00Z
updated: 2026-08-08T10:32:44Z
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

- [ ] Phone width uses a conventional single-pane navigation flow and expanded tablet width preserves list/detail context.
- [ ] Selecting colliding rows routes to the correct environment and raw entity.
- [ ] Loading, empty, offline, reconnecting, revoked, and error states are visible and have appropriate actions; fresh HTTP rows remain usable while live subscriptions reconnect.
- [ ] A failed passive environment retains last-known rows with visible reachability, and one offline machine cannot empty the aggregate home shell.
- [ ] Rows preserve canonical status/lifecycle/capability data and deterministic identity/color inputs while the foundation renders one flat list rather than follow-on shelf policy.
- [ ] State collection is lifecycle-aware and stable keys prevent unnecessary row recreation.
- [ ] No continuously repainting animation or unbounded UI cache is introduced.

## Traceability

- Story: US-003
- Acceptance criteria: AC-003, AC-006

## Technical Notes

- Consume T-022's separate data-freshness and live-source state. Do not infer that `reconnecting` means the displayed snapshot is stale.
- Use Material semantic color/typography roles, provider branding with deterministic fallback, and deterministic project badges. Do not transplant the review's pure-black, 44dp, or fixed 760dp iOS constants.
- Interactive Android targets follow the platform's 48dp minimum; content width and pane behavior follow the selected adaptive Material components/window classes.
- Preserve capability and lifecycle fields for later Pinned/Active/Snoozed/Settled work, but do not implement shelf ordering, auto-wake, or auto-settle policy here.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:00Z: Created from .project/templates/task.md by `delano task add`.
