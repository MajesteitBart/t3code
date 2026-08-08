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

## Current Delivery State

- `active-chat-controls` predates this setup and contains three open tasks; its code/evidence state has not been audited here.
- `native-android-foundation` remains active with an approved spec and delivery plan. WS-A and WS-B are done with 9 of 22 tasks complete; 13 tasks remain across WS-C, WS-D, and WS-E.
- The PR #5178 deep review was evidence-checked. Accepted findings are folded into the canonical project; rejected/deferred claims are classified in `research/swiftui-pr5178-review-assessment/findings.md`. T-021/T-022 split session lifecycle from codecs and reconciliation from supervisor core.
- Delano validation passes with zero errors; during WS-A execution the sole warning records intentionally uncommitted `.project` provenance.
- `apps/kotlin-android` now contains the independent `app`, `core-protocol`, `core-data`, and `core-testing` Gradle modules plus build-proven focused entry points. `core-protocol` owns canonical Kotlin wire models, direct pairing/HTTP, Effect RPC codecs, and a policy-free one-attempt ticketed session. It does not depend on Expo prebuild, Metro, or a generated React Native Android project.
- The 2026-08-08 WS-B delivery request explicitly authorizes this workstream, its focused Gradle/contract/integration evidence, a commit, and a push of the current branch. It explicitly prohibits a pull request and does not authorize later workstreams.

## Next

- Continue only with a separately authorized, dependency-safe task. T-009 and T-017 currently have satisfied dependencies; do not infer authorization for either from WS-B completion.
- Add the standalone Android CI runner in WS-E before treating these local commands as required repository gates.
- Rebase the implementation branch onto current main before any future PR, when explicitly requested.

## Remaining Risks and Evidence Gaps

- CI has no standalone Android runner yet; release signing/ownership also remains outside WS-A.
- Real server-process interoperability, durable receipt/domain-event evidence, and ambiguity reconciliation remain gated by T-017/T-013; WS-B used canonical fixtures and isolated in-process HTTP/WebSocket servers.
- Verified App Links remain disabled until an owned host publishes Digital Asset Links for approved signing certificates. Android 17 target-SDK work must add and test `ACCESS_LOCAL_NETWORK`; the current target-36 release correctly omits it.
- Review-diff and composer are adapter-first reuse candidates; terminal remains gated by Ghostty/JNI/NDK/Zig/ABI evidence, and native controls should be replaced in Compose. D-015 and T-020 own the handoff.
- Integration, accessibility, CI, release, and measured performance gates are intentionally reserved and fail closed until their owning tasks implement them.
- FCM parity requires relay/server/contract work and may be staged after direct-connection parity.
- SwiftUI build/test claims come from PR metadata and source inspection; this Windows environment did not run Xcode tests.
- PR #5178 remains open and experimental; it is a behavioral reference, not shipped/canonical contract evidence.
- The React Native mobile distribution wording conflicts between root and app-specific READMEs.
