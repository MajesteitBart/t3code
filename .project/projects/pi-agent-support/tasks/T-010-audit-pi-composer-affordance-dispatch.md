---
id: T-010
name: Audit Pi composer affordance dispatch
status: done
workstream: WS-F
created: 2026-06-23T19:52:23Z
updated: 2026-06-23T20:32:21Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-004, T-006, T-008, T-009]
conflicts_with: []
parallel: true
priority: high
estimate: S
story_id: US-006
acceptance_criteria_ids: [AC-010, AC-011, AC-012, AC-013]
operating_mode: debug
---

# Task: Audit Pi composer affordance dispatch

## Description

Reproduce and trace Pi-selected composer behavior for slash commands, skills, and file/document mentions before implementing fixes.

## Acceptance Criteria

- [x] Reproduction matrix captures expected versus actual behavior for `/`, `/model`, `/model query`, `/plan`, `/default`, `$skill`, and `@file`/document mentions while Pi is selected.
- [x] Evidence identifies whether each failure is frontend trigger state, provider status metadata, dispatch serialization, Pi RPC support, or adapter rejection.
- [x] No production implementation changes are made as part of this audit task.
- [x] Follow-up implementation tasks are confirmed or refined with file-level acceptance.

## Traceability

- Story: US-006
- Acceptance criteria: AC-010, AC-011, AC-012, AC-013

## Technical Notes

Known starting evidence:

- User screenshot shows Pi provider/model selection working, but the skills panel reports no skills and the `/` menu only shows built-ins.
- `apps/web/src/composer-logic.ts` lacks the `slash-model` branch already present in `packages/shared/src/composerTrigger.ts`.
- `apps/server/src/provider/Layers/PiProvider.ts` builds Pi snapshots without `skills` or `slashCommands`.
- `apps/server/src/provider/Layers/PiAdapter.ts` rejects non-empty attachments in `sendTurn`, which may affect mention dispatch.

## Definition of Done

- [x] Reproduction evidence complete
- [x] Layer attribution complete
- [x] Follow-up task scope confirmed
- [x] Research/update evidence recorded

## Evidence Log

- 2026-06-23T20:32:21Z: 2026-06-23: T-010 audit recorded in updates/2026-06-23-pi-composer-affordance-audit.md; focused audit tests passed with 6 files and 59 tests via pnpm exec vp test run.

- 2026-06-23T20:29:57Z: Begin Pi composer affordance dispatch audit

- 2026-06-23T19:52:23Z: Created from .project/templates/task.md by `delano task add`.
