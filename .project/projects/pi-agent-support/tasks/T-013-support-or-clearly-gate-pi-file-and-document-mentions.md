---
id: T-013
name: Support or clearly gate Pi file and document mentions
status: done
workstream: WS-F
created: 2026-06-23T19:52:51Z
updated: 2026-06-23T20:43:49Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-010]
conflicts_with: [apps/web/src/components/chat/ChatComposer.tsx, apps/web/src/components/ChatView.tsx, packages/shared/src/composerTrigger.ts, apps/server/src/provider/Layers/PiAdapter.ts, packages/contracts/src/providerRuntime.ts]
parallel: true
priority: high
estimate: M
story_id: US-008
acceptance_criteria_ids: [AC-013]
operating_mode: feature
---

# Task: Support or clearly gate Pi file and document mentions

## Description

Trace and fix the Pi path for @file/document mentions so they either reach Pi in a compatible form or are blocked with explicit composer/runtime feedback.

## Acceptance Criteria

- [x] The `@file`/document mention flow is traced from composer token to outgoing prompt or attachment payload to Pi adapter `sendTurn`.
- [x] If Pi can consume workspace-file/document context, the adapter sends it in a Pi-compatible form instead of rejecting attachments.
- [x] If Pi cannot consume T3 attachment payloads, composer or runtime behavior blocks or degrades mentions for Pi with explicit copy before send and preserves the typed prompt.
- [x] Tests cover file mention, document mention, attachment rejection or degradation, and retry behavior.

## Traceability

- Story: US-008
- Acceptance criteria: AC-013

## Technical Notes

Do not let a failed Pi attachment path clear the composer prompt without recoverable feedback. If the compatible v1 form is prompt text rather than T3 attachments, keep that behavior explicit and tested.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-23T20:43:49Z: 2026-06-23: T-013 evidence recorded in updates/2026-06-23-pi-mention-attachment-gating.md; focused web mention/context and Pi adapter tests passed with 6 files and 83 tests via pnpm exec vp test run.

- 2026-06-23T20:43:20Z: T-013 complete. File/document context routes as prompt text for Pi, image attachments are blocked before dispatch with draft-preserving copy, Pi adapter keeps attachment rejection as a backstop, docs updated, and targeted tests passed: 6 files, 83 tests.

- 2026-06-23T20:40:40Z: Begin Pi mention and attachment compatibility tracing

- 2026-06-23T20:32:48Z: T-010 audit complete; ready for Pi mention compatibility implementation

- 2026-06-23T19:52:51Z: Created from .project/templates/task.md by `delano task add`.
