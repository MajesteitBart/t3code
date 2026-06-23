---
name: Pi Agent Support
slug: pi-agent-support
owner: T3 Code maintainers
status: active
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:55:11Z
outcome: Pi can be configured, selected, and used as a T3 Code provider when the Pi RPC runtime is available, with dynamic models, provider-native thinking options, T3 in-app browser access through preview MCP tools when supported, correct chat rendering, and safe recovery from startup/send/discovery/browser failures.
uncertainty: high
probe_required: true
probe_status: pending
external_reference: https://github.com/pingdotgg/t3code/issues/402
target_version: v1
operating_mode: feature
---

# Spec: Pi Agent Support

## Executive Summary

Add Pi as a first-class T3 Code provider through the existing provider driver and orchestration model. Use Pi RPC mode. Treat the fork PR linked from issue #402 as reference material for architecture and failure cases, not as code to merge directly.

Browser integration is part of the provider outcome. T3 Code's in-app browser is not a Pi SDK feature and should not be replaced by Pi's native `agent-browser`; it is exposed by T3 as provider-scoped MCP `preview_*` tools that a Pi RPC session must receive when Pi supports external MCP servers.

## Problem and Users

Users want to run Pi Agent from T3 Code with the same provider selection, model selection, turn sending, rendering, and recovery behavior they expect from existing providers. Maintainers need the implementation to fit current provider abstractions so future providers can follow the same path.

## Outcome and Success Metrics

- Pi appears in provider configuration and picker flows only when represented by a configured, usable provider instance.
- Pi runtime availability respects the configured binary path and environment.
- Pi models are discovered dynamically from Pi, not from a fake static fallback.
- Pi thinking/reasoning controls reflect provider-native options only.
- Pi turns execute through the server provider service and render correctly in chat.
- Pi can use the T3 in-app browser through the existing `preview_*` MCP tools when an automation-capable desktop preview owner is connected.
- Empty intermediate Pi tool/planning steps do not create bogus assistant messages.
- Startup, send, discovery, model-switch, browser-tool, and child-process failures clean up without poisoning provider state.
- `vp check`, `vp run typecheck`, and targeted provider/web tests pass.

## User Stories

- US-001: As a T3 Code user with Pi installed, I want to select Pi as a provider, so that I can run a Pi-backed coding turn from the normal chat composer.
- US-002: As a T3 Code user without Pi installed, I want Pi to be unavailable or disabled, so that the app does not imply a provider is usable when it is not.
- US-003: As a T3 Code user, I want Pi model and thinking options to come from Pi, so that I do not select unsupported fake options.
- US-004: As a maintainer, I want Pi to use the existing provider driver and runtime event model, so that provider support remains maintainable.
- US-005: As a T3 Code user, I want Pi to use the same in-app browser preview tools as other agents, so that browser work stays collaborative and visible inside T3 Code.

## Acceptance Scenarios

- AC-001: Given Pi is configured and available, when the user opens the provider picker, then Pi is selectable as a normal provider instance.
- AC-002: Given Pi is missing or the configured binary path is invalid, when provider status refreshes, then Pi is shown disabled/unavailable and other providers remain unaffected.
- AC-003: Given Pi model discovery succeeds, when the model picker opens, then Pi models are sourced from Pi discovery results.
- AC-004: Given Pi model discovery fails, when the model picker opens, then no fake Pi fallback model is offered and the error state is clear.
- AC-005: Given a Pi turn emits intermediate tool or planning events with no assistant text, when the chat timeline renders, then no empty assistant message appears.
- AC-006: Given Pi startup or send fails, when the failure returns, then the failed session or turn state is cleaned up and the user can retry.
- AC-007: Given a Pi model or thinking option is changed, when a turn is sent, then the server passes only provider-native supported options.
- AC-008: Given Pi is running in a thread with an automation-capable T3 desktop preview owner, when Pi calls `preview_status`, `preview_open`, `preview_navigate`, `preview_snapshot`, or focused interaction tools, then the calls route through T3's provider-scoped MCP preview server and return the current in-app browser state.
- AC-009: Given Pi cannot consume external MCP servers or no desktop preview owner is connected, when browser tools are requested, then the limitation is explicit and recoverable, with no silent fallback to Pi `agent-browser`, global Chrome automation, or standalone Playwright.

## Scope

### In Scope

- Pi settings schema and server settings patch support.
- Pi driver registration via `ProviderDriver`.
- Pi RPC process/session manager.
- Pi adapter mapping RPC events to canonical `ProviderRuntimeEvent` values.
- Pi adapter/runtime registration of T3's provider-scoped preview MCP server when Pi supports external MCP tools.
- Provider health, availability, and dynamic model discovery.
- Provider-native Pi thinking options through model capabilities/option descriptors.
- Web provider metadata, provider picker, model picker, and settings integration.
- Tests for contracts, provider snapshots, adapter event mapping, failure cleanup, model discovery, and UI selection logic.

### Out of Scope

- Provider-native server-side slash command parsing.
- Provider UX redesign.
- Fake approval semantics if Pi does not support them cleanly.
- Directly importing or vendoring code from the reference fork.
- Replacing T3's in-app browser with Pi `agent-browser`, standalone Playwright, or a global Chrome/Chrome DevTools path.
- Mobile-specific Pi UI beyond shared runtime correctness unless existing shared UI changes require it.

## Functional Requirements

- FR-001: Define a `pi` provider driver kind and Pi settings schema without closing `ProviderDriverKind`.
- FR-002: Add Pi to built-in driver registration and browser-safe provider metadata.
- FR-003: Start Pi in RPC mode from the configured binary path and environment.
- FR-004: Manage Pi session lifecycle with scoped cleanup for start, send, interrupt, stop, and adapter finalization.
- FR-005: Discover Pi models through provider-scoped/on-demand Pi RPC behavior.
- FR-006: Expose only real Pi thinking/reasoning levels.
- FR-007: Validate custom Pi model slugs before persistence or use.
- FR-008: Map Pi RPC events into canonical runtime events without duplicate turn lifecycle events.
- FR-009: Suppress or remap empty intermediate events so they do not render as assistant text.
- FR-010: Preserve existing providers and provider-instance routing behavior.
- FR-011: Pass the provider-scoped T3 MCP endpoint and Authorization header into Pi RPC sessions when Pi supports external MCP server registration.
- FR-012: Keep browser automation on the existing `preview_*` MCP tool surface and desktop preview owner, including current unavailable-owner and timeout errors.
- FR-013: If Pi RPC lacks external MCP support, surface that as an explicit unsupported capability rather than pretending the in-app browser works.

## Non-Functional Requirements

- Reliability first for session lifecycle and failure cleanup.
- No Pi-specific frontend flow when existing provider/model picker patterns can represent the behavior.
- Codex-only and non-Pi users should not pay unnecessary Pi discovery costs or see noisy warnings.
- Tests must cover failure paths, not only happy path startup.
- Implementation must preserve `.repos/` as read-only reference material.

## Assumptions

- Pi provides a stable local RPC mode suitable for child-process management.
- Pi model discovery and thinking options can be queried without starting a long-running user turn.
- Current `ProviderDriver` and per-instance registry abstractions are sufficient for Pi.
- The fork PR still contains useful failure-case notes, but current main has moved beyond its exact architecture.
- T3's browser integration is MCP-dependent, not SDK-dependent; using Pi RPC remains compatible if Pi can consume a remote MCP server.

## Needs Clarification

- What exact Pi command and flags start RPC mode in the current Pi runtime?
- What RPC messages expose models, thinking levels, prompt/send, interrupt, and session state?
- Which Pi events are user-visible assistant text versus intermediate planning/tool events?
- Should the default Pi provider instance be enabled by default or opt-in like early access providers?
- Does Pi support any approval or permission semantics that should be exposed in v1?
- How does current Pi RPC accept external MCP servers: command payload, startup flag, config file, environment variable, or not at all?
- Can Pi receive provider/developer instructions that tell it to prefer T3 `preview_*` tools over external browser automation?

## Hypotheses and Unknowns

- Hypothesis: Pi can fit as a normal `ProviderDriver` with a Pi-specific runtime manager and adapter, similar in shape to ACP-based providers.
- Unknown: whether Pi has a native model-switch command that works in-session.
- Unknown: how Pi reports failures, final answers, and multi-stage runs over RPC.
- Unknown: whether Pi exposes thinking levels as model capabilities, runtime settings, or prompt options.
- Unknown: whether Pi RPC supports remote MCP server registration with custom Authorization headers.

## Touchpoints to Exercise

- `packages/contracts/src/settings.ts`
- `packages/contracts/src/model.ts`
- `packages/contracts/src/providerRuntime.ts`
- `apps/server/src/provider/ProviderDriver.ts`
- `apps/server/src/provider/builtInDrivers.ts`
- `apps/server/src/mcp/McpSessionRegistry.ts`
- `apps/server/src/mcp/McpProviderSession.ts`
- `apps/server/src/mcp/toolkits/preview/tools.ts`
- `apps/web/src/components/preview/PreviewAutomationOwner.tsx`
- `apps/server/src/provider/Layers/*Adapter.ts`
- `apps/server/src/provider/Layers/*Provider.ts`
- `apps/web/src/components/settings/providerDriverMeta.ts`
- `apps/web/src/session-logic.ts`
- `apps/web/src/components/chat/ProviderModelPicker.tsx`
- `apps/web/src/components/chat/ModelPickerContent.tsx`
- `apps/web/src/components/chat/MessagesTimeline.logic.ts`

## Probe Findings

- 2026-06-23 browser/MCP audit: the current T3 in-app browser is exposed as T3-owned MCP `preview_*` tools backed by `PreviewAutomationBroker`, `McpSessionRegistry`, and an automation-capable desktop preview owner.
- 2026-06-23 browser/MCP audit: existing providers that can consume MCP receive the provider-scoped T3 MCP endpoint and Authorization header from `McpProviderSession`; Cursor/Grok pass it as `mcpServers`, and OpenCode registers it with `mcp.add`.
- 2026-06-23 browser/MCP audit: the reference Pi PR uses RPC and maps Pi tool events, but no evidence was found that it injects the current T3 MCP preview server into Pi.
- `T-001` still must confirm the live Pi RPC protocol, especially whether remote MCP server registration is supported and what wire shape it requires.

## Footguns Discovered

- The installed Delano `init.sh` template executed backticked Markdown inside an unquoted heredoc; this was repaired locally during bootstrap.
- Generated validation helpers initially assumed root `scripts/` copies of Delano helpers; those paths were repaired to use `.agents/scripts`.
- `.repos/` contains vendored source with bidi-control test data; text-safety validation should not scan read-only vendored references.
- "RPC instead of SDK" is not sufficient by itself for in-app browser support; Pi must receive or bridge the T3 MCP preview tools.
- Pi `agent-browser` and other external browser automation tools are not equivalent to T3's collaborative in-app browser surface.

## Remaining Unknowns

- Exact Pi RPC protocol.
- Exact Pi thinking-level option names.
- Real Pi runtime availability on this machine during implementation.
- Whether the fork PR tests still apply cleanly to current provider-instance architecture.
- Whether Pi RPC can attach the T3 MCP preview server and authorization header without SDK-only APIs.

## Dependencies

- Local Pi runtime for final E2E verification.
- Existing provider driver registry and server provider snapshot infrastructure.
- Existing T3 MCP preview automation infrastructure and desktop preview owner connection.
- Existing web provider settings/model picker flows.
- Issue #402 and the fork PR as reference inputs.

## Approval Notes

Bootstrap created the active contract. Implementation tasks after `T-001` remain blocked until the probe updates this project with concrete Pi RPC decisions.
