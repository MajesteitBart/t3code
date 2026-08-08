# Native Android Source Material

## User Direction

- On 2026-08-07, the user selected Option 1 from the Android analysis: a standalone native Android client equivalent in architecture and capability to the SwiftUI experiment.
- The initial setup request authorized planning artifacts only. On 2026-08-08, the user separately authorized full WS-A implementation, focused Gradle/build and emulator evidence, a commit, and a push of the current branch; the user explicitly prohibited opening a pull request.
- On 2026-08-08, the user separately authorized full WS-B contracts/transport implementation, real Gradle/contract/integration evidence, a commit, and a push of the current branch; the user again explicitly prohibited a pull request.

## Primary Reference

- GitHub PR #5178, `feat(ios): add experimental SwiftUI client`.
- The PR was confirmed open and unmerged on 2026-08-08; do not describe it as shipped.
- Inspected PR head: `54d15002a28e4cfc0e3d2051f04694ae1b681a79`.
- Merge-base inspection found 159 changed files and 49,657 additions; 154 files live under `apps/swift-ios`.
- `apps/swift-ios/README.md` documents the client surface, identities, platform integrations, build configuration, verification, and release checklist.

## Repository References

- `docs/internals/overview.md`: RPC, client runtime, event sourcing, provider, checkpoint, and startup architecture.
- `packages/contracts/`: canonical wire schemas and RPC groups.
- `packages/client-runtime/`: web and React Native connection/state reference.
- `apps/mobile/`: shipped React Native Android implementation and existing Kotlin/C++ native components.
- `apps/mobile/modules/t3-terminal/android/`: Ghostty-backed Android terminal implementation.
- `apps/mobile/modules/t3-review-diff/android/`: Android review-diff renderer.
- `apps/mobile/modules/t3-composer-editor/android/`: Android composer editor.
- `apps/swift-ios/`: native-client behavior and test inventory.
- `.project/projects/native-android-foundation/research/swiftui-pr5178-review-assessment/findings.md`: claim-by-claim assessment of the deep-review note and the authoritative fold-forward rationale.
- `packages/contracts/src/relay.ts` and `infra/relay/`: current relay/APNs boundary relevant to future FCM support.

## Analysis Conclusions

- Most user-visible product flows already exist for Android through React Native; the new project is justified by the explicit decision to evaluate a separate native client, not by absence of Android functionality.
- A native Android client should share server contracts and user outcomes, but use Android-native interaction and lifecycle patterns.
- Foundation's highest risks are contract drift, sent-versus-unsent replay safety, active/passive state ownership, compensated credential/catalog persistence, target-SDK local-network privacy, and reviewability. DPoP/push remain later-project risks.
- Delivery should be vertical and incremental; copying the Swift file structure or its largest adapter one-to-one is not an architecture plan.
- Transfer canonical wire shapes and cross-client invariants; do not transfer Swift timers, actor mechanisms, full feature scope, or iOS visual constants without Android evidence.
- T-004 measured the existing Kotlin boundaries: review-diff and composer are adapter-first `adapt` candidates; terminal is deferred behind Ghostty/JNI/toolchain/ABI gates; native controls should be implemented directly in Compose. D-015 is the canonical audit result.
