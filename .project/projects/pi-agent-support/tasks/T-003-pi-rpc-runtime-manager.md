---
id: T-003
name: Build Pi RPC runtime manager
status: done
workstream: WS-B
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T12:59:18Z
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
operating_mode: feature
---

# Task: Build Pi RPC runtime manager

## Description

Implement the server-side Pi RPC process/session layer that starts Pi from configured binary path and environment, manages session scope, sends prompts, interrupts/stops sessions, and cleans up child processes.

## Acceptance Criteria

- [x] Pi RPC startup uses configured binary path and environment.
- [x] Startup failure returns a structured provider error and leaves no live session.
- [x] Stop/interrupt/finalizer paths release process and pending state.
- [x] Unit tests cover successful startup and child-process spawn failure.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-006

## Technical Notes

Pattern after existing scoped adapters and runtime helpers. Do not create global Pi state outside the provider instance scope.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated if operator setup changes

## Evidence Log

- 2026-06-23T12:59:18Z: Pi RPC runtime manager implemented in apps/server/src/provider/piRpcRuntime.ts with tests in piRpcRuntime.test.ts. Verified with apps/server explicit vp test run for piRpcRuntime.test.ts and .\\node_modules\\.bin\\vp.cmd run --filter t3 typecheck.

- 2026-06-23T12:48:08Z: T-001/T-002 completed runtime command and settings decisions

- 2026-06-23T12:48:08Z: Building Pi RPC runtime manager

- 2026-06-23: Blocked pending T-001/T-002.
