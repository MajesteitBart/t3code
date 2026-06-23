---
id: T-005
name: Wire Pi health, registration, and model discovery
status: blocked
workstream: WS-B
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
linear_issue_id:
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr:
depends_on: [T-002, T-003]
conflicts_with: []
parallel: true
priority: high
estimate: L
story_id: US-002
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004]
blocked_owner: Bart
blocked_check_back: after T-002 and T-003 define settings and runtime
operating_mode: feature
---

# Task: Wire Pi health, registration, and model discovery

## Description

Add `PiDriver`, register it as a built-in provider, implement Pi provider snapshot health checks, and discover Pi models dynamically.

## Acceptance Criteria

- [ ] `PiDriver` is registered in `BUILT_IN_DRIVERS`.
- [ ] Pi unavailable/missing binary produces an unavailable or error snapshot without affecting other providers.
- [ ] Pi disabled state is represented clearly.
- [ ] Pi model discovery is dynamic and scoped to Pi.
- [ ] Discovery failure does not show fake fallback models.
- [ ] Custom Pi models are validated before use or persistence.
- [ ] Tests cover missing binary, disabled provider, discovery success, and discovery failure.

## Traceability

- Story: US-002
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004

## Technical Notes

Follow managed snapshot patterns from current provider implementations, but do not copy static fallback model behavior that conflicts with issue #402.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated if setup behavior changes

## Evidence Log

- 2026-06-23: Blocked pending T-002/T-003.
