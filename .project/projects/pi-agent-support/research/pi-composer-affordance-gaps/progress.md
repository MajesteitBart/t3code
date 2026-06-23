---
type: research_progress
project: pi-agent-support
slug: pi-composer-affordance-gaps
created: 2026-06-23T19:48:24Z
updated: 2026-06-23T19:58:00Z
---

# Progress: Pi composer affordance gaps

## 2026-06-23T19:48:24Z

- Opened research intake for project `pi-agent-support`.
- Primary question: Why does the Pi integration only partially support slash commands, repo skills, and document/file mentions, and what debug or feature tasks should be added before implementation?

## Validation Evidence

- `delano status --brief` before changes showed `pi-agent-support spec=complete plan=done open_tasks=0 total_tasks=9`.
- `delano workstream add` created `WS-F Composer Affordance Parity`.
- `delano task add` created `T-010` through `T-014` for audit, slash/model parity, Pi skills/provider slash commands, file/document mentions, and final verification.
- Source review found web/shared slash parser drift, empty Pi skills/slash command metadata, and Pi attachment rejection.

## Handoff Summary

- Findings were folded into `spec.md`, `plan.md`, `WS-F`, tasks `T-010` through `T-014`, and update `2026-06-23-pi-composer-affordance-review.md`.
- No implementation or build was performed.
