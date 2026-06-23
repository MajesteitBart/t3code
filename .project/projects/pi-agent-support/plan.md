---
name: Pi Agent Support
status: done
lead: Bart
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T21:11:20Z
linear_project_id: 
risk_level: high
spec_status_at_plan_time: active
target_version: v1
operating_mode: feature
---

# Delivery Plan: Pi Agent Support

## What Changed After Probe

`T-001` completed the live Pi probe against local Pi 0.79.10. Pi RPC is viable for session startup, dynamic model discovery, model switching, thinking control, prompt/send, abort, and event streaming over strict JSONL. Core Pi does not provide remote MCP registration, but `pi-mcp-adapter` 2.10.0 accepts `--mcp-config`, so T3 preview MCP browser support is bridged through that extension rather than replaced with Pi `agent-browser` or global browser automation.

## What Changed After Composer Review

The Pi integration reached provider/model/browser partial operation, but composer affordances still need dedicated work. The review found three separate layers: web slash trigger drift from the shared composer trigger contract, empty Pi `skills` and `slashCommands` provider metadata, and a Pi adapter attachment rejection that can affect file/document mention sends. These are tracked in `WS-F` before implementation continues.

## Technical Context

The current repo has a per-instance provider driver SPI rather than a closed provider-kind union. `ProviderDriverKind` is an open branded slug, and `ProviderInstanceId` is the routing key. Built-in providers register plain `ProviderDriver` values in `apps/server/src/provider/builtInDrivers.ts`. Each driver creates a scoped provider instance with snapshot, adapter, and text-generation closures.

Pi should therefore be added as a new driver and adapter, not as a special route in the server or a Pi-only UI flow.

The in-app browser path is already provider-scoped and MCP-backed. `ProviderService` issues an MCP credential for a thread/provider instance, `McpSessionRegistry` grants the `preview` capability, and `McpProviderSession` makes the endpoint and bearer header available to provider adapters. Existing providers that support MCP pass that config into their runtime rather than implementing browser automation themselves. Pi v1 passes the same endpoint through a scoped `pi-mcp-adapter` config file and provides the bearer token through the Pi process environment.

## Architecture Decisions

- Use driver kind `pi` unless `T-001` finds a concrete incompatibility.
- Add `PiSettings` to contracts with at least enabled state, binary path, and any runtime-specific config needed for RPC startup.
- Register `PiDriver` in `BUILT_IN_DRIVERS`.
- Keep Pi process/session management scoped to the Pi driver/adapter/runtime files.
- Map Pi RPC events into existing `ProviderRuntimeEvent` types.
- Represent Pi thinking levels through model capabilities/option descriptors.
- Keep T3 browser automation as the T3 MCP `preview_*` surface. Pi consumes that provider-scoped MCP server through `pi-mcp-adapter` and the Pi `mcp` proxy/direct-tool path.
- Do not use Pi `agent-browser`, global Chrome automation, standalone Playwright, or provider-specific browser paths as a silent substitute for T3's in-app browser.
- Surface MCP bridge failures as recoverable runtime warnings and keep the rest of the provider usable.
- Add Pi browser tool guidance through `--append-system-prompt`; do not fork T3's browser UI for Pi.
- Do not create static fake Pi fallback models.
- Scope model discovery to Pi provider checks or Pi-selected flows.
- Fail unavailable or misconfigured Pi instances as provider snapshots, not as global server failures.
- Keep slash command behavior in shared composer trigger semantics, not Pi-specific prompt handling.
- Populate Pi skill/provider command metadata only from executable Pi/RPC/extension capabilities, or make unsupported states explicit in the UI.
- Decide file/document mention compatibility before dispatch so Pi does not silently drop context or reject the turn after prompt reset.

## Policy and Contract Checks

- [x] `.project` remains the execution source of truth
- [x] Probe decision is explicit
- [x] Evidence gates are defined before handoff
- [x] External sync writes require dry-run or operator approval

## Generated Artifact Map

- `spec.md`: derived from issue #402, fork PR summary, AGENTS.md, and live provider architecture inspection.
- `plan.md`: derived from current provider driver/per-instance architecture.
- `workstreams/`: split by probe/contracts, server runtime, web UX, browser MCP integration, and verification/docs.
- `tasks/`: decomposed by provider/runtime/web/browser work plus reopened composer-affordance parity tasks `T-010` through `T-014`.

## Complexity Exceptions

- Risk level is high because provider runtime, process lifecycle, event mapping, and UI model selection all change together.
- Final acceptance depends on a working Pi runtime for live verification.

## Probe-Driven Architecture Changes

- 2026-06-23 browser/MCP audit added a dedicated browser workstream. The implementation must keep browser automation on T3's existing MCP preview surface and avoid provider-specific fallbacks.
- 2026-06-23 live Pi probe confirmed Pi RPC JSONL command/event behavior and confirmed `pi-mcp-adapter` can carry T3 preview MCP config through `--mcp-config`.
- 2026-06-23 composer affordance review added `WS-F` for slash command/model query parity, Pi skill/provider command metadata, file/document mentions, and final composer smoke validation.

## Workstream Design

- `WS-A`: Probe and contract alignment.
- `WS-B`: Server runtime and adapter.
- `WS-C`: Web settings and provider UX.
- `WS-E`: Browser MCP integration.
- `WS-D`: Verification, docs, and closeout.
- `WS-F`: Composer affordance parity for Pi-selected slash commands, skills, and file/document mentions.

## Milestone Strategy

1. Probe current Pi RPC and reference implementation against current T3 provider architecture.
2. Add contracts/settings/provider metadata.
3. Add Pi server driver, provider snapshot, model discovery, and adapter runtime.
4. Add Pi browser capability handling that writes a scoped `pi-mcp-adapter` config for T3 preview MCP.
5. Wire web settings, provider picker, model picker, and thinking options.
6. Restore Pi composer affordance parity for slash commands, skills, and file/document mentions.
7. Harden failure paths and verify with tests plus a live Pi/browser/composer smoke.

## Rollout Strategy

Ship behind normal provider availability: Pi is selectable only when configured and available. Missing Pi should remain a local provider status, not a global warning or startup failure.

Browser support rolls out as capability-gated behavior. The Pi provider uses `pi-mcp-adapter` when a T3 provider-scoped MCP credential exists, and bridge/preview-owner failures must be explicit in tests, docs, and user-visible failure paths.

## Test Strategy

- Contract tests for Pi settings decode/patch behavior and model option descriptors.
- Provider snapshot tests for installed, missing, disabled, discovery failure, and dynamic model cases.
- Adapter tests for startup, send, interrupt, stop, failed spawn, failed prompt, empty intermediate events, duplicate lifecycle prevention, and cleanup.
- Browser/MCP tests or probe evidence for Pi MCP config injection, token-safe config handling, adapter startup, and no silent fallback to external browser automation.
- Web logic/component tests for provider metadata, picker visibility, no fake model fallback, option selection, slash command/model query trigger behavior, provider skill search, and file/document mention gating or conversion.
- Provider snapshot/status-cache tests for Pi `skills` and `slashCommands` metadata.
- Pi adapter/runtime tests for file/document mention payload handling or explicit rejection before prompt loss.
- Full gates: `vp check` and `vp run typecheck`.
- Browser verification if provider picker/model picker/chat rendering/composer affordance behavior changes.

## Rollback Strategy

If Pi implementation regresses provider startup or UI selection, remove `PiDriver` from `BUILT_IN_DRIVERS` and keep settings payloads round-trippable through open provider instance envelopes. Unknown `pi` instances should degrade to unavailable snapshots.

## Remaining Delivery Risks

- Pi RPC controls were confirmed locally, but runtime behavior may vary by installed Pi version.
- The reference fork may be stale against the current provider-instance architecture.
- Dynamic discovery timeouts need careful tuning to avoid slowing non-Pi users.
- Empty intermediate event filtering must avoid hiding real assistant output.
- Future Pi versions may add core external MCP registration; v1 should keep the extension-backed bridge localized so that capability can be swapped without changing the browser surface.
- Browser automation depends on an automation-capable desktop preview owner for the scoped thread, so hosted/mobile-only contexts need clear unavailable behavior.
- Pi may not expose repo skills or provider slash commands through the same surfaces as Codex/Claude; this must be discovered before fabricating UI entries.
- File/document mentions may use T3 attachment payloads today, but Pi currently rejects attachments in `sendTurn`; unsupported behavior must be gated before prompt reset.
