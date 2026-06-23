# GUI Testing Policy

## Enforcement Mode

Required for UI behavior or layout changes.

## Smoke Routes

- Provider settings page for adding/editing provider instances.
- Chat provider picker and model picker.
- Composer option controls for provider-native model options.
- Chat timeline during a provider turn with tool/planning/intermediate events.
- Error states for unavailable or disabled providers.

## Console Filtering

Blocking:

- runtime exceptions;
- contract decode failures;
- failed WebSocket request/response cycles caused by the change;
- repeated provider snapshot or model discovery errors for inactive providers.

Non-blocking only when documented:

- expected provider-unavailable messages for missing local binaries;
- known development-server warnings unrelated to the change.

## Evidence Requirements

- Browser screenshot or concise notes for changed settings/picker surfaces.
- Console/network summary if provider selection or turn sending is affected.
- End-to-end note showing unavailable Pi does not break other providers.

## Design Validation Threshold

Pi should appear as a normal provider instance, not as a special-case mode. Empty model lists, unavailable runtime states, and thinking options should fit existing settings and model-picker patterns.
