---
id: T-003
name: Build Pi RPC runtime manager
status: blocked
workstream: WS-B
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
linear_issue_id:
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr:
depends_on: [T-001, T-002]
conflicts_with: []
parallel: true
priority: high
estimate: L
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-006]
blocked_owner: Bart
blocked_check_back: after T-001 and T-002 define runtime command and settings
operating_mode: feature
---

# Task: Build Pi RPC runtime manager

## Description

Implement the server-side Pi RPC process/session layer that starts Pi from configured binary path and environment, manages session scope, sends prompts, interrupts/stops sessions, and cleans up child processes.

## Acceptance Criteria

- [ ] Pi RPC startup uses configured binary path and environment.
- [ ] Startup failure returns a structured provider error and leaves no live session.
- [ ] Stop/interrupt/finalizer paths release process and pending state.
- [ ] Unit tests cover successful startup and child-process spawn failure.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-006

## Technical Notes

Pattern after existing scoped adapters and runtime helpers. Do not create global Pi state outside the provider instance scope.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated if operator setup changes

## Evidence Log

- 2026-06-23: Blocked pending T-001/T-002.
