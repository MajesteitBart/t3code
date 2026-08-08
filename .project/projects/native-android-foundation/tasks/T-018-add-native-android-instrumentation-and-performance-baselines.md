---
id: T-018
name: Add native Android instrumentation and performance baselines
status: planned
workstream: WS-E
created: 2026-08-07T13:17:03Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-015, T-017]
conflicts_with: [Android test targets, benchmark fixtures]
parallel: true
priority: medium
estimate: L
operating_mode: multi-stream
story_id: US-006
acceptance_criteria_ids: [AC-006, AC-008]
---

# Task: Add native Android instrumentation and performance baselines

## Description

Create stable emulator instrumentation entry points plus startup and representative home-list Macrobenchmark or equivalent baseline evidence.

## Acceptance Criteria

- [ ] A documented focused command runs foundation instrumentation on the selected emulator API.
- [ ] Startup and representative home-list metrics are recorded with device, API, build type, fixture size, and thresholds.
- [ ] The benchmark detects continuous invalidation, duplicate collectors, or unbounded row work.
- [ ] Benchmark and instrumentation state are isolated from live T3 data and other running dev servers.

## Traceability

- Story: US-006
- Acceptance criteria: AC-006, AC-008

## Technical Notes

- Record budgets before T-016 asserts them: cold/warm startup as applicable, representative aggregate list size, frame/recomposition evidence, device/emulator image, API, ABI, build type, thermal/setup notes, and variance policy.
- A passing single run is not a threshold. Define sample count and an actionable failure rule without overgeneralizing emulator numbers to low-end production hardware.
- Include state updates from active WebSocket, reconnecting HTTP fallback, and passive reachability changes to expose duplicate collection or broad recomposition.
- Exact budgets are selected from measured evidence; do not import Swift timing or 760pt layout constants.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:03Z: Created from .project/templates/task.md by `delano task add`.
