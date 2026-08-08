package com.t3tools.t3code.compose.core.protocol

import kotlin.time.Duration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

public data class DirectPairingResult(
  public val descriptor: ExecutionEnvironmentDescriptor,
  public val httpBaseUrl: String,
  public val webSocketBaseUrl: String,
  public val accessCredential: RedactedSecret,
  public val expiresInSeconds: Double,
  public val scopes: List<String>,
)

public class EnvironmentHttpClient(
  private val transport: OneAttemptHttpClient = OneAttemptHttpClient(),
) {
  private val json = ContractJson.format

  public suspend fun pair(
    rawValue: String,
    clientLabel: String? = null,
    timeout: Duration? = null,
  ): DirectPairingResult = pair(PairingUrl.resolve(rawValue), clientLabel, timeout)

  public suspend fun pair(
    target: PairingTarget,
    clientLabel: String? = null,
    timeout: Duration? = null,
  ): DirectPairingResult {
    val descriptor = descriptor(target.httpBaseUrl, timeout)
    val token = exchange(target, clientLabel, timeout)
    if (token.issuedTokenType != ACCESS_TOKEN_TYPE || token.tokenType != "Bearer") {
      throw ProtocolViolationFailure("The environment issued an unsupported direct credential.")
    }
    return DirectPairingResult(
      descriptor = descriptor,
      httpBaseUrl = target.httpBaseUrl,
      webSocketBaseUrl = target.webSocketBaseUrl,
      accessCredential = RedactedSecret.from(token.accessToken),
      expiresInSeconds = token.expiresIn,
      scopes = token.scope.split(' ').filter(String::isNotBlank),
    )
  }

  public suspend fun descriptor(httpBaseUrl: String, timeout: Duration? = null): ExecutionEnvironmentDescriptor {
    val request = Request.Builder()
      .url(endpoint(httpBaseUrl, "/.well-known/t3/environment"))
      .get()
      .build()
    return decodeSuccess(transport.execute(request, timeout), emptyList())
  }

  public suspend fun shellSnapshot(
    httpBaseUrl: String,
    credential: RedactedSecret,
    timeout: Duration? = null,
  ): OrchestrationShellSnapshot {
    val request = authorizedRequest(httpBaseUrl, "/api/orchestration/shell", credential).get().build()
    return decodeSuccess(transport.execute(request, timeout), listOf(credential.reveal()))
  }

  public suspend fun threadSnapshot(
    httpBaseUrl: String,
    credential: RedactedSecret,
    threadId: String,
    turnLimit: Int? = null,
    beforeCursor: String? = null,
    timeout: Duration? = null,
  ): OrchestrationThreadDetailSnapshot {
    val base = httpBaseUrl.toHttpUrl().newBuilder()
      .addPathSegments("api/orchestration/threads")
      .addPathSegment(threadId)
    turnLimit?.let { base.addQueryParameter("turnLimit", it.toString()) }
    beforeCursor?.let { base.addQueryParameter("beforeCursor", it) }
    val request = Request.Builder()
      .url(base.build())
      .header("Authorization", "Bearer ${credential.reveal()}")
      .get()
      .build()
    return decodeSuccess(transport.execute(request, timeout), listOf(credential.reveal()))
  }

  public suspend fun dispatch(
    httpBaseUrl: String,
    credential: RedactedSecret,
    command: JsonObject,
    timeout: Duration? = null,
  ): DispatchResult {
    val body = json.encodeToString(command).toRequestBody(JSON_MEDIA_TYPE)
    val request = authorizedRequest(
      httpBaseUrl,
      "/api/orchestration/dispatch",
      credential,
    ).post(body).build()
    return decodeSuccess(transport.execute(request, timeout), listOf(credential.reveal()))
  }

  public suspend fun mintWebSocketTicket(
    httpBaseUrl: String,
    credential: RedactedSecret,
    timeout: Duration? = null,
  ): AuthWebSocketTicketResult {
    val request = authorizedRequest(
      httpBaseUrl,
      "/api/auth/websocket-ticket",
      credential,
    ).post(EMPTY_BODY).build()
    return decodeSuccess(transport.execute(request, timeout), listOf(credential.reveal()))
  }

  private suspend fun exchange(
    target: PairingTarget,
    clientLabel: String?,
    timeout: Duration?,
  ): AuthAccessTokenResult {
    val form = FormBody.Builder()
      .add("grant_type", TOKEN_EXCHANGE_GRANT)
      .add("subject_token", target.bootstrapCredential.reveal())
      .add("subject_token_type", ENVIRONMENT_BOOTSTRAP_TOKEN_TYPE)
      .add("requested_token_type", ACCESS_TOKEN_TYPE)
      .add("client_device_type", "mobile")
      .add("client_os", "Android")
      .apply {
        clientLabel?.trim()?.takeIf(String::isNotEmpty)?.let { add("client_label", it) }
      }
      .build()
    val request = Request.Builder()
      .url(endpoint(target.httpBaseUrl, "/oauth/token"))
      .post(form)
      .build()
    return decodeSuccess(
      transport.execute(request, timeout),
      listOf(target.bootstrapCredential.reveal()),
    )
  }

  private inline fun <reified T> decodeSuccess(
    response: RawHttpResponse,
    secrets: List<String>,
  ): T {
    if (response.status !in 200..299) throw remoteFailure(response, secrets)
    return try {
      json.decodeFromString(response.body)
    } catch (error: SerializationException) {
      throw ProtocolViolationFailure("The environment returned an incompatible response.", error)
    } catch (error: IllegalArgumentException) {
      throw ProtocolViolationFailure("The environment returned an incompatible response.", error)
    }
  }

  private fun remoteFailure(response: RawHttpResponse, secrets: List<String>): Throwable {
    val problem = runCatching {
      val value = json.parseToJsonElement(response.body).jsonObject
      RemoteProblem(
        code = value["code"]?.jsonPrimitive?.contentOrNull,
        reason = value["reason"]?.jsonPrimitive?.contentOrNull,
        message = value["message"]?.jsonPrimitive?.contentOrNull,
        traceId = value["traceId"]?.jsonPrimitive?.contentOrNull,
      )
    }.getOrNull()
    val code = redactProtocolDetail(problem?.code, secrets)
    val reason = redactProtocolDetail(problem?.reason, secrets)
    val message = redactProtocolDetail(problem?.message, secrets)
    val traceId = redactProtocolDetail(problem?.traceId, secrets)
    return if (response.status == 401) {
      AuthorizationRejectedFailure(response.status, code, reason, message, traceId)
    } else {
      ServerRejectedFailure(response.status, code, reason, message, traceId)
    }
  }

  private fun authorizedRequest(
    httpBaseUrl: String,
    path: String,
    credential: RedactedSecret,
  ): Request.Builder = Request.Builder()
    .url(endpoint(httpBaseUrl, path))
    .header("Authorization", "Bearer ${credential.reveal()}")

  private fun endpoint(httpBaseUrl: String, path: String): HttpUrl =
    httpBaseUrl.toHttpUrl().newBuilder().encodedPath(path).build()

  private data class RemoteProblem(
    val code: String?,
    val reason: String?,
    val message: String?,
    val traceId: String?,
  )

  private companion object {
    const val TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
    const val ENVIRONMENT_BOOTSTRAP_TOKEN_TYPE =
      "urn:t3:params:oauth:token-type:environment-bootstrap"
    const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    val EMPTY_BODY = ByteArray(0).toRequestBody()
  }
}
