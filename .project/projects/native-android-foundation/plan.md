---
name: Native Android Client Foundation
status: active
lead: team
created: 2026-08-07T13:09:35Z
updated: 2026-08-08T10:32:44Z
linear_project_id:
risk_level: high
spec_status_at_plan_time: active
operating_mode: multi-stream
---

# Delivery Plan: Native Android Client Foundation

## What Changed After Probe

No separate prototype probe was required. Repository inspection of PR #5178, canonical TypeScript contracts, shared client-runtime behavior, and existing Kotlin native modules reduced the first delivery to an architecture-proving vertical slice. The full native Android parity effort remains intentionally outside this project's completion boundary.

## Technical Context

The server exposes authenticated Effect RPC over WebSocket plus focused HTTP endpoints. It owns provider processes, orchestration, persistence, files, Git, terminals, and checkpoints. Web and React Native clients consume typed TypeScript contracts; SwiftUI implements a separate native adapter and test suite.

The Android foundation starts as a self-contained Gradle application within the monorepo. It must build independently of Expo prebuild and Metro, use a distinct identity, and connect to the existing server without Android-specific protocol forks. The root Node workspace may provide contract fixtures and orchestration commands, but Android compilation and tests remain ordinary Gradle entry points.

## Architecture Decisions

1. **Separate client:** create a side-by-side native Android application, provisionally at `apps/kotlin-android`; do not replace `apps/mobile`.
2. **Native stack:** Kotlin, Jetpack Compose/Material 3 adaptive UI, coroutines/Flow, OkHttp, Kotlin serialization, Room, and Android Keystore-backed secret protection.
3. **Minimal module boundaries:** begin with `app`, `core-protocol`, `core-data`, and `core-testing`. Keep onboarding/home code in feature packages until an actual compilation or ownership boundary justifies another Gradle module.
4. **Constructor composition:** do not adopt a dependency-injection framework during foundation. Interfaces and constructor injection are sufficient for deterministic tests.
5. **Canonical contracts:** `packages/contracts` remains authoritative. Check in small Kotlin-readable conformance fixtures produced or validated by a focused TypeScript command; every fixture set records its contracts source revision and focused validation fails on drift. Do not introduce a general generator until fixture maintenance proves insufficient.
6. **Single retry owner with active/passive state:** HTTP/WebSocket attempts expose cancellation and typed failures; supervisors alone own retry, backoff, reachability, and resubscription. Only the active environment owns live subscriptions. Passive environments use bounded HTTP refresh and keep last-known rows when a read fails.
7. **Scoped identity and routing:** use length-prefixed UTF-8 environment scoping before merged state reaches Compose. Route actions with explicit UI ID, wire ID, environment ID, and owning client/session; accept raw IDs only when ownership is unique.
8. **Separated, compensated persistence:** Keystore-backed credentials and Room environment/state data have different repositories and deletion semantics. Save credential before catalog and remove catalog before credential, with coherent rollback. One-time pairing credentials are never stored.
9. **Android-native parity:** use adaptive navigation, predictive back, TalkBack, and lifecycle-aware collection. Later iOS-specific outcomes map to Android primitives rather than copied controls.
10. **Deferred platform breadth:** T3 Connect, DPoP, FCM, widgets, share targets, shortcuts, workspace tools, and production release are follow-on projects.
11. **Ambiguity recovery:** sent unary calls are never replayed by transport. A stable logical turn preserves `{commandId, messageId, createdAt}` and verifies commit from a fresh snapshot; server receipt/domain-event evidence remains the integration backstop.
12. **Trusted route intake:** verified web intents are limited to owned T3 hosts, while arbitrary direct server pairing input remains valid through paste or the canonical custom-scheme/QR wrapper.

## Policy and Contract Checks

- [x] `.project` remains the execution source of truth.
- [x] Probe decision is explicit in `spec.md`.
- [x] Evidence gates are defined before handoff.
- [x] External sync writes require dry-run plus explicit operator approval.
- [x] React Native Android and SwiftUI mobile remain distinct clients.
- [x] Contract changes require decisions for every applicable server/client surface.
- [x] Live T3 home data is excluded from builds and integration tests.

## Generated Artifact Map

- `spec.md`: approved outcome, boundaries, measurable scenarios, and open questions.
- `plan.md`: architecture, sequencing, rollout/rollback, and verification strategy.
- `decisions.md`: active, provisional, superseded, and open architecture decisions.
- `workstreams/`: five ownership boundaries covering build, protocol, state, UI, and quality.
- `tasks/`: atomic dependency graph generated by `breakdown-skill`; all begin `planned`.
- `updates/`: execution evidence only after implementation starts.

## Complexity Exceptions

- A separate native client deliberately duplicates some client behavior. This is user-selected Option 1 and is acceptable only with contract-conformance fixtures, explicit state ownership, and narrow vertical delivery.
- A small number of Gradle modules is justified by platform and test boundaries. More granular feature modules are rejected during foundation as premature machinery.

## Probe-Driven Architecture Changes

- The initial broad parity plan was narrowed to direct pairing plus an adaptive live shell.
- Push/FCM work moved out because current relay contracts are APNs-shaped and would turn the foundation into a cross-surface backend project.
- Existing terminal/diff/composer implementations are inputs to a reuse decision, not dependencies of the foundation shell.
- Stable command/message recovery is tested through an integration harness without requiring production chat UI or bootstrap/worktree recovery.
- The SwiftUI reference supplied concrete pairing/RPC/state tests, but exact Swift timers, actor mechanics, full feature catalog, and visual constants are not Android requirements.
- Android 16/17 local-network protection and verified App Links are explicit platform decisions rather than iOS-only concerns.

## Workstream Design

### WS-A — Architecture and Build Foundation

Owns the decision record, project/Gradle scaffold, identities, minimal module graph, and local commands. It unblocks every other stream and must avoid editing React Native generated Android output.

### WS-B — Contracts and Transport

Owns canonical fixtures, Kotlin wire models, HTTP pairing/ticket APIs, Effect RPC framing/codecs, one-attempt WebSocket session lifecycle, cancellation, and transport race tests. It does not own persistence, reconnect, or backoff policy.

### WS-C — Persistence and Connection State

Owns Keystore/credential repositories, Room schema/migrations, environment-scoped routes, active/passive supervisors, snapshot reconciliation, stale-publish guards, last-known state, reconnect/resubscribe policy, ambiguity recovery, and process-death recovery.

### WS-D — Adaptive Product Shell

Owns Compose onboarding, saved-environment and home shell UI, phone/tablet navigation, state presentation, lifecycle-aware collection, predictive back, and accessibility baseline. It consumes WS-C state and does not construct transports.

### WS-E — Quality, CI, and Handoff

Owns test fixtures and disposable server harness, static/build/test/benchmark entry points, focused CI, documentation, parity assessment, and the go/no-go recommendation for follow-on projects.

## Milestone Strategy

1. **M0 — Decisions ready:** package/SDK/toolchain, contract fixture, and module-reuse decisions recorded.
2. **M1 — Buildable shell:** independent debug/release-shaped variants install beside React Native; local-network permission policy, trusted route intake, empty adaptive UI, and focused Gradle checks pass.
3. **M2 — Protocol proof:** pairing, token protection seam, corrected Effect RPC framing, ticket-per-attempt session lifecycle, cancellation, contract provenance/drift, compression compatibility, and transport race tests pass.
4. **M3 — Lifecycle proof:** two isolated environments, collision-safe state, Room restoration, process death, active/passive supervision, reconnect/resubscribe, stale-publish prevention, and ambiguous command recovery pass.
5. **M4 — Product proof:** onboarding and live home shell work on phone/tablet with offline/error/loading states and accessibility evidence.
6. **M5 — Foundation gate:** focused CI and performance baseline pass; docs and follow-on project recommendations are complete.

Milestones are evidence gates. Later milestones may not hide failures in earlier contract or lifecycle behavior.

## Rollout Strategy

- Keep the app internal and separately identified throughout foundation.
- Land one narrow concern per PR only when the user explicitly requests PR creation.
- Add CI as an opt-in focused job first; make it required only after runtime and flake behavior are stable.
- Use disposable worktree-local T3 state and synthetic/copy-once fixtures.
- Do not advertise, publish, or migrate users during this project.

## Test Strategy

- **Pure unit tests:** length-prefixed identifiers/routes, reducers, capability-absent/unknown-item behavior, jittered backoff, state transitions, 64-bit JSON fidelity, serializers, fixture decoding, pairing/QR URL parsing, and compensated credential/catalog deletion.
- **Transport tests:** corrected `headers: []` framing; `Chunk`/`Exit`/`Ack`/`Interrupt`/fatal tags; fresh ticket per attempt; sent-versus-unsent failures; session-owned cancellation; ticket expiry; malformed payloads; compression offer/decode; one-shot stream failure; and supervisor resubscription with fresh request IDs using deterministic clocks/dispatchers.
- **Persistence tests:** Room migrations, save/remove compensation, fail-closed credential kind, atomic client/session replacement, process recreation, and corrupt/partial state recovery.
- **State tests:** only the active environment owns live subscriptions; passive poll failures retain last-known rows; fresh HTTP data remains visible while reconnecting; superseded jobs cannot publish; unknown stream items cause one bounded refresh.
- **Server integration:** two isolated disposable T3 processes with colliding IDs, direct pairing, stable turn identity, forced lost response, snapshot commit verification, and one receipt/domain event. Await typed receipts/drains rather than sleeps.
- **Instrumentation/UI:** onboarding paste/custom-wrapper/owned-link intake, saved environments, target-SDK local-network denial recovery, phone/tablet navigation, lifecycle recreation, predictive back, TalkBack labels, 48dp interactive targets, and font scaling.
- **Performance:** startup and representative home-list Macrobenchmark/baseline profile evidence; verify no continuously repainting or duplicate collectors.
- **Compatibility:** run the smallest React Native Android/native-module checks when shared Kotlin code changes.

## Rollback Strategy

- Until release, rollback is removal or disablement of the standalone app and its focused CI entry; it owns no production user migration.
- Keep package identities and storage isolated so uninstalling the experiment cannot affect React Native Android.
- Avoid server schema changes in foundation. If a narrowly approved contract correction is required, ship it backward-compatibly with focused tests and an explicit revert path.
- Any Kotlin module extraction retains the existing Expo-facing adapter until React Native verification passes; revert extraction independently from native-client work.

## Remaining Delivery Risks

- Android toolchain/package decisions are still provisional and can affect CI, min-SDK reach, and native library compatibility.
- Effect RPC framing/stream semantics and the selected WebSocket stack's compression behavior may expose more native-client edge cases than HTTP fixtures cover.
- Current Delano runtime requires three local compatibility patches in this large Windows ESM monorepo; reinstalling Delano may overwrite them.
- Existing Kotlin native modules may be too coupled to Expo or generated projects for immediate reuse.
- A test-only ambiguity harness must prove snapshot commit recovery plus real server receipts/domain events without becoming production-only debug machinery.
- Emulator performance evidence may not represent low-end physical devices; follow-on release work needs a hardware matrix.
- Android local-network permission behavior depends on the selected target SDK, while verified App Links also depend on owned-domain Digital Asset Links and signing identities.
