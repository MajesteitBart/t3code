---
id: T-002
name: Add Pi contracts and settings schema
status: done
workstream: WS-A
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T12:47:56Z
linear_issue_id: 
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr: 
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-003
acceptance_criteria_ids: [AC-003, AC-004, AC-007]
operating_mode: feature
---

# Task: Add Pi contracts and settings schema

## Description

Add contract-level Pi settings, defaults, patches, display names, model defaults only if justified by discovery behavior, and provider-native option descriptors for thinking/reasoning.

## Acceptance Criteria

- [x] `PiSettings` decodes from empty config and supports configured binary path.
- [x] Server settings and patch schemas preserve Pi provider instances.
- [x] Pi provider metadata is browser-safe and does not close `ProviderDriverKind`.
- [x] No fake static Pi model fallback is introduced.
- [x] Contract tests cover decode and patch behavior.

## Traceability

- Story: US-003
- Acceptance criteria: AC-003, AC-004, AC-007

## Technical Notes

Use `ProviderInstanceConfig.config` for driver-owned payloads. Keep settings additions compatible with unknown driver round-tripping.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated if settings behavior changes

## Evidence Log

- 2026-06-23T12:47:56Z: Pi settings contracts and browser-safe provider metadata implemented. Verified with .\\node_modules\\.bin\\vp.cmd run --filter @t3tools/contracts test -- settings.test.ts and .\\node_modules\\.bin\\vp.cmd run --filter @t3tools/contracts typecheck.

- 2026-06-23T12:44:23Z: Adding Pi settings contracts and provider metadata

- 2026-06-23T12:44:17Z: T-001 completed Pi RPC/settings decisions; contract settings work can begin

- 2026-06-23: Blocked pending T-001 probe.
