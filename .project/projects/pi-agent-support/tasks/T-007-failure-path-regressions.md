---
id: T-007
name: Harden Pi failure paths and regression tests
status: done
workstream: WS-D
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T15:19:24Z
linear_issue_id: 
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr: 
depends_on: [T-004, T-005, T-006, T-009]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-004
acceptance_criteria_ids: [AC-002, AC-004, AC-005, AC-006]
operating_mode: feature
---

# Task: Harden Pi failure paths and regression tests

## Description

Add targeted regression coverage and cleanup fixes for Pi startup, send, model-switch, discovery, browser-tool, spawn, and rendering failures.

## Acceptance Criteria

- [x] Startup failure cleans up session state.
- [x] Send failure resets active turn state.
- [x] Model-switch failure leaves the prior session consistent.
- [x] Spawn failure does not crash the server.
- [x] Discovery failure is scoped to Pi and does not warn Codex-only users.
- [x] Browser-tool failures such as no focused preview owner, disconnected automation host, timeout, and Pi MCP bridge configuration failure are recoverable and do not corrupt the provider session.
- [x] Pi browser integration does not silently use external browser automation when T3 preview MCP is unavailable.
- [x] Empty intermediate Pi events do not render as bogus assistant responses.
- [x] Tests fail before the relevant fixes and pass after implementation.

## Traceability

- Story: US-004
- Acceptance criteria: AC-002, AC-004, AC-005, AC-006

## Technical Notes

Use deterministic adapter tests and web rendering logic tests where possible. Prefer focused fixtures over broad snapshot tests. Browser automation failure-path tests should target the MCP broker/session boundary where possible rather than launching a full desktop preview.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Evidence recorded

## Evidence Log

- 2026-06-23T15:19:24Z: Failure regressions now cover startup cleanup, prompt failure active-turn reset, model-switch consistency, missing spawn/discovery failures scoped to Pi, token-safe Pi MCP adapter config injection, no external browser fallback, and empty intermediate message suppression.

- 2026-06-23: Blocked pending T-004/T-005/T-006.
