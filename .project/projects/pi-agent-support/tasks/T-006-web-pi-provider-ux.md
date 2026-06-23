---
id: T-006
name: Add Pi to web provider settings and picker flows
status: blocked
workstream: WS-C
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
linear_issue_id:
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr:
depends_on: [T-002, T-005]
conflicts_with: []
parallel: true
priority: medium
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-007]
blocked_owner: Bart
blocked_check_back: after T-002/T-005 expose Pi metadata and snapshots
operating_mode: feature
---

# Task: Add Pi to web provider settings and picker flows

## Description

Wire Pi into browser-safe provider metadata, provider settings, provider picker, model picker, and provider-native option controls using existing generic flows.

## Acceptance Criteria

- [ ] Pi appears in settings with the correct icon/label and fields.
- [ ] Provider picker offers Pi only when the Pi provider instance is usable.
- [ ] Model picker shows dynamic Pi models and clear empty/error states.
- [ ] Thinking controls match provider-native Pi options.
- [ ] No Pi-only composer command or frontend path is added.
- [ ] Component or logic tests cover provider selection and empty model states.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-007

## Technical Notes

Use existing generic provider metadata and model option descriptor patterns. Browser verification is required if UI rendering changes.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Browser verification complete
- [ ] Docs updated if user setup changes

## Evidence Log

- 2026-06-23: Blocked pending T-002/T-005.
