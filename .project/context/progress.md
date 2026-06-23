# Progress

## What Changed

- Delano runtime and context files are present in the worktree.
- Placeholder context was replaced with T3 Code-specific delivery context.
- `BOOTSTRAP.md` was added as the repeatable setup brief.
- `pi-agent-support` was created as the first active Delano project with workstreams and task decomposition.
- A dedicated `WS-E Browser MCP Integration` workstream and `T-009` task were added after auditing how T3's in-app browser is exposed to providers.

## Why It Changed

The requested active project is Pi Agent support from GitHub issue #402. The issue requires a clean provider integration through T3 Code's existing provider/orchestration model, using the fork PR only as reference.

The browser integration question clarified that "RPC instead of SDK" is not enough by itself. Pi must receive T3's provider-scoped MCP preview tools, or browser support must be documented as unsupported for Pi v1.

## What Is Next

- Start `pi-agent-support/T-001`.
- Compare issue #402, the fork PR, and current provider architecture in detail.
- Confirm whether Pi RPC supports external MCP server registration with Authorization headers.
- Update project notes with exact Pi RPC protocol decisions before starting implementation tasks.

## Remaining Risks

- Pi RPC protocol shape and local availability need live probing.
- The fork reference was written against an older provider architecture and may not map directly to the current per-instance driver model.
- The fork reference does not appear to wire the current T3 preview MCP server into Pi.
- T3 in-app browser support depends on an automation-capable desktop preview owner and Pi support for external MCP servers.
- End-to-end validation requires a machine with a working Pi runtime.
- Full repo validation can surface generated Delano runtime debt that is separate from Pi implementation.
