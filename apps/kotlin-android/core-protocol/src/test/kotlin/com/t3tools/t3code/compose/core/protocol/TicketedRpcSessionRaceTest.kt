package com.t3tools.t3code.compose.core.protocol

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class TicketedRpcSessionRaceTest {
  private val json = ContractJson.format

  @Test
  public fun `close before ready invalidates queued work and ignores a late open`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("close-before-ready"))
      val connector = RaceConnector()
      val session = start(
        server,
        factory(connector, StandardTestDispatcher(testScheduler)),
      )
      val connection = connector.connections.single()
      val pending = async {
        runCatching { session.request(FoundationRpcMethod.DISPATCH_COMMAND, renameCommand()) }
      }
      runCurrent()

      session.close()
      runCurrent()
      connection.open()
      runCurrent()

      val failure = pending.await().exceptionOrNull()
      assertTrue(failure is RpcDisconnectedFailure)
      assertFalse((failure as RpcDisconnectedFailure).ambiguous)
      assertTrue(connection.sent.isEmpty())
      assertEquals(1, connection.cancelCount)
      assertEquals(RpcSessionClosureReason.LocalClose, session.closure.await().reason)
      assertEquals(1, connector.connectCount)
    }
  }

  @Test
  public fun `an already cancelled caller never installs or sends a request`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("pre-cancel-ticket"))
      val connector = RaceConnector()
      val session = start(
        server,
        factory(connector, StandardTestDispatcher(testScheduler)),
      )
      val connection = connector.connections.single()
      connection.open()
      runCurrent()
      val entered = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val pending = async {
        entered.complete(Unit)
        withContext(NonCancellable) { release.await() }
        session.request(FoundationRpcMethod.DISPATCH_COMMAND, renameCommand())
      }
      runCurrent()

      assertTrue(entered.isCompleted)
      pending.cancel()
      release.complete(Unit)
      runCurrent()

      assertTrue(pending.isCancelled)
      assertTrue(connection.sent.isEmpty())
      session.close()
    }
  }

  @Test
  public fun `malformed fatal output closes once and redacts the ticket from every failure`() = runTest {
    MockWebServer().use { server ->
      server.start()
      val ticket = "sensitive-ticket-value"
      server.enqueue(ticketResponse(ticket))
      val connector = RaceConnector()
      val session = start(
        server,
        factory(connector, StandardTestDispatcher(testScheduler)),
      )
      val connection = connector.connections.single()
      connection.open()
      runCurrent()
      val pending = async {
        runCatching { session.request(FoundationRpcMethod.DISPATCH_COMMAND, renameCommand()) }
      }
      runCurrent()

      connection.message(
        """
        {
          "_tag":"ClientProtocolError",
          "error":{"reason":{"message":"invalid wsTicket=$ticket ($ticket)"}}
        }
        """.trimIndent(),
      )
      runCurrent()
      val failure = pending.await().exceptionOrNull()
      val closure = session.closure.await()
      val closureFailure = (closure.reason as RpcSessionClosureReason.Failed).error

      assertTrue(failure is FatalRpcProtocolFailure)
      assertTrue(closureFailure is FatalRpcProtocolFailure)
      assertFalse((failure as FatalRpcProtocolFailure).safeMessage.contains(ticket))
      assertTrue(failure.safeMessage.contains("<redacted>"))
      assertFalse(closureFailure.message.orEmpty().contains(ticket))
      assertFalse(session.toString().contains(ticket))
      assertEquals(1, connection.cancelCount)

      connection.message(exitSuccess(1L))
      connection.fail(IOException("late failure"))
      runCurrent()
      assertEquals(1, connection.cancelCount)
      assertEquals(closure, session.closure.await())
    }
  }

  @Test
  public fun `malformed remote frame diagnostics redact ticket and access credentials`() = runTest {
    MockWebServer().use { server ->
      server.start()
      val ticket = "malformed-ticket-value"
      val credential = "direct-access"
      server.enqueue(ticketResponse(ticket))
      val connector = RaceConnector()
      val session = start(
        server,
        factory(connector, StandardTestDispatcher(testScheduler)),
      )
      val connection = connector.connections.single()
      connection.open()
      runCurrent()

      connection.message("{\"_tag\":\"unknown-$ticket-$credential\"}")
      runCurrent()
      val closureFailure = (session.closure.await().reason as RpcSessionClosureReason.Failed).error

      assertTrue(closureFailure is ProtocolViolationFailure)
      assertFalse(closureFailure.message.orEmpty().contains(ticket))
      assertFalse(closureFailure.message.orEmpty().contains(credential))
      assertTrue(closureFailure.message.orEmpty().contains("<redacted>"))
      assertEquals(1, connection.cancelCount)
    }
  }

  @Test
  public fun `concurrent subscribe cancel cycles leave no routable collectors or extra sockets`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("collector-ticket"))
      val connector = RaceConnector()
      val session = start(
        server,
        factory(connector, StandardTestDispatcher(testScheduler)),
      )
      val connection = connector.connections.single()
      connection.open()
      runCurrent()

      val collectors = (1..12).map {
        async {
          session.subscribeShell(
            OrchestrationSubscribeShellInput(requestCompletionMarker = true),
          ).collect()
        }
      }
      runCurrent()
      assertEquals(12, connection.sentFrames("Request").size)
      val requestIds = connection.sentFrames("Request").map { it.requiredLong("id") }
      assertEquals((1L..12L).toList(), requestIds)

      collectors.forEach { it.cancel() }
      runCurrent()
      assertTrue(collectors.all { it.isCancelled })
      assertEquals(requestIds, connection.sentFrames("Interrupt").map { it.requiredLong("requestId") })

      for (requestId in requestIds) connection.message(shellChunk(requestId))
      runCurrent()
      assertTrue(connection.sentFrames("Ack").isEmpty())
      assertEquals(1, connector.connectCount)

      session.close()
      assertEquals(1, connection.closeCount)
    }
  }

  @Test
  public fun `supervisor handoff remints and recreates only long lived intent with a fresh id`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("expired-ticket", "2020-01-01T00:00:00.000Z"))
      server.enqueue(ticketResponse("replacement-ticket", "2030-01-01T00:00:00.000Z"))
      val connector = RaceConnector()
      val factory = factory(connector, StandardTestDispatcher(testScheduler))
      val firstSession = start(server, factory)
      val firstConnection = connector.connections.single()
      firstConnection.open()
      runCurrent()
      val longLived = async {
        runCatching {
          firstSession.subscribeShell(
            OrchestrationSubscribeShellInput(requestCompletionMarker = true),
            RpcSubscriptionMode.LONG_LIVED_INTENT,
          ).collect()
        }
      }
      val oneShot = async {
        runCatching {
          firstSession.subscribeShell(
            OrchestrationSubscribeShellInput(requestCompletionMarker = true),
            RpcSubscriptionMode.ONE_SHOT,
          ).collect()
        }
      }
      val unary = async {
        runCatching { firstSession.request(FoundationRpcMethod.DISPATCH_COMMAND, renameCommand()) }
      }
      runCurrent()
      assertEquals(listOf(1L, 2L, 3L), firstConnection.sentFrames("Request").map { it.requiredLong("id") })

      firstConnection.fail(IOException("synthetic server restart"))
      runCurrent()
      assertTrue(longLived.await().exceptionOrNull() is RpcSubscriptionDisconnectedFailure)
      val oneShotFailure = oneShot.await().exceptionOrNull() as RpcSubscriptionDisconnectedFailure
      assertEquals(RpcSubscriptionMode.ONE_SHOT, oneShotFailure.mode)
      val unaryFailure = unary.await().exceptionOrNull() as RpcDisconnectedFailure
      assertTrue(unaryFailure.ambiguous)
      assertTrue(firstConnection.sentFrames("Interrupt").isEmpty())

      val replacement = start(server, factory)
      val secondConnection = connector.connections[1]
      secondConnection.open()
      runCurrent()
      val replacementCollector = async {
        replacement.subscribeShell(
          OrchestrationSubscribeShellInput(requestCompletionMarker = true),
          RpcSubscriptionMode.LONG_LIVED_INTENT,
        ).collect()
      }
      runCurrent()

      assertEquals(2, connector.connectCount)
      assertEquals("expired-ticket", connector.requests[0].url.queryParameter("wsTicket"))
      assertEquals("replacement-ticket", connector.requests[1].url.queryParameter("wsTicket"))
      assertEquals(listOf(4L), secondConnection.sentFrames("Request").map { it.requiredLong("id") })
      assertEquals(
        listOf(FoundationRpcMethod.SUBSCRIBE_SHELL.wireTag),
        secondConnection.sentFrames("Request").map { it.requiredString("tag") },
      )

      replacementCollector.cancel()
      runCurrent()
      replacement.close()
      assertEquals(listOf(4L), secondConnection.sentFrames("Interrupt").map { it.requiredLong("requestId") })
      assertEquals(1, firstConnection.cancelCount)
      assertEquals(1, secondConnection.closeCount)
    }
  }

  @Test
  public fun `response timeout owns one interrupt and late exit cannot complete the next request`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("deadline-ticket"))
      val connector = RaceConnector()
      val factory = factory(
        connector,
        StandardTestDispatcher(testScheduler),
        RpcSessionTimeouts(connectionWait = 2.seconds, response = 1.seconds, keepAlive = null),
      )
      val session = start(server, factory)
      val connection = connector.connections.single()
      connection.open()
      runCurrent()
      val first = async { runCatching { session.dispatch(renameCommand()) } }
      runCurrent()

      advanceTimeBy(1_000)
      runCurrent()
      assertTrue(first.await().exceptionOrNull() is RpcResponseTimeoutFailure)
      assertEquals(listOf(1L), connection.sentFrames("Interrupt").map { it.requiredLong("requestId") })

      connection.message(exitSuccess(1L))
      runCurrent()
      val second = async { session.dispatch(renameCommand()) }
      runCurrent()
      connection.message(exitSuccess(2L))
      runCurrent()

      assertEquals(9_007_199_254_740_991L, second.await().sequence)
      assertEquals(listOf(1L, 2L), connection.sentFrames("Request").map { it.requiredLong("id") })
      assertEquals(1, connector.connectCount)
      session.close()
    }
  }

  @Test
  public fun `method metadata forbids bootstrap fallback and one shot recreation`() {
    val bootstrap = buildJsonObject {
      put("type", "thread.turn.start")
      put("bootstrap", buildJsonObject { put("runSetupScript", true) })
    }

    assertEquals(
      RpcUnsentFallbackPolicy.WEBSOCKET_ONLY,
      RpcDispatchPolicy.unsentFallback(bootstrap),
    )
    assertEquals(
      RpcUnsentFallbackPolicy.EXPLICIT_HTTP_ALLOWED,
      RpcDispatchPolicy.unsentFallback(renameCommand()),
    )
    assertEquals(RpcReplacementPolicy.NEVER, FoundationRpcMethod.DISPATCH_COMMAND.replacementPolicy)
    assertEquals(
      RpcReplacementPolicy.SUPERVISOR_MAY_RECREATE_LONG_LIVED_INTENT,
      FoundationRpcMethod.SUBSCRIBE_SHELL.replacementPolicy,
    )
    assertTrue(RpcSubscriptionMode.LONG_LIVED_INTENT.supervisorMayRecreate)
    assertFalse(RpcSubscriptionMode.ONE_SHOT.supervisorMayRecreate)
  }

  private fun factory(
    connector: RaceConnector,
    dispatcher: CoroutineDispatcher,
    timeouts: RpcSessionTimeouts = RpcSessionTimeouts(keepAlive = null),
  ): TicketedRpcSessionFactory = TicketedRpcSessionFactory(
    connector = connector,
    timeouts = timeouts,
    callbackDispatcher = dispatcher,
  )

  private suspend fun start(
    server: MockWebServer,
    factory: TicketedRpcSessionFactory,
  ): TicketedRpcSession = factory.start(
    httpBaseUrl = server.url("/").toString(),
    webSocketBaseUrl = server.url("/").toString().replaceFirst("http://", "ws://"),
    credential = RedactedSecret.from("direct-access"),
  )

  private fun renameCommand(): JsonObject = buildJsonObject {
    put("type", "thread.meta.update")
    put("commandId", "command-fixture")
    put("threadId", "thread-fixture")
    put("title", "Renamed")
    put("createdAt", "2026-08-08T00:00:00.000Z")
  }

  private fun ticketResponse(
    ticket: String,
    expiresAt: String = "2026-08-08T15:00:00.000Z",
  ): MockResponse = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "application/json")
    .body("{\"ticket\":\"$ticket\",\"expiresAt\":\"$expiresAt\"}")
    .build()

  private fun shellChunk(requestId: Long): String =
    """
    {
      "_tag":"Chunk",
      "requestId":$requestId,
      "values":[{"kind":"synchronized"}]
    }
    """.trimIndent()

  private fun exitSuccess(requestId: Long): String =
    """
    {
      "_tag":"Exit",
      "requestId":$requestId,
      "exit":{"_tag":"Success","value":{"sequence":9007199254740991}}
    }
    """.trimIndent()

  private inner class RaceConnector : RpcWebSocketConnector {
    val requests = mutableListOf<Request>()
    val connections = mutableListOf<RaceConnection>()
    val connectCount: Int get() = connections.size

    override fun connect(request: Request, events: RpcWebSocketEvents): RpcWebSocketConnection {
      requests += request
      return RaceConnection(events).also(connections::add)
    }
  }

  private inner class RaceConnection(
    private val events: RpcWebSocketEvents,
  ) : RpcWebSocketConnection {
    val sent = mutableListOf<String>()
    var closeCount: Int = 0
      private set
    var cancelCount: Int = 0
      private set

    fun open() {
      events.onOpen(null)
    }

    fun message(value: String) {
      events.onText(value)
    }

    fun fail(error: Throwable) {
      events.onFailure(error, null)
    }

    fun sentFrames(tag: String): List<JsonObject> = sent
      .map { json.parseToJsonElement(it).jsonObject }
      .filter { it.requiredString("_tag") == tag }

    override fun send(text: String): Boolean {
      sent += text
      return true
    }

    override fun close(code: Int, reason: String?): Boolean {
      closeCount += 1
      return true
    }

    override fun cancel() {
      cancelCount += 1
    }
  }

  private fun JsonObject.requiredLong(name: String): Long =
    requireNotNull(this[name]).jsonPrimitive.content.toLong()

  private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]).jsonPrimitive.content
}
