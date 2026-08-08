---
id: T-008
name: Cover transport cancellation and race behavior
status: done
workstream: WS-B
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T14:33:31Z
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

- [x] Focused tests cover every transport race listed in the task description.
- [x] Tests use controllable dispatchers, channels, receipts, or server drains rather than arbitrary sleeps.
- [x] Repeated subscribe-cancel cycles leave no additional active collectors or sockets.
- [x] Every reconnect attempt uses a fresh ticket and fresh subscription request IDs; a sent unary call and a one-shot stream are never replayed.
- [x] `Interrupt` is emitted only when the cancelled request still belongs to the current session, and an unsent bounded wait is the only source of `connectionUnavailable`.
- [x] HTTP gzip decode and the selected WebSocket compression proof pass with large 64-bit JSON values intact.
- [x] Failure output redacts credentials and remains actionable.

## Traceability

- Story: US-004
- Acceptance criteria: AC-004, AC-007

## Technical Notes

- Cross-check `apps/swift-ios/Tests/CoreTests/WebSocketRPCRaceTests.swift` and `TransportReliabilityTests.swift`, then express the cases with Kotlin's selected dispatcher/clock and the Android session ownership from T-021.
- Do not copy wall-clock constants into tests. Inject unsent wait, response timeout, keepalive, and retry timing.
- The message-first bootstrap no-fallback rule and side-effecting one-shot no-resubscribe policy should be represented as method metadata/fixtures even though full new-task and Git UI are follow-on scope.
- This task tests supervisor handoff but does not add reconnect below T-011.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T14:33:31Z: Final high-risk review hardening passed a forced 84-task Gradle run: request-ID exhaustion now fails closed, malformed remote diagnostics redact ticket and direct credentials, and contract/transport plus debug/release lint all pass in .agents/logs/tests/20260808T143159Z.log.

- 2026-08-08T14:19:23Z: foundationTransportIntegration, foundationContractConformance, and debug/release core-protocol lint pass; controlled tests cover all listed races, fresh remint/request IDs, no replay, current-generation Interrupt, cleanup/redaction, gzip, negotiated bidirectional permessage-deflate, and MAX_SAFE_INTEGER Long fidelity.

- 2026-08-08T14:18:57Z: Acceptance verified by `TicketedRpcSessionTest` and `TicketedRpcSessionRaceTest` with virtual clocks and serialized callback receipts: sent/unsent deadlines, current-generation cancellation, close-before-ready/late-open, expired-ticket replacement, malformed/fatal late frames, twelve concurrent subscribe-cancel cycles, one-shot termination, manual server-restart handoff, fresh ticket and request IDs, response-timeout/late-Exit ownership, closure-once, and exact socket release. No test uses an arbitrary sleep.

- 2026-08-08T14:18:57Z: Compression and integer evidence verified by `TransportCompressionIntegrationTest` on the real pinned OkHttp 5.4.0/MockWebServer stack: the client offers and observes `permessage-deflate`, a greater-than-64 KiB request is decoded by the server, a compressed greater-than-64 KiB response is decoded by the client, and sequence `9007199254740991` reaches Kotlin `Long` intact. Existing `EnvironmentHttpClientTest` proves transparent controlled gzip. Ticket-bearing fatal output and public endpoint diagnostics remain redacted.

- 2026-08-08T14:18:57Z: Definition of Done verified. Method metadata pins WebSocket-only bootstrap fallback and terminal one-shot policy; factory-scoped request allocation gives replacement subscriptions fresh IDs while generation ownership prevents stale Interrupt. Commands and ownership are documented in `README.md` and `core-protocol/CONTRACTS.md`. `./gradlew.bat foundationTransportIntegration foundationContractConformance :core-protocol:lintDebug :core-protocol:lintRelease --no-daemon` passed for debug/release and the 27-file provenance gate.

- 2026-08-08T14:09:12Z: Add deterministic lifecycle race coverage and a real OkHttp compressed WebSocket round trip.

- 2026-08-08T14:09:09Z: T-006 and T-021 are done; the complete one-attempt transport boundary is ready for deterministic race and compression proof.

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
