plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.t3tools.t3code.compose.core.testing"
  compileSdk = 36
  buildToolsVersion = "35.0.0"

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = true
    warningsAsErrors = true
  }

  testOptions {
    // This host-only fixture uses OkHttp, whose platform probe calls
    // android.util.Log when android.jar is present on the unit-test classpath.
    unitTests.isReturnDefaultValues = true
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(platform(libs.androidx.compose.bom))
  api(libs.androidx.compose.ui.test.junit4)
  api(libs.androidx.test.runner)
  api(libs.androidx.test.ext.junit)
  api(libs.androidx.test.espresso.core)
  api(libs.junit)
  api(libs.kotlinx.coroutines.test)
  api(libs.okhttp.mockwebserver)

  testImplementation(project(":core-data"))
  testImplementation(project(":core-protocol"))
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.json)
}

val repositoryRootPath = rootProject.layout.projectDirectory.dir("../..").asFile.absolutePath

tasks.withType<Test>().configureEach {
  if (name == "testDebugUnitTest" || name == "testReleaseUnitTest") {
    exclude("**/integration/**")
  }
}

val nativeAndroidIntegrationTest = tasks.register<Test>("nativeAndroidIntegrationTest") {
  group = "verification"
  description = "Runs the disposable two-server native Android integration fixture."
  dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
  include("**/integration/**")
  systemProperty("t3.repositoryRoot", repositoryRootPath)
  outputs.upToDateWhen { false }
}

afterEvaluate {
  val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
  nativeAndroidIntegrationTest.configure {
    testClassesDirs = debugUnitTest.testClassesDirs
    classpath = debugUnitTest.classpath
  }
}
