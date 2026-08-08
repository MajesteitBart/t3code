# T3 Code Compose for Android

This is the standalone Kotlin/Jetpack Compose client. Its Gradle build is independent of Expo prebuild, Metro, and `apps/mobile/android`; run every command below from `apps/kotlin-android`.

## Prerequisites

- JDK 17, exposed through `JAVA_HOME` or Gradle's normal Java discovery.
- Android SDK Platform 36 and SDK Build Tools 35.0.0.
- Android SDK Platform Tools for install and device inspection.
- For instrumentation, a connected phone or phone emulator supported by the project (the foundation is verified on the API-35 `TwentyGoApi35` AVD).

The checked-in Gradle 8.13 wrapper downloads its distribution from Gradle and verifies the pinned SHA-256 checksum. No machine-installed Gradle, Node workspace install, Expo process, signing credential, pairing code, or T3 home directory is required. `foundationAssemble` produces an unsigned release-shaped APK; release signing remains follow-on release work.

The contract-conformance command is the one exception: it also requires the repository's Node 24/pnpm workspace install because it verifies checked-in Kotlin fixtures against the live canonical TypeScript schemas and installed Effect RPC protocol.

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

# Install only the development variant on the selected connected device.
./gradlew.bat foundationInstallDevelopment

# Run connected Compose/instrumentation tests on the selected connected device.
./gradlew.bat foundationInstrumentation
```

These tasks are deliberately native-Android-only. They do not invoke the repository-wide test, lint, typecheck, Expo, or React Native pipelines.

## Reserved evidence gates

The following stable command names resolve today but intentionally fail closed until their owning task replaces the placeholder with real evidence. Listing or dry-running a task is not evidence that its gate passes.

```powershell
# T-017: disposable, isolated T3 server integration harness.
./gradlew.bat foundationIntegration

# T-016: TalkBack, traversal, touch-target, font-scale, and lifecycle checklist.
./gradlew.bat foundationAccessibility

# T-018: AC-008 instrumentation and measured performance budgets.
./gradlew.bat foundationPerformance
```

Future integration helpers must require an explicitly isolated home and port, print the exact PID they start, redact credentials/tickets/pairing material, and stop only that captured PID. They must never default to or mutate live T3 developer state.

## Useful inspection

```powershell
# Show all stable foundation entry points without executing them.
./gradlew.bat tasks --group build
./gradlew.bat tasks --group install
./gradlew.bat tasks --group verification

# Resolve every still-reserved gate without running its fail-closed action.
./gradlew.bat foundationIntegration foundationAccessibility foundationPerformance --dry-run
```
