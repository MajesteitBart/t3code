package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.ContractJson
import com.t3tools.t3code.compose.core.protocol.DispatchResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class StableTurnRecoveryTest {
  @Test
  public fun lostResponseConfirmedByFreshSnapshotNeverReplaysTransport() = runTest {
    val turn = turn()
    var dispatches = 0
    var verifications = 0
    val recovery = StableTurnRecovery(
      transport = StableTurnOneAttemptTransport {
        dispatches += 1
        throw StableTurnResponseAmbiguousFailure(IOException("response lost after acceptance"))
      },
      verifier = StableTurnCommitVerifier { threadId, messageId ->
        verifications += 1
        assertEquals(turn.threadId, threadId)
        assertEquals(turn.identity.messageId, messageId)
        StableTurnCommitVerification.COMMITTED
      },
    )

    val outcome = recovery.send(turn)

    assertTrue(outcome is StableTurnOutcome.ConfirmedCommitted)
    assertSame(turn, outcome.turn)
    assertEquals(1, dispatches)
    assertEquals(1, verifications)
  }

  @Test
  public fun absentAndUnavailableConfirmationPreserveAmbiguousIdentity() = runTest {
    val turn = turn()
    val cases = listOf(
      StableTurnCommitVerification.NOT_COMMITTED to StableTurnAmbiguity.COMMIT_NOT_VISIBLE,
      StableTurnCommitVerification.UNAVAILABLE to StableTurnAmbiguity.VERIFICATION_UNAVAILABLE,
    )

    cases.forEach { (verification, expectedReason) ->
      val recovery = StableTurnRecovery(
        transport = StableTurnOneAttemptTransport {
          throw StableTurnResponseAmbiguousFailure(IOException("ambiguous"))
        },
        verifier = StableTurnCommitVerifier { _, _ -> verification },
      )

      val outcome = recovery.send(turn)

      assertTrue(outcome is StableTurnOutcome.Ambiguous)
      outcome as StableTurnOutcome.Ambiguous
      assertSame(turn, outcome.turn)
      assertSame(turn.identity, outcome.turn.identity)
      assertEquals(expectedReason, outcome.reason)
    }
  }

  @Test
  public fun explicitRetryVerifiesFirstAndSendsTheIdenticalPayloadOnlyWhenAbsent() = runTest {
    val turn = turn()
    val events = mutableListOf<String>()
    val payloads = mutableListOf<String>()
    var dispatches = 0
    val recovery = StableTurnRecovery(
      transport = StableTurnOneAttemptTransport { payload ->
        events += "dispatch"
        payloads += ContractJson.format.encodeToString(payload)
        dispatches += 1
        if (dispatches == 1) {
          throw StableTurnResponseAmbiguousFailure(IOException("first response lost"))
        }
        DispatchResult(sequence = 42)
      },
      verifier = StableTurnCommitVerifier { _, _ ->
        events += "verify"
        StableTurnCommitVerification.NOT_COMMITTED
      },
    )

    val ambiguous = recovery.send(turn) as StableTurnOutcome.Ambiguous
    val retried = recovery.retry(ambiguous)

    assertTrue(retried is StableTurnOutcome.Accepted)
    retried as StableTurnOutcome.Accepted
    assertSame(turn, retried.turn)
    assertEquals(42, retried.sequence)
    assertEquals(listOf("dispatch", "verify", "verify", "dispatch"), events)
    assertEquals(listOf(turn.canonicalWirePayload, turn.canonicalWirePayload), payloads)
  }

  @Test
  public fun explicitRetryDoesNotDispatchWhenVerificationCommitsOrIsUnavailable() = runTest {
    val turn = turn()
    for (verification in listOf(
      StableTurnCommitVerification.COMMITTED,
      StableTurnCommitVerification.UNAVAILABLE,
    )) {
      var dispatches = 0
      val recovery = StableTurnRecovery(
        transport = StableTurnOneAttemptTransport {
          dispatches += 1
          DispatchResult(sequence = 99)
        },
        verifier = StableTurnCommitVerifier { _, _ -> verification },
      )
      val ambiguous = StableTurnOutcome.Ambiguous(
        turn,
        StableTurnAmbiguity.VERIFICATION_UNAVAILABLE,
      )

      val result = recovery.retry(ambiguous)

      assertEquals(0, dispatches)
      assertSame(turn, result.turn)
      if (verification == StableTurnCommitVerification.COMMITTED) {
        assertTrue(result is StableTurnOutcome.ConfirmedCommitted)
      } else {
        assertEquals(
          StableTurnAmbiguity.VERIFICATION_UNAVAILABLE,
          (result as StableTurnOutcome.Ambiguous).reason,
        )
      }
    }
  }

  @Test
  public fun actualCoroutineCancellationIsNeverConvertedIntoAmbiguity() = runTest {
    val cancellation = CancellationException("lifecycle cancelled")
    val recovery = StableTurnRecovery(
      transport = StableTurnOneAttemptTransport { throw cancellation },
      verifier = StableTurnCommitVerifier { _, _ -> StableTurnCommitVerification.COMMITTED },
    )

    val thrown = try {
      recovery.send(turn())
      throw AssertionError("Expected cancellation")
    } catch (failure: CancellationException) {
      failure
    }

    assertSame(cancellation, thrown)
  }

  @Test
  public fun nonAmbiguousDispatchFailureRemainsUnchanged() = runTest {
    val rejection = IllegalArgumentException("authoritative client rejection")
    val recovery = StableTurnRecovery(
      transport = StableTurnOneAttemptTransport { throw rejection },
      verifier = StableTurnCommitVerifier { _, _ -> StableTurnCommitVerification.COMMITTED },
    )

    val thrown = try {
      recovery.send(turn())
      throw AssertionError("Expected rejection")
    } catch (failure: IllegalArgumentException) {
      failure
    }

    assertSame(rejection, thrown)
  }

  private fun turn(): PreparedStableTurn = PreparedStableTurn.create(
    threadId = "thread-stable",
    identity = StableTurnIdentity(
      commandId = "command-stable",
      messageId = "message-stable",
      createdAt = "2026-08-08T00:00:02.000Z",
    ),
    text = "Keep this logical turn stable.",
  )
}
