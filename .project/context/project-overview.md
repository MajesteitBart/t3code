# Project Overview

## Mission

T3 Code is an open control surface for coding-agent providers. One T3 server owns provider processes, workspaces, terminals, source control, durable orchestration state, and authenticated RPC; clients let users supervise that work locally or remotely without sacrificing responsiveness.

## Client Surfaces

- `apps/web`: browser client, also wrapped by `apps/desktop`.
- `apps/mobile`: React Native client for Android and iOS.
- `apps/swift-ios`: separate native SwiftUI client introduced by PR #5178.
- `apps/kotlin-android`: the new standalone Kotlin/Jetpack Compose foundation, separately identified from the React Native Android client and not a replacement for it.

## Delivery Scopes

- `active-chat-controls`: pre-existing Delano project with three open tasks. Its implementation state has not been audited as part of the native Android work.
- `native-android-foundation`: the first user-selected Option 1 project, limited to an architecture-proving native Android vertical slice before broader parity work.

## Current Health

- The Delano runtime is installed and the context pack has been converted from placeholders to repository-specific guidance.
- The repository still contains the experimental SwiftUI PR source as a behavioral reference; it is not the canonical Android contract or a shipped client.
- Native Android WS-A, WS-B, and WS-C are done, and WS-E's T-017 disposable real-server harness is complete. D-015 allows later adapter-first review-diff/composer work, defers the Ghostty/JNI terminal pending its native supply-chain gates, and selects Compose-native replacement for Expo-specific controls.
- The native app builds debug/release-shaped variants with collision-safe identities and renders on the supported API-35 phone emulator. Its Kotlin boundary now covers canonical contracts, policy-free HTTP/RPC attempts, protected credentials, Room-backed multi-environment state, active/passive supervision, stale-publication guards, process-death recovery, and stable ambiguous-turn identity. The disposable harness verifies two real isolated T3 servers, durable receipts/events, restart, revocation, and exact teardown. Product shell, accessibility/performance, CI, release, and push-provider scope remain later work.
