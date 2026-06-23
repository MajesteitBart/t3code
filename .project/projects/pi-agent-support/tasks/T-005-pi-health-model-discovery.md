---
id: T-005
name: Wire Pi health, registration, and model discovery
status: done
workstream: WS-B
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T14:02:12Z
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
operating_mode: feature
---

# Task: Wire Pi health, registration, and model discovery

## Description

Add `PiDriver`, register it as a built-in provider, implement Pi provider snapshot health checks, and discover Pi models dynamically.

## Acceptance Criteria

- [x] `PiDriver` is registered in `BUILT_IN_DRIVERS`.
- [x] Pi unavailable/missing binary produces an unavailable or error snapshot without affecting other providers.
- [x] Pi disabled state is represented clearly.
- [x] Pi model discovery is dynamic and scoped to Pi.
- [x] Discovery failure does not show fake fallback models.
- [x] Custom Pi models are validated before use or persistence.
- [x] Tests cover missing binary, disabled provider, discovery success, and discovery failure.

## Traceability

- Story: US-002
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004

## Technical Notes

Follow managed snapshot patterns from current provider implementations, but do not copy static fallback model behavior that conflicts with issue #402.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated if setup behavior changes

## Evidence Log

- 2026-06-23T14:02:12Z: PiDriver registered, disabled/missing/discovery-failure snapshots implemented, dynamic get_available_models discovery added without fake fallback models, custom model validation covered, and PiProvider tests pass.

- 2026-06-23T12:59:27Z: Implementing Pi driver, provider snapshot, and dynamic model discovery

- 2026-06-23T12:59:27Z: T-002/T-003 completed settings and runtime; provider registration and discovery can begin

- 2026-06-23: Blocked pending T-002/T-003.
