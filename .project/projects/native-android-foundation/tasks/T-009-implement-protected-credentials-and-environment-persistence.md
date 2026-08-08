---
id: T-009
name: Implement protected credentials and environment persistence
status: done
workstream: WS-C
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T15:00:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002, T-006]
conflicts_with: [core-data database schema, credential repository]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-002
acceptance_criteria_ids: [AC-002]
---

# Task: Implement protected credentials and environment persistence

## Description

Add separate Keystore-backed credential and Room-backed environment repositories with versioned schemas, compensated save/removal ordering, atomic activation, and process-recreation behavior.

## Acceptance Criteria

- [x] Access credentials are protected by the approved Android Keystore-backed mechanism and never stored in Room.
- [x] Environment metadata, active selection, and last-known shell state persist in a versioned Room schema.
- [x] Save writes the credential before the catalog and restores/removes it if the catalog write fails; removal writes the catalog before deleting the credential and restores catalog/selection on deletion failure.
- [x] Rollback failures are collected into a typed persistence failure without hiding the initiating error or exposing secret material.
- [x] The stored credential envelope explicitly identifies direct bearer and fails closed on unknown/mismatched kinds; no unused DPoP key/thumbprint fields are introduced during foundation.
- [x] Tests cover process recreation, corrupt state, missing Keystore material, migration, and deletion.

## Traceability

- Story: US-002
- Acceptance criteria: AC-002

## Technical Notes

- Behavioral reference: `PairingService.pair` and `EnvironmentRuntime.saveManagedEnvironment/remove` in `apps/swift-ios/Core/T3Client.swift`.
- Required save invariant: a catalog row must never point to a credential that was not durably stored. Required remove invariant: a destroyed credential must never leave a catalog row that claims the environment is usable.
- Keep credential and catalog operations separately testable. Cross-repository atomicity is compensation, not a fake Room transaction over Keystore.
- The app is new, so the review's proposed "legacy bearer default" and preemptive DPoP Room fields are unnecessary. Unknown credential-kind data is incompatible/re-pair-required, not silently bearer.
- Never persist the one-time pairing code, WebSocket ticket, raw secret in Room, or a secret-bearing URL.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T15:00:44Z: Final KSP-based debug/release unit and lint gates pass; four API-35 device tests prove real Keystore protection, Room migration/process recreation/corruption/deletion, and compensated persistence.

- 2026-08-08T15:00:12Z: Implemented AES-256-GCM Android Keystore protection with environment-bound authenticated data, a fail-closed versioned direct-bearer envelope, app-private ciphertext storage outside Room, Room schema v2 plus non-destructive `MIGRATION_1_2`, atomic catalog selection/snapshot writes, and non-cancellable credential-first/catalog-first compensation with typed initiating and rollback failures.
- 2026-08-08T15:00:12Z: `:core-data:testDebugUnitTest :core-data:testReleaseUnitTest :core-data:lintDebug :core-data:lintRelease --no-daemon` passed on the final KSP 2.3.9 processor setup. Pure tests cover envelope compatibility, exact save/remove ordering, old-credential restoration, rollback-failure collection, and selection restoration.
- 2026-08-08T15:00:12Z: `:core-data:connectedDebugAndroidTest --no-daemon` passed four tests on the API-35 `TwentyGoApi35` AVD after the final source changes. Evidence covers real Android Keystore process recreation, ciphertext/AAD swap rejection, missing key material, real Room v1-to-v2 migration, corrupt descriptor failure, process recreation, cascade deletion, and environment-isolated removal. The exact captured emulator launch/child PIDs were stopped after the run.
- 2026-08-08T15:00:12Z: Self-review checked all six acceptance criteria and four Definition of Done items against production code, checked-in Room schema, pure/device tests, warnings-as-errors lint, backup exclusions, and `core-data/PERSISTENCE.md`; no one-time pairing code, WebSocket ticket, DPoP placeholder, live data, or non-synthetic credential entered persistence or evidence.

- 2026-08-08T14:42:45Z: Implement separated Keystore credential protection and versioned Room environment persistence with compensated save/remove and process-recreation evidence.

- 2026-08-08T14:42:45Z: Readiness reviewed: WS-A/WS-B dependencies are done, baseline validation and core-data/protocol unit targets pass, and persistence ownership is isolated to core-data.

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
