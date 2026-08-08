---
id: T-003
name: Document and wire focused Android commands
status: planned
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002]
conflicts_with: [package.json, README command sections]
parallel: true
priority: medium
estimate: S
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Document and wire focused Android commands

## Description

Expose stable local build, unit-test, lint/static, instrumentation, and install entry points and document prerequisites without adding repo-wide checks.

## Acceptance Criteria

- [ ] The native Android README lists exact prerequisites and focused build, test, lint, instrumentation, and install commands.
- [ ] Each documented command resolves from the documented working directory.
- [ ] Root task wiring, if added, invokes only native Android scope.
- [ ] No command accesses live T3 home data or prints signing and credential values.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- Reserve distinct focused commands for assemble/install, unit/static checks, AC-007 contract conformance, integration harness, instrumentation/accessibility, and AC-008 performance evidence; do not hide all gates behind one repo-wide command.
- Commands that start helpers must expose/capture exact PIDs and isolated home/port inputs so T-017 can stop only what it starts.
- Redact access credentials, pairing codes, WebSocket tickets, App Link signing material, and private paths from example output.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
