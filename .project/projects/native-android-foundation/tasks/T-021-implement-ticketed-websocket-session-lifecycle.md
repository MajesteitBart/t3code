---
id: T-021
name: Implement ticketed WebSocket session lifecycle
status: planned
workstream: WS-B
created: 2026-08-08T10:32:25Z
updated: 2026-08-08T10:32:44Z
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

- [ ] Each connection attempt mints a fresh one-use WebSocket ticket and redacts it from URLs in logs and evidence.
- [ ] Sent unary calls fail as ambiguous on disconnect and are never replayed; provably unsent calls may fail as connectionUnavailable after a bounded configurable wait.
- [ ] Cancellation sends Interrupt only while the request still belongs to the current session, and one-shot streams fail rather than resubscribe.
- [ ] The session exposes closure once, releases resources deterministically, and performs no internal reconnect or backoff.

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

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-08T10:32:25Z: Created from .project/templates/task.md by `delano task add`.
