---
name: Pi Agent Support
slug: pi-agent-support
owner: T3 Code maintainers
status: complete
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T21:11:20Z
outcome: Pi can be configured, selected, and used as a T3 Code provider when the Pi RPC runtime is available, with dynamic models, provider-native thinking options, T3 in-app browser access through preview MCP tools when supported, shared composer affordances for slash commands, repo skills, and file/document mentions, correct chat rendering, and safe recovery from startup/send/discovery/browser/composer failures.
uncertainty: high
probe_required: true
probe_status: completed
external_reference: https://github.com/pingdotgg/t3code/issues/402
target_version: v1
operating_mode: feature
---

# Spec: Pi Agent Support

## Executive Summary

Add Pi as a first-class T3 Code provider through the existing provider driver and orchestration model. Use Pi RPC mode. Treat the fork PR linked from issue #402 as reference material for architecture and failure cases, not as code to merge directly.

Browser integration is part of the provider outcome. T3 Code's in-app browser is not a Pi SDK feature and should not be replaced by Pi's native `agent-browser`; it is exposed by T3 as provider-scoped MCP `preview_*` tools. Core Pi 0.79.10 does not provide remote MCP registration itself, but the installed `pi-mcp-adapter` extension accepts `--mcp-config`, so Pi v1 must bridge T3's preview MCP server through that extension.

Composer affordance parity is also part of the provider outcome. Pi must either support T3's shared slash command, repo skill, and file/document mention flows end-to-end or make unsupported affordances explicit before a user sends a turn.

## Problem and Users

Users want to run Pi Agent from T3 Code with the same provider selection, model selection, turn sending, rendering, and recovery behavior they expect from existing providers. Maintainers need the implementation to fit current provider abstractions so future providers can follow the same path.

## Outcome and Success Metrics

- Pi appears in provider configuration and picker flows only when represented by a configured, usable provider instance.
- Pi runtime availability respects the configured binary path and environment.
- Pi models are discovered dynamically from Pi, not from a fake static fallback.
- Pi thinking/reasoning controls reflect provider-native options only.
- Pi turns execute through the server provider service and render correctly in chat.
- Pi in-app browser availability is explicit: Pi receives T3's provider-scoped preview MCP server through `pi-mcp-adapter`; if that bridge or the desktop preview owner is unavailable, v1 must not silently substitute external browser automation.
- Pi-selected composer slash behavior matches the shared composer trigger contract, including direct `/model` and `/model query` flows.
- Pi provider status supplies executable skills and provider slash commands to the composer when Pi exposes them, or clearly gates those affordances when unsupported.
- File and document mentions either reach Pi in a compatible context form or are blocked/degraded with explicit feedback before send.
- Empty intermediate Pi tool/planning steps do not create bogus assistant messages.
- Startup, send, discovery, model-switch, browser-tool, composer-affordance, and child-process failures clean up without poisoning provider state.
- `vp check`, `vp run typecheck`, and targeted provider/web tests pass.

## User Stories

- US-001: As a T3 Code user with Pi installed, I want to select Pi as a provider, so that I can run a Pi-backed coding turn from the normal chat composer.
- US-002: As a T3 Code user without Pi installed, I want Pi to be unavailable or disabled, so that the app does not imply a provider is usable when it is not.
- US-003: As a T3 Code user, I want Pi model and thinking options to come from Pi, so that I do not select unsupported fake options.
- US-004: As a maintainer, I want Pi to use the existing provider driver and runtime event model, so that provider support remains maintainable.
- US-005: As a T3 Code user, I want Pi to use the same in-app browser preview tools as other agents, so that browser work stays collaborative and visible inside T3 Code.
- US-006: As a T3 Code user, I want Pi-selected slash commands to behave like other providers, so that `/model`, `/plan`, and `/default` do not become accidental prompts.
- US-007: As a T3 Code user, I want Pi to show usable repo skills and provider slash commands when available, so that the composer does not imply "no skills" incorrectly.
- US-008: As a T3 Code user, I want file and document mentions to work with Pi or fail before send with a clear reason, so that context is not silently dropped or rejected.
- US-009: As a maintainer, I want a focused Pi composer parity smoke, so that slash commands, skills, and mentions are verified together before closeout.

## Acceptance Scenarios

- AC-001: Given Pi is configured and available, when the user opens the provider picker, then Pi is selectable as a normal provider instance.
- AC-002: Given Pi is missing or the configured binary path is invalid, when provider status refreshes, then Pi is shown disabled/unavailable and other providers remain unaffected.
- AC-003: Given Pi model discovery succeeds, when the model picker opens, then Pi models are sourced from Pi discovery results.
- AC-004: Given Pi model discovery fails, when the model picker opens, then no fake Pi fallback model is offered and the error state is clear.
- AC-005: Given a Pi turn emits intermediate tool or planning events with no assistant text, when the chat timeline renders, then no empty assistant message appears.
- AC-006: Given Pi startup or send fails, when the failure returns, then the failed session or turn state is cleaned up and the user can retry.
- AC-007: Given a Pi model or thinking option is changed, when a turn is sent, then the server passes only provider-native supported options.
- AC-008: Given Pi is running in a thread with an automation-capable T3 desktop preview owner, when Pi calls `preview_status`, `preview_open`, `preview_navigate`, `preview_snapshot`, or focused interaction tools, then the calls route through T3's provider-scoped MCP preview server and return the current in-app browser state.
- AC-009: Given the Pi MCP adapter bridge cannot be configured or no desktop preview owner is connected, when browser tools are requested, then the limitation is explicit and recoverable, with no silent fallback to Pi `agent-browser`, global Chrome automation, or standalone Playwright.
- AC-010: Given Pi is the selected provider, when the user types or selects built-in slash commands including `/model`, `/model query`, `/plan`, and `/default`, then the composer runs the local T3 command flow instead of sending those commands as normal Pi prompts.
- AC-011: Given Pi exposes repo skills, when the user types `$` or searches skills in the composer, then Pi skills are discoverable and inserted consistently with existing provider skill chips.
- AC-012: Given Pi exposes executable provider slash commands, when the user opens the `/` menu, then only Pi-executable provider commands appear and non-executable commands are hidden or marked unavailable.
- AC-013: Given the user mentions a file or document with Pi selected, when the turn is sent, then Pi receives the context in a compatible form or the UI/runtime blocks/degrades the send with an explicit reason before losing the prompt.

## Scope

### In Scope

- Pi settings schema and server settings patch support.
- Pi driver registration via `ProviderDriver`.
- Pi RPC process/session manager.
- Pi adapter mapping RPC events to canonical `ProviderRuntimeEvent` values.
- Explicit Pi browser capability handling: bridge T3 preview MCP through `pi-mcp-adapter` and keep bridge failures recoverable.
- Provider health, availability, and dynamic model discovery.
- Provider-native Pi thinking options through model capabilities/option descriptors.
- Web provider metadata, provider picker, model picker, and settings integration.
- Shared composer slash command, skill, and file/document mention affordances for Pi-selected threads.
- Pi provider skill and provider slash-command status metadata, including explicit unsupported states where Pi cannot execute the affordance.
- Tests for contracts, provider snapshots, adapter event mapping, failure cleanup, model discovery, and UI selection logic.

### Out of Scope

- Provider-native server-side slash command parsing unrelated to T3's shared composer/menu affordances.
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
- FR-011: Pass T3's provider-scoped preview MCP server to Pi through a scoped `pi-mcp-adapter` config file and environment-provided bearer token; do not persist the bearer token to disk.
- FR-012: Keep browser automation on the existing `preview_*` MCP tool surface and desktop preview owner, including current unavailable-owner and timeout errors.
- FR-013: If the Pi MCP adapter bridge cannot be configured, surface that as an explicit recoverable capability failure rather than pretending the in-app browser works.
- FR-014: Keep Pi-selected slash command behavior aligned with the shared composer trigger semantics used across web/mobile composer surfaces.
- FR-015: Populate Pi provider snapshots with skill and provider slash-command metadata when Pi can provide it, without fabricating commands that cannot execute.
- FR-016: Preserve existing provider behavior when adding Pi composer affordance support.
- FR-017: Convert, support, or explicitly gate Pi file/document mention payloads before `sendTurn` rejects or loses user context.

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
- Pi may not support every T3 composer affordance natively; explicit gating is acceptable only when the UI prevents silent context loss.

## Needs Clarification

- Should the default Pi provider instance be enabled by default or opt-in like early access providers?
- Does Pi support any approval or permission semantics that should be exposed in v1?
- Can Pi receive provider/developer instructions that tell it to prefer T3 `preview_*` tools over external browser automation?

## Hypotheses and Unknowns

- Hypothesis: Pi can fit as a normal `ProviderDriver` with a Pi-specific runtime manager and adapter, similar in shape to ACP-based providers.
- Confirmed: Pi starts RPC mode with `pi --mode rpc` or the package CLI path through Node on Windows.
- Confirmed: Pi RPC exposes `get_state`, `get_available_models`, `set_model`, `set_thinking_level`, `prompt`, `abort`, and related session commands over JSONL stdin/stdout.
- Confirmed: Pi streams assistant text through `message_update` events with `text_delta`; thinking and tool events are separate and must not create empty assistant text.
- Confirmed: Pi supports in-session model switching and thinking-level commands.
- Confirmed: the installed Pi 0.79.10 package documents no core MCP support, but `pi-mcp-adapter` 2.10.0 provides the MCP bridge and registers a `--mcp-config` flag.

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
- `apps/web/src/composer-logic.ts`
- `packages/shared/src/composerTrigger.ts`
- `apps/web/src/components/chat/ChatComposer.tsx`
- `apps/web/src/components/ChatView.tsx`
- `apps/web/src/providerSkillSearch.ts`
- `apps/server/src/provider/providerSnapshot.ts`
- `apps/server/src/provider/Layers/PiProvider.ts`
- `apps/server/src/provider/Layers/PiAdapter.ts`

## Probe Findings

- 2026-06-23 browser/MCP audit: the current T3 in-app browser is exposed as T3-owned MCP `preview_*` tools backed by `PreviewAutomationBroker`, `McpSessionRegistry`, and an automation-capable desktop preview owner.
- 2026-06-23 browser/MCP audit: existing providers that can consume MCP receive the provider-scoped T3 MCP endpoint and Authorization header from `McpProviderSession`; Cursor/Grok pass it as `mcpServers`, and OpenCode registers it with `mcp.add`.
- 2026-06-23 browser/MCP audit: the reference Pi PR uses RPC and maps Pi tool events, but no evidence was found that it injects the current T3 MCP preview server into Pi.
- 2026-06-23 live Pi probe: local Pi is 0.79.10. `pi --help` advertises `--mode rpc`, `--provider`, `--model`, `--thinking`, and `--list-models`.
- 2026-06-23 live Pi probe: Windows Node child processes should launch Pi through the package CLI path or an equivalent shim-resolved command, not by spawning `pi.ps1` directly.
- 2026-06-23 live Pi probe: RPC framing is strict JSONL over stdin/stdout. The package docs require splitting only on `\n` and stripping an optional trailing `\r`.
- 2026-06-23 live Pi probe: `get_available_models` returned 35 dynamic models from local Pi config, including the configured Baseten `zai-org/GLM-5.2` model. No static fallback model is needed.
- 2026-06-23 live Pi probe: a prompt smoke against Baseten accepted `prompt` and streamed `message_update` `text_delta` content followed by `turn_end` and `agent_end`; empty intermediate event types were separate from assistant text.
- 2026-06-23 live Pi probe: Pi's README says core Pi has no MCP and MCP must be built by extension. The installed `pi-mcp-adapter` 2.10.0 extension supports HTTP MCP servers with bearer-token environment variables and a `--mcp-config` flag.
- 2026-06-23 live Pi MCP probe: `pi --mode rpc --no-session --mcp-config <temp> --append-system-prompt <text>` accepted the adapter config, initialized MCP status, and returned a successful `get_state` response.
- 2026-06-23 composer affordance review: web `ChatComposer` uses local `apps/web/src/composer-logic.ts` trigger detection, while `packages/shared/src/composerTrigger.ts` already has a `slash-model` branch for `/model` and `/model query`.
- 2026-06-23 composer affordance review: Pi provider snapshots currently call `buildServerProvider` without `skills` or `slashCommands`, so the composer receives empty arrays even though Codex and Claude populate those affordances through provider status.
- 2026-06-23 composer affordance review: `PiAdapter.sendTurn` rejects non-empty attachments with "Pi provider does not support T3 Code attachments in v1", so file/document mention handling needs an explicit compatibility decision before implementation.

## Footguns Discovered

- The installed Delano `init.sh` template executed backticked Markdown inside an unquoted heredoc; this was repaired locally during bootstrap.
- Generated validation helpers initially assumed root `scripts/` copies of Delano helpers; those paths were repaired to use `.agents/scripts`.
- `.repos/` contains vendored source with bidi-control test data; text-safety validation should not scan read-only vendored references.
- "RPC instead of SDK" is not sufficient by itself for in-app browser support; Pi must receive the T3 MCP preview tools through `pi-mcp-adapter`.
- Pi `agent-browser` and other external browser automation tools are not equivalent to T3's collaborative in-app browser surface.

## Remaining Unknowns

- Whether the fork PR tests still apply cleanly to current provider-instance architecture.
- Whether future Pi versions add core remote MCP registration that can replace the extension-backed `pi-mcp-adapter` bridge.
- Which Pi RPC or extension surface, if any, can provide repo skill and provider slash-command inventories.
- Whether file/document mentions should be converted into prompt text/context for Pi or gated until Pi supports T3 attachment payloads.

## Dependencies

- Local Pi runtime for final E2E verification.
- Existing provider driver registry and server provider snapshot infrastructure.
- Existing T3 MCP preview automation infrastructure and desktop preview owner connection.
- Existing web provider settings/model picker flows.
- Issue #402 and the fork PR as reference inputs.

## Approval Notes

`T-001` completed the live RPC probe. Implementation proceeded with Pi RPC model/session/event support and an extension-backed `pi-mcp-adapter` bridge for T3 preview MCP.
