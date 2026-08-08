package com.t3tools.t3code.compose.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.t3tools.t3code.compose.core.data.AndroidKeystoreCredentialRepository
import com.t3tools.t3code.compose.core.data.CanonicalRefreshRequest
import com.t3tools.t3code.compose.core.data.CanonicalSnapshotRefresher
import com.t3tools.t3code.compose.core.data.CredentialKind
import com.t3tools.t3code.compose.core.data.CredentialUnavailableFailure
import com.t3tools.t3code.compose.core.data.CredentialUnavailableReason
import com.t3tools.t3code.compose.core.data.EnvironmentCatalogState
import com.t3tools.t3code.compose.core.data.EnvironmentConnectionSupervisor
import com.t3tools.t3code.compose.core.data.EnvironmentDatabaseFactory
import com.t3tools.t3code.compose.core.data.EnvironmentPersistenceCoordinator
import com.t3tools.t3code.compose.core.data.RoomEnvironmentCatalogRepository
import com.t3tools.t3code.compose.core.data.ShellAggregateState
import com.t3tools.t3code.compose.core.data.ShellDataFreshness
import com.t3tools.t3code.compose.core.data.ShellStateReconciler
import com.t3tools.t3code.compose.core.protocol.EnvironmentHttpClient
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.PairingUrl
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class FoundationRuntimeStatus(
  val initializing: Boolean = true,
  val globalError: String? = null,
  val globalMessage: String? = null,
  val busyEnvironmentId: String? = null,
)

private data class FoundationNavigationState(
  val projectUiId: String?,
  val threadUiId: String?,
  val destination: CompactDestination,
)

private data class FoundationChromeState(
  val status: FoundationRuntimeStatus,
  val pairing: PairingPanelUi,
)

internal class FoundationViewModel(
  application: Application,
  private val savedStateHandle: SavedStateHandle,
  private val httpClient: EnvironmentHttpClient = EnvironmentHttpClient(),
  private val catalog: RoomEnvironmentCatalogRepository = EnvironmentDatabaseFactory.create(application),
  private val credentials: AndroidKeystoreCredentialRepository =
    AndroidKeystoreCredentialRepository(application),
) : ViewModel() {
  private val persistence = EnvironmentPersistenceCoordinator(credentials, catalog)
  private val catalogState = MutableStateFlow(EMPTY_CATALOG)
  private val runtimeStatus = MutableStateFlow(FoundationRuntimeStatus())
  private val pairingPanel = MutableStateFlow(PairingPanelUi())
  private val actionMutex = Mutex()
  private val selectedProject = savedStateHandle.getStateFlow<String?>(SELECTED_PROJECT_KEY, null)
  private val selectedThread = savedStateHandle.getStateFlow<String?>(SELECTED_THREAD_KEY, null)
  private val compactDestination = savedStateHandle.getStateFlow(
    COMPACT_DESTINATION_KEY,
    CompactDestination.ENVIRONMENTS.name,
  )
  private var pairingJob: Job? = null
  private var lastPairingInput: SensitivePairingInput? = null
  private var desiredForeground = false
  private var bootstrapped = false

  private val reconciler = ShellStateReconciler(
    parentScope = viewModelScope,
    canonicalRefresher = CanonicalSnapshotRefresher(::refreshCanonicalSnapshot),
  )
  private val supervisor = EnvironmentConnectionSupervisor(
    parentScope = viewModelScope,
    credentials = credentials,
  )

  private val navigation = combine(selectedProject, selectedThread, compactDestination) {
      projectUiId,
      threadUiId,
      destination,
    ->
    FoundationNavigationState(
      projectUiId,
      threadUiId,
      CompactDestination.entries.singleOrNull { it.name == destination }
        ?: CompactDestination.ENVIRONMENTS,
    )
  }
  private val chrome = combine(runtimeStatus, pairingPanel, ::FoundationChromeState)

  val uiState: StateFlow<FoundationUiState> = combine(
    catalogState,
    reconciler.state,
    navigation,
    chrome,
  ) { currentCatalog, aggregate, currentNavigation, currentChrome ->
    FoundationUiMapper.map(
      currentCatalog,
      aggregate,
      currentNavigation.projectUiId,
      currentNavigation.threadUiId,
      currentNavigation.destination,
    ).copy(
      initializing = currentChrome.status.initializing,
      globalError = currentChrome.status.globalError,
      globalMessage = currentChrome.status.globalMessage,
      busyEnvironmentId = currentChrome.status.busyEnvironmentId,
      pairing = currentChrome.pairing,
    )
  }.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    FoundationUiState(),
  )

  init {
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      reconciler.collect(supervisor.publications)
    }
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      persistFreshSnapshots(reconciler.state)
    }
    viewModelScope.launch { bootstrap() }
  }

  fun setForeground(value: Boolean) {
    desiredForeground = value
    viewModelScope.launch {
      actionMutex.withLock {
        if (bootstrapped) supervisor.setForeground(value)
      }
    }
  }

  fun showAddEnvironment() {
    pairingPanel.value = PairingPanelUi(
      visible = true,
      draftRevision = pairingPanel.value.draftRevision + 1L,
    )
  }

  fun acceptPairingRoute(uriValue: String) {
    when (val result = PairingRouteIntake.parse(uriValue, BuildConfig.PAIRING_SCHEME)) {
      is PairingRouteResult.Accepted -> {
        lastPairingInput = result.input
        pairingPanel.value = PairingPanelUi(
          visible = true,
          draftRevision = pairingPanel.value.draftRevision + 1L,
          draft = result.input,
        )
      }
      is PairingRouteResult.Rejected -> {
        runtimeStatus.value = runtimeStatus.value.copy(globalMessage = result.reason)
      }
    }
  }

  fun dismissPairing() {
    pairingJob?.cancel()
    pairingJob = null
    lastPairingInput = null
    pairingPanel.value = PairingPanelUi(
      visible = false,
      draftRevision = pairingPanel.value.draftRevision + 1L,
    )
  }

  fun editPairing() {
    pairingPanel.value = pairingPanel.value.copy(status = PairingStatusUi.Idle)
  }

  fun pair(rawInput: String) {
    if (pairingJob?.isActive == true) return
    val sensitiveInput = SensitivePairingInput.from(rawInput)
    lastPairingInput = sensitiveInput
    startPairing(sensitiveInput)
  }

  fun retryPairing() {
    val input = lastPairingInput
    if (input == null) {
      editPairing()
    } else {
      startPairing(input)
    }
  }

  fun cancelPairing() {
    pairingJob?.cancel()
    pairingJob = null
    pairingPanel.value = pairingPanel.value.copy(
      status = PairingStatusUi.Failed(
        PairingFailurePresenter.present(CancellationException()),
      ),
    )
  }

  fun activateEnvironment(environmentId: String) {
    viewModelScope.launch {
      actionMutex.withLock {
        performEnvironmentOperation(environmentId) {
          activateLocked(environmentId)
          clearSelection(CompactDestination.PROJECTS)
        }
      }
    }
  }

  fun removeEnvironment(environmentId: String) {
    viewModelScope.launch {
      actionMutex.withLock {
        performEnvironmentOperation(environmentId) {
          persistence.remove(environmentId)
          supervisor.remove(environmentId)
          reconciler.removeEnvironment(environmentId)
          val restored = catalog.read()
          catalogState.value = restored
          if (
            reconciler.state.value.projects[scopedId(selectedProject.value.orEmpty())]
              ?.environmentId == environmentId ||
            reconciler.state.value.threads[scopedId(selectedThread.value.orEmpty())]
              ?.environmentId == environmentId
          ) {
            clearSelection(
              if (restored.activeEnvironmentId == null) {
                CompactDestination.ENVIRONMENTS
              } else {
                CompactDestination.PROJECTS
              },
            )
          }
        }
      }
    }
  }

  fun retryEnvironment(environmentId: String) {
    viewModelScope.launch {
      actionMutex.withLock {
        performEnvironmentOperation(environmentId) {
          val current = catalogState.value
          supervisor.replaceEnvironments(current.environments, current.activeEnvironmentId)
        }
      }
    }
  }

  fun pairAgain(environmentId: String) {
    val environment = catalogState.value.environments.firstOrNull {
      it.environmentId == environmentId
    }
    pairingPanel.value = PairingPanelUi(
      visible = true,
      draftRevision = pairingPanel.value.draftRevision + 1L,
      status = if (environment == null) {
        PairingStatusUi.Idle
      } else {
        PairingStatusUi.Failed(
          PairingFailureUi(
            PairingFailureKind.REVOKED,
            "Pair ${environment.label} again",
            "Paste a new one-time pairing link for this environment.",
            PairingRecoveryAction.EDIT,
          ),
        )
      },
    )
  }

  fun showEnvironments() {
    savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.ENVIRONMENTS.name
  }

  fun showProjects() {
    savedStateHandle[SELECTED_PROJECT_KEY] = null
    savedStateHandle[SELECTED_THREAD_KEY] = null
    savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.PROJECTS.name
  }

  fun selectProject(uiId: String) {
    viewModelScope.launch {
      actionMutex.withLock {
        val address = FoundationSelectionResolver.project(reconciler.state.value, uiId)
          ?: return@withLock
        if (catalogState.value.activeEnvironmentId != address.environmentId) {
          activateLocked(address.environmentId)
        }
        savedStateHandle[SELECTED_PROJECT_KEY] = address.uiId.value
        savedStateHandle[SELECTED_THREAD_KEY] = null
        savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.THREADS.name
      }
    }
  }

  fun selectThread(uiId: String) {
    viewModelScope.launch {
      actionMutex.withLock {
        val address = FoundationSelectionResolver.thread(reconciler.state.value, uiId)
          ?: return@withLock
        val row = reconciler.state.value.threads[address.uiId] ?: return@withLock
        if (catalogState.value.activeEnvironmentId != address.environmentId) {
          activateLocked(address.environmentId)
        }
        savedStateHandle[SELECTED_PROJECT_KEY] = row.projectUiId.value
        savedStateHandle[SELECTED_THREAD_KEY] = address.uiId.value
        savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.THREAD_DETAIL.name
      }
    }
  }

  fun navigateBack(): Boolean = when (
    CompactDestination.entries.singleOrNull {
      it.name == compactDestination.value
    } ?: CompactDestination.ENVIRONMENTS
  ) {
    CompactDestination.THREAD_DETAIL -> {
      savedStateHandle[SELECTED_THREAD_KEY] = null
      savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.THREADS.name
      true
    }
    CompactDestination.THREADS -> {
      savedStateHandle[SELECTED_PROJECT_KEY] = null
      savedStateHandle[SELECTED_THREAD_KEY] = null
      savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.PROJECTS.name
      true
    }
    CompactDestination.PROJECTS -> {
      savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.ENVIRONMENTS.name
      true
    }
    CompactDestination.ENVIRONMENTS -> false
  }

  fun clearMessage() {
    runtimeStatus.value = runtimeStatus.value.copy(globalMessage = null)
  }

  override fun onCleared() {
    pairingJob?.cancel()
    runBlocking {
      withContext(NonCancellable) {
        supervisor.release()
        reconciler.release()
      }
    }
    catalog.close()
    super.onCleared()
  }

  private suspend fun bootstrap() {
    actionMutex.withLock {
      try {
        supervisor.setForeground(false)
        val restored = catalog.read()
        catalogState.value = restored
        for (environment in restored.environments) {
          reconciler.registerEnvironment(
            environment,
            restored.lastKnownShell[environment.environmentId],
          )
        }
        supervisor.replaceEnvironments(restored.environments, restored.activeEnvironmentId)
        bootstrapped = true
        if (desiredForeground) supervisor.setForeground(true)
        if (restored.environments.isNotEmpty() && compactDestination.value == CompactDestination.ENVIRONMENTS.name) {
          savedStateHandle[COMPACT_DESTINATION_KEY] = CompactDestination.PROJECTS.name
        }
        runtimeStatus.value = FoundationRuntimeStatus(initializing = false)
        if (restored.environments.isEmpty() && !pairingPanel.value.visible) showAddEnvironment()
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        runtimeStatus.value = FoundationRuntimeStatus(
          initializing = false,
          globalError = PairingFailurePresenter.present(error).message,
        )
        pairingPanel.value = PairingPanelUi(visible = true)
      }
    }
  }

  private fun startPairing(input: SensitivePairingInput) {
    if (pairingJob?.isActive == true) return
    val safeTarget = runCatching { PairingUrl.parseFields(input.reveal()).host }.getOrNull()
    pairingPanel.value = pairingPanel.value.copy(
      visible = true,
      draft = input,
      status = PairingStatusUi.Pairing(safeTarget),
    )
    val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
      try {
        val pairing = httpClient.pair(input.reveal(), clientLabel = "T3 Compose Android")
        actionMutex.withLock {
          val saved = persistence.savePairing(pairing, makeActive = true)
          val restored = catalog.read()
          reconciler.registerEnvironment(saved, restored.lastKnownShell[saved.environmentId])
          supervisor.replaceEnvironments(restored.environments, restored.activeEnvironmentId)
          catalogState.value = restored
          clearSelection(CompactDestination.PROJECTS)
        }
        lastPairingInput = null
        pairingPanel.value = pairingPanel.value.copy(
          draftRevision = pairingPanel.value.draftRevision + 1L,
          draft = null,
          status = PairingStatusUi.Paired(pairing.descriptor.label),
        )
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        val presented = PairingFailurePresenter.present(error)
        pairingPanel.value = pairingPanel.value.copy(status = PairingStatusUi.Failed(presented))
      }
    }
    pairingJob = job
    job.invokeOnCompletion {
      if (pairingJob === job) pairingJob = null
    }
    job.start()
  }

  private suspend fun activateLocked(environmentId: String) {
    if (catalogState.value.activeEnvironmentId == environmentId) return
    catalog.activate(environmentId)
    supervisor.activate(environmentId)
    catalogState.value = catalogState.value.copy(activeEnvironmentId = environmentId)
  }

  private suspend fun performEnvironmentOperation(
    environmentId: String,
    operation: suspend () -> Unit,
  ) {
    runtimeStatus.value = runtimeStatus.value.copy(
      busyEnvironmentId = environmentId,
      globalMessage = null,
    )
    try {
      operation()
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      runtimeStatus.value = runtimeStatus.value.copy(
        globalMessage = PairingFailurePresenter.present(error).message,
      )
    } finally {
      runtimeStatus.value = runtimeStatus.value.copy(busyEnvironmentId = null)
    }
  }

  private suspend fun refreshCanonicalSnapshot(request: CanonicalRefreshRequest): OrchestrationShellSnapshot {
    val environment = catalogState.value.environments.singleOrNull {
      it.environmentId == request.permit.environmentId
    } ?: throw IllegalStateException("The requested environment is no longer saved.")
    val credential = credentials.read(environment.environmentId)
      ?: throw CredentialUnavailableFailure(CredentialUnavailableReason.MISSING_KEY_MATERIAL)
    if (credential.kind != CredentialKind.DIRECT_BEARER) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE)
    }
    return httpClient.shellSnapshot(
      environment.httpBaseUrl,
      credential.secret,
      request.timeout,
    )
  }

  private suspend fun persistFreshSnapshots(state: StateFlow<ShellAggregateState>) {
    val persistedSequences = mutableMapOf<String, Long>()
    state.collect { aggregate ->
      persistedSequences.keys.retainAll(aggregate.environments.keys)
      for ((environmentId, environmentState) in aggregate.environments) {
        val sequence = environmentState.snapshotSequence ?: continue
        if (environmentState.freshness != ShellDataFreshness.FRESH) continue
        if (persistedSequences[environmentId] == sequence) continue
        val updatedAt = environmentState.updatedAt ?: continue
        val snapshot = OrchestrationShellSnapshot(
          snapshotSequence = sequence,
          projects = environmentState.projects.values.sortedBy { it.uiId.value }.map { it.raw },
          threads = environmentState.threads.values.sortedBy { it.uiId.value }.map { it.raw },
          updatedAt = updatedAt,
        )
        try {
          catalog.saveLastKnownShell(environmentId, snapshot)
          persistedSequences[environmentId] = sequence
        } catch (error: Throwable) {
          if (error is CancellationException) throw error
          runtimeStatus.value = runtimeStatus.value.copy(
            globalMessage = "Current environment data could not be saved for offline use.",
          )
        }
      }
    }
  }

  private fun clearSelection(destination: CompactDestination) {
    savedStateHandle[SELECTED_PROJECT_KEY] = null
    savedStateHandle[SELECTED_THREAD_KEY] = null
    savedStateHandle[COMPACT_DESTINATION_KEY] = destination.name
  }

  companion object {
    private const val SELECTED_PROJECT_KEY = "selected-project-ui-id"
    private const val SELECTED_THREAD_KEY = "selected-thread-ui-id"
    private const val COMPACT_DESTINATION_KEY = "compact-destination"
    private val EMPTY_CATALOG = EnvironmentCatalogState(emptyList(), null, emptyMap())

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        FoundationViewModel(application, createSavedStateHandle())
      }
    }
  }
}
