---
id: T-013
name: Prove ambiguous turn recovery with stable identity
status: done
workstream: WS-C
created: 2026-08-07T13:16:59Z
updated: 2026-08-08T16:57:47Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-017, T-022]
conflicts_with: [command integration fixture]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-005
acceptance_criteria_ids: [AC-005]
---

# Task: Prove ambiguous turn recovery with stable identity

## Description

Add a test-only turn path that preserves stable command/message identity, verifies an ambiguous commit from a fresh thread snapshot before replay, and uses server receipt/domain-event evidence as a backstop.

## Acceptance Criteria

- [x] The fixture deliberately loses the first client response after server acceptance.
- [x] The logical turn fixes `{commandId, messageId, createdAt}` on first attempt and reuses it verbatim for every later explicit attempt.
- [x] A fresh thread snapshot containing `messageId` resolves the lost response as success without replay; an unavailable/absent confirmation surfaces ambiguity and preserves the identity.
- [x] An explicit retry first repeats commit verification and, only when still uncommitted, sends the identical wire payload; transport itself never replays the sent request.
- [x] Server evidence shows one accepted durable receipt and one matching message/domain effect, and the test waits on typed receipts/drains rather than sleeps or polling.
- [x] No production debug screen or alternate protocol is introduced.

## Traceability

- Story: US-005
- Acceptance criteria: AC-005

## Technical Notes

- Client reference: `NativeFeatureClient.sendMessageResolved/messageWasCommitted` and `NativeRetryIdentityTests.swift`.
- Server reference: `apps/server/src/orchestration/Layers/OrchestrationEngine.ts` checks the durable receipt repository by `commandId` before deciding/persisting and returns the prior accepted sequence. The review's claim that receipt idempotency "may not exist" is false for this branch.
- Snapshot verification remains necessary because a sent RPC response can be lost and the client has no receipt lookup API. Server dedupe is a backstop for a later explicit retry, not permission for automatic transport replay.
- Use a precreated synthetic thread so this foundation proof does not implement new-task bootstrap/worktree cleanup. Partial bootstrap recovery with stable final-turn identity is recorded for the future chat/new-task project.
- Canonical turn wire mode is `interactionMode: "default"`, not the Swift UI term `standard`.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T16:57:47Z: Post-close review preserves definitive typed rejections outside ambiguity recovery; 6 stable-turn cases pass in debug/release and the final combined real-server gate passes.

- 2026-08-08T16:31:01Z: StableTurnRecoveryTest passed 5 cases in debug and release; nativeAndroidIntegrationTest passed 4 real-server tests including lost-after-acceptance recovery. The stable test verified one dispatch before fresh-snapshot recovery, byte-identical explicit retry behavior, one durable accepted receipt, one matching message and turn-start effect after a deliberate duplicate, and typed drain synchronization. core-data/core-testing debug and release lint passed; STATE.md documents the contract.

- 2026-08-08T16:22:03Z: Implement stable turn identity, fresh snapshot verification before replay, and durable receipt/domain-effect evidence through the disposable fixture.

- 2026-08-08T16:22:02Z: T-017 and T-022 are done; T-012 has closed, so the serial command-integration conflict zone is clear.

- 2026-08-07T13:16:59Z: Created from .project/templates/task.md by `delano task add`.
