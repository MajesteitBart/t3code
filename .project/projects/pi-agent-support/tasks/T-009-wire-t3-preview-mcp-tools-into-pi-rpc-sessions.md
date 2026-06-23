---
id: T-009
name: Wire T3 preview MCP tools into Pi RPC sessions
status: done
workstream: WS-E
created: 2026-06-23T10:55:02Z
updated: 2026-06-23T15:19:24Z
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
operating_mode: feature
---

# Task: Wire T3 preview MCP tools into Pi RPC sessions

## Description

Inject T3 Code's provider-scoped preview MCP server into Pi RPC sessions through `pi-mcp-adapter` so Pi can use the in-app browser through preview_* tools.

## Acceptance Criteria

- [x] Pi receives the T3 MCP endpoint and Authorization header from the provider-scoped session credential when a Pi session starts through a scoped `--mcp-config` file and token environment variable.
- [x] Pi can discover or invoke the T3 preview_* tools through `pi-mcp-adapter` using the `mcp` proxy path and direct preview tools when metadata is cached.
- [x] Unavailable desktop preview owners and Pi MCP bridge configuration failures fail with explicit recoverable errors, not fake browser success.
- [x] The implementation does not silently fall back to Pi agent-browser, global Chrome automation, or standalone Playwright for T3 in-app browser work.
- [x] Tests or documented probe evidence cover MCP config injection, token-safe config handling, unavailable preview-owner handling, and the Pi MCP adapter path.

## Traceability

- Story: US-005
- Acceptance criteria: AC-008, AC-009

## Technical Notes

Use the current provider MCP path as the reference:

- `ProviderService.prepareMcpSession` issues a provider-scoped credential before adapter startup.
- `McpProviderSession.readMcpProviderSession(threadId)` exposes the endpoint and Authorization header to adapters.
- Cursor and Grok pass the config as `mcpServers`; OpenCode registers it through `mcp.add`.
- The T3 browser tools are defined in `apps/server/src/mcp/toolkits/preview/tools.ts` and routed through `PreviewAutomationBroker`.
- Pi receives a scoped `pi-mcp-adapter` config through `--mcp-config`; the bearer token is passed as `T3_MCP_BEARER_TOKEN` rather than written to disk.
- Pi receives browser guidance through `--append-system-prompt`, including the `mcp({ search: "preview" })` first-run proxy flow.
- Do not route Pi browser work to Pi `agent-browser`, global Chrome, or standalone Playwright unless the user explicitly asks outside this provider integration.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T15:19:24Z: Corrected MCP implementation to use `pi-mcp-adapter`: Pi startup now receives a scoped `--mcp-config`, `T3_MCP_BEARER_TOKEN` env var, and appended browser instructions. Focused tests verify token-safe config generation, startup args, and no external-browser fallback; live Pi RPC smoke accepted `--mcp-config` and returned `get_state`.
- 2026-06-23T14:02:16Z: Superseded. Initial implementation treated core Pi MCP absence as unsupported before verifying the installed `pi-mcp-adapter` extension.

- 2026-06-23T10:55:02Z: Created from .project/templates/task.md by `delano task add`.
- 2026-06-23: Browser/MCP architecture decision folded into task; blocked until Pi runtime manager exists.
