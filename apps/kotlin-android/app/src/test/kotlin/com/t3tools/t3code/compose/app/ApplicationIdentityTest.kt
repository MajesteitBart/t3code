package com.t3tools.t3code.compose.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApplicationIdentityTest {
  @Test
  fun applicationIdentityIsIsolatedFromExistingClients() {
    val expectedApplicationId =
      if (BuildConfig.DEBUG) "com.t3tools.t3code.compose.dev" else "com.t3tools.t3code.compose"
    val expectedScheme = if (BuildConfig.DEBUG) "t3code-compose-dev" else "t3code-compose"

    assertEquals(expectedApplicationId, BuildConfig.APPLICATION_ID)
    assertEquals(expectedScheme, BuildConfig.PAIRING_SCHEME)
    assertFalse(BuildConfig.APPLICATION_ID in EXISTING_CLIENT_IDENTITIES)
  }

  private companion object {
    val EXISTING_CLIENT_IDENTITIES =
      setOf(
        "com.t3tools.t3code",
        "com.t3tools.t3code.dev",
        "com.t3tools.t3code.preview",
        "com.t3tools.t3code.swiftui",
        "com.t3tools.t3code.swiftui.dev",
      )
  }
}
