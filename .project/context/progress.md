# Progress

Last refreshed: 2026-08-08

## Completed Setup Evidence

- `origin/main` was fast-forwarded and pushed to match `upstream/main` at `be1a836745395286cbd392512179ab5816f538ba`.
- PR #5178 is checked out on `pr-5178-swift-ios` at `54d15002a28e4cfc0e3d2051f04694ae1b681a79`.
- The PR was categorized as a standalone SwiftUI client: 159 files, 49,657 additions, 154 files under `apps/swift-ios`, and no server/contract implementation changes.
- The user selected Option 1: create a separate native Android client rather than only filling React Native Android parity gaps.
- `AGENTS.md` was merged into the supplied Delano template structure while retaining T3 Code-specific safety and multi-surface rules.
- Delano runtime assets were refreshed with the current npm CLI in update-safe mode; existing `.project` state was preserved.
- The context pack was replaced with repository-grounded material through `manage-context`.
- Native Android WS-A is complete: T-001 froze the toolchain/identity/route/permission decisions, T-002 added the standalone Compose scaffold, T-004 recorded module reuse boundaries, and T-003 added stable focused commands and bootstrap documentation.
- The checksum-pinned Gradle 8.13/JDK 17 build assembled development and release-shaped APKs, passed debug/release unit tests and warnings-as-errors lint, installed on the API-35 phone AVD, and passed its connected Compose render test.
- Native Android WS-B is complete: T-005 added 27 canonical, provenance-checked contract fixture files; T-006 implemented direct pairing and one-attempt typed HTTP; T-007 added transport-independent Effect RPC codecs; T-021 added the fresh-ticket single-generation WebSocket session; and T-008 proved race, cleanup, redaction, gzip, and compression behavior.
- `foundationContractConformance` checks the canonical TypeScript/Effect revision and content hashes before debug/release Kotlin decoding tests. `foundationTransportIntegration` runs controlled transport races and a real OkHttp 5.4.0/MockWebServer bidirectional `permessage-deflate` round trip with `9007199254740991` preserved as `Long`.
- Native Android WS-C is complete: T-009 separates Android Keystore credentials from versioned Room state with compensating operations; T-010 adds collision-safe scoped identities and reducers; T-011/T-022 add one-owner active/passive supervision and serialized reconciliation fences; T-012 proves process-death, outage/restart, revocation, and removal across two environments; and T-013 preserves stable turn identity through ambiguous response recovery.
- WS-E T-017 is complete. `foundationIntegration` starts two normal T3 server runtimes under isolated worktree-local homes, verifies exact PID/port/listener ownership, redacts credentials, exposes typed snapshot/receipt/event/drain control, and tears down only captured processes and validated roots. The final gate passed twice after disabling idle socket reuse in the fixture while retaining one-attempt/no-retry semantics.
- `foundationPersistence` passed four device-backed Room/Keystore tests on the API-35 `TwentyGoApi35` AVD plus debug/release unit tests. The final combined contract, transport, connection-state, integration, all-module debug/release unit, warnings-as-errors lint, and debug/release assembly sweep passed.
- WS-D T-014 and T-015 are complete. The app now composes the existing protocol/data modules into masked direct onboarding, protected saved-environment management, aggregate collision-safe project/thread navigation, explicit offline/reconnecting/revoked/error states, and lifecycle-aware phone/tablet Compose layouts. Final evidence includes 7 API-35 UI tests, real isolated-server pairing, expanded-layout inspection, and app process recreation.

## Current Delivery State

- `active-chat-controls` predates this setup and contains three open tasks; its code/evidence state has not been audited here.
- `native-android-foundation` remains active with an approved spec and delivery plan. WS-A, WS-B, and WS-C are done. WS-D has T-014/T-015 done and T-016 planned; WS-E has T-017 done and T-018/T-019/T-020 planned. Eighteen of 22 tasks are complete.
- The PR #5178 deep review was evidence-checked. Accepted findings are folded into the canonical project; rejected/deferred claims are classified in `research/swiftui-pr5178-review-assessment/findings.md`. T-021/T-022 split session lifecycle from codecs and reconciliation from supervisor core.
- Delano validation passes with zero errors; during WS-A execution the sole warning records intentionally uncommitted `.project` provenance.
- `apps/kotlin-android` now contains the independent `app`, `core-protocol`, `core-data`, and `core-testing` Gradle modules plus build-proven focused entry points. `core-protocol` owns canonical Kotlin wire models, direct pairing/HTTP, Effect RPC codecs, and a policy-free one-attempt ticketed session. `core-data` owns protected credentials, Room catalog/snapshots, scoped aggregate state, connection supervision, reconciliation, and stable-turn recovery. `app` owns only the thin ViewModel/action/UI composition and adaptive shell. It does not depend on Expo prebuild, Metro, or a generated React Native Android project.
- The current 2026-08-08 delivery request explicitly authorized dependency-safe WS-D work, real tests/emulator checks, a coherent commit, and a push of the current branch while prohibiting a pull request. T-014/T-015 were dependency-safe and are done. T-016 was not opened because its declared T-018 dependency remains planned in WS-E.

## Next

- Under separately authorized WS-E scope, complete T-018's recorded performance budgets; only then open T-016 and finish WS-D accessibility/lifecycle verification.
- Add the standalone Android CI runner in WS-E before treating these local commands as required repository gates.
- Rebase the implementation branch onto current main before any future PR, when explicitly requested.

## Remaining Risks and Evidence Gaps

- CI has no standalone Android runner yet; release signing/ownership also remains outside WS-A.
- Real server-process interoperability, durable receipt/domain-event evidence, process-death recovery, and ambiguity reconciliation now have local evidence; they are not yet installed as required CI gates.
- Verified App Links remain disabled until an owned host publishes Digital Asset Links for approved signing certificates. Android 17 target-SDK work must add and test `ACCESS_LOCAL_NETWORK`; the current target-36 release correctly omits it.
- Review-diff and composer are adapter-first reuse candidates; terminal remains gated by Ghostty/JNI/NDK/Zig/ABI evidence, and native controls should be replaced in Compose. D-015 and T-020 own the handoff.
- Accessibility and measured performance gates remain intentionally reserved and fail closed until dependency-ordered T-018 then T-016 implement them; focused CI/release ownership remains T-019/T-020 scope.
- Production transcript rendering, composer/send behavior, workspace tools, and their user-facing ambiguity recovery remain outside this foundation spec. D-002 and T-020 require a follow-on project rather than silent scope expansion.
- FCM parity requires relay/server/contract work and may be staged after direct-connection parity.
- SwiftUI build/test claims come from PR metadata and source inspection; this Windows environment did not run Xcode tests.
- PR #5178 remains open and experimental; it is a behavioral reference, not shipped/canonical contract evidence.
- The React Native mobile distribution wording conflicts between root and app-specific READMEs.
