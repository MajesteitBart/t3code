package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.AuthorizationRejectedFailure
import com.t3tools.t3code.compose.core.protocol.EnvironmentHttpClient
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.OrchestrationSubscribeShellInput
import com.t3tools.t3code.compose.core.protocol.ProtocolFailure
import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import com.t3tools.t3code.compose.core.protocol.RpcSubscriptionMode
import com.t3tools.t3code.compose.core.protocol.ShellStreamDecodeResult
import com.t3tools.t3code.compose.core.protocol.TicketedRpcSession
import com.t3tools.t3code.compose.core.protocol.TicketedRpcSessionFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public data class ConnectionSupervisionSettings(
  public val passiveCadence: Duration = 20.seconds,
  public val passiveTimeout: Duration = 6.seconds,
  public val activeFallbackTimeout: Duration = 6.seconds,
  public val backoff: ReconnectBackoff = ReconnectBackoff(),
) {
  init {
    require(passiveCadence > Duration.ZERO) { "Passive cadence must be positive." }
    require(passiveTimeout > Duration.ZERO) { "Passive timeout must be positive." }
    require(activeFallbackTimeout > Duration.ZERO) { "Active fallback timeout must be positive." }
  }
}

public data class ReconnectBackoff(
  public val initial: Duration = 500.milliseconds,
  public val maximum: Duration = 30.seconds,
  public val multiplier: Double = 2.0,
  public val jitterRatio: Double = 0.2,
  public val maximumConsecutiveFailures: Int = 8,
) {
  init {
    require(initial > Duration.ZERO) { "Initial backoff must be positive." }
    require(maximum >= initial) { "Maximum backoff cannot be shorter than initial backoff." }
    require(multiplier >= 1.0) { "Backoff multiplier must be at least one." }
    require(jitterRatio in 0.0..1.0) { "Backoff jitter ratio must be between zero and one." }
    require(maximumConsecutiveFailures > 0) { "Maximum failures must be positive." }
  }

  public fun delayFor(failureNumber: Int, randomUnit: Double): Duration {
    require(failureNumber > 0) { "Failure number must be positive." }
    require(randomUnit in 0.0..1.0) { "Random sample must be between zero and one." }
    val exponential = initial.inWholeMilliseconds.toDouble() * multiplier.pow(failureNumber - 1)
    val bounded = exponential.coerceAtMost(maximum.inWholeMilliseconds.toDouble())
    val jitterMultiplier = 1.0 + ((randomUnit * 2.0) - 1.0) * jitterRatio
    return (bounded * jitterMultiplier)
      .coerceIn(1.0, maximum.inWholeMilliseconds.toDouble())
      .toLong()
      .milliseconds
  }
}

public fun interface SupervisionRandom {
  public fun nextUnit(): Double
}

public enum class EnvironmentSupervisionMode {
  ACTIVE,
  PASSIVE,
}

public enum class ShellSnapshotOrigin {
  ACTIVE_HTTP_FALLBACK,
  PASSIVE_HTTP,
}

public data class PublicationPermit(
  public val environmentId: String,
  public val owner: EnvironmentOwnerHandle,
  public val refreshEpoch: Long,
) {
  init {
    require(environmentId.isNotEmpty()) { "Publication environment cannot be empty." }
    require(refreshEpoch > 0L) { "Refresh epoch must be positive." }
  }
}

public sealed interface ConnectionPublication {
  public data class AuthorityChanged(
    public val environmentId: String,
    public val owner: EnvironmentOwnerHandle?,
    public val mode: EnvironmentSupervisionMode?,
    public val authorityEpoch: Long,
  ) : ConnectionPublication {
    init {
      require(environmentId.isNotEmpty()) { "Authority environment cannot be empty." }
      require(authorityEpoch > 0L) { "Authority epoch must be positive." }
      require((owner == null) == (mode == null)) {
        "Authority owner and supervision mode must be installed or released together."
      }
    }
  }

  public data class SnapshotLoaded(
    public val permit: PublicationPermit,
    public val snapshot: OrchestrationShellSnapshot,
    public val origin: ShellSnapshotOrigin,
  ) : ConnectionPublication

  public data class StreamReceived(
    public val permit: PublicationPermit,
    public val result: ShellStreamDecodeResult,
  ) : ConnectionPublication

  public data class ReachabilityChanged(
    public val permit: PublicationPermit,
    public val reachable: Boolean,
    public val reconnecting: Boolean,
    public val safeError: String?,
  ) : ConnectionPublication

  public data class Revoked(
    public val permit: PublicationPermit,
    public val safeError: String,
  ) : ConnectionPublication
}

public interface SupervisorSession {
  public val generation: Long

  public fun subscribeLongLivedShell(input: OrchestrationSubscribeShellInput): Flow<ShellStreamDecodeResult>

  public suspend fun close()
}

public fun interface SupervisorSessionFactory {
  public suspend fun start(
    environment: SavedEnvironment,
    credential: RedactedSecret,
  ): SupervisorSession
}

public fun interface EnvironmentSnapshotLoader {
  public suspend fun load(
    environment: SavedEnvironment,
    credential: RedactedSecret,
    timeout: Duration,
  ): OrchestrationShellSnapshot
}

public class ProtocolSupervisorSessionFactory(
  private val delegate: TicketedRpcSessionFactory = TicketedRpcSessionFactory(),
) : SupervisorSessionFactory {
  override suspend fun start(
    environment: SavedEnvironment,
    credential: RedactedSecret,
  ): SupervisorSession = ProtocolSupervisorSession(
    delegate.start(
      httpBaseUrl = environment.httpBaseUrl,
      webSocketBaseUrl = environment.webSocketBaseUrl,
      credential = credential,
    ),
  )
}

public class ProtocolEnvironmentSnapshotLoader(
  private val delegate: EnvironmentHttpClient = EnvironmentHttpClient(),
) : EnvironmentSnapshotLoader {
  override suspend fun load(
    environment: SavedEnvironment,
    credential: RedactedSecret,
    timeout: Duration,
  ): OrchestrationShellSnapshot = delegate.shellSnapshot(
    environment.httpBaseUrl,
    credential,
    timeout,
  )
}

private class ProtocolSupervisorSession(
  private val delegate: TicketedRpcSession,
) : SupervisorSession {
  override val generation: Long = delegate.generation

  override fun subscribeLongLivedShell(
    input: OrchestrationSubscribeShellInput,
  ): Flow<ShellStreamDecodeResult> = delegate.subscribeShell(
    input,
    RpcSubscriptionMode.LONG_LIVED_INTENT,
  )

  override suspend fun close() {
    delegate.close()
  }
}

/**
 * The only retry owner above WS-B's one-attempt HTTP and WebSocket objects. Configuration changes
 * publish replacement authority before stale jobs are cancelled, while new sockets start only
 * after the old jobs have released their exact sessions.
 */
public class EnvironmentConnectionSupervisor(
  parentScope: CoroutineScope,
  private val credentials: CredentialRepository,
  private val sessionFactory: SupervisorSessionFactory = ProtocolSupervisorSessionFactory(),
  private val snapshotLoader: EnvironmentSnapshotLoader = ProtocolEnvironmentSnapshotLoader(),
  private val settings: ConnectionSupervisionSettings = ConnectionSupervisionSettings(),
  private val random: SupervisionRandom = SupervisionRandom { Random.nextDouble() },
  private val publicationObserver: (ConnectionPublication) -> Unit = {},
) {
  private data class WorkerSpec(
    val environment: SavedEnvironment,
    val mode: EnvironmentSupervisionMode,
    val workerEpoch: Long,
    val reservedOwner: EnvironmentOwnerHandle,
  )

  private data class WorkerRecord(
    val spec: WorkerSpec,
    val job: Job,
    val currentOwner: EnvironmentOwnerHandle,
  )

  private val supervisorJob = SupervisorJob(parentScope.coroutineContext[Job])
  private val scope = CoroutineScope(parentScope.coroutineContext + supervisorJob)
  private val configurationMutex = Mutex()
  private val stateMutex = Mutex()
  private val publicationsChannel = Channel<ConnectionPublication>(Channel.UNLIMITED)
  private val identitySequence = AtomicLong(1L)
  private val refreshSequence = AtomicLong(1L)
  private var environments = linkedMapOf<String, SavedEnvironment>()
  private var activeEnvironmentId: String? = null
  private var foreground = true
  private var released = false
  private var workers = linkedMapOf<String, WorkerRecord>()

  public val publications: Flow<ConnectionPublication> = publicationsChannel.receiveAsFlow()

  public suspend fun replaceEnvironments(
    saved: List<SavedEnvironment>,
    activeEnvironmentId: String?,
  ) {
    configurationMutex.withLock {
      check(!released) { "The connection supervisor has been released." }
      val indexed = saved.associateByTo(linkedMapOf(), SavedEnvironment::environmentId)
      require(indexed.size == saved.size) { "Saved environment IDs must be unique." }
      require(activeEnvironmentId == null || indexed.containsKey(activeEnvironmentId)) {
        "The active environment must be saved."
      }
      stateMutex.withLock {
        environments = indexed
        this.activeEnvironmentId = activeEnvironmentId ?: indexed.keys.firstOrNull()
      }
      reconfigureWorkers()
    }
  }

  public suspend fun activate(environmentId: String) {
    configurationMutex.withLock {
      check(!released) { "The connection supervisor has been released." }
      stateMutex.withLock {
        require(environments.containsKey(environmentId)) { "Cannot activate an unknown environment." }
        activeEnvironmentId = environmentId
      }
      reconfigureWorkers()
    }
  }

  public suspend fun remove(environmentId: String) {
    configurationMutex.withLock {
      check(!released) { "The connection supervisor has been released." }
      stateMutex.withLock {
        environments.remove(environmentId)
        if (activeEnvironmentId == environmentId) activeEnvironmentId = environments.keys.firstOrNull()
      }
      reconfigureWorkers(extraReleasedEnvironmentIds = setOf(environmentId))
    }
  }

  public suspend fun setForeground(value: Boolean) {
    configurationMutex.withLock {
      check(!released) { "The connection supervisor has been released." }
      stateMutex.withLock { foreground = value }
      reconfigureWorkers()
    }
  }

  public suspend fun release() {
    configurationMutex.withLock {
      if (released) return
      stateMutex.withLock {
        released = true
        foreground = false
      }
      reconfigureWorkers()
      supervisorJob.cancelAndJoin()
      publicationsChannel.close()
    }
  }

  private suspend fun reconfigureWorkers(extraReleasedEnvironmentIds: Set<String> = emptySet()) {
    lateinit var oldWorkers: List<WorkerRecord>
    lateinit var newWorkers: List<WorkerRecord>
    lateinit var authorityChanges: List<ConnectionPublication.AuthorityChanged>
    stateMutex.withLock {
      oldWorkers = workers.values.toList()
      val oldEnvironmentIds = oldWorkers.mapTo(mutableSetOf()) { it.spec.environment.environmentId }
      val shouldRun = foreground && !released
      val prepared = linkedMapOf<String, WorkerRecord>()
      val changes = mutableListOf<ConnectionPublication.AuthorityChanged>()
      if (shouldRun) {
        for (environment in environments.values.sortedBy(SavedEnvironment::environmentId)) {
          val mode = if (environment.environmentId == activeEnvironmentId) {
            EnvironmentSupervisionMode.ACTIVE
          } else {
            EnvironmentSupervisionMode.PASSIVE
          }
          val owner = EnvironmentOwnerHandle(
            ownerEpoch = nextIdentity(),
            clientEpoch = nextIdentity(),
            sessionGeneration = null,
          )
          val spec = WorkerSpec(environment, mode, nextIdentity(), owner)
          val job = scope.launch(start = CoroutineStart.LAZY) { runWorker(spec) }
          prepared[environment.environmentId] = WorkerRecord(spec, job, owner)
          changes += ConnectionPublication.AuthorityChanged(
            environment.environmentId,
            owner,
            mode,
            nextIdentity(),
          )
        }
      }
      val releasedIds = oldEnvironmentIds + extraReleasedEnvironmentIds - prepared.keys
      for (environmentId in releasedIds.sorted()) {
        changes += ConnectionPublication.AuthorityChanged(
          environmentId,
          owner = null,
          mode = null,
          authorityEpoch = nextIdentity(),
        )
      }
      workers = prepared
      newWorkers = prepared.values.toList()
      authorityChanges = changes
    }

    // Stale work is fenced before its cancellation is requested.
    authorityChanges.forEach(::publish)
    oldWorkers.forEach { it.job.cancel() }
    oldWorkers.forEach { it.job.join() }
    newWorkers.forEach { it.job.start() }
  }

  private suspend fun runWorker(spec: WorkerSpec) {
    when (spec.mode) {
      EnvironmentSupervisionMode.ACTIVE -> runActive(spec)
      EnvironmentSupervisionMode.PASSIVE -> runPassive(spec)
    }
  }

  private suspend fun runActive(spec: WorkerSpec) {
    var failures = 0
    while (isCurrent(spec)) {
      currentCoroutineContext().ensureActive()
      val startingOwner = EnvironmentOwnerHandle(
        ownerEpoch = spec.reservedOwner.ownerEpoch,
        clientEpoch = nextIdentity(),
        sessionGeneration = null,
      )
      if (!installOwner(spec, startingOwner)) return
      val credential = try {
        directCredential(spec.environment.environmentId)
      } catch (error: Throwable) {
        rethrowIfCoroutineCancelled(error)
        revoke(spec, startingOwner, error)
        return
      }

      var session: SupervisorSession? = null
      var observedStreamValue = false
      try {
        session = sessionFactory.start(spec.environment, credential)
        val sessionOwner = EnvironmentOwnerHandle(
          ownerEpoch = spec.reservedOwner.ownerEpoch,
          clientEpoch = startingOwner.clientEpoch,
          sessionGeneration = session.generation,
        )
        if (!installOwner(spec, sessionOwner)) return
        val permit = permit(spec.environment.environmentId, sessionOwner)
        session.subscribeLongLivedShell(
          OrchestrationSubscribeShellInput(requestCompletionMarker = true),
        ).collect { result ->
          if (isCurrentOwner(spec, sessionOwner)) {
            observedStreamValue = true
            publish(ConnectionPublication.StreamReceived(permit, result))
          }
        }
        throw IllegalStateException("The long-lived shell subscription ended without closing its worker.")
      } catch (error: Throwable) {
        rethrowIfCoroutineCancelled(error)
        session?.let { failedSession ->
          withContext(NonCancellable) { failedSession.close() }
          session = null
        }
        if (isRevokedFailure(error)) {
          revoke(spec, currentOwner(spec) ?: startingOwner, error)
          return
        }
        failures = if (observedStreamValue) 1 else failures + 1
        val owner = currentOwner(spec) ?: startingOwner
        publishIfCurrent(
          spec,
          owner,
          ConnectionPublication.ReachabilityChanged(
            permit(spec.environment.environmentId, owner),
            reachable = false,
            reconnecting = failures < settings.backoff.maximumConsecutiveFailures,
            safeError = safeFailure(error),
          ),
        )
        if (!loadActiveFallback(spec, owner, credential)) return
        if (failures >= settings.backoff.maximumConsecutiveFailures) return
        delay(settings.backoff.delayFor(failures, random.nextUnit()))
      } finally {
        session?.let { activeSession ->
          withContext(NonCancellable) { activeSession.close() }
        }
      }
    }
  }

  private suspend fun loadActiveFallback(
    spec: WorkerSpec,
    owner: EnvironmentOwnerHandle,
    credential: RedactedSecret,
  ): Boolean = try {
    val refreshPermit = permit(spec.environment.environmentId, owner)
    val snapshot = snapshotLoader.load(
      spec.environment,
      credential,
      settings.activeFallbackTimeout,
    )
    publishIfCurrent(
      spec,
      owner,
      ConnectionPublication.SnapshotLoaded(
        refreshPermit,
        snapshot,
        ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
      ),
    )
    true
  } catch (error: Throwable) {
    rethrowIfCoroutineCancelled(error)
    if (isRevokedFailure(error)) {
      revoke(spec, owner, error)
      false
    } else {
      true
    }
  }

  private suspend fun runPassive(spec: WorkerSpec) {
    val owner = spec.reservedOwner
    while (isCurrentOwner(spec, owner)) {
      currentCoroutineContext().ensureActive()
      val credential = try {
        directCredential(spec.environment.environmentId)
      } catch (error: Throwable) {
        rethrowIfCoroutineCancelled(error)
        revoke(spec, owner, error)
        return
      }
      val refreshPermit = permit(spec.environment.environmentId, owner)
      try {
        val snapshot = snapshotLoader.load(spec.environment, credential, settings.passiveTimeout)
        publishIfCurrent(
          spec,
          owner,
          ConnectionPublication.SnapshotLoaded(
            refreshPermit,
            snapshot,
            ShellSnapshotOrigin.PASSIVE_HTTP,
          ),
        )
        publishIfCurrent(
          spec,
          owner,
          ConnectionPublication.ReachabilityChanged(
            permit(spec.environment.environmentId, owner),
            reachable = true,
            reconnecting = false,
            safeError = null,
          ),
        )
      } catch (error: Throwable) {
        rethrowIfCoroutineCancelled(error)
        if (isRevokedFailure(error)) {
          revoke(spec, owner, error)
          return
        }
        publishIfCurrent(
          spec,
          owner,
          ConnectionPublication.ReachabilityChanged(
            refreshPermit,
            reachable = false,
            reconnecting = false,
            safeError = safeFailure(error),
          ),
        )
      }
      delay(settings.passiveCadence)
    }
  }

  private suspend fun directCredential(environmentId: String): RedactedSecret {
    val credential = credentials.read(environmentId)
      ?: throw CredentialUnavailableFailure(CredentialUnavailableReason.MISSING_KEY_MATERIAL)
    if (credential.kind != CredentialKind.DIRECT_BEARER) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE)
    }
    return credential.secret
  }

  private suspend fun installOwner(spec: WorkerSpec, owner: EnvironmentOwnerHandle): Boolean {
    return stateMutex.withLock {
      val current = workers[spec.environment.environmentId]
      if (current?.spec?.workerEpoch != spec.workerEpoch) return@withLock false
      workers[spec.environment.environmentId] = current.copy(currentOwner = owner)
      // Keep state installation and publication ordered against configuration replacement.
      publish(
        ConnectionPublication.AuthorityChanged(
          spec.environment.environmentId,
          owner,
          spec.mode,
          nextIdentity(),
        ),
      )
      true
    }
  }

  private suspend fun currentOwner(spec: WorkerSpec): EnvironmentOwnerHandle? = stateMutex.withLock {
    workers[spec.environment.environmentId]
      ?.takeIf { it.spec.workerEpoch == spec.workerEpoch }
      ?.currentOwner
  }

  private suspend fun isCurrent(spec: WorkerSpec): Boolean = stateMutex.withLock {
    val current = workers[spec.environment.environmentId]
    !released && foreground && current?.spec?.workerEpoch == spec.workerEpoch
  }

  private suspend fun isCurrentOwner(
    spec: WorkerSpec,
    owner: EnvironmentOwnerHandle,
  ): Boolean = stateMutex.withLock {
    val current = workers[spec.environment.environmentId]
    !released && foreground && current?.spec?.workerEpoch == spec.workerEpoch &&
      current.currentOwner == owner
  }

  private suspend fun publishIfCurrent(
    spec: WorkerSpec,
    owner: EnvironmentOwnerHandle,
    publication: ConnectionPublication,
  ) {
    if (isCurrentOwner(spec, owner)) publish(publication)
  }

  private suspend fun revoke(spec: WorkerSpec, owner: EnvironmentOwnerHandle, error: Throwable) {
    publishIfCurrent(
      spec,
      owner,
      ConnectionPublication.Revoked(
        permit(spec.environment.environmentId, owner),
        safeFailure(error),
      ),
    )
  }

  private fun permit(environmentId: String, owner: EnvironmentOwnerHandle): PublicationPermit =
    PublicationPermit(environmentId, owner, nextRefresh())

  private fun publish(publication: ConnectionPublication) {
    check(publicationsChannel.trySend(publication).isSuccess) {
      "Connection publication channel is closed."
    }
    publicationObserver(publication)
  }

  private fun nextIdentity(): Long = nextPositive(identitySequence, "Supervisor identity")

  private fun nextRefresh(): Long = nextPositive(refreshSequence, "Refresh epoch")

  private fun nextPositive(sequence: AtomicLong, label: String): Long {
    val value = sequence.getAndIncrement()
    check(value > 0L) { "$label space is exhausted." }
    return value
  }

  private fun isRevokedFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any {
      it is AuthorizationRejectedFailure || it is CredentialUnavailableFailure
    }

  private suspend fun rethrowIfCoroutineCancelled(error: Throwable) {
    if (error !is CancellationException) return
    currentCoroutineContext().ensureActive()
    if (error !is ProtocolFailure) throw error
  }

  private fun safeFailure(error: Throwable): String = when (error) {
    is ProtocolFailure -> error.safeMessage
    is CredentialUnavailableFailure -> error.message.orEmpty()
    else -> "The environment connection attempt failed."
  }
}
