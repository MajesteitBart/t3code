plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
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
}
