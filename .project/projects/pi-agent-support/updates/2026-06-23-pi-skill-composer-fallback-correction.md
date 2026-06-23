---
timestamp: 2026-06-23T21:41:50Z
status: complete
task: 
stream: 
---

# Progress Update

## Completed

- Corrected the Pi composer skill menu after live testing exposed that `$` still showed a Pi-specific dead-end.
- `ChatComposer` and `ChatView` now resolve composer/timeline skill metadata from the selected provider when it has enabled skills, otherwise from the workspace skill catalog published by other provider snapshots.
- Pi provider-native slash-command results remain empty until Pi exposes an executable command path, while `$skill` can use workspace skills under Pi.
- Updated `docs/providers/pi.md` to describe the fallback behavior.

## In Progress

- None.

## Blockers

- None.

## Next Actions

- No follow-up action required for this correction.

## Evidence

- `pnpm exec vp test run apps/web/src/providerSkillSearch.test.ts apps/web/src/components/chat/composerMenuEmptyState.test.ts` passed: 2 files, 8 tests.
- `pnpm exec vp check` passed with 0 errors and existing unrelated lint warnings.
- `pnpm exec vp run typecheck` passed across all 15 packages.
- `pnpm exec delano validate` passed with 0 errors and 1 compatibility warning for the missing `.claude` mirror.
- Live Pi GLM-5.2 smoke test returned `pi_smoke_ok` for the prompt asking for the lowercase form of `PI_SMOKE_OK`.
- Live browser verification on `http://localhost:5733/` showed workspace skill entries under `$` with `GLM-5.2` selected and no `Pi does not expose executable skills yet.` message.
