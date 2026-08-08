---
id: T-004
name: Decide reuse boundaries for existing Kotlin modules
status: planned
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: [apps/mobile/modules]
parallel: true
priority: medium
estimate: M
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Decide reuse boundaries for existing Kotlin modules

## Description

Audit the terminal, review diff, composer, and native controls modules for Expo, JNI, resource, and toolchain coupling; record extract, adapt, or defer decisions.

## Acceptance Criteria

- [ ] The decision log records extract, adapt, or defer for each existing Android native module.
- [ ] Each decision identifies Expo, JNI, resource, license, and toolchain coupling with source references.
- [ ] Any proposed extraction retains a thin Expo adapter and a focused React Native compatibility check.
- [ ] No production module is moved solely to satisfy this audit task.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- The Swift feature inventory is evidence for later project families, not permission to extract terminal/review/composer code during foundation.
- Weight the audit toward ordinary Android library boundaries, Expo/JNI/resource/toolchain coupling, and whether a thin existing React Native adapter can remain. Do not preselect Ghostty or any UI module solely because a future parity area may use it.
- Record follow-on ownership and prerequisites in T-020; leave implementation/decomposition to an approved project.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
