---
id: T-005
name: Create canonical contract conformance fixtures
status: planned
workstream: WS-B
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002]
conflicts_with: [packages/contracts, contract fixture directory]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-004
acceptance_criteria_ids: [AC-007]
---

# Task: Create canonical contract conformance fixtures

## Description

Define the foundation contract inventory and add a focused TypeScript fixture exporter or validator plus Kotlin decoding tests tied to packages/contracts.

## Acceptance Criteria

- [ ] A checked inventory names every HTTP and RPC contract used by the foundation.
- [ ] Fixtures cover success, omitted optional/capability fields, unknown fields and stream items, incompatible enum values, large 64-bit sequence values, model-selection legacy aliases where consumed, and representative errors with `traceId`.
- [ ] A focused command fails when Kotlin fixtures drift from the canonical TypeScript contract source.
- [ ] A fixture manifest records the canonical contracts Git commit/content hash used to generate or validate the fixture set.
- [ ] Fixture files contain no credentials, pairing codes, absolute private paths, or live user data.

## Traceability

- Story: US-004
- Acceptance criteria: AC-007

## Technical Notes

- Authority: `packages/contracts/src/auth.ts`, `environmentHttp.ts`, `orchestration.ts`, and `rpc.ts`, plus current server endpoint definitions. Swift is a behavioral cross-check, never the fixture source of truth.
- Initial HTTP candidate inventory is deliberately bounded: `/.well-known/t3/environment`, `/oauth/token`, `/api/orchestration/shell`, the thread snapshot used by T-013, `/api/orchestration/dispatch`, and `/api/auth/websocket-ticket`. Add `/api/orchestration/snapshot` only if the chosen reducer/recovery path consumes it. Session/client-management and full pagination endpoints are follow-on unless an acceptance test requires them.
- Initial RPC subset is `orchestration.dispatchCommand` and `orchestration.subscribeShell`, plus only the configuration/thread methods demonstrably consumed by the foundation implementation. Record the wider method catalog separately; do not generate unused Kotlin clients now.
- Effect RPC empty request headers encode as `"headers":[]`. The deep-review example `"headers":[[]]` is wrong.
- Canonical wire interaction values are `default|plan`; Swift UI `.standard` maps to wire `.default` and must not appear in fixtures.
- Preserve integer tokens as Kotlin `Long`/JSON integer values without a `Double` bridge. Stable fixture output should sort keys or otherwise be byte-deterministic.
- Unknown shell/thread stream variants should exercise the documented bounded-refresh path rather than silently mutating state. Optional capability flags remain lenient and environment-scoped.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
