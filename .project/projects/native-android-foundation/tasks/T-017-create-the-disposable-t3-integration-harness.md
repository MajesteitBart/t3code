---
id: T-017
name: Create the disposable T3 integration harness
status: planned
workstream: WS-E
created: 2026-08-07T13:17:03Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002, T-005]
conflicts_with: [test server ports, integration fixture state]
parallel: true
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-005
acceptance_criteria_ids: [AC-003, AC-004, AC-005]
---

# Task: Create the disposable T3 integration harness

## Description

Provide a focused harness that starts two isolated disposable T3 server processes with synthetic/worktree-local state, captures each PID/port safely, exposes snapshots/receipts/drains, and tears down only what it started.

## Acceptance Criteria

- [ ] The harness never reads or writes live T3 home data.
- [ ] Two independent server processes use separate disposable home directories and ports and expose colliding raw project/thread identifiers.
- [ ] The harness captures both exact spawned PIDs and resolved ports, verifies their working directories/ownership, and stops only those processes.
- [ ] Pairing and secret values are redacted from logs and Delano evidence.
- [ ] Tests can await typed snapshots, command receipts/domain events, and worker drains without sleeps or polling.

## Traceability

- Story: US-005
- Acceptance criteria: AC-003, AC-004, AC-005

## Technical Notes

- For this proof, one T3 server process represents one environment. Do not fake two environments inside one server merely to reduce process count.
- Allocate/capture each port and PID independently. Do not find or stop processes by name, path substring, or broad port scan; validate any port owner before termination.
- Each process receives worktree-local synthetic state only. Never point either process at `~/.t3/userdata` or copy an active SQLite database unsafely.
- Seed the same raw project and thread IDs in both environments plus a precreated thread used by T-013.
- Expose test-only observation through existing typed server seams/receipts and drains; do not create a production receipt lookup protocol for Android.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:03Z: Created from .project/templates/task.md by `delano task add`.
