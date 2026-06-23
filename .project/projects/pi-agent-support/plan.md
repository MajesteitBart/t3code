---
name: Pi Agent Support
status: active
lead: Bart
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:55:11Z
linear_project_id:
risk_level: high
spec_status_at_plan_time: active
target_version: v1
operating_mode: feature
---

# Delivery Plan: Pi Agent Support

## What Changed After Probe

No live Pi probe has run yet. A lightweight browser/MCP audit found that T3's in-app browser is exposed through T3-owned MCP `preview_*` tools, not through provider SDK APIs. `T-001` still owns the full Pi RPC probe and must confirm whether Pi can consume an external MCP server over RPC.

## Technical Context

The current repo has a per-instance provider driver SPI rather than a closed provider-kind union. `ProviderDriverKind` is an open branded slug, and `ProviderInstanceId` is the routing key. Built-in providers register plain `ProviderDriver` values in `apps/server/src/provider/builtInDrivers.ts`. Each driver creates a scoped provider instance with snapshot, adapter, and text-generation closures.

Pi should therefore be added as a new driver and adapter, not as a special route in the server or a Pi-only UI flow.

The in-app browser path is already provider-scoped and MCP-backed. `ProviderService` issues an MCP credential for a thread/provider instance, `McpSessionRegistry` grants the `preview` capability, and `McpProviderSession` makes the endpoint and bearer header available to provider adapters. Existing providers that support MCP pass that config into their runtime rather than implementing browser automation themselves.

## Architecture Decisions

- Use driver kind `pi` unless `T-001` finds a concrete incompatibility.
- Add `PiSettings` to contracts with at least enabled state, binary path, and any runtime-specific config needed for RPC startup.
- Register `PiDriver` in `BUILT_IN_DRIVERS`.
- Keep Pi process/session management scoped to the Pi driver/adapter/runtime files.
- Map Pi RPC events into existing `ProviderRuntimeEvent` types.
- Represent Pi thinking levels through model capabilities/option descriptors.
- Keep T3 browser automation as the T3 MCP `preview_*` surface. Pi should receive that provider-scoped MCP server when Pi RPC supports external MCP server registration.
- Do not use Pi `agent-browser`, global Chrome automation, standalone Playwright, or provider-specific browser paths as a silent substitute for T3's in-app browser.
- If Pi RPC cannot attach a remote MCP server, surface browser automation as unsupported for Pi v1 and keep the rest of the provider usable.
- Add Pi browser tool guidance only through the provider/runtime instruction path supported by Pi RPC; do not fork T3's browser UI for Pi.
- Do not create static fake Pi fallback models.
- Scope model discovery to Pi provider checks or Pi-selected flows.
- Fail unavailable or misconfigured Pi instances as provider snapshots, not as global server failures.

## Policy and Contract Checks

- [x] `.project` remains the execution source of truth
- [x] Probe decision is explicit
- [x] Evidence gates are defined before handoff
- [x] External sync writes require dry-run or operator approval

## Generated Artifact Map

- `spec.md`: derived from issue #402, fork PR summary, AGENTS.md, and live provider architecture inspection.
- `plan.md`: derived from current provider driver/per-instance architecture.
- `workstreams/`: split by probe/contracts, server runtime, web UX, browser MCP integration, and verification/docs.
- `tasks/`: decomposed so only the initial probe task is ready.

## Complexity Exceptions

- Risk level is high because provider runtime, process lifecycle, event mapping, and UI model selection all change together.
- Final acceptance depends on a working Pi runtime for live verification.

## Probe-Driven Architecture Changes

- 2026-06-23 browser/MCP audit added a dedicated browser workstream. The implementation must bridge Pi RPC to T3's existing MCP preview server if Pi supports remote MCP, instead of treating the browser as Pi SDK functionality.
- Pending `T-001` for live Pi RPC command shape and external MCP registration proof.

## Workstream Design

- `WS-A`: Probe and contract alignment.
- `WS-B`: Server runtime and adapter.
- `WS-C`: Web settings and provider UX.
- `WS-E`: Browser MCP integration.
- `WS-D`: Verification, docs, and closeout.

## Milestone Strategy

1. Probe current Pi RPC and reference implementation against current T3 provider architecture.
2. Add contracts/settings/provider metadata.
3. Add Pi server driver, provider snapshot, model discovery, and adapter runtime.
4. Wire T3 preview MCP tools into Pi sessions if Pi RPC supports external MCP servers.
5. Wire web settings, provider picker, model picker, and thinking options.
6. Harden failure paths and verify with tests plus a live Pi/browser smoke.

## Rollout Strategy

Ship behind normal provider availability: Pi is selectable only when configured and available. Missing Pi should remain a local provider status, not a global warning or startup failure.

Browser support should roll out as capability-gated behavior. A Pi provider can be usable without in-app browser access if Pi RPC cannot consume T3's MCP server, but the limitation must be explicit in tests, docs, and user-visible failure paths.

## Test Strategy

- Contract tests for Pi settings decode/patch behavior and model option descriptors.
- Provider snapshot tests for installed, missing, disabled, discovery failure, and dynamic model cases.
- Adapter tests for startup, send, interrupt, stop, failed spawn, failed prompt, empty intermediate events, duplicate lifecycle prevention, and cleanup.
- Browser/MCP tests or probe evidence for provider-scoped MCP config injection, unavailable desktop preview owner handling, and unsupported Pi external-MCP behavior.
- Web logic/component tests for provider metadata, picker visibility, no fake model fallback, and option selection.
- Full gates: `vp check` and `vp run typecheck`.
- Browser verification if provider picker/model picker/chat rendering changes.

## Rollback Strategy

If Pi implementation regresses provider startup or UI selection, remove `PiDriver` from `BUILT_IN_DRIVERS` and keep settings payloads round-trippable through open provider instance envelopes. Unknown `pi` instances should degrade to unavailable snapshots.

## Remaining Delivery Risks

- Pi RPC may not expose exactly the model/thinking/session controls expected by issue #402.
- The reference fork may be stale against the current provider-instance architecture.
- Dynamic discovery timeouts need careful tuning to avoid slowing non-Pi users.
- Empty intermediate event filtering must avoid hiding real assistant output.
- Pi may expose external MCP server registration only through SDK APIs or not at all; if so, in-app browser support must be deferred or explicitly unsupported for RPC v1.
- Browser automation depends on an automation-capable desktop preview owner for the scoped thread, so hosted/mobile-only contexts need clear unavailable behavior.
