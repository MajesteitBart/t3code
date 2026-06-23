---
id: WS-F
name: WS-F Composer Affordance Parity
owner: T3 Code maintainers
status: done
created: 2026-06-23T19:52:11Z
updated: 2026-06-23T21:11:20Z
operating_mode: feature
---

# Workstream: WS-F Composer Affordance Parity

## Objective

Restore Pi-selected composer affordance parity for T3 slash commands, provider/repo skills, provider slash commands, and file/document mentions without starting implementation before the debug audit task confirms the exact failing layer for each affordance.

## Owned Files/Areas

- `apps/web/src/composer-logic.ts`
- `packages/shared/src/composerTrigger.ts`
- `apps/web/src/components/chat/ChatComposer.tsx`
- `apps/web/src/components/chat/ComposerCommandMenu.tsx`
- `apps/web/src/components/ChatView.tsx`
- `apps/web/src/providerSkillSearch.ts`
- `apps/server/src/provider/providerSnapshot.ts`
- `apps/server/src/provider/providerStatusCache.ts`
- `apps/server/src/provider/Layers/PiProvider.ts`
- `apps/server/src/provider/Layers/PiAdapter.ts`
- Focused web/provider tests covering composer triggers, Pi provider status metadata, and mention dispatch

## Dependencies

- `WS-B` must provide the Pi provider snapshot and adapter/runtime path.
- `WS-C` must provide the selected Pi provider/model state in the web composer.
- `WS-E` must leave Pi browser/MCP behavior explicit so composer tasks do not route browser work to external tooling.
- `T-010` must complete before implementing `T-011`, `T-012`, or `T-013`.

## Risks

- Web currently has a local composer trigger implementation that can drift from `packages/shared/src/composerTrigger.ts`.
- Pi provider status currently has empty `skills` and `slashCommands` arrays, so the composer cannot distinguish unsupported from undiscovered affordances.
- Pi `sendTurn` currently rejects attachments, so file/document mention dispatch can fail after the prompt has been cleared unless the path is gated or converted earlier.
- Provider-native commands and repo skills may not share one Pi RPC surface; fabricated menu entries would be worse than an explicit unsupported state.

## Handoff Criteria

- Slash command/model query behavior is traced and then aligned with shared composer semantics for Pi-selected threads.
- Pi provider status either exposes executable skills/provider slash commands or the UI clearly marks those affordances unsupported for Pi.
- File/document mentions are converted into Pi-compatible context or blocked/degraded before send with clear copy and no prompt loss.
- Final verification includes focused tests, `vp check`, `vp run typecheck`, `delano validate`, and a real Pi-selected composer smoke when implementation resumes.
