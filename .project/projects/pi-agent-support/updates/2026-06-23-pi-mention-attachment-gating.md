---
timestamp: 2026-06-23T20:43:20Z
status: in-progress
task: T-013
stream: WS-F
---

# Progress Update

## Completed

- Traced the composer path selection flow: `@file` items are serialized by `serializeComposerFileLink` into markdown links in the prompt, then sent to Pi as plain text.
- Traced contextual document/browser/review flows: terminal, element, preview annotation, and review comment context blocks are appended to `messageTextForSend` before dispatch and do not use T3 attachment payloads.
- Added `resolveUnsupportedProviderAttachmentSend` and wired it into `ChatView.onSend` before optimistic dispatch and draft clearing, so Pi image attachments are blocked with explicit copy and the prompt/images remain in the composer for retry.
- Kept the Pi adapter's runtime validation as a backstop and added a regression test proving attachment payloads are rejected before issuing a Pi RPC `prompt` command.
- Documented the v1 behavior in `docs/providers/pi.md`.

## In Progress

- T-014 remains ready for final composer-affordance parity verification.

## Blockers

- None.

## Next Actions

- Run the full T-014 parity verification and final repo gates.

## Evidence

- `pnpm exec vp test run apps/web/src/components/ChatView.logic.test.ts apps/web/src/composer-editor-mentions.test.ts apps/web/src/lib/elementContext.test.ts apps/web/src/lib/previewAnnotation.test.ts apps/web/src/reviewCommentContext.test.ts apps/server/src/provider/Layers/PiAdapter.test.ts` passed with 6 files and 83 tests.
- File mention coverage: `apps/web/src/composer-editor-mentions.test.ts` plus `resolveUnsupportedProviderAttachmentSend` confirms prompt-text file mentions are not blocked for Pi.
- Document/context coverage: element, preview annotation, and review comment tests cover prompt-text context serialization; the Pi guard allows those paths when no image attachment payload exists.
- Retry behavior: the Pi attachment guard returns before `sendInFlightRef`, optimistic message creation, and `clearComposerDraftContent`, preserving the typed prompt and attachments for user retry/removal.
