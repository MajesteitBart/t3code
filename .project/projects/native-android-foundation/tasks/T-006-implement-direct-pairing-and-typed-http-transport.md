---
id: T-006
name: Implement direct pairing and typed HTTP transport
status: planned
workstream: WS-B
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-005]
conflicts_with: [core-protocol HTTP API]
parallel: true
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-002
acceptance_criteria_ids: [AC-002]
---

# Task: Implement direct pairing and typed HTTP transport

## Description

Implement pairing URL parsing, target-SDK-aware endpoint probing, RFC 8693 one-time credential exchange, the foundation HTTP subset, and layered typed failures with cancellation.

## Acceptance Criteria

- [ ] Canonical URL, loose host/code, bare-host, and `t3code://pair?pairingUrl=...` forms parse deterministically; fragment token wins over query, and one input derives HTTP plus WebSocket bases.
- [ ] Valid input performs the public descriptor read and exact RFC 8693 form exchange with `client_device_type=mobile`, `client_os=Android`, optional Android label, and strict direct `issued_token_type`/`token_type=Bearer` validation.
- [ ] Malformed, authorization-rejected, local-network-permission-denied when applicable, unreachable, timeout, cancelled, server-rejected, and transport cases map to tested layered failures that preserve status/reason/message/`traceId` without secrets.
- [ ] One-time pairing credentials are not written to disk or emitted in logs.
- [ ] HTTP calls expose one logical attempt and contain no application retry/backoff loop; the selected client is configured/tested so side-effecting requests are not silently replayed.
- [ ] A controlled gzip response is advertised/decoded using the selected client's transparent-compression behavior without assuming an explicit application `Accept-Encoding` header.

## Traceability

- Story: US-002
- Acceptance criteria: AC-002

## Technical Notes

- Token exchange fields: `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token`, `subject_token_type=urn:t3:params:oauth:token-type:environment-bootstrap`, and `requested_token_type=urn:ietf:params:oauth:token-type:access_token`.
- Pairing grammar reference: `apps/swift-ios/Core/PairingURL.swift`. Support `http|https|ws|wss`, fragment-before-query token lookup, optional label/host, loose `host code` with a 4..256 character token, bare hosts defaulting to HTTPS, and HTTP/WS scheme conversion.
- Arbitrary server URLs remain valid explicit input. Trusted-host restrictions apply only to inbound web App Links owned by T3, not to pasted pairing text or the custom wrapper.
- Android local-network denial is platform-conditional, not `n/a`: follow the T-001 target-SDK decision and official Android 16/17 guidance.
- T-006 returns the exchanged direct credential plus non-secret metadata to the persistence coordinator; T-009 owns save/remove ordering and rollback.
- A direct-bearer 401 from an authorized endpoint or ticket mint is a revoked/re-pair-required result, not a refresh loop. Managed DPoP refresh remains out of scope.
- OkHttp normally manages transparent gzip. Verify the wire offer and decoded body for the pinned version instead of forcing Swift's explicit-header implementation.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
