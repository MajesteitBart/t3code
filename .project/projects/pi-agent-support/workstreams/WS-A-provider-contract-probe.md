---
id: WS-A
name: Provider Contract Probe
owner: T3 Code maintainers
status: active
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
operating_mode: feature
---

# Workstream: Provider Contract Probe

## Objective

Ground Pi support in the current T3 Code provider architecture and confirm the concrete Pi RPC contract before implementation.

## Owned Files/Areas

- `.project/projects/pi-agent-support/spec.md`
- `.project/projects/pi-agent-support/plan.md`
- `.project/projects/pi-agent-support/decisions.md`
- `packages/contracts/src/settings.ts`
- `packages/contracts/src/model.ts`
- `apps/web/src/components/settings/providerDriverMeta.ts`

## Dependencies

- Issue #402.
- Fork PR reference.
- Current provider contract and settings code.

## Risks

- The fork reference may assume older provider-kind architecture.
- Pi RPC may require a narrower v1 than the issue requests.

## Handoff Criteria

- Probe findings are recorded.
- Settings/contract changes are ready to implement.
- Downstream tasks have concrete Pi protocol decisions.
