---
id: T-014
name: Verify Pi composer affordance parity
status: done
workstream: WS-F
created: 2026-06-23T19:52:59Z
updated: 2026-06-23T21:11:20Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-011, T-012, T-013]
conflicts_with: []
parallel: true
priority: high
estimate: S
story_id: US-009
acceptance_criteria_ids: [AC-010, AC-011, AC-012, AC-013]
operating_mode: feature
---

# Task: Verify Pi composer affordance parity

## Description

Run focused tests and a real Pi-selected composer smoke after the slash, skills, and mention fixes are implemented.

## Acceptance Criteria

- [x] Browser-verified Pi composer smoke covers `/model`, `$skill`, and `@file`/document mention behavior or the deliberate unsupported state.
- [x] Focused web/provider tests for the new affordances pass.
- [x] `vp check`, `vp run typecheck`, and `delano validate` pass before closeout.
- [x] Evidence is recorded in project updates and the relevant task evidence logs.

## Traceability

- Story: US-009
- Acceptance criteria: AC-010, AC-011, AC-012, AC-013

## Technical Notes

This task is verification only and should remain blocked until `T-011`, `T-012`, and `T-013` are complete.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T21:11:20Z: Final parity verification passed: browser smoke selected Pi model GLM-5.2 and covered /model glm, Pi unsupported state, and @README prompt-text behavior; focused tests passed 14 files/152 tests; vp check, vp run typecheck, and delano validate passed.

- 2026-06-23T20:44:16Z: Begin final Pi composer affordance verification

- 2026-06-23T20:44:10Z: T-011, T-012, and T-013 are complete; final parity verification is unblocked

- 2026-06-23T19:52:59Z: Created from .project/templates/task.md by `delano task add`.
