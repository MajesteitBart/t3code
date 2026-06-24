---
id: T-017
name: Support Pi user-interaction extension UI requests
status: done
workstream: WS-B
created: 2026-06-23T22:54:49Z
updated: 2026-06-23T23:11:31Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: []
conflicts_with: [apps/server/src/provider/Layers/PiAdapter.ts, apps/web/src/session-logic.ts, apps/web/src/components/chat/MessagesTimeline.tsx]
parallel: true
priority: high
estimate: M
story_id: 
acceptance_criteria_ids: [AC-009]
---

# Task: Support Pi user-interaction extension UI requests

## Description

Pi emits extension UI/user-interaction requests, including ask_user flows, during normal agent turns. The current adapter surfaces these as unsupported errors, so user interaction in Pi sessions does not work. Bridge supported interactive request types into the existing T3 Code user-input flow instead of failing the turn.

## Acceptance Criteria

- [x] Pi ask_user/user-interaction requests no longer render as 'Pi extension UI requests are not supported' errors.
- [x] Supported Pi ask_user requests produce an actionable user-input item in the T3 Code timeline.
- [x] Submitting a response resolves the pending Pi request through the Pi RPC bridge without starting a duplicate turn.
- [x] Targeted tests cover Pi ask_user request handling and response delivery.

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

- 2026-06-23T23:11:31Z: Built Windows x64 desktop installer after the user-interaction fix and ran desktop smoke test. Evidence: pnpm run dist:desktop:win:x64 produced release/T3-Code-0.0.27-x64.exe and release/T3-Code-0.0.27-x64.exe.blockmap; pnpm run test:desktop-smoke passed.

- 2026-06-23T23:05:55Z: Implemented Pi extension UI bridge: input/editor/select/confirm requests now emit user-input.requested, respondToUserInput sends extension_ui_response through the Pi RPC stdin send path, and freeform-only prompts are preserved in web session parsing. Evidence: pnpm exec vp test run apps/server/src/provider/Layers/PiAdapter.test.ts apps/web/src/session-logic.test.ts apps/server/src/provider/Layers/PiProvider.test.ts passed 79 tests; pnpm exec vp run typecheck passed; pnpm exec vp check passed with existing warnings only; git diff --check passed; pnpm exec delano validate passed with zero errors.

- 2026-06-23T22:54:59Z: User reported Pi ask_user/user interaction is blocked by unsupported extension UI request handling.

- 2026-06-23T22:54:49Z: Created from .project/templates/task.md by `delano task add`.
