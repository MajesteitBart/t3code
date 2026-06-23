---
type: research_findings
project: pi-agent-support
slug: pi-composer-affordance-gaps
created: 2026-06-23T19:48:24Z
updated: 2026-06-23T19:58:00Z
---

# Findings: Pi composer affordance gaps

## Source References

- User screenshots from 2026-06-23 showing Pi model selection working, empty skills panel, and `/` menu with built-in commands only.
- `delano status --brief`: `pi-agent-support` was marked complete/done with 0 open tasks before this review.
- `apps/web/src/composer-logic.ts`
- `packages/shared/src/composerTrigger.ts`
- `apps/web/src/components/chat/ChatComposer.tsx`
- `apps/web/src/components/ChatView.tsx`
- `apps/server/src/provider/Layers/PiProvider.ts`
- `apps/server/src/provider/Layers/PiAdapter.ts`
- `apps/server/src/provider/providerSnapshot.ts`
- `packages/contracts/src/server.ts`

## Observations

- Pi is partially usable: the user screenshot shows Pi provider/model selection and built-in slash commands in the composer.
- The web composer still imports trigger logic from `apps/web/src/composer-logic.ts`, whose trigger kinds are `path`, `slash-command`, and `skill`; it does not expose the `slash-model` state already present in `packages/shared/src/composerTrigger.ts`.
- `packages/shared/src/composerTrigger.ts` handles `/model` and `/model query` as a dedicated `slash-model` trigger, so there is a concrete shared contract for model-query slash behavior.
- `ChatComposer` builds provider slash-command menu items from `selectedProviderStatus.slashCommands` and skill search results from `selectedProviderStatus.skills`.
- Pi provider status currently calls `buildServerProvider` without `slashCommands` or `skills`; `providerSnapshot` defaults both arrays to empty, which matches the empty skills panel in the screenshot.
- Existing providers prove the metadata path: Codex populates skills from `skills/list`; Claude parses slash commands from initialization.
- `ChatView` sends composer image attachments through `startThreadTurn`, and `PiAdapter.sendTurn` currently rejects any non-empty attachments with "Pi provider does not support T3 Code attachments in v1." File/document mention dispatch must be traced before deciding whether the fix belongs in prompt conversion, adapter support, or UI gating.

## Options Considered

| Option | Pros | Cons | Decision |
| --- | --- | --- | --- |
| Treat this as a bug in the existing completed web task | Minimal Delano churn | Hides cross-layer scope and reopens a closed task with unclear acceptance | Rejected |
| Add one broad "fix composer" task | Simple backlog shape | Mixes debug, slash/model parser work, provider metadata, mention payloads, and verification | Rejected |
| Add a new workstream with one audit task and separate implementation/verification tasks | Keeps layer attribution and acceptance clear; matches the user's "debug or feature adding" request | Adds five tasks to a previously complete project | Accepted |

## Fold-Forward Candidates

| Finding | Target Artifact | Proposed Change |
| --- | --- | --- |
| Slash/model behavior diverges between web local and shared trigger logic | `spec.md`, `plan.md`, `T-011` | Add AC-010 and a task to restore shared slash/model query parity. |
| Pi provider status has empty skills and provider slash commands | `spec.md`, `plan.md`, `T-012` | Add AC-011/AC-012 and a task to populate or explicitly gate metadata. |
| Mention dispatch may hit Pi attachment rejection | `spec.md`, `plan.md`, `T-013` | Add AC-013 and a task to support or gate file/document mentions before prompt loss. |
| Existing project was marked complete | `WS-F`, `T-010` through `T-014`, progress update | Reopen project planning scope with a composer affordance workstream and validation task. |

## Open Questions

- Which Pi RPC or extension command returns repo skill inventory, if any?
- Does Pi expose provider slash commands separately from skills, or should unsupported command metadata remain empty?
- Should file/document mentions be converted to prompt text for Pi v1, or should the UI block them until Pi supports T3 attachment payloads?
