---
id: T-004
name: Map Pi events into canonical provider runtime events
status: blocked
workstream: WS-B
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
linear_issue_id:
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr:
depends_on: [T-003]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-004
acceptance_criteria_ids: [AC-005, AC-006]
blocked_owner: Bart
blocked_check_back: after T-003 runtime manager exists
operating_mode: feature
---

# Task: Map Pi events into canonical provider runtime events

## Description

Implement the Pi provider adapter for start/send/interrupt/respond/stop/list/read/rollback methods and map Pi RPC events into canonical T3 provider runtime events.

## Acceptance Criteria

- [ ] Pi adapter satisfies `ProviderAdapterShape`.
- [ ] User-visible assistant text emits content events.
- [ ] Intermediate tool/planning events emit work/plan/tool lifecycle events, not empty assistant messages.
- [ ] Multi-stage Pi runs do not complete the turn before the final user-visible answer.
- [ ] Duplicate turn-start/turn-complete events are prevented.
- [ ] Tests cover empty intermediate events, multi-stage output, and duplicate lifecycle prevention.

## Traceability

- Story: US-004
- Acceptance criteria: AC-005, AC-006

## Technical Notes

Use existing `ProviderRuntimeEvent` types and timeline rendering expectations. Add new raw source literals only if Pi needs a source not already representable.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated if event semantics change

## Evidence Log

- 2026-06-23: Blocked pending T-003.
