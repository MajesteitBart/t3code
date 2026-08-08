# Tech Context

## Current Stack

- Monorepo runtime: Node.js `^24.13.1`, pnpm 11, Vite+ (`vp`), TypeScript, Effect, React, and Vite.
- Server: TypeScript/Effect, SQLite-backed event sourcing, authenticated Effect RPC over WebSocket, provider subprocess adapters, and queue-backed reactors.
- Web/desktop: React/Vite plus Electron.
- React Native mobile: Expo 56, React Native 0.85, shared `packages/client-runtime`, platform native modules in Swift/Kotlin/C++.
- SwiftUI mobile: iOS 17+, SwiftUI, native HTTP/WebSocket implementation, Keychain, WidgetKit, Share extension, Live Activities, Clerk, and Ghostty.
- Native Android foundation: standalone `apps/kotlin-android` with Kotlin/Compose plugin 2.3.21, Compose BOM 2026.06.01, Gradle 8.13, AGP 8.13.2, JDK 17, min SDK 24, and target/compile SDK 36. Modules are `app`, `core-protocol` (OkHttp 5.4.0, coroutines/serialization 1.11.0), `core-data` (Room 2.8.4), and `core-testing`. Keystore, WorkManager, Glance, and FCM remain later-task scope.

## Commands

```powershell
vp i
vp run dev
vp test run <focused-test-files>
vp run --filter <package> typecheck
node scripts/mobile-native-static-check.ts
npx -y @bvdm/delano@latest status --open --brief
npx -y @bvdm/delano@latest validate

cd apps/kotlin-android
./gradlew.bat foundationAssemble
./gradlew.bat foundationUnitTest
./gradlew.bat foundationStaticCheck
./gradlew.bat foundationContractConformance
./gradlew.bat foundationTransportIntegration
./gradlew.bat foundationInstallDevelopment
./gradlew.bat foundationInstrumentation
```

Do not run repo-wide checks unless explicitly requested. Native Android build, unit, static/lint, contract-conformance, transport-integration, install, and instrumentation entry points are live. The disposable real-server integration, accessibility, and performance entry points remain reserved fail-closed tasks until T-017, T-016, and T-018 replace them with evidence.

## Runtime Constraints

- Development is single-origin; never set `VITE_HTTP_URL` or `VITE_WS_URL`.
- Worktree-local `.t3` state must remain isolated from live developer state under `~/.t3/userdata`.
- The web client requires a pairing URL with its token.
- The SwiftUI reference requires macOS/Xcode to build; this Windows checkout can inspect it but cannot verify its Xcode targets.
- Local Android prerequisites are verified with Temurin 17, Android Platform 36, Build Tools 35.0.0, platform-tools, and the API-35 `TwentyGoApi35` phone AVD. Repository CI still lacks a standalone Android SDK/Gradle job.

## Integration Points

- `packages/contracts`: canonical HTTP/RPC and orchestration schemas.
- `packages/client-runtime`: reference behavior for shared web/React Native connection and state handling.
- `apps/server`: authentication, transport, event sourcing, provider adapters, Git, files, terminals, and checkpoints.
- `apps/swift-ios`: behavioral and native-client reference for the Android initiative.
- `apps/mobile/modules`: existing Android implementations audited in D-015. Review-diff/composer may later be adapted behind thin Expo adapters; terminal and native controls are deferred for the recorded reasons.
- `apps/kotlin-android`: independent Compose application and stable Android command surface; `core-protocol/CONTRACTS.md` records the canonical subset, provenance, and retry/cancellation ownership boundary.
- Clerk and the T3 relay: account authentication and managed environments.
- APNs today; FCM parity requires contract, relay persistence, and delivery changes rather than a client-only patch.
