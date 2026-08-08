# Project Structure

## Delivery Boundaries

- `AGENTS.md`: compact, persistent operating rules and retrieval map.
- `HANDBOOK.md`: Delano operating model and lifecycle contract.
- `.project/context/`: durable repository/product context; not an execution backlog.
- `.project/projects/`: specs, plans, decisions, workstreams, tasks, and updates for delivery scopes.
- `.agents/`: installed skills, scripts, schemas, rules, hooks, and fixtures.
- `.codex/`: repository Codex configuration and hook registration.
- `.delano/`: optional legacy/local viewer assets; the active CLI viewer comes from the installed Delano package.
- `.claude/`: compatibility surface when present, never a second authored source of truth.

## Product and Runtime Areas

- `apps/server`: server runtime and provider orchestration.
- `apps/web`: browser UI.
- `apps/desktop`: Electron wrapper and desktop behavior.
- `apps/mobile`: React Native iOS/Android client and existing platform-native modules.
- `apps/swift-ios`: standalone native SwiftUI client.
- Proposed `apps/kotlin-android`: working location for the standalone native Android client; final naming is an explicit project decision.
- `packages/contracts`: wire contracts.
- `packages/client-runtime`: nonvisual TypeScript client behavior for web and React Native.
- `packages/shared`: small shared runtime utilities.
- `infra/relay`: relay infrastructure and push delivery.
- `docs/`: user, internal, and operations documentation.
- `.repos/`: vendored read-only reference repositories.

## Documentation Ownership

- User-visible shipped behavior: `docs/user/`.
- Contributor and architecture material: `docs/internals/`.
- Operational procedures: `docs/operations/`.
- New terminology: `docs/internals/glossary.md`.
- App-specific build instructions: each app's README.

## Local-Only and Generated Areas

- `.t3/` is worktree-local runtime/test state and must stay isolated from live T3 home data.
- Build outputs, generated Expo native projects, caches, credentials, signing configuration, and local environment files are not delivery truth.
- Existing untracked files predate this planning effort; do not stage, delete, or rewrite them merely because Delano is now installed.
