package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentCapabilities
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatform
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformArch
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformOs
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.ShellStreamDecodeResult
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class ShellStateReconcilerTest {
  @Test
  public fun activeHttpFallbackAdvancesRowsButKeepsReconnectingSource() {
    runTest {
      val reconciler = reconciler()
      reconciler.registerEnvironment(environment("environment-a"), snapshot(1, "old-project"))
      val owner = owner(1, 2, 3)
      reconciler.accept(authority("environment-a", owner, EnvironmentSupervisionMode.ACTIVE, 1))
      reconciler.accept(
        ConnectionPublication.ReachabilityChanged(
          permit("environment-a", owner, 1),
          reachable = false,
          reconnecting = true,
          safeError = "Connection interrupted.",
        ),
      )

      val result = reconciler.accept(
        ConnectionPublication.SnapshotLoaded(
          permit("environment-a", owner, 2),
          snapshot(2, "fresh-project"),
          ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
        ),
      )

      assertEquals(ReconciliationDisposition.APPLIED, result)
      val state = reconciler.state.value.environments.getValue("environment-a")
      assertEquals(2L, state.snapshotSequence)
      assertTrue(state.projects.values.any { it.wireId == "fresh-project" })
      assertFalse(state.projects.values.any { it.wireId == "old-project" })
      assertEquals(ShellSourceState.RECONNECTING, state.source)
      assertEquals(ShellDataFreshness.FRESH, state.freshness)
      assertEquals(EnvironmentReachability.REACHABLE, state.reachability)
      reconciler.release()
    }
  }

  @Test
  public fun failedPassiveRefreshChangesOnlyReachabilityAndRetainsLastKnownRows() {
    runTest {
      val reconciler = reconciler()
      reconciler.registerEnvironment(environment("environment-b"), snapshot(7, "cached-project"))
      val owner = owner(4, 5, sessionGeneration = null)
      reconciler.accept(authority("environment-b", owner, EnvironmentSupervisionMode.PASSIVE, 4))

      reconciler.accept(
        ConnectionPublication.ReachabilityChanged(
          permit("environment-b", owner, 8),
          reachable = false,
          reconnecting = false,
          safeError = "Environment unavailable.",
        ),
      )

      val state = reconciler.state.value.environments.getValue("environment-b")
      assertEquals(7L, state.snapshotSequence)
      assertEquals(setOf("cached-project"), state.projects.values.map { it.wireId }.toSet())
      assertEquals(ShellDataFreshness.LAST_KNOWN, state.freshness)
      assertEquals(ShellSourceState.PASSIVE, state.source)
      assertEquals(EnvironmentReachability.UNREACHABLE, state.reachability)
      assertEquals("Environment unavailable.", state.safeError)
      reconciler.release()
    }
  }

  @Test
  public fun supersededAuthorityRefreshAndEnvironmentCannotPublishLateState() {
    runTest {
      val reconciler = reconciler()
      reconciler.registerEnvironment(environment("environment-a"), snapshot(1, "restored"))
      val staleOwner = owner(10, 11, 12)
      val currentOwner = owner(20, 21, 22)
      reconciler.accept(authority("environment-a", staleOwner, EnvironmentSupervisionMode.ACTIVE, 10))
      reconciler.accept(
        ConnectionPublication.SnapshotLoaded(
          permit("environment-a", staleOwner, 10),
          snapshot(10, "first-live"),
          ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
        ),
      )
      reconciler.accept(authority("environment-a", currentOwner, EnvironmentSupervisionMode.ACTIVE, 20))
      reconciler.accept(
        ConnectionPublication.SnapshotLoaded(
          permit("environment-a", currentOwner, 20),
          snapshot(20, "current"),
          ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
        ),
      )

      assertEquals(
        ReconciliationDisposition.IGNORED_STALE,
        reconciler.accept(
          ConnectionPublication.SnapshotLoaded(
            permit("environment-a", staleOwner, 99),
            snapshot(99, "stale-session"),
            ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
          ),
        ),
      )
      assertEquals(
        ReconciliationDisposition.IGNORED_STALE,
        reconciler.accept(
          ConnectionPublication.SnapshotLoaded(
            permit("environment-a", currentOwner, 19),
            snapshot(99, "stale-refresh"),
            ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
          ),
        ),
      )
      assertEquals(
        ReconciliationDisposition.IGNORED_STALE,
        reconciler.accept(
          authority("environment-a", staleOwner, EnvironmentSupervisionMode.ACTIVE, 15),
        ),
      )
      var state = reconciler.state.value.environments.getValue("environment-a")
      assertEquals(20L, state.snapshotSequence)
      assertEquals(currentOwner, state.owner)
      assertEquals(setOf("current"), state.projects.values.map { it.wireId }.toSet())

      reconciler.removeEnvironment("environment-a")
      assertEquals(
        ReconciliationDisposition.IGNORED_UNKNOWN_ENVIRONMENT,
        reconciler.accept(
          ConnectionPublication.SnapshotLoaded(
            permit("environment-a", currentOwner, 21),
            snapshot(100, "removed-environment"),
            ShellSnapshotOrigin.ACTIVE_HTTP_FALLBACK,
          ),
        ),
      )
      reconciler.registerEnvironment(environment("environment-a"))
      assertEquals(
        ReconciliationDisposition.IGNORED_STALE,
        reconciler.accept(
          authority("environment-a", staleOwner, EnvironmentSupervisionMode.ACTIVE, 19),
        ),
      )
      state = reconciler.state.value.environments.getValue("environment-a")
      assertNull(state.owner)
      assertTrue(state.projects.isEmpty())
      reconciler.release()
    }
  }

  @Test
  public fun unknownItemsCoalesceOneBoundedCanonicalRefreshWithoutBlockingOtherEnvironments() {
    runTest {
      val canonical = CompletableDeferred<OrchestrationShellSnapshot>()
      val requests = mutableListOf<CanonicalRefreshRequest>()
      val reconciler = reconciler(
        refresher = CanonicalSnapshotRefresher { request ->
          requests += request
          canonical.await()
        },
      )
      reconciler.registerEnvironment(environment("environment-a"), snapshot(1, "a-cached"))
      reconciler.registerEnvironment(environment("environment-b"), snapshot(1, "b-cached"))
      val activeOwner = owner(30, 31, 32)
      val passiveOwner = owner(40, 41, sessionGeneration = null)
      reconciler.accept(authority("environment-a", activeOwner, EnvironmentSupervisionMode.ACTIVE, 30))
      reconciler.accept(authority("environment-b", passiveOwner, EnvironmentSupervisionMode.PASSIVE, 40))
      val unknown = ConnectionPublication.StreamReceived(
        permit("environment-a", activeOwner, 50),
        ShellStreamDecodeResult.RefreshRequired("future-event"),
      )

      assertEquals(ReconciliationDisposition.CANONICAL_REFRESH_STARTED, reconciler.accept(unknown))
      assertEquals(ReconciliationDisposition.CANONICAL_REFRESH_COALESCED, reconciler.accept(unknown))
      assertEquals(ReconciliationDisposition.CANONICAL_REFRESH_COALESCED, reconciler.accept(unknown))
      runCurrent()
      assertEquals(1, requests.size)
      assertEquals(6.seconds, requests.single().timeout)

      reconciler.accept(
        ConnectionPublication.SnapshotLoaded(
          permit("environment-b", passiveOwner, 51),
          snapshot(2, "b-fresh"),
          ShellSnapshotOrigin.PASSIVE_HTTP,
        ),
      )
      assertEquals(
        setOf("b-fresh"),
        reconciler.state.value.environments.getValue("environment-b")
          .projects.values.map { it.wireId }.toSet(),
      )

      canonical.complete(snapshot(6, "a-canonical"))
      runCurrent()
      val activeState = reconciler.state.value.environments.getValue("environment-a")
      assertEquals(6L, activeState.snapshotSequence)
      assertEquals(ShellSourceState.LIVE, activeState.source)
      assertEquals(setOf("a-canonical"), activeState.projects.values.map { it.wireId }.toSet())
      assertEquals(ReconciliationDisposition.CANONICAL_REFRESH_COALESCED, reconciler.accept(unknown))
      assertEquals(1, requests.size)
      reconciler.release()
    }
  }

  @Test
  public fun cancelledCanonicalRefreshCannotPublishAfterAuthorityReplacement() {
    runTest {
      val delayed = CompletableDeferred<OrchestrationShellSnapshot>()
      val reconciler = reconciler(
        refresher = CanonicalSnapshotRefresher {
          withContext(NonCancellable) { delayed.await() }
        },
      )
      reconciler.registerEnvironment(environment("environment-a"), snapshot(1, "cached"))
      val oldOwner = owner(60, 61, 62)
      val newOwner = owner(70, 71, 72)
      reconciler.accept(authority("environment-a", oldOwner, EnvironmentSupervisionMode.ACTIVE, 60))
      reconciler.accept(
        ConnectionPublication.StreamReceived(
          permit("environment-a", oldOwner, 60),
          ShellStreamDecodeResult.RefreshRequired("future-event"),
        ),
      )
      runCurrent()

      reconciler.accept(authority("environment-a", newOwner, EnvironmentSupervisionMode.ACTIVE, 70))
      delayed.complete(snapshot(99, "late-canonical"))
      runCurrent()

      val state = reconciler.state.value.environments.getValue("environment-a")
      assertEquals(1L, state.snapshotSequence)
      assertEquals(newOwner, state.owner)
      assertEquals(setOf("cached"), state.projects.values.map { it.wireId }.toSet())
      reconciler.release()
    }
  }

  @Test
  public fun canonicalRefreshTimesOutOnceAndLeavesRowsUnchanged() {
    runTest {
      var refreshCalls = 0
      val reconciler = reconciler(
        refresher = CanonicalSnapshotRefresher {
          refreshCalls += 1
          delay(Long.MAX_VALUE)
          snapshot(99, "never")
        },
        settings = ReconciliationSettings(canonicalRefreshTimeout = 2.seconds),
      )
      reconciler.registerEnvironment(environment("environment-a"), snapshot(1, "cached"))
      val owner = owner(80, 81, 82)
      reconciler.accept(authority("environment-a", owner, EnvironmentSupervisionMode.ACTIVE, 80))
      val unknown = ConnectionPublication.StreamReceived(
        permit("environment-a", owner, 80),
        ShellStreamDecodeResult.RefreshRequired("future-event"),
      )
      reconciler.accept(unknown)
      runCurrent()
      assertEquals(1, refreshCalls)

      advanceTimeBy(2_000)
      runCurrent()
      assertEquals(ReconciliationDisposition.CANONICAL_REFRESH_COALESCED, reconciler.accept(unknown))
      assertEquals(1, refreshCalls)
      val state = reconciler.state.value.environments.getValue("environment-a")
      assertEquals(1L, state.snapshotSequence)
      assertEquals(setOf("cached"), state.projects.values.map { it.wireId }.toSet())
      reconciler.release()
    }
  }

  @Test
  public fun malformedSnapshotCannotPartiallyReplaceStateOrStopAnotherEnvironment() {
    runTest {
      val reconciler = reconciler()
      reconciler.registerEnvironment(environment("environment-a"), snapshot(1, "a-cached"))
      reconciler.registerEnvironment(environment("environment-b"), snapshot(1, "b-cached"))
      val firstOwner = owner(90, 91, sessionGeneration = null)
      val secondOwner = owner(100, 101, sessionGeneration = null)
      reconciler.accept(authority("environment-a", firstOwner, EnvironmentSupervisionMode.PASSIVE, 90))
      reconciler.accept(authority("environment-b", secondOwner, EnvironmentSupervisionMode.PASSIVE, 100))

      val malformed = OrchestrationShellSnapshot(
        snapshotSequence = 2,
        projects = listOf(buildJsonObject { put("title", "Missing ID") }),
        threads = emptyList(),
        updatedAt = "2026-08-08T00:00:00Z",
      )
      assertEquals(
        ReconciliationDisposition.INVALID_SNAPSHOT,
        reconciler.accept(
          ConnectionPublication.SnapshotLoaded(
            permit("environment-a", firstOwner, 2),
            malformed,
            ShellSnapshotOrigin.PASSIVE_HTTP,
          ),
        ),
      )
      assertEquals(
        setOf("a-cached"),
        reconciler.state.value.environments.getValue("environment-a")
          .projects.values.map { it.wireId }.toSet(),
      )

      assertEquals(
        ReconciliationDisposition.APPLIED,
        reconciler.accept(
          ConnectionPublication.SnapshotLoaded(
            permit("environment-b", secondOwner, 3),
            snapshot(3, "b-fresh"),
            ShellSnapshotOrigin.PASSIVE_HTTP,
          ),
        ),
      )
      assertEquals(
        setOf("b-fresh"),
        reconciler.state.value.environments.getValue("environment-b")
          .projects.values.map { it.wireId }.toSet(),
      )
      reconciler.release()
    }
  }

  private fun TestScope.reconciler(
    refresher: CanonicalSnapshotRefresher = CanonicalSnapshotRefresher {
      snapshot(1, "canonical")
    },
    settings: ReconciliationSettings = ReconciliationSettings(),
  ): ShellStateReconciler = ShellStateReconciler(
    parentScope = backgroundScope,
    canonicalRefresher = refresher,
    settings = settings,
  )

  private fun authority(
    environmentId: String,
    owner: EnvironmentOwnerHandle,
    mode: EnvironmentSupervisionMode,
    authorityEpoch: Long,
  ): ConnectionPublication.AuthorityChanged = ConnectionPublication.AuthorityChanged(
    environmentId,
    owner,
    mode,
    authorityEpoch,
  )

  private fun permit(
    environmentId: String,
    owner: EnvironmentOwnerHandle,
    refreshEpoch: Long,
  ): PublicationPermit = PublicationPermit(environmentId, owner, refreshEpoch)

  private fun owner(
    ownerEpoch: Long,
    clientEpoch: Long,
    sessionGeneration: Long?,
  ): EnvironmentOwnerHandle = EnvironmentOwnerHandle(ownerEpoch, clientEpoch, sessionGeneration)

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

  private companion object {
    fun snapshot(sequence: Long, projectId: String): OrchestrationShellSnapshot =
      OrchestrationShellSnapshot(
        snapshotSequence = sequence,
        projects = listOf(
          buildJsonObject {
            put("id", projectId)
            put("title", "Project $projectId")
          },
        ),
        threads = emptyList(),
        updatedAt = "2026-08-08T00:00:00Z",
      )
  }
}
