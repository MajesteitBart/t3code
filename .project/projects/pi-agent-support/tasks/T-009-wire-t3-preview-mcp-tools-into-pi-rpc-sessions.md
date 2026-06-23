---
id: T-009
name: Wire T3 preview MCP tools into Pi RPC sessions
status: blocked
workstream: WS-E
created: 2026-06-23T10:55:02Z
updated: 2026-06-23T10:55:11Z
linear_issue_id: 
github_issue: https://github.com/pingdotgg/t3code/issues/402
github_pr: 
depends_on: [T-003]
conflicts_with: [apps/server/src/provider/Layers/PiAdapter.ts, apps/server/src/piRpcManager.ts]
parallel: false
priority: high
estimate: M
story_id: US-005
acceptance_criteria_ids: [AC-008, AC-009]
blocked_owner: Bart
blocked_check_back: after T-003 creates the Pi RPC runtime/session manager
operating_mode: feature
---

# Task: Wire T3 preview MCP tools into Pi RPC sessions

## Description

Inject or otherwise register T3 Code's provider-scoped preview MCP server with Pi RPC sessions so Pi can use the in-app browser through preview_* tools when the Pi runtime supports external MCP servers.

## Acceptance Criteria

- [ ] Pi receives the T3 MCP endpoint and Authorization header from the provider-scoped session credential when a Pi session starts.
- [ ] Pi can discover or invoke the T3 preview_* tools through its RPC runtime when external MCP server support is available.
- [ ] Unavailable desktop preview owners and unsupported Pi MCP capability fail with explicit recoverable errors, not fake browser success.
- [ ] The implementation does not silently fall back to Pi agent-browser, global Chrome automation, or standalone Playwright for T3 in-app browser work.
- [ ] Tests or documented probe evidence cover MCP config injection, unavailable preview-owner handling, and the unsupported-Pi-MCP path.

## Traceability

- Story: US-005
- Acceptance criteria: AC-008, AC-009

## Technical Notes

Use the current provider MCP path as the reference:

- `ProviderService.prepareMcpSession` issues a provider-scoped credential before adapter startup.
- `McpProviderSession.readMcpProviderSession(threadId)` exposes the endpoint and Authorization header to adapters.
- Cursor and Grok pass the config as `mcpServers`; OpenCode registers it through `mcp.add`.
- The T3 browser tools are defined in `apps/server/src/mcp/toolkits/preview/tools.ts` and routed through `PreviewAutomationBroker`.
- Do not route Pi browser work to Pi `agent-browser`, global Chrome, or standalone Playwright unless the user explicitly asks outside this provider integration.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-06-23T10:55:02Z: Created from .project/templates/task.md by `delano task add`.
- 2026-06-23: Browser/MCP architecture decision folded into task; blocked until Pi runtime manager exists.
