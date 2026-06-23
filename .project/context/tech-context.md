# Tech Context

## Stack

- TypeScript monorepo managed by pnpm and Vite+ (`vp`).
- Effect-based contracts, services, schemas, and runtime layers.
- Node server in `apps/server`.
- React/Vite web app in `apps/web`.
- Expo mobile client in `apps/mobile`.
- Electron desktop shell in `apps/desktop`.
- Shared contracts in `packages/contracts`.
- Shared runtime helpers in `packages/shared` and `packages/client-runtime`.

## Runtime Constraints

- Root `package.json` requires Node `^24.13.1`; local validation may run under a newer local Node and should report that if it matters.
- Required completion gates for code tasks are `vp check` and `vp run typecheck`.
- Native mobile changes also require `vp run lint:mobile`.
- UI behavior changes require a real browser or device/simulator check.
- Pi support must respect configured binary paths and must not assume `pi` is on `PATH`.

## Integration Points

- `packages/contracts/src/providerInstance.ts`: open driver kind and provider instance routing contracts.
- `packages/contracts/src/settings.ts`: provider settings schemas and server settings patches.
- `packages/contracts/src/model.ts`: model defaults, model aliases, and provider option descriptors.
- `apps/server/src/provider/ProviderDriver.ts`: provider driver SPI.
- `apps/server/src/provider/builtInDrivers.ts`: built-in driver registration.
- `apps/server/src/mcp/McpSessionRegistry.ts`: provider-scoped MCP credential issuing with preview capability.
- `apps/server/src/mcp/McpProviderSession.ts`: thread-scoped MCP endpoint/header handoff to provider adapters.
- `apps/server/src/mcp/toolkits/preview/tools.ts`: T3 in-app browser `preview_*` tool contracts.
- `apps/web/src/components/preview/PreviewAutomationOwner.tsx`: desktop preview owner that handles preview automation requests.
- `apps/server/src/provider/Layers/*Adapter.ts`: canonical runtime event adapters.
- `apps/server/src/provider/Layers/*Provider.ts`: provider health and model snapshot logic.
- `apps/web/src/components/settings/providerDriverMeta.ts`: browser-safe provider metadata.
- `apps/web/src/session-logic.ts` and chat model picker components: provider/model selection presentation.
