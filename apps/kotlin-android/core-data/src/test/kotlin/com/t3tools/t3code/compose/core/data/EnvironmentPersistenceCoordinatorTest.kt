package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.DirectPairingResult
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentCapabilities
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatform
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformArch
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformOs
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

public class EnvironmentPersistenceCoordinatorTest {
  @Test
  public fun saveIsCredentialFirstAndDeletesNewCredentialWhenCatalogFails() = runTest {
    val events = mutableListOf<String>()
    val credentials = FakeCredentialRepository(events)
    val catalog = FakeCatalogRepository(events).apply { saveFailure = IOException("catalog") }
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)

    val failure = captureFailure { coordinator.savePairing(pairing()) }

    assertEquals(PersistencePhase.WRITE_CATALOG, failure.phase)
    assertEquals(listOf("credential-read", "credential-write", "catalog-save", "credential-delete"), events)
    assertNull(credentials.value)
    assertEquals(0, failure.rollbackFailures.size)
  }

  @Test
  public fun saveRestoresPreviousCredentialAndCollectsRollbackFailure() = runTest {
    val events = mutableListOf<String>()
    val previous = EnvironmentCredential.directBearer(RedactedSecret.from("previous-secret"))
    val credentials = FakeCredentialRepository(events).apply {
      value = previous
      failOnWriteNumber = 2
    }
    val initiating = IOException("catalog")
    val catalog = FakeCatalogRepository(events).apply { saveFailure = initiating }
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)

    val failure = captureFailure { coordinator.savePairing(pairing()) }

    assertSame(initiating, failure.initiatingFailure)
    assertEquals(1, failure.rollbackFailures.size)
    assertEquals(
      listOf("credential-read", "credential-write", "catalog-save", "credential-write"),
      events,
    )
  }

  @Test
  public fun removeIsCatalogFirstAndRestoresCatalogSelectionWhenCredentialDeletionFails() = runTest {
    val events = mutableListOf<String>()
    val environment = SavedEnvironment.from(pairing())
    val removed = RemovedEnvironmentState(environment, environment.environmentId, snapshot())
    val credentials = FakeCredentialRepository(events).apply {
      value = EnvironmentCredential.directBearer(RedactedSecret.from("stored-secret"))
      deleteFailure = IOException("delete")
    }
    val catalog = FakeCatalogRepository(events).apply { removedState = removed }
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)

    val failure = captureFailure { coordinator.remove(environment.environmentId) }

    assertEquals(PersistencePhase.DELETE_CREDENTIAL, failure.phase)
    assertEquals(listOf("catalog-remove", "credential-delete", "catalog-restore"), events)
    assertSame(removed, catalog.restoredState)
  }

  @Test
  public fun successfulSaveAndRemovalPreserveRequiredOrdering() = runTest {
    val events = mutableListOf<String>()
    val credentials = FakeCredentialRepository(events)
    val catalog = FakeCatalogRepository(events)
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)

    val saved = coordinator.savePairing(pairing())
    catalog.removedState = RemovedEnvironmentState(saved, saved.environmentId, null)
    coordinator.remove(saved.environmentId)

    assertEquals(
      listOf(
        "credential-read",
        "credential-write",
        "catalog-save",
        "catalog-remove",
        "credential-delete",
      ),
      events,
    )
  }

  @Test
  public fun cancellationDuringCatalogSaveCompensatesThenRemainsCancellation() = runTest {
    val events = mutableListOf<String>()
    val cancellation = CancellationException("cancel catalog save")
    val credentials = FakeCredentialRepository(events)
    val catalog = FakeCatalogRepository(events).apply { saveFailure = cancellation }
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)

    val thrown = captureCancellation { coordinator.savePairing(pairing()) }

    assertSame(cancellation, thrown)
    assertNull(credentials.value)
    assertEquals(
      listOf("credential-read", "credential-write", "catalog-save", "credential-delete"),
      events,
    )
  }

  @Test
  public fun cancellationDuringCredentialDeleteRestoresCatalogThenRemainsCancellation() = runTest {
    val events = mutableListOf<String>()
    val cancellation = CancellationException("cancel credential delete")
    val environment = SavedEnvironment.from(pairing())
    val removed = RemovedEnvironmentState(environment, environment.environmentId, snapshot())
    val credentials = FakeCredentialRepository(events).apply { deleteFailure = cancellation }
    val catalog = FakeCatalogRepository(events).apply { removedState = removed }
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)

    val thrown = captureCancellation { coordinator.remove(environment.environmentId) }

    assertSame(cancellation, thrown)
    assertSame(removed, catalog.restoredState)
    assertEquals(listOf("catalog-remove", "credential-delete", "catalog-restore"), events)
  }

  private suspend fun captureFailure(block: suspend () -> Unit): PersistenceTransactionFailure = try {
    block()
    throw AssertionError("Expected PersistenceTransactionFailure")
  } catch (error: PersistenceTransactionFailure) {
    error
  }

  private suspend fun captureCancellation(block: suspend () -> Unit): CancellationException = try {
    block()
    throw AssertionError("Expected CancellationException")
  } catch (error: CancellationException) {
    error
  }

  private fun pairing(): DirectPairingResult = DirectPairingResult(
    descriptor = descriptor(),
    httpBaseUrl = "https://environment.test/",
    webSocketBaseUrl = "wss://environment.test/",
    accessCredential = RedactedSecret.from("new-direct-secret"),
    expiresInSeconds = 3600.0,
    scopes = listOf("environment:read", "environment:write"),
  )

  private fun descriptor(): ExecutionEnvironmentDescriptor = ExecutionEnvironmentDescriptor(
    environmentId = "environment-a",
    label = "Environment A",
    platform = ExecutionEnvironmentPlatform(
      os = ExecutionEnvironmentPlatformOs.LINUX,
      arch = ExecutionEnvironmentPlatformArch.X64,
    ),
    serverVersion = "test",
    capabilities = ExecutionEnvironmentCapabilities(repositoryIdentity = true),
  )

  private fun snapshot(): OrchestrationShellSnapshot = OrchestrationShellSnapshot(
    snapshotSequence = 7,
    projects = emptyList(),
    threads = emptyList(),
    updatedAt = "2026-08-08T00:00:00Z",
  )

  private class FakeCredentialRepository(
    private val events: MutableList<String>,
  ) : CredentialRepository {
    var value: EnvironmentCredential? = null
    var deleteFailure: Throwable? = null
    var failOnWriteNumber: Int? = null
    private var writes = 0

    override suspend fun read(environmentId: String): EnvironmentCredential? {
      events += "credential-read"
      return value
    }

    override suspend fun write(environmentId: String, credential: EnvironmentCredential) {
      events += "credential-write"
      writes += 1
      if (failOnWriteNumber == writes) throw IOException("credential-write")
      value = credential
    }

    override suspend fun delete(environmentId: String) {
      events += "credential-delete"
      deleteFailure?.let { throw it }
      value = null
    }
  }

  private class FakeCatalogRepository(
    private val events: MutableList<String>,
  ) : EnvironmentCatalogRepository {
    var saveFailure: Throwable? = null
    var removedState = RemovedEnvironmentState(null, null, null)
    var restoredState: RemovedEnvironmentState? = null

    override suspend fun read(): EnvironmentCatalogState =
      EnvironmentCatalogState(emptyList(), null, emptyMap())

    override suspend fun save(environment: SavedEnvironment, makeActive: Boolean) {
      events += "catalog-save"
      saveFailure?.let { throw it }
    }

    override suspend fun activate(environmentId: String) = Unit

    override suspend fun saveLastKnownShell(
      environmentId: String,
      snapshot: OrchestrationShellSnapshot,
    ) = Unit

    override suspend fun remove(environmentId: String): RemovedEnvironmentState {
      events += "catalog-remove"
      return removedState
    }

    override suspend fun restore(removed: RemovedEnvironmentState) {
      events += "catalog-restore"
      restoredState = removed
    }
  }
}
