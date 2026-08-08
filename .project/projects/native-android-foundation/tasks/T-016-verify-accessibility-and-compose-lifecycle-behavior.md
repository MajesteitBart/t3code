---
id: T-016
name: Verify accessibility and Compose lifecycle behavior
status: planned
workstream: WS-D
created: 2026-08-07T13:17:03Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-015, T-018]
conflicts_with: [Compose instrumentation tests]
parallel: true
priority: medium
estimate: L
operating_mode: multi-stream
story_id: US-003
acceptance_criteria_ids: [AC-006, AC-008]
---

# Task: Verify accessibility and Compose lifecycle behavior

## Description

Add instrumentation for predictive back, rotation, process recreation, TalkBack semantics, font scaling, state restoration, and bounded recomposition on the foundation shell.

## Acceptance Criteria

- [ ] Predictive back follows the documented phone and tablet navigation hierarchy.
- [ ] Rotation and process recreation preserve selection or restore a documented safe destination.
- [ ] TalkBack labels, traversal order, touch targets, and large font scaling pass the focused accessibility checklist.
- [ ] Representative state updates stay within the recorded recomposition and frame-time baseline.
- [ ] Instrumentation evidence identifies device/emulator and API level.

## Traceability

- Story: US-003
- Acceptance criteria: AC-006, AC-008

## Technical Notes

- Android Compose guidance sets a 48dp minimum interactive target; the deep review's 44pt/dp iOS value is not the Android acceptance bar.
- T-018 records device/build/fixture budgets first. This task consumes those recorded thresholds, which removes the previous circular baseline dependency.
- Exercise a reconnecting-with-fresh-data state and retained passive rows so semantics/actions remain accurate when connectivity and data freshness differ.
- Browser/computer-use/emulator interaction still requires the explicit approval described by repository instructions; keep pure/instrumentation setup runnable independently where possible.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:03Z: Created from .project/templates/task.md by `delano task add`.
