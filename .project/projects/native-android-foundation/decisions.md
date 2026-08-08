---
name: Native Android Client Foundation
slug: native-android-foundation
owner: team
created: 2026-08-07T13:09:35Z
updated: 2026-08-08T10:32:44Z
---

# Decisions: Native Android Client Foundation

## Active Decisions

### D-001 — Build a side-by-side native Android client

- Decision: implement user-selected Option 1 as a separate Kotlin/Compose application. Keep React Native Android supported and installed independently.
- Rationale: this is the architectural equivalent of the additive SwiftUI PR and permits an evidence-based native experiment without forcing a migration.
- Consequence: protocol and state behavior are intentionally reimplemented, so fixture conformance and lifecycle tests are mandatory.

### D-002 — Deliver a bounded foundation before feature parity

- Decision: the first project ends after direct pairing, canonical transport, persistent multi-environment state, an adaptive live shell, and reliability evidence.
- Rationale: this is the smallest vertical slice that can falsify the native-client architecture before a PR-sized port accumulates.
- Consequence: chat, workspace, T3 Connect, FCM, widgets, share, shortcuts, and release each remain later scope.

### D-003 — Keep contracts canonical in TypeScript

- Decision: `packages/contracts` remains the only wire-contract authority. Kotlin uses checked conformance fixtures and explicit models.
- Alternative rejected for foundation: a general Kotlin code generator, because generator ownership and Effect-schema translation would add machinery before drift is measured.
- Revisit trigger: repeated fixture/model drift across three or more contract changes.

### D-004 — Use minimal native boundaries

- Decision: begin with `app`, `core-protocol`, `core-data`, and `core-testing`; keep UI features as packages until a real build/ownership reason justifies modules.
- Rationale: separate security/persistence/protocol test boundaries without producing a module per screen.

### D-005 — Own retries in environment supervisors

- Decision: transport attempts do not retry themselves. Per-environment state owners use one active/passive policy: only the active environment owns live WebSocket subscriptions; passive environments use bounded, low-frequency HTTP refresh. The supervisor layer owns backoff, reachability, reconnect, resubscription, active-session replacement, and lifecycle release.
- Rationale: duplicate retry layers are a primary cause of duplicate subscriptions, commands, and stale state.
- Consequence: passive read failures retain last-known rows and only update reachability. Fresh HTTP state while the active socket reconnects is presented as current data with a `reconnecting` source state. Timing/backoff values are configurable and deterministically tested rather than copied as immutable Swift constants.

### D-006 — Separate secrets from state

- Decision: Android Keystore-backed storage owns access credentials; Room owns non-secret environment metadata and last-known state. Pairing credentials are exchanged and discarded. Save writes the exchanged credential before the catalog and compensates on catalog failure; removal writes the catalog before credential deletion and restores the catalog on deletion failure.
- Rationale: deletion, corruption, backup, and migration semantics differ between secrets and ordinary application state.
- Consequence: the foundation credential envelope has an explicit, fail-closed direct-bearer discriminator. DPoP binding fields and managed-credential migrations are added only by the later T3 Connect project.

### D-007 — Prefer Android-native interaction outcomes

- Decision: use Compose adaptive navigation, predictive back, WorkManager/Glance/notifications in later projects, and Android accessibility conventions rather than mechanically copying SwiftUI controls.

### D-008 — Use layered failures and preserve diagnostic context

- Decision: pairing, HTTP authorization, and RPC expose distinct typed failures with shared recovery semantics rather than one flat iOS-shaped enum. Preserve HTTP status, structured reason/message, and `traceId` where present.
- Required categories: malformed input; platform-conditional local-network permission denial; unreachable; timeout; cancelled; server/authorization rejection; transport; RPC `connectionUnavailable` (provably unsent), `disconnected` (sent/session lost), response timeout, remote failure, and protocol violation.
- Consequence: direct-bearer HTTP or ticket-mint 401 marks only that environment revoked/re-pair-required and does not loop. A future managed-auth adapter may select or refresh a credential and retry at most once.

### D-009 — Resolve ambiguous commands from state before replay

- Decision: preserve `{commandId, messageId, createdAt}` for a logical turn. After a sent response is lost, fetch a fresh thread snapshot and treat the matching `messageId` as committed; if the commit cannot be confirmed, surface ambiguity and keep the same identity for a later explicit retry.
- Rationale: the Swift reference correctly avoids blind replay, while the canonical server also persists receipts keyed by `commandId`. Snapshot verification is the client recovery contract; server receipt/domain-event evidence remains the backstop proof.
- Consequence: transport never replays sent unary calls. Bootstrap/worktree-specific partial recovery is deferred with the new-task/chat project.

### D-010 — Separate trusted route injection from arbitrary pairing input

- Decision: arbitrary direct server pairing URLs remain valid when pasted or carried inside the canonical custom-scheme wrapper. Register verified Android App Links only for explicitly owned T3 web hosts backed by Digital Asset Links; do not claim broad `http/https` hosts.
- Rationale: domain trust governs which web origins may inject an app route, not which user-supplied T3 servers may be paired.
- Consequence: target-SDK local-network permissions and owned-host App Link availability are resolved in T-001/T-002. Foundation accepts QR payload text; CameraX scanning remains follow-on scope.

## Provisional Decisions

- Application directory: `apps/kotlin-android`.
- Release-shaped application ID: `com.t3tools.t3code.compose`; development suffix: `.dev`.
- Development display name: `T3 Compose Dev`; release-shaped display name remains undecided until product positioning is approved.
- Constructor composition is preferred over a dependency-injection framework during foundation.
- Kotlin serialization, OkHttp, Room, and coroutines/Flow are the default libraries pending toolchain compatibility checks.
- The selected networking stack must document transparent HTTP compression and WebSocket per-message-deflate behavior; no negotiation-inspection capability is assumed before T-001 evidence.

These values are not release commitments. T-001 must confirm or replace them with evidence.

## Superseded Decisions

- None.

## Open Decision Questions

- What are the supported minimum, target, and compile SDK levels?
- Does the selected target require `ACCESS_LOCAL_NETWORK`, and how will Android 16 opt-in plus Android 17 enforcement be tested?
- Which Android Gradle Plugin, Gradle, Kotlin, Compose BOM, and Java versions align with current CI and native libraries?
- Should the directory/application identity use `kotlin-android`, `compose-android`, or a product-specific name?
- Are contract fixtures checked in, generated during a focused command, or both?
- Can existing native modules be extracted without depending on Expo-generated build state?
- Which CI runner/emulator and physical-device class define the initial performance bar?
- Which T3-owned domains can publish Digital Asset Links for each build identity/signing certificate?
- Can the selected WebSocket implementation expose extension negotiation, or will a compressed round trip be the conformance proof?
- Does FCM/relay support follow immediately after foundation or after core chat/workspace parity?
- Who owns signing, Clerk, FCM, Play Console, and release operations for later projects?
