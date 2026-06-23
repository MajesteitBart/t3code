---
id: T-008
name: Validate Pi end to end and document setup
status: blocked
workstream: WS-D
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:55:11Z
linear_issue_id:
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr:
depends_on: [T-007]
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008, AC-009]
blocked_owner: Bart
blocked_check_back: after T-007 failure-path coverage is complete
operating_mode: feature
---

# Task: Validate Pi end to end and document setup

## Description

Run final gates, perform a live Pi provider smoke when Pi is available, verify in-app browser behavior or its explicit unsupported state, and document user setup/known limitations.

## Acceptance Criteria

- [ ] `vp check` passes.
- [ ] `vp run typecheck` passes.
- [ ] Targeted Pi tests pass.
- [ ] Browser verification covers provider picker, model picker, and a Pi turn or unavailable-Pi fallback.
- [ ] In-app browser verification covers Pi access to T3 `preview_*` MCP tools when supported, or records an explicit unsupported-Pi-MCP limitation.
- [ ] Provider documentation explains Pi setup, binary path, model discovery, thinking options, T3 in-app browser support/limitations, and v1 limitations.
- [ ] Project updates record final evidence and any remaining blockers.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007

## Technical Notes

If Pi is unavailable locally, record that as a blocker and still verify unavailable-provider behavior. If Pi is available but cannot attach T3's MCP preview server, record that as a browser-capability limitation rather than substituting Pi `agent-browser` or standalone browser automation.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Browser verification complete
- [ ] Docs updated

## Evidence Log

- 2026-06-23: Blocked pending T-007.
