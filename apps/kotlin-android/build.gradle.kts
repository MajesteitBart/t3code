plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
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

registerReservedGate(
  name = "foundationContractConformance",
  owner = "T-005",
  purpose = "AC-007 canonical contract provenance and drift checks",
)
registerReservedGate(
  name = "foundationIntegration",
  owner = "T-017",
  purpose = "the disposable server integration harness",
)
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
