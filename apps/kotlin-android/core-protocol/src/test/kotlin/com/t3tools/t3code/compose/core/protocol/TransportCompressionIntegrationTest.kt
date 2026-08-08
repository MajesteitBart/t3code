package com.t3tools.t3code.compose.core.protocol

import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class TransportCompressionIntegrationTest {
  private val json = ContractJson.format

  @Test
  public fun `real OkHttp WebSocket negotiates compression and preserves a large Long response`() =
    runBlocking {
      MockWebServer().use { server ->
        server.start()
        val ticket = "synthetic-compression-ticket"
        server.enqueue(ticketResponse(ticket))
        val serverReceived = CompletableDeferred<JsonObject>()
        val serverClosing = CompletableDeferred<Unit>()
        val serverFailure = CompletableDeferred<Throwable>()
        val serverListener = object : WebSocketListener() {
          override fun onMessage(webSocket: WebSocket, text: String) {
            val request = json.parseToJsonElement(text).jsonObject
            serverReceived.complete(request)
            val requestId = requireNotNull(request["id"]).jsonPrimitive.content.toLong()
            webSocket.send(exitSuccess(requestId, LARGE_PADDING))
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            serverClosing.complete(Unit)
            webSocket.close(code, null)
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            serverFailure.complete(t)
          }
        }
        server.enqueue(
          MockResponse.Builder()
            .setHeader("Sec-WebSocket-Extensions", "permessage-deflate")
            .webSocketUpgrade(serverListener)
            .build(),
        )

        val httpOkHttp = oneAttemptClient()
        val socketOkHttp = oneAttemptClient(minimumCompressionBytes = 1_024L)
        val factory = TicketedRpcSessionFactory(
          httpClient = EnvironmentHttpClient(OneAttemptHttpClient(httpOkHttp)),
          connector = OkHttpRpcWebSocketConnector(socketOkHttp),
          timeouts = RpcSessionTimeouts(keepAlive = null),
        )

        try {
          val session = factory.start(
            httpBaseUrl = server.url("/").toString(),
            webSocketBaseUrl = server.url("/").toString().replaceFirst("http://", "ws://"),
            credential = RedactedSecret.from("synthetic-direct-access"),
          )
          val open = withTimeout(5.seconds) { session.openInfo.await() }
          val command = buildJsonObject {
            put("type", "thread.meta.update")
            put("commandId", "compression-command")
            put("threadId", "compression-thread")
            put("title", "Compressed")
            put("createdAt", "2026-08-08T00:00:00.000Z")
            put("syntheticPadding", LARGE_PADDING)
          }
          val result = withTimeout(5.seconds) { session.dispatch(command) }
          val received = withTimeout(5.seconds) { serverReceived.await() }

          assertEquals(9_007_199_254_740_991L, result.sequence)
          assertEquals(LARGE_PADDING.length, received.requiredObject("payload").requiredString("syntheticPadding").length)
          assertTrue(open.negotiatedExtensions.orEmpty().contains("permessage-deflate"))
          assertFalse(session.toString().contains(ticket))
          assertFalse(serverFailure.isCompleted)

          val ticketRequest = server.takeRequest(5, TimeUnit.SECONDS)
          val socketRequest = server.takeRequest(5, TimeUnit.SECONDS)
          assertNotNull(ticketRequest)
          assertNotNull(socketRequest)
          assertEquals("/api/auth/websocket-ticket", requireNotNull(ticketRequest).url.encodedPath)
          assertTrue(
            requireNotNull(socketRequest)
              .headers["Sec-WebSocket-Extensions"]
              .orEmpty()
              .contains("permessage-deflate"),
          )
          assertTrue(socketRequest.url.queryParameter("wsTicket")?.isNotBlank() == true)

          session.close()
          withTimeout(5.seconds) { serverClosing.await() }
          assertEquals(RpcSessionClosureReason.LocalClose, session.closure.await().reason)
        } finally {
          shutdown(httpOkHttp)
          shutdown(socketOkHttp)
        }
      }
    }

  private fun ticketResponse(ticket: String): MockResponse = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "application/json")
    .body("{\"ticket\":\"$ticket\",\"expiresAt\":\"2030-01-01T00:00:00.000Z\"}")
    .build()

  private fun exitSuccess(requestId: Long, padding: String): String = buildJsonObject {
    put("_tag", "Exit")
    put("requestId", requestId)
    put(
      "exit",
      buildJsonObject {
        put("_tag", "Success")
        put(
          "value",
          buildJsonObject {
            put("sequence", 9_007_199_254_740_991L)
            put("syntheticPadding", padding)
          },
        )
      },
    )
  }.toString()

  private fun oneAttemptClient(minimumCompressionBytes: Long? = null): OkHttpClient =
    OkHttpClient.Builder()
      .retryOnConnectionFailure(false)
      .followRedirects(false)
      .followSslRedirects(false)
      .apply {
        minimumCompressionBytes?.let(::minWebSocketMessageToCompress)
      }
      .build()

  private fun shutdown(client: OkHttpClient) {
    client.connectionPool.evictAll()
    client.dispatcher.executorService.shutdown()
    client.dispatcher.executorService.awaitTermination(5, TimeUnit.SECONDS)
  }

  private fun JsonObject.requiredObject(name: String): JsonObject =
    requireNotNull(this[name]).jsonObject

  private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]).jsonPrimitive.content

  private companion object {
    val LARGE_PADDING: String = "T3".repeat(32 * 1_024)
  }
}
