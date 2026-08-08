---
id: T-020
name: Run the foundation gate and scope follow-on projects
status: planned
workstream: WS-E
created: 2026-08-07T13:17:03Z
updated: 2026-08-08T12:55:30Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-004, T-019]
conflicts_with: [project closeout, context pack]
parallel: false
priority: high
estimate: M
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008]
---

# Task: Run the foundation gate and scope follow-on projects

## Description

Review outcome evidence, unresolved risks, performance, contract/lifecycle reliability, and module reuse; issue a proceed, revise, or stop recommendation and outline only the next approved project.

## Acceptance Criteria

- [ ] The outcome review marks every success metric met, unmet, or explicitly waived with owner and reason.
- [ ] The decision log records proceed, revise, or stop with evidence.
- [ ] Remaining contract, security, performance, reuse, CI, and release risks have owners or are explicit blockers.
- [ ] The next recommended project has a measurable outcome and non-goals but is not decomposed before activation.
- [ ] Context and progress files match the evidence at handoff.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008

## Technical Notes

- Candidate project families, pending discovery and gate outcome: (1) chat/transcript/composer plus new-task bootstrap recovery; (2) daily UX/shelf policy and task creation; (3) T3 Connect/DPoP plus Android relay/FCM server prerequisites; (4) platform integrations such as deep navigation, share, shortcuts, notifications, background work, haptics, and Glance/ongoing status; (5) workspace files/review/source-control/terminal tools; (6) release/signing/store operations.
- Preserve useful Swift evidence (pagination/viewport tests, reducer cases, write fencing, offline outbox, platform route mailbox) as research inputs. Do not preapprove its parser/cache choices, throttle/backoff constants, attachment limits, iOS visual tokens, or task decomposition.
- The next project receives one measurable outcome and non-goals only after the foundation gate; listing candidates here is not activation.
- T-004 reuse handoff: review-diff and composer are adapter-first `adapt` candidates only; retain their thin Expo modules and require the focused React Native compatibility checks, license handling, and performance baselines recorded in D-015. Terminal remains deferred until an approved project owns NDK/CMake/Zig/Ghostty supply-chain, four-ABI, 16-KiB-page, and RN adapter validation. Native controls should be replaced natively in Compose, not extracted. This note records prerequisites only and does not activate or decompose a follow-on project.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-08T12:55:30Z: T-004 recorded reuse prerequisites in D-015: adapter-first review/composer candidates, terminal supply-chain and ABI gates, and Compose-native controls replacement; this does not activate follow-on work.

- 2026-08-07T13:17:03Z: Created from .project/templates/task.md by `delano task add`.
