---
id: WS-E
name: WS-E Browser MCP Integration
owner: T3 Code maintainers
status: active
created: 2026-06-23T10:54:54Z
updated: 2026-06-23T10:55:11Z
operating_mode: feature
---

# Workstream: WS-E Browser MCP Integration

## Objective

Make T3 Code's collaborative in-app browser available to Pi through the existing provider-scoped MCP `preview_*` tool surface when Pi RPC supports external MCP servers.

## Owned Files/Areas

- `apps/server/src/provider/Layers/PiAdapter.ts`
- `apps/server/src/piRpcManager.ts` or the eventual Pi RPC runtime manager file
- `apps/server/src/mcp/McpProviderSession.ts` usage points
- `apps/server/src/mcp/McpSessionRegistry.ts` usage points
- `apps/server/src/mcp/toolkits/preview/tools.ts` as the tool contract reference
- `apps/server/src/provider/CodexDeveloperInstructions.ts` as the browser-tool instruction reference, not as a Pi-specific import target
- Pi provider tests covering MCP injection and unavailable preview-owner behavior

## Dependencies

- `WS-A` must confirm Pi RPC command shape and whether external MCP server registration is available.
- `WS-B` must provide a Pi runtime/session manager that can accept per-session MCP configuration.
- An automation-capable T3 desktop preview owner must be available for live browser verification.

## Risks

- Pi RPC may not support remote MCP server registration with custom Authorization headers.
- The reference PR maps Pi tool lifecycle events but does not appear to inject T3's current MCP preview server.
- Browser success can be faked accidentally by falling back to Pi `agent-browser` or global browser automation; this workstream must prevent that.
- Hosted/mobile contexts may not have an automation-capable desktop preview owner, so unavailable behavior must be explicit and recoverable.

## Handoff Criteria

- Pi runtime/session startup either registers the T3 MCP endpoint and Authorization header or records a documented unsupported capability.
- Pi browser-tool instructions, if supported by Pi RPC, direct Pi toward `preview_status`, `preview_open`, `preview_navigate`, `preview_snapshot`, and focused interaction tools.
- Tests or documented probe evidence cover MCP config injection, no-owner browser failure, and no silent external-browser fallback.
- Verification docs explain that T3 in-app browser support depends on an automation-capable desktop preview owner.
