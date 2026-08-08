package com.t3tools.t3code.compose.app

import com.t3tools.t3code.compose.core.data.CorruptEnvironmentStateFailure
import com.t3tools.t3code.compose.core.data.CredentialUnavailableFailure
import com.t3tools.t3code.compose.core.data.EntityAddress
import com.t3tools.t3code.compose.core.data.EnvironmentCatalogState
import com.t3tools.t3code.compose.core.data.EnvironmentReachability
import com.t3tools.t3code.compose.core.data.PersistenceTransactionFailure
import com.t3tools.t3code.compose.core.data.RouteResolution
import com.t3tools.t3code.compose.core.data.ScopedEntityId
import com.t3tools.t3code.compose.core.data.ShellAggregateState
import com.t3tools.t3code.compose.core.data.ShellDataFreshness
import com.t3tools.t3code.compose.core.data.ShellSourceState
import com.t3tools.t3code.compose.core.protocol.AuthorizationRejectedFailure
import com.t3tools.t3code.compose.core.protocol.CancelledFailure
import com.t3tools.t3code.compose.core.protocol.LocalNetworkPermissionDeniedFailure
import com.t3tools.t3code.compose.core.protocol.MalformedInputFailure
import com.t3tools.t3code.compose.core.protocol.ProtocolFailure
import com.t3tools.t3code.compose.core.protocol.ProtocolViolationFailure
import com.t3tools.t3code.compose.core.protocol.ServerRejectedFailure
import com.t3tools.t3code.compose.core.protocol.TimeoutFailure
import com.t3tools.t3code.compose.core.protocol.TransportFailure
import com.t3tools.t3code.compose.core.protocol.UnreachableFailure
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException

internal class SensitivePairingInput private constructor(private val value: String) {
  fun reveal(): String = value

  override fun toString(): String = "<redacted-pairing-input>"

  companion object {
    fun from(value: String): SensitivePairingInput = SensitivePairingInput(value.trim())
  }
}

internal enum class PairingFailureKind {
  MALFORMED,
  AUTHORIZATION_REJECTED,
  LOCAL_NETWORK_PERMISSION,
  UNREACHABLE,
  TIMEOUT,
  CANCELLED,
  SERVER_REJECTED,
  TRANSPORT,
  REVOKED,
  PERSISTENCE,
  INCOMPATIBLE_RESPONSE,
  UNKNOWN,
}

internal enum class PairingRecoveryAction {
  EDIT,
  RETRY,
  OPEN_SETTINGS,
  PAIR_AGAIN,
}

internal data class PairingFailureUi(
  val kind: PairingFailureKind,
  val title: String,
  val message: String,
  val action: PairingRecoveryAction,
  val traceId: String? = null,
)

internal sealed interface PairingStatusUi {
  data object Idle : PairingStatusUi

  data class Pairing(val safeTarget: String?) : PairingStatusUi

  data class Paired(val environmentLabel: String) : PairingStatusUi

  data class Failed(val failure: PairingFailureUi) : PairingStatusUi
}

internal data class PairingPanelUi(
  val visible: Boolean = false,
  val draftRevision: Long = 0L,
  val draft: SensitivePairingInput? = null,
  val status: PairingStatusUi = PairingStatusUi.Idle,
)

internal enum class EnvironmentReachabilityUi {
  UNKNOWN,
  REACHABLE,
  OFFLINE,
  REVOKED,
}

internal enum class EnvironmentSourceUi {
  RESTORED,
  LOADING,
  PASSIVE,
  LIVE,
  RECONNECTING,
  RELEASED,
}

internal enum class EnvironmentFreshnessUi {
  NONE,
  LAST_KNOWN,
  FRESH,
}

internal data class EnvironmentRowUi(
  val environmentId: String,
  val label: String,
  val safeEndpoint: String,
  val isActive: Boolean,
  val reachability: EnvironmentReachabilityUi,
  val source: EnvironmentSourceUi,
  val freshness: EnvironmentFreshnessUi,
  val safeError: String?,
  val projectCount: Int,
  val threadCount: Int,
  val badgeSeed: Int,
  val capabilities: EnvironmentCapabilitiesUi,
)

internal data class EnvironmentCapabilitiesUi(
  val repositoryIdentity: Boolean,
  val connectionProbe: Boolean?,
  val threadSettlement: Boolean?,
  val threadSnooze: Boolean?,
  val threadPinning: Boolean?,
  val threadTitleRegeneration: Boolean?,
  val serverSelfUpdate: String?,
  val serverSelfUpdateProgress: Boolean?,
)

internal data class ProjectRowUi(
  val uiId: String,
  val environmentId: String,
  val environmentLabel: String,
  val environmentReachability: EnvironmentReachabilityUi,
  val environmentFreshness: EnvironmentFreshnessUi,
  val wireId: String,
  val title: String,
  val lifecycle: String?,
  val status: String?,
  val archived: Boolean,
  val provisional: Boolean,
  val badgeSeed: Int,
)

internal data class ThreadRowUi(
  val uiId: String,
  val environmentId: String,
  val wireId: String,
  val projectUiId: String,
  val projectWireId: String,
  val title: String,
  val lifecycle: String?,
  val status: String?,
  val archived: Boolean,
  val provisional: Boolean,
  val interactionMode: String?,
)

internal enum class CompactDestination {
  ENVIRONMENTS,
  PROJECTS,
  THREADS,
  THREAD_DETAIL,
}

internal data class FoundationUiState(
  val initializing: Boolean = true,
  val globalError: String? = null,
  val globalMessage: String? = null,
  val environments: List<EnvironmentRowUi> = emptyList(),
  val activeEnvironmentId: String? = null,
  val projects: List<ProjectRowUi> = emptyList(),
  val threads: List<ThreadRowUi> = emptyList(),
  val selectedProjectUiId: String? = null,
  val selectedThreadUiId: String? = null,
  val compactDestination: CompactDestination = CompactDestination.ENVIRONMENTS,
  val busyEnvironmentId: String? = null,
  val pairing: PairingPanelUi = PairingPanelUi(),
) {
  val activeEnvironment: EnvironmentRowUi?
    get() = environments.firstOrNull { it.environmentId == activeEnvironmentId }

  val selectedProject: ProjectRowUi?
    get() = projects.firstOrNull { it.uiId == selectedProjectUiId }

  val selectedThread: ThreadRowUi?
    get() = threads.firstOrNull { it.uiId == selectedThreadUiId }
}

internal object FoundationUiMapper {
  fun map(
    catalog: EnvironmentCatalogState,
    aggregate: ShellAggregateState,
    selectedProjectUiId: String?,
    selectedThreadUiId: String?,
    requestedDestination: CompactDestination,
  ): FoundationUiState {
    val environments = catalog.environments.map { saved ->
      val shell = aggregate.environments[saved.environmentId]
      EnvironmentRowUi(
        environmentId = saved.environmentId,
        label = saved.label,
        safeEndpoint = saved.httpBaseUrl,
        isActive = saved.environmentId == catalog.activeEnvironmentId,
        reachability = shell?.reachability.toUi(),
        source = shell?.source.toUi(),
        freshness = shell?.freshness.toUi(),
        safeError = shell?.safeError,
        projectCount = shell?.projects?.size ?: 0,
        threadCount = shell?.threads?.size ?: 0,
        badgeSeed = stableSeed(saved.environmentId),
        capabilities = saved.descriptor.capabilities.let { capabilities ->
          EnvironmentCapabilitiesUi(
            repositoryIdentity = capabilities.repositoryIdentity,
            connectionProbe = capabilities.connectionProbe,
            threadSettlement = capabilities.threadSettlement,
            threadSnooze = capabilities.threadSnooze,
            threadPinning = capabilities.threadPinning,
            threadTitleRegeneration = capabilities.threadTitleRegeneration,
            serverSelfUpdate = capabilities.serverSelfUpdate,
            serverSelfUpdateProgress = capabilities.serverSelfUpdateProgress,
          )
        },
      )
    }.sortedWith(compareByDescending<EnvironmentRowUi> { it.isActive }.thenBy { it.label.lowercase() })

    val environmentsById = environments.associateBy(EnvironmentRowUi::environmentId)
    val projects = aggregate.projects.values.mapNotNull { row ->
      val environment = environmentsById[row.environmentId] ?: return@mapNotNull null
      ProjectRowUi(
        uiId = row.uiId.value,
        environmentId = row.environmentId,
        environmentLabel = environment.label,
        environmentReachability = environment.reachability,
        environmentFreshness = environment.freshness,
        wireId = row.wireId,
        title = row.title,
        lifecycle = row.lifecycle,
        status = row.status,
        archived = row.archived,
        provisional = row.provisional,
        badgeSeed = stableSeed(row.uiId.value),
      )
    }.sortedWith(
      compareBy<ProjectRowUi> { it.title.lowercase() }
        .thenBy { it.environmentLabel.lowercase() }
        .thenBy { it.uiId },
    )

    val threads = aggregate.threads.values.map { row ->
      ThreadRowUi(
        uiId = row.uiId.value,
        environmentId = row.environmentId,
        wireId = row.wireId,
        projectUiId = row.projectUiId.value,
        projectWireId = row.projectWireId,
        title = row.title,
        lifecycle = row.lifecycle,
        status = row.status,
        archived = row.archived,
        provisional = row.provisional,
        interactionMode = row.interactionMode,
      )
    }.sortedWith(
      compareBy<ThreadRowUi> { it.environmentId }
        .thenBy { it.title.lowercase() }
        .thenBy { it.uiId },
    )

    val activeId = catalog.activeEnvironmentId ?: environments.firstOrNull()?.environmentId
    val normalizedProjectId = selectedProjectUiId
      ?.takeIf { candidate -> projects.any { it.uiId == candidate } }
    val projectThreads = threads.filter {
      it.environmentId == activeId && it.projectUiId == normalizedProjectId
    }
    val normalizedThreadId = selectedThreadUiId
      ?.takeIf { candidate -> projectThreads.any { it.uiId == candidate } }
    val destination = when (requestedDestination) {
      CompactDestination.ENVIRONMENTS -> CompactDestination.ENVIRONMENTS
      CompactDestination.PROJECTS -> if (activeId == null) {
        CompactDestination.ENVIRONMENTS
      } else {
        CompactDestination.PROJECTS
      }
      CompactDestination.THREADS -> if (normalizedProjectId == null) {
        if (activeId == null) CompactDestination.ENVIRONMENTS else CompactDestination.PROJECTS
      } else {
        CompactDestination.THREADS
      }
      CompactDestination.THREAD_DETAIL -> when {
        normalizedThreadId != null -> CompactDestination.THREAD_DETAIL
        normalizedProjectId != null -> CompactDestination.THREADS
        activeId != null -> CompactDestination.PROJECTS
        else -> CompactDestination.ENVIRONMENTS
      }
    }

    return FoundationUiState(
      initializing = false,
      environments = environments,
      activeEnvironmentId = activeId,
      projects = projects,
      threads = projectThreads,
      selectedProjectUiId = normalizedProjectId,
      selectedThreadUiId = normalizedThreadId,
      compactDestination = destination,
    )
  }

  private fun EnvironmentReachability?.toUi(): EnvironmentReachabilityUi = when (this) {
    EnvironmentReachability.REACHABLE -> EnvironmentReachabilityUi.REACHABLE
    EnvironmentReachability.UNREACHABLE -> EnvironmentReachabilityUi.OFFLINE
    EnvironmentReachability.REVOKED -> EnvironmentReachabilityUi.REVOKED
    EnvironmentReachability.UNKNOWN, null -> EnvironmentReachabilityUi.UNKNOWN
  }

  private fun ShellSourceState?.toUi(): EnvironmentSourceUi = when (this) {
    ShellSourceState.LOADING -> EnvironmentSourceUi.LOADING
    ShellSourceState.PASSIVE -> EnvironmentSourceUi.PASSIVE
    ShellSourceState.LIVE -> EnvironmentSourceUi.LIVE
    ShellSourceState.RECONNECTING -> EnvironmentSourceUi.RECONNECTING
    ShellSourceState.RELEASED -> EnvironmentSourceUi.RELEASED
    ShellSourceState.RESTORED, null -> EnvironmentSourceUi.RESTORED
  }

  private fun ShellDataFreshness?.toUi(): EnvironmentFreshnessUi = when (this) {
    ShellDataFreshness.LAST_KNOWN -> EnvironmentFreshnessUi.LAST_KNOWN
    ShellDataFreshness.FRESH -> EnvironmentFreshnessUi.FRESH
    ShellDataFreshness.NONE, null -> EnvironmentFreshnessUi.NONE
  }

  private fun stableSeed(value: String): Int = value.fold(0x45D9F3B) { result, character ->
    result * 31 + character.code
  }
}

internal object PairingFailurePresenter {
  fun present(error: Throwable): PairingFailureUi = when (error) {
    is MalformedInputFailure -> PairingFailureUi(
      PairingFailureKind.MALFORMED,
      "Check the pairing details",
      error.safeMessage,
      PairingRecoveryAction.EDIT,
    )
    is AuthorizationRejectedFailure -> PairingFailureUi(
      PairingFailureKind.AUTHORIZATION_REJECTED,
      "Pairing code rejected",
      error.safeMessage,
      PairingRecoveryAction.EDIT,
      safeTraceId(error.traceId),
    )
    is LocalNetworkPermissionDeniedFailure -> PairingFailureUi(
      PairingFailureKind.LOCAL_NETWORK_PERMISSION,
      "Local network access is off",
      "Allow local network access for T3 Compose, then try again.",
      PairingRecoveryAction.OPEN_SETTINGS,
    )
    is UnreachableFailure -> PairingFailureUi(
      PairingFailureKind.UNREACHABLE,
      "Environment unavailable",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
    )
    is TimeoutFailure -> PairingFailureUi(
      PairingFailureKind.TIMEOUT,
      "Environment took too long",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
    )
    is CancelledFailure -> PairingFailureUi(
      PairingFailureKind.CANCELLED,
      "Pairing cancelled",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
    )
    is CancellationException -> PairingFailureUi(
      PairingFailureKind.CANCELLED,
      "Pairing cancelled",
      "The pairing attempt was cancelled.",
      PairingRecoveryAction.RETRY,
    )
    is ServerRejectedFailure -> PairingFailureUi(
      PairingFailureKind.SERVER_REJECTED,
      "Environment rejected pairing",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
      safeTraceId(error.traceId),
    )
    is TransportFailure -> PairingFailureUi(
      PairingFailureKind.TRANSPORT,
      "Secure connection failed",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
    )
    is CredentialUnavailableFailure -> PairingFailureUi(
      PairingFailureKind.REVOKED,
      "Pair again",
      error.message.orEmpty(),
      PairingRecoveryAction.PAIR_AGAIN,
    )
    is PersistenceTransactionFailure -> PairingFailureUi(
      PairingFailureKind.PERSISTENCE,
      "Environment was not saved",
      error.message.orEmpty(),
      PairingRecoveryAction.RETRY,
    )
    is CorruptEnvironmentStateFailure -> PairingFailureUi(
      PairingFailureKind.PERSISTENCE,
      "Saved environments could not be read",
      error.message.orEmpty(),
      PairingRecoveryAction.PAIR_AGAIN,
    )
    is ProtocolViolationFailure -> PairingFailureUi(
      PairingFailureKind.INCOMPATIBLE_RESPONSE,
      "Environment response is incompatible",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
    )
    is ProtocolFailure -> PairingFailureUi(
      PairingFailureKind.UNKNOWN,
      "Connection failed",
      error.safeMessage,
      PairingRecoveryAction.RETRY,
    )
    else -> PairingFailureUi(
      PairingFailureKind.UNKNOWN,
      "Connection failed",
      "T3 Compose could not finish the request.",
      PairingRecoveryAction.RETRY,
    )
  }

  private fun safeTraceId(value: String?): String? = value
    ?.take(128)
    ?.takeIf { trace -> trace.isNotBlank() && trace.all { it.isLetterOrDigit() || it in "-_.:" } }
}

internal sealed interface PairingRouteResult {
  data class Accepted(val input: SensitivePairingInput) : PairingRouteResult

  data class Rejected(val reason: String) : PairingRouteResult
}

internal object PairingRouteIntake {
  fun parse(uriValue: String, expectedScheme: String): PairingRouteResult {
    val uri = runCatching { URI(uriValue) }.getOrNull()
      ?: return PairingRouteResult.Rejected("The pairing route is malformed.")
    if (!uri.scheme.equals(expectedScheme, ignoreCase = true) || !uri.host.equals("pair", true)) {
      return PairingRouteResult.Rejected("The pairing route is not trusted by this app.")
    }
    if (uri.userInfo != null || uri.fragment != null) {
      return PairingRouteResult.Rejected("The pairing route uses an unsupported form.")
    }
    val pairingUrl = parseQuery(uri.rawQuery)
      .firstOrNull { it.first.equals("pairingUrl", ignoreCase = true) }
      ?.second
      ?.trim()
      .orEmpty()
    if (pairingUrl.isEmpty()) {
      return PairingRouteResult.Rejected("The pairing route is missing pairingUrl.")
    }
    return PairingRouteResult.Accepted(SensitivePairingInput.from(pairingUrl))
  }

  private fun parseQuery(rawQuery: String?): List<Pair<String, String>> {
    if (rawQuery.isNullOrEmpty()) return emptyList()
    return runCatching {
      rawQuery.split('&').map { entry ->
        val separator = entry.indexOf('=')
        val name = if (separator < 0) entry else entry.substring(0, separator)
        val value = if (separator < 0) "" else entry.substring(separator + 1)
        URLDecoder.decode(name, StandardCharsets.UTF_8.name()) to
          URLDecoder.decode(value, StandardCharsets.UTF_8.name())
      }
    }.getOrDefault(emptyList())
  }
}

internal object FoundationSelectionResolver {
  fun project(state: ShellAggregateState, uiId: String): EntityAddress? =
    state.resolveProject(scopedId(uiId)).addressOrNull()

  fun thread(state: ShellAggregateState, uiId: String): EntityAddress? =
    state.resolveThread(scopedId(uiId)).addressOrNull()

  private fun RouteResolution.addressOrNull(): EntityAddress? = when (this) {
    is RouteResolution.Resolved -> route.address
    is RouteResolution.OwnerUnavailable -> address
    is RouteResolution.Ambiguous, RouteResolution.NotFound -> null
  }
}

internal fun scopedId(value: String): ScopedEntityId = ScopedEntityId(value)
