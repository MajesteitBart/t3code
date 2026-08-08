---
name: Native Android Client Foundation
slug: native-android-foundation
owner: team
created: 2026-08-07T13:09:35Z
updated: 2026-08-08T16:47:00Z
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
- Cancellation consequence: WS-B's typed `CancelledFailure` is also a `CancellationException`. A supervisor checks whether its own coroutine is still active before classifying it: an active typed transport cancellation remains retryable, while genuine lifecycle cancellation is rethrown.

### D-006 — Separate secrets from state

- Decision: Android Keystore-backed storage owns access credentials; Room owns non-secret environment metadata and last-known state. Pairing credentials are exchanged and discarded. Save writes the exchanged credential before the catalog and compensates on catalog failure; removal writes the catalog before credential deletion and restores the catalog on deletion failure.
- Rationale: deletion, corruption, backup, and migration semantics differ between secrets and ordinary application state.
- Consequence: the foundation credential envelope has an explicit, fail-closed direct-bearer discriminator. DPoP binding fields and managed-credential migrations are added only by the later T3 Connect project.
- Cancellation consequence: cross-repository compensation runs in a non-cancellable context, then rethrows genuine lifecycle cancellation unchanged with any rollback failures suppressed.

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
- Implementation: `PreparedStableTurn` freezes the identity and canonical payload, while `StableTurnRecovery` performs fresh-snapshot verification before any explicit resend and retains the original turn when verification is absent or unavailable.

### D-010 — Separate trusted route injection from arbitrary pairing input

- Decision: arbitrary direct server pairing URLs remain valid when pasted or carried inside the canonical custom-scheme wrapper. Register verified Android App Links only for explicitly owned T3 web hosts backed by Digital Asset Links; do not claim broad `http/https` hosts.
- Rationale: domain trust governs which web origins may inject an app route, not which user-supplied T3 servers may be paired.
- Consequence: target-SDK local-network permissions and owned-host App Link availability are resolved in T-001/T-002. Foundation accepts QR payload text; CameraX scanning remains follow-on scope.

### D-011 — Freeze the foundation toolchain on the API 36 compatibility line

- Decision: create the standalone project at `apps/kotlin-android` with `minSdk 24`, `targetSdk 36`, `compileSdk 36`, SDK Build Tools `35.0.0`, Java 17 bytecode/toolchain, Gradle `8.13`, Android Gradle Plugin `8.13.2`, Kotlin/Compose compiler plugin `2.3.21`, Compose BOM `2026.06.01`, Activity Compose `1.13.0`, and Lifecycle Runtime Compose `2.10.0`.
- Rationale: the checked developer environment has Temurin 17.0.19, Android platforms 34/35/36, Build Tools 35.0.0/36.0.0, and an API 35 Google APIs emulator. Expo SDK 56 also supports Android 7+ and compiles/targets API 36, so `minSdk 24` and API 36 preserve compatibility with the existing React Native native-module boundary without making the new app depend on Expo. AGP 8.13.2 is the last pre-built-in-Kotlin line, supports API 36.1, requires Gradle 8.13/JDK 17, and carries the R8 version required for Kotlin 2.3. Sources: `apps/mobile/app.config.ts`, `apps/mobile/modules/*/android/build.gradle`, `.github/workflows/ci.yml`, [Expo SDK 56 platform matrix](https://docs.expo.dev/versions/v56.0.0/), [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [AGP/Kotlin compatibility](https://developer.android.com/build/kotlin-support), and [Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).
- CI consequence: existing repository CI provisions Node/Rust and mobile EAS jobs delegate Android builds to Expo; it does not provision a standalone Android SDK/Gradle job. WS-E must add an explicit native-Android runner before this build becomes a required repository gate.
- Compatibility boundary: Lifecycle `2.11.0` is deliberately excluded because its AAR metadata requires API 37 and AGP 9.1+; `2.10.0` is the newest lifecycle line admitted by this API 36/AGP 8.13 foundation.
- Upgrade trigger: move to AGP 9/built-in Kotlin or target/compile SDK 37 only through a focused compatibility change that also implements and tests the Android 17 local-network runtime-permission flow.

### D-012 — Use collision-safe native Android identities and a narrow route surface

- Decision: the release-shaped application ID is `com.t3tools.t3code.compose`; the development build appends `.dev`. Display names are `T3 Code Compose` and `T3 Compose Dev`. Custom schemes are `t3code-compose` and `t3code-compose-dev`, limited to the `pair` route.
- Rationale: React Native owns `com.t3tools.t3code`, `.dev`, and `.preview`; SwiftUI owns `com.t3tools.t3code.swiftui` and `.swiftui.dev`. Android application-data and credential sandboxes therefore cannot overlap with either client.
- App Links: `app.t3.codes` is T3-owned and is the only current web pairing host candidate, but the repository contains neither a Digital Asset Links publication for the Compose IDs nor approved signing-certificate fingerprints. T-002 therefore registers no `http`/`https` intent filters. App Links stay disabled until that host publishes matching `assetlinks.json`; arbitrary direct servers remain paste input or payloads inside the build-specific custom scheme.
- Cleartext consequence: direct user-supplied LAN endpoints may be HTTP, so the foundation application permits cleartext transport at the application boundary. Pairing/transport tasks remain responsible for explicit input, typed failures, secret redaction, and never treating an arbitrary web origin as a trusted app route.

### D-013 — Pin the foundation networking compatibility surface

- Decision: pin OkHttp/MockWebServer `5.4.0`, kotlinx.coroutines `1.11.0`, and kotlinx.serialization JSON `1.11.0` in the standalone version catalog. HTTP and WebSocket attempts will use the shared OkHttp client with library-level retries disabled; supervisors remain the only retry owners.
- HTTP compression: OkHttp documents transparent gzip response handling. Callers must not add their own unconditional gzip decoding; conformance tests will verify compressed canonical responses. Source: [OkHttp project documentation](https://github.com/lysine-dev/okhttp).
- WebSocket compression: OkHttp 5.4.0 offers `permessage-deflate` and compresses outbound messages at or above its configurable `minWebSocketMessageToCompress` threshold (1024 bytes by default). The public `WebSocketListener.onOpen` receives the HTTP 101 `Response`, so the negotiated `Sec-WebSocket-Extensions` header is observable without internal APIs. T-007/T-021 must still prove a controlled compressed round trip because a header alone does not prove compatible frame encode/decode. Sources: [OkHttp compression setting](https://square.github.io/okhttp/5.x/okhttp/okhttp3/-ok-http-client/-builder/min-web-socket-message-to-compress.html) and OkHttp 5.4.0 `WebSocketListener`/`RealWebSocket` source.
- Fixture consequence: the disposable integration harness keeps retries and redirects disabled and configures zero idle HTTP connections. Each fixture operation remains one attempt, while a typed control/drain interval cannot leave an idle socket eligible for stale reuse.

### D-014 — Exercise Android 16 local-network restrictions without over-declaring release permissions

- Decision: because the foundation targets SDK 36, the release-shaped manifest declares `INTERNET` but does not declare or request `ACCESS_LOCAL_NETWORK`; Android explicitly says apps targeting SDK 36 or lower retain implicit LAN access and must not request the Android 17 permission. The development manifest alone declares `NEARBY_WIFI_DEVICES` with `neverForLocation` so maintainers can opt the dev package into Android 16's `RESTRICT_LOCAL_NETWORK` compatibility change, verify denied TCP behavior, and grant Nearby devices to restore access.
- Android 16 coverage: on an API 36 device, enable `RESTRICT_LOCAL_NETWORK` for `com.t3tools.t3code.compose.dev`, reboot, exercise direct pairing with permission denied, then grant Nearby devices and repeat. T-006/T-014/T-016 own the typed failure, rationale UI, and instrumentation once those layers exist.
- Android 17 coverage: before raising `targetSdk` to 37, add `ACCESS_LOCAL_NETWORK`, a runtime request/rationale and revocation path, and denial/recovery instrumentation on API 37. No unrelated Bluetooth, Wi-Fi discovery, location, or nearby-device permission is added to the release-shaped build. Source: [Android local-network permission guidance](https://developer.android.com/privacy-and-security/local-network-permission).

### D-015 — Adapt only separable Android views; defer coupled terminal and showcase controls

- Audit boundary: T-004 is a read-only reuse decision. No file under `apps/mobile/modules/` moves during foundation, and no existing module becomes a dependency of `apps/kotlin-android`.
- `t3-terminal` — **defer**. Expo coupling is explicit in `android/build.gradle:32`, `T3TerminalModule.kt:3-61`, and the `ExpoView`/`EventDispatcher` shell in `T3TerminalView.kt:15-25`. JNI coupling is material: `GhosttyBridge.kt:5-63` loads two shared libraries and exposes the native surface, while `t3_terminal_jni.cpp:201-376` binds it to the current `expo.modules.t3terminal` JNI names. Resources include four ABI-specific `libghostty-vt.so` files and Meslo font assets loaded by `TerminalCanvasView.kt:35-41`. Toolchain coupling includes the mobile root SDK/NDK values, C++17 with warnings-as-errors, CMake 3.22.1, 16-KiB linker pages (`android/build.gradle:9-26`, `cpp/CMakeLists.txt:1-23`), plus pinned Ghostty revision `9f62873...`, Zig 0.15.2, Android NDK, patches, and four-ABI packaging (`scripts/build-libghostty-android.sh:10-14,84-151`). License coupling is Ghostty MIT plus MesloLGS NF Apache-2.0 and must retain `THIRD_PARTY_NOTICES.md:18-35` and `native/libghostty-vt/LICENSE`. A future terminal project may extract an ordinary Android engine/view library only if it keeps `T3TerminalModule` as a thin Expo adapter and adds a focused React Native check for ABI loading, initial buffer/feed, resize, input events, selection, cleanup, theme, and hardware-key revision before switching consumers.
- `t3-review-diff` — **adapt in a follow-on review project**. The build's only declared dependency is Expo Modules Core (`android/build.gradle:17-19`), and the JS contract is concentrated in `T3ReviewDiffModule.kt:10-75`; however, the 1,429-line implementation currently subclasses `ExpoView` and dispatches Expo events directly (`T3ReviewDiffView.kt:26-33`). There is no JNI, native binary, packaged resource, or third-party runtime dependency: rows/themes/styles are Android/Kotlin models and JSON (`T3ReviewDiffView.kt:18-19,143-198,436-545`), and drawing is Android Canvas/system typeface code (`ReviewDiffCanvasDrawing.kt:3-11,157-174,305-313`). It inherits the mobile root compile/min/target SDK toolchain and the repository MIT license. When review work is approved, separate an ordinary Android review surface plus models/drawing helpers, retain a thin `T3ReviewDiffModule`/Expo adapter, and require a React Native compatibility check covering row/token reset and patching, collapse/view/line/comment events, scroll commands, cleanup, a representative render snapshot, and large-diff frame behavior.
- `t3-composer-editor` — **adapt in a follow-on composer project**. Expo Modules Core and its prop/event/async surface are isolated in `android/build.gradle:17-19` and `T3ComposerEditorModule.kt:10-70`, while `T3ComposerEditorView.kt:25-35` currently combines `ExpoView` with the Android editor. There is no JNI or packaged resource. The reusable portion is ordinary Android `EditText`, selection/input-method/clipboard behavior, JSON token models, and `ReplacementSpan` rendering (`T3ComposerEditorView.kt:109-303,337-478`). It inherits the mobile root SDK toolchain. Its module-local Expo MIT license must remain attached to derived/extracted code in addition to repository licensing. A future extraction must leave a thin Expo adapter and pass a focused React Native compatibility check for controlled event-count fencing, token-chip rendering, selection, focus/blur, paste-image events, content sizing, autocorrect, and spellcheck before switching the RN consumer.
- `t3-native-controls` — **defer and replace natively in Compose**. Expo coupling is the feature: the module reads `appContext.currentActivity`/`reactContext` showcase files and extras (`T3NativeControlsModule.kt:10-33`) and wraps a small `ExpoView`/event dispatcher (`T3HeaderButtonView.kt:8-22`). There is no JNI, packaged resource, third-party dependency, or module-local license; it inherits the mobile root SDK toolchain and repository MIT license. Its only reusable drawing is a 64-line private Canvas icon view (`T3HeaderButtonView.kt:34-97`), so sharing would cost more adapter surface than a semantic Compose icon/button implementation. Keep the React Native module unchanged; do not extract its showcase file protocol into the native app.
- Gate ownership: T-020 owns the proceed/revise/stop call for any reuse project. `t3-review-diff` and `t3-composer-editor` require an approved measurable outcome, an ordinary Android boundary, retained thin Expo adapters, the focused RN checks above, license retention, and baseline performance evidence. Terminal additionally requires a supported NDK/CMake/Zig/Ghostty supply-chain plan and ABI/16-KiB-page tests. Native controls has no extraction prerequisite because replacement is the decision.

### D-016 — Keep real-server test control outside the production protocol

- Decision: the native Android integration fixture starts the normal server runtime with an additional test application layer and a line-delimited stdin/stdout control channel for description, snapshots, receipts, events, drains, and session revocation.
- Rationale: recovery and idempotency need deterministic observation of the real event store and reactors, but a production receipt/debug API would enlarge the public protocol only for tests.
- Consequence: both server homes stay worktree-local and isolated; tests capture exact PIDs, ports, listener ownership, and redacted diagnostics. Product clients continue to use the existing authenticated HTTP and Effect RPC contracts.

## Provisional Decisions

- Constructor composition is preferred over a dependency-injection framework during foundation.

These values are foundation architecture choices, not public-release commitments.

## Superseded Decisions

- None.

## Open Decision Questions

- Which CI runner/emulator and physical-device class define the initial performance bar?
- Does FCM/relay support follow immediately after foundation or after core chat/workspace parity?
- Who owns signing, Clerk, FCM, Play Console, and release operations for later projects?
