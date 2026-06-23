# System Patterns

## Provider Driver SPI

Provider implementations are plain `ProviderDriver` values that materialize per-instance `ProviderInstance` records. Each instance owns its snapshot, adapter, text-generation service, identity, and resource scope. New Pi support should follow this shape rather than adding singleton Pi services.

## Open Driver And Instance Routing

`ProviderDriverKind` is an open branded slug. `ProviderInstanceId` is the routing key. Unknown drivers and fork-only configurations must round-trip and degrade as unavailable snapshots rather than crashing settings or persisted sessions.

## Canonical Runtime Events

Provider-native events are mapped into `ProviderRuntimeEvent` before orchestration and UI rendering. Pi intermediate planning/tool events without user-visible assistant text should become work/plan/tool lifecycle events, not empty assistant messages.

## Managed Provider Snapshots

Provider health and model catalogs flow through managed `ServerProvider` snapshots. Availability checks should respect configured binary paths and environment overrides. Dynamic model discovery should be scoped to Pi checks, not paid by Codex-only users.

## T3 Browser MCP Bridge

The in-app browser is a T3-owned collaborative preview surface exposed to providers through provider-scoped MCP `preview_*` tools. Existing providers receive a thread/provider-scoped MCP endpoint and Authorization header from `McpProviderSession`. Pi support must bridge to that surface if Pi RPC supports external MCP servers; it must not silently substitute Pi `agent-browser`, global Chrome automation, or standalone Playwright.

## File-Contract-First Delivery

`.project/projects/<slug>/` is the execution contract. Update spec, plan, workstreams, tasks, decisions, and progress notes when scope or evidence changes. Do not treat chat history or a fork branch as canonical delivery state.

## Conservative Reference Use

The Pi fork PR is reference material for architecture, tests, and failure cases. It is not a merge target and should not override the current repo's provider driver and per-instance architecture.
