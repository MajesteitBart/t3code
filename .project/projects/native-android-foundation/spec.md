---
name: Native Android Client Foundation
slug: native-android-foundation
owner: team
status: active
created: 2026-08-07T13:09:35Z
updated: 2026-08-08T10:32:44Z
outcome: A separately installable Kotlin/Compose client can pair with disposable T3 environments, restore saved state after process death, and render a live adaptive shell while recovering ambiguous command results without blind replay or duplicate domain effects.
uncertainty: medium
probe_required: false
probe_status: skipped
probe_decision_rationale: PR #5178, the canonical server contracts, and existing Android native modules provide enough evidence to plan a bounded foundation slice; implementation tasks contain focused proof gates for remaining integration risk.
operating_mode: multi-stream
---

# Spec: Native Android Client Foundation

## Executive Summary

Build the smallest independently installable native Android vertical slice that proves Option 1 is viable. The result is a Kotlin/Jetpack Compose application beside the React Native and SwiftUI clients, with direct pairing, encrypted credentials, canonical RPC, saved environments, resilient connection supervision, a phone/tablet shell, and deterministic contract/transport tests.

This project is a foundation gate, not the complete SwiftUI parity port. It must retire the largest architecture and integration risks before chat, workspace tools, T3 Connect, push, widgets, and store delivery are decomposed into follow-on projects.

## Problem and Users

PR #5178 proves that a fully native client can speak T3's existing server contracts, but it implements that boundary only in Swift. A naïve Kotlin copy would create a second hand-maintained protocol, retry engine, persistence model, and large UI change without first proving contract conformance or lifecycle behavior.

The primary users of this foundation are T3 maintainers who need evidence that a native Android client can coexist with `apps/mobile`, remain remote-ready, and scale into feature parity without destabilizing the server or existing clients. The eventual end users are Android developers who supervise agents throughout the day on phones and tablets.

## Outcome and Success Metrics

The project succeeds when all of the following are evidenced:

- A clean focused command builds and installs debug and release-shaped native Android variants with application IDs distinct from the React Native client and from each other.
- The app pairs with a disposable T3 environment, exchanges the one-time credential, stores the resulting secret with Android Keystore-backed protection, and restores the environment after forced process death.
- Two saved environments with colliding raw project/thread identifiers render collision-safe state in an adaptive phone/tablet shell.
- The native transport consumes canonical JSON fixtures and supports HTTP, authenticated Effect RPC WebSocket subscriptions, cancellation, reconnect, and resubscription without stale duplicate collectors.
- An ambiguous-response integration fixture preserves stable command/message identity, verifies commit from a fresh snapshot before any explicit retry, and produces one durable server receipt/domain effect rather than duplicate work.
- Focused unit, integration, instrumentation, lint/static, and baseline performance checks have documented entry points and pass on the supported Android test target.
- A focused contract command fails when canonical TypeScript contracts drift, and fixture provenance identifies the contracts revision used.
- No changes are required in the React Native app for the native shell to build or run; any extracted Kotlin library keeps a thin Expo adapter for the existing client.

## User Stories

- US-001: As a maintainer, I can install the native development app beside the React Native app so that experiments do not overwrite shipped state.
- US-002: As an Android user, I can pair directly with a T3 server and return after process death without pairing again.
- US-003: As a user with multiple environments, I can distinguish and reopen projects and threads even when servers reuse raw identifiers.
- US-004: As a maintainer, I can change a server contract and have native Android conformance tests expose incompatible decoding before release.
- US-005: As a maintainer, I can force disconnect and ambiguous retry cases and observe one coherent state transition with no duplicate command.
- US-006: As a maintainer, I can measure startup and representative home-list budgets and detect material regressions before release.

## Acceptance Scenarios

- AC-001: Given both React Native Android and native Android development builds are installed, when either is launched, then it reads and writes only its own application data and credentials.
- AC-002: Given a valid direct pairing URL, when pairing completes and the process is killed, then relaunch restores the saved environment using protected credentials and no one-time credential is persisted.
- AC-003: Given two environments expose the same raw project and thread IDs, when both snapshots are loaded, then the UI renders independently scoped rows and routes each selection to the correct environment.
- AC-004: Given the socket closes while subscriptions are active, when connectivity returns, then one supervisor reconnects, resubscribes once, and replaces stale state without leaking duplicate collectors.
- AC-005: Given a sent turn response is lost after server acceptance, when the client resolves the ambiguity, then a fresh thread snapshot confirms the stable message ID before replay, an unconfirmed result preserves the original command/message identity for an explicit retry, and server evidence contains one durable receipt/domain effect.
- AC-006: Given phone and expanded tablet widths, when the home shell renders, then navigation, loading, offline, empty, and error states remain usable with TalkBack and font scaling enabled.
- AC-007: Given canonical contracts or their source revision change, when the focused conformance command runs, then stale Kotlin fixtures/models fail with actionable drift output and regenerated fixtures record their source provenance.
- AC-008: Given the selected Android test device, API, build type, and representative shell fixture, when startup/list benchmarks run, then recorded budgets pass and continuous invalidation, duplicate collectors, or unbounded row work fail the gate.

## Scope

### In Scope

- Decide and scaffold a standalone Gradle/Android application, provisionally under `apps/kotlin-android`.
- Kotlin, Jetpack Compose, Material 3, adaptive navigation, coroutines/Flow, and dependency injection kept minimal and explicit.
- Direct pairing and credential exchange, Android Keystore-backed secret storage, and non-secret environment persistence.
- A contract-conformance boundary for required HTTP and Effect RPC methods using fixtures tied to `packages/contracts`.
- WebSocket session lifecycle, ticket authentication, cancellation, reconnect/resubscribe, and active/passive multi-environment supervision.
- Room-backed saved environments and last-known shell state with migration and process-death tests.
- A minimal onboarding/environment/home shell for phones and tablets; no production chat/workspace feature UI.
- A non-UI turn-dispatch integration fixture proving stable command/message identity, snapshot-based ambiguity recovery, and server receipt/domain-effect idempotency.
- Focused build/test/lint/benchmark commands, CI entry point, architecture docs, and a handoff decision for later parity projects.
- A documented reuse decision for existing Kotlin terminal, review, composer, and native-controls modules; extraction only when required to prove the module boundary.

### Out of Scope

- Replacing, renaming, or removing the React Native Android app.
- Full new-task, chat, attachments, approvals, input, files, review, Git, terminal, settings, or source-control UI.
- Clerk/T3 Connect, DPoP, relay environment discovery, FCM, remote push registration, widgets, shortcuts, share targets, or background status surfaces.
- Store signing, Play Console configuration, public release, production migration, telemetry rollout, or deprecating another client.
- Broad server or contract redesign. Foundation work may add fixtures or a narrowly approved compatibility correction, but not fork the protocol for Android.
- Reproducing iOS visual controls when Android-native interaction patterns differ.

## Functional Requirements

- FR-001: The native client has independent debug and release-shaped identities, labels, URL schemes, storage, and build outputs.
- FR-002: Pairing accepts canonical direct links, rejects malformed/expired credentials safely, and persists only the exchanged access credential.
- FR-003: Secrets use Android Keystore-backed protection; non-secret environment metadata and last-known state use versioned Room storage.
- FR-004: Required contract models decode canonical success, missing-field, unknown-field, and incompatible-enum fixtures deterministically.
- FR-005: HTTP and WebSocket clients expose cancellation and typed failure categories without embedding retry policy in composables.
- FR-006: Per-environment state owners share one active/passive policy: only the active environment owns live subscriptions, passive environments use bounded HTTP refresh, and the supervisor layer owns retry, reachability, resubscription, and lifecycle release.
- FR-007: All project/thread identities crossing into merged state are scoped by environment.
- FR-008: The Compose shell exposes pairing, saved environments, environment reachability, project/thread rows, selection, loading, empty, offline, and error states.
- FR-009: A test-only integration path dispatches a turn with stable `{commandId, messageId, createdAt}`, verifies ambiguous commit from a fresh thread snapshot before replay, preserves identity when unconfirmed, and verifies one durable server receipt/domain effect.
- FR-010: Existing clients continue compiling against unchanged boundaries; reuse is through an explicit Android library API rather than direct dependency on Expo modules.

## Non-Functional Requirements

- NFR-001 Performance: no continuously repainting UI, unbounded state collectors, unbounded caches, or reconnect loops; baseline startup/list rendering measurements are recorded.
- NFR-002 Reliability: process death, rotation, network loss, server restart, cancellation, and credential revocation have deterministic state transitions and focused tests.
- NFR-003 Security: secrets and pairing codes never appear in logs or Delano evidence; Keystore failure and credential deletion paths are tested.
- NFR-004 Accessibility: the foundation shell supports TalkBack semantics, large font scaling, touch targets, contrast, and predictive back.
- NFR-005 Remote readiness: architecture permits direct local, remote, relay, and tunnel connections even though this project implements only direct pairing.
- NFR-006 Reviewability: each implementation PR is a narrow vertical concern with focused verification; no PR attempts a PR-#5178-sized port.
- NFR-007 Contract integrity: Kotlin wire models are validated against canonical fixtures and are not treated as a second source of truth.
- NFR-008 Platform privacy: local-network access and verified web-link intake follow the selected target SDK and Android platform policy; arbitrary server URLs remain explicit onboarding input, not broadly claimed web intents.

## Assumptions

- `packages/contracts` and the current T3 server remain the protocol authority.
- The native Android project can live in the monorepo without changing the Node/pnpm package topology beyond focused build orchestration.
- Current Android toolchains support the selected target on available CI and maintainer machines; the exact versions are decided in T-001.
- A disposable paired T3 server can be used by integration tests without accessing live `~/.t3/userdata`.
- The existing Kotlin/C++ native modules may be refactored only after their Expo coupling and license/build boundaries are inspected.
- The user-selected native-client direction is approved; naming, package, SDK, and release details remain implementation decisions.

## Needs Clarification

- Confirm the final application directory and package/application IDs; provisional names are recorded in `decisions.md`.
- Confirm the lowest Android API level and target/compile SDK supported by maintainers and CI.
- Confirm the target-SDK-dependent local-network permission path and test matrix (Android 16 compatibility mode and Android 17 enforcement where applicable).
- Confirm which T3-owned domains can publish Digital Asset Links for the selected application ID and signing certificates.
- Confirm whether the first release-shaped build is internal-only or expected to become a Play Store application later.
- Confirm whether an FCM/relay project should immediately follow foundation or wait until direct product workflows reach parity.
- Confirm ownership for Android signing, Clerk configuration, FCM, and store operations before those follow-on projects activate.

## Hypotheses and Unknowns

- Kotlin serialization plus checked canonical fixtures will control contract drift without maintaining a heavy code generator.
- The selected OkHttp/WebSocket version plus coroutines/Flow can reproduce Effect RPC session semantics with explicit cancellation and bounded supervision; T-001/T-021 must prove compression behavior and document whether negotiated extensions are observable.
- Room is sufficient for environment metadata and last-known shell state; a later outbox may require additional transactional design.
- Existing Android terminal/diff/composer code can become ordinary Android libraries with thin Expo bindings, but the degree of Expo coupling is not yet measured.
- A baseline adaptive Compose shell can remain smooth on representative large home fixtures; exact performance thresholds need measurement on agreed hardware/emulators.

## Touchpoints to Exercise

- `packages/contracts/src/rpc.ts`, orchestration contracts, auth/ticket endpoints, and environment descriptors.
- `packages/client-runtime/src/rpc/session.ts` and connection-supervisor behavior.
- SwiftUI `T3Client`, `FeatureClient`, root model, persistence, and pairing tests as behavioral references.
- React Native Android app identities, deep links, Keystore/SecureStore behavior, and current Kotlin modules.
- Disposable worktree-local T3 server using copied/synthetic test data, never live T3 home state.
- Phone portrait, tablet expanded width, rotation, process recreation, offline/reconnect, and accessibility settings.
- Android local-network privacy behavior for the selected target SDK, verified App Links for owned hosts, and custom-scheme/QR-wrapper intake.

## Probe Findings

- Source inspection established that PR #5178 is an additive native client with no corresponding server/contract implementation change.
- The SwiftUI client demonstrates direct native HTTP/Effect RPC integration and exposes contract, pairing, persistence, transport-race, and state tests that can guide Android fixtures.
- Deep review of PR #5178 established that it is an open experimental reference, not a shipped contract. Its transferable value is in verified wire/reliability invariants, while Swift timers, actors, visual tokens, and full feature inventory remain non-authoritative for Android.
- Current Android guidance includes local-network protection (opt-in on Android 16; enforced for target SDK 37+ on Android 17), so permission-denied handling cannot be marked iOS-only.
- React Native Android already includes Ghostty terminal, review-diff, composer, controls, T3 Connect/DPoP, sharing, and shortcuts; duplication decisions must therefore be explicit.
- Android remote agent-awareness is currently blocked by an APNs/iOS-shaped relay contract, but that does not block the direct-pairing foundation.

## Footguns Discovered

- Hand-copying the 5,000-line Swift native adapter into one Kotlin class would reproduce its concentration risk and make ownership unclear.
- Retrying WebSocket calls below the supervisor can create duplicate subscriptions or commands.
- Treating a lost sent response as permission for automatic replay can duplicate side effects; ambiguity recovery must inspect canonical state and preserve identity.
- Persisting one-time pairing credentials, DPoP private material, or push tokens in ordinary Room rows would violate the credential boundary.
- Raw server IDs collide across environments unless scoped before entering merged UI state.
- Depending directly on Expo modules from a standalone Gradle app would couple native Android viability to React Native build generation.
- Running against or mutating live T3 home state is prohibited.

## Remaining Unknowns

- Final build identity, minimum API, Android Gradle Plugin, Kotlin, Compose BOM, and Java versions.
- Whether contract fixtures should be generated in CI or checked in and validated by a focused TypeScript exporter.
- Which dependency-injection approach, if any, is warranted beyond constructor composition.
- Whether current CI offers an Android emulator suitable for deterministic integration and Macrobenchmark runs.
- How much of the existing Android native modules can be extracted without destabilizing React Native builds.
- Whether the selected WebSocket stack exposes per-message-deflate negotiation or requires a round-trip-only compatibility proof.
- Which owned web-link hosts can be verified for the provisional/final application identities.

## Dependencies

- Stable canonical contracts and a disposable T3 server fixture.
- Android SDK/emulator, Java, Gradle, and CI capacity selected during architecture setup.
- Maintainer decisions for package identity and minimum API.
- Existing app/package boundaries remain available for inspection.
- Explicit user approval before browser, emulator, or computer-use verification.

## Approval Notes

- 2026-08-07T13:11:53Z: User approved Option 1 and requested the first native Android project; activate the bounded foundation plan without starting implementation tasks.

- 2026-08-07: The user explicitly selected Option 1, the standalone native Android client, and requested creation of the first Delano project.
- 2026-08-07: The first project is intentionally limited to the smallest architecture-proving vertical slice; later parity areas remain follow-on projects until this gate has evidence.
