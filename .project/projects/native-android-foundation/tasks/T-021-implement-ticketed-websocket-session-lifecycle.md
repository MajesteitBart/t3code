---
id: T-021
name: Implement ticketed WebSocket session lifecycle
status: done
workstream: WS-B
created: 2026-08-08T10:32:25Z
updated: 2026-08-08T14:08:26Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-006, T-007]
conflicts_with: [core-protocol WebSocket session lifecycle]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-004
acceptance_criteria_ids: [AC-004]
---

# Task: Implement ticketed WebSocket session lifecycle

## Description

Build the one-attempt ticketed WebSocket session lifecycle around the Effect RPC codecs, including per-attempt ticket minting, cancellation ownership, typed sent-versus-unsent failure, and deterministic resource release without internal reconnect.

## Acceptance Criteria

- [x] Each connection attempt mints a fresh one-use WebSocket ticket and redacts it from URLs in logs and evidence.
- [x] Sent unary calls fail as ambiguous on disconnect and are never replayed; provably unsent calls may fail as connectionUnavailable after a bounded configurable wait.
- [x] Cancellation sends Interrupt only while the request still belongs to the current session, and one-shot streams fail rather than resubscribe.
- [x] The session exposes closure once, releases resources deterministically, and performs no internal reconnect or backoff.

## Traceability

- Story: US-004
- Acceptance criteria: AC-004

## Technical Notes

- Endpoint is the derived WebSocket base plus `/ws?wsTicket=<one-use-ticket>`. Mint via `POST /api/auth/websocket-ticket` immediately before every connection attempt; redact the query in all diagnostics.
- Install request/session ownership before any suspending send. Mark unary work sent before crossing the send suspension so a concurrent close cannot misclassify it as replayable.
- Allocate monotonically increasing IDs within a session. A supervisor-created replacement session resubscribes long-lived intents with fresh IDs; this one-attempt object never reconnects itself.
- A sent request that loses its session returns `disconnected`/ambiguous and is never automatically sent again. Only a request proven unsent after the configurable connection wait returns `connectionUnavailable`, which an upper dispatch policy may explicitly HTTP-fallback.
- `Interrupt {requestId}` is valid only for the same session generation that owns the request ID. `Ack` follows consumed chunks. One-shot/side-effecting streams are marked non-resubscribable.
- Support Ping/Pong liveness and the compression proof selected by T-001. Swift's 5-second ping, 4-second unsent wait, and 30-second response timeout are reference defaults, not frozen Android values.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T14:08:26Z: TicketedRpcSessionTest passes for fresh ticket/redaction, bounded unsent wait, sent ambiguity/no replay, current-generation Interrupt, terminal one-shot stream, Ack-after-consumption, Ping/Pong, closure-once, and one connector attempt; debug/release tests and lint pass.

- 2026-08-08T14:08:00Z: Acceptance verified by `TicketedRpcSessionTest`: two factory starts mint distinct tickets while public endpoint/toString values remain redacted; an unsent request waits exactly the configured virtual deadline; sent work disconnects as ambiguous with one Request and one connector invocation; cancellation sends one generation-owned Interrupt; consumed chunks send Ack; a one-shot stream fails terminally; application Ping/Pong stays on the same attempt; repeated close completes one closure and calls resource close once.

- 2026-08-08T14:08:00Z: Definition of Done verified. The serialized callback queue and request maps install ownership before sends, invalidate all ownership on terminal closure, and contain no retry, reconnect, resubscribe, or backoff path. The default OkHttp connector enforces disabled retry and redirects; handshake failure causes discard ticket-bearing details. Boundaries are documented in `core-protocol/CONTRACTS.md`. `./gradlew.bat :core-protocol:testDebugUnitTest :core-protocol:testReleaseUnitTest :core-protocol:lintDebug :core-protocol:lintRelease --no-daemon` passed.

- 2026-08-08T13:58:10Z: Implement one-attempt ticketed WebSocket lifecycle with generation-scoped cancellation and no retry policy.

- 2026-08-08T13:58:06Z: T-006 and T-007 are done; the typed HTTP ticket client and transport-independent RPC codec are verified.

- 2026-08-08T10:32:25Z: Created from .project/templates/task.md by `delano task add`.
