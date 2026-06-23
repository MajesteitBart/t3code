---
id: T-002
name: Add Pi contracts and settings schema
status: blocked
workstream: WS-A
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
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
blocked_owner: Bart
blocked_check_back: after T-001 records Pi RPC and settings decisions
operating_mode: feature
---

# Task: Add Pi contracts and settings schema

## Description

Add contract-level Pi settings, defaults, patches, display names, model defaults only if justified by discovery behavior, and provider-native option descriptors for thinking/reasoning.

## Acceptance Criteria

- [ ] `PiSettings` decodes from empty config and supports configured binary path.
- [ ] Server settings and patch schemas preserve Pi provider instances.
- [ ] Pi provider metadata is browser-safe and does not close `ProviderDriverKind`.
- [ ] No fake static Pi model fallback is introduced.
- [ ] Contract tests cover decode and patch behavior.

## Traceability

- Story: US-003
- Acceptance criteria: AC-003, AC-004, AC-007

## Technical Notes

Use `ProviderInstanceConfig.config` for driver-owned payloads. Keep settings additions compatible with unknown driver round-tripping.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated if settings behavior changes

## Evidence Log

- 2026-06-23: Blocked pending T-001 probe.
