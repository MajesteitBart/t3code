package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentCapabilities
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatform
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformArch
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformOs
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellStreamItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class ShellStateTest {
  @Test
  public fun scopedIdentityUsesUtf8ByteLengthAndRoundTripsDelimiterRichValues() {
    val environmentId = "環境:alpha"
    val wireId = "thread:同じ"

    val scoped = ScopedEntityId.create(ScopedEntityKind.THREAD, environmentId, wireId)
    val parsed = ScopedEntityId.parse(scoped.value)

    assertTrue(scoped.value.startsWith("thread:12:"))
    assertEquals(ParsedScopedEntityId(ScopedEntityKind.THREAD, environmentId, wireId), parsed)
    assertEquals(null, ScopedEntityId.parse("thread:2:環wire"))
  }

  @Test
  public fun collidingRawIdsRemainDistinctAndRawRoutingFailsAmbiguous() {
    val firstOwner = owner(1)
    val secondOwner = owner(2)
    var state = ShellAggregateState()
    state = register(state, "environment-a", snapshot(5, "same-project", "same-thread"))
    state = register(state, "environment-b", snapshot(6, "same-project", "same-thread"))
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner("environment-a", firstOwner, ShellSourceState.LIVE),
    )
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner("environment-b", secondOwner, ShellSourceState.LIVE),
    )

    val firstId = ScopedEntityId.create(ScopedEntityKind.THREAD, "environment-a", "same-thread")
    val secondId = ScopedEntityId.create(ScopedEntityKind.THREAD, "environment-b", "same-thread")
    assertNotEquals(firstId, secondId)
    assertEquals(2, state.threads.size)
    assertTrue(state.resolveRaw(ScopedEntityKind.THREAD, "same-thread") is RouteResolution.Ambiguous)

    val resolved = state.resolveThread(firstId) as RouteResolution.Resolved
    assertEquals("environment-a", resolved.route.address.environmentId)
    assertEquals("same-thread", resolved.route.address.wireId)
    assertEquals(firstOwner, resolved.route.owner)
  }

  @Test
  public fun archivedProvisionalRowsCapabilitiesAndLifecycleSurviveReductionAndRouting() {
    val environment = environment("environment-a")
    val snapshot = OrchestrationShellSnapshot(
      snapshotSequence = 10,
      projects = listOf(
        project("project-a", archived = true, provisional = true, lifecycle = "running"),
      ),
      threads = listOf(
        thread("thread-a", "project-a", archived = true, provisional = true, status = "waiting"),
      ),
      updatedAt = "2026-08-08T00:00:00Z",
    )
    val owner = owner(7)
    var state = ShellReducer.reduce(
      ShellAggregateState(),
      ShellMutation.RegisterEnvironment(environment, snapshot),
    )
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner(environment.environmentId, owner, ShellSourceState.LIVE),
    )

    val environmentState = state.environments.getValue(environment.environmentId)
    val project = environmentState.projects.values.single()
    val thread = environmentState.threads.values.single()
    assertEquals(true, environmentState.environment.descriptor.capabilities.threadSnooze)
    assertEquals("running", project.lifecycle)
    assertTrue(project.archived)
    assertTrue(project.provisional)
    assertEquals("waiting", thread.status)
    assertEquals("default", thread.interactionMode)
    assertTrue(thread.archived)
    assertTrue(thread.provisional)
    assertTrue(state.resolveProject(project.uiId) is RouteResolution.Resolved)
    assertTrue(state.resolveThread(thread.uiId) is RouteResolution.Resolved)
  }

  @Test
  public fun reducerCoversLoadingLiveOfflineRevokedRemovalAndRetainsLastKnownRows() {
    val environmentId = "environment-a"
    val owner = owner(1)
    var state = register(ShellAggregateState(), environmentId, snapshot(1))
    state = ShellReducer.reduce(state, ShellMutation.MarkLoading(environmentId))
    assertEquals(ShellSourceState.LOADING, state.environments.getValue(environmentId).source)
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner(environmentId, owner, ShellSourceState.LIVE),
    )
    state = ShellReducer.reduce(
      state,
      ShellMutation.ApplyStreamItem(
        environmentId,
        owner,
        OrchestrationShellStreamItem.ProjectUpserted(2, project("project-new")),
      ),
    )
    val live = state.environments.getValue(environmentId)
    assertEquals(ShellSourceState.LIVE, live.source)
    assertEquals(ShellDataFreshness.FRESH, live.freshness)
    assertEquals(2, live.projects.size)

    state = ShellReducer.reduce(state, ShellMutation.MarkOffline(environmentId, owner, "offline"))
    val offline = state.environments.getValue(environmentId)
    assertEquals(EnvironmentReachability.UNREACHABLE, offline.reachability)
    assertEquals(live.projects, offline.projects)

    state = ShellReducer.reduce(state, ShellMutation.MarkRevoked(environmentId, owner))
    val revoked = state.environments.getValue(environmentId)
    assertEquals(EnvironmentReachability.REVOKED, revoked.reachability)
    assertEquals(null, revoked.owner)
    assertEquals(live.projects, revoked.projects)

    state = ShellReducer.reduce(state, ShellMutation.RemoveEnvironment(environmentId))
    assertTrue(state.environments.isEmpty())
    assertTrue(state.projects.isEmpty())
  }

  @Test
  public fun supersededOwnerAndOlderSequenceCannotPublishLateState() {
    val environmentId = "environment-a"
    val firstOwner = owner(1)
    val secondOwner = owner(2)
    var state = register(ShellAggregateState(), environmentId, snapshot(10, "original"))
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner(environmentId, firstOwner, ShellSourceState.LIVE),
    )
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner(environmentId, secondOwner, ShellSourceState.RECONNECTING),
    )
    val beforeLate = state

    state = ShellReducer.reduce(
      state,
      ShellMutation.ReplaceSnapshot(
        environmentId,
        firstOwner,
        snapshot(20, "late-owner"),
        ShellSourceState.LIVE,
      ),
    )
    assertSame(beforeLate, state)

    state = ShellReducer.reduce(
      state,
      ShellMutation.ReplaceSnapshot(
        environmentId,
        secondOwner,
        snapshot(9, "old-sequence"),
        ShellSourceState.LIVE,
      ),
    )
    assertSame(beforeLate, state)

    state = ShellReducer.reduce(
      state,
      ShellMutation.ReplaceSnapshot(
        environmentId,
        secondOwner,
        snapshot(11, "fresh"),
        ShellSourceState.LIVE,
      ),
    )
    val current = state.environments.getValue(environmentId)
    assertEquals(11L, current.snapshotSequence)
    assertEquals("fresh", current.projects.values.single().wireId)
    assertFalse(current.projects.values.any { it.wireId == "late-owner" })
  }

  @Test
  public fun uniqueRawLookupRequiresCurrentOwnerAndResolvesAfterOwnershipArrives() {
    val environmentId = "environment-a"
    var state = register(ShellAggregateState(), environmentId, snapshot(1))

    val unavailable = state.resolveRaw(ScopedEntityKind.PROJECT, "project-a")
    assertTrue(unavailable is RouteResolution.OwnerUnavailable)

    val owner = owner(4)
    state = ShellReducer.reduce(
      state,
      ShellMutation.SetOwner(environmentId, owner, ShellSourceState.PASSIVE),
    )
    val resolved = state.resolveRaw(ScopedEntityKind.PROJECT, "project-a") as RouteResolution.Resolved
    assertEquals(owner, resolved.route.owner)
  }

  private fun register(
    state: ShellAggregateState,
    environmentId: String,
    snapshot: OrchestrationShellSnapshot,
  ): ShellAggregateState = ShellReducer.reduce(
    state,
    ShellMutation.RegisterEnvironment(environment(environmentId), snapshot),
  )

  private fun owner(epoch: Long): EnvironmentOwnerHandle = EnvironmentOwnerHandle(
    ownerEpoch = epoch,
    clientEpoch = epoch * 10,
    sessionGeneration = epoch * 100,
  )

  private fun environment(environmentId: String): SavedEnvironment = SavedEnvironment(
    environmentId = environmentId,
    label = "Environment $environmentId",
    httpBaseUrl = "https://$environmentId.test/",
    webSocketBaseUrl = "wss://$environmentId.test/",
    descriptor = ExecutionEnvironmentDescriptor(
      environmentId = environmentId,
      label = "Environment $environmentId",
      platform = ExecutionEnvironmentPlatform(
        os = ExecutionEnvironmentPlatformOs.LINUX,
        arch = ExecutionEnvironmentPlatformArch.X64,
      ),
      serverVersion = "test",
      capabilities = ExecutionEnvironmentCapabilities(
        repositoryIdentity = true,
        threadSnooze = true,
      ),
    ),
  )

  private fun snapshot(
    sequence: Long,
    projectId: String = "project-a",
    threadId: String = "thread-a",
  ): OrchestrationShellSnapshot = OrchestrationShellSnapshot(
    snapshotSequence = sequence,
    projects = listOf(project(projectId)),
    threads = listOf(thread(threadId, projectId)),
    updatedAt = "2026-08-08T00:00:00Z",
  )

  private fun project(
    id: String,
    archived: Boolean = false,
    provisional: Boolean = false,
    lifecycle: String? = null,
  ): JsonObject = buildJsonObject {
    put("id", id)
    put("title", "Project $id")
    if (archived) put("archivedAt", "2026-08-08T00:00:00Z")
    if (provisional) put("provisional", true)
    lifecycle?.let { put("lifecycle", it) }
  }

  private fun thread(
    id: String,
    projectId: String,
    archived: Boolean = false,
    provisional: Boolean = false,
    status: String? = null,
  ): JsonObject = buildJsonObject {
    put("id", id)
    put("projectId", projectId)
    put("title", "Thread $id")
    put("interactionMode", "default")
    if (archived) put("archivedAt", "2026-08-08T00:00:00Z")
    if (provisional) put("provisional", true)
    status?.let { put("status", it) }
  }
}
