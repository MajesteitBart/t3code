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

## 2026-06-23: Bridge Pi Browser MCP Through pi-mcp-adapter

Decision: Pi v1 will use core Pi RPC for provider startup, dynamic model discovery, model switching, thinking control, prompt/send, abort, and event mapping. T3 preview MCP browser automation is provided through the Pi extension `pi-mcp-adapter` by passing a scoped `--mcp-config` file and a bearer token environment variable.

Rationale: The live probe confirmed `pi --mode rpc` JSONL commands and events are sufficient for provider execution. Core Pi has no MCP support, but `pi-mcp-adapter` 2.10.0 registers the `--mcp-config` flag and can consume HTTP MCP servers with bearer-token environment variables. Adding a Pi-native browser fallback would violate the T3 in-app browser requirement.

## 2026-06-23: Launch Pi Through Shim-Safe Runtime Resolution

Decision: The server runtime should resolve and launch the Pi CLI using the same Windows-safe command resolution pattern as other CLI-backed providers rather than spawning `pi.ps1` directly.

Rationale: The live Node probe could not spawn the PowerShell shim directly, while invoking the package CLI through Node worked. Existing provider runtime helpers already handle platform shims and should be reused.
