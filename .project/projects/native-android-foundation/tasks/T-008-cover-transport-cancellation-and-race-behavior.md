---
id: T-008
name: Cover transport cancellation and race behavior
status: planned
workstream: WS-B
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-006, T-021]
conflicts_with: [core-protocol test harness]
parallel: true
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-004
acceptance_criteria_ids: [AC-004, AC-007]
---

# Task: Cover transport cancellation and race behavior

## Description

Add deterministic tests for sent/unsent request races, session-owned cancellation, close-before-ready, ticket expiry/remint, malformed frames, concurrent/one-shot subscriptions, server restart handoff, compression, and resource cleanup.

## Acceptance Criteria

- [ ] Focused tests cover every transport race listed in the task description.
- [ ] Tests use controllable dispatchers, channels, receipts, or server drains rather than arbitrary sleeps.
- [ ] Repeated subscribe-cancel cycles leave no additional active collectors or sockets.
- [ ] Every reconnect attempt uses a fresh ticket and fresh subscription request IDs; a sent unary call and a one-shot stream are never replayed.
- [ ] `Interrupt` is emitted only when the cancelled request still belongs to the current session, and an unsent bounded wait is the only source of `connectionUnavailable`.
- [ ] HTTP gzip decode and the selected WebSocket compression proof pass with large 64-bit JSON values intact.
- [ ] Failure output redacts credentials and remains actionable.

## Traceability

- Story: US-004
- Acceptance criteria: AC-004, AC-007

## Technical Notes

- Cross-check `apps/swift-ios/Tests/CoreTests/WebSocketRPCRaceTests.swift` and `TransportReliabilityTests.swift`, then express the cases with Kotlin's selected dispatcher/clock and the Android session ownership from T-021.
- Do not copy wall-clock constants into tests. Inject unsent wait, response timeout, keepalive, and retry timing.
- The message-first bootstrap no-fallback rule and side-effecting one-shot no-resubscribe policy should be represented as method metadata/fixtures even though full new-task and Git UI are follow-on scope.
- This task tests supervisor handoff but does not add reconnect below T-011.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
