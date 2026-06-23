# Project Overview

## Mission

T3 Code is an early WIP GUI for coding agents across web, desktop, mobile, and remote access surfaces. Good changes keep provider sessions, reconnects, partial streams, remote endpoints, and failure paths predictable.

## Active Delivery Scopes

- `pi-agent-support`: add Pi as a first-class provider through the existing provider driver, runtime adapter, model selection, and web UI flows.

## Current Health

- The repo already has multi-provider infrastructure for Codex, Claude, Cursor, Grok, and OpenCode.
- Provider driver kinds are open branded slugs; unknown drivers are expected to degrade as unavailable rather than crash.
- The active Pi project is contract-only at bootstrap time. No Pi runtime implementation has been added in this turn.
- Validation gates for completed code work remain `vp check` and `vp run typecheck`, plus targeted tests and browser checks for UI changes.
