---
id: T-016
name: Queue Pi follow-up prompts while a turn is active
status: done
workstream: WS-B
created: 2026-06-23T22:34:26Z
updated: 2026-06-23T22:41:17Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: []
conflicts_with: [apps/server/src/provider/Layers/PiAdapter.ts]
parallel: true
priority: high
estimate: S
story_id: 
acceptance_criteria_ids: [AC-009]
---

# Task: Queue Pi follow-up prompts while a turn is active

## Description

Pi rejects plain prompt RPC calls while the agent is already processing and asks for streamingBehavior steer or followUp. Normal composer submits during an active Pi turn should be sent as followUp so they queue instead of surfacing a provider turn start failure.

## Acceptance Criteria

- [x] When a Pi session has an active turn, sendTurn includes streamingBehavior followUp on the prompt RPC request.
- [x] Active Pi follow-up submission keeps the existing active turn id and does not emit a duplicate turn.started event.
- [x] Prompt RPC failures in active follow-up mode do not clear the active turn state.
- [x] Targeted Pi adapter tests cover active-turn follow-up prompt behavior.

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

- 2026-06-23T22:41:17Z: Pi sendTurn now sends streamingBehavior followUp while a Pi turn is active and preserves the active turn on follow-up prompt failure. Evidence: PiAdapter test covers follow-up submission, no duplicate turn.started, and active turn retention; pnpm exec vp test run apps/server/src/provider/Layers/PiAdapter.test.ts apps/web/src/session-logic.test.ts passed 71 tests; vp check passed; vp run typecheck passed.

- 2026-06-23T22:34:31Z: User hit Provider turn start failed: Agent is already processing while Pi was still working.

- 2026-06-23T22:34:26Z: Created from .project/templates/task.md by `delano task add`.
