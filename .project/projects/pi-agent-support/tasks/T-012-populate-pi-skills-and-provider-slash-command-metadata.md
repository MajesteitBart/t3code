---
id: T-012
name: Populate Pi skills and provider slash command metadata
status: done
workstream: WS-F
created: 2026-06-23T19:52:42Z
updated: 2026-06-23T20:40:34Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-010]
conflicts_with: [apps/server/src/provider/Layers/PiProvider.ts, apps/server/src/provider/providerSnapshot.ts, apps/server/src/provider/providerStatusCache.ts, apps/web/src/components/chat/ChatComposer.tsx, apps/web/src/providerSkillSearch.ts]
parallel: true
priority: high
estimate: M
story_id: US-007
acceptance_criteria_ids: [AC-011, AC-012]
operating_mode: feature
---

# Task: Populate Pi skills and provider slash command metadata

## Description

Ensure Pi provider status supplies the composer with executable skill and provider slash-command metadata, or explicitly gates unsupported affordances.

## Acceptance Criteria

- [x] Pi provider status either exposes repo/provider skills and provider slash commands from Pi RPC or extension metadata, or marks them explicitly unsupported with a clear UI state.
- [x] Composer `$skill` search is populated when Pi can supply skills and filters disabled or unsupported skills consistently.
- [x] Provider slash commands appear in the `/` menu only when executable for Pi.
- [x] Status cache/provider snapshot tests cover Pi skills and slashCommands arrays.

## Traceability

- Story: US-007
- Acceptance criteria: AC-011, AC-012

## Technical Notes

Use Codex `skills/list` and Claude initialization slash-command parsing as reference patterns, but only expose Pi metadata that can execute through Pi or an installed Pi extension.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T20:40:34Z: 2026-06-23: T-012 evidence recorded in updates/2026-06-23-pi-executable-metadata-gating.md; targeted provider/composer tests passed with 4 files and 17 tests via pnpm exec vp test run.

- 2026-06-23T20:40:04Z: T-012 complete. Pi snapshots explicitly carry empty executable skill/provider slash-command metadata, the composer shows a Pi-specific unsupported skills state, docs note the v1 limitation, and targeted tests passed: 4 files, 17 tests.

- 2026-06-23T20:38:21Z: Begin Pi skills and provider slash-command metadata gating

- 2026-06-23T20:32:48Z: T-010 audit complete; ready for Pi skill/provider command metadata implementation

- 2026-06-23T19:52:42Z: Created from .project/templates/task.md by `delano task add`.
