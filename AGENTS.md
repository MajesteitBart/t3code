# AGENTS.md

## Mission

T3 Code is a very early WIP GUI for coding agents. Good work keeps provider sessions, reconnects, partial streams, remote access, mobile/desktop shells, and failure paths fast, reliable, and predictable.

## First-Turn Workflow

1. Start with `git status --short --branch`, then inspect the files around the requested area before assuming ownership or architecture.
2. Read the relevant source of truth from the map below. Prefer live repo state and tests over model memory or stale notes.
3. For non-trivial Delano work, use the matching `.project/projects/<slug>/` contracts before implementation. For small local fixes, update Delano/context files only when scope, architecture, status, or evidence changes.
4. Make the smallest coherent change that improves correctness and maintainability. Extract shared logic when behavior would otherwise be duplicated across apps or packages.
5. Verify with the required gates and task-specific checks, then report done, partial, or blocked explicitly.

## Task Completion Requirements

- `vp check` and `vp run typecheck` must pass before considering tasks completed.
  - If changing native mobile code, `vp run lint:mobile` must also pass.
- Use `vp test` for the built-in Vite+ test command and `vp run test` when you specifically need the `test` package script.
- For UI changes, run the relevant app and do a real browser or device/simulator check when behavior or layout matters.

## Source of Truth

- `README.md` and `CONTRIBUTING.md`: product status, setup, provider expectations, and contribution bar.
- `docs/README.md`: documentation index.
- `docs/architecture/overview.md`: server, web, orchestration, provider event, and async completion model.
- `docs/architecture/connection-runtime.md`: shared web/mobile connection ownership, retry policy, state boundaries, and platform layering.
- `docs/architecture/providers.md`: WebSocket protocol, provider runtime, push channels, and deterministic worker synchronization.
- `docs/architecture/remote.md` and `docs/cloud/`: T3 Connect, hosted relay, remote access, auth, and endpoint design.
- `docs/reference/workspace-layout.md`, `docs/reference/scripts.md`, and `docs/reference/encyclopedia.md`: repo layout, scripts, and shared vocabulary.
- `docs/operations/ci.md`, `docs/operations/observability.md`, `docs/operations/release.md`, and `infra/relay/README.md`: CI gates, traces, release/deploy workflow, and relay operations.
- `apps/mobile/README.md`: mobile variants, Expo dev-client workflow, native checks, and EAS build notes.
- `HANDBOOK.md`, `.agents/skills/*/SKILL.md`, and `.agents/scripts/pm/`: Delano operating model, stage skills, and PM scripts.
- `.project/context/`: Delano context pack. Treat placeholder text as context debt until updated with repo-specific truth.
- `.project/projects/<slug>/`: active Delano delivery contracts, workstreams, tasks, decisions, and updates.
- `package.json`, `pnpm-workspace.yaml`, and package-level `package.json` files: workspace shape and executable commands.

## Retrieval Index

- Server, provider sessions, orchestration, checkpoints, receipts, and traces: `apps/server`, `packages/contracts`, `packages/shared`, `docs/architecture/overview.md`, `docs/architecture/providers.md`, `docs/operations/observability.md`.
- Web UI, session UX, conversation rendering, client state, and browser transport: `apps/web`, `packages/client-runtime`, `docs/architecture/connection-runtime.md`.
- Mobile UI, native modules, Expo variants, review diff, terminal, notifications, and native checks: `apps/mobile`, `apps/mobile/README.md`, `packages/client-runtime`, `docs/architecture/connection-runtime.md`.
- Desktop shell, packaged backend, updater, app assets, and release artifacts: `apps/desktop`, `docs/reference/scripts.md`, `docs/operations/release.md`.
- Relay, remote access, T3 Connect auth, managed endpoints, queues, APNs, and Cloudflare/Alchemy deployment: `infra/relay`, `docs/architecture/remote.md`, `docs/cloud/`, `docs/operations/relay-observability.md`.
- Contracts and protocol schemas: `packages/contracts`; keep this package schema-only and free of runtime logic.
- Shared runtime utilities: `packages/shared`; use explicit subpath exports such as `@t3tools/shared/git` or `@t3tools/shared/DrainableWorker`; no barrel index.
- Shared web/mobile runtime: `packages/client-runtime`; use explicit public subpaths and keep connection policy out of React components.
- ACP and Codex app-server protocol packages: `packages/effect-acp` and `packages/effect-codex-app-server`; regenerate schemas with the package scripts when protocol inputs change.
- SSH, Tailscale, and remote connectivity helpers: `packages/ssh`, `packages/tailscale`, and the remote/connection docs.
- Marketing site and public pages: `apps/marketing`.

## Project Priorities

1. Performance first.
2. Reliability first.
3. Keep behavior predictable under load and during failures: session restarts, reconnects, partial streams, provider exits, failed subscriptions, and slow external processes.

If a tradeoff is required, choose correctness and robustness over short-term convenience.

Long-term maintainability is a core priority. Prefer existing patterns, shared modules, and repo-local helper APIs over local one-off logic. Do not take shortcuts by duplicating behavior that belongs in a package or shared runtime layer.

## Package Roles

- `apps/server`: Node.js WebSocket server. Wraps provider runtimes, serves the web app, and manages provider sessions.
- `apps/web`: React/Vite UI. Owns browser UX, conversation/event rendering, and client-side state. Connects through the shared runtime and WebSocket APIs.
- `apps/desktop`: Electron shell. Spawns a desktop-scoped backend and loads the shared web app.
- `apps/mobile`: Expo/React Native client. Shares runtime logic with web while owning native shell, terminal, review, notification, and mobile navigation surfaces.
- `apps/marketing`: public marketing/download pages.
- `infra/relay`: T3 Connect hosted relay on Cloudflare/Alchemy.
- `packages/contracts`: shared Effect Schema and TypeScript contracts only; no runtime logic.
- `packages/shared`: shared runtime utilities with explicit subpath exports; no barrel index.
- `packages/client-runtime`: shared runtime package for web and mobile connection, RPC, state, relay, and domain operations.
- `packages/effect-acp` and `packages/effect-codex-app-server`: generated/typed protocol clients and schemas.
- `packages/ssh` and `packages/tailscale`: remote connectivity helpers.

## Delano Workflow

Use the full Delano flow for features, contract changes, or material improvements:

1. Discovery: define measurable outcome in `spec.md` with `discovery-skill`.
2. Prototype Probe: run only when uncertainty is high; fold findings back into the spec.
3. Planning: capture architecture, rollout, rollback, and test strategy in `plan.md`.
4. Breakdown: create atomic tasks with binary acceptance and acyclic dependencies.
5. Synchronization: reconcile Linear/GitHub state when tracker state is involved.
6. Execution: work dependency-safely inside workstream boundaries and record evidence in `updates/`.
7. Quality: run risk-based checks and verify acceptance before closure.
8. Closeout: compare against the outcome, update memory/context, and close the loop.

Useful commands:

- `delano onboarding --approve-agents-analysis`: review `AGENTS.md`; it never edits.
- `delano status --brief`: inspect project status.
- `delano next -- --all`: inspect available next tasks.
- `delano validate`: validate Delano contracts.

## Reference Repos and Vendored Code

- Open-source Codex repo: https://github.com/openai/codex
- Codex-Monitor: https://github.com/Dimillian/CodexMonitor

Use these as implementation references when designing protocol handling, UX flows, and operational safeguards.

This project vendors external repositories under `.repos/` as read-only reference material for coding agents.

- Prefer examples and patterns from vendored source over generated guesses or web search results.
- Do not edit files under `.repos/` unless explicitly asked.
- Do not import from `.repos/`; application code must import from normal package dependencies.
- Manage vendored subtrees with `bun run sync:repos`; use `bun run sync:repos --repo <id>` to sync one configured repository.
- When updating a dependency with a configured vendored subtree, sync that subtree in the same change so `.repos/` matches the installed dependency version.
- When writing Effect code, read `.repos/effect-smol/LLMS.md` first and inspect `.repos/effect-smol/` for idiomatic usage, tests, module structure, and API design.
- When writing relay infrastructure code with Alchemy, inspect `.repos/alchemy-effect/` for idiomatic usage, tests, module structure, and API design.

## Safety

- Do not run destructive filesystem or git operations without explicit approval.
- Preserve user changes in the worktree; never reset or revert unrelated edits.
- Confirm before outbound/public actions such as publishing releases, deploying, pushing branches, opening PRs, or posting externally.
- Keep secrets out of logs, commits, docs, and screenshots.
