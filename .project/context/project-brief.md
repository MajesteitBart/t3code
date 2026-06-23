# Project Brief

## Problem

Users want Pi Agent support in T3 Code without a one-off integration path. GitHub issue #402 asks for Pi in RPC mode, dynamic Pi model discovery, provider-native thinking options, safe availability handling, and clean recovery from startup/send/discovery failures.

Delano is used here to keep that work contract-driven because Pi touches shared contracts, server provider runtime, model selection, web UI, and failure-path tests.

## Target Outcome

Pi can be selected and used as a T3 Code provider only when the configured Pi runtime is available. Pi models and thinking options come from the provider path rather than static fake fallbacks, and Pi turns render without empty intermediate assistant messages.

## Scope Boundaries

In scope:

- Pi settings schema and provider presentation metadata.
- Pi driver registration through the existing provider driver SPI.
- Pi RPC process/session management on the server.
- Pi event mapping into canonical provider runtime events.
- Provider health, availability, dynamic model discovery, and model validation.
- Web provider picker, model picker, and thinking-option selection through existing UI flows.
- Tests for startup failure, send failure, discovery failure, empty intermediate events, duplicate lifecycle events, and unavailable runtime behavior.

Out of scope:

- Provider-native server-side slash command parsing.
- Redesigning the provider UX.
- Fake Codex-style approval semantics if Pi does not support them cleanly.
- Importing code directly from the fork reference branch or `.repos/`.
