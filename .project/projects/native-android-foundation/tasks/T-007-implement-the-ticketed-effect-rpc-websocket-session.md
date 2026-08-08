---
id: T-007
name: Implement Effect RPC framing and codecs
status: planned
workstream: WS-B
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-005]
conflicts_with: [core-protocol WebSocket session]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-004
acceptance_criteria_ids: [AC-007]
---

# Task: Implement Effect RPC framing and codecs

## Description

Implement transport-independent Effect RPC request/control/response codecs and the bounded foundation method/stream models against canonical fixtures. Session lifecycle is T-021.

## Acceptance Criteria

- [ ] Request fixtures encode `_tag=Request`, monotonic integer `id`, method `tag`, payload, and exactly `headers: []` when no headers exist.
- [ ] Control/response fixtures cover `Ping`, `Pong`, `Ack {requestId}`, `Interrupt {requestId}`, `Chunk {requestId,values}`, `Exit`, `Defect`, and `ClientProtocolError` with typed remote/protocol errors.
- [ ] Foundation unary/subscription payloads and shell stream variants decode canonical fixtures with 64-bit sequence fidelity and no `Double` bridge.
- [ ] Unknown stream variants produce the explicit refresh-required result consumed by T-022 rather than an unsafe partial mutation.
- [ ] Codec tests run without a network and fail through the AC-007 focused conformance command when canonical source changes.

## Traceability

- Story: US-004
- Acceptance criteria: AC-007

## Technical Notes

- Swift cross-check: `apps/swift-ios/Core/WebSocketRPC.swift`; canonical authority remains the Effect/server contract. Its `RPCRequestEnvelope.headers` type is `[[String]]`, but every empty request is constructed with `[]`.
- `Chunk` values are yielded before `Ack`. `Exit.Success` returns `value`; failure text may be found under the first cause's `error.message` or `error.detail`. Fatal protocol tags terminate the affected session.
- Shell subscription payload: `orchestration.subscribeShell {requestCompletionMarker:true, afterSequence?}`. Shell items observed by the reference are `synchronized|snapshot|project-upserted|project-removed|thread-upserted|thread-removed`; sequence-bearing deltas remain monotonic.
- Add only methods consumed by foundation. Keep the full projects/VCS/terminal/review/assets/source-control catalog as follow-on inventory.
- Keep codecs independent of OkHttp, coroutines, retry, and Android lifecycle so T-021 and deterministic tests can own those concerns.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
