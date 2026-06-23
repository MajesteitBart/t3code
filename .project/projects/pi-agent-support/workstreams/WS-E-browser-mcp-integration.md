---
id: WS-E
name: WS-E Browser MCP Integration
owner: T3 Code maintainers
status: done
created: 2026-06-23T10:54:54Z
updated: 2026-06-23T15:19:24Z
operating_mode: feature
---

# Workstream: WS-E Browser MCP Integration

## Objective

Make T3 Code's collaborative in-app browser available to Pi through the existing provider-scoped MCP `preview_*` tool surface using the Pi `pi-mcp-adapter` extension.

## Owned Files/Areas

- `apps/server/src/provider/Layers/PiAdapter.ts`
- `apps/server/src/piRpcManager.ts` or the eventual Pi RPC runtime manager file
- `apps/server/src/mcp/McpProviderSession.ts` usage points
- `apps/server/src/mcp/McpSessionRegistry.ts` usage points
- `apps/server/src/mcp/toolkits/preview/tools.ts` as the tool contract reference
- `apps/server/src/provider/CodexDeveloperInstructions.ts` as the browser-tool instruction reference, not as a Pi-specific import target
- Pi provider tests covering MCP injection and unavailable preview-owner behavior

## Dependencies

- `WS-A` must confirm Pi RPC command shape and the extension-backed MCP bridge path.
- `WS-B` must provide a Pi runtime/session manager that can accept per-session MCP configuration.
- An automation-capable T3 desktop preview owner must be available for live browser verification.

## Risks

- Core Pi may not support remote MCP server registration with custom Authorization headers, so the bridge must remain localized to `pi-mcp-adapter`.
- The reference PR maps Pi tool lifecycle events but does not appear to inject T3's current MCP preview server.
- Browser success can be faked accidentally by falling back to Pi `agent-browser` or global browser automation; this workstream must prevent that.
- Hosted/mobile contexts may not have an automation-capable desktop preview owner, so unavailable behavior must be explicit and recoverable.

## Handoff Criteria

- Pi runtime/session startup writes a scoped MCP config for the T3 endpoint and passes the bearer token through the Pi process environment.
- Pi browser-tool instructions direct Pi toward `preview_status`, `preview_open`, `preview_navigate`, `preview_snapshot`, and the Pi MCP proxy first-run flow.
- Tests or documented probe evidence cover MCP config injection, token-safe config handling, no-owner browser failure, and no silent external-browser fallback.
- Verification docs explain that T3 in-app browser support depends on an automation-capable desktop preview owner.
