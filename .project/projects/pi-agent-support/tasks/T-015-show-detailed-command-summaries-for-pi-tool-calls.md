---
id: T-015
name: Show detailed command summaries for Pi tool calls
status: done
workstream: WS-C
created: 2026-06-23T22:32:26Z
updated: 2026-06-23T22:41:17Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: []
conflicts_with: [apps/web/src/components/chat/MessagesTimeline.tsx, apps/web/src/session-logic.ts, apps/server/src/provider/Layers/PiAdapter.ts]
parallel: true
priority: high
estimate: M
story_id: 
acceptance_criteria_ids: [AC-009]
---

# Task: Show detailed command summaries for Pi tool calls

## Description

Pi-selected turns currently render completed tool calls as generic tool names such as Bash, Edit, and Write without the command/path/detail needed to understand what happened. Restore detailed command summaries in the chat timeline without regressing existing provider tool-call rendering.

## Acceptance Criteria

- [x] Bash/shell tool-call rows display the actual command or a concise command preview, not only the word Bash.
- [x] File-edit/write tool-call rows display the target path or a concise file operation summary when that metadata is available.
- [x] Grouped/collapsed tool calls retain the detailed row labels visible in the timeline.
- [x] Targeted tests cover command/detail extraction for representative Bash, Edit, and Write tool calls.

## Traceability

- Story: none
- Acceptance criteria: AC-009

## Technical Notes

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T22:41:17Z: Normalized Pi tool data now preserves rawInput/rawOutput plus concise command summaries, and session-log extraction reads Pi raw input args plus snake_case file paths for Bash/Edit/Write timeline labels. Evidence: pnpm exec vp test run apps/server/src/provider/Layers/PiAdapter.test.ts apps/web/src/session-logic.test.ts passed 71 tests; vp check passed; vp run typecheck passed.

- 2026-06-23T22:32:30Z: User reported Pi command/tool rows only show generic names in the timeline.

- 2026-06-23T22:32:26Z: Created from .project/templates/task.md by `delano task add`.
