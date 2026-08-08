package com.t3tools.t3code.compose.core.protocol

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class TicketedRpcSessionTest {
  private val json = ContractJson.format

  @Test
  public fun `every attempt mints a fresh ticket and exposes only a redacted endpoint`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("ticket-one"))
      server.enqueue(ticketResponse("ticket-two"))
      val connector = ControlledConnector()
      val factory = factory(connector, StandardTestDispatcher(testScheduler))

      val first = factory.start(
        httpBaseUrl = server.url("/").toString(),
        webSocketBaseUrl = server.webSocketBase(),
        credential = RedactedSecret.from("direct-access"),
      )
      val second = factory.start(
        httpBaseUrl = server.url("/").toString(),
        webSocketBaseUrl = server.webSocketBase(),
        credential = RedactedSecret.from("direct-access"),
      )

      assertEquals(2, server.requestCount)
      assertEquals(2, connector.requests.size)
      val firstTicket = connector.requests[0].url.queryParameter("wsTicket")
      val secondTicket = connector.requests[1].url.queryParameter("wsTicket")
      assertEquals("ticket-one", firstTicket)
      assertEquals("ticket-two", secondTicket)
      assertNotEquals(firstTicket, secondTicket)
      assertFalse(first.toString().contains("ticket-one"))
      assertTrue(first.redactedEndpoint.contains("wsTicket=<redacted>"))

      first.close()
      first.close()
      second.close()
      assertEquals(1, connector.connections[0].cancelCount)
      assertEquals(1, connector.connections[1].cancelCount)
      assertEquals(first.closure.await(), first.closure.await())
      assertEquals(2, connector.connectCount)
    }
  }

  @Test
  public fun `provably unsent unary fails only at the configured connection deadline`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("unsent-ticket"))
      val connector = ControlledConnector()
      val factory = factory(
        connector,
        StandardTestDispatcher(testScheduler),
        RpcSessionTimeouts(connectionWait = 1.seconds, response = 10.seconds, keepAlive = null),
      )
      val session = start(factory, server)
      val pending = async {
        runCatching { session.request(FoundationRpcMethod.DISPATCH_COMMAND, archiveCommand()) }
      }
      runCurrent()

      advanceTimeBy(999)
      runCurrent()
      assertFalse(pending.isCompleted)
      advanceTimeBy(1)
      runCurrent()

      assertTrue(pending.await().exceptionOrNull() is RpcConnectionUnavailableFailure)
      assertTrue(connector.connections.single().sent.isEmpty())
      session.close()
    }
  }

  @Test
  public fun `a sent unary becomes ambiguous on disconnect and is never replayed`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("sent-ticket"))
      val connector = ControlledConnector()
      val factory = factory(connector, StandardTestDispatcher(testScheduler))
      val session = start(factory, server)
      val connection = connector.connections.single()
      connection.open()
      runCurrent()

      val pending = async {
        runCatching { session.request(FoundationRpcMethod.DISPATCH_COMMAND, archiveCommand()) }
      }
      runCurrent()
      assertEquals(listOf("Request"), connection.sentTags())

      connection.fail(IOException("synthetic disconnect"))
      runCurrent()
      val failure = pending.await().exceptionOrNull()

      assertTrue(failure is RpcDisconnectedFailure)
      val disconnected = failure as RpcDisconnectedFailure
      assertTrue(disconnected.ambiguous)
      assertEquals(1L, disconnected.requestId)
      assertEquals(listOf("Request"), connection.sentTags())
      assertEquals(1, connector.connectCount)
      assertEquals(1, connection.cancelCount)
      assertTrue(session.closure.await().opened)
    }
  }

  @Test
  public fun `cancellation interrupts only the request owned by the open generation`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("cancel-ticket"))
      val connector = ControlledConnector()
      val factory = factory(connector, StandardTestDispatcher(testScheduler))
      val session = start(factory, server)
      val connection = connector.connections.single()
      connection.open()
      runCurrent()

      val pending = async {
        session.request(FoundationRpcMethod.DISPATCH_COMMAND, archiveCommand())
      }
      runCurrent()
      pending.cancel()
      runCurrent()

      assertTrue(pending.isCancelled)
      assertEquals(listOf("Request", "Interrupt"), connection.sentTags())
      assertEquals(1L, connection.sentEnvelope(1)["requestId"]?.jsonPrimitive?.content?.toLong())

      connection.fail(IOException("late disconnect"))
      runCurrent()
      assertEquals(listOf("Request", "Interrupt"), connection.sentTags())
      assertEquals(1, connector.connectCount)
    }
  }

  @Test
  public fun `consumed shell chunks are acked and one-shot streams terminate without resubscribe`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("stream-ticket"))
      val connector = ControlledConnector()
      val factory = factory(connector, StandardTestDispatcher(testScheduler))
      val session = start(factory, server)
      val connection = connector.connections.single()
      connection.open()
      runCurrent()
      val received = mutableListOf<ShellStreamDecodeResult>()
      val collection = async {
        runCatching {
          session.subscribeShell(
            OrchestrationSubscribeShellInput(requestCompletionMarker = true),
            RpcSubscriptionMode.ONE_SHOT,
          ).collect(received::add)
        }
      }
      runCurrent()
      assertEquals(listOf("Request"), connection.sentTags())

      val chunk = fixture("rpc/chunk-shell-snapshot.json").let(json::parseToJsonElement).jsonObject
      connection.message(JsonObject(chunk + ("requestId" to JsonPrimitive(1L))).toString())
      runCurrent()
      assertEquals(1, received.size)
      assertEquals(listOf("Request", "Ack"), connection.sentTags())

      connection.fail(IOException("stream disconnected"))
      runCurrent()
      val failure = collection.await().exceptionOrNull()
      assertTrue(failure is RpcSubscriptionDisconnectedFailure)
      assertEquals(RpcSubscriptionMode.ONE_SHOT, (failure as RpcSubscriptionDisconnectedFailure).mode)
      assertEquals(1, connector.connectCount)
      assertEquals(listOf("Request", "Ack"), connection.sentTags())
    }
  }

  @Test
  public fun `application ping and pong liveness stays within the same attempt`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(ticketResponse("liveness-ticket"))
      val connector = ControlledConnector()
      val factory = factory(
        connector,
        StandardTestDispatcher(testScheduler),
        RpcSessionTimeouts(connectionWait = 4.seconds, response = 30.seconds, keepAlive = 1.seconds),
      )
      val session = start(factory, server)
      val connection = connector.connections.single()
      connection.open("permessage-deflate")
      runCurrent()

      assertEquals("permessage-deflate", session.openInfo.await().negotiatedExtensions)
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(listOf("Ping"), connection.sentTags())

      connection.message("{\"_tag\":\"Ping\"}")
      runCurrent()
      assertEquals(listOf("Ping", "Pong"), connection.sentTags())
      assertEquals(1, connector.connectCount)
      session.close()
    }
  }

  private fun factory(
    connector: ControlledConnector,
    dispatcher: CoroutineDispatcher,
    timeouts: RpcSessionTimeouts = RpcSessionTimeouts(keepAlive = null),
  ): TicketedRpcSessionFactory = TicketedRpcSessionFactory(
    connector = connector,
    timeouts = timeouts,
    callbackDispatcher = dispatcher,
  )

  private suspend fun start(
    factory: TicketedRpcSessionFactory,
    server: MockWebServer,
  ): TicketedRpcSession = factory.start(
    httpBaseUrl = server.url("/").toString(),
    webSocketBaseUrl = server.webSocketBase(),
    credential = RedactedSecret.from("direct-access"),
  )

  private fun archiveCommand(): JsonObject = buildJsonObject {
    put("type", "thread.archive")
    put("commandId", "command-fixture")
    put("threadId", "thread-fixture")
  }

  private fun ticketResponse(ticket: String): MockResponse = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "application/json")
    .body("{\"ticket\":\"$ticket\",\"expiresAt\":\"2026-08-08T14:00:00.000Z\"}")
    .build()

  private fun MockWebServer.webSocketBase(): String =
    url("/").toString().replaceFirst("http://", "ws://")

  private fun fixture(path: String): String = requireNotNull(
    javaClass.classLoader?.getResourceAsStream("contracts/foundation/$path"),
  ).bufferedReader(Charsets.UTF_8).use { it.readText() }

  private inner class ControlledConnector : RpcWebSocketConnector {
    val requests = mutableListOf<Request>()
    val connections = mutableListOf<ControlledConnection>()
    val connectCount: Int get() = connections.size

    override fun connect(request: Request, events: RpcWebSocketEvents): RpcWebSocketConnection {
      requests += request
      return ControlledConnection(events).also(connections::add)
    }
  }

  private inner class ControlledConnection(
    private val events: RpcWebSocketEvents,
  ) : RpcWebSocketConnection {
    val sent = mutableListOf<String>()
    var closeCount = 0
      private set
    var cancelCount = 0
      private set

    fun open(extensions: String? = null) {
      events.onOpen(extensions)
    }

    fun message(value: String) {
      events.onText(value)
    }

    fun fail(error: Throwable) {
      events.onFailure(error, null)
    }

    fun sentTags(): List<String> = sent.map {
      json.parseToJsonElement(it).jsonObject.requiredString("_tag")
    }

    fun sentEnvelope(index: Int): JsonObject = json.parseToJsonElement(sent[index]).jsonObject

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

    private fun JsonObject.requiredString(name: String): String =
      requireNotNull(this[name]).jsonPrimitive.content
  }
}
