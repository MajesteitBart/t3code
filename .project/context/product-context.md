# Product Context

## Users

- Developers using T3 Code locally through web, desktop, or mobile shells.
- Maintainers adding and hardening provider integrations.
- Users with multiple provider accounts or custom provider instances.
- Remote/mobile users who depend on stable connection and provider state.

## Core Flows

- Configure provider instances in settings.
- See only usable providers in picker flows.
- Select a model and provider-native options before sending a turn.
- Start or resume a provider-backed thread.
- Send, interrupt, and recover turns without poisoning provider state.
- Render assistant text, reasoning, tool activity, plans, approvals, and user-input requests coherently.

## Constraints

- Prefer reliability and predictable failure behavior over broad scope.
- Do not invent a Pi-only frontend flow.
- Do not hard-code fake Pi fallback models.
- Do not expose synthetic Pi thinking levels.
- Do not assume Pi is installed or authenticated on every machine.
- Do not leave half-initialized sessions after startup, send, model-switch, or spawn failures.
