plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.t3tools.t3code.compose.app"
  compileSdk = 36
  buildToolsVersion = "35.0.0"

  defaultConfig {
    applicationId = "com.t3tools.t3code.compose"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0-foundation"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["pairingScheme"] = "t3code-compose"
    resValue("string", "app_name", "T3 Code Compose")
    buildConfigField("String", "PAIRING_SCHEME", "\"t3code-compose\"")
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".dev"
      versionNameSuffix = "-dev"
      manifestPlaceholders["pairingScheme"] = "t3code-compose-dev"
      resValue("string", "app_name", "T3 Compose Dev")
      buildConfigField("String", "PAIRING_SCHEME", "\"t3code-compose-dev\"")
    }

    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures {
    buildConfig = true
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = true
    informational +=
      setOf(
        "AndroidGradlePluginVersion",
        "GradleDependency",
        "NewerVersionAvailable",
      )
    warningsAsErrors = true
  }

}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":core-data"))
  implementation(project(":core-protocol"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)

  androidTestImplementation(project(":core-testing"))
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
