---
id: T-005
name: Create canonical contract conformance fixtures
status: done
workstream: WS-B
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T13:33:09Z
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

- [x] A checked inventory names every HTTP and RPC contract used by the foundation.
- [x] Fixtures cover success, omitted optional/capability fields, unknown fields and stream items, incompatible enum values, large 64-bit sequence values, model-selection legacy aliases where consumed, and representative errors with `traceId`.
- [x] A focused command fails when Kotlin fixtures drift from the canonical TypeScript contract source.
- [x] A fixture manifest records the canonical contracts Git commit/content hash used to generate or validate the fixture set.
- [x] Fixture files contain no credentials, pairing codes, absolute private paths, or live user data.

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

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T13:33:09Z: foundationContractConformance passed debug/release Kotlin decoding plus TypeScript/Effect provenance; scripts typecheck and core-protocol debug/release lint passed; deliberate stale-fixture probe failed actionably and restoration rechecked cleanly.

- 2026-08-08T13:32:27Z: Added the six-endpoint/two-method checked inventory, 25 deterministic fixture payloads plus manifest, normalized canonical-source Git revision/content hash, installed Effect RPC version/source hash, Kotlin wire models, legacy-model alias promotion, and explicit unknown-stream refresh classification.
- 2026-08-08T13:32:27Z: `node scripts/export-native-android-contract-fixtures.ts --check` passed. A deliberate one-file stale-fixture probe failed with the exact path and regeneration command, then `--write` restored the canonical byte output and `--check` passed again.
- 2026-08-08T13:32:27Z: `pnpm exec vp run --filter @t3tools/scripts typecheck` passed. `./gradlew.bat foundationContractConformance --no-daemon` passed the exporter and debug/release Kotlin suites; `:core-protocol:lintDebug :core-protocol:lintRelease --no-daemon` passed warnings-as-errors lint.
- 2026-08-08T13:32:27Z: Self-review: checked all five acceptance criteria against `CONTRACTS.md`, manifest metadata, fixture cases, negative drift behavior, and Kotlin assertions; verified fixtures use only synthetic/redacted values and contain no absolute private paths or live data. No GUI, emulator, or server process is applicable to this contract-only task.

- 2026-08-08T13:22:18Z: Begin the bounded canonical HTTP/RPC inventory, deterministic fixture exporter, source-revision provenance, Kotlin models, and AC-007 conformance gate.

- 2026-08-08T13:22:17Z: Readiness reviewed: T-002 is done; WS-A provides the buildable core-protocol target; canonical TypeScript contracts and endpoint definitions are available; baseline Delano validation and core-protocol Gradle test pass.

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
