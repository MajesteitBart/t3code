package com.t3tools.t3code.compose.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun pairingInputIsHiddenAndSubmitsThroughTheActionBoundary() {
    val actions = RecordingActions()
    val state = FoundationUiState(
      initializing = false,
      pairing = PairingPanelUi(visible = true),
    )
    setContent(state, actions)

    composeRule.onNodeWithTag(FoundationSemantics.PairingInput).performTextInput("host.test one-time-code")
    composeRule.onNodeWithTag(FoundationSemantics.PairingSubmit).performClick()

    composeRule.runOnIdle {
      assertEquals(listOf("host.test one-time-code"), actions.submittedPairingInputs)
    }
  }

  @Test
  fun savedEnvironmentRowsExposeActivationRemovalAndRevokedRecovery() {
    val actions = RecordingActions()
    val state = shellState(
      firstReachability = EnvironmentReachabilityUi.REVOKED,
      destination = CompactDestination.ENVIRONMENTS,
    )
    setContent(state, actions)

    composeRule.onNodeWithTag("environment:environment-b").performClick()
    composeRule.onNodeWithTag("remove-environment:environment-a").performClick()
    composeRule.onNodeWithTag("pair-again:environment-a").performClick()

    composeRule.runOnIdle {
      assertEquals(listOf("environment-b"), actions.activatedEnvironmentIds)
      assertEquals(listOf("environment-a"), actions.removedEnvironmentIds)
      assertEquals(listOf("environment-a"), actions.repairedEnvironmentIds)
      assertTrue(actions.showProjectsCalls > 0)
    }
  }

  @Test
  fun localNetworkFailureOffersSettingsWithoutShowingRawInput() {
    val actions = RecordingActions()
    val state = FoundationUiState(
      initializing = false,
      pairing = PairingPanelUi(
        visible = true,
        status = PairingStatusUi.Failed(
          PairingFailureUi(
            kind = PairingFailureKind.LOCAL_NETWORK_PERMISSION,
            title = "Local network access is off",
            message = "Allow local network access for T3 Compose, then try again.",
            action = PairingRecoveryAction.OPEN_SETTINGS,
          ),
        ),
      ),
    )
    setContent(state, actions)

    composeRule.onNodeWithText("Open app settings").performClick()

    composeRule.runOnIdle { assertEquals(1, actions.openSettingsCalls) }
  }

  @Test
  fun reconnectingEnvironmentKeepsFreshRowsUsableAndOffersRetry() {
    val actions = RecordingActions()
    val base = shellState(
      firstReachability = EnvironmentReachabilityUi.OFFLINE,
      destination = CompactDestination.PROJECTS,
    )
    val reconnecting = base.environments.first().copy(
      source = EnvironmentSourceUi.RECONNECTING,
      freshness = EnvironmentFreshnessUi.FRESH,
      safeError = "Live connection interrupted",
    )
    setContent(base.copy(environments = listOf(reconnecting) + base.environments.drop(1)), actions)

    composeRule.onNodeWithText("Live updates reconnecting").assertIsDisplayed()
    composeRule.onNodeWithTag("project:project-a").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Retry now").performClick()

    composeRule.runOnIdle {
      assertEquals(listOf("project-a"), actions.selectedProjectIds)
      assertEquals(listOf("environment-a"), actions.retriedEnvironmentIds)
    }
  }

  @Test
  fun aggregateHomeKeepsCollidingOfflineProjectsVisibleAndRoutesByScopedId() {
    val actions = RecordingActions()
    val base = shellState(
      firstReachability = EnvironmentReachabilityUi.REACHABLE,
      destination = CompactDestination.PROJECTS,
    )
    val offlineEnvironment = base.environments[1].copy(
      reachability = EnvironmentReachabilityUi.OFFLINE,
      source = EnvironmentSourceUi.PASSIVE,
      freshness = EnvironmentFreshnessUi.LAST_KNOWN,
    )
    val offlineProject = base.projects.single().copy(
      uiId = "project-b",
      environmentId = offlineEnvironment.environmentId,
      environmentLabel = offlineEnvironment.label,
      environmentReachability = EnvironmentReachabilityUi.OFFLINE,
      environmentFreshness = EnvironmentFreshnessUi.LAST_KNOWN,
    )
    setContent(
      base.copy(
        environments = listOf(base.environments[0], offlineEnvironment),
        projects = base.projects + offlineProject,
      ),
      actions,
    )

    composeRule.onNodeWithTag("project:project-a").assertIsDisplayed()
    composeRule.onNodeWithTag("project:project-b").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Last known").assertIsDisplayed()

    composeRule.runOnIdle { assertEquals(listOf("project-b"), actions.selectedProjectIds) }
  }

  @Test
  fun expandedWidthKeepsEnvironmentProjectAndThreadContextVisible() {
    val state = shellState(
      firstReachability = EnvironmentReachabilityUi.REACHABLE,
      destination = CompactDestination.THREAD_DETAIL,
      selectedProject = "project-a",
      selectedThread = "thread-a",
    )
    composeRule.setContent {
      T3CodeComposeTheme {
        Box(Modifier.requiredSize(1000.dp, 700.dp)) {
          FoundationScreen(state, NoOpFoundationActions)
        }
      }
    }

    composeRule.onNodeWithTag(FoundationSemantics.ExpandedShell).fetchSemanticsNode()
    composeRule.onNodeWithTag(FoundationSemantics.EnvironmentList).fetchSemanticsNode()
    composeRule.onNodeWithTag(FoundationSemantics.ProjectList).fetchSemanticsNode()
    composeRule.onNodeWithTag(FoundationSemantics.ThreadList).fetchSemanticsNode()
    composeRule.onNodeWithTag(FoundationSemantics.ThreadDetail).fetchSemanticsNode()
  }

  private fun setContent(state: FoundationUiState, actions: FoundationActions) {
    composeRule.setContent {
      T3CodeComposeTheme { FoundationScreen(state, actions) }
    }
  }

  private fun shellState(
    firstReachability: EnvironmentReachabilityUi,
    destination: CompactDestination,
    selectedProject: String? = null,
    selectedThread: String? = null,
  ): FoundationUiState = FoundationUiState(
    initializing = false,
    environments = listOf(
      environment("environment-a", "Studio A", true, firstReachability),
      environment("environment-b", "Studio B", false, EnvironmentReachabilityUi.REACHABLE),
    ),
    activeEnvironmentId = "environment-a",
    projects = listOf(
      ProjectRowUi(
        uiId = "project-a",
        environmentId = "environment-a",
        environmentLabel = "Studio A",
        environmentReachability = EnvironmentReachabilityUi.REACHABLE,
        environmentFreshness = EnvironmentFreshnessUi.FRESH,
        wireId = "shared-project",
        title = "T3 Code",
        lifecycle = "active",
        status = "ready",
        archived = false,
        provisional = false,
        badgeSeed = 3,
      ),
    ),
    threads = listOf(
      ThreadRowUi(
        uiId = "thread-a",
        environmentId = "environment-a",
        wireId = "shared-thread",
        projectUiId = "project-a",
        projectWireId = "shared-project",
        title = "Native Android shell",
        lifecycle = "active",
        status = "working",
        archived = false,
        provisional = false,
        interactionMode = "default",
      ),
    ),
    selectedProjectUiId = selectedProject,
    selectedThreadUiId = selectedThread,
    compactDestination = destination,
  )

  private fun environment(
    id: String,
    label: String,
    active: Boolean,
    reachability: EnvironmentReachabilityUi,
  ) = EnvironmentRowUi(
    environmentId = id,
    label = label,
    safeEndpoint = "http://10.0.2.2:13773/",
    isActive = active,
    reachability = reachability,
    source = if (reachability == EnvironmentReachabilityUi.REVOKED) {
      EnvironmentSourceUi.RELEASED
    } else {
      EnvironmentSourceUi.LIVE
    },
    freshness = EnvironmentFreshnessUi.FRESH,
    safeError = null,
    projectCount = 1,
    threadCount = 1,
    badgeSeed = id.hashCode(),
    capabilities = EnvironmentCapabilitiesUi(
      repositoryIdentity = true,
      connectionProbe = true,
      threadSettlement = true,
      threadSnooze = true,
      threadPinning = true,
      threadTitleRegeneration = true,
      serverSelfUpdate = "available",
      serverSelfUpdateProgress = true,
    ),
  )

  private class RecordingActions : FoundationActions {
    val submittedPairingInputs = mutableListOf<String>()
    val activatedEnvironmentIds = mutableListOf<String>()
    val removedEnvironmentIds = mutableListOf<String>()
    val repairedEnvironmentIds = mutableListOf<String>()
    val retriedEnvironmentIds = mutableListOf<String>()
    val selectedProjectIds = mutableListOf<String>()
    var showProjectsCalls = 0
    var openSettingsCalls = 0

    override fun addEnvironment() = Unit
    override fun dismissPairing() = Unit
    override fun editPairing() = Unit
    override fun submitPairing(input: String) {
      submittedPairingInputs += input
    }
    override fun retryPairing() = Unit
    override fun cancelPairing() = Unit
    override fun openAppSettings() {
      openSettingsCalls += 1
    }
    override fun activateEnvironment(environmentId: String) {
      activatedEnvironmentIds += environmentId
    }
    override fun removeEnvironment(environmentId: String) {
      removedEnvironmentIds += environmentId
    }
    override fun retryEnvironment(environmentId: String) {
      retriedEnvironmentIds += environmentId
    }
    override fun pairAgain(environmentId: String) {
      repairedEnvironmentIds += environmentId
    }
    override fun showEnvironments() = Unit
    override fun showProjects() {
      showProjectsCalls += 1
    }
    override fun selectProject(uiId: String) {
      selectedProjectIds += uiId
    }
    override fun selectThread(uiId: String) = Unit
    override fun navigateBack(): Boolean = false
    override fun clearMessage() = Unit
  }
}
