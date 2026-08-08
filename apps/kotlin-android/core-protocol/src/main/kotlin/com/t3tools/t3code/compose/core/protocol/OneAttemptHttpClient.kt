package com.t3tools.t3code.compose.core.protocol

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

public data class RawHttpResponse(
  public val status: Int,
  public val headers: Headers,
  public val body: String,
)

public class OneAttemptHttpClient(
  public val client: OkHttpClient = defaultClient(),
) {
  init {
    require(!client.retryOnConnectionFailure) {
      "OneAttemptHttpClient requires retryOnConnectionFailure=false; supervisors own retry."
    }
    require(!client.followRedirects && !client.followSslRedirects) {
      "OneAttemptHttpClient requires redirects disabled so side effects are never replayed."
    }
  }

  public suspend fun execute(request: Request, timeout: Duration? = null): RawHttpResponse {
    val call = client.newCall(request)
    if (timeout != null && timeout.isFinite()) {
      call.timeout().timeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }
    val response = await(call, request.url.host)
    return response.use {
      RawHttpResponse(
        status = it.code,
        headers = it.headers,
        body = it.body.string(),
      )
    }
  }

  private suspend fun await(call: Call, host: String): Response =
    suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            val failure = ProtocolFailureMapper.fromIOException(host, e, call.isCanceled())
            continuation.resumeWith(Result.failure(failure))
          }

          override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
          }
        },
      )
    }

  public companion object {
    public fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
      .retryOnConnectionFailure(false)
      .followRedirects(false)
      .followSslRedirects(false)
      .build()
  }
}
