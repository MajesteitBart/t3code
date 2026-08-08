---
id: T-002
name: Scaffold the standalone Compose application
status: done
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T12:52:35Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: [Gradle settings, root build files]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Scaffold the standalone Compose application

## Description

Create the approved Gradle application and minimal app, core-protocol, core-data, and core-testing boundaries with independent development and release-shaped variants.

## Acceptance Criteria

- [x] A focused Gradle command assembles both development and release-shaped variants from a clean checkout.
- [x] The development variant installs beside the React Native Android app without sharing application data.
- [x] The app renders a minimal Compose root on the supported phone emulator.
- [x] The manifest declares the approved custom pairing scheme and only the verified owned-host App Link filters decided in T-001; it does not claim arbitrary `http/https` server hosts.
- [x] Target-SDK local-network permissions, rationale, and build-variant behavior match the T-001 decision without granting unrelated nearby-device capabilities.
- [x] No Expo prebuild, Metro process, or apps/mobile generated Android directory is required.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- Route parsing and UI handling remain T-006/T-014; this task owns only manifest/application plumbing.
- Keep development and release-shaped schemes/hosts collision-safe with React Native Android. Do not copy the SwiftUI-only `t3code-swiftui[-dev]` scheme.
- If owned-host Digital Asset Links cannot yet be published for the provisional identity, keep verified App Links disabled/documented rather than pretending verification succeeded; custom-scheme and paste paths still satisfy the foundation flow.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T12:52:35Z: Gradle 8.13 assembleDebug/assembleRelease, debug/release unit tests, lint, API-35 install, and connectedDebugAndroidTest passed; APK and merged-manifest identities/permissions/routes were inspected.

- 2026-08-08T12:52:08Z: `apps/kotlin-android/gradlew.bat :app:assembleDebug :app:assembleRelease --no-daemon` passed with Gradle 8.13/JDK 17 after pinning Lifecycle Runtime Compose 2.10.0 to the D-011 API-36 compatibility boundary. The checksum-pinned wrapper produced both `app-debug.apk` and `app-release-unsigned.apk`.
- 2026-08-08T12:52:08Z: `aapt dump badging` reports `com.t3tools.t3code.compose.dev` / `t3code-compose-dev` and `com.t3tools.t3code.compose` / `t3code-compose`, each at min SDK 24, target/compile SDK 36. The existing React Native IDs (`com.t3tools.t3code`, `.dev`, `.preview`) remain distinct, `ApplicationIdentityTest` passes for both variants, and API-35 installation allocated `/data/user/0/com.t3tools.t3code.compose.dev`, proving a separate Android application-data sandbox.
- 2026-08-08T12:52:08Z: `gradlew.bat testDebugUnitTest testReleaseUnitTest --no-daemon` and `gradlew.bat lintDebug lintRelease --no-daemon` passed. Lint remains warnings-as-errors; deliberately frozen dependency-version notices are informational and the upgrade boundary is documented in D-011.
- 2026-08-08T12:52:08Z: On the `TwentyGoApi35` phone AVD, `:app:installDebug` passed, `:app:connectedDebugAndroidTest` finished one Compose UI test successfully, and direct screenshot inspection showed the `T3 Code Compose` / `Native Android foundation` root. The test package and captured emulator process tree were removed after validation.
- 2026-08-08T12:52:08Z: Merged-manifest and APK inspection confirms release declares only `INTERNET`, debug adds only `NEARBY_WIFI_DEVICES` with `neverForLocation`, neither variant declares `ACCESS_LOCAL_NETWORK`, the only browsable routes are the build-specific custom `pair` schemes, and no arbitrary `http/https` App Link is claimed.
- 2026-08-08T12:52:08Z: Self-review confirmed the standalone settings include only `app`, `core-protocol`, `core-data`, and `core-testing`; no Expo plugin, generated `apps/mobile/android`, Metro process, or root workspace build mutation is used. D-011 was updated with the build-proven Lifecycle compatibility boundary.

- 2026-08-08T12:30:56Z: Build the independent Compose application and app/core-protocol/core-data/core-testing boundaries from finalized decisions.

- 2026-08-08T12:30:56Z: T-001 is done; toolchain, identities, permission policy, route surface, and module graph are final, so the scaffold is dependency-safe.

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
