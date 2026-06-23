---
id: WS-B
name: Server Runtime And Adapter
owner: T3 Code maintainers
status: active
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
operating_mode: feature
---

# Workstream: Server Runtime And Adapter

## Objective

Implement Pi RPC lifecycle, health/model discovery, driver registration, and canonical event mapping inside the existing server provider stack.

## Owned Files/Areas

- `apps/server/src/provider/Drivers/PiDriver.ts`
- `apps/server/src/provider/Layers/PiAdapter.ts`
- `apps/server/src/provider/Layers/PiProvider.ts`
- `apps/server/src/provider/builtInDrivers.ts`
- `apps/server/src/provider/Services/*`
- `packages/contracts/src/providerRuntime.ts`

## Dependencies

- `WS-A` probe and contract decisions.

## Risks

- Session cleanup failures can poison provider state.
- Pi multi-stage events can produce duplicate lifecycle events or empty assistant messages.

## Handoff Criteria

- Server tests cover runtime lifecycle and failure cleanup.
- Pi snapshots reflect availability and dynamic models.
- Pi adapter emits canonical runtime events.
