package com.t3tools.t3code.compose.core.protocol

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

public class EnvironmentHttpClientTest {
  @Test
  public fun `pair reads descriptor then sends the exact Android RFC 8693 form once`() = runBlocking {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(jsonResponse(DESCRIPTOR_JSON))
      server.enqueue(jsonResponse(ACCESS_TOKEN_JSON))
      val transport = OneAttemptHttpClient()
      val client = EnvironmentHttpClient(transport)

      val result = client.pair("${server.url("pair")}#token=pair-once", "Theo's Pixel")

      assertEquals("environment-1", result.descriptor.environmentId)
      assertEquals("access-token", result.accessCredential.reveal())
      assertFalse(result.toString().contains("access-token"))
      assertFalse(result.toString().contains("pair-once"))
      assertFalse(transport.client.retryOnConnectionFailure)
      assertFalse(transport.client.followRedirects)
      assertFalse(transport.client.followSslRedirects)

      val descriptorRequest = server.takeRequest()
      val tokenRequest = server.takeRequest()
      assertEquals("/.well-known/t3/environment", descriptorRequest.url.encodedPath)
      assertEquals("/oauth/token", tokenRequest.url.encodedPath)
      assertEquals("gzip", descriptorRequest.headers["Accept-Encoding"])
      assertEquals("gzip", tokenRequest.headers["Accept-Encoding"])
      assertNull(tokenRequest.headers["Authorization"])

      val form = parseForm(requireNotNull(tokenRequest.body).utf8())
      assertEquals(
        mapOf(
          "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
          "subject_token" to "pair-once",
          "subject_token_type" to "urn:t3:params:oauth:token-type:environment-bootstrap",
          "requested_token_type" to "urn:ietf:params:oauth:token-type:access_token",
          "client_device_type" to "mobile",
          "client_os" to "Android",
          "client_label" to "Theo's Pixel",
        ),
        form,
      )
      assertFalse(form.containsKey("scope"))
      assertEquals(2, server.requestCount)
    }
  }

  @Test
  public fun `OkHttp advertises and transparently decodes a controlled gzip response`() = runBlocking {
    MockWebServer().use { server ->
      server.start()
      val compressed = gzip(DESCRIPTOR_JSON.toByteArray(StandardCharsets.UTF_8))
      server.enqueue(
        MockResponse.Builder()
          .code(200)
          .addHeader("Content-Type", "application/json")
          .addHeader("Content-Encoding", "gzip")
          .body(Buffer().write(compressed))
          .build(),
      )

      val descriptor = EnvironmentHttpClient().descriptor(server.url("/").toString())

      assertEquals("environment-1", descriptor.environmentId)
      val request = server.takeRequest()
      assertEquals("gzip", request.headers["Accept-Encoding"])
      assertEquals(1, server.requestCount)
    }
  }

  @Test
  public fun `authorization errors preserve diagnostics while redacting the bootstrap credential`() = runBlocking {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(jsonResponse(DESCRIPTOR_JSON))
      server.enqueue(
        jsonResponse(
          """
          {
            "code":"auth_invalid",
            "reason":"invalid_credential",
            "message":"pair-once was rejected",
            "traceId":"trace-fixture"
          }
          """.trimIndent(),
          status = 401,
        ),
      )

      val error = expectFailure<AuthorizationRejectedFailure> {
        EnvironmentHttpClient().pair("${server.url("pair")}#token=pair-once")
      }

      assertEquals(401, error.status)
      assertEquals("auth_invalid", error.code)
      assertEquals("invalid_credential", error.reason)
      assertEquals("trace-fixture", error.traceId)
      assertEquals("<redacted> was rejected", error.remoteMessage)
      assertFalse(error.toString().contains("pair-once"))
    }
  }

  @Test
  public fun `strict direct token validation rejects unsupported issued and bearer types`() = runBlocking {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(jsonResponse(DESCRIPTOR_JSON))
      server.enqueue(jsonResponse(ACCESS_TOKEN_JSON.replace("\"Bearer\"", "\"DPoP\"")))

      val error = expectFailure<ProtocolViolationFailure> {
        EnvironmentHttpClient().pair("${server.url("pair")}#token=pair-once")
      }

      assertTrue(error.safeMessage.contains("unsupported direct credential"))
      assertFalse(error.toString().contains("pair-once"))
    }
  }

  @Test
  public fun `redirecting side effects are not followed or replayed`() = runBlocking {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(
        MockResponse.Builder()
          .code(307)
          .addHeader("Location", server.url("redirected"))
          .body("{}")
          .build(),
      )

      val error = expectFailure<ServerRejectedFailure> {
        EnvironmentHttpClient().dispatch(
          httpBaseUrl = server.url("/").toString(),
          credential = RedactedSecret.from("access-token"),
          command = buildJsonObject {
            put("type", "thread.archive")
            put("commandId", "command-1")
            put("threadId", "thread-1")
          },
        )
      }

      assertEquals(307, error.status)
      assertEquals(1, server.requestCount)
      assertEquals("/api/orchestration/dispatch", server.takeRequest().url.encodedPath)
    }
  }

  @Test
  public fun `coroutine cancellation cancels the one in-flight call without another attempt`() = runBlocking {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(
        MockResponse.Builder()
          .code(200)
          .headersDelay(30, TimeUnit.SECONDS)
          .body(DESCRIPTOR_JSON)
          .build(),
      )
      val deferred = async(start = CoroutineStart.UNDISPATCHED) {
        EnvironmentHttpClient().descriptor(server.url("/").toString())
      }
      assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

      deferred.cancel()
      val failure = runCatching { deferred.await() }.exceptionOrNull()

      assertTrue(failure is CancellationException)
      assertEquals(1, server.requestCount)
    }
  }

  @Test
  public fun `network failures map to layered platform-aware categories`() {
    val localDenied = ProtocolFailureMapper.fromIOException(
      "192.168.1.5",
      SocketException("EACCES: Permission denied"),
      cancelled = false,
    )
    val remoteDenied = ProtocolFailureMapper.fromIOException(
      "app.t3.codes",
      SocketException("EACCES: Permission denied"),
      cancelled = false,
    )

    assertTrue(localDenied is LocalNetworkPermissionDeniedFailure)
    assertTrue(remoteDenied is TransportFailure)
    assertTrue(
      ProtocolFailureMapper.fromIOException(
        "missing.invalid",
        UnknownHostException(),
        cancelled = false,
      ) is UnreachableFailure,
    )
    assertTrue(
      ProtocolFailureMapper.fromIOException(
        "studio.local",
        SocketTimeoutException(),
        cancelled = false,
      ) is TimeoutFailure,
    )
    assertTrue(
      ProtocolFailureMapper.fromIOException(
        "studio.local",
        IOException(),
        cancelled = true,
      ) is CancelledFailure,
    )
    assertTrue(ProtocolFailureMapper.isLocalHost("studio.local"))
    assertTrue(ProtocolFailureMapper.isLocalHost("172.20.1.2"))
    assertFalse(ProtocolFailureMapper.isLocalHost("app.t3.codes"))
  }

  private companion object {
    val DESCRIPTOR_JSON =
      """
      {
        "environmentId":"environment-1",
        "label":"Studio",
        "platform":{"os":"linux","arch":"x64"},
        "serverVersion":"1.0.0",
        "capabilities":{"repositoryIdentity":true}
      }
      """.trimIndent()

    val ACCESS_TOKEN_JSON =
      """
      {
        "access_token":"access-token",
        "issued_token_type":"urn:ietf:params:oauth:token-type:access_token",
        "token_type":"Bearer",
        "expires_in":3600,
        "scope":"orchestration:read orchestration:operate"
      }
      """.trimIndent()
  }
}

private fun jsonResponse(body: String, status: Int = 200): MockResponse = MockResponse.Builder()
  .code(status)
  .addHeader("Content-Type", "application/json")
  .body(body)
  .build()

private fun parseForm(body: String): Map<String, String> = body.split('&').associate { field ->
  val (name, value) = field.split('=', limit = 2)
  URLDecoder.decode(name, StandardCharsets.UTF_8) to
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private fun gzip(value: ByteArray): ByteArray {
  val output = ByteArrayOutputStream()
  GZIPOutputStream(output).use { it.write(value) }
  return output.toByteArray()
}

private suspend inline fun <reified T : Throwable> expectFailure(
  crossinline block: suspend () -> Unit,
): T {
  try {
    block()
  } catch (error: Throwable) {
    if (error is T) return error
    throw error
  }
  fail("Expected ${T::class.simpleName}")
  error("unreachable")
}
