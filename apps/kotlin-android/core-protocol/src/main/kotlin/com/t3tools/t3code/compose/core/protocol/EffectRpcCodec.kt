package com.t3tools.t3code.compose.core.protocol

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

public enum class RpcReplacementPolicy {
  NEVER,
  SUPERVISOR_MAY_RECREATE_LONG_LIVED_INTENT,
}

public enum class RpcUnsentFallbackPolicy {
  EXPLICIT_HTTP_ALLOWED,
  WEBSOCKET_ONLY,
}

public enum class FoundationRpcMethod(
  public val wireTag: String,
  public val replacementPolicy: RpcReplacementPolicy,
) {
  DISPATCH_COMMAND("orchestration.dispatchCommand", RpcReplacementPolicy.NEVER),
  SUBSCRIBE_SHELL(
    "orchestration.subscribeShell",
    RpcReplacementPolicy.SUPERVISOR_MAY_RECREATE_LONG_LIVED_INTENT,
  ),
}

public object RpcDispatchPolicy {
  /** Bootstrap turn-start commands cannot be expanded by the HTTP dispatch endpoint. */
  public fun unsentFallback(command: JsonObject): RpcUnsentFallbackPolicy {
    val type = (command["type"] as? JsonPrimitive)?.contentOrNull
    return if (type == "thread.turn.start" && command["bootstrap"] is JsonObject) {
      RpcUnsentFallbackPolicy.WEBSOCKET_ONLY
    } else {
      RpcUnsentFallbackPolicy.EXPLICIT_HTTP_ALLOWED
    }
  }
}

/** Allocates JSON-safe, monotonically increasing Effect RPC request identifiers. */
public class RpcRequestIdSequence(startAt: Long = 1L) {
  private val next = AtomicLong(startAt)

  init {
    require(startAt in 1L..MAX_SAFE_JSON_INTEGER) {
      "Effect RPC request IDs must start inside the positive JSON-safe integer range."
    }
  }

  public fun next(): Long {
    while (true) {
      val candidate = next.get()
      check(candidate in 1L..MAX_SAFE_JSON_INTEGER) { "Effect RPC request ID space is exhausted." }
      if (next.compareAndSet(candidate, candidate + 1L)) return candidate
    }
  }

  private companion object {
    const val MAX_SAFE_JSON_INTEGER: Long = 9_007_199_254_740_991L
  }
}

public sealed interface RpcControlFrame {
  public data object Ping : RpcControlFrame

  public data object Pong : RpcControlFrame

  public data class Ack(public val requestId: Long) : RpcControlFrame

  public data class Interrupt(public val requestId: Long) : RpcControlFrame
}

public sealed interface RpcInboundFrame {
  public data object Ping : RpcInboundFrame

  public data object Pong : RpcInboundFrame

  public data class Ack(public val requestId: Long) : RpcInboundFrame

  public data class Interrupt(public val requestId: Long) : RpcInboundFrame

  public data class Chunk(
    public val requestId: Long,
    public val values: List<JsonElement>,
  ) : RpcInboundFrame

  public data class Exit(
    public val requestId: Long,
    public val outcome: RpcExitOutcome,
  ) : RpcInboundFrame

  public data class Defect(public val error: FatalRpcProtocolFailure) : RpcInboundFrame

  public data class ClientProtocolError(
    public val error: FatalRpcProtocolFailure,
  ) : RpcInboundFrame
}

public sealed interface RpcExitOutcome {
  public data class Success(public val value: JsonElement) : RpcExitOutcome

  public data class Failure(public val error: RemoteRpcFailure) : RpcExitOutcome
}

public class RemoteRpcFailure internal constructor(
  public val requestId: Long,
  public val remoteTag: String?,
  override val safeMessage: String,
) : IOException(safeMessage), ProtocolFailure

public class FatalRpcProtocolFailure internal constructor(
  public val frameTag: String,
  override val safeMessage: String,
) : IOException(safeMessage), ProtocolFailure

public sealed interface OrchestrationShellStreamItem {
  public data object Synchronized : OrchestrationShellStreamItem

  public data class Snapshot(
    public val snapshot: OrchestrationShellSnapshot,
  ) : OrchestrationShellStreamItem

  public data class ProjectUpserted(
    public val sequence: Long,
    public val project: JsonObject,
  ) : OrchestrationShellStreamItem

  public data class ProjectRemoved(
    public val sequence: Long,
    public val projectId: String,
  ) : OrchestrationShellStreamItem

  public data class ThreadUpserted(
    public val sequence: Long,
    public val thread: JsonObject,
  ) : OrchestrationShellStreamItem

  public data class ThreadRemoved(
    public val sequence: Long,
    public val threadId: String,
  ) : OrchestrationShellStreamItem
}

public sealed interface ShellStreamDecodeResult {
  public data class Decoded(
    public val item: OrchestrationShellStreamItem,
  ) : ShellStreamDecodeResult

  /** A future union member must trigger a fresh snapshot instead of a partial state mutation. */
  public data class RefreshRequired(public val unknownKind: String) : ShellStreamDecodeResult
}

/**
 * Effect RPC JSON framing only. This codec has no socket, retry, timing, or lifecycle behavior.
 */
public object EffectRpcCodec {
  private val json = ContractJson.format

  public fun encodeDispatchRequest(id: Long, command: JsonObject): String =
    encodeRequest(id, FoundationRpcMethod.DISPATCH_COMMAND, command)

  public fun encodeSubscribeShellRequest(
    id: Long,
    input: OrchestrationSubscribeShellInput,
  ): String = encodeRequest(
    id,
    FoundationRpcMethod.SUBSCRIBE_SHELL,
    json.encodeToJsonElement(input).jsonObject,
  )

  public fun encodeRequest(
    id: Long,
    method: FoundationRpcMethod,
    payload: JsonObject,
  ): String {
    requireRequestId(id)
    return json.encodeToString(
      buildJsonObject {
        put("_tag", "Request")
        put("id", id)
        put("tag", method.wireTag)
        put("payload", payload)
        put("headers", buildJsonArray {})
      },
    )
  }

  public fun encodeControl(frame: RpcControlFrame): String = json.encodeToString(
    buildJsonObject {
      when (frame) {
        RpcControlFrame.Ping -> put("_tag", "Ping")
        RpcControlFrame.Pong -> put("_tag", "Pong")
        is RpcControlFrame.Ack -> {
          requireRequestId(frame.requestId)
          put("_tag", "Ack")
          put("requestId", frame.requestId)
        }
        is RpcControlFrame.Interrupt -> {
          requireRequestId(frame.requestId)
          put("_tag", "Interrupt")
          put("requestId", frame.requestId)
        }
      }
    },
  )

  public fun decodeFrame(encoded: String): RpcInboundFrame {
    val envelope = try {
      json.parseToJsonElement(encoded).jsonObject
    } catch (error: SerializationException) {
      throw ProtocolViolationFailure("The RPC frame is not valid JSON.", error)
    } catch (error: IllegalArgumentException) {
      throw ProtocolViolationFailure("The RPC frame must be a JSON object.", error)
    }

    return when (val tag = envelope.requiredString("_tag")) {
      "Ping" -> RpcInboundFrame.Ping
      "Pong" -> RpcInboundFrame.Pong
      "Ack" -> RpcInboundFrame.Ack(envelope.requiredRequestId())
      "Interrupt" -> RpcInboundFrame.Interrupt(envelope.requiredRequestId())
      "Chunk" -> {
        val values = envelope["values"] as? JsonArray
          ?: throw ProtocolViolationFailure("An RPC Chunk frame requires values.")
        if (values.isEmpty()) throw ProtocolViolationFailure("An RPC Chunk frame cannot be empty.")
        RpcInboundFrame.Chunk(envelope.requiredRequestId(), values)
      }
      "Exit" -> decodeExit(envelope)
      "Defect" -> RpcInboundFrame.Defect(
        fatalFailure(tag, envelope["defect"]),
      )
      "ClientProtocolError" -> RpcInboundFrame.ClientProtocolError(
        fatalFailure(tag, envelope["error"]),
      )
      else -> throw ProtocolViolationFailure("Unknown RPC frame tag $tag.")
    }
  }

  public fun decodeShellChunk(frame: RpcInboundFrame.Chunk): List<ShellStreamDecodeResult> =
    frame.values.map(::decodeShellItem)

  public fun decodeShellItem(value: JsonElement): ShellStreamDecodeResult {
    val item = value as? JsonObject
      ?: throw ProtocolViolationFailure("A shell stream item must be a JSON object.")
    val kind = item.requiredString("kind")
    return try {
      when (kind) {
        "synchronized" -> decoded(OrchestrationShellStreamItem.Synchronized)
        "snapshot" -> decoded(
          OrchestrationShellStreamItem.Snapshot(
            json.decodeFromJsonElement(item.requiredElement("snapshot")),
          ),
        )
        "project-upserted" -> decoded(
          OrchestrationShellStreamItem.ProjectUpserted(
            sequence = item.requiredSequence(),
            project = item.requiredObject("project"),
          ),
        )
        "project-removed" -> decoded(
          OrchestrationShellStreamItem.ProjectRemoved(
            sequence = item.requiredSequence(),
            projectId = item.requiredString("projectId"),
          ),
        )
        "thread-upserted" -> decoded(
          OrchestrationShellStreamItem.ThreadUpserted(
            sequence = item.requiredSequence(),
            thread = item.requiredObject("thread"),
          ),
        )
        "thread-removed" -> decoded(
          OrchestrationShellStreamItem.ThreadRemoved(
            sequence = item.requiredSequence(),
            threadId = item.requiredString("threadId"),
          ),
        )
        else -> ShellStreamDecodeResult.RefreshRequired(kind)
      }
    } catch (error: ProtocolViolationFailure) {
      throw error
    } catch (error: SerializationException) {
      throw ProtocolViolationFailure("Shell stream item $kind is incompatible.", error)
    } catch (error: IllegalArgumentException) {
      throw ProtocolViolationFailure("Shell stream item $kind is incompatible.", error)
    }
  }

  private fun decodeExit(envelope: JsonObject): RpcInboundFrame.Exit {
    val requestId = envelope.requiredRequestId()
    val exit = envelope.requiredObject("exit")
    val outcome = when (val exitTag = exit.requiredString("_tag")) {
      "Success" -> RpcExitOutcome.Success(exit["value"] ?: JsonNull)
      "Failure" -> RpcExitOutcome.Failure(remoteFailure(requestId, exit))
      else -> throw ProtocolViolationFailure("Unknown RPC Exit tag $exitTag.")
    }
    return RpcInboundFrame.Exit(requestId, outcome)
  }

  private fun remoteFailure(requestId: Long, exit: JsonObject): RemoteRpcFailure {
    val cause = (exit["cause"] as? JsonArray)
      ?.firstOrNull()
      ?.let { it as? JsonObject }
    val error = cause?.get("error")
    val remoteTag = (error as? JsonObject)
      ?.get("_tag")
      ?.let { it as? JsonPrimitive }
      ?.contentOrNull
    val message = findFailureMessage(error)
      ?: "The environment rejected the RPC request."
    return RemoteRpcFailure(requestId, remoteTag, message.take(MAX_ERROR_LENGTH))
  }

  private fun fatalFailure(frameTag: String, detail: JsonElement?): FatalRpcProtocolFailure {
    val message = findFailureMessage(detail)
      ?: "The server reported an RPC protocol error."
    return FatalRpcProtocolFailure(frameTag, message.take(MAX_ERROR_LENGTH))
  }

  private fun findFailureMessage(value: JsonElement?, depth: Int = 0): String? {
    if (value == null || depth > MAX_ERROR_DEPTH) return null
    if (value is JsonObject) {
      for (key in listOf("message", "detail")) {
        val message = (value[key] as? JsonPrimitive)?.contentOrNull
        if (!message.isNullOrBlank()) return message
      }
      for (nested in value.values) {
        findFailureMessage(nested, depth + 1)?.let { return it }
      }
    }
    if (value is JsonArray) {
      for (nested in value) {
        findFailureMessage(nested, depth + 1)?.let { return it }
      }
    }
    return null
  }

  private fun decoded(item: OrchestrationShellStreamItem): ShellStreamDecodeResult =
    ShellStreamDecodeResult.Decoded(item)

  private fun JsonObject.requiredElement(name: String): JsonElement =
    this[name] ?: throw ProtocolViolationFailure("RPC payload is missing $name.")

  private fun JsonObject.requiredObject(name: String): JsonObject =
    requiredElement(name) as? JsonObject
      ?: throw ProtocolViolationFailure("RPC payload field $name must be an object.")

  private fun JsonObject.requiredString(name: String): String {
    val primitive = requiredElement(name) as? JsonPrimitive
      ?: throw ProtocolViolationFailure("RPC payload field $name must be a string.")
    if (!primitive.isString) throw ProtocolViolationFailure("RPC payload field $name must be a string.")
    return primitive.content
  }

  private fun JsonObject.requiredRequestId(): Long =
    requiredLong("requestId").also(::requireRequestId)

  private fun JsonObject.requiredSequence(): Long = requiredLong("sequence").also { sequence ->
    if (sequence < 0L) throw ProtocolViolationFailure("Shell stream sequence must be non-negative.")
  }

  private fun JsonObject.requiredLong(name: String): Long {
    val primitive = requiredElement(name) as? JsonPrimitive
      ?: throw ProtocolViolationFailure("RPC payload field $name must be an integer.")
    if (primitive.isString) throw ProtocolViolationFailure("RPC payload field $name must be an integer.")
    return primitive.longOrNull
      ?: throw ProtocolViolationFailure("RPC payload field $name must be a 64-bit integer.")
  }

  private fun requireRequestId(requestId: Long) {
    if (requestId !in 1L..MAX_SAFE_JSON_INTEGER) {
      throw ProtocolViolationFailure("RPC request ID must be a positive JSON-safe integer.")
    }
  }

  private const val MAX_SAFE_JSON_INTEGER: Long = 9_007_199_254_740_991L
  private const val MAX_ERROR_LENGTH: Int = 512
  private const val MAX_ERROR_DEPTH: Int = 8
}
