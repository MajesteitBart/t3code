---
id: WS-C
name: Web Provider UX
owner: T3 Code maintainers
status: active
created: 2026-06-23T10:27:50Z
updated: 2026-06-23T10:27:50Z
operating_mode: feature
---

# Workstream: Web Provider UX

## Objective

Represent Pi in existing settings, provider picker, model picker, and composer option flows without a Pi-only frontend path.

## Owned Files/Areas

- `apps/web/src/components/settings/providerDriverMeta.ts`
- `apps/web/src/session-logic.ts`
- `apps/web/src/components/chat/ProviderModelPicker.tsx`
- `apps/web/src/components/chat/ModelPickerContent.tsx`
- `apps/web/src/components/chat/providerIconUtils.ts`
- `apps/web/src/components/chat/MessagesTimeline.logic.ts`

## Dependencies

- `WS-A` Pi settings and option descriptor decisions.
- `WS-B` provider snapshot and model discovery behavior.

## Risks

- Empty intermediate Pi steps can leak as empty assistant messages.
- Fake fallback models would make unavailable Pi look usable.

## Handoff Criteria

- Pi UI behavior follows existing provider patterns.
- Unavailable Pi does not break existing providers.
- Browser verification evidence exists for changed UI flows.
