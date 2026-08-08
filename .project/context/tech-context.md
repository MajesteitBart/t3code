# Tech Context

## Current Stack

- Monorepo runtime: Node.js `^24.13.1`, pnpm 11, Vite+ (`vp`), TypeScript, Effect, React, and Vite.
- Server: TypeScript/Effect, SQLite-backed event sourcing, authenticated Effect RPC over WebSocket, provider subprocess adapters, and queue-backed reactors.
- Web/desktop: React/Vite plus Electron.
- React Native mobile: Expo 56, React Native 0.85, shared `packages/client-runtime`, platform native modules in Swift/Kotlin/C++.
- SwiftUI mobile: iOS 17+, SwiftUI, native HTTP/WebSocket implementation, Keychain, WidgetKit, Share extension, Live Activities, Clerk, and Ghostty.
- Proposed native Android: Kotlin, Jetpack Compose/Material 3, coroutines/Flow, OkHttp, Kotlin serialization, Room, Android Keystore, WorkManager, Glance, and optionally FCM. These choices are planning decisions until approved in the native Android project.

## Commands

```powershell
vp i
vp run dev
vp test run <focused-test-files>
vp run --filter <package> typecheck
node scripts/mobile-native-static-check.ts
npx -y @bvdm/delano@latest status --open --brief
npx -y @bvdm/delano@latest validate
```

Do not run repo-wide checks unless explicitly requested. The native Android project must add focused Gradle build, unit-test, instrumentation-test, lint, and benchmark entry points before implementation is considered ready.

## Runtime Constraints

- Development is single-origin; never set `VITE_HTTP_URL` or `VITE_WS_URL`.
- Worktree-local `.t3` state must remain isolated from live developer state under `~/.t3/userdata`.
- The web client requires a pairing URL with its token.
- The SwiftUI reference requires macOS/Xcode to build; this Windows checkout can inspect it but cannot verify its Xcode targets.
- Android build prerequisites and CI image availability have not yet been audited.
- The current branch is based on PR #5178 and is not rebased onto the newest upstream main.

## Integration Points

- `packages/contracts`: canonical HTTP/RPC and orchestration schemas.
- `packages/client-runtime`: reference behavior for shared web/React Native connection and state handling.
- `apps/server`: authentication, transport, event sourcing, provider adapters, Git, files, terminals, and checkpoints.
- `apps/swift-ios`: behavioral and native-client reference for the Android initiative.
- `apps/mobile/modules`: existing Android terminal, diff, composer, and control implementations that may be extracted behind stable Android library boundaries.
- Clerk and the T3 relay: account authentication and managed environments.
- APNs today; FCM parity requires contract, relay persistence, and delivery changes rather than a client-only patch.
