---
id: T-008
name: Validate Pi end to end and document setup
status: done
workstream: WS-D
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T15:19:24Z
linear_issue_id: 
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr: 
depends_on: [T-007]
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008, AC-009]
operating_mode: feature
---

# Task: Validate Pi end to end and document setup

## Description

Run final gates, perform a live Pi provider smoke when Pi is available, verify in-app browser behavior through `pi-mcp-adapter` or explicit bridge failures, and document user setup/known limitations.

## Acceptance Criteria

- [x] `vp check` passes.
- [x] `vp run typecheck` passes.
- [x] Targeted Pi tests pass.
- [x] Browser verification covers provider picker, model picker, and a Pi turn or unavailable-Pi fallback.
- [x] In-app browser verification covers Pi access to T3 `preview_*` MCP tools through `pi-mcp-adapter`, or records an explicit bridge/preview-owner limitation.
- [x] Provider documentation explains Pi setup, binary path, model discovery, thinking options, T3 in-app browser support/limitations, and v1 limitations.
- [x] Project updates record final evidence and any remaining blockers.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007

## Technical Notes

If Pi is unavailable locally, record that as a blocker and still verify unavailable-provider behavior. If Pi is available but cannot attach T3's MCP preview server through `pi-mcp-adapter`, record that as a browser-capability limitation rather than substituting Pi `agent-browser` or standalone browser automation.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Browser verification complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T15:19:24Z: MCP correction evidence: `pi-mcp-adapter` 2.10.0 is installed; live Pi RPC smoke accepted `--mcp-config` and returned `get_state`; focused adapter tests verify scoped config injection, token-safe bearer handling, appended browser instructions, and no external-browser fallback.
- 2026-06-23T14:02:34Z: Final evidence before MCP correction: vp check passed; vp run typecheck passed; contract settings tests passed; Pi server/provider/runtime tests passed; web model/composer tests passed; pi --version returned 0.79.10; Pi RPC get_available_models returned dynamic local models; browser smoke verified Pi Settings > Providers disabled/early-access state; docs/providers/pi.md added.

- 2026-06-23: Blocked pending T-007.
