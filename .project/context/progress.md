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

## Current Delivery State

- `active-chat-controls` predates this setup and contains one in-progress task; its code/evidence state has not been audited here.
- `native-android-foundation` remains active with an approved spec and delivery plan. WS-A is done with 4 of 22 tasks complete; 18 tasks remain across the later workstreams.
- The PR #5178 deep review was evidence-checked. Accepted findings are folded into the canonical project; rejected/deferred claims are classified in `research/swiftui-pr5178-review-assessment/findings.md`. T-021/T-022 split session lifecycle from codecs and reconciliation from supervisor core.
- Delano validation passes with zero errors; during WS-A execution the sole warning records intentionally uncommitted `.project` provenance.
- `apps/kotlin-android` now contains the independent `app`, `core-protocol`, `core-data`, and `core-testing` Gradle modules plus build-proven focused entry points. It does not depend on Expo prebuild, Metro, or a generated React Native Android project.
- The 2026-08-08 WS-A delivery request explicitly authorizes its implementation, emulator verification, commit, and current-branch push. It does not authorize a pull request or broader project tasks.

## Next

- Continue only with a separately authorized, dependency-safe task from the remaining workstreams; T-005 owns canonical contract fixtures and T-021 owns the controlled WebSocket compression/session probe.
- Add the standalone Android CI runner in WS-E before treating these local commands as required repository gates.
- Rebase the implementation branch onto current main before any future PR, when explicitly requested.

## Remaining Risks and Evidence Gaps

- CI has no standalone Android runner yet; release signing/ownership also remains outside WS-A.
- Contract fixture provenance/drift remains T-005, and a controlled compressed WebSocket round trip remains T-021 evidence.
- Verified App Links remain disabled until an owned host publishes Digital Asset Links for approved signing certificates. Android 17 target-SDK work must add and test `ACCESS_LOCAL_NETWORK`; the current target-36 release correctly omits it.
- Review-diff and composer are adapter-first reuse candidates; terminal remains gated by Ghostty/JNI/NDK/Zig/ABI evidence, and native controls should be replaced in Compose. D-015 and T-020 own the handoff.
- Integration, accessibility, CI, release, and measured performance gates are intentionally reserved and fail closed until their owning tasks implement them.
- FCM parity requires relay/server/contract work and may be staged after direct-connection parity.
- SwiftUI build/test claims come from PR metadata and source inspection; this Windows environment did not run Xcode tests.
- PR #5178 remains open and experimental; it is a behavioral reference, not shipped/canonical contract evidence.
- The React Native mobile distribution wording conflicts between root and app-specific READMEs.
