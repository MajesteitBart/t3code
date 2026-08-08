---
type: research_intake
project: native-android-foundation
slug: swiftui-pr5178-review-assessment
owner: team
status: completed
created: 2026-08-08T10:23:33Z
updated: 2026-08-08T10:32:44Z
---

# Research Plan: Assess SwiftUI PR #5178 deep-review recommendations

## Goal

Answer the research question and fold durable conclusions into canonical Delano project artifacts.

## Primary Question

Which claims in the deep review are supported by PR #5178 and canonical T3 contracts, and which should be folded into the native Android foundation without scope creep or iOS-specific overfitting?

## Scope

### In Scope

- Gather relevant evidence.
- Capture findings and decisions.
- Identify changes needed in `spec.md`, `plan.md`, `decisions.md`, workstreams, tasks, or updates.

### Out of Scope

- Marking delivery tasks done from research alone.
- External sync writes without normal Delano approval semantics.
- Storing secrets, credentials, or private machine paths.

## Current Phase

Completed

## Phases

- [x] Open research intake
- [x] Investigate sources and options
- [x] Summarize findings
- [x] Fold forward into canonical project artifacts or explicitly close as no-action

## Decisions Made

| Decision                                                                          | Rationale                                                                                                                                                                                                              |
| --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Treat PR #5178 as a reference implementation, not a shipped Android contract      | The PR is still open and titled as an experimental SwiftUI client; canonical TypeScript contracts and server behavior remain authoritative.                                                                            |
| Integrate contract-backed invariants, not Swift constants or ownership boundaries | Wire shapes, replay safety, persistence ordering, and state invariants transfer; exact timers, actor mechanisms, and iOS presentation choices do not automatically transfer.                                           |
| Preserve server receipt evidence while adding client commit verification          | The iOS client verifies ambiguous turns from snapshots, while the server also durably deduplicates `commandId`; the Android proof should exercise both without blind replay.                                           |
| Keep foundation scope bounded                                                     | Only contracts used by pairing, the live shell, and the test-only ambiguity proof enter foundation implementation. Full chat, command catalogs, T3 Connect, CameraX, and workspace parity remain follow-on candidates. |

## Blockers

| Blocker | Owner | Check-back |
| ------- | ----- | ---------- |
| None    | -     | -          |
