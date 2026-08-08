---
id: T-019
name: Add focused CI and foundation documentation
status: planned
workstream: WS-E
created: 2026-08-07T13:17:03Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-003, T-008, T-012, T-013, T-016, T-018]
conflicts_with: [CI workflows, docs, testing skills]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-004
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008]
---

# Task: Add focused CI and foundation documentation

## Description

Add risk-appropriate native Android CI and update app/internal/testing documentation with exact scope, commands, identities, limitations, and evidence mapping.

## Acceptance Criteria

- [ ] Focused CI runs native Android build, unit, static/lint, contract, and the approved feasible integration checks without invoking the repo-wide suite.
- [ ] CI caches and emulator lifecycle do not share mutable T3 home state.
- [ ] Documentation distinguishes React Native Android, SwiftUI mobile, and native Android and states foundation limitations.
- [ ] Every spec acceptance scenario maps to a passing command or artifact.
- [ ] CI fails when canonical contracts move without regenerated/provenanced fixtures and enforces the recorded performance budgets on the approved runner only.
- [ ] Any shared Kotlin extraction has a passing focused React Native compatibility check.

## Traceability

- Story: US-004
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008

## Technical Notes

- Publish an acceptance-to-evidence table covering AC-001..AC-008. AC-007 names the fixture provenance/drift command; AC-008 names the measured runner/device and budget artifact.
- Documentation must call PR #5178 an open experimental SwiftUI reference, not a shipped client or canonical Android contract.
- Document active/passive supervision, snapshot-first ambiguity recovery, target-SDK local-network permission behavior, trusted App Link versus arbitrary paste input, and explicit foundation non-goals.
- Keep expensive emulator/benchmark jobs focused and opt-in until their flake/variance evidence justifies a required gate.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:03Z: Created from .project/templates/task.md by `delano task add`.
