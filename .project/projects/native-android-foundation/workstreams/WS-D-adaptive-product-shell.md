---
id: WS-D
name: WS-D Adaptive Product Shell
owner: team
status: active
created: 2026-08-07T13:12:09Z
updated: 2026-08-08T17:05:14Z
operating_mode: multi-stream
---

# Workstream: WS-D Adaptive Product Shell

## Objective

Prove a thin, Android-native Compose presentation layer for direct onboarding, saved environments, and live project/thread shell state across phone and tablet layouts.

## Owned Files/Areas

- Proposed Compose application shell and feature packages for onboarding, environments, and home
- Adaptive navigation, trusted intent/custom-wrapper/paste intake, selection routing, lifecycle-aware state collection, loading/empty/offline/error presentation
- Predictive back, TalkBack semantics, font scaling, light/dark baseline, and UI instrumentation tests
- Foundation-only design tokens and reusable components justified by actual screens

## Dependencies

- WS-A application shell, identity, and Compose toolchain.
- WS-C environment/state APIs and lifecycle behavior.
- WS-B error categories that can be mapped to user-visible recovery.

## Risks

- UI may accidentally own transport/retry or derive stale parallel state.
- Phone-first navigation can fail on tablet expanded widths.
- High-frequency snapshot collection can cause recomposition and GPU regressions.
- Copying SwiftUI controls mechanically can produce non-native Android behavior.
- Conflating socket connectivity with data freshness can hide usable HTTP-refreshed state behind a false offline wall.

## Handoff Criteria

- Direct pairing and saved-environment flows accept canonical QR-wrapper text and expose layered recovery, including platform-conditional local-network denial, without leaking credential details.
- Live project/thread rows route to the correct environment with collision-safe keys.
- Phone and tablet shells cover loading, empty, offline, reconnecting, revoked, and error states.
- Reconnecting may display fresh HTTP state; failed passive environments retain last-known rows with visible reachability.
- Predictive back, TalkBack, font scaling, and representative recomposition/performance checks pass.
- Composables depend on stable state/actions, not protocol or persistence implementations.
