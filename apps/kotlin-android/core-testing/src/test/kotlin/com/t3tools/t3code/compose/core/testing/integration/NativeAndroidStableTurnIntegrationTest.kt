package com.t3tools.t3code.compose.core.testing.integration

import com.t3tools.t3code.compose.core.data.FreshThreadSnapshotCommitVerifier
import com.t3tools.t3code.compose.core.data.PreparedStableTurn
import com.t3tools.t3code.compose.core.data.StableTurnIdentity
import com.t3tools.t3code.compose.core.data.StableTurnOneAttemptTransport
import com.t3tools.t3code.compose.core.data.StableTurnOutcome
import com.t3tools.t3code.compose.core.data.StableTurnRecovery
import com.t3tools.t3code.compose.core.data.StableTurnResponseAmbiguousFailure
import com.t3tools.t3code.compose.core.protocol.ContractJson
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class NativeAndroidStableTurnIntegrationTest {
  @Test
  public fun lostAcceptedResponseResolvesFromSnapshotWithoutReplayAndServerDedupesBackstop(): Unit =
    runBlocking {
      DisposableT3IntegrationHarness.start().use { harness ->
        val server = harness.first
        val turn = PreparedStableTurn.create(
          threadId = PRECREATED_TURN_THREAD_ID,
          identity = StableTurnIdentity(
            commandId = "native-android-stable-turn-command",
            messageId = "native-android-stable-turn-message",
            createdAt = "2026-08-08T00:00:02.000Z",
          ),
          text = "Prove one logical turn across an ambiguous response.",
        )
        val dispatchedPayloads = mutableListOf<String>()
        var dispatchAttempts = 0
        var freshSnapshotLoads = 0
        val recovery = StableTurnRecovery(
          transport = StableTurnOneAttemptTransport { payload ->
            dispatchAttempts += 1
            dispatchedPayloads += ContractJson.format.encodeToString(payload)
            val accepted = server.dispatch(payload)
            if (dispatchAttempts == 1) {
              throw StableTurnResponseAmbiguousFailure(
                IOException("Synthetic response loss after server acceptance."),
              )
            }
            accepted
          },
          verifier = FreshThreadSnapshotCommitVerifier { threadId ->
            freshSnapshotLoads += 1
            server.threadSnapshot(threadId)
          },
        )

        val recovered = recovery.send(turn)

        assertTrue(recovered is StableTurnOutcome.ConfirmedCommitted)
        assertSame(turn, recovered.turn)
        assertEquals(1, dispatchAttempts)
        assertEquals(1, freshSnapshotLoads)
        assertEquals(listOf(turn.canonicalWirePayload), dispatchedPayloads)

        server.drainWorkers()
        val firstSnapshot = server.threadSnapshot(PRECREATED_TURN_THREAD_ID)
        assertEquals(
          1,
          firstSnapshot.thread.messages.count { it.id == turn.identity.messageId },
        )
        val receipt = server.receipt(turn.identity.commandId)
        assertNotNull(receipt)
        requireNotNull(receipt)
        assertEquals("accepted", receipt.status)
        assertEquals("thread", receipt.aggregateKind)
        assertEquals(PRECREATED_TURN_THREAD_ID, receipt.aggregateId)
        val firstEvents = server.events(turn.identity.commandId)
        assertMatchingDomainEffects(firstEvents, turn.identity.messageId)

        val duplicateResult = server.dispatch(turn.wirePayload)
        assertEquals(receipt.resultSequence, duplicateResult.sequence)
        server.drainWorkers()

        val repeatedReceipt = server.receipt(turn.identity.commandId)
        assertEquals(receipt, repeatedReceipt)
        assertMatchingDomainEffects(server.events(turn.identity.commandId), turn.identity.messageId)
        val finalSnapshot = server.threadSnapshot(PRECREATED_TURN_THREAD_ID)
        assertEquals(
          1,
          finalSnapshot.thread.messages.count { it.id == turn.identity.messageId },
        )
      }
    }

  private fun assertMatchingDomainEffects(
    events: List<kotlinx.serialization.json.JsonObject>,
    messageId: String,
  ) {
    val messageEvents = events.filter { event ->
      event["type"]?.jsonPrimitive?.content == "thread.message-sent" &&
        event["payload"]?.jsonObject?.get("messageId")?.jsonPrimitive?.content == messageId
    }
    val turnEvents = events.filter { event ->
      event["type"]?.jsonPrimitive?.content == "thread.turn-start-requested" &&
        event["payload"]?.jsonObject?.get("messageId")?.jsonPrimitive?.content == messageId
    }
    assertEquals(1, messageEvents.size)
    assertEquals(1, turnEvents.size)
  }
}
