# T3 Code Delano Bootstrap

## Source Brief

This setup is based on the current T3 Code repository state, the repo-local `AGENTS.md`, and the Pi Agent support request from GitHub issue #402:

- https://github.com/pingdotgg/t3code/issues/402
- https://github.com/IgorWarzocha/t3code/pull/1

The issue asks for Pi as a first-class T3 Code provider using Pi RPC mode. The fork PR is reference material only; do not merge or replay it blindly.

## Setup Goal

Keep this checkout ready for agentic implementation with Delano while preserving the public fork's existing repo shape. The first active project is `pi-agent-support`.

## Read First

1. `AGENTS.md`
2. `README.md`
3. `docs/architecture/overview.md`
4. `docs/architecture/providers.md`
5. `docs/architecture/connection-runtime.md`
6. `packages/contracts/src/providerInstance.ts`
7. `packages/contracts/src/settings.ts`
8. `apps/server/src/provider/ProviderDriver.ts`
9. `apps/server/src/provider/builtInDrivers.ts`
10. `.project/context/*.md`
11. `.project/projects/pi-agent-support/spec.md`
12. `.project/projects/pi-agent-support/plan.md`

## Repository And GitHub

- Local checkout: current repository root.
- Origin remote: `https://github.com/MajesteitBart/t3code.git`.
- Upstream remote: `https://github.com/pingdotgg/t3code.git`.
- Do not push, publish, deploy, open PRs, or post GitHub comments without explicit approval.
- Preserve user changes and generated Delano files in the worktree.

## Delano Runtime

- `.agents/` is the canonical Delano runtime.
- `.project/context/` is the handoff context pack.
- `.project/projects/` is the delivery contract source of truth.
- `.claude/` is not required unless a compatibility adapter is intentionally installed later.

Useful commands:

```bash
delano status --brief
delano next -- --all
delano validate
bash .agents/scripts/pm/status.sh --open --brief
bash .agents/scripts/pm/next.sh --all
bash .agents/scripts/pm/validate.sh
```

## Active Project

`pi-agent-support` is the first project to work on. It is decomposed into workstreams and tasks. Only `T-001` starts ready because the implementation should first compare the current provider stack with issue #402 and the fork reference.

Do not start downstream implementation tasks until the probe/audit updates the project with exact Pi RPC protocol decisions, settings schema decisions, and verification fixtures.

## Completion Evidence

Before claiming implementation work complete, capture:

- task evidence in `.project/projects/pi-agent-support/tasks/*.md`;
- progress notes in `.project/projects/pi-agent-support/updates/`;
- Delano status/next-task output;
- `vp check`;
- `vp run typecheck`;
- targeted tests for changed contracts/server/web paths;
- browser verification for provider picker, model picker, and turn rendering if UI behavior changes.
