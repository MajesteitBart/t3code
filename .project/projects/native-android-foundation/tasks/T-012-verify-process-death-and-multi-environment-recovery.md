---
id: T-012
name: Verify process death and multi-environment recovery
status: planned
workstream: WS-C
created: 2026-08-07T13:17:00Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-017, T-022]
conflicts_with: [Room integration fixtures, connection integration tests]
parallel: true
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-002
acceptance_criteria_ids: [AC-002, AC-003, AC-004]
---

# Task: Verify process death and multi-environment recovery

## Description

Exercise two isolated colliding environments through process recreation, active/passive refresh, offline startup, reconnect, revocation, and removal using the disposable integration harness.

## Acceptance Criteria

- [ ] A forced process recreation restores both environments, active selection, and last-known scoped rows.
- [ ] Offline startup renders last-known state and reconciles it after connectivity returns.
- [ ] A failed passive read marks only that environment unreachable and retains its last-known rows; activating it transfers live-subscription ownership without duplicate sockets.
- [ ] An authorized HTTP or ticket-mint 401 marks only the affected direct environment revoked/re-pair-required with no auth loop; removal clears the correct credential, state, and owner without affecting the other environment.
- [ ] The integration run records no duplicate subscriptions, stale routes, or cross-environment state leakage.

## Traceability

- Story: US-002
- Acceptance criteria: AC-002, AC-003, AC-004

## Technical Notes

- T-017 supplies two server processes, so restart, revocation, and outage can target one environment independently.
- Include a case where active WebSocket data is unavailable but an HTTP shell refresh succeeds; rows advance while presentation remains `reconnecting`.
- Include a delayed result from the pre-activation/passive owner and prove it cannot overwrite state after ownership transfers.
- Observe supervisor/socket/subscription counts through test seams rather than process-name scans or timing guesses.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:00Z: Created from .project/templates/task.md by `delano task add`.
