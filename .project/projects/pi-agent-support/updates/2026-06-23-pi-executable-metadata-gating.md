---
timestamp: 2026-06-23T20:40:04Z
status: in-progress
task: T-012
stream: WS-F
---

# Progress Update

## Completed

- Made Pi provider snapshots explicitly pass empty `slashCommands` and `skills` arrays in disabled, pending, health-check failure, model-discovery failure, and ready states.
- Added a composer empty-state helper so `$` with Pi selected shows `Pi does not expose executable skills yet.` when Pi has no enabled skills instead of a generic no-results state.
- Kept provider slash-command rendering tied to executable provider snapshot metadata only; Pi contributes no provider slash-command items until Pi exposes an executable path.
- Documented the v1 limitation in `docs/providers/pi.md`.

## In Progress

- T-013 remains ready for mention and attachment compatibility/gating.

## Blockers

- None.

## Next Actions

- Trace the Pi mention/attachment path and add explicit pre-send/runtime gating for unsupported payloads.

## Evidence

- `pnpm exec vp test run apps/server/src/provider/Layers/PiProvider.test.ts apps/server/src/provider/providerStatusCache.test.ts apps/web/src/components/chat/composerMenuEmptyState.test.ts apps/web/src/providerSkillSearch.test.ts` passed with 4 files and 17 tests.
- Pi snapshot tests assert `slashCommands` and `skills` stay empty for disabled and ready Pi states.
- Provider cache tests now round-trip a Pi snapshot with empty executable metadata arrays.
