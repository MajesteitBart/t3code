# T3 Code Compose for Android

This is the standalone Kotlin/Jetpack Compose client. Its Gradle build is independent of Expo prebuild, Metro, and `apps/mobile/android`; run every command below from `apps/kotlin-android`.

## Foundation product shell

The app now uses the verified protocol, persistence, supervision, and reconciliation modules as one composition root. It can:

- accept a pasted canonical pairing link, loose server-and-code input, copied `t3code://pair?pairingUrl=...` QR-wrapper text, or the build-specific `t3code-compose[-dev]://pair?pairingUrl=...` Android route;
- exchange and discard the one-time credential, protect only the resulting direct bearer with Android Keystore, and restore saved environments plus last-known shell rows from Room;
- activate, retry, re-pair, and remove saved environments while showing reachable, offline, reconnecting, revoked, and last-known states separately;
- render collision-safe project and thread navigation as a single pane below 600 dp, a retained environment/content layout from 600 dp, and a three-pane list/detail layout from 840 dp;
- stop live supervision when the activity is not foregrounded and collect UI state through the lifecycle-aware Compose boundary.

Pairing text is rendered as a password field and is cleared after exchange. Safe error surfaces may show a sanitized server trace ID, but never the one-time code, access credential, or WebSocket ticket. Arbitrary server URLs remain valid only when the user explicitly pastes them or they arrive inside the reviewed custom wrapper. No `http` or `https` App Link is registered: `app.t3.codes` still lacks Digital Asset Links for these package/signing identities, so unowned web hosts cannot inject a connection route.

The foundation shell intentionally stops at environment/project/thread state. Production transcript rendering, composer/send behavior, workspace tools, T3 Connect, push, and release remain outside the approved foundation contract.

## Prerequisites

- JDK 17, exposed through `JAVA_HOME` or Gradle's normal Java discovery.
- Android SDK Platform 36 and SDK Build Tools 35.0.0.
- Android SDK Platform Tools for install and device inspection.
- For instrumentation, a connected phone or phone emulator supported by the project (the foundation is verified on the API-35 `TwentyGoApi35` AVD).

The checked-in Gradle 8.13 wrapper downloads its distribution from Gradle and verifies the pinned SHA-256 checksum. No machine-installed Gradle, Node workspace install, Expo process, signing credential, pairing code, or T3 home directory is required. `foundationAssemble` produces an unsigned release-shaped APK; release signing remains follow-on release work.

The contract-conformance and disposable integration commands also require the repository's Node 24/pnpm workspace install. Contract conformance verifies checked-in Kotlin fixtures against the live canonical TypeScript schemas and installed Effect RPC protocol; integration starts the normal server runtime through a test-only control seam.

On Windows PowerShell, use `./gradlew.bat`; on macOS/Linux, substitute `./gradlew`.

## Focused commands

```powershell
cd apps/kotlin-android

# Build both the com.t3tools.t3code.compose.dev development APK and the
# com.t3tools.t3code.compose release-shaped APK.
./gradlew.bat foundationAssemble

# Run unit tests for app, core-data, core-protocol, and core-testing in both variants.
./gradlew.bat foundationUnitTest

# Run warnings-as-errors Android lint for every module and both variants.
./gradlew.bat foundationStaticCheck

# Verify canonical TypeScript/Effect provenance and Kotlin contract decoding.
./gradlew.bat foundationContractConformance

# Run transport races plus a real compressed OkHttp WebSocket round trip.
./gradlew.bat foundationTransportIntegration

# Run pure compensation/envelope tests plus API-device Room migration,
# Android Keystore, process-recreation, corruption, and deletion checks.
./gradlew.bat foundationPersistence

# Run pure scoped identity, reducer, active/passive supervision,
# reconciliation, stale-publication, and ambiguity-policy checks.
./gradlew.bat foundationConnectionState

# Start two isolated disposable T3 servers, seed colliding raw IDs, and verify
# snapshots, durable receipts, domain events, worker drains, and exact teardown.
./gradlew.bat foundationIntegration

# Install only the development variant on the selected connected device.
./gradlew.bat foundationInstallDevelopment

# Run connected Compose/instrumentation tests on the selected connected device.
./gradlew.bat foundationInstrumentation
```

These tasks are deliberately native-Android-only. They do not invoke the repository-wide test, lint, typecheck, Expo, or React Native pipelines.

`foundationPersistence` requires one selected connected Android device or emulator. It stores only synthetic test values in the instrumentation package sandbox and removes its database, preferences, and test-only Keystore entry after each test. The production storage and state contracts are documented in [`core-data/PERSISTENCE.md`](core-data/PERSISTENCE.md) and [`core-data/STATE.md`](core-data/STATE.md).

`foundationIntegration` creates two separate homes below the worktree's gitignored `.t3` directory, removes ambient T3/Vite routing variables from both child environments, and never touches live developer state. It captures and cross-checks each spawned PID, working directory, base directory, resolved port, and exact listener owner. Startup credentials remain in redacted in-memory wrappers; failure diagnostics redact token, bearer, credential, authorization, and pairing-fragment forms. Fixture HTTP remains one-attempt with redirects and retries disabled, while a zero-idle connection pool prevents setup from selecting a socket closed during a typed control/drain interval. Teardown uses only the two captured `Process` objects and removes only the validated fixture root.

## Reserved evidence gates

The following stable command names resolve today but intentionally fail closed until their owning task replaces the placeholder with real evidence. Listing or dry-running a task is not evidence that its gate passes.

```powershell
# T-016: TalkBack, traversal, touch-target, font-scale, and lifecycle checklist.
./gradlew.bat foundationAccessibility

# T-018: AC-008 instrumentation and measured performance budgets.
./gradlew.bat foundationPerformance
```

Future integration scenarios must reuse the isolated harness and its typed control operations instead of adding production receipt/debug APIs, timing sleeps, ambient homes, broad process scans, or transport retries.

## Useful inspection

```powershell
# Show all stable foundation entry points without executing them.
./gradlew.bat tasks --group build
./gradlew.bat tasks --group install
./gradlew.bat tasks --group verification

# Resolve every still-reserved gate without running its fail-closed action.
./gradlew.bat foundationAccessibility foundationPerformance --dry-run
```
