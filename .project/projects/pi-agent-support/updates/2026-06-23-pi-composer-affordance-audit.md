---
timestamp: 2026-06-23T20:31:36Z
status: in-progress
task: T-010
stream: WS-F
---

# Progress Update

## Completed

- Completed the T-010 code/test audit without production implementation changes.
- Ran focused audit tests:
  `pnpm exec vp test run apps/web/src/composer-logic.test.ts packages/shared/src/composerInlineTokens.test.ts apps/server/src/provider/Layers/PiProvider.test.ts apps/server/src/provider/Layers/PiAdapter.test.ts apps/server/src/provider/providerStatusCache.test.ts apps/web/src/providerSkillSearch.test.ts`
  passed with 6 files and 59 tests.

## Reproduction Matrix

| Affordance | Expected while Pi is selected | Current actual behavior | Layer attribution |
| --- | --- | --- | --- |
| `/` | Built-in commands plus executable Pi provider commands are shown. | Built-ins are shown; Pi provider commands are absent because the Pi snapshot has `slashCommands: []`. | Provider status metadata gap in `apps/server/src/provider/Layers/PiProvider.ts` and snapshot/cache path. |
| `/model` | Model command uses the shared model-trigger path and opens the provider model picker. | Web local parser reports `{ kind: "slash-command", query: "model" }`; selecting the built-in menu item opens the picker, but the trigger state does not match shared `slash-model`. | Frontend trigger-state drift in `apps/web/src/composer-logic.ts`. |
| `/model query` | Model picker remains active and filters by `query`. | Web local parser returns no trigger after `/model ` arguments, so direct query/filter behavior is lost and the text can proceed as a normal prompt. | Frontend trigger-state drift; shared parser already supports this in `packages/shared/src/composerTrigger.ts`. |
| `/plan` | Local T3 command switches the thread to plan mode instead of sending a provider prompt. | Standalone send handling and menu selection remain provider-agnostic and route through `parseStandaloneComposerSlashCommand` / `handleInteractionModeChange`. | No Pi-specific failure found. Keep regression coverage in T-011. |
| `/default` | Local T3 command switches the thread back to default mode instead of sending a provider prompt. | Standalone send handling and menu selection remain provider-agnostic and route through `parseStandaloneComposerSlashCommand` / `handleInteractionModeChange`. | No Pi-specific failure found. Keep regression coverage in T-011. |
| `$skill` | Pi-supported repo/provider skills appear and insert as skill chips, or unsupported state is explicit. | Skill trigger works, but `searchProviderSkills(selectedProviderStatus?.skills ?? [])` receives an empty Pi skills array. | Provider status metadata gap; UI cannot distinguish unsupported from undiscovered. |
| `@file` / document mentions | Mentioned context reaches Pi in a compatible form or is blocked/degraded before send with prompt preserved. | Path search and insertion work. File mentions become markdown links in prompt text through `serializeComposerFileLink`; image uploads are the current binary `attachments` path and Pi adapter rejects non-empty attachments. Document/context compatibility still needs a deliberate Pi policy before T-013 closes. | Dispatch serialization plus adapter capability boundary. Do not treat all mentions as image attachments, but do gate unsupported attachment/document payloads before prompt loss. |

## Layer Evidence

- `apps/web/src/composer-logic.ts` supports trigger kinds `path`, `slash-command`, and `skill`; it lacks the shared `slash-model` branch.
- `packages/shared/src/composerTrigger.ts` already supports `slash-model` for `/model` and `/model query`.
- `apps/web/src/composer-logic.test.ts` currently asserts the drift: `/model` is a generic slash command and `/model spark` has no active trigger.
- `apps/web/src/components/chat/ChatComposer.tsx` builds provider slash commands from `selectedProviderStatus.slashCommands` and skills from `selectedProviderStatus.skills`; the menu path itself is provider-agnostic.
- `apps/server/src/provider/Layers/PiProvider.ts` builds Pi snapshots without `skills` or `slashCommands`, so `providerSnapshot.ts` defaults both arrays to empty.
- `apps/web/src/components/ChatView.tsx` clears the composer only after local validation; failure restore paths exist, but Pi-specific unsupported attachment/document states need tests before relying on them.
- `apps/server/src/provider/Layers/PiAdapter.ts` rejects non-empty `attachments` with `Pi provider does not support T3 Code attachments in v1.`

## Follow-up Confirmation

- `T-011` is confirmed as frontend shared-trigger parity plus regression tests for Pi and a non-Pi provider path.
- `T-012` is confirmed as provider snapshot/status metadata work: expose executable Pi skills/provider commands only if Pi can execute them, otherwise provide an explicit unsupported state.
- `T-013` is confirmed as mention/send compatibility work: preserve prompt text, support prompt-text file links where sufficient, and gate unsupported binary/document payloads before send.
- `T-014` remains the verification gate after `T-011`, `T-012`, and `T-013`.

## In Progress

- None.

## Blockers

- None.

## Next Actions

- Close `T-010` and open the dependency-safe implementation tasks.
