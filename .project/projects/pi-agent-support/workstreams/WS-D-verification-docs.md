---
id: WS-D
name: Verification And Docs
owner: T3 Code maintainers
status: active
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:55:11Z
operating_mode: feature
---

# Workstream: Verification And Docs

## Objective

Prove Pi provider readiness with automated gates, targeted tests, live smoke evidence where possible, and concise documentation.

## Owned Files/Areas

- `docs/providers/`
- `docs/architecture/providers.md`
- provider/server/web tests touched by implementation
- `.project/projects/pi-agent-support/updates/`

## Dependencies

- `WS-B`, `WS-C`, and `WS-E` implementation tasks.
- A local Pi runtime for final live verification.

## Risks

- Final E2E may be blocked if Pi is unavailable locally.
- Browser behavior must be checked after provider/model picker changes.
- In-app browser behavior must not be claimed unless Pi can access T3 `preview_*` MCP tools or the unsupported state is explicitly documented.

## Handoff Criteria

- `vp check` and `vp run typecheck` pass or documented blockers are explicit.
- Targeted tests pass.
- UI/live verification evidence is recorded.
- Provider docs are updated for Pi setup and limitations.
- T3 in-app browser support or limitation is documented separately from Pi-native `agent-browser`.
