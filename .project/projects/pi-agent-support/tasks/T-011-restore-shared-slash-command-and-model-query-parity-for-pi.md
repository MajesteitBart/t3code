---
id: T-011
name: Restore shared slash command and model query parity for Pi
status: done
workstream: WS-F
created: 2026-06-23T19:52:33Z
updated: 2026-06-23T20:38:13Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-010]
conflicts_with: [apps/web/src/composer-logic.ts, packages/shared/src/composerTrigger.ts, apps/web/src/components/chat/ChatComposer.tsx, apps/mobile/src/features/threads/ThreadComposer.tsx]
parallel: true
priority: high
estimate: M
story_id: US-006
acceptance_criteria_ids: [AC-010]
operating_mode: feature
---

# Task: Restore shared slash command and model query parity for Pi

## Description

Make Pi-selected composer slash handling follow the shared composer trigger contract, especially direct /model query behavior.

## Acceptance Criteria

- [x] Web composer trigger parsing uses shared composer trigger semantics or has tests proving parity with `packages/shared/src/composerTrigger.ts` for `/model`, `/model query`, `/plan`, and `/default`.
- [x] With Pi selected, typing `/model query` opens or filters the model picker instead of sending a literal `/model` prompt.
- [x] Built-in slash commands keep working for existing providers and mobile/shared composer surfaces are checked for drift.
- [x] Tests cover Pi selected provider behavior and at least one non-Pi provider path.

## Traceability

- Story: US-006
- Acceptance criteria: AC-010

## Technical Notes

Start from the divergence between `apps/web/src/composer-logic.ts` and `packages/shared/src/composerTrigger.ts`; avoid adding a Pi-only parser branch if the shared parser can be reused.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T20:38:13Z: 2026-06-23: T-011 evidence recorded in updates/2026-06-23-pi-shared-slash-model-parity.md; focused composer tests passed with 6 files and 130 tests via pnpm exec vp test run.

- 2026-06-23T20:37:49Z: T-011 complete. Web now delegates composer trigger parsing to `@t3tools/shared/composerTrigger`, slash-model picker action tests cover Pi and Codex providers, and focused composer tests passed: 6 files, 130 tests.

- 2026-06-23T20:33:10Z: Begin shared slash/model trigger parity implementation

- 2026-06-23T20:32:48Z: T-010 audit complete; ready for slash/model parity implementation

- 2026-06-23T19:52:33Z: Created from .project/templates/task.md by `delano task add`.
