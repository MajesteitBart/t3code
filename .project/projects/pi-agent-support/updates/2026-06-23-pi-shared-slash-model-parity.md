---
timestamp: 2026-06-23T20:37:49Z
status: in-progress
task: T-011
stream: WS-F
---

# Progress Update

## Completed

- Replaced the web composer's local trigger parser with the shared `@t3tools/shared/composerTrigger` semantics while preserving web-only terminal placeholder token boundaries.
- Added a small `composerSlashModelTrigger` resolver so `/model` and `/model query` take the model-picker path for Pi and non-Pi providers without adding a Pi-specific parser branch.
- Threaded the `/model query` text into `ProviderModelPicker`/`ModelPickerContent` as the initial model search query, then removed the command text from the composer without focusing the editor back over the picker.
- Kept `/plan` and `/default` as standalone slash commands by re-exporting the shared parser contract through `apps/web/src/composer-logic.ts`.

## In Progress

- T-012 and T-013 remain ready for provider metadata and Pi mention/attachment gating work.

## Blockers

- None.

## Next Actions

- Populate or explicitly gate Pi skill/provider slash-command metadata.
- Support or clearly gate Pi file, document, and image mention paths.

## Evidence

- `pnpm exec vp test run packages/shared/src/composerTrigger.test.ts apps/web/src/composer-logic.test.ts apps/web/src/components/chat/composerSlashModelTrigger.test.ts` passed with 3 files and 46 tests.
- `pnpm exec vp test run apps/web/src/composer-logic.test.ts apps/web/src/components/chat/composerSlashModelTrigger.test.ts apps/web/src/composerDraftStore.test.ts apps/web/src/providerSkillSearch.test.ts packages/shared/src/composerTrigger.test.ts packages/shared/src/composerInlineTokens.test.ts` passed with 6 files and 130 tests.
- Mobile drift check: `apps/mobile/src/features/threads/ThreadComposer.tsx` already imports the shared parser and does not implement a separate `/model` send path; no mobile code changed for this task.
