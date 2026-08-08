package com.t3tools.t3code.compose.app

import com.t3tools.t3code.compose.core.data.EnvironmentCatalogState
import com.t3tools.t3code.compose.core.data.SavedEnvironment
import com.t3tools.t3code.compose.core.data.ScopedEntityId
import com.t3tools.t3code.compose.core.data.ScopedEntityKind
import com.t3tools.t3code.compose.core.data.ShellAggregateState
import com.t3tools.t3code.compose.core.data.ShellMutation
import com.t3tools.t3code.compose.core.data.ShellReducer
import com.t3tools.t3code.compose.core.data.ShellSourceState
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentCapabilities
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatform
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformArch
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformOs
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationUiMapperTest {
  @Test
  fun aggregateRowsWithoutACatalogEntryAreIgnoredDuringAtomicReplacement() {
    val saved = environment("environment-a", "Studio A")
    var aggregate = ShellReducer.reduce(
      ShellAggregateState(),
      ShellMutation.RegisterEnvironment(saved),
    )
    aggregate = ShellReducer.reduce(
      aggregate,
      ShellMutation.ReplaceSnapshot(saved.environmentId, null, snapshot(1), ShellSourceState.LIVE),
    )

    val ui = FoundationUiMapper.map(
      EnvironmentCatalogState(emptyList(), null, emptyMap()),
      aggregate,
      selectedProjectUiId = null,
      selectedThreadUiId = null,
      requestedDestination = CompactDestination.PROJECTS,
    )

    assertTrue(ui.environments.isEmpty())
    assertTrue(ui.projects.isEmpty())
    assertEquals(CompactDestination.ENVIRONMENTS, ui.compactDestination)
  }

  @Test
  fun collidingRowsStayEnvironmentScopedAndOfflineRowsRemainVisible() {
    val first = environment("environment-a", "Studio A")
    val second = environment("environment-b", "Studio B")
    var aggregate = ShellAggregateState()
    aggregate = ShellReducer.reduce(aggregate, ShellMutation.RegisterEnvironment(first))
    aggregate = ShellReducer.reduce(aggregate, ShellMutation.RegisterEnvironment(second))
    aggregate = ShellReducer.reduce(
      aggregate,
      ShellMutation.ReplaceSnapshot(first.environmentId, null, snapshot(1), ShellSourceState.LIVE),
    )
    aggregate = ShellReducer.reduce(
      aggregate,
      ShellMutation.ReplaceSnapshot(second.environmentId, null, snapshot(1), ShellSourceState.PASSIVE),
    )
    aggregate = ShellReducer.reduce(
      aggregate,
      ShellMutation.MarkOffline(second.environmentId, null, "Environment unavailable"),
    )
    val catalog = EnvironmentCatalogState(listOf(first, second), first.environmentId, emptyMap())

    val firstUi = FoundationUiMapper.map(
      catalog,
      aggregate,
      selectedProjectUiId = null,
      selectedThreadUiId = null,
      requestedDestination = CompactDestination.PROJECTS,
    )
    val secondUi = FoundationUiMapper.map(
      catalog.copy(activeEnvironmentId = second.environmentId),
      aggregate,
      selectedProjectUiId = null,
      selectedThreadUiId = null,
      requestedDestination = CompactDestination.PROJECTS,
    )

    assertEquals(2, firstUi.projects.size)
    assertEquals(2, secondUi.projects.size)
    val firstProject = firstUi.projects.single { it.environmentId == first.environmentId }
    val secondProject = secondUi.projects.single { it.environmentId == second.environmentId }
    assertNotEquals(firstProject.uiId, secondProject.uiId)
    assertEquals(
      ScopedEntityId.create(ScopedEntityKind.PROJECT, first.environmentId, "shared-project").value,
      firstProject.uiId,
    )
    val offline = secondUi.environments.single { it.environmentId == second.environmentId }
    assertEquals(EnvironmentReachabilityUi.OFFLINE, offline.reachability)
    assertEquals(1, offline.projectCount)
    assertEquals(1, offline.threadCount)
    assertTrue(secondUi.projects.isNotEmpty())
    assertTrue(offline.capabilities.repositoryIdentity)
    assertEquals("available", offline.capabilities.serverSelfUpdate)
    assertEquals(true, offline.capabilities.serverSelfUpdateProgress)
    assertEquals(first.environmentId, FoundationSelectionResolver.project(aggregate, firstProject.uiId)?.environmentId)
    assertEquals("shared-project", FoundationSelectionResolver.project(aggregate, firstProject.uiId)?.wireId)
    assertEquals(second.environmentId, FoundationSelectionResolver.project(aggregate, secondProject.uiId)?.environmentId)
  }

  private fun environment(id: String, label: String) = SavedEnvironment(
    environmentId = id,
    label = label,
    httpBaseUrl = "http://127.0.0.1/",
    webSocketBaseUrl = "ws://127.0.0.1/",
    descriptor = ExecutionEnvironmentDescriptor(
      environmentId = id,
      label = label,
      platform = ExecutionEnvironmentPlatform(
        ExecutionEnvironmentPlatformOs.LINUX,
        ExecutionEnvironmentPlatformArch.X64,
      ),
      serverVersion = "test",
      capabilities = ExecutionEnvironmentCapabilities(
        repositoryIdentity = true,
        serverSelfUpdate = "available",
        serverSelfUpdateProgress = true,
      ),
    ),
  )

  private fun snapshot(sequence: Long) = OrchestrationShellSnapshot(
    snapshotSequence = sequence,
    projects = listOf(
      buildJsonObject {
        put("id", "shared-project")
        put("title", "Shared project")
        put("status", "ready")
      },
    ),
    threads = listOf(
      buildJsonObject {
        put("id", "shared-thread")
        put("projectId", "shared-project")
        put("title", "Shared thread")
        put("status", "active")
      },
    ),
    updatedAt = "2026-08-08T00:00:00Z",
  )

}
