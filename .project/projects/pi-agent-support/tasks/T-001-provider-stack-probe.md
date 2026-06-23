---
id: T-001
name: Audit current provider stack and Pi reference
status: done
workstream: WS-A
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T15:19:24Z
linear_issue_id: 
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr: 
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-004
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007]
operating_mode: feature
---

# Task: Audit current provider stack and Pi reference

## Description

Compare issue #402, the fork PR, current main's provider architecture, and the T3 preview MCP browser path. Produce the implementation map for Pi RPC startup, settings, dynamic model discovery, thinking options, event mapping, browser MCP integration, and failure cleanup.

## Acceptance Criteria

- [x] Issue #402 requirements are mapped to current files and provider abstractions.
- [x] Fork PR behavior is reviewed as reference without treating it as merge source.
- [x] Exact Pi RPC command/protocol questions are listed, with live probe results when Pi is locally available.
- [x] T3's provider-scoped MCP preview path is mapped, including how existing providers receive the endpoint and Authorization header.
- [x] Pi RPC support for external MCP server registration or extension-backed MCP bridging is confirmed, disproven, or left as an explicit blocker with the exact evidence needed.
- [x] `spec.md`, `plan.md`, or `decisions.md` are updated with concrete implementation decisions.
- [x] Downstream blocked tasks have enough information to start safely.

## Traceability

- Story: US-004
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007

## Technical Notes

Start from:

- `packages/contracts/src/providerInstance.ts`
- `packages/contracts/src/settings.ts`
- `packages/contracts/src/model.ts`
- `apps/server/src/provider/ProviderDriver.ts`
- `apps/server/src/provider/builtInDrivers.ts`
- `apps/server/src/provider/Layers/GrokAdapter.ts`
- `apps/server/src/provider/Layers/CursorAdapter.ts`
- `apps/server/src/provider/Layers/OpenCodeAdapter.ts`
- `apps/server/src/provider/Layers/GrokProvider.ts`
- `apps/server/src/mcp/McpSessionRegistry.ts`
- `apps/server/src/mcp/McpProviderSession.ts`
- `apps/server/src/mcp/toolkits/preview/tools.ts`
- `apps/web/src/components/preview/PreviewAutomationOwner.tsx`
- `apps/web/src/components/settings/providerDriverMeta.ts`
- `apps/web/src/session-logic.ts`

## Definition of Done

- [x] Implementation map recorded
- [x] Probe findings recorded
- [x] Browser/MCP support decision recorded
- [x] Decisions updated
- [x] Validation command recorded

## Evidence Log

- 2026-06-23T15:19:24Z: Follow-up probe confirmed installed `pi-mcp-adapter` 2.10.0 registers `--mcp-config`; downstream implementation uses that extension bridge for T3 preview MCP rather than the superseded unsupported-browser path.

- 2026-06-23T12:43:49Z: Superseded for browser support. Live Pi 0.79.10 RPC probe recorded core RPC behavior; later `pi-mcp-adapter` probe supplied the external MCP bridge path.

- 2026-06-23T12:36:54Z: Starting live Pi RPC/provider architecture probe before implementation

- 2026-06-23T12:42:36Z: Live Pi 0.79.10 probe completed. `pi --mode rpc` supports JSONL `get_state`, `get_available_models`, `set_model`, `set_thinking_level`, `prompt`, and streamed message/tool/turn events; dynamic model discovery returned 35 local models including Baseten `zai-org/GLM-5.2`; prompt smoke streamed assistant `text_delta` content and turn completion; installed core package docs/types expose no core remote MCP registration path.
- 2026-06-23: Task created during Delano bootstrap; implementation evidence pending.
- 2026-06-23: Browser/MCP audit folded into project contracts; later follow-up confirmed the `pi-mcp-adapter` bridge.
