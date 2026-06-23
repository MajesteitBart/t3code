---
id: T-004
name: Map Pi events into canonical provider runtime events
status: done
workstream: WS-B
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T14:02:08Z
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
operating_mode: feature
---

# Task: Map Pi events into canonical provider runtime events

## Description

Implement the Pi provider adapter for start/send/interrupt/respond/stop/list/read/rollback methods and map Pi RPC events into canonical T3 provider runtime events.

## Acceptance Criteria

- [x] Pi adapter satisfies `ProviderAdapterShape`.
- [x] User-visible assistant text emits content events.
- [x] Intermediate tool/planning events emit work/plan/tool lifecycle events, not empty assistant messages.
- [x] Multi-stage Pi runs do not complete the turn before the final user-visible answer.
- [x] Duplicate turn-start/turn-complete events are prevented.
- [x] Tests cover empty intermediate events, multi-stage output, and duplicate lifecycle prevention.

## Traceability

- Story: US-004
- Acceptance criteria: AC-005, AC-006

## Technical Notes

Use existing `ProviderRuntimeEvent` types and timeline rendering expectations. Add new raw source literals only if Pi needs a source not already representable.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated if event semantics change

## Evidence Log

- 2026-06-23T14:02:08Z: Pi adapter implemented with scoped RPC sessions, canonical runtime event mapping, agent_end completion, duplicate lifecycle guards, empty message suppression, failure cleanup, and tests in PiAdapter.test.ts.

- 2026-06-23T12:59:27Z: Implementing Pi provider adapter and runtime event mapping

- 2026-06-23T12:59:26Z: T-003 runtime manager completed; adapter event mapping can begin

- 2026-06-23: Blocked pending T-003.
