# Add OpenClaw provider to T3 Code

## Goal

Add OpenClaw as a first-class provider in T3 Code, alongside `codex`, `claudeAgent`, `cursor`, and `opencode`.

This rollout aims to make OpenClaw selectable anywhere provider-backed sessions are configured, while following the existing provider architecture instead of bolting on a one-off integration.

## Planned scope

### 1. Contracts and shared provider metadata

Update the core contract layer so `openclaw` is a valid `ProviderKind` everywhere it needs to be.

Planned files:

- `packages/contracts/src/orchestration.ts`
- `packages/contracts/src/settings.ts`
- `packages/contracts/src/model.ts`

Planned changes:

- add `openclaw` to `ProviderKind`
- add an `OpenClawModelSelection` variant
- extend `ModelSelection` unions and settings patch schemas
- add default model/display-name metadata for `openclaw`
- add provider settings for OpenClaw gateway connection and custom models

### 2. Server-side provider discovery and health

Add an OpenClaw provider snapshot service so T3 Code can report installation, connectivity, authentication state, and available models in the Settings UI.

Planned files:

- `apps/server/src/provider/Services/OpenClawProvider.ts`
- `apps/server/src/provider/Layers/OpenClawProvider.ts`
- `apps/server/src/provider/builtInProviderCatalog.ts`
- `apps/server/src/provider/Layers/ProviderRegistry.ts`
- `apps/server/src/serverSettings.ts`
- `apps/server/src/server.ts`

Planned changes:

- add OpenClaw settings to the server settings model
- probe the configured OpenClaw gateway
- surface a `ServerProvider` snapshot for `openclaw`
- register OpenClaw in the built-in provider order and registry wiring

### 3. Runtime adapter

Add a dedicated `OpenClawAdapter` that speaks to the OpenClaw gateway over WebSocket/JSON-RPC, following the same provider adapter contract used by the other providers.

Planned files:

- `apps/server/src/provider/Services/OpenClawAdapter.ts`
- `apps/server/src/provider/Layers/OpenClawAdapter.ts`
- `apps/server/src/provider/Layers/ProviderAdapterRegistry.ts`
- `apps/server/src/provider/Layers/ProviderAdapterRegistry.test.ts`

Planned behavior:

- start or resume an OpenClaw-backed session
- send turns and stream canonical runtime events
- handle interrupts, approvals, and structured user input
- stop sessions and support thread read / rollback behavior where possible

## 4. Git text-generation support

T3 Code already routes commit / PR / branch / thread-title generation through provider-specific implementations. Because `ProviderKind` is shared here too, OpenClaw needs a matching text-generation path.

Planned files:

- `apps/server/src/git/Services/TextGeneration.ts`
- `apps/server/src/git/Layers/RoutingTextGeneration.ts`
- `apps/server/src/git/Layers/OpenClawTextGeneration.ts`

Planned changes:

- add `openclaw` to the text-generation provider union
- add a router branch for OpenClaw
- implement a small OpenClaw-backed text-generation layer for Git writing helpers

### 5. Web UI wiring

Expose OpenClaw in the same places where users can choose providers, configure provider settings, and see provider icons/status.

Planned files include:

- `apps/web/src/session-logic.ts`
- `apps/web/src/modelSelection.ts`
- `apps/web/src/composerDraftStore.ts`
- `apps/web/src/components/chat/providerIconUtils.ts`
- `apps/web/src/components/settings/SettingsPanels.tsx`
- related browser/unit tests that assume the current provider list

Planned changes:

- add OpenClaw to provider picker lists and per-provider state
- add an OpenClaw icon mapping
- add settings fields for gateway URL, password/token, and custom models
- make model-selection helpers understand `openclaw`

### 6. Validation

After implementation:

- install dependencies if needed
- run targeted tests for contracts, provider registry, and settings UI
- run the broader build/test flow that is practical in this repo
- do a small end-to-end validation against the local code path, ideally including provider refresh and a real OpenClaw-backed request if the environment allows it

## Implementation notes

- Follow the existing provider architecture already used for `codex`, `claudeAgent`, `cursor`, and `opencode`.
- Reuse patterns from `OpenKnots/okcode` where helpful, especially for the OpenClaw adapter flow, but adapt them to T3 Code’s contracts and runtime event model.
- Prefer a thin, maintainable OpenClaw layer over clever abstraction.
- Keep provider-kind strings exact: `openclaw`, `codex`, `claudeAgent`, `cursor`, `opencode`.
