---
id: T-009
name: Implement protected credentials and environment persistence
status: planned
workstream: WS-C
created: 2026-08-07T13:16:56Z
updated: 2026-08-08T10:32:44Z
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

- [ ] Access credentials are protected by the approved Android Keystore-backed mechanism and never stored in Room.
- [ ] Environment metadata, active selection, and last-known shell state persist in a versioned Room schema.
- [ ] Save writes the credential before the catalog and restores/removes it if the catalog write fails; removal writes the catalog before deleting the credential and restores catalog/selection on deletion failure.
- [ ] Rollback failures are collected into a typed persistence failure without hiding the initiating error or exposing secret material.
- [ ] The stored credential envelope explicitly identifies direct bearer and fails closed on unknown/mismatched kinds; no unused DPoP key/thumbprint fields are introduced during foundation.
- [ ] Tests cover process recreation, corrupt state, missing Keystore material, migration, and deletion.

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

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:56Z: Created from .project/templates/task.md by `delano task add`.
