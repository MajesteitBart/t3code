---
type: research_intake
project: pi-agent-support
slug: pi-composer-affordance-gaps
owner: codex
status: folded-forward
created: 2026-06-23T19:48:24Z
updated: 2026-06-23T19:58:00Z
---

# Research Plan: Pi composer affordance gaps

## Goal

Answer the research question and fold durable conclusions into canonical Delano project artifacts.

## Primary Question

Why does the Pi integration only partially support slash commands, repo skills, and document/file mentions, and what debug or feature tasks should be added before implementation?

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

Folded forward

## Phases

- [x] Open research intake
- [x] Investigate sources and options
- [x] Summarize findings
- [x] Fold forward into canonical project artifacts or explicitly close as no-action

## Decisions Made

| Decision | Rationale |
| --- | --- |
| Add a new Delano workstream instead of editing completed tasks | The gaps cross web composer parsing, provider metadata, and Pi adapter payload handling after the previous provider/browser work had been closed. |
| Split debug audit from implementation tasks | The user requested review and task creation only, and the exact Pi support surface for skills/commands/mentions still needs layer attribution. |

## Blockers

| Blocker | Owner | Check-back |
| --- | --- | --- |
