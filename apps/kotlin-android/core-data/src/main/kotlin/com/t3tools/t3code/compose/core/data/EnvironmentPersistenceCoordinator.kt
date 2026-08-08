package com.t3tools.t3code.compose.core.data

import com.t3tools.t3code.compose.core.protocol.DirectPairingResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

public enum class PersistencePhase {
  READ_PREVIOUS_CREDENTIAL,
  WRITE_CREDENTIAL,
  WRITE_CATALOG,
  REMOVE_CATALOG,
  DELETE_CREDENTIAL,
}

public class PersistenceTransactionFailure(
  public val phase: PersistencePhase,
  public val initiatingFailure: Throwable,
  public val rollbackFailures: List<Throwable> = emptyList(),
) : IOException(
  buildString {
    append("Environment persistence failed during ")
    append(phase.name.lowercase().replace('_', ' '))
    append('.')
    if (rollbackFailures.isNotEmpty()) append(" Compensation also failed.")
  },
)

/**
 * Coordinates Keystore and Room without pretending they share a transaction. Save is
 * credential-first; removal is catalog-first; each second-step failure has explicit compensation.
 */
public class EnvironmentPersistenceCoordinator(
  private val credentials: CredentialRepository,
  private val catalog: EnvironmentCatalogRepository,
) {
  public suspend fun savePairing(
    pairing: DirectPairingResult,
    makeActive: Boolean = true,
  ): SavedEnvironment {
    val environment = SavedEnvironment.from(pairing)
    val previousCredential = try {
      credentials.read(environment.environmentId)
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      throw PersistenceTransactionFailure(
        PersistencePhase.READ_PREVIOUS_CREDENTIAL,
        error,
      )
    }
    try {
      credentials.write(
        environment.environmentId,
        EnvironmentCredential.directBearer(pairing.accessCredential),
      )
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      throw PersistenceTransactionFailure(PersistencePhase.WRITE_CREDENTIAL, error)
    }

    try {
      catalog.save(environment, makeActive)
    } catch (error: Throwable) {
      val rollbackFailures = withContext(NonCancellable) {
        buildList {
          runCatching {
            if (previousCredential == null) {
              credentials.delete(environment.environmentId)
            } else {
              credentials.write(environment.environmentId, previousCredential)
            }
          }.exceptionOrNull()?.let(::add)
        }
      }
      if (error is CancellationException) {
        rollbackFailures.forEach(error::addSuppressed)
        throw error
      }
      throw PersistenceTransactionFailure(PersistencePhase.WRITE_CATALOG, error, rollbackFailures)
    }
    return environment
  }

  public suspend fun remove(environmentId: String) {
    val removed = try {
      catalog.remove(environmentId)
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      throw PersistenceTransactionFailure(PersistencePhase.REMOVE_CATALOG, error)
    }
    try {
      credentials.delete(environmentId)
    } catch (error: Throwable) {
      val rollbackFailures = withContext(NonCancellable) {
        buildList {
          runCatching { catalog.restore(removed) }.exceptionOrNull()?.let(::add)
        }
      }
      if (error is CancellationException) {
        rollbackFailures.forEach(error::addSuppressed)
        throw error
      }
      throw PersistenceTransactionFailure(PersistencePhase.DELETE_CREDENTIAL, error, rollbackFailures)
    }
  }
}
