---
timestamp: 2026-06-23T15:21:12Z
status: complete
task: 
stream: 
---

# Progress Update

## Completed

- Corrected Pi browser MCP support after verifying the installed `pi-mcp-adapter` extension.
- Pi sessions now receive a scoped `--mcp-config` file for the T3 provider MCP endpoint.
- The T3 MCP bearer token is passed through `T3_MCP_BEARER_TOKEN` instead of being persisted to disk.
- Pi startup now appends browser instructions for direct `preview_*` tools and the first-run `mcp({ search: "preview" })` proxy flow.
- Focused Pi adapter/runtime tests pass.
- A live Pi RPC smoke accepted `--mcp-config` and returned `get_state`.

## In Progress

- None

## Blockers

- None

## Next Actions

- Re-run repo gates and Delano validation after the correction.
