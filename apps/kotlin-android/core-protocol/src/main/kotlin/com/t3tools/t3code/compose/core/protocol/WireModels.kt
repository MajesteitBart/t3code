package com.t3tools.t3code.compose.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
public data class ExecutionEnvironmentDescriptor(
  public val environmentId: String,
  public val label: String,
  public val platform: ExecutionEnvironmentPlatform,
  public val serverVersion: String,
  public val capabilities: ExecutionEnvironmentCapabilities,
)

@Serializable
public data class ExecutionEnvironmentPlatform(
  public val os: ExecutionEnvironmentPlatformOs,
  public val arch: ExecutionEnvironmentPlatformArch,
)

@Serializable
public enum class ExecutionEnvironmentPlatformOs {
  @SerialName("darwin")
  DARWIN,

  @SerialName("linux")
  LINUX,

  @SerialName("windows")
  WINDOWS,

  @SerialName("unknown")
  UNKNOWN,
}

@Serializable
public enum class ExecutionEnvironmentPlatformArch {
  @SerialName("arm64")
  ARM64,

  @SerialName("x64")
  X64,

  @SerialName("other")
  OTHER,
}

@Serializable
public data class ExecutionEnvironmentCapabilities(
  public val repositoryIdentity: Boolean = false,
  public val connectionProbe: Boolean? = null,
  public val threadSettlement: Boolean? = null,
  public val threadSnooze: Boolean? = null,
  public val threadPinning: Boolean? = null,
  public val threadTitleRegeneration: Boolean? = null,
  public val serverSelfUpdate: String? = null,
  public val serverSelfUpdateProgress: Boolean? = null,
)

@Serializable
public data class AuthAccessTokenResult(
  @SerialName("access_token") public val accessToken: String,
  @SerialName("issued_token_type") public val issuedTokenType: String,
  @SerialName("token_type") public val tokenType: String,
  @SerialName("expires_in") public val expiresIn: Double,
  public val scope: String,
)

@Serializable
public data class AuthWebSocketTicketResult(
  public val ticket: String,
  public val expiresAt: String,
)

@Serializable
public data class EnvironmentHttpError(
  @SerialName("_tag") public val tag: String,
  public val code: String? = null,
  public val reason: String? = null,
  public val message: String? = null,
  public val traceId: String? = null,
  public val requiredScope: String? = null,
)

@Serializable(with = ModelSelectionSerializer::class)
public data class ModelSelection(
  public val instanceId: String,
  public val model: String,
  public val options: JsonObject? = null,
)

public object ModelSelectionSerializer : KSerializer<ModelSelection> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ModelSelection") {
    element<String>("instanceId", isOptional = true)
    element<String>("provider", isOptional = true)
    element<String>("model")
    element<JsonObject>("options", isOptional = true)
  }

  override fun deserialize(decoder: Decoder): ModelSelection {
    val jsonDecoder = decoder as? JsonDecoder
      ?: throw SerializationException("ModelSelection is only supported in JSON.")
    val objectValue = jsonDecoder.decodeJsonElement().jsonObject
    val instanceId = objectValue["instanceId"]?.jsonPrimitive?.contentOrNull
      ?: objectValue["provider"]?.jsonPrimitive?.contentOrNull
      ?: throw SerializationException("ModelSelection requires instanceId or legacy provider.")
    val model = objectValue["model"]?.jsonPrimitive?.contentOrNull
      ?: throw SerializationException("ModelSelection requires model.")
    if (instanceId.isBlank() || model.isBlank()) {
      throw SerializationException("ModelSelection identifiers must be non-empty.")
    }
    return ModelSelection(
      instanceId = instanceId,
      model = model,
      options = objectValue["options"] as? JsonObject,
    )
  }

  override fun serialize(encoder: Encoder, value: ModelSelection) {
    val jsonEncoder = encoder as? JsonEncoder
      ?: throw SerializationException("ModelSelection is only supported in JSON.")
    val fields = linkedMapOf<String, JsonElement>(
      "instanceId" to JsonPrimitive(value.instanceId),
      "model" to JsonPrimitive(value.model),
    )
    value.options?.let { fields["options"] = it }
    jsonEncoder.encodeJsonElement(JsonObject(fields))
  }
}

@Serializable
public enum class ProviderInteractionMode {
  @SerialName("default")
  DEFAULT,

  @SerialName("plan")
  PLAN,
}

@Serializable
public data class OrchestrationShellSnapshot(
  public val snapshotSequence: Long,
  public val projects: List<JsonObject>,
  public val threads: List<JsonObject>,
  public val updatedAt: String,
)

@Serializable
public data class OrchestrationSubscribeShellInput(
  public val afterSequence: Long? = null,
  public val requestCompletionMarker: Boolean? = null,
)

@Serializable
public data class DispatchResult(
  public val sequence: Long,
)

@Serializable
public data class OrchestrationThreadDetailSnapshot(
  public val snapshotSequence: Long,
  public val thread: OrchestrationThread,
  public val page: OrchestrationThreadDetailPage? = null,
)

@Serializable
public data class OrchestrationThreadDetailPage(
  public val beforeCursor: String?,
  public val hasMore: Boolean,
  public val snapshotSequence: Long,
  public val threadSequence: Long? = null,
)

@Serializable
public data class OrchestrationThread(
  public val id: String,
  public val projectId: String,
  public val title: String,
  public val modelSelection: ModelSelection,
  public val runtimeMode: String,
  public val interactionMode: ProviderInteractionMode = ProviderInteractionMode.DEFAULT,
  public val branch: String?,
  public val worktreePath: String?,
  public val latestTurn: JsonElement?,
  public val createdAt: String,
  public val updatedAt: String,
  public val archivedAt: String? = null,
  public val settledOverride: String? = null,
  public val settledAt: String? = null,
  public val snoozedUntil: String? = null,
  public val snoozedAt: String? = null,
  public val pinnedAt: String? = null,
  public val titleRegeneration: JsonElement? = null,
  public val deletedAt: String?,
  public val messages: List<OrchestrationMessage>,
  public val proposedPlans: List<JsonElement> = emptyList(),
  public val activities: List<JsonElement>,
  public val checkpoints: List<JsonElement>,
  public val session: JsonElement?,
)

@Serializable
public data class OrchestrationMessage(
  public val id: String,
  public val role: String,
  public val text: String,
  public val attachments: List<JsonElement>? = null,
  public val turnId: String?,
  public val streaming: Boolean,
  public val createdAt: String,
  public val updatedAt: String,
)
