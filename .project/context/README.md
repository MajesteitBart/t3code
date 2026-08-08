# T3 Code Context Pack

This directory is durable, repository-local context for Delano-managed work. Prefer current code and canonical docs when they disagree with these notes, then repair the context pack instead of working around drift.

## Read First

1. `project-overview.md` for the mission, clients, and active delivery scopes.
2. `project-brief.md` for the current native Android initiative and boundaries.
3. The matching project under `.project/projects/` for executable scope and acceptance.
4. Only the specialized context required by the task.

## Retrieval Index

- Product, users, and core flows: `product-context.md`
- Architecture and reusable system behavior: `system-patterns.md`
- Languages, tooling, commands, and platform constraints: `tech-context.md`
- Repository ownership boundaries: `project-structure.md`
- Naming, documentation, and review conventions: `project-style-guide.md`
- GUI and native-client verification: `gui-testing.md`
- Current delivery state and evidence gaps: `progress.md`
- Source material for the native Android initiative: `source-material.md`
- Context debt, contradictions, and audit evidence: `context-health.md`

## Canonical Sources

- Product and setup: `README.md`, `CONTRIBUTING.md`
- Maintainer architecture: `docs/internals/`
- User-facing behavior: `docs/user/`
- Wire contracts: `packages/contracts/`
- Delano workflow: `HANDBOOK.md`, `.agents/`
- Active delivery contracts: `.project/projects/`

## Context Maintenance

Use `manage-context` after material scope, architecture, status, or implementation changes. Record uncertainty explicitly; do not turn planned behavior into claims about shipped behavior.
