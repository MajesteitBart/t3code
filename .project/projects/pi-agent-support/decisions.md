# Decisions

## 2026-06-23: Use Current Provider Driver SPI

Decision: Pi support will target the current `ProviderDriver` and per-instance registry model.

Rationale: The live contracts already accept open `ProviderDriverKind` slugs and route by `ProviderInstanceId`. A closed-union widening task from older notes is no longer the main architectural step.

## 2026-06-23: Treat Fork PR As Reference Only

Decision: The fork PR linked from issue #402 is reference material for scope, tests, and failure cases, not a branch to merge directly.

Rationale: The issue explicitly warns not to treat the PR as a direct merge target, and the current repository has continued evolving.

## 2026-06-23: Probe Before Implementation

Decision: Only `T-001` is ready after bootstrap; implementation tasks remain blocked.

Rationale: Pi RPC command shape, event semantics, model discovery, and thinking-level mapping must be confirmed before code changes.

## 2026-06-23: Browser Integration Uses T3 MCP Preview Tools

Decision: Pi browser support will target T3 Code's existing provider-scoped MCP `preview_*` tools. The Pi RPC integration must inject or register the T3 MCP endpoint and Authorization header if Pi supports external MCP servers.

Rationale: The in-app browser is a T3-owned collaborative preview surface backed by `McpSessionRegistry`, `McpProviderSession`, and `PreviewAutomationBroker`. The reference Pi PR uses RPC and maps Pi tool events, but it does not appear to wire the current T3 preview MCP server into Pi. RPC remains the right provider execution direction only if the browser capability is bridged explicitly.

## 2026-06-23: Do Not Substitute External Browser Automation

Decision: Pi `agent-browser`, global Chrome/Chrome DevTools automation, standalone Playwright, and provider-specific browser paths are not acceptable silent substitutes for T3's in-app browser integration.

Rationale: The user asked about the T3 Code in-app browser. Replacing it with a Pi-native or global browser would lose the collaborative, thread-scoped preview semantics and would make browser behavior diverge from existing provider guidance.
