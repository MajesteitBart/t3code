---
id: WS-A
name: WS-A Architecture and Build Foundation
owner: team
status: planned
created: 2026-08-07T13:12:09Z
updated: 2026-08-07T13:12:21Z
operating_mode: multi-stream
---

# Workstream: WS-A Architecture and Build Foundation

## Objective

Turn the provisional native-client direction into an independently buildable, separately identified Compose application with the smallest justified module and toolchain surface.

## Owned Files/Areas

- `.project/projects/native-android-foundation/decisions.md`
- Proposed `apps/kotlin-android/settings.gradle.kts`, root build files, version catalog, Gradle wrapper, and `app/`
- Native Android build/run documentation and focused root task wiring
- Package identities, manifests, resource names, target-SDK local-network permission policy, owned-host/custom-scheme intent filters, and application composition root

## Dependencies

- User approval of Option 1 and the active spec.
- Current Android SDK/Java/CI inventory.
- T3 Code identity conventions in `apps/mobile/app.config.ts` and SwiftUI build settings.

## Risks

- Toolchain choices may conflict with existing Ghostty/Kotlin native modules or CI.
- Premature modularization could slow iteration; insufficient boundaries could make protocol/data tests depend on Android UI.
- Reusing a shipped application ID or storage authority could overwrite another client.
- Broad web intent filters could let unowned hosts inject routes, while an omitted target-SDK permission policy could silently break direct LAN pairing.

## Handoff Criteria

- Toolchain, directory, package, SDK, and module decisions are recorded with evidence.
- Debug and release-shaped variants build from focused commands and install beside React Native Android.
- The manifest claims only the approved custom scheme and verified owned web hosts; arbitrary server URLs remain explicit onboarding input.
- The composition root is thin and no feature constructs transport/persistence directly.
- Other workstreams have stable module/package locations and test entry points.
