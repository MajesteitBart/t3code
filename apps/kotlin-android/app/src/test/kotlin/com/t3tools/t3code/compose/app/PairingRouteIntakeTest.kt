package com.t3tools.t3code.compose.app

import com.t3tools.t3code.compose.core.data.CredentialUnavailableFailure
import com.t3tools.t3code.compose.core.data.CredentialUnavailableReason
import com.t3tools.t3code.compose.core.protocol.PairingUrl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRouteIntakeTest {
  @Test
  fun buildSpecificWrapperAcceptsOnlyItsReviewedPairingRoute() {
    val pairingUrl = "http://10.0.2.2:13773/pair#token=one-time-secret"
    val encoded = URLEncoder.encode(pairingUrl, StandardCharsets.UTF_8.name())

    val accepted = PairingRouteIntake.parse(
      "t3code-compose-dev://pair?pairingUrl=$encoded",
      "t3code-compose-dev",
    ) as PairingRouteResult.Accepted

    assertEquals(pairingUrl, accepted.input.reveal())
    assertEquals("<redacted-pairing-input>", accepted.input.toString())
    assertTrue(
      PairingRouteIntake.parse(
        "https://unowned.example/pair?pairingUrl=$encoded",
        "t3code-compose-dev",
      ) is PairingRouteResult.Rejected,
    )
    assertTrue(
      PairingRouteIntake.parse(
        "t3code-compose-dev://other?pairingUrl=$encoded",
        "t3code-compose-dev",
      ) is PairingRouteResult.Rejected,
    )
  }

  @Test
  fun canonicalQrWrapperRemainsValidExplicitPasteInput() {
    val pairingUrl = "http://10.0.2.2:13773/pair#token=one-time-secret"
    val encoded = URLEncoder.encode(pairingUrl, StandardCharsets.UTF_8.name())

    val resolved = PairingUrl.resolve("t3code://pair?pairingUrl=$encoded")

    assertEquals("http://10.0.2.2:13773/", resolved.httpBaseUrl)
    assertFalse(resolved.toString().contains("one-time-secret"))
  }

  @Test
  fun failurePresentationDoesNotEchoUnknownDetailsAndRequiresRepairForMissingKeys() {
    val unknown = PairingFailurePresenter.present(IllegalStateException("never-show-this-secret"))
    val revoked = PairingFailurePresenter.present(
      CredentialUnavailableFailure(CredentialUnavailableReason.MISSING_KEY_MATERIAL),
    )

    assertFalse(unknown.message.contains("never-show-this-secret"))
    assertEquals(PairingFailureKind.REVOKED, revoked.kind)
    assertEquals(PairingRecoveryAction.PAIR_AGAIN, revoked.action)
  }
}
