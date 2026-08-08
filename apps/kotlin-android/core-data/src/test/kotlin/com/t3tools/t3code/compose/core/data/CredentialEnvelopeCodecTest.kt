package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

public class CredentialEnvelopeCodecTest {
  @Test
  public fun directBearerRoundTripsWithoutLeakingThroughToString() {
    val secretValue = "direct-secret-for-test"
    val credential = EnvironmentCredential.directBearer(RedactedSecret.from(secretValue))

    val decoded = CredentialEnvelopeCodec.decode(CredentialEnvelopeCodec.encode(credential))

    assertEquals(CredentialKind.DIRECT_BEARER, decoded.kind)
    assertEquals(secretValue, decoded.secret.reveal())
    assertFalse(decoded.toString().contains(secretValue))
  }

  @Test
  public fun unknownCredentialKindFailsClosed() {
    val failure = captureUnavailable {
      CredentialEnvelopeCodec.decode(
        """{"version":1,"kind":"managed-dpop","secret":"not-used"}""".encodeToByteArray(),
      )
    }

    assertEquals(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE, failure.reason)
  }

  @Test
  public fun corruptOrBlankEnvelopeFailsClosed() {
    val malformed = captureUnavailable {
      CredentialEnvelopeCodec.decode("not-json".encodeToByteArray())
    }
    val blank = captureUnavailable {
      CredentialEnvelopeCodec.decode(
        """{"version":1,"kind":"direct-bearer","secret":""}""".encodeToByteArray(),
      )
    }

    assertEquals(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE, malformed.reason)
    assertEquals(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE, blank.reason)
  }

  private fun captureUnavailable(block: () -> Unit): CredentialUnavailableFailure = try {
    block()
    throw AssertionError("Expected CredentialUnavailableFailure")
  } catch (error: CredentialUnavailableFailure) {
    error
  }
}
