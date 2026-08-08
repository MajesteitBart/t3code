# Project Overview

## Mission

T3 Code is an open control surface for coding-agent providers. One T3 server owns provider processes, workspaces, terminals, source control, durable orchestration state, and authenticated RPC; clients let users supervise that work locally or remotely without sacrificing responsiveness.

## Client Surfaces

- `apps/web`: browser client, also wrapped by `apps/desktop`.
- `apps/mobile`: React Native client for Android and iOS.
- `apps/swift-ios`: separate native SwiftUI client introduced by PR #5178.
- Planned native Android client: a separate Kotlin/Jetpack Compose counterpart to `apps/swift-ios`, not a rewrite of the React Native Android client.

## Delivery Scopes

- `active-chat-controls`: pre-existing Delano project with one task marked in progress. Its implementation state has not been audited as part of the native Android setup.
- `native-android-foundation`: the first user-selected Option 1 project, limited to an architecture-proving native Android vertical slice before broader parity work.

## Current Health

- The Delano runtime is installed and the context pack has been converted from placeholders to repository-specific guidance.
- The checked-out source is the experimental SwiftUI PR branch, providing the reference implementation for native-client parity.
- The existing React Native Android client already contains useful Kotlin native modules for terminal, review diff, composer, and controls; reuse boundaries require an explicit architecture task.
- A native Android application does not exist yet. Build commands, package identities, minimum SDK, release ownership, and push-provider scope must be decided before implementation.
