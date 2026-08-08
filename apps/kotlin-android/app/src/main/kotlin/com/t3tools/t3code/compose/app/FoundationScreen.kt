package com.t3tools.t3code.compose.app

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal object FoundationSemantics {
  const val Root = "native-android-foundation"
  const val AddEnvironment = "add-environment"
  const val Onboarding = "environment-onboarding"
  const val PairingInput = "pairing-input"
  const val PairingSubmit = "pairing-submit"
  const val EnvironmentList = "environment-list"
  const val ProjectList = "project-list"
  const val ThreadList = "thread-list"
  const val ThreadDetail = "thread-detail"
  const val StatusBanner = "environment-status"
  const val ExpandedShell = "expanded-shell"
}

private enum class FoundationWindowClass {
  COMPACT,
  MEDIUM,
  EXPANDED,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FoundationScreen(
  state: FoundationUiState,
  actions: FoundationActions,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize().testTag(FoundationSemantics.Root),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = titleFor(state),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          if (state.compactDestination != CompactDestination.ENVIRONMENTS) {
            TextButton(onClick = { actions.navigateBack() }) { Text("Back") }
          }
        },
        actions = {
          TextButton(
            modifier = Modifier.testTag(FoundationSemantics.AddEnvironment),
            onClick = actions::addEnvironment,
          ) {
            Text("Add")
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
      when {
        state.initializing -> StaticState(
          title = "Restoring environments",
          detail = "Saved T3 environments and last-known state are loading.",
        )
        state.globalError != null && state.environments.isEmpty() -> StaticState(
          title = "Saved state unavailable",
          detail = state.globalError,
          actionLabel = "Add environment",
          onAction = actions::addEnvironment,
        )
        state.environments.isEmpty() -> OnboardingLanding(actions::addEnvironment)
        else -> AdaptiveHomeShell(state, actions)
      }

      state.globalMessage?.let { message ->
        MessageBanner(
          modifier = Modifier.align(Alignment.BottomCenter),
          message = message,
          onDismiss = actions::clearMessage,
        )
      }
    }
  }

  if (state.pairing.visible) {
    if (state.environments.isEmpty()) {
      PairingFullscreen(state.pairing, actions)
    } else {
      AlertDialog(
        modifier = Modifier.testTag(FoundationSemantics.Onboarding),
        onDismissRequest = actions::dismissPairing,
        title = { Text("Add environment") },
        text = { PairingForm(state.pairing, actions) },
        confirmButton = {},
        dismissButton = {},
      )
    }
  }
}

@Composable
private fun AdaptiveHomeShell(state: FoundationUiState, actions: FoundationActions) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val windowClass = when {
      maxWidth < 600.dp -> FoundationWindowClass.COMPACT
      maxWidth < 840.dp -> FoundationWindowClass.MEDIUM
      else -> FoundationWindowClass.EXPANDED
    }
    BackHandler(
      enabled = windowClass == FoundationWindowClass.COMPACT &&
        state.compactDestination != CompactDestination.ENVIRONMENTS,
    ) {
      actions.navigateBack()
    }
    when (windowClass) {
      FoundationWindowClass.COMPACT -> CompactHomeShell(state, actions)
      FoundationWindowClass.MEDIUM -> MediumHomeShell(state, actions)
      FoundationWindowClass.EXPANDED -> ExpandedHomeShell(state, actions)
    }
  }
}

@Composable
private fun CompactHomeShell(state: FoundationUiState, actions: FoundationActions) {
  when (state.compactDestination) {
    CompactDestination.ENVIRONMENTS -> EnvironmentPane(state, actions)
    CompactDestination.PROJECTS -> ProjectPane(state, actions)
    CompactDestination.THREADS -> ThreadPane(state, actions)
    CompactDestination.THREAD_DETAIL -> ThreadDetailPane(state, actions)
  }
}

@Composable
private fun MediumHomeShell(state: FoundationUiState, actions: FoundationActions) {
  Row(Modifier.fillMaxSize().testTag(FoundationSemantics.ExpandedShell)) {
    EnvironmentPane(
      state,
      actions,
      modifier = Modifier.weight(0.38f).fillMaxHeight(),
      compactHeader = true,
    )
    HorizontalPaneDivider()
    when {
      state.selectedThread != null -> ThreadDetailPane(
        state,
        actions,
        Modifier.weight(0.62f).fillMaxHeight(),
      )
      state.selectedProject != null -> ThreadPane(
        state,
        actions,
        Modifier.weight(0.62f).fillMaxHeight(),
      )
      else -> ProjectPane(
        state,
        actions,
        Modifier.weight(0.62f).fillMaxHeight(),
      )
    }
  }
}

@Composable
private fun ExpandedHomeShell(state: FoundationUiState, actions: FoundationActions) {
  Row(Modifier.fillMaxSize().testTag(FoundationSemantics.ExpandedShell)) {
    EnvironmentPane(
      state,
      actions,
      modifier = Modifier.weight(0.24f).fillMaxHeight(),
      compactHeader = true,
    )
    HorizontalPaneDivider()
    ProjectPane(
      state,
      actions,
      modifier = Modifier.weight(0.30f).fillMaxHeight(),
    )
    HorizontalPaneDivider()
    Column(Modifier.weight(0.46f).fillMaxHeight()) {
      if (state.selectedThread == null) {
        ThreadPane(state, actions, Modifier.weight(1f), compactHeader = true)
      } else {
        ThreadDetailPane(state, actions, Modifier.weight(0.44f), compactHeader = true)
        HorizontalDivider()
        ThreadPane(state, actions, Modifier.weight(0.56f), compactHeader = true)
      }
    }
  }
}

@Composable
private fun EnvironmentPane(
  state: FoundationUiState,
  actions: FoundationActions,
  modifier: Modifier = Modifier,
  compactHeader: Boolean = false,
) {
  Pane(
    modifier = modifier.testTag(FoundationSemantics.EnvironmentList),
    title = if (compactHeader) "Environments" else "Saved environments",
    subtitle = "${state.environments.size} saved",
  ) {
    LazyColumn(
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(state.environments, key = EnvironmentRowUi::environmentId) { environment ->
        EnvironmentCard(
          environment = environment,
          busy = state.busyEnvironmentId == environment.environmentId,
          onActivate = {
            actions.activateEnvironment(environment.environmentId)
            actions.showProjects()
          },
          onRemove = { actions.removeEnvironment(environment.environmentId) },
          onRetry = { actions.retryEnvironment(environment.environmentId) },
          onPairAgain = { actions.pairAgain(environment.environmentId) },
        )
      }
    }
  }
}

@Composable
private fun ProjectPane(
  state: FoundationUiState,
  actions: FoundationActions,
  modifier: Modifier = Modifier,
) {
  val environment = state.activeEnvironment
  Pane(
    modifier = modifier.testTag(FoundationSemantics.ProjectList),
    title = "Projects",
    subtitle = "${state.projects.size} across ${state.environments.size} environments",
  ) {
    if (environment == null) {
      StaticState(
        title = "Choose an environment",
        detail = "Select a saved environment to see its projects.",
        actionLabel = "Environments",
        onAction = actions::showEnvironments,
      )
      return@Pane
    }
    EnvironmentStatusBanner(environment, actions)
    if (state.projects.isEmpty()) {
      val (title, detail) = emptyProjectMessage(environment)
      StaticState(title, detail)
    } else {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(state.projects, key = ProjectRowUi::uiId) { project ->
          ProjectCard(
            project,
            selected = project.uiId == state.selectedProjectUiId,
            onClick = { actions.selectProject(project.uiId) },
          )
        }
      }
    }
  }
}

@Composable
private fun ThreadPane(
  state: FoundationUiState,
  actions: FoundationActions,
  modifier: Modifier = Modifier,
  compactHeader: Boolean = false,
) {
  val project = state.selectedProject
  Pane(
    modifier = modifier.testTag(FoundationSemantics.ThreadList),
    title = if (compactHeader) "Threads" else project?.title ?: "Threads",
    subtitle = project?.let { "${state.threads.size} threads" },
  ) {
    if (project == null) {
      StaticState(
        title = "Choose a project",
        detail = "Select a project to see its threads.",
      )
    } else if (state.threads.isEmpty()) {
      StaticState(
        title = "No threads yet",
        detail = "This project has no thread rows in the current environment snapshot.",
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(state.threads, key = ThreadRowUi::uiId) { thread ->
          ThreadCard(
            thread,
            selected = thread.uiId == state.selectedThreadUiId,
            onClick = { actions.selectThread(thread.uiId) },
          )
        }
      }
    }
  }
}

@Composable
private fun ThreadDetailPane(
  state: FoundationUiState,
  actions: FoundationActions,
  modifier: Modifier = Modifier,
  compactHeader: Boolean = false,
) {
  val thread = state.selectedThread
  Pane(
    modifier = modifier.testTag(FoundationSemantics.ThreadDetail),
    title = if (compactHeader) "Thread" else thread?.title ?: "Thread",
    subtitle = thread?.let { "${it.projectWireId} / ${it.wireId}" },
  ) {
    if (thread == null) {
      StaticState(
        title = "Choose a thread",
        detail = "Select a thread to inspect its current shell state.",
      )
      return@Pane
    }
    Column(
      Modifier.fillMaxSize().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(thread.title, style = MaterialTheme.typography.headlineSmall)
      MetadataLine("Environment", state.activeEnvironment?.label ?: thread.environmentId)
      MetadataLine("Status", thread.status ?: "Unknown")
      MetadataLine("Lifecycle", thread.lifecycle ?: "Unknown")
      MetadataLine("Interaction", thread.interactionMode ?: "default")
      if (thread.archived) StatusPill("Archived")
      if (thread.provisional) StatusPill("Provisional")
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick = actions::showProjects) { Text("Back to projects") }
    }
  }
}

@Composable
private fun EnvironmentCard(
  environment: EnvironmentRowUi,
  busy: Boolean,
  onActivate: () -> Unit,
  onRemove: () -> Unit,
  onRetry: () -> Unit,
  onPairAgain: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("environment:${environment.environmentId}")
      .semantics { selected = environment.isActive },
    onClick = onActivate,
    colors = CardDefaults.cardColors(
      containerColor = if (environment.isActive) {
        MaterialTheme.colorScheme.secondaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Badge(environment.label, environment.badgeSeed)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            environment.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (environment.isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (environment.isActive) StatusPill("Current")
        }
        Text(
          environment.safeEndpoint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          environmentStatusText(environment),
          style = MaterialTheme.typography.labelMedium,
          color = environmentStatusColor(environment),
        )
        Text(
          "${environment.projectCount} projects, ${environment.threadCount} threads",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          when {
            environment.reachability == EnvironmentReachabilityUi.REVOKED -> {
              TextButton(
                modifier = Modifier.testTag("pair-again:${environment.environmentId}"),
                enabled = !busy,
                onClick = onPairAgain,
              ) {
                Text("Pair again")
              }
            }
            environment.reachability == EnvironmentReachabilityUi.OFFLINE -> {
              TextButton(
                modifier = Modifier.testTag("retry-environment:${environment.environmentId}"),
                enabled = !busy,
                onClick = onRetry,
              ) {
                Text("Retry")
              }
            }
          }
          TextButton(
            modifier = Modifier.testTag("remove-environment:${environment.environmentId}"),
            enabled = !busy,
            onClick = onRemove,
          ) {
            Text(if (busy) "Working" else "Remove")
          }
        }
      }
    }
  }
}

@Composable
private fun ProjectCard(project: ProjectRowUi, selected: Boolean, onClick: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("project:${project.uiId}")
      .semantics { this.selected = selected },
    onClick = onClick,
    colors = CardDefaults.cardColors(
      containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Badge(project.title, project.badgeSeed)
      Column(Modifier.weight(1f)) {
        Text(
          project.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          buildList {
            add(project.environmentLabel)
            addAll(listOfNotNull(project.lifecycle, project.status))
          }.joinToString(" / "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (project.environmentReachability == EnvironmentReachabilityUi.OFFLINE) {
        StatusPill(
          if (project.environmentFreshness == EnvironmentFreshnessUi.FRESH) "Offline" else "Last known",
        )
      }
      if (project.archived) StatusPill("Archived")
      if (project.provisional) StatusPill("Draft")
    }
  }
}

@Composable
private fun ThreadCard(thread: ThreadRowUi, selected: Boolean, onClick: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("thread:${thread.uiId}")
      .semantics { this.selected = selected },
    onClick = onClick,
    colors = CardDefaults.cardColors(
      containerColor = if (selected) {
        MaterialTheme.colorScheme.tertiaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(
        thread.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        listOfNotNull(thread.lifecycle, thread.status, thread.interactionMode)
          .joinToString(" / ")
          .ifEmpty { "Thread" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EnvironmentStatusBanner(environment: EnvironmentRowUi, actions: FoundationActions) {
  val content = when {
    environment.reachability == EnvironmentReachabilityUi.REVOKED -> Triple(
      "Pairing expired",
      "This environment rejected its saved credential. Pair it again to resume.",
      "Pair again",
    )
    environment.source == EnvironmentSourceUi.RECONNECTING &&
      environment.freshness == EnvironmentFreshnessUi.FRESH -> Triple(
        "Live updates reconnecting",
        "A current HTTP snapshot is available while the live connection recovers.",
        "Retry now",
      )
    environment.reachability == EnvironmentReachabilityUi.OFFLINE &&
      environment.freshness != EnvironmentFreshnessUi.NONE -> Triple(
        "Showing last-known data",
        environment.safeError ?: "This environment is offline. Saved rows remain available.",
        "Retry",
      )
    environment.reachability == EnvironmentReachabilityUi.OFFLINE -> Triple(
      "Environment offline",
      environment.safeError ?: "No current or saved shell state is available.",
      "Retry",
    )
    environment.source == EnvironmentSourceUi.LOADING -> Triple(
      "Loading environment",
      "T3 Compose is waiting for the first shell snapshot.",
      null,
    )
    else -> null
  } ?: return

  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
      .testTag(FoundationSemantics.StatusBanner)
      .semantics { liveRegion = LiveRegionMode.Polite },
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.padding(14.dp)) {
      Text(content.first, style = MaterialTheme.typography.titleSmall)
      Text(content.second, style = MaterialTheme.typography.bodySmall)
      content.third?.let { label ->
        TextButton(
          onClick = {
            if (environment.reachability == EnvironmentReachabilityUi.REVOKED) {
              actions.pairAgain(environment.environmentId)
            } else {
              actions.retryEnvironment(environment.environmentId)
            }
          },
        ) {
          Text(label)
        }
      }
    }
  }
}

@Composable
private fun PairingFullscreen(panel: PairingPanelUi, actions: FoundationActions) {
  Surface(
    modifier = Modifier.fillMaxSize().testTag(FoundationSemantics.Onboarding),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Text("Connect T3 Compose", style = MaterialTheme.typography.headlineMedium)
        Text(
          "Paste a one-time pairing link, a server and code, or a copied T3 QR payload.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PairingForm(panel, actions)
      }
    }
  }
}

@Composable
private fun PairingForm(panel: PairingPanelUi, actions: FoundationActions) {
  var input by remember(panel.draftRevision) {
    mutableStateOf(panel.draft?.reveal().orEmpty())
  }
  LaunchedEffect(panel.draftRevision) {
    input = panel.draft?.reveal().orEmpty()
  }
  val pairing = panel.status is PairingStatusUi.Pairing

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      value = input,
      onValueChange = { input = it },
      modifier = Modifier.fillMaxWidth().testTag(FoundationSemantics.PairingInput).semantics {
        password()
        contentDescription = "Pairing details, hidden"
      },
      enabled = !pairing,
      label = { Text("Pairing link or server and code") },
      supportingText = { Text("Credentials stay hidden and are discarded after exchange.") },
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(
        onDone = { if (!pairing && input.isNotBlank()) actions.submitPairing(input) },
      ),
      singleLine = false,
      minLines = 2,
      maxLines = 3,
    )

    when (val status = panel.status) {
      PairingStatusUi.Idle -> Unit
      is PairingStatusUi.Pairing -> PairingProgress(status.safeTarget, actions::cancelPairing)
      is PairingStatusUi.Paired -> PairingSuccess(status.environmentLabel, actions::dismissPairing)
      is PairingStatusUi.Failed -> PairingFailure(status.failure, actions)
    }

    if (panel.status !is PairingStatusUi.Paired) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          modifier = Modifier.testTag(FoundationSemantics.PairingSubmit),
          enabled = input.isNotBlank() && !pairing,
          onClick = { actions.submitPairing(input) },
        ) {
          Text(if (pairing) "Pairing" else "Pair environment")
        }
        if (panel.status !is PairingStatusUi.Pairing) {
          TextButton(onClick = actions::dismissPairing) { Text("Cancel") }
        }
      }
    }
  }
}

@Composable
private fun PairingProgress(safeTarget: String?, onCancel: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
      Text("Pairing in progress", style = MaterialTheme.typography.titleSmall)
      Text(
        safeTarget?.let { "Connecting to $it" } ?: "Checking the environment and exchanging the code.",
        style = MaterialTheme.typography.bodySmall,
      )
      TextButton(onClick = onCancel) { Text("Cancel attempt") }
    }
  }
}

@Composable
private fun PairingSuccess(environmentLabel: String, onContinue: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
      Text("Environment saved", style = MaterialTheme.typography.titleSmall)
      Text("$environmentLabel is ready to use.", style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = onContinue) { Text("Continue") }
    }
  }
}

@Composable
private fun PairingFailure(failure: PairingFailureUi, actions: FoundationActions) {
  Surface(
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    color = MaterialTheme.colorScheme.errorContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
      Text(failure.title, style = MaterialTheme.typography.titleSmall)
      Text(failure.message, style = MaterialTheme.typography.bodySmall)
      failure.traceId?.let { Text("Trace ID: $it", style = MaterialTheme.typography.labelSmall) }
      TextButton(
        onClick = {
          when (failure.action) {
            PairingRecoveryAction.EDIT, PairingRecoveryAction.PAIR_AGAIN -> actions.editPairing()
            PairingRecoveryAction.RETRY -> actions.retryPairing()
            PairingRecoveryAction.OPEN_SETTINGS -> actions.openAppSettings()
          }
        },
      ) {
        Text(
          when (failure.action) {
            PairingRecoveryAction.EDIT -> "Edit details"
            PairingRecoveryAction.RETRY -> "Try again"
            PairingRecoveryAction.OPEN_SETTINGS -> "Open app settings"
            PairingRecoveryAction.PAIR_AGAIN -> "Pair again"
          },
        )
      }
    }
  }
}

@Composable
private fun Pane(
  modifier: Modifier,
  title: String,
  subtitle: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      subtitle?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    HorizontalDivider()
    content()
  }
}

@Composable
private fun StaticState(
  title: String,
  detail: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    Modifier.fillMaxSize().padding(28.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(
      modifier = Modifier.padding(top = 8.dp),
      text = detail,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (actionLabel != null && onAction != null) {
      Button(modifier = Modifier.padding(top = 18.dp), onClick = onAction) { Text(actionLabel) }
    }
  }
}

@Composable
private fun OnboardingLanding(onAdd: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("T3 Code Compose", style = MaterialTheme.typography.headlineMedium)
    Text(
      modifier = Modifier.padding(top = 10.dp),
      text = "Connect a T3 environment to see its projects and threads.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(modifier = Modifier.padding(top = 22.dp), onClick = onAdd) { Text("Add environment") }
  }
}

@Composable
private fun MessageBanner(modifier: Modifier, message: String, onDismiss: () -> Unit) {
  Surface(
    modifier = modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    color = MaterialTheme.colorScheme.inverseSurface,
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    shape = MaterialTheme.shapes.medium,
    tonalElevation = 4.dp,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
      TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
  }
}

@Composable
private fun Badge(label: String, seed: Int) {
  val palette = listOf(
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.tertiaryContainer,
    MaterialTheme.colorScheme.surfaceVariant,
  )
  val color = palette[Math.floorMod(seed, palette.size)]
  Box(
    modifier = Modifier.size(40.dp).background(color, MaterialTheme.shapes.medium),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label.trim().firstOrNull()?.uppercase() ?: "T",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun StatusPill(label: String) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraLarge) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

@Composable
private fun MetadataLine(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun HorizontalPaneDivider() {
  Box(
    Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
  )
}

private fun titleFor(state: FoundationUiState): String = when (state.compactDestination) {
  CompactDestination.ENVIRONMENTS -> "T3 Code Compose"
  CompactDestination.PROJECTS -> "Projects"
  CompactDestination.THREADS -> state.selectedProject?.title ?: "Threads"
  CompactDestination.THREAD_DETAIL -> state.selectedThread?.title ?: "Thread"
}

private fun environmentStatusText(environment: EnvironmentRowUi): String = when {
  environment.reachability == EnvironmentReachabilityUi.REVOKED -> "Pairing required"
  environment.source == EnvironmentSourceUi.RECONNECTING &&
    environment.freshness == EnvironmentFreshnessUi.FRESH -> "Current data / reconnecting"
  environment.reachability == EnvironmentReachabilityUi.OFFLINE &&
    environment.freshness != EnvironmentFreshnessUi.NONE -> "Offline / last-known data"
  environment.reachability == EnvironmentReachabilityUi.OFFLINE -> "Offline"
  environment.source == EnvironmentSourceUi.LIVE -> "Live"
  environment.source == EnvironmentSourceUi.PASSIVE -> "Available / background refresh"
  environment.source == EnvironmentSourceUi.LOADING -> "Loading"
  environment.freshness == EnvironmentFreshnessUi.LAST_KNOWN -> "Restored"
  else -> "Checking connection"
}

@Composable
private fun environmentStatusColor(environment: EnvironmentRowUi): Color = when {
  environment.reachability == EnvironmentReachabilityUi.REVOKED -> MaterialTheme.colorScheme.error
  environment.reachability == EnvironmentReachabilityUi.OFFLINE -> MaterialTheme.colorScheme.error
  environment.source == EnvironmentSourceUi.RECONNECTING -> MaterialTheme.colorScheme.tertiary
  else -> MaterialTheme.colorScheme.primary
}

private fun emptyProjectMessage(environment: EnvironmentRowUi): Pair<String, String> = when {
  environment.source == EnvironmentSourceUi.LOADING ->
    "Loading projects" to "Waiting for the first environment snapshot."
  environment.reachability == EnvironmentReachabilityUi.REVOKED ->
    "Pairing required" to "Pair this environment again before loading projects."
  environment.reachability == EnvironmentReachabilityUi.OFFLINE ->
    "No saved projects" to "This environment is offline and has no last-known project rows."
  else -> "No projects" to "This environment has no projects in its current shell snapshot."
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 1100, heightDp = 720)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 390, heightDp = 844)
@Composable
private fun FoundationScreenPreview() {
  T3CodeComposeTheme {
    Surface {
      FoundationScreen(sampleState(), NoOpFoundationActions)
    }
  }
}

private fun sampleState(): FoundationUiState = FoundationUiState(
  initializing = false,
  environments = listOf(
    EnvironmentRowUi(
      environmentId = "sample-environment",
      label = "Studio",
      safeEndpoint = "http://10.0.2.2:13773/",
      isActive = true,
      reachability = EnvironmentReachabilityUi.REACHABLE,
      source = EnvironmentSourceUi.LIVE,
      freshness = EnvironmentFreshnessUi.FRESH,
      safeError = null,
      projectCount = 1,
      threadCount = 1,
      badgeSeed = 17,
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
    ),
  ),
  activeEnvironmentId = "sample-environment",
  projects = listOf(
    ProjectRowUi(
      uiId = "project:18:sample-environmentproject-a",
      environmentId = "sample-environment",
      environmentLabel = "Studio",
      environmentReachability = EnvironmentReachabilityUi.REACHABLE,
      environmentFreshness = EnvironmentFreshnessUi.FRESH,
      wireId = "project-a",
      title = "T3 Code",
      lifecycle = "active",
      status = "ready",
      archived = false,
      provisional = false,
      badgeSeed = 29,
    ),
  ),
  compactDestination = CompactDestination.PROJECTS,
)
