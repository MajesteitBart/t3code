package com.t3tools.t3code.compose.core.testing.integration

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class NativeAndroidTwoServerIntegrationTest {
  @Test
  public fun startsSeedsObservesAndTearsDownTwoIsolatedServers(): Unit = runBlocking {
    lateinit var fixtureRoot: Path
    lateinit var first: DisposableT3Server
    lateinit var second: DisposableT3Server

    DisposableT3IntegrationHarness.start().use { harness ->
      fixtureRoot = harness.fixtureRoot
      first = harness.first
      second = harness.second

      assertTrue(first.isAlive)
      assertTrue(second.isAlive)
      assertNotEquals(first.pid, second.pid)
      assertNotEquals(first.port, second.port)
      assertNotEquals(first.baseDirectory, second.baseDirectory)
      assertTrue(first.baseDirectory.startsWith(fixtureRoot))
      assertTrue(second.baseDirectory.startsWith(fixtureRoot))
      assertEquals("<redacted>", first.pairing.accessCredential.toString())
      assertEquals("<redacted>", second.pairing.accessCredential.toString())

      listOf(first, second).forEach { server ->
        val httpSnapshot = server.shellSnapshot()
        val controlledSnapshot = server.controlledShellSnapshot()
        assertEquals(httpSnapshot.snapshotSequence, controlledSnapshot.snapshotSequence)
        assertEquals(
          setOf(COLLIDING_PROJECT_ID),
          controlledSnapshot.projects.map { it.getValue("id").jsonPrimitive.content }.toSet(),
        )
        assertEquals(
          setOf(COLLIDING_THREAD_ID, PRECREATED_TURN_THREAD_ID),
          controlledSnapshot.threads.map { it.getValue("id").jsonPrimitive.content }.toSet(),
        )

        val receipt = server.receipt("${server.name}-thread-create")
        assertNotNull(receipt)
        requireNotNull(receipt)
        assertEquals("accepted", receipt.status)
        assertEquals(COLLIDING_THREAD_ID, receipt.aggregateId)
        val events = server.events("${server.name}-thread-create")
        assertEquals(1, events.size)
        assertEquals("thread.created", events.single().getValue("type").jsonPrimitive.content)
        assertEquals(
          receipt.resultSequence,
          events.single().getValue("sequence").jsonPrimitive.content.toLong(),
        )

        val drain = server.drainWorkers()
        assertEquals(controlledSnapshot.snapshotSequence, drain.snapshotSequence)
        assertTrue(drain.latestSequence >= drain.snapshotSequence)
        assertFalse(server.redactedDiagnostics().any { it.contains("#token=") && !it.contains("<redacted>") })
      }

      val firstProjectTitle = first.controlledShellSnapshot().projects.single()
        .getValue("title").jsonPrimitive.content
      val secondProjectTitle = second.controlledShellSnapshot().projects.single()
        .getValue("title").jsonPrimitive.content
      assertNotEquals(firstProjectTitle, secondProjectTitle)
    }

    assertFalse(first.isAlive)
    assertFalse(second.isAlive)
    assertFalse(Files.exists(fixtureRoot))
  }

  @Test
  public fun diagnosticRedactionRemovesEveryCredentialShape() {
    val secret = "native-integration-secret-value"
    val redacted = HarnessDiagnostics.redact(
      "Pairing URL: http://127.0.0.1:3773/pair#token=$secret " +
        "Authorization: Bearer $secret credential=$secret",
    )

    assertFalse(redacted.contains(secret))
    assertTrue(redacted.contains("<redacted>"))
  }
}
