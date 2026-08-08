plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
}

val androidModules = listOf(":app", ":core-data", ":core-protocol", ":core-testing")
val buildVariants = listOf("Debug", "Release")

tasks.register("foundationAssemble") {
  group = "build"
  description = "Assembles the development and release-shaped application variants."
  dependsOn(":app:assembleDebug", ":app:assembleRelease")
}

tasks.register("foundationInstallDevelopment") {
  group = "install"
  description = "Installs the development application on the selected Android device."
  dependsOn(":app:installDebug")
}

tasks.register("foundationUnitTest") {
  group = "verification"
  description = "Runs debug and release unit tests for every native Android module."
  dependsOn(
    androidModules.flatMap { module ->
      buildVariants.map { variant -> "$module:test${variant}UnitTest" }
    },
  )
}

tasks.register("foundationStaticCheck") {
  group = "verification"
  description = "Runs warnings-as-errors Android lint for every module and build variant."
  dependsOn(
    androidModules.flatMap { module ->
      buildVariants.map { variant -> "$module:lint$variant" }
    },
  )
}

tasks.register("foundationInstrumentation") {
  group = "verification"
  description = "Runs native Android instrumentation on the selected connected device."
  dependsOn(":app:connectedDebugAndroidTest")
}

fun registerReservedGate(name: String, owner: String, purpose: String) {
  tasks.register(name) {
    group = "verification"
    description = "Reserved for $purpose; implementation is owned by $owner."
    doLast {
      throw GradleException(
        "$name is a fail-closed reserved gate. $owner must replace this placeholder before evidence may be claimed.",
      )
    }
  }
}

val repositoryRoot = rootProject.layout.projectDirectory.dir("../..")

tasks.register<Exec>("verifyNativeAndroidContractFixtures") {
  group = "verification"
  description = "Checks native Android fixtures against canonical TypeScript and Effect RPC sources."
  workingDir(repositoryRoot)
  commandLine("node", "scripts/export-native-android-contract-fixtures.ts", "--check")
  outputs.upToDateWhen { false }
}

tasks.register("foundationContractConformance") {
  group = "verification"
  description = "Runs canonical fixture drift checks and Kotlin contract decoding tests."
  dependsOn(
    "verifyNativeAndroidContractFixtures",
    ":core-protocol:testDebugUnitTest",
    ":core-protocol:testReleaseUnitTest",
  )
}

tasks.register("foundationTransportIntegration") {
  group = "verification"
  description = "Runs deterministic transport races and the real OkHttp compressed WebSocket round trip."
  dependsOn("verifyNativeAndroidContractFixtures", ":core-protocol:testDebugUnitTest")
}

tasks.register("foundationPersistence") {
  group = "verification"
  description = "Runs Room/Keystore persistence tests, including device-backed process recreation."
  dependsOn(
    ":core-data:testDebugUnitTest",
    ":core-data:testReleaseUnitTest",
    ":core-data:connectedDebugAndroidTest",
  )
}

tasks.register("foundationConnectionState") {
  group = "verification"
  description = "Runs scoped-state, supervision, reconciliation, and ambiguity policy tests."
  dependsOn(
    ":core-data:testDebugUnitTest",
    ":core-data:testReleaseUnitTest",
    ":core-protocol:testDebugUnitTest",
    ":core-protocol:testReleaseUnitTest",
  )
}

tasks.register("foundationIntegration") {
  group = "verification"
  description = "Runs the disposable two-server T3 integration harness."
  dependsOn(":core-testing:nativeAndroidIntegrationTest")
}
registerReservedGate(
  name = "foundationAccessibility",
  owner = "T-016",
  purpose = "the focused accessibility and Compose lifecycle checklist",
)
registerReservedGate(
  name = "foundationPerformance",
  owner = "T-018",
  purpose = "AC-008 measured instrumentation and performance evidence",
)
