package com.t3tools.t3code.compose.core.testing.integration

import com.t3tools.t3code.compose.core.data.CanonicalSnapshotRefresher
import com.t3tools.t3code.compose.core.data.ConnectionPublication
import com.t3tools.t3code.compose.core.data.ConnectionSupervisionSettings
import com.t3tools.t3code.compose.core.data.CredentialRepository
import com.t3tools.t3code.compose.core.data.EnvironmentConnectionSupervisor
import com.t3tools.t3code.compose.core.data.EnvironmentCredential
import com.t3tools.t3code.compose.core.data.EnvironmentReachability
import com.t3tools.t3code.compose.core.data.EnvironmentSnapshotLoader
import com.t3tools.t3code.compose.core.data.EnvironmentSupervisionMode
import com.t3tools.t3code.compose.core.data.ProtocolEnvironmentSnapshotLoader
import com.t3tools.t3code.compose.core.data.ProtocolSupervisorSessionFactory
import com.t3tools.t3code.compose.core.data.PublicationPermit
import com.t3tools.t3code.compose.core.data.ReconnectBackoff
import com.t3tools.t3code.compose.core.data.ReconciliationDisposition
import com.t3tools.t3code.compose.core.data.RouteResolution
import com.t3tools.t3code.compose.core.data.SavedEnvironment
import com.t3tools.t3code.compose.core.data.ScopedEntityKind
import com.t3tools.t3code.compose.core.data.ShellDataFreshness
import com.t3tools.t3code.compose.core.data.ShellSourceState
import com.t3tools.t3code.compose.core.data.ShellStateReconciler
import com.t3tools.t3code.compose.core.data.ShellSnapshotOrigin
import com.t3tools.t3code.compose.core.data.SupervisionRandom
import com.t3tools.t3code.compose.core.data.SupervisorSession
import com.t3tools.t3code.compose.core.data.SupervisorSessionFactory
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.OrchestrationSubscribeShellInput
import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import com.t3tools.t3code.compose.core.protocol.ShellStreamDecodeResult
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class NativeAndroidRecoveryIntegrationTest {
  @Test
  public fun restoresRecoversTransfersRevokesAndRemovesWithoutCrossEnvironmentLeakage(): Unit =
    runBlocking {
      DisposableT3IntegrationHarness.start().use { harness ->
        val firstOriginal = harness.first
        val secondOriginal = harness.second
        val firstEnvironment = SavedEnvironment.from(firstOriginal.pairing)
        val secondEnvironment = SavedEnvironment.from(secondOriginal.pairing)
        val firstLastKnown = integrationStage("capture first last-known control snapshot") {
          firstOriginal.controlledShellSnapshot()
        }
        val secondLastKnown = integrationStage("capture second last-known control snapshot") {
          secondOriginal.controlledShellSnapshot()
        }
        val credentials = MemoryCredentialRepository(
          firstEnvironment.environmentId to EnvironmentCredential.directBearer(
            firstOriginal.pairing.accessCredential,
          ),
          secondEnvironment.environmentId to EnvironmentCredential.directBearer(
            secondOriginal.pairing.accessCredential,
          ),
        )

        val firstPid = firstOriginal.pid
        val firstPort = firstOriginal.port
        harness.stopFirst()
        assertFalse(firstOriginal.isAlive)

        coroutineScope {
          val trackingSessions = TrackingSessionFactory()
          val snapshotLoader = GatedSnapshotLoader()
          val reconciler = ShellStateReconciler(
            parentScope = this,
            canonicalRefresher = CanonicalSnapshotRefresher { request ->
              val environment = listOf(firstEnvironment, secondEnvironment).single {
                it.environmentId == request.permit.environmentId
              }
              val credential = requireNotNull(credentials.read(environment.environmentId)).secret
              ProtocolEnvironmentSnapshotLoader().load(environment, credential, request.timeout)
            },
          )
          reconciler.registerEnvironment(firstEnvironment, firstLastKnown)
          reconciler.registerEnvironment(secondEnvironment, secondLastKnown)

          val restored = reconciler.state.value
          assertEquals(
            ShellDataFreshness.LAST_KNOWN,
            restored.environments.getValue(firstEnvironment.environmentId).freshness,
          )
          assertEquals(
            ShellDataFreshness.LAST_KNOWN,
            restored.environments.getValue(secondEnvironment.environmentId).freshness,
          )
          assertEquals(
            RouteResolution.Ambiguous::class,
            restored.resolveRaw(ScopedEntityKind.PROJECT, COLLIDING_PROJECT_ID)::class,
          )

          val supervisor = EnvironmentConnectionSupervisor(
            parentScope = this,
            credentials = credentials,
            sessionFactory = trackingSessions,
            snapshotLoader = snapshotLoader,
            settings = ConnectionSupervisionSettings(
              passiveCadence = 250.milliseconds,
              passiveTimeout = 5.seconds,
              activeFallbackTimeout = 5.seconds,
              backoff = ReconnectBackoff(
                initial = 100.milliseconds,
                maximum = 1.seconds,
                multiplier = 1.5,
                jitterRatio = 0.0,
                maximumConsecutiveFailures = 30,
              ),
            ),
            random = SupervisionRandom { 0.5 },
          )
          val publications =
            CopyOnWriteArrayList<Pair<ConnectionPublication, ReconciliationDisposition>>()
          val collector = launch {
            supervisor.publications.collect { publication ->
              publications += publication to reconciler.accept(publication)
            }
          }

          try {
            supervisor.replaceEnvironments(
              listOf(firstEnvironment, secondEnvironment),
              activeEnvironmentId = firstEnvironment.environmentId,
            )

            reconciler.awaitState { state ->
              val first = state.environments[firstEnvironment.environmentId]
              val second = state.environments[secondEnvironment.environmentId]
              first?.reachability == EnvironmentReachability.UNREACHABLE &&
                first.source == ShellSourceState.RECONNECTING &&
                first.projects.isNotEmpty() &&
                second?.reachability == EnvironmentReachability.REACHABLE &&
                second.source == ShellSourceState.PASSIVE
            }

            val firstRestarted = integrationStage("restart offline active environment") {
              harness.restartFirst()
            }
            assertNotEquals(firstPid, firstRestarted.pid)
            assertEquals(firstPort, firstRestarted.port)
            assertEquals(firstEnvironment.environmentId, firstRestarted.pairing.descriptor.environmentId)

            reconciler.awaitState { state ->
              val first = state.environments[firstEnvironment.environmentId]
              first?.reachability == EnvironmentReachability.REACHABLE &&
                first.source == ShellSourceState.LIVE &&
                first.owner?.sessionGeneration != null
            }
            trackingSessions.awaitMetrics { it.openSessions == 1 && it.activeCollectors == 1 }
            assertEquals(1, trackingSessions.metrics.value.maximumOpenSessions)
            assertEquals(1, trackingSessions.metrics.value.maximumActiveCollectors)

            val oldSecondOwner = assertNotNullValue(
              reconciler.state.value.environments.getValue(secondEnvironment.environmentId).owner,
            )
            supervisor.activate(secondEnvironment.environmentId)
            reconciler.awaitState { state ->
              val first = state.environments[firstEnvironment.environmentId]
              val second = state.environments[secondEnvironment.environmentId]
              first?.source == ShellSourceState.PASSIVE &&
                second?.source == ShellSourceState.LIVE &&
                second.owner?.sessionGeneration != null
            }
            trackingSessions.awaitMetrics { it.openSessions == 1 && it.activeCollectors == 1 }
            assertEquals(1, trackingSessions.metrics.value.maximumOpenSessions)
            assertEquals(1, trackingSessions.metrics.value.maximumActiveCollectors)

            val secondBeforeLate = reconciler.state.value.environments
              .getValue(secondEnvironment.environmentId)
            val delayedResult = reconciler.accept(
              ConnectionPublication.SnapshotLoaded(
                permit = PublicationPermit(
                  secondEnvironment.environmentId,
                  oldSecondOwner,
                  Long.MAX_VALUE,
                ),
                snapshot = syntheticSnapshot(9_999, "late-owner-project"),
                origin = ShellSnapshotOrigin.PASSIVE_HTTP,
              ),
            )
            assertEquals(ReconciliationDisposition.IGNORED_STALE, delayedResult)
            val secondAfterLate = reconciler.state.value.environments
              .getValue(secondEnvironment.environmentId)
            assertEquals(secondBeforeLate.snapshotSequence, secondAfterLate.snapshotSequence)
            assertFalse(secondAfterLate.projects.values.any { it.wireId == "late-owner-project" })

            val fallbackGate = snapshotLoader.gateNext(secondEnvironment.environmentId)
            trackingSessions.failActive(IOException("Synthetic active stream interruption."))
            withTimeout(30.seconds) { fallbackGate.started.await() }
            integrationStage("dispatch and drain active HTTP fallback mutation") {
              harness.second.dispatch(
                projectCreateCommand(
                  commandId = "environment-b-fallback-project-create",
                  projectId = "project-created-during-fallback",
                  workspaceRoot = harness.second.baseDirectory.resolve("fallback-workspace").toString(),
                ),
              )
              harness.second.drainWorkers()
            }
            fallbackGate.release.complete(Unit)
            reconciler.awaitState { state ->
              val second = state.environments[secondEnvironment.environmentId]
              second?.source == ShellSourceState.RECONNECTING &&
                second.projects.values.any { it.wireId == "project-created-during-fallback" }
            }
            reconciler.awaitState { state ->
              state.environments[secondEnvironment.environmentId]?.source == ShellSourceState.LIVE
            }

            val retainedFirstRows = reconciler.state.value.environments
              .getValue(firstEnvironment.environmentId).projects.keys
            val passiveFirst = harness.first
            val passiveFirstPid = passiveFirst.pid
            harness.stopFirst()
            assertFalse(passiveFirst.isAlive)
            reconciler.awaitState { state ->
              val first = state.environments[firstEnvironment.environmentId]
              first?.reachability == EnvironmentReachability.UNREACHABLE &&
                first.projects.keys == retainedFirstRows
            }
            val activeSecondDuringOutage = reconciler.state.value.environments
              .getValue(secondEnvironment.environmentId)
            assertEquals(EnvironmentReachability.REACHABLE, activeSecondDuringOutage.reachability)
            assertEquals(ShellSourceState.LIVE, activeSecondDuringOutage.source)

            val firstAfterPassiveOutage = integrationStage("restart passive environment") {
              harness.restartFirst()
            }
            assertNotEquals(passiveFirstPid, firstAfterPassiveOutage.pid)
            assertEquals(firstPort, firstAfterPassiveOutage.port)
            assertTrue(
              integrationStage("load restarted passive environment control snapshot") {
                firstAfterPassiveOutage.controlledShellSnapshot()
              }.projects.isNotEmpty(),
            )
            try {
              reconciler.awaitState(
                "passive environment recovery after server restart",
                12.seconds,
              ) { state ->
                val first = state.environments[firstEnvironment.environmentId]
                first?.reachability == EnvironmentReachability.REACHABLE &&
                  first.source == ShellSourceState.PASSIVE
              }
            } catch (failure: AssertionError) {
              val recent = publications.takeLast(12).map { (publication, disposition) ->
                "${publication::class.simpleName}:$disposition"
              }
              throw AssertionError(
                "${failure.message}; loads=${snapshotLoader.metrics.value}; recent=$recent",
                failure,
              )
            }

            assertEquals(
              1,
              integrationStage("revoke active mobile session") {
                harness.second.revokeMobileSessions()
              },
            )
            val secondPidBeforeRevocationRestart = harness.second.pid
            harness.stopSecond()
            val secondAfterRevocationRestart = integrationStage("restart revoked active environment") {
              harness.restartSecond()
            }
            assertNotEquals(secondPidBeforeRevocationRestart, secondAfterRevocationRestart.pid)
            reconciler.awaitState { state ->
              state.environments[secondEnvironment.environmentId]?.reachability ==
                EnvironmentReachability.REVOKED
            }
            val stateAfterRevocation = reconciler.state.value
            assertEquals(
              EnvironmentReachability.REACHABLE,
              stateAfterRevocation.environments.getValue(firstEnvironment.environmentId).reachability,
            )
            assertNull(
              stateAfterRevocation.environments.getValue(secondEnvironment.environmentId).owner,
            )
            assertEquals(
              1,
              publications.map(Pair<ConnectionPublication, ReconciliationDisposition>::first)
                .filterIsInstance<ConnectionPublication.Revoked>()
                .count { it.permit.environmentId == secondEnvironment.environmentId },
            )

            supervisor.remove(secondEnvironment.environmentId)
            reconciler.removeEnvironment(secondEnvironment.environmentId)
            credentials.delete(secondEnvironment.environmentId)
            reconciler.awaitState { state ->
              state.environments.keys == setOf(firstEnvironment.environmentId) &&
                state.environments.getValue(firstEnvironment.environmentId).source == ShellSourceState.LIVE
            }
            trackingSessions.awaitMetrics { it.openSessions == 1 && it.activeCollectors == 1 }
            assertNull(credentials.read(secondEnvironment.environmentId))
            assertNotNull(credentials.read(firstEnvironment.environmentId))
            assertEquals(
              RouteResolution.Resolved::class,
              reconciler.state.value.resolveRaw(
                ScopedEntityKind.PROJECT,
                COLLIDING_PROJECT_ID,
              )::class,
            )
            assertTrue(trackingSessions.metrics.value.maximumOpenSessions <= 1)
            assertTrue(trackingSessions.metrics.value.maximumActiveCollectors <= 1)
            assertFalse(publications.any { (publication, disposition) ->
              publication is ConnectionPublication.SnapshotLoaded &&
                publication.permit.environmentId == secondEnvironment.environmentId &&
                disposition == ReconciliationDisposition.APPLIED &&
                publication.permit.owner == oldSecondOwner &&
                publication.snapshot.snapshotSequence == 9_999L
            })
          } finally {
            supervisor.release()
            collector.cancelAndJoin()
            reconciler.release()
          }
        }
      }
    }

  private class MemoryCredentialRepository(
    vararg entries: Pair<String, EnvironmentCredential>,
  ) : CredentialRepository {
    private val values = ConcurrentHashMap(entries.toMap())

    override suspend fun read(environmentId: String): EnvironmentCredential? = values[environmentId]

    override suspend fun write(environmentId: String, credential: EnvironmentCredential) {
      values[environmentId] = credential
    }

    override suspend fun delete(environmentId: String) {
      values.remove(environmentId)
    }
  }

  private data class SessionMetrics(
    val startAttempts: Int = 0,
    val openSessions: Int = 0,
    val activeCollectors: Int = 0,
    val maximumOpenSessions: Int = 0,
    val maximumActiveCollectors: Int = 0,
  )

  private class TrackingSessionFactory(
    private val delegate: SupervisorSessionFactory = ProtocolSupervisorSessionFactory(),
  ) : SupervisorSessionFactory {
    val metrics = MutableStateFlow(SessionMetrics())
    private val activeFailure = Channel<Throwable>(Channel.UNLIMITED)

    override suspend fun start(
      environment: SavedEnvironment,
      credential: RedactedSecret,
    ): SupervisorSession {
      metrics.update { it.copy(startAttempts = it.startAttempts + 1) }
      val session = delegate.start(environment, credential)
      metrics.update { current ->
        val open = current.openSessions + 1
        current.copy(openSessions = open, maximumOpenSessions = maxOf(open, current.maximumOpenSessions))
      }
      return TrackingSession(session, metrics, activeFailure)
    }

    fun failActive(error: Throwable) {
      check(activeFailure.trySend(error).isSuccess) { "Active failure signal was not accepted." }
    }

    suspend fun awaitMetrics(predicate: (SessionMetrics) -> Boolean): SessionMetrics =
      withTimeout(30.seconds) { metrics.first(predicate) }
  }

  private class TrackingSession(
    private val delegate: SupervisorSession,
    private val metrics: MutableStateFlow<SessionMetrics>,
    private val activeFailure: Channel<Throwable>,
  ) : SupervisorSession {
    private val closed = AtomicBoolean()

    override val generation: Long get() = delegate.generation

    override fun subscribeLongLivedShell(
      input: OrchestrationSubscribeShellInput,
    ): Flow<ShellStreamDecodeResult> = channelFlow {
      metrics.update { current ->
        val active = current.activeCollectors + 1
        current.copy(
          activeCollectors = active,
          maximumActiveCollectors = maxOf(active, current.maximumActiveCollectors),
        )
      }
      try {
        coroutineScope {
          val delegateCollector = launch {
            delegate.subscribeLongLivedShell(input).collect { send(it) }
          }
          val failureCollector = launch {
            throw activeFailure.receive()
          }
          delegateCollector.join()
          failureCollector.cancelAndJoin()
        }
      } finally {
        metrics.update { it.copy(activeCollectors = it.activeCollectors - 1) }
      }
    }

    override suspend fun close() {
      if (!closed.compareAndSet(false, true)) return
      delegate.close()
      metrics.update { it.copy(openSessions = it.openSessions - 1) }
    }
  }

  private data class SnapshotGate(
    val started: CompletableDeferred<Unit> = CompletableDeferred(),
    val release: CompletableDeferred<Unit> = CompletableDeferred(),
  )

  private data class SnapshotLoadMetrics(
    val attempts: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val inFlight: Int = 0,
    val lastFailureType: String? = null,
  )

  private class GatedSnapshotLoader(
    private val delegate: EnvironmentSnapshotLoader = ProtocolEnvironmentSnapshotLoader(),
  ) : EnvironmentSnapshotLoader {
    private val gates = ConcurrentHashMap<String, SnapshotGate>()
    val metrics = MutableStateFlow<Map<String, SnapshotLoadMetrics>>(emptyMap())

    fun gateNext(environmentId: String): SnapshotGate = SnapshotGate().also { gate ->
      check(gates.putIfAbsent(environmentId, gate) == null) { "A snapshot gate is already armed." }
    }

    override suspend fun load(
      environment: SavedEnvironment,
      credential: RedactedSecret,
      timeout: Duration,
    ): OrchestrationShellSnapshot {
      updateMetrics(environment.environmentId) { current ->
        current.copy(attempts = current.attempts + 1, inFlight = current.inFlight + 1)
      }
      gates.remove(environment.environmentId)?.let { gate ->
        gate.started.complete(Unit)
        gate.release.await()
      }
      return try {
        delegate.load(environment, credential, timeout).also {
          updateMetrics(environment.environmentId) { current ->
            current.copy(successes = current.successes + 1)
          }
        }
      } catch (failure: Throwable) {
        updateMetrics(environment.environmentId) { current ->
          current.copy(
            failures = current.failures + 1,
            lastFailureType = failure::class.simpleName,
          )
        }
        throw failure
      } finally {
        updateMetrics(environment.environmentId) { current ->
          current.copy(inFlight = current.inFlight - 1)
        }
      }
    }

    private fun updateMetrics(
      environmentId: String,
      update: (SnapshotLoadMetrics) -> SnapshotLoadMetrics,
    ) {
      metrics.update { current ->
        current + (environmentId to update(current[environmentId] ?: SnapshotLoadMetrics()))
      }
    }
  }

  private suspend fun ShellStateReconciler.awaitState(
    label: String = "reconciliation state",
    timeout: Duration = 30.seconds,
    predicate: (com.t3tools.t3code.compose.core.data.ShellAggregateState) -> Boolean,
  ) = try {
    withTimeout(timeout) { state.first(predicate) }
  } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
    val summary = state.value.environments.mapValues { (_, environment) ->
      listOf(
        environment.reachability,
        environment.source,
        environment.freshness,
        environment.snapshotSequence,
        environment.owner?.sessionGeneration,
      ).joinToString(separator = "/")
    }
    throw AssertionError("Timed out waiting for $label: $summary", failure)
  }

  private fun projectCreateCommand(
    commandId: String,
    projectId: String,
    workspaceRoot: String,
  ) = buildJsonObject {
    put("type", "project.create")
    put("commandId", commandId)
    put("projectId", projectId)
    put("title", "Fallback project")
    put("workspaceRoot", workspaceRoot)
    put("createWorkspaceRootIfMissing", true)
    put("createdAt", "2026-08-08T00:00:01.000Z")
  }

  private fun syntheticSnapshot(sequence: Long, projectId: String): OrchestrationShellSnapshot =
    OrchestrationShellSnapshot(
      snapshotSequence = sequence,
      projects = listOf(
        buildJsonObject {
          put("id", projectId)
          put("title", "Synthetic $projectId")
        },
      ),
      threads = emptyList(),
      updatedAt = "2026-08-08T00:00:01.000Z",
    )

  private fun <T : Any> assertNotNullValue(value: T?): T {
    assertNotNull(value)
    return requireNotNull(value)
  }

  private suspend fun <T> integrationStage(label: String, block: suspend () -> T): T = try {
    block()
  } catch (failure: Throwable) {
    throw AssertionError("Recovery integration stage failed: $label.", failure)
  }
}
