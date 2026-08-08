package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentCapabilities
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatform
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformArch
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformOs
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellStreamItem
import com.t3tools.t3code.compose.core.protocol.OrchestrationSubscribeShellInput
import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import com.t3tools.t3code.compose.core.protocol.ShellStreamDecodeResult
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class EnvironmentConnectionSupervisorTest {
  @Test
  public fun exactlyOneActiveSocketAndPassiveHttpTransferAtomicallyOnActivation() {
    runTest {
      val log = mutableListOf<String>()
      val credentials = FakeCredentials("environment-a", "environment-b")
      val sessions = FakeSessionFactory(log)
      val snapshots = FakeSnapshotLoader()
      val publications = mutableListOf<ConnectionPublication>()
      val supervisor = supervisor(
        credentials,
        sessions,
        snapshots,
        log,
        publications,
      )

      supervisor.replaceEnvironments(
        listOf(environment("environment-a"), environment("environment-b")),
        activeEnvironmentId = "environment-a",
      )
      runCurrent()

      assertEquals(listOf("environment-a"), sessions.startedEnvironments)
      assertEquals(listOf("environment-b"), snapshots.loadedEnvironments)
      assertEquals(listOf(6.seconds), snapshots.timeouts)
      assertEquals(1, sessions.maximumOpenSessions)
      assertEquals(1, sessions.sessions.single().activeCollectors)

      advanceTimeBy(19_999)
      runCurrent()
      assertEquals(1, snapshots.loadedEnvironments.size)
      advanceTimeBy(1)
      runCurrent()
      assertEquals(listOf("environment-b", "environment-b"), snapshots.loadedEnvironments)

      log.clear()
      supervisor.activate("environment-b")
      runCurrent()

      assertEquals(listOf("environment-a", "environment-b"), sessions.startedEnvironments)
      assertEquals(
        listOf("environment-b", "environment-b", "environment-a"),
        snapshots.loadedEnvironments,
      )
      assertEquals(1, sessions.maximumOpenSessions)
      assertEquals(1, sessions.sessions.first().closeCalls)
      val newAuthorityIndex = log.indexOfFirst { it == "authority:environment-b:ACTIVE" }
      val oldCloseIndex = log.indexOfFirst { it == "session-close:environment-a" }
      assertTrue(newAuthorityIndex >= 0)
      assertTrue(oldCloseIndex > newAuthorityIndex)

      val activeAuthorities = publications.filterIsInstance<ConnectionPublication.AuthorityChanged>()
        .filter { it.environmentId == "environment-b" && it.mode == EnvironmentSupervisionMode.ACTIVE }
      assertTrue(activeAuthorities.any { it.owner?.sessionGeneration != null })
      supervisor.release()
    }
  }

  @Test
  public fun reconnectIsSupervisorOwnedBoundedAndRecreatesOnlyLongLivedIntent() {
    runTest {
      val log = mutableListOf<String>()
      val sessions = FakeSessionFactory(log)
      val snapshots = FakeSnapshotLoader()
      val publications = mutableListOf<ConnectionPublication>()
      val supervisor = supervisor(
        FakeCredentials("environment-a"),
        sessions,
        snapshots,
        log,
        publications,
        settings = settings(maximumFailures = 3),
      )
      supervisor.replaceEnvironments(listOf(environment("environment-a")), "environment-a")
      runCurrent()
      val first = sessions.sessions.single()
      first.fail(IOException("socket closed"))
      runCurrent()

      assertEquals(1, sessions.startCalls)
      assertEquals(1, first.closeCalls)
      assertEquals(listOf("environment-a"), snapshots.loadedEnvironments)
      assertTrue(
        publications.filterIsInstance<ConnectionPublication.SnapshotLoaded>()
          .any { it.origin == ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK },
      )

      advanceTimeBy(999)
      runCurrent()
      assertEquals(1, sessions.startCalls)
      advanceTimeBy(1)
      runCurrent()
      assertEquals(2, sessions.startCalls)
      assertEquals(listOf(1L, 2L), sessions.sessions.map(FakeSession::generation))
      assertEquals(listOf(1L, 2L), sessions.sessions.map(FakeSession::subscriptionRequestIdentity))
      assertTrue(sessions.sessions.all { it.subscriptionModes == listOf("long-lived") })
      assertEquals(1, sessions.maximumOpenSessions)

      val sessionPermits = publications.filterIsInstance<ConnectionPublication.AuthorityChanged>()
        .mapNotNull(ConnectionPublication.AuthorityChanged::owner)
        .filter { it.sessionGeneration != null }
      assertEquals(listOf(1L, 2L), sessionPermits.mapNotNull { it.sessionGeneration }.distinct())
      supervisor.release()
    }
  }

  @Test
  public fun startFailuresRetryOnlyAfterInjectedBackoffAndStopAtConfiguredBound() {
    runTest {
      val sessions = FakeSessionFactory(mutableListOf()).apply { startFailuresRemaining = 2 }
      val supervisor = supervisor(
        FakeCredentials("environment-a"),
        sessions,
        FakeSnapshotLoader(),
        mutableListOf(),
        mutableListOf(),
        settings = settings(maximumFailures = 2),
      )
      supervisor.replaceEnvironments(listOf(environment("environment-a")), "environment-a")
      runCurrent()
      assertEquals(1, sessions.startCalls)

      advanceTimeBy(999)
      runCurrent()
      assertEquals(1, sessions.startCalls)
      advanceTimeBy(1)
      runCurrent()
      assertEquals(2, sessions.startCalls)

      advanceTimeBy(60_000)
      runCurrent()
      assertEquals(2, sessions.startCalls)
      supervisor.release()
    }
  }

  @Test
  public fun passiveFailureRetainsActiveSessionAndRevokesOnlyAffectedEnvironment() {
    runTest {
      val sessions = FakeSessionFactory(mutableListOf())
      val snapshots = FakeSnapshotLoader().apply {
        behavior = { environment, _ ->
          if (environment.environmentId == "environment-b") {
            throw CredentialUnavailableFailure(CredentialUnavailableReason.MISSING_KEY_MATERIAL)
          }
          snapshot(1)
        }
      }
      val publications = mutableListOf<ConnectionPublication>()
      val supervisor = supervisor(
        FakeCredentials("environment-a", "environment-b"),
        sessions,
        snapshots,
        mutableListOf(),
        publications,
      )
      supervisor.replaceEnvironments(
        listOf(environment("environment-a"), environment("environment-b")),
        "environment-a",
      )
      runCurrent()

      assertEquals(0, sessions.sessions.single().closeCalls)
      val revoked = publications.filterIsInstance<ConnectionPublication.Revoked>().single()
      assertEquals("environment-b", revoked.permit.environmentId)
      assertEquals(null, revoked.permit.owner.sessionGeneration)
      assertFalse(publications.filterIsInstance<ConnectionPublication.Revoked>().any {
        it.permit.environmentId == "environment-a"
      })
      supervisor.release()
    }
  }

  @Test
  public fun lifecycleReleaseAndRemovalCancelExactWorkersAndCollectors() {
    runTest {
      val sessions = FakeSessionFactory(mutableListOf())
      val snapshots = FakeSnapshotLoader()
      val supervisor = supervisor(
        FakeCredentials("environment-a", "environment-b"),
        sessions,
        snapshots,
        mutableListOf(),
        mutableListOf(),
      )
      supervisor.replaceEnvironments(
        listOf(environment("environment-a"), environment("environment-b")),
        "environment-a",
      )
      runCurrent()
      val first = sessions.sessions.single()

      supervisor.setForeground(false)
      runCurrent()
      assertEquals(1, first.closeCalls)
      assertEquals(0, first.activeCollectors)
      val passiveCalls = snapshots.loadedEnvironments.size
      advanceTimeBy(60_000)
      runCurrent()
      assertEquals(passiveCalls, snapshots.loadedEnvironments.size)

      supervisor.setForeground(true)
      runCurrent()
      assertEquals(2, sessions.startCalls)
      supervisor.remove("environment-a")
      runCurrent()
      assertEquals(3, sessions.startCalls)
      assertEquals("environment-b", sessions.startedEnvironments.last())
      assertTrue(sessions.sessions.dropLast(1).all { it.closeCalls == 1 })

      supervisor.release()
      assertEquals(1, sessions.sessions.last().closeCalls)
      assertTrue(sessions.sessions.all { it.activeCollectors == 0 })
    }
  }

  @Test
  public fun delayedPassiveResultCannotPublishAfterOwnershipTransferEvenWhenCancellationIsIgnored() {
    runTest {
      val delayed = CompletableDeferred<OrchestrationShellSnapshot>()
      val snapshots = FakeSnapshotLoader().apply {
        behavior = { environment, _ ->
          if (environment.environmentId == "environment-b") {
            withContext(NonCancellable) { delayed.await() }
          } else {
            snapshot(1)
          }
        }
      }
      val publications = mutableListOf<ConnectionPublication>()
      val supervisor = supervisor(
        FakeCredentials("environment-a", "environment-b"),
        FakeSessionFactory(mutableListOf()),
        snapshots,
        mutableListOf(),
        publications,
      )
      supervisor.replaceEnvironments(
        listOf(environment("environment-a"), environment("environment-b")),
        "environment-a",
      )
      runCurrent()
      val activation = backgroundScope.launch { supervisor.activate("environment-b") }
      runCurrent()
      delayed.complete(snapshot(99))
      runCurrent()
      activation.join()
      runCurrent()

      assertFalse(
        publications.filterIsInstance<ConnectionPublication.SnapshotLoaded>().any {
          it.permit.environmentId == "environment-b" &&
            it.origin == ShellSnapshotOrigin.PASSIVE_HTTP &&
            it.snapshot.snapshotSequence == 99L
        },
      )
      supervisor.release()
    }
  }

  @Test
  public fun backoffIsDeterministicBoundedAndJittered() {
    val backoff = ReconnectBackoff(
      initial = 1.seconds,
      maximum = 8.seconds,
      multiplier = 2.0,
      jitterRatio = 0.25,
      maximumConsecutiveFailures = 5,
    )

    assertEquals(750.milliseconds, backoff.delayFor(1, 0.0))
    assertEquals(1.seconds, backoff.delayFor(1, 0.5))
    assertEquals(2500.milliseconds, backoff.delayFor(2, 1.0))
    assertEquals(8.seconds, backoff.delayFor(10, 1.0))
  }

  private fun TestScope.supervisor(
    credentials: CredentialRepository,
    sessions: FakeSessionFactory,
    snapshots: FakeSnapshotLoader,
    log: MutableList<String>,
    publications: MutableList<ConnectionPublication>,
    settings: ConnectionSupervisionSettings = settings(),
  ): EnvironmentConnectionSupervisor = EnvironmentConnectionSupervisor(
    parentScope = backgroundScope,
    credentials = credentials,
    sessionFactory = sessions,
    snapshotLoader = snapshots,
    settings = settings,
    random = SupervisionRandom { 0.5 },
    publicationObserver = { publication ->
      publications += publication
      if (publication is ConnectionPublication.AuthorityChanged) {
        log += "authority:${publication.environmentId}:${publication.mode}"
      }
    },
  )

  private fun settings(maximumFailures: Int = 4): ConnectionSupervisionSettings =
    ConnectionSupervisionSettings(
      passiveCadence = 20.seconds,
      passiveTimeout = 6.seconds,
      activeFallbackTimeout = 6.seconds,
      backoff = ReconnectBackoff(
        initial = 1.seconds,
        maximum = 8.seconds,
        multiplier = 2.0,
        jitterRatio = 0.0,
        maximumConsecutiveFailures = maximumFailures,
      ),
    )

  private fun environment(id: String): SavedEnvironment = SavedEnvironment(
    environmentId = id,
    label = "Environment $id",
    httpBaseUrl = "https://$id.test/",
    webSocketBaseUrl = "wss://$id.test/",
    descriptor = ExecutionEnvironmentDescriptor(
      environmentId = id,
      label = "Environment $id",
      platform = ExecutionEnvironmentPlatform(
        os = ExecutionEnvironmentPlatformOs.LINUX,
        arch = ExecutionEnvironmentPlatformArch.X64,
      ),
      serverVersion = "test",
      capabilities = ExecutionEnvironmentCapabilities(repositoryIdentity = true),
    ),
  )

  private class FakeCredentials(vararg environmentIds: String) : CredentialRepository {
    private val values = environmentIds.associateWith {
      EnvironmentCredential.directBearer(RedactedSecret.from("synthetic-$it-secret"))
    }.toMutableMap()

    override suspend fun read(environmentId: String): EnvironmentCredential? = values[environmentId]

    override suspend fun write(environmentId: String, credential: EnvironmentCredential) {
      values[environmentId] = credential
    }

    override suspend fun delete(environmentId: String) {
      values.remove(environmentId)
    }
  }

  private class FakeSessionFactory(
    private val log: MutableList<String>,
  ) : SupervisorSessionFactory {
    val sessions = mutableListOf<FakeSession>()
    val startedEnvironments = mutableListOf<String>()
    var startFailuresRemaining = 0
    var startCalls = 0
    var openSessions = 0
    var maximumOpenSessions = 0

    override suspend fun start(
      environment: SavedEnvironment,
      credential: RedactedSecret,
    ): SupervisorSession {
      startCalls += 1
      startedEnvironments += environment.environmentId
      log += "session-start:${environment.environmentId}"
      if (startFailuresRemaining > 0) {
        startFailuresRemaining -= 1
        throw IOException("synthetic start failure")
      }
      openSessions += 1
      maximumOpenSessions = maxOf(maximumOpenSessions, openSessions)
      return FakeSession(
        environment.environmentId,
        generation = sessions.size + 1L,
        subscriptionRequestIdentity = sessions.size + 1L,
        log = log,
        onClose = { openSessions -= 1 },
      ).also(sessions::add)
    }
  }

  private class FakeSession(
    private val environmentId: String,
    override val generation: Long,
    val subscriptionRequestIdentity: Long,
    private val log: MutableList<String>,
    private val onClose: () -> Unit,
  ) : SupervisorSession {
    private val items = Channel<ShellStreamDecodeResult>(Channel.UNLIMITED)
    val subscriptionModes = mutableListOf<String>()
    var activeCollectors = 0
    var closeCalls = 0
    private var closed = false

    override fun subscribeLongLivedShell(
      input: OrchestrationSubscribeShellInput,
    ): Flow<ShellStreamDecodeResult> = flow {
      assertEquals(true, input.requestCompletionMarker)
      subscriptionModes += "long-lived"
      activeCollectors += 1
      try {
        for (item in items) emit(item)
      } finally {
        activeCollectors -= 1
      }
    }

    suspend fun emitSynchronized() {
      items.send(ShellStreamDecodeResult.Decoded(OrchestrationShellStreamItem.Synchronized))
    }

    fun fail(error: Throwable) {
      items.close(error)
    }

    override suspend fun close() {
      if (closed) return
      closed = true
      closeCalls += 1
      log += "session-close:$environmentId"
      items.close()
      onClose()
    }
  }

  private class FakeSnapshotLoader : EnvironmentSnapshotLoader {
    val loadedEnvironments = mutableListOf<String>()
    val timeouts = mutableListOf<Duration>()
    var behavior: suspend (SavedEnvironment, Duration) -> OrchestrationShellSnapshot = { _, _ ->
      snapshot(1)
    }

    override suspend fun load(
      environment: SavedEnvironment,
      credential: RedactedSecret,
      timeout: Duration,
    ): OrchestrationShellSnapshot {
      loadedEnvironments += environment.environmentId
      timeouts += timeout
      return behavior(environment, timeout)
    }
  }

  private companion object {
    fun snapshot(sequence: Long): OrchestrationShellSnapshot = OrchestrationShellSnapshot(
      snapshotSequence = sequence,
      projects = emptyList(),
      threads = emptyList(),
      updatedAt = "2026-08-08T00:00:00Z",
    )
  }
}
