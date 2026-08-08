---
id: WS-B
name: WS-B Contracts and Transport
owner: team
status: done
created: 2026-08-07T13:12:09Z
updated: 2026-08-08T14:19:23Z
operating_mode: multi-stream
---

# Workstream: WS-B Contracts and Transport

## Objective

Provide a canonical-fixture-checked Kotlin protocol boundary for pairing, tickets, HTTP, and Effect RPC WebSockets with explicit cancellation and no hidden retry policy.

## Owned Files/Areas

- `apps/kotlin-android/core-protocol/`
- Focused contract-fixture exporter/validator near `packages/contracts`
- Pairing URL/HTTP clients, environment descriptors, ticket API, Effect RPC framing/codecs, one-attempt WebSocket session lifecycle, serializers, and layered typed failures
- Contract and transport unit/integration tests

## Dependencies

- WS-A toolchain/module decisions and buildable protocol test target.
- Canonical server contracts and disposable server fixture shape.
- Authentication and pairing behavior documented by existing clients.

## Risks

- Native models can silently drift from Effect schemas.
- Copying an observed Swift JSON example incorrectly (for example `headers: [[]]` instead of `headers: []`) can create a second incompatible protocol.
- Cancellation/socket-close races can leak collectors or resume stale streams.
- Adding retry inside HTTP/WebSocket layers would conflict with WS-C supervision.
- Logs can expose pairing or access credentials unless redaction is tested.

## Handoff Criteria

- [x] Canonical fixtures cover every contract used by the foundation shell.
- [x] Pairing and ticket clients classify failures without leaking credentials.
- [x] RPC framing covers the required tags and canonical method subset with 64-bit integer fidelity, unknown-item recovery, and contracts-revision provenance.
- [x] One-attempt sessions mint a fresh ticket, distinguish sent from unsent work, support session-owned cancellation, and hand deterministic close state to WS-C without internal reconnect.
- [x] HTTP gzip and WebSocket compression compatibility are proven using the selected stack without assuming extension-negotiation introspection.
- [x] Socket race and malformed-payload tests pass without sleep-based synchronization.
- [x] WS-C can consume a one-attempt session abstraction without knowing wire details.

## Completion Evidence

- T-005, T-006, T-007, T-021, and T-008 are done with every task acceptance criterion and Definition of Done item checked.
- `foundationContractConformance` validates 27 deterministic files against canonical contract Git/content hashes plus the installed Effect RPC source hash before debug/release Kotlin tests.
- `foundationTransportIntegration` covers virtual-time ownership races and a real bidirectional OkHttp/MockWebServer `permessage-deflate` round trip; Android lint passes for debug and release.
- Final high-risk review hardening passed a forced 84-task Gradle run after the last source changes; evidence is recorded in `.agents/logs/tests/20260808T143159Z.log` and the WS-B quality-closeout update.
- `core-protocol/CONTRACTS.md` is the WS-C handoff: retry, backoff, replacement sessions, and recreation of long-lived intent remain supervisor-owned.
