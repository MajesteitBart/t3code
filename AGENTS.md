# AGENTS.md — T3 Code

## Mission

Keep T3 Code an open, fast, remote-ready control surface for coding agents across web, desktop, React Native mobile, and native mobile clients. Prefer the smallest system that makes correct behavior unsurprising.

## First-Turn Workflow

1. Read `.project/context/README.md`, then the active Delano project under `.project/projects/` and only the source-of-truth material relevant to the task.
2. Run `git status --short --branch`, `delano status --open --brief`, and the smallest relevant diagnostic before editing.
3. For material delivery work, work only from a ready or in-progress Delano task and keep task evidence plus `.project/context/progress.md` current. Small local fixes may use the compact workflow below.
4. Inspect existing code and preserve unrelated or user-owned changes. Do not spawn subagents unless the user explicitly asks for them.
5. Make the smallest coherent change, run focused checks while iterating, and report the result as `done`, `partial`, or `blocked`.

## Source of truth

- `HANDBOOK.md` and `.agents/`: Delano operating model, skills, validation runtime, and evidence rules.
- `.project/context/README.md`: retrieval index for durable repository and product context.
- `.project/projects/<slug>/`: active delivery contracts (`spec.md`, `plan.md`, `decisions.md`, `workstreams/`, `tasks/`, and `updates/`).
- `README.md` and `CONTRIBUTING.md`: product entrypoint, setup, and contribution expectations.
- `docs/user/`: shipped-product behavior; `docs/internals/`: architecture and contributor guidance; `docs/operations/`: runbooks.
- `docs/internals/glossary.md`: canonical T3 terminology.
- `packages/contracts/`: wire schemas. A schema change requires explicit decisions for the server, web, desktop, React Native mobile, and SwiftUI mobile implementations as applicable.
- `.repos/`: vendored, read-only references. Prefer their established patterns; never edit or import from them. Use `vpr sync:repos` when intentionally updating the matching dependency.

Retrieval hints:

- Delivery lifecycle → `HANDBOOK.md` and the matching `.agents/skills/*/SKILL.md`.
- Current scope and acceptance → the active project's `spec.md`, `plan.md`, workstream, and task files.
- Architecture → `docs/internals/overview.md`, `.project/context/system-patterns.md`, and the active plan.
- Repository layout and commands → `.project/context/project-structure.md` and `.project/context/tech-context.md`.
- UI/client validation → `.project/context/gui-testing.md` and the relevant client testing skill.
- Current evidence and handoff state → `.project/context/progress.md` and project `updates/`.

## Delano workflow

Delano context lives in `.project/context/`; delivery contracts live in `.project/projects/`. Keep them current when scope, architecture, status, evidence, or product direction changes. Prefer the Delano CLI for lifecycle/frontmatter changes so rollups stay consistent.

Use the full flow for features, contract changes, or material improvements:

1. **Discovery**: define and approve a measurable outcome in `spec.md` with `discovery-skill`.
2. **Prototype Probe**: time-box only when material uncertainty blocks a safe spec; write findings back to the spec.
3. **Planning**: capture architecture, milestones, rollout, and rollback in `plan.md` with `planning-skill`.
4. **Breakdown**: create atomic tasks with binary acceptance and acyclic dependencies using `breakdown-skill`.
5. **Synchronization**: reconcile Linear or GitHub only when tracker state is involved, using `sync-skill` and explicit approval for external mutations.
6. **Execution**: work dependency-safe tasks within workstream boundaries and record evidence in `updates/` with `execution-skill`.
7. **Quality Ops**: run risk-based checks and verify acceptance before closure with `quality-skill`.
8. **Closeout**: compare delivery to the outcome, update project memory, and close the loop with `closeout-skill`.

For a small local fix: inspect current state, retrieve the relevant source of truth, make the smallest coherent change, verify narrowly, and update Delano artifacts only when scope, architecture, status, or evidence materially changed.

Common commands:

```bash
delano help
delano status --open --brief
delano validate
delano next -- --all
delano project create <slug> --name "<name>" --owner <owner>
delano project show <slug> --json
delano project start|close|block|defer|update <slug> --reason "<text>"
delano workstream add <project-slug> <WS-ID> --name "<name>" --owner <owner>
delano task add <project-slug> <T-ID> --name "<name>" --workstream <WS-ID>
delano task open|start|close|block|defer|update <project-slug> <T-ID> --reason "<text>"
delano update add <project-slug> --message "<text>" --task <T-ID> --stream <WS-ID>
delano research <project-slug> <research-slug> --title "<title>" --question "<question>" --json
```

Use `--evidence "<text>"` when closing tasks and `--message "<text>"` for task updates. If intent remains unclear, record research or open questions before changing executable contracts.

## Model selection for workflows and subagents

- Do not spawn subagents unless the user explicitly mentions their use. This rule overrides workflow convenience or parallelism.
- When subagents are explicitly authorized, assign bounded ownership, preserve other agents' changes, and use only models available in the active harness.
- Choose stronger reasoning for architecture, protocol, security, and review work; choose lower-cost execution only for clear, mechanical tasks. Judge output quality rather than model labels.
- User-facing UI, copy, motion, and API design require deliberate taste review in addition to correctness.

## Commands

```powershell
vp i
vp run dev
vp run dev:server
vp run dev:web
vp test run <focused-test-files>
vp run --filter <package> typecheck
vp lint <focused-paths>
vp fmt --check <focused-paths>
delano status --open --brief
delano validate
```

- Do not run repo-wide `vp check`, `vp run -r test`, or `vp run -r typecheck` unless the user asks; CI owns the full suite.
- Read actual dev ports and pairing URLs from the `[dev-runner]` line. Never set `VITE_HTTP_URL` or `VITE_WS_URL` for development; Vite proxies `/api`, `/ws`, `/oauth`, and `/.well-known` on one origin.
- The web app requires pairing. Hand over the pairing URL including its token, never a bare origin.
- Worktree state defaults to the worktree's gitignored `.t3`, which intentionally outranks ambient `T3CODE_HOME`. An explicit `--home-dir` still wins.

## Architecture rules

T3 Code has distinct clients and connection modes:

- `apps/server`: WebSocket orchestration, providers, checkpointing, and event-sourced command handling. Read `.repos/effect-smol/LLMS.md` before writing Effect code.
- `apps/web`: React/Vite client; `apps/desktop` wraps it with Electron and IPC behavior.
- `apps/mobile`: React Native client for iOS and Android.
- `apps/swift-ios`: separate native SwiftUI client with its own UI, persistence, and identities.
- `apps/marketing`: public website.
- `packages/client-runtime`: TypeScript client logic shared by web and React Native mobile; it does not automatically reach SwiftUI.
- `packages/contracts`: typed wire contracts; keep heavy runtime logic elsewhere.
- `packages/shared`: shared runtime utilities through subpath exports; no barrel exports.

Keep these product principles intact:

- **Open at the core**: architecture and implementation remain understandable and forkable.
- **Performance without compromise**: avoid oversized WebSocket payloads, continuously repainting animations, GPU-heavy effects, and expensive list rendering. Users notice dropped frames, stale labels, and lying spinners.
- **Remote ready**: support local network, remote/relay, tunnel, multi-device, and multi-environment behavior deliberately.
- **Multi-surface**: decide explicitly which of web, desktop, React Native mobile, and SwiftUI mobile a change affects.

Before completing user-facing or provider-shaped work, check:

- Entry points: chat, Settings, command palette, and keybindings where applicable.
- Providers: Codex, Claude, Cursor, Grok, and OpenCode; complexity belongs at adapter boundaries.
- Contracts: schema, server, and every applicable client.
- Reverse states: every action has an exit and visible current state.
- Connection modes: local, relay, tunnel, multi-device, and multi-environment cases.
- Docs: user-visible behavior in `docs/user/`, architecture in `docs/internals/`, runbooks in `docs/operations/`, and vocabulary in the glossary.

Prefer inferred types over annotations; never introduce `any` casually. Comments explain how a function or abstraction is used, not each line of behavior.

## Quality and safety

- Never kill processes by name, path, worktree string, `pkill -f`, or matched PID. Stop only a PID captured at spawn, or a confirmed port owner whose process working directory is this worktree.
- `~/.t3/userdata` is live developer state. It may be read or copied but never served, opened read-write, symlinked, or cleaned. Test data flows one way into worktree-local `.t3/userdata`.
- Snapshot a live SQLite source with `VACUUM INTO`; do not plain-copy an active database without its WAL/SHM siblings.
- Preserve unrelated working-tree changes. Avoid destructive git/filesystem commands and recoverable-data loss.
- Do not expose secrets, credentials, tokens, pairing codes, or private user data in logs, docs, evidence, commits, issues, or PRs.
- Backend behavior changes require focused tests. Event-sourced async tests wait on typed receipts and worker drains, never arbitrary sleeps or polling timeouts.
- User-visible frontend validation should cover each affected surface. Only run browser/computer-use validation when the user explicitly agrees or asks; use `test-t3-app` for web and `test-t3-mobile` for the selected React Native or SwiftUI path.
- Stop every server or helper you start using the exact PID you captured.

## Git and evidence

- Work on the current branch unless the user directs otherwise. Commits, pushes, PRs, rebases, releases, and public mutations require explicit user authorization.
- Never open a PR unless explicitly asked. Before a requested PR, rebase onto current main, keep one concern per PR, and use a conventional plain-language title.
- UI PRs require before/after images; motion or timing changes require a short video.
- Before any requested commit: run `git diff --check`, focused risk-appropriate tests/builds, `delano validate`, and `git status --short`.
- A task closes only when its binary acceptance criteria are checked and concrete command or artifact evidence is recorded.
- Report verification honestly, including checks skipped because the environment, platform, permission, or scope made them unavailable.
