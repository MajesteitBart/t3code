---
id: T-001
name: Finalize Android toolchain and application identity
status: done
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T12:30:33Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: [apps/mobile/app.config.ts, Android build identity]
parallel: false
priority: high
estimate: M
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Finalize Android toolchain and application identity

## Description

Audit current Android, CI, and native-library constraints; record the final directory, package IDs, SDK/toolchain versions, module boundaries, and focused commands in the decision log.

## Acceptance Criteria

- [x] decisions.md records final directory, release and development application IDs, display names, min/target/compile SDK, Java, Gradle, Android Gradle Plugin, Kotlin, and Compose versions.
- [x] The selected application IDs cannot overwrite any React Native or SwiftUI identity.
- [x] The decision cites current CI and existing Android native-module compatibility evidence.
- [x] The target-SDK decision states whether `ACCESS_LOCAL_NETWORK` is required and defines Android 16 compatibility-mode plus Android 17 enforcement coverage as applicable.
- [x] The selected HTTP/WebSocket versions document transparent gzip behavior, per-message-deflate compatibility, and whether negotiated extensions are observable or require a round-trip proof.
- [x] The route-intake decision names only T3-owned web hosts that can publish Digital Asset Links; arbitrary servers are left to paste/custom-wrapper input.
- [x] No unresolved toolchain decision remains that would change the Gradle scaffold.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- Android platform source: `https://developer.android.com/privacy-and-security/local-network-permission`. The review's claim that Android has no local-network permission is obsolete: Android 16 offers opt-in protection and Android 17 enforces `ACCESS_LOCAL_NETWORK` for target SDK 37+.
- App Link source: `https://developer.android.com/training/app-links/about`. Verification requires an owned host, matching manifest filter, hosted `assetlinks.json`, and signing-certificate association.
- Do not assume OkHttp exposes the WebSocket 101 extension response. Select a proof supported by the pinned version: negotiation inspection when public/testable, otherwise a controlled compressed round trip.
- Exact Swift defaults (5-second RPC ping, 4-second unsent wait, 20-second passive poll, 6-second passive timeout, and its backoff constants) are reference points, not toolchain requirements.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T12:30:33Z: D-011 through D-014; local Java/SDK/CI/client inventory; official compatibility sources; delano validate zero errors; explicit self-review recorded in task evidence.

- 2026-08-08T12:30:10Z: Recorded D-011 through D-014 with the final directory, identities, SDK/toolchain/library versions, LAN permission matrix, App Link gate, and compression/negotiation behavior. Local evidence: Temurin 17.0.19; installed Android platforms 34/35/36, Build Tools 35.0.0/36.0.0, and API 35 AVD; repository identity/CI/native-module source inspection. External compatibility evidence links are embedded in `decisions.md`.
- 2026-08-08T12:30:10Z: Validation passed: `delano validate` completed with zero errors; its single warning was expected dirty `.project` provenance from this in-progress task.
- 2026-08-08T12:30:10Z: Self-review: cross-checked every acceptance item against D-011 through D-014, React Native and SwiftUI identities, official Android/Gradle/Kotlin/Compose/Expo/OkHttp sources, and current local/CI inventory; no emulator or generated Expo project was exercised for this decision-only task.

- 2026-08-08T12:28:19Z: Begin audited Android toolchain, identity, local-network, route-intake, and networking compatibility decisions.

- 2026-08-08T12:28:19Z: Readiness reviewed: dependency-free WS-A decision task is fully specified, repository and toolchain evidence is available, and baseline validation passes.

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
