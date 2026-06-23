---
id: T-006
name: Add Pi to web provider settings and picker flows
status: done
workstream: WS-C
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T14:02:21Z
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
operating_mode: feature
---

# Task: Add Pi to web provider settings and picker flows

## Description

Wire Pi into browser-safe provider metadata, provider settings, provider picker, model picker, and provider-native option controls using existing generic flows.

## Acceptance Criteria

- [x] Pi appears in settings with the correct icon/label and fields.
- [x] Provider picker offers Pi only when the Pi provider instance is usable.
- [x] Model picker shows dynamic Pi models and clear empty/error states.
- [x] Thinking controls match provider-native Pi options.
- [x] No Pi-only composer command or frontend path is added.
- [x] Component or logic tests cover provider selection and empty model states.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-007

## Technical Notes

Use existing generic provider metadata and model option descriptor patterns. Browser verification is required if UI rendering changes.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Browser verification complete
- [x] Docs updated if user setup changes

## Evidence Log

- 2026-06-23T14:02:21Z: Pi appears in provider settings with icon/badge, provider/model selection avoids fake defaults, send is blocked when Pi has no selected model, model/composer tests pass, and browser smoke verified Settings > Providers shows Pi Early Access disabled with clear message.

- 2026-06-23: Blocked pending T-002/T-005.
