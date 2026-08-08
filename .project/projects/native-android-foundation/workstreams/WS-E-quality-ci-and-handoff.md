---
id: WS-E
name: WS-E Quality, CI, and Handoff
owner: team
status: planned
created: 2026-08-07T13:12:09Z
updated: 2026-08-07T13:12:21Z
operating_mode: multi-stream
---

# Workstream: WS-E Quality, CI, and Handoff

## Objective

Make the native foundation reproducible and reviewable through disposable-server fixtures, focused CI, instrumentation/performance evidence, documentation, and an explicit go/no-go decision for subsequent parity projects.

## Owned Files/Areas

- Proposed `apps/kotlin-android/core-testing/`, instrumentation tests, Macrobenchmark/baseline profile targets
- Two-process disposable T3 server fixture and focused integration entry points with separate captured PIDs/ports/home directories
- CI workflow changes specific to native Android
- App README plus applicable `docs/internals/`, operations, and testing-skill updates
- Final parity matrix, evidence index, risk review, and follow-on project recommendations

## Dependencies

- WS-A supplies stable build/test commands.
- WS-B supplies protocol fixtures and deterministic transport seams.
- WS-C supplies lifecycle, stale-publication, snapshot-recovery, and receipt/domain-effect scenarios.
- WS-D supplies user-visible phone/tablet/accessibility flows.

## Risks

- Emulator and server startup can become flaky if tests use sleeps, shared ports, or live state.
- CI scope can accidentally become repo-wide or excessively expensive.
- Benchmark results from one emulator can be overgeneralized to physical devices.
- Documentation can imply production readiness before authentication, feature, and release projects exist.

## Handoff Criteria

- Focused build, unit, integration, instrumentation, lint/static, and benchmark commands are documented and pass in their intended environments.
- Tests use two isolated disposable environments, deterministic receipts/drains, exact captured PIDs, and no arbitrary sleeps.
- Existing React Native checks pass for any shared Kotlin extraction.
- Evidence maps every spec acceptance scenario to a command or artifact.
- A written foundation review recommends proceed, revise, or stop and scopes the next project without claiming full Android parity.
