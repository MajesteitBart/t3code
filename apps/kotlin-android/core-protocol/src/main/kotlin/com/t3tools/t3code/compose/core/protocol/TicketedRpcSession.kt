package com.t3tools.t3code.compose.core.protocol

import java.io.IOException
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

public data class RpcSessionTimeouts(
  public val connectionWait: Duration = 4.seconds,
  public val response: Duration = 30.seconds,
  public val keepAlive: Duration? = 5.seconds,
) {
  init {
    require(connectionWait > Duration.ZERO) { "connectionWait must be positive." }
    require(response > Duration.ZERO) { "response must be positive." }
    require(keepAlive == null || keepAlive > Duration.ZERO) { "keepAlive must be positive." }
  }
}

public enum class RpcSubscriptionMode {
  /** WS-C may recreate this intent on a separately constructed replacement session. */
  LONG_LIVED_INTENT,

  /** A disconnect is terminal and must never be recreated. */
  ONE_SHOT,
  ;

  public val supervisorMayRecreate: Boolean
    get() = this == LONG_LIVED_INTENT
}

public class RpcConnectionUnavailableFailure internal constructor(
  cause: Throwable? = null,
) : IOException("The live RPC connection was unavailable before the request was sent.", cause),
  ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class RpcDisconnectedFailure internal constructor(
  public val requestId: Long?,
  public val ambiguous: Boolean,
  cause: Throwable? = null,
) : IOException(
  if (ambiguous) {
    "The RPC request may have crossed the connection before it disconnected."
  } else {
    "The RPC session disconnected."
  },
  cause,
), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class RpcResponseTimeoutFailure internal constructor(
  public val requestId: Long,
) : IOException("The environment did not answer RPC request $requestId in time."), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class RpcSubscriptionDisconnectedFailure internal constructor(
  public val mode: RpcSubscriptionMode,
  cause: Throwable? = null,
) : IOException("The RPC subscription's one-attempt session disconnected.", cause), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public sealed interface RpcSessionClosureReason {
  public data object LocalClose : RpcSessionClosureReason

  public data class RemoteClose(
    public val code: Int,
    public val safeReason: String,
  ) : RpcSessionClosureReason

  public data class Failed(public val error: Throwable) : RpcSessionClosureReason
}

public data class RpcSessionClosure(
  public val generation: Long,
  public val opened: Boolean,
  public val reason: RpcSessionClosureReason,
)

public data class RpcSessionOpenInfo(
  public val generation: Long,
  public val negotiatedExtensions: String?,
)

public interface RpcWebSocketConnection {
  public fun send(text: String): Boolean

  public fun close(code: Int, reason: String?): Boolean

  public fun cancel()
}

public interface RpcWebSocketEvents {
  public fun onOpen(negotiatedExtensions: String?)

  public fun onText(text: String)

  public fun onBinary(bytes: ByteArray)

  public fun onClosing(code: Int, reason: String)

  public fun onClosed(code: Int, reason: String)

  public fun onFailure(error: Throwable, responseStatus: Int?)
}

public fun interface RpcWebSocketConnector {
  public fun connect(request: Request, events: RpcWebSocketEvents): RpcWebSocketConnection
}

public class OkHttpRpcWebSocketConnector(
  public val client: OkHttpClient = defaultClient(),
) : RpcWebSocketConnector {
  init {
    require(!client.retryOnConnectionFailure) {
      "The one-attempt WebSocket connector requires retryOnConnectionFailure=false."
    }
    require(!client.followRedirects && !client.followSslRedirects) {
      "The one-attempt WebSocket connector requires redirects disabled."
    }
  }

  override fun connect(request: Request, events: RpcWebSocketEvents): RpcWebSocketConnection {
    val socket = client.newWebSocket(
      request,
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          events.onOpen(response.header("Sec-WebSocket-Extensions"))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          events.onText(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
          events.onBinary(bytes.toByteArray())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
          events.onClosing(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          events.onClosed(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          events.onFailure(t, response?.code)
        }
      },
    )
    return object : RpcWebSocketConnection {
      override fun send(text: String): Boolean = socket.send(text)

      override fun close(code: Int, reason: String?): Boolean = socket.close(code, reason)

      override fun cancel() {
        socket.cancel()
      }
    }
  }

  public companion object {
    public fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
      .retryOnConnectionFailure(false)
      .followRedirects(false)
      .followSslRedirects(false)
      .build()
  }
}

public class TicketedRpcSessionFactory(
  private val httpClient: EnvironmentHttpClient = EnvironmentHttpClient(),
  private val connector: RpcWebSocketConnector = OkHttpRpcWebSocketConnector(),
  private val timeouts: RpcSessionTimeouts = RpcSessionTimeouts(),
  private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
  private val generations = AtomicLong(1L)
  private val requestIds = RpcRequestIdSequence()

  public suspend fun start(
    httpBaseUrl: String,
    webSocketBaseUrl: String,
    credential: RedactedSecret,
    ticketTimeout: Duration? = null,
  ): TicketedRpcSession {
    // The mint is deliberately inside start: every factory call is one fresh connection attempt.
    val ticket = httpClient.mintWebSocketTicket(httpBaseUrl, credential, ticketTimeout)
    val endpoint = RedactedWebSocketEndpoint.create(
      webSocketBaseUrl,
      RedactedSecret.from(ticket.ticket),
      credential,
    )
    val generation = generations.getAndIncrement()
    check(generation > 0L) { "RPC session generation space is exhausted." }
    return TicketedRpcSession(
      generation = generation,
      endpoint = endpoint,
      connector = connector,
      timeouts = timeouts,
      requestIds = requestIds,
      callbackDispatcher = callbackDispatcher,
    ).also { it.start() }
  }
}

public class TicketedRpcSession internal constructor(
  public val generation: Long,
  private val endpoint: RedactedWebSocketEndpoint,
  private val connector: RpcWebSocketConnector,
  private val timeouts: RpcSessionTimeouts,
  private val requestIds: RpcRequestIdSequence,
  callbackDispatcher: CoroutineDispatcher,
) {
  private enum class Phase {
    CONNECTING,
    OPEN,
    CLOSED,
  }

  private enum class SocketRelease {
    NONE,
    GRACEFUL,
    CANCEL,
  }

  private data class PendingUnary(
    val requestId: Long,
    val ownerGeneration: Long,
    val encodedRequest: String,
    val result: CompletableDeferred<JsonElement>,
    var sent: Boolean = false,
    var connectionWaitJob: Job? = null,
    var responseJob: Job? = null,
  )

  private data class ShellChunk(
    val requestId: Long,
    val ownerGeneration: Long,
    val values: List<ShellStreamDecodeResult>,
  )

  private data class PendingSubscription(
    val requestId: Long,
    val ownerGeneration: Long,
    val mode: RpcSubscriptionMode,
    val encodedRequest: String,
    val chunks: Channel<ShellChunk>,
    var sent: Boolean = false,
    var connectionWaitJob: Job? = null,
  )

  private data class FinalizedState(
    val connection: RpcWebSocketConnection?,
    val pendingUnary: List<PendingUnary>,
    val subscriptions: List<PendingSubscription>,
    val opened: Boolean,
  )

  private val mutex = Mutex()
  private val sessionJob = SupervisorJob()
  private val scope = CoroutineScope(callbackDispatcher + sessionJob)
  private val connectionInstalled = CompletableDeferred<Unit>()
  private val eventQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
  private val eventProcessor = scope.launch {
    connectionInstalled.await()
    for (event in eventQueue) event()
  }
  private val closureResult = CompletableDeferred<RpcSessionClosure>()
  private val openResult = CompletableDeferred<RpcSessionOpenInfo>()
  private val unary = linkedMapOf<Long, PendingUnary>()
  private val subscriptions = linkedMapOf<Long, PendingSubscription>()
  private var phase = Phase.CONNECTING
  private var connection: RpcWebSocketConnection? = null
  private var opened = false
  private var keepAliveJob: Job? = null

  public val closure: Deferred<RpcSessionClosure> = closureResult
  public val openInfo: Deferred<RpcSessionOpenInfo> = openResult
  public val redactedEndpoint: String get() = endpoint.toString()

  internal suspend fun start() {
    val events = object : RpcWebSocketEvents {
      override fun onOpen(negotiatedExtensions: String?) {
        dispatchEvent { handleOpen(negotiatedExtensions) }
      }

      override fun onText(text: String) {
        dispatchEvent { handleMessage(text) }
      }

      override fun onBinary(bytes: ByteArray) {
        dispatchEvent { handleMessage(bytes.toString(Charsets.UTF_8)) }
      }

      override fun onClosing(code: Int, reason: String) {
        dispatchEvent { handleRemoteClose(code, reason, SocketRelease.GRACEFUL) }
      }

      override fun onClosed(code: Int, reason: String) {
        dispatchEvent { handleRemoteClose(code, reason, SocketRelease.NONE) }
      }

      override fun onFailure(error: Throwable, responseStatus: Int?) {
        dispatchEvent { handleFailure(error, responseStatus) }
      }
    }

    val openedConnection = runCatching { connector.connect(endpoint.request, events) }
    val startFailure = openedConnection.exceptionOrNull()
    val shouldCancelOpenedConnection = mutex.withLock {
      if (phase == Phase.CONNECTING && openedConnection.isSuccess) {
        connection = openedConnection.getOrThrow()
        false
      } else {
        openedConnection.isSuccess
      }
    }
    connectionInstalled.complete(Unit)

    if (startFailure != null) {
      finishSession(
        reason = RpcSessionClosureReason.Failed(safeTransportFailure(startFailure, opened = false)),
        release = SocketRelease.NONE,
      )
    } else if (shouldCancelOpenedConnection) {
      openedConnection.getOrNull()?.cancel()
    }
  }

  public suspend fun request(
    method: FoundationRpcMethod,
    payload: JsonObject,
  ): JsonElement {
    currentCoroutineContext().ensureActive()
    val pending = registerUnary(method, payload)
    return try {
      pending.result.await()
    } catch (error: CancellationException) {
      withContext(NonCancellable) { cancelUnary(pending.requestId) }
      throw error
    }
  }

  public suspend fun dispatch(command: JsonObject): DispatchResult {
    val value = request(FoundationRpcMethod.DISPATCH_COMMAND, command)
    return try {
      ContractJson.format.decodeFromJsonElement(value)
    } catch (error: SerializationException) {
      throw ProtocolViolationFailure("The RPC dispatch result is incompatible.", error)
    } catch (error: IllegalArgumentException) {
      throw ProtocolViolationFailure("The RPC dispatch result is incompatible.", error)
    }
  }

  public fun subscribeShell(
    input: OrchestrationSubscribeShellInput,
    mode: RpcSubscriptionMode = RpcSubscriptionMode.LONG_LIVED_INTENT,
  ): Flow<ShellStreamDecodeResult> = flow {
    currentCoroutineContext().ensureActive()
    val pending = registerSubscription(input, mode)
    try {
      for (chunk in pending.chunks) {
        for (value in chunk.values) emit(value)
        acknowledgeChunk(chunk)
      }
    } finally {
      withContext(NonCancellable) { cancelSubscription(pending.requestId) }
    }
  }

  public suspend fun close() {
    finishSession(RpcSessionClosureReason.LocalClose, SocketRelease.GRACEFUL)
  }

  override fun toString(): String =
    "TicketedRpcSession(generation=$generation, endpoint=$redactedEndpoint)"

  private fun dispatchEvent(block: suspend () -> Unit) {
    eventQueue.trySend(block)
  }

  private suspend fun registerUnary(
    method: FoundationRpcMethod,
    payload: JsonObject,
  ): PendingUnary {
    val requestId = requestIds.next()
    val pending = PendingUnary(
      requestId = requestId,
      ownerGeneration = generation,
      encodedRequest = EffectRpcCodec.encodeRequest(requestId, method, payload),
      result = CompletableDeferred(),
    )
    val sendNow = mutex.withLock {
      if (phase == Phase.CLOSED) throw RpcDisconnectedFailure(null, ambiguous = false)
      unary[requestId] = pending
      pending.connectionWaitJob = scope.launch {
        delay(timeouts.connectionWait)
        expireUnsentUnary(requestId)
      }
      phase == Phase.OPEN
    }
    if (sendNow) sendUnary(requestId)
    return pending
  }

  private suspend fun registerSubscription(
    input: OrchestrationSubscribeShellInput,
    mode: RpcSubscriptionMode,
  ): PendingSubscription {
    val requestId = requestIds.next()
    val pending = PendingSubscription(
      requestId = requestId,
      ownerGeneration = generation,
      mode = mode,
      encodedRequest = EffectRpcCodec.encodeSubscribeShellRequest(requestId, input),
      chunks = Channel(Channel.UNLIMITED),
    )
    val sendNow = mutex.withLock {
      if (phase == Phase.CLOSED) throw RpcDisconnectedFailure(null, ambiguous = false)
      subscriptions[requestId] = pending
      pending.connectionWaitJob = scope.launch {
        delay(timeouts.connectionWait)
        expireUnsentSubscription(requestId)
      }
      phase == Phase.OPEN
    }
    if (sendNow) sendSubscription(requestId)
    return pending
  }

  private suspend fun handleOpen(negotiatedExtensions: String?) {
    var pendingIds = emptyList<Long>() to emptyList<Long>()
    val shouldClose = mutex.withLock {
      if (phase != Phase.CONNECTING || connection == null) return@withLock true
      phase = Phase.OPEN
      opened = true
      openResult.complete(RpcSessionOpenInfo(generation, negotiatedExtensions))
      keepAliveJob = timeouts.keepAlive?.let {
        scope.launch {
          while (isActive) {
            delay(it)
            if (!sendControl(RpcControlFrame.Ping)) return@launch
          }
        }
      }
      pendingIds = unary.keys.toList() to subscriptions.keys.toList()
      false
    }
    if (shouldClose) {
      connection?.close(NORMAL_CLOSE_CODE, "stale session")
      return
    }
    for (requestId in pendingIds.first) sendUnary(requestId)
    for (requestId in pendingIds.second) sendSubscription(requestId)
  }

  private suspend fun sendUnary(requestId: Long) {
    var sendFailure: Throwable? = null
    mutex.withLock {
      val pending = unary[requestId] ?: return
      val activeConnection = connection
      if (
        phase != Phase.OPEN ||
        pending.sent ||
        pending.ownerGeneration != generation ||
        activeConnection == null
      ) {
        return
      }
      // Ownership becomes ambiguous before the frame crosses the socket API.
      pending.sent = true
      pending.connectionWaitJob?.cancel()
      pending.connectionWaitJob = null
      val accepted = runCatching { activeConnection.send(pending.encodedRequest) }
        .onFailure { sendFailure = it }
        .getOrDefault(false)
      if (accepted) {
        pending.responseJob = scope.launch {
          delay(timeouts.response)
          expireSentUnary(requestId)
        }
      } else if (sendFailure == null) {
        sendFailure = IOException("The WebSocket rejected an outbound RPC frame.")
      }
    }
    sendFailure?.let {
      finishSession(
        RpcSessionClosureReason.Failed(safeTransportFailure(it, opened = true)),
        SocketRelease.CANCEL,
      )
    }
  }

  private suspend fun sendSubscription(requestId: Long) {
    var sendFailure: Throwable? = null
    mutex.withLock {
      val pending = subscriptions[requestId] ?: return
      val activeConnection = connection
      if (
        phase != Phase.OPEN ||
        pending.sent ||
        pending.ownerGeneration != generation ||
        activeConnection == null
      ) {
        return
      }
      pending.sent = true
      pending.connectionWaitJob?.cancel()
      pending.connectionWaitJob = null
      val accepted = runCatching { activeConnection.send(pending.encodedRequest) }
        .onFailure { sendFailure = it }
        .getOrDefault(false)
      if (!accepted && sendFailure == null) {
        sendFailure = IOException("The WebSocket rejected an outbound RPC frame.")
      }
    }
    sendFailure?.let {
      finishSession(
        RpcSessionClosureReason.Failed(safeTransportFailure(it, opened = true)),
        SocketRelease.CANCEL,
      )
    }
  }

  private suspend fun handleMessage(encoded: String) {
    if (!isCurrentOpenGeneration()) return
    val frame = try {
      EffectRpcCodec.decodeFrame(encoded)
    } catch (error: ProtocolViolationFailure) {
      finishSession(RpcSessionClosureReason.Failed(safeProtocolViolation(error)), SocketRelease.CANCEL)
      return
    }

    when (frame) {
      RpcInboundFrame.Ping -> sendControl(RpcControlFrame.Pong)
      RpcInboundFrame.Pong,
      is RpcInboundFrame.Ack,
      is RpcInboundFrame.Interrupt,
      -> Unit
      is RpcInboundFrame.Chunk -> handleChunk(frame)
      is RpcInboundFrame.Exit -> handleExit(frame)
      is RpcInboundFrame.Defect ->
        finishSession(RpcSessionClosureReason.Failed(safeFatal(frame.error)), SocketRelease.CANCEL)
      is RpcInboundFrame.ClientProtocolError ->
        finishSession(RpcSessionClosureReason.Failed(safeFatal(frame.error)), SocketRelease.CANCEL)
    }
  }

  private suspend fun handleChunk(frame: RpcInboundFrame.Chunk) {
    val values = try {
      EffectRpcCodec.decodeShellChunk(frame)
    } catch (error: ProtocolViolationFailure) {
      finishSession(RpcSessionClosureReason.Failed(safeProtocolViolation(error)), SocketRelease.CANCEL)
      return
    }
    val target = mutex.withLock {
      subscriptions[frame.requestId]
        ?.takeIf { phase == Phase.OPEN && it.ownerGeneration == generation }
    } ?: return
    val offered = target.chunks.trySend(ShellChunk(frame.requestId, generation, values))
    if (offered.isFailure) cancelSubscription(frame.requestId)
  }

  private suspend fun handleExit(frame: RpcInboundFrame.Exit) {
    val completedUnary: PendingUnary?
    val completedSubscription: PendingSubscription?
    mutex.withLock {
      completedUnary = unary.remove(frame.requestId)
      completedUnary?.connectionWaitJob?.cancel()
      completedUnary?.responseJob?.cancel()
      completedSubscription = subscriptions.remove(frame.requestId)
      completedSubscription?.connectionWaitJob?.cancel()
    }
    when (val outcome = frame.outcome) {
      is RpcExitOutcome.Success -> {
        completedUnary?.result?.complete(outcome.value)
        completedSubscription?.chunks?.close()
      }
      is RpcExitOutcome.Failure -> {
        val safeError = RemoteRpcFailure(
          requestId = outcome.error.requestId,
          remoteTag = outcome.error.remoteTag,
          safeMessage = endpoint.redact(outcome.error.safeMessage).orEmpty(),
        )
        completedUnary?.result?.completeExceptionally(safeError)
        completedSubscription?.chunks?.close(safeError)
      }
    }
  }

  private suspend fun acknowledgeChunk(chunk: ShellChunk) {
    if (chunk.ownerGeneration != generation) return
    val owned = mutex.withLock {
      phase == Phase.OPEN && subscriptions[chunk.requestId]?.ownerGeneration == generation
    }
    if (owned) sendControl(RpcControlFrame.Ack(chunk.requestId))
  }

  private suspend fun sendControl(frame: RpcControlFrame): Boolean {
    var failure: Throwable? = null
    var attempted = false
    val sent = mutex.withLock {
      val activeConnection = connection
      if (phase != Phase.OPEN || activeConnection == null) return@withLock false
      attempted = true
      runCatching { activeConnection.send(EffectRpcCodec.encodeControl(frame)) }
        .onFailure { failure = it }
        .getOrDefault(false)
    }
    if (attempted && !sent) {
      val sendFailure = failure ?: IOException("The WebSocket rejected an outbound RPC control frame.")
      finishSession(
        RpcSessionClosureReason.Failed(safeTransportFailure(sendFailure, opened = true)),
        SocketRelease.CANCEL,
      )
    }
    return sent
  }

  private suspend fun cancelUnary(requestId: Long) {
    var sendFailed = false
    val pending = mutex.withLock {
      val owned = unary.remove(requestId) ?: return
      owned.connectionWaitJob?.cancel()
      owned.responseJob?.cancel()
      if (
        owned.sent &&
        owned.ownerGeneration == generation &&
        phase == Phase.OPEN
      ) {
        sendFailed = connection?.send(
          EffectRpcCodec.encodeControl(RpcControlFrame.Interrupt(requestId)),
        ) == false
      }
      owned
    }
    pending.result.completeExceptionally(CancelledFailure())
    if (sendFailed) {
      finishSession(
        RpcSessionClosureReason.Failed(TransportFailure(endpoint.host)),
        SocketRelease.CANCEL,
      )
    }
  }

  private suspend fun cancelSubscription(requestId: Long) {
    var sendFailed = false
    val pending = mutex.withLock {
      val owned = subscriptions.remove(requestId) ?: return
      owned.connectionWaitJob?.cancel()
      if (
        owned.sent &&
        owned.ownerGeneration == generation &&
        phase == Phase.OPEN
      ) {
        sendFailed = connection?.send(
          EffectRpcCodec.encodeControl(RpcControlFrame.Interrupt(requestId)),
        ) == false
      }
      owned
    }
    pending.chunks.close()
    if (sendFailed) {
      finishSession(
        RpcSessionClosureReason.Failed(TransportFailure(endpoint.host)),
        SocketRelease.CANCEL,
      )
    }
  }

  private suspend fun expireUnsentUnary(requestId: Long) {
    val expired = mutex.withLock {
      val pending = unary[requestId]
      if (pending == null || pending.sent) return
      unary.remove(requestId)
    }
    expired?.result?.completeExceptionally(RpcConnectionUnavailableFailure())
  }

  private suspend fun expireSentUnary(requestId: Long) {
    var sendFailed = false
    val expired = mutex.withLock {
      val pending = unary.remove(requestId) ?: return
      pending.connectionWaitJob?.cancel()
      pending.responseJob?.cancel()
      if (
        pending.sent &&
        pending.ownerGeneration == generation &&
        phase == Phase.OPEN
      ) {
        sendFailed = connection?.send(
          EffectRpcCodec.encodeControl(RpcControlFrame.Interrupt(requestId)),
        ) == false
      }
      pending
    }
    expired.result.completeExceptionally(RpcResponseTimeoutFailure(requestId))
    if (sendFailed) {
      finishSession(
        RpcSessionClosureReason.Failed(TransportFailure(endpoint.host)),
        SocketRelease.CANCEL,
      )
    }
  }

  private suspend fun expireUnsentSubscription(requestId: Long) {
    val expired = mutex.withLock {
      val pending = subscriptions[requestId]
      if (pending == null || pending.sent) return
      subscriptions.remove(requestId)
    }
    expired?.chunks?.close(RpcConnectionUnavailableFailure())
  }

  private suspend fun handleRemoteClose(
    code: Int,
    reason: String,
    release: SocketRelease,
  ) {
    val safeReason = endpoint.redact(reason).orEmpty().take(MAX_CLOSE_REASON_LENGTH)
    finishSession(
      RpcSessionClosureReason.RemoteClose(code, safeReason),
      release,
      remoteCloseCode = code,
    )
  }

  private suspend fun handleFailure(error: Throwable, responseStatus: Int?) {
    val wasOpen = mutex.withLock { opened }
    val failure = when (responseStatus) {
      401 -> AuthorizationRejectedFailure(401, null, "websocket_ticket_rejected", null, null)
      null -> safeTransportFailure(error, wasOpen)
      else -> ServerRejectedFailure(responseStatus, null, "websocket_handshake_rejected", null, null)
    }
    finishSession(RpcSessionClosureReason.Failed(failure), SocketRelease.CANCEL)
  }

  private suspend fun finishSession(
    reason: RpcSessionClosureReason,
    release: SocketRelease,
    remoteCloseCode: Int = NORMAL_CLOSE_CODE,
  ) {
    val finalized = mutex.withLock {
      if (phase == Phase.CLOSED) return
      phase = Phase.CLOSED
      eventQueue.close()
      eventProcessor.cancel()
      keepAliveJob?.cancel()
      keepAliveJob = null
      val snapshot = FinalizedState(
        connection = connection,
        pendingUnary = unary.values.toList(),
        subscriptions = subscriptions.values.toList(),
        opened = opened,
      )
      connection = null
      unary.clear()
      subscriptions.clear()
      snapshot.pendingUnary.forEach {
        it.connectionWaitJob?.cancel()
        it.responseJob?.cancel()
      }
      snapshot.subscriptions.forEach { it.connectionWaitJob?.cancel() }
      if (!openResult.isCompleted) {
        openResult.completeExceptionally(closureError(reason))
      }
      closureResult.complete(RpcSessionClosure(generation, snapshot.opened, reason))
      snapshot
    }

    val terminalCause = (reason as? RpcSessionClosureReason.Failed)?.error
    for (pending in finalized.pendingUnary) {
      val failure = when {
        terminalCause is FatalRpcProtocolFailure || terminalCause is ProtocolViolationFailure ->
          requireNotNull(terminalCause)
        pending.sent -> RpcDisconnectedFailure(pending.requestId, ambiguous = true, terminalCause)
        else -> RpcDisconnectedFailure(pending.requestId, ambiguous = false, terminalCause)
      }
      pending.result.completeExceptionally(failure)
    }
    for (subscription in finalized.subscriptions) {
      val failure = when {
        terminalCause is FatalRpcProtocolFailure || terminalCause is ProtocolViolationFailure ->
          requireNotNull(terminalCause)
        else -> RpcSubscriptionDisconnectedFailure(subscription.mode, terminalCause)
      }
      subscription.chunks.close(failure)
    }

    when (release) {
      SocketRelease.NONE -> Unit
      SocketRelease.GRACEFUL -> {
        val activeConnection = finalized.connection
        if (finalized.opened) {
          if (activeConnection?.close(remoteCloseCode, "session complete") == false) {
            activeConnection.cancel()
          }
        } else {
          activeConnection?.cancel()
        }
      }
      SocketRelease.CANCEL -> finalized.connection?.cancel()
    }
    sessionJob.cancel()
  }

  private suspend fun isCurrentOpenGeneration(): Boolean = mutex.withLock {
    phase == Phase.OPEN && opened
  }

  private fun safeTransportFailure(error: Throwable, opened: Boolean): Throwable {
    if (opened) return RpcDisconnectedFailure(null, ambiguous = false)
    val mapped = if (error is IOException) {
      ProtocolFailureMapper.fromIOException(endpoint.host, error, cancelled = false)
    } else {
      TransportFailure(endpoint.host)
    }
    // Preserve the category while dropping a third-party cause that could contain the ticket URL.
    return when (mapped) {
      is LocalNetworkPermissionDeniedFailure -> LocalNetworkPermissionDeniedFailure(endpoint.host)
      is TimeoutFailure -> TimeoutFailure(endpoint.host)
      is UnreachableFailure -> UnreachableFailure(endpoint.host)
      else -> TransportFailure(endpoint.host)
    }
  }

  private fun closureError(reason: RpcSessionClosureReason): Throwable = when (reason) {
    RpcSessionClosureReason.LocalClose -> RpcDisconnectedFailure(null, ambiguous = false)
    is RpcSessionClosureReason.RemoteClose -> RpcDisconnectedFailure(null, ambiguous = false)
    is RpcSessionClosureReason.Failed -> reason.error
  }

  private fun safeFatal(error: FatalRpcProtocolFailure): FatalRpcProtocolFailure =
    FatalRpcProtocolFailure(
      frameTag = error.frameTag,
      safeMessage = endpoint.redact(error.safeMessage).orEmpty(),
    )

  private fun safeProtocolViolation(error: ProtocolViolationFailure): ProtocolViolationFailure =
    ProtocolViolationFailure(endpoint.redact(error.safeMessage).orEmpty())

  private companion object {
    const val NORMAL_CLOSE_CODE: Int = 1000
    const val MAX_CLOSE_REASON_LENGTH: Int = 123
  }
}

internal class RedactedWebSocketEndpoint private constructor(
  internal val request: Request,
  internal val host: String,
  private val secrets: List<RedactedSecret>,
  private val safeValue: String,
) {
  internal fun redact(value: String?): String? =
    redactProtocolDetail(value, secrets.map(RedactedSecret::reveal))

  override fun toString(): String = safeValue

  companion object {
    fun create(
      webSocketBaseUrl: String,
      ticket: RedactedSecret,
      credential: RedactedSecret,
    ): RedactedWebSocketEndpoint {
      val uri = runCatching { URI(webSocketBaseUrl) }
        .getOrElse { throw MalformedInputFailure("WebSocket base URL is invalid.") }
      val scheme = uri.scheme?.lowercase()
      val httpScheme = when (scheme) {
        "ws" -> "http"
        "wss" -> "https"
        else -> throw MalformedInputFailure("WebSocket base URL must use ws or wss.")
      }
      val host = uri.host ?: throw MalformedInputFailure("WebSocket base URL is missing its host.")
      if (uri.userInfo != null) {
        throw MalformedInputFailure("WebSocket base URL must not include user information.")
      }
      val url = HttpUrl.Builder()
        .scheme(httpScheme)
        .host(host)
        .apply { if (uri.port >= 0) port(uri.port) }
        .encodedPath("/ws")
        .addQueryParameter("wsTicket", ticket.reveal())
        .build()
      val authorityHost = if (host.contains(':')) "[$host]" else host
      val authority = if (uri.port >= 0) "$authorityHost:${uri.port}" else authorityHost
      val safeValue = "$scheme://$authority/ws?wsTicket=<redacted>"
      return RedactedWebSocketEndpoint(
        request = Request.Builder().url(url).build(),
        host = host,
        secrets = listOf(ticket, credential),
        safeValue = safeValue,
      )
    }
  }
}
