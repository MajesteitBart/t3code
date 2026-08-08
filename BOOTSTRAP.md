# T3 Code Delano Bootstrap

## Source Brief

This repository was retrofitted with Delano from four inputs:

1. The existing T3 Code repository, documentation, and repo-local `AGENTS.md`.
2. A user-supplied external `AGENTS.md` template whose section structure and delivery workflow were adapted to T3 Code.
3. GitHub PR #5178, checked out locally as the standalone SwiftUI native-client reference.
4. The user's explicit selection of Option 1: build a separate native Android client rather than only closing React Native Android parity gaps.

## Setup Goal

- Preserve T3 Code's open, performance-sensitive, remote-ready, multi-client operating rules.
- Install the Delano runtime without overwriting repository-owned `.project` state.
- Replace generic context placeholders with trustworthy repository context.
- Create and fully decompose the first bounded native Android project.
- Leave implementation tasks planned until maintainers review and open them.

## Read First

1. `AGENTS.md`
2. `.project/context/README.md`
3. `.project/projects/native-android-foundation/spec.md`
4. `.project/projects/native-android-foundation/plan.md`
5. The relevant workstream and task before implementation
6. `docs/internals/overview.md`, `packages/contracts/`, and the applicable client code for architecture-sensitive changes

## Repository and Remotes

- Existing fork remote: `origin` (`MajesteitBart/t3code`).
- Parent remote: `upstream` (`pingdotgg/t3code`).
- Repository creation, visibility, ownership, collaborators, releases, and deployments were not changed during bootstrap.
- The setup was performed on `pr-5178-swift-ios`; no commit, push, rebase, or PR is implied by this file.

## Delano Installation

Delano CLI `0.4.0` was used in update-safe mode:

```powershell
npx -y @bvdm/delano@latest install --target . --no-project-state --force --yes
npx -y @bvdm/delano@latest onboarding --approve-agents-analysis
```

`--no-project-state` is required for upgrades because `.project/context`, `.project/projects`, and `.project/registry` are repository-owned after first installation.

### Local compatibility patches

The installed payload needed three narrow fixes for this large Windows monorepo with root `"type": "module"`:

- `.agents/scripts/check-text-safety.mjs` raises the `git ls-files` buffer and excludes read-only vendored `.repos/` content.
- `.agents/src/cli/package.json` marks the local CLI helper subtree as CommonJS.
- `.agents/scripts/check-log-safety.sh` requires the Claude compatibility copy only when `.claude/common` exists, not when the repo only has the tracked `.claude/skills` pointer.

A future forced Delano upgrade can overwrite these files. Re-run `delano validate`, confirm whether upstream Delano has incorporated equivalent fixes, and reapply only if the failures recur.

## Context Workflow

Use `manage-context` when scope, architecture, delivery state, testing policy, or evidence changes. Start with `.project/context/README.md`; update facts supported by code/contracts/evidence and list uncertainty plainly.

The current context pack captures:

- product mission, users, and critical flows;
- server/client architecture and wire-contract authority;
- repository boundaries, commands, testing, and safety constraints;
- the SwiftUI PR source material and native Android decision;
- current progress, contradictions, and open evidence gaps.

## Delano Projects

### Existing project preserved

`active-chat-controls` predates this bootstrap and has one task marked in progress. Its implementation/evidence state was not audited or changed.

### First native Android project

`native-android-foundation` is the first active Option 1 project. It proves the architecture with:

- a separate Kotlin/Compose application and identity;
- canonical contract fixtures and direct pairing;
- authenticated HTTP/Effect RPC WebSocket transport;
- Keystore/Room persistence and multi-environment supervision;
- an adaptive onboarding/home shell;
- process-death, reconnect, idempotency, accessibility, performance, and CI evidence.

It contains five workstreams and 22 atomic planned tasks. Full chat, workspace, T3 Connect, FCM, widgets, share/shortcuts, and release parity remain follow-on projects and must not be smuggled into foundation tasks.

## Validation

Run focused delivery checks:

```powershell
npx -y @bvdm/delano@latest status --open --brief
npx -y @bvdm/delano@latest next -- --all
npx -y @bvdm/delano@latest validate
git diff --check
git status --short --branch
```

Do not run repo-wide tests or typechecks for documentation/project-contract changes. Native Android implementation must add its own focused Gradle checks before closing the first implementation task.

## Completion Evidence for Bootstrap

- `AGENTS.md` uses the supplied Delano structure with T3 Code-specific rules.
- Delano runtime installation reports successful update-safe installation.
- Context audit reports no missing required context or placeholder debt.
- `native-android-foundation` has an active spec/plan, five workstreams, 22 planned tasks, binary acceptance criteria, estimates, and an acyclic dependency graph.
- Full `delano validate` completed with zero errors and one expected dirty-worktree warning; no task is opened merely to make `delano next` return work.
