---
id: T-007
name: Harden Pi failure paths and regression tests
status: blocked
workstream: WS-D
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:55:11Z
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
blocked_owner: Bart
blocked_check_back: after server, web, and browser MCP Pi paths exist
operating_mode: feature
---

# Task: Harden Pi failure paths and regression tests

## Description

Add targeted regression coverage and cleanup fixes for Pi startup, send, model-switch, discovery, browser-tool, spawn, and rendering failures.

## Acceptance Criteria

- [ ] Startup failure cleans up session state.
- [ ] Send failure resets active turn state.
- [ ] Model-switch failure leaves the prior session consistent.
- [ ] Spawn failure does not crash the server.
- [ ] Discovery failure is scoped to Pi and does not warn Codex-only users.
- [ ] Browser-tool failures such as no focused preview owner, disconnected automation host, timeout, and unsupported Pi MCP capability are recoverable and do not corrupt the provider session.
- [ ] Pi browser integration does not silently use external browser automation when T3 preview MCP is unavailable.
- [ ] Empty intermediate Pi events do not render as bogus assistant responses.
- [ ] Tests fail before the relevant fixes and pass after implementation.

## Traceability

- Story: US-004
- Acceptance criteria: AC-002, AC-004, AC-005, AC-006

## Technical Notes

Use deterministic adapter tests and web rendering logic tests where possible. Prefer focused fixtures over broad snapshot tests. Browser automation failure-path tests should target the MCP broker/session boundary where possible rather than launching a full desktop preview.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Evidence recorded

## Evidence Log

- 2026-06-23: Blocked pending T-004/T-005/T-006.
