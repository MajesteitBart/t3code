package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellStreamItem
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

public enum class ScopedEntityKind(public val wirePrefix: String) {
  PROJECT("project"),
  THREAD("thread"),
}

@JvmInline
public value class ScopedEntityId(public val value: String) {
  override fun toString(): String = value

  public companion object {
    public fun create(
      kind: ScopedEntityKind,
      environmentId: String,
      wireId: String,
    ): ScopedEntityId {
      require(environmentId.isNotEmpty()) { "Environment ID cannot be empty." }
      require(wireId.isNotEmpty()) { "Wire ID cannot be empty." }
      val byteCount = environmentId.toByteArray(StandardCharsets.UTF_8).size
      return ScopedEntityId("${kind.wirePrefix}:$byteCount:$environmentId$wireId")
    }

    public fun parse(value: String): ParsedScopedEntityId? {
      val firstSeparator = value.indexOf(':')
      if (firstSeparator <= 0) return null
      val secondSeparator = value.indexOf(':', firstSeparator + 1)
      if (secondSeparator <= firstSeparator + 1) return null
      val kindValue = value.substring(0, firstSeparator)
      val kind = ScopedEntityKind.entries.singleOrNull { it.wirePrefix == kindValue } ?: return null
      val environmentByteCount = value.substring(firstSeparator + 1, secondSeparator).toIntOrNull()
        ?: return null
      if (environmentByteCount <= 0) return null
      val remainder = value.substring(secondSeparator + 1).toByteArray(StandardCharsets.UTF_8)
      if (environmentByteCount >= remainder.size) return null
      val environmentBytes = remainder.copyOfRange(0, environmentByteCount)
      val wireBytes = remainder.copyOfRange(environmentByteCount, remainder.size)
      val environmentId = environmentBytes.decodeStrictUtf8() ?: return null
      val wireId = wireBytes.decodeStrictUtf8() ?: return null
      if (environmentId.isEmpty() || wireId.isEmpty()) return null
      val parsed = ParsedScopedEntityId(kind, environmentId, wireId)
      return parsed.takeIf { create(kind, environmentId, wireId).value == value }
    }
  }
}

public data class ParsedScopedEntityId(
  public val kind: ScopedEntityKind,
  public val environmentId: String,
  public val wireId: String,
)

public data class EnvironmentOwnerHandle(
  public val ownerEpoch: Long,
  public val clientEpoch: Long,
  public val sessionGeneration: Long?,
) {
  init {
    require(ownerEpoch > 0L) { "Owner epoch must be positive." }
    require(clientEpoch > 0L) { "Client epoch must be positive." }
    require(sessionGeneration == null || sessionGeneration > 0L) {
      "Session generation must be positive when present."
    }
  }
}

public enum class EnvironmentReachability {
  UNKNOWN,
  REACHABLE,
  UNREACHABLE,
  REVOKED,
}

public enum class ShellSourceState {
  RESTORED,
  LOADING,
  PASSIVE,
  LIVE,
  RECONNECTING,
  RELEASED,
}

public enum class ShellDataFreshness {
  NONE,
  LAST_KNOWN,
  FRESH,
}

public data class ShellProjectRow(
  public val uiId: ScopedEntityId,
  public val environmentId: String,
  public val wireId: String,
  public val title: String,
  public val lifecycle: String?,
  public val status: String?,
  public val archived: Boolean,
  public val provisional: Boolean,
  public val raw: JsonObject,
)

public data class ShellThreadRow(
  public val uiId: ScopedEntityId,
  public val environmentId: String,
  public val wireId: String,
  public val projectUiId: ScopedEntityId,
  public val projectWireId: String,
  public val title: String,
  public val lifecycle: String?,
  public val status: String?,
  public val archived: Boolean,
  public val provisional: Boolean,
  public val interactionMode: String?,
  public val raw: JsonObject,
)

public data class EnvironmentShellState(
  public val environment: SavedEnvironment,
  public val owner: EnvironmentOwnerHandle? = null,
  public val reachability: EnvironmentReachability = EnvironmentReachability.UNKNOWN,
  public val source: ShellSourceState = ShellSourceState.RESTORED,
  public val freshness: ShellDataFreshness = ShellDataFreshness.NONE,
  public val snapshotSequence: Long? = null,
  public val updatedAt: String? = null,
  public val safeError: String? = null,
  public val projects: Map<ScopedEntityId, ShellProjectRow> = emptyMap(),
  public val threads: Map<ScopedEntityId, ShellThreadRow> = emptyMap(),
)

public data class ShellAggregateState(
  public val environments: Map<String, EnvironmentShellState> = emptyMap(),
) {
  public val projects: Map<ScopedEntityId, ShellProjectRow> =
    environments.values.flatMap { it.projects.entries }.associate { it.toPair() }

  public val threads: Map<ScopedEntityId, ShellThreadRow> =
    environments.values.flatMap { it.threads.entries }.associate { it.toPair() }

  public fun resolveProject(uiId: ScopedEntityId): RouteResolution =
    resolveUiId(uiId, projects[uiId]?.wireId)

  public fun resolveThread(uiId: ScopedEntityId): RouteResolution =
    resolveUiId(uiId, threads[uiId]?.wireId)

  public fun resolveRaw(kind: ScopedEntityKind, wireId: String): RouteResolution {
    val candidates = when (kind) {
      ScopedEntityKind.PROJECT -> projects.values.filter { it.wireId == wireId }.map { it.uiId }
      ScopedEntityKind.THREAD -> threads.values.filter { it.wireId == wireId }.map { it.uiId }
    }
    return when (candidates.size) {
      0 -> RouteResolution.NotFound
      1 -> resolveUiId(candidates.single(), wireId)
      else -> RouteResolution.Ambiguous(candidates.sortedBy(ScopedEntityId::value))
    }
  }

  private fun resolveUiId(uiId: ScopedEntityId, wireId: String?): RouteResolution {
    val parsed = ScopedEntityId.parse(uiId.value) ?: return RouteResolution.NotFound
    val resolvedWireId = wireId ?: return RouteResolution.NotFound
    if (parsed.wireId != resolvedWireId) return RouteResolution.NotFound
    val environment = environments[parsed.environmentId] ?: return RouteResolution.NotFound
    val address = EntityAddress(uiId, resolvedWireId, parsed.environmentId)
    val owner = environment.owner ?: return RouteResolution.OwnerUnavailable(address)
    return RouteResolution.Resolved(EntityRoute(address, owner))
  }
}

public data class EntityAddress(
  public val uiId: ScopedEntityId,
  public val wireId: String,
  public val environmentId: String,
)

public data class EntityRoute(
  public val address: EntityAddress,
  public val owner: EnvironmentOwnerHandle,
)

public sealed interface RouteResolution {
  public data class Resolved(public val route: EntityRoute) : RouteResolution

  public data class OwnerUnavailable(public val address: EntityAddress) : RouteResolution

  public data class Ambiguous(public val candidateUiIds: List<ScopedEntityId>) : RouteResolution

  public data object NotFound : RouteResolution
}

public sealed interface ShellMutation {
  public data class RegisterEnvironment(
    public val environment: SavedEnvironment,
    public val lastKnown: OrchestrationShellSnapshot? = null,
  ) : ShellMutation

  public data class SetOwner(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle?,
    public val source: ShellSourceState,
  ) : ShellMutation

  public data class MarkLoading(public val environmentId: String) : ShellMutation

  public data class ReplaceSnapshot(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle?,
    public val snapshot: OrchestrationShellSnapshot,
    public val source: ShellSourceState,
  ) : ShellMutation

  public data class ApplyStreamItem(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle,
    public val item: OrchestrationShellStreamItem,
  ) : ShellMutation

  public data class MarkOffline(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle?,
    public val safeError: String?,
  ) : ShellMutation

  public data class MarkReconnecting(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle,
    public val safeError: String?,
  ) : ShellMutation

  public data class MarkReachable(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle,
  ) : ShellMutation

  public data class MarkRevoked(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle?,
    public val safeError: String? = null,
  ) : ShellMutation

  public data class RemoveEnvironment(public val environmentId: String) : ShellMutation
}

public object ShellReducer {
  public fun reduce(state: ShellAggregateState, mutation: ShellMutation): ShellAggregateState =
    when (mutation) {
      is ShellMutation.RegisterEnvironment -> register(state, mutation)
      is ShellMutation.SetOwner -> updateEnvironment(state, mutation.environmentId) { current ->
        current.copy(
          owner = mutation.owner,
          reachability = if (
            mutation.owner != null && current.reachability == EnvironmentReachability.REVOKED
          ) {
            EnvironmentReachability.UNKNOWN
          } else {
            current.reachability
          },
          source = mutation.source,
          safeError = null,
        )
      }
      is ShellMutation.MarkLoading -> updateEnvironment(state, mutation.environmentId) { current ->
        current.copy(source = ShellSourceState.LOADING, safeError = null)
      }
      is ShellMutation.ReplaceSnapshot -> replaceSnapshot(state, mutation)
      is ShellMutation.ApplyStreamItem -> applyStreamItem(state, mutation)
      is ShellMutation.MarkOffline -> updateOwned(state, mutation.environmentId, mutation.owner) { current ->
        current.copy(
          reachability = EnvironmentReachability.UNREACHABLE,
          source = if (current.owner == null) ShellSourceState.PASSIVE else current.source,
          safeError = mutation.safeError,
        )
      }
      is ShellMutation.MarkReconnecting ->
        updateOwned(state, mutation.environmentId, mutation.owner) { current ->
          current.copy(
            reachability = EnvironmentReachability.UNREACHABLE,
            source = ShellSourceState.RECONNECTING,
            safeError = mutation.safeError,
          )
        }
      is ShellMutation.MarkReachable ->
        updateOwned(state, mutation.environmentId, mutation.owner) { current ->
          current.copy(
            reachability = EnvironmentReachability.REACHABLE,
            safeError = null,
          )
        }
      is ShellMutation.MarkRevoked -> updateOwned(state, mutation.environmentId, mutation.owner) { current ->
        current.copy(
          reachability = EnvironmentReachability.REVOKED,
          source = ShellSourceState.RELEASED,
          owner = null,
          safeError = mutation.safeError,
        )
      }
      is ShellMutation.RemoveEnvironment -> state.copy(
        environments = state.environments - mutation.environmentId,
      )
    }

  private fun register(
    state: ShellAggregateState,
    mutation: ShellMutation.RegisterEnvironment,
  ): ShellAggregateState {
    val restored = mutation.lastKnown?.let { snapshot ->
      materialize(
        environment = mutation.environment,
        current = EnvironmentShellState(environment = mutation.environment),
        snapshot = snapshot,
        source = ShellSourceState.RESTORED,
        freshness = ShellDataFreshness.LAST_KNOWN,
      )
    } ?: EnvironmentShellState(environment = mutation.environment)
    return state.copy(environments = state.environments + (mutation.environment.environmentId to restored))
  }

  private fun replaceSnapshot(
    state: ShellAggregateState,
    mutation: ShellMutation.ReplaceSnapshot,
  ): ShellAggregateState = updateOwned(state, mutation.environmentId, mutation.owner) { current ->
    if (current.snapshotSequence != null && mutation.snapshot.snapshotSequence < current.snapshotSequence) {
      current
    } else {
      materialize(
        environment = current.environment,
        current = current,
        snapshot = mutation.snapshot,
        source = mutation.source,
        freshness = ShellDataFreshness.FRESH,
      )
    }
  }

  private fun applyStreamItem(
    state: ShellAggregateState,
    mutation: ShellMutation.ApplyStreamItem,
  ): ShellAggregateState = updateOwned(state, mutation.environmentId, mutation.owner) { current ->
    when (val item = mutation.item) {
      OrchestrationShellStreamItem.Synchronized -> current.copy(
        reachability = EnvironmentReachability.REACHABLE,
        source = ShellSourceState.LIVE,
        safeError = null,
      )
      is OrchestrationShellStreamItem.Snapshot -> {
        if (current.snapshotSequence != null && item.snapshot.snapshotSequence < current.snapshotSequence) {
          current
        } else {
          materialize(
            environment = current.environment,
            current = current,
            snapshot = item.snapshot,
            source = ShellSourceState.LIVE,
            freshness = ShellDataFreshness.FRESH,
          )
        }
      }
      is OrchestrationShellStreamItem.ProjectUpserted -> {
        if (isStale(item.sequence, current.snapshotSequence)) current else current.copy(
          snapshotSequence = item.sequence,
          projects = current.projects + projectRow(current.environment.environmentId, item.project),
          reachability = EnvironmentReachability.REACHABLE,
          source = ShellSourceState.LIVE,
          freshness = ShellDataFreshness.FRESH,
          safeError = null,
        )
      }
      is OrchestrationShellStreamItem.ProjectRemoved -> {
        if (isStale(item.sequence, current.snapshotSequence)) current else current.copy(
          snapshotSequence = item.sequence,
          projects = current.projects - ScopedEntityId.create(
            ScopedEntityKind.PROJECT,
            current.environment.environmentId,
            item.projectId,
          ),
          reachability = EnvironmentReachability.REACHABLE,
          source = ShellSourceState.LIVE,
          freshness = ShellDataFreshness.FRESH,
          safeError = null,
        )
      }
      is OrchestrationShellStreamItem.ThreadUpserted -> {
        if (isStale(item.sequence, current.snapshotSequence)) current else current.copy(
          snapshotSequence = item.sequence,
          threads = current.threads + threadRow(current.environment.environmentId, item.thread),
          reachability = EnvironmentReachability.REACHABLE,
          source = ShellSourceState.LIVE,
          freshness = ShellDataFreshness.FRESH,
          safeError = null,
        )
      }
      is OrchestrationShellStreamItem.ThreadRemoved -> {
        if (isStale(item.sequence, current.snapshotSequence)) current else current.copy(
          snapshotSequence = item.sequence,
          threads = current.threads - ScopedEntityId.create(
            ScopedEntityKind.THREAD,
            current.environment.environmentId,
            item.threadId,
          ),
          reachability = EnvironmentReachability.REACHABLE,
          source = ShellSourceState.LIVE,
          freshness = ShellDataFreshness.FRESH,
          safeError = null,
        )
      }
    }
  }

  private fun materialize(
    environment: SavedEnvironment,
    current: EnvironmentShellState,
    snapshot: OrchestrationShellSnapshot,
    source: ShellSourceState,
    freshness: ShellDataFreshness,
  ): EnvironmentShellState {
    val environmentId = environment.environmentId
    return current.copy(
      reachability = EnvironmentReachability.REACHABLE,
      source = source,
      freshness = freshness,
      snapshotSequence = snapshot.snapshotSequence,
      updatedAt = snapshot.updatedAt,
      safeError = null,
      projects = snapshot.projects.associate { projectRow(environmentId, it) },
      threads = snapshot.threads.associate { threadRow(environmentId, it) },
    )
  }

  private fun projectRow(environmentId: String, raw: JsonObject): Pair<ScopedEntityId, ShellProjectRow> {
    val wireId = raw.requiredString("id", "project")
    val uiId = ScopedEntityId.create(ScopedEntityKind.PROJECT, environmentId, wireId)
    return uiId to ShellProjectRow(
      uiId = uiId,
      environmentId = environmentId,
      wireId = wireId,
      title = raw.string("title") ?: wireId,
      lifecycle = raw.string("lifecycle"),
      status = raw.string("status"),
      archived = raw.string("archivedAt") != null || raw.boolean("archived") == true,
      provisional = raw.boolean("provisional") == true,
      raw = raw,
    )
  }

  private fun threadRow(environmentId: String, raw: JsonObject): Pair<ScopedEntityId, ShellThreadRow> {
    val wireId = raw.requiredString("id", "thread")
    val projectWireId = raw.requiredString("projectId", "thread")
    val uiId = ScopedEntityId.create(ScopedEntityKind.THREAD, environmentId, wireId)
    return uiId to ShellThreadRow(
      uiId = uiId,
      environmentId = environmentId,
      wireId = wireId,
      projectUiId = ScopedEntityId.create(ScopedEntityKind.PROJECT, environmentId, projectWireId),
      projectWireId = projectWireId,
      title = raw.string("title") ?: wireId,
      lifecycle = raw.string("lifecycle"),
      status = raw.string("status"),
      archived = raw.string("archivedAt") != null || raw.boolean("archived") == true,
      provisional = raw.boolean("provisional") == true,
      interactionMode = raw.string("interactionMode"),
      raw = raw,
    )
  }

  private fun updateOwned(
    state: ShellAggregateState,
    environmentId: String,
    owner: EnvironmentOwnerHandle?,
    transform: (EnvironmentShellState) -> EnvironmentShellState,
  ): ShellAggregateState = updateEnvironment(state, environmentId) { current ->
    if (current.owner != owner) current else transform(current)
  }

  private fun updateEnvironment(
    state: ShellAggregateState,
    environmentId: String,
    transform: (EnvironmentShellState) -> EnvironmentShellState,
  ): ShellAggregateState {
    val current = state.environments[environmentId] ?: return state
    val updated = transform(current)
    if (updated == current) return state
    return state.copy(environments = state.environments + (environmentId to updated))
  }

  private fun isStale(incoming: Long, current: Long?): Boolean = current != null && incoming <= current

  private fun JsonObject.requiredString(name: String, entity: String): String =
    string(name)?.takeIf(String::isNotEmpty)
      ?: throw CorruptEnvironmentStateFailure(
        IllegalArgumentException("Saved $entity state is missing a non-empty $name."),
      )

  private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

  private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull
}

private fun ByteArray.decodeStrictUtf8(): String? = runCatching {
  StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(this)).toString()
}.getOrNull()
