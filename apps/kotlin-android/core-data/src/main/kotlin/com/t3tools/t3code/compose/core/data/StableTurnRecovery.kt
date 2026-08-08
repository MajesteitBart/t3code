package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.ContractJson
import com.t3tools.t3code.compose.core.protocol.DispatchResult
import com.t3tools.t3code.compose.core.protocol.AuthorizationRejectedFailure
import com.t3tools.t3code.compose.core.protocol.MalformedInputFailure
import com.t3tools.t3code.compose.core.protocol.OrchestrationThreadDetailSnapshot
import com.t3tools.t3code.compose.core.protocol.ProtocolFailure
import com.t3tools.t3code.compose.core.protocol.ProviderInteractionMode
import com.t3tools.t3code.compose.core.protocol.ServerRejectedFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public data class StableTurnIdentity(
  public val commandId: String,
  public val messageId: String,
  public val createdAt: String,
) {
  init {
    require(commandId.isNotBlank()) { "Stable turn command ID cannot be blank." }
    require(messageId.isNotBlank()) { "Stable turn message ID cannot be blank." }
    require(createdAt.isNotBlank()) { "Stable turn creation time cannot be blank." }
  }
}

/** An immutable logical turn whose identity and encoded wire payload survive ambiguous attempts. */
public class PreparedStableTurn private constructor(
  public val threadId: String,
  public val identity: StableTurnIdentity,
  public val wirePayload: JsonObject,
  public val canonicalWirePayload: String,
) {
  public companion object {
    public fun create(
      threadId: String,
      identity: StableTurnIdentity,
      text: String,
      attachments: List<JsonElement> = emptyList(),
      runtimeMode: String = "approval-required",
      interactionMode: ProviderInteractionMode = ProviderInteractionMode.DEFAULT,
    ): PreparedStableTurn {
      require(threadId.isNotBlank()) { "Stable turn thread ID cannot be blank." }
      require(runtimeMode.isNotBlank()) { "Stable turn runtime mode cannot be blank." }
      val payload = buildJsonObject {
        put("type", "thread.turn.start")
        put("commandId", identity.commandId)
        put("threadId", threadId)
        put(
          "message",
          buildJsonObject {
            put("messageId", identity.messageId)
            put("role", "user")
            put("text", text)
            put("attachments", JsonArray(attachments.toList()))
          },
        )
        put("runtimeMode", runtimeMode)
        put(
          "interactionMode",
          when (interactionMode) {
            ProviderInteractionMode.DEFAULT -> "default"
            ProviderInteractionMode.PLAN -> "plan"
          },
        )
        put("createdAt", identity.createdAt)
      }
      return PreparedStableTurn(
        threadId = threadId,
        identity = identity,
        wirePayload = payload,
        canonicalWirePayload = ContractJson.format.encodeToString(payload),
      )
    }
  }
}

public fun interface StableTurnOneAttemptTransport {
  public suspend fun dispatch(command: JsonObject): DispatchResult
}

/** Marks an adapter-observed response loss after a one-attempt dispatch may have reached T3. */
public class StableTurnResponseAmbiguousFailure(
  cause: Throwable? = null,
) : IOException("The turn response was lost after dispatch.", cause)

public enum class StableTurnCommitVerification {
  COMMITTED,
  NOT_COMMITTED,
  UNAVAILABLE,
}

public fun interface StableTurnCommitVerifier {
  public suspend fun verify(threadId: String, messageId: String): StableTurnCommitVerification
}

/** Loads a new thread snapshot for every verification and never caches a prior answer. */
public class FreshThreadSnapshotCommitVerifier(
  private val loadSnapshot: suspend (threadId: String) -> OrchestrationThreadDetailSnapshot,
) : StableTurnCommitVerifier {
  override suspend fun verify(
    threadId: String,
    messageId: String,
  ): StableTurnCommitVerification {
    val snapshot = loadSnapshot(threadId)
    check(snapshot.thread.id == threadId) { "Fresh thread snapshot identity did not match the request." }
    return if (snapshot.thread.messages.any { it.id == messageId }) {
      StableTurnCommitVerification.COMMITTED
    } else {
      StableTurnCommitVerification.NOT_COMMITTED
    }
  }
}

public enum class StableTurnAmbiguity {
  COMMIT_NOT_VISIBLE,
  VERIFICATION_UNAVAILABLE,
}

public sealed interface StableTurnOutcome {
  public val turn: PreparedStableTurn

  public data class Accepted(
    override val turn: PreparedStableTurn,
    public val sequence: Long,
  ) : StableTurnOutcome

  public data class ConfirmedCommitted(
    override val turn: PreparedStableTurn,
  ) : StableTurnOutcome

  public data class Ambiguous(
    override val turn: PreparedStableTurn,
    public val reason: StableTurnAmbiguity,
  ) : StableTurnOutcome
}

/**
 * Owns ambiguity recovery above a policy-free one-attempt transport. It never loops or mutates a
 * prepared command; explicit retry verifies a fresh snapshot before reusing the same payload.
 */
public class StableTurnRecovery(
  private val transport: StableTurnOneAttemptTransport,
  private val verifier: StableTurnCommitVerifier,
) {
  public suspend fun send(turn: PreparedStableTurn): StableTurnOutcome = dispatchOnce(turn)

  public suspend fun retry(ambiguous: StableTurnOutcome.Ambiguous): StableTurnOutcome =
    when (verifySafely(ambiguous.turn)) {
      StableTurnCommitVerification.COMMITTED ->
        StableTurnOutcome.ConfirmedCommitted(ambiguous.turn)
      StableTurnCommitVerification.UNAVAILABLE ->
        ambiguous.copy(reason = StableTurnAmbiguity.VERIFICATION_UNAVAILABLE)
      StableTurnCommitVerification.NOT_COMMITTED -> dispatchOnce(ambiguous.turn)
    }

  private suspend fun dispatchOnce(turn: PreparedStableTurn): StableTurnOutcome = try {
    val result = transport.dispatch(turn.wirePayload)
    StableTurnOutcome.Accepted(turn, result.sequence)
  } catch (failure: Throwable) {
    rethrowIfCoroutineCancelled(failure)
    if (!failure.isAmbiguousDispatchFailure()) throw failure
    resolveAmbiguousDispatch(turn)
  }

  private suspend fun resolveAmbiguousDispatch(turn: PreparedStableTurn): StableTurnOutcome =
    when (verifySafely(turn)) {
      StableTurnCommitVerification.COMMITTED -> StableTurnOutcome.ConfirmedCommitted(turn)
      StableTurnCommitVerification.NOT_COMMITTED -> StableTurnOutcome.Ambiguous(
        turn,
        StableTurnAmbiguity.COMMIT_NOT_VISIBLE,
      )
      StableTurnCommitVerification.UNAVAILABLE -> StableTurnOutcome.Ambiguous(
        turn,
        StableTurnAmbiguity.VERIFICATION_UNAVAILABLE,
      )
    }

  private suspend fun verifySafely(turn: PreparedStableTurn): StableTurnCommitVerification = try {
    verifier.verify(turn.threadId, turn.identity.messageId)
  } catch (failure: Throwable) {
    rethrowIfCoroutineCancelled(failure)
    StableTurnCommitVerification.UNAVAILABLE
  }

  private suspend fun rethrowIfCoroutineCancelled(failure: Throwable) {
    if (failure !is CancellationException) return
    currentCoroutineContext().ensureActive()
    if (failure !is ProtocolFailure) throw failure
  }

  private fun Throwable.isAmbiguousDispatchFailure(): Boolean = when (this) {
    is AuthorizationRejectedFailure,
    is MalformedInputFailure,
    is ServerRejectedFailure,
    -> false
    is ProtocolFailure,
    is StableTurnResponseAmbiguousFailure,
    -> true
    else -> false
  }
}
