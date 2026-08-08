package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.ShellStreamDecodeResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

public data class ReconciliationSettings(
  public val canonicalRefreshTimeout: Duration = 6.seconds,
) {
  init {
    require(canonicalRefreshTimeout > Duration.ZERO) {
      "Canonical refresh timeout must be positive."
    }
  }
}

public data class CanonicalRefreshRequest(
  public val permit: PublicationPermit,
  public val unknownKind: String,
  public val timeout: Duration,
) {
  init {
    require(unknownKind.isNotEmpty()) { "Unknown stream kind cannot be empty." }
  }
}

public fun interface CanonicalSnapshotRefresher {
  public suspend fun refresh(request: CanonicalRefreshRequest): OrchestrationShellSnapshot
}

public enum class ReconciliationDisposition {
  APPLIED,
  CANONICAL_REFRESH_STARTED,
  CANONICAL_REFRESH_COALESCED,
  IGNORED_STALE,
  IGNORED_UNKNOWN_ENVIRONMENT,
  IGNORED_RELEASED,
  INVALID_SNAPSHOT,
}

/**
 * Serializes persisted, HTTP, and stream state behind explicit authority and refresh fences.
 * Cancellation releases resources; correctness comes from checking the same guard again at
 * publication time.
 */
public class ShellStateReconciler(
  parentScope: CoroutineScope,
  private val canonicalRefresher: CanonicalSnapshotRefresher,
  initialState: ShellAggregateState = ShellAggregateState(),
  private val settings: ReconciliationSettings = ReconciliationSettings(),
) {
  private data class AuthorityRecord(
    val authorityEpoch: Long,
    val owner: EnvironmentOwnerHandle,
    val mode: EnvironmentSupervisionMode,
    var latestRefreshEpoch: Long = 0L,
    var revoked: Boolean = false,
  )

  private data class RefreshGuard(
    val environmentId: String,
    val authorityEpoch: Long,
    val owner: EnvironmentOwnerHandle,
    val triggerRefreshEpoch: Long,
  )

  private data class UnknownRefreshRecord(
    val guard: RefreshGuard,
    val job: Job,
  )

  private val reconcilerJob = SupervisorJob(parentScope.coroutineContext[Job])
  private val scope = CoroutineScope(parentScope.coroutineContext + reconcilerJob)
  private val mutex = Mutex()
  private val mutableState = MutableStateFlow(initialState)
  private val authorityFloors = mutableMapOf<String, Long>()
  private val authorities = mutableMapOf<String, AuthorityRecord>()
  private val unknownRefreshes = mutableMapOf<String, UnknownRefreshRecord>()
  private var released = false

  public val state: StateFlow<ShellAggregateState> = mutableState.asStateFlow()

  public suspend fun registerEnvironment(
    environment: SavedEnvironment,
    lastKnown: OrchestrationShellSnapshot? = null,
  ): ReconciliationDisposition = mutex.withLock {
    if (released) return@withLock ReconciliationDisposition.IGNORED_RELEASED
    var next = ShellReducer.reduce(
      mutableState.value,
      ShellMutation.RegisterEnvironment(environment, lastKnown),
    )
    authorities[environment.environmentId]?.takeUnless(AuthorityRecord::revoked)?.let { authority ->
      next = ShellReducer.reduce(
        next,
        ShellMutation.SetOwner(
          environment.environmentId,
          authority.owner,
          sourceForAuthority(next.environments.getValue(environment.environmentId), authority),
        ),
      )
    }
    mutableState.value = next
    ReconciliationDisposition.APPLIED
  }

  public suspend fun removeEnvironment(environmentId: String): ReconciliationDisposition {
    val refreshJob = mutex.withLock {
      if (released) return ReconciliationDisposition.IGNORED_RELEASED
      authorities.remove(environmentId)
      val removed = unknownRefreshes.remove(environmentId)?.job
      mutableState.value = ShellReducer.reduce(
        mutableState.value,
        ShellMutation.RemoveEnvironment(environmentId),
      )
      removed
    }
    refreshJob?.cancelAndJoin()
    return ReconciliationDisposition.APPLIED
  }

  public suspend fun accept(publication: ConnectionPublication): ReconciliationDisposition =
    mutex.withLock {
      if (released) return@withLock ReconciliationDisposition.IGNORED_RELEASED
      when (publication) {
        is ConnectionPublication.AuthorityChanged -> acceptAuthority(publication)
        is ConnectionPublication.SnapshotLoaded -> acceptSnapshot(publication)
        is ConnectionPublication.StreamReceived -> acceptStream(publication)
        is ConnectionPublication.ReachabilityChanged -> acceptReachability(publication)
        is ConnectionPublication.Revoked -> acceptRevocation(publication)
      }
    }

  public suspend fun collect(publications: Flow<ConnectionPublication>) {
    publications.collect { publication -> accept(publication) }
  }

  public suspend fun release() {
    val refreshJobs = mutex.withLock {
      if (released) return
      released = true
      val jobs = unknownRefreshes.values.map(UnknownRefreshRecord::job)
      unknownRefreshes.clear()
      authorities.clear()
      var next = mutableState.value
      for (environmentId in next.environments.keys) {
        next = ShellReducer.reduce(
          next,
          ShellMutation.SetOwner(environmentId, owner = null, source = ShellSourceState.RELEASED),
        )
      }
      mutableState.value = next
      jobs
    }
    refreshJobs.forEach(Job::cancel)
    for (refreshJob in refreshJobs) refreshJob.join()
    reconcilerJob.cancelAndJoin()
  }

  private fun acceptAuthority(
    publication: ConnectionPublication.AuthorityChanged,
  ): ReconciliationDisposition {
    val floor = authorityFloors[publication.environmentId] ?: 0L
    if (publication.authorityEpoch <= floor) return ReconciliationDisposition.IGNORED_STALE
    authorityFloors[publication.environmentId] = publication.authorityEpoch
    unknownRefreshes.remove(publication.environmentId)?.job?.cancel()

    val current = mutableState.value.environments[publication.environmentId]
      ?: return ReconciliationDisposition.IGNORED_UNKNOWN_ENVIRONMENT
    val owner = publication.owner
    val mode = publication.mode
    if (owner == null || mode == null) {
      authorities.remove(publication.environmentId)
      mutableState.value = ShellReducer.reduce(
        mutableState.value,
        ShellMutation.SetOwner(
          publication.environmentId,
          owner = null,
          source = ShellSourceState.RELEASED,
        ),
      )
      return ReconciliationDisposition.APPLIED
    }

    val authority = AuthorityRecord(publication.authorityEpoch, owner, mode)
    authorities[publication.environmentId] = authority
    mutableState.value = ShellReducer.reduce(
      mutableState.value,
      ShellMutation.SetOwner(
        publication.environmentId,
        owner,
        sourceForAuthority(current, authority),
      ),
    )
    return ReconciliationDisposition.APPLIED
  }

  private fun acceptSnapshot(
    publication: ConnectionPublication.SnapshotLoaded,
  ): ReconciliationDisposition {
    val authority = currentAuthority(publication.permit) ?: return staleOrUnknown(publication.permit)
    if (
      publication.origin == ShellSnapshotOrigin.PASSIVE_HTTP &&
      authority.mode != EnvironmentSupervisionMode.PASSIVE ||
      publication.origin == ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK &&
      authority.mode != EnvironmentSupervisionMode.ACTIVE
    ) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    if (!advanceRefresh(authority, publication.permit)) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    val source = when (publication.origin) {
      ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK -> ShellSourceState.RECONNECTING
      ShellSnapshotOrigin.PASSIVE_HTTP -> ShellSourceState.PASSIVE
    }
    return reduceSafely(
      ShellMutation.ReplaceSnapshot(
        publication.permit.environmentId,
        publication.permit.owner,
        publication.snapshot,
        source,
      ),
    )
  }

  private fun acceptStream(
    publication: ConnectionPublication.StreamReceived,
  ): ReconciliationDisposition {
    val authority = currentAuthority(publication.permit) ?: return staleOrUnknown(publication.permit)
    if (
      authority.mode != EnvironmentSupervisionMode.ACTIVE ||
      publication.permit.owner.sessionGeneration == null
    ) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    if (!advanceRefresh(authority, publication.permit)) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    return when (val result = publication.result) {
      is ShellStreamDecodeResult.Decoded -> reduceSafely(
        ShellMutation.ApplyStreamItem(
          publication.permit.environmentId,
          publication.permit.owner,
          result.item,
        ),
      )
      is ShellStreamDecodeResult.RefreshRequired -> scheduleCanonicalRefresh(
        publication.permit,
        authority,
        result.unknownKind,
      )
    }
  }

  private fun acceptReachability(
    publication: ConnectionPublication.ReachabilityChanged,
  ): ReconciliationDisposition {
    val authority = currentAuthority(publication.permit) ?: return staleOrUnknown(publication.permit)
    if (publication.reconnecting && authority.mode != EnvironmentSupervisionMode.ACTIVE) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    if (!advanceRefresh(authority, publication.permit)) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    val mutation = when {
      publication.reachable -> ShellMutation.MarkReachable(
        publication.permit.environmentId,
        publication.permit.owner,
      )
      publication.reconnecting && authority.mode == EnvironmentSupervisionMode.ACTIVE ->
        ShellMutation.MarkReconnecting(
          publication.permit.environmentId,
          publication.permit.owner,
          publication.safeError,
        )
      else -> ShellMutation.MarkOffline(
        publication.permit.environmentId,
        publication.permit.owner,
        publication.safeError,
      )
    }
    return reduceSafely(mutation)
  }

  private fun acceptRevocation(
    publication: ConnectionPublication.Revoked,
  ): ReconciliationDisposition {
    val authority = currentAuthority(publication.permit) ?: return staleOrUnknown(publication.permit)
    if (!advanceRefresh(authority, publication.permit)) {
      return ReconciliationDisposition.IGNORED_STALE
    }
    authority.revoked = true
    unknownRefreshes.remove(publication.permit.environmentId)?.job?.cancel()
    return reduceSafely(
      ShellMutation.MarkRevoked(
        publication.permit.environmentId,
        publication.permit.owner,
        publication.safeError,
      ),
    )
  }

  private fun currentAuthority(permit: PublicationPermit): AuthorityRecord? {
    val authority = authorities[permit.environmentId] ?: return null
    if (authority.revoked || authority.owner != permit.owner) return null
    return authority
  }

  private fun advanceRefresh(authority: AuthorityRecord, permit: PublicationPermit): Boolean {
    if (permit.refreshEpoch < authority.latestRefreshEpoch) return false
    authority.latestRefreshEpoch = permit.refreshEpoch
    return true
  }

  private fun staleOrUnknown(permit: PublicationPermit): ReconciliationDisposition =
    if (mutableState.value.environments.containsKey(permit.environmentId)) {
      ReconciliationDisposition.IGNORED_STALE
    } else {
      ReconciliationDisposition.IGNORED_UNKNOWN_ENVIRONMENT
    }

  private fun scheduleCanonicalRefresh(
    permit: PublicationPermit,
    authority: AuthorityRecord,
    unknownKind: String,
  ): ReconciliationDisposition {
    if (unknownRefreshes[permit.environmentId]?.guard?.authorityEpoch == authority.authorityEpoch) {
      return ReconciliationDisposition.CANONICAL_REFRESH_COALESCED
    }
    val guard = RefreshGuard(
      permit.environmentId,
      authority.authorityEpoch,
      permit.owner,
      permit.refreshEpoch,
    )
    val request = CanonicalRefreshRequest(permit, unknownKind, settings.canonicalRefreshTimeout)
    val job = scope.launch(start = CoroutineStart.LAZY) { runCanonicalRefresh(guard, request) }
    unknownRefreshes[permit.environmentId] = UnknownRefreshRecord(guard, job)
    job.start()
    return ReconciliationDisposition.CANONICAL_REFRESH_STARTED
  }

  private suspend fun runCanonicalRefresh(
    guard: RefreshGuard,
    request: CanonicalRefreshRequest,
  ) {
    val snapshot = try {
      withTimeout(request.timeout) { canonicalRefresher.refresh(request) }
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      return
    }
    mutex.withLock {
      if (released) return@withLock
      val authority = authorities[guard.environmentId] ?: return@withLock
      val refresh = unknownRefreshes[guard.environmentId] ?: return@withLock
      if (
        authority.authorityEpoch != guard.authorityEpoch ||
        authority.owner != guard.owner ||
        authority.revoked ||
        authority.latestRefreshEpoch != guard.triggerRefreshEpoch ||
        refresh.guard != guard
      ) {
        return@withLock
      }
      val source = when (authority.mode) {
        EnvironmentSupervisionMode.ACTIVE -> ShellSourceState.LIVE
        EnvironmentSupervisionMode.PASSIVE -> ShellSourceState.PASSIVE
      }
      reduceSafely(
        ShellMutation.ReplaceSnapshot(
          guard.environmentId,
          guard.owner,
          snapshot,
          source,
        ),
      )
    }
  }

  private fun sourceForAuthority(
    current: EnvironmentShellState,
    authority: AuthorityRecord,
  ): ShellSourceState = when (authority.mode) {
    EnvironmentSupervisionMode.PASSIVE -> ShellSourceState.PASSIVE
    EnvironmentSupervisionMode.ACTIVE -> if (current.freshness == ShellDataFreshness.NONE) {
      ShellSourceState.LOADING
    } else {
      ShellSourceState.RECONNECTING
    }
  }

  private fun reduceSafely(mutation: ShellMutation): ReconciliationDisposition = try {
    mutableState.value = ShellReducer.reduce(mutableState.value, mutation)
    ReconciliationDisposition.APPLIED
  } catch (_: CorruptEnvironmentStateFailure) {
    ReconciliationDisposition.INVALID_SNAPSHOT
  }
}
