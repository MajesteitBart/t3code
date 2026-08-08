package com.t3tools.t3code.compose.core.data

import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.t3tools.t3code.compose.core.protocol.ContractJson
import com.t3tools.t3code.compose.core.protocol.DirectPairingResult
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentCapabilities
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatform
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformArch
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentPlatformOs
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CoreDataPersistenceInstrumentedTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val databaseName = "core-data-persistence-test.db"

  @Before
  public fun resetState() {
    context.deleteDatabase(databaseName)
    context.deleteSharedPreferences(PROTECTED_PREFERENCES)
    deleteTestKey()
  }

  @After
  public fun cleanState() {
    context.deleteDatabase(databaseName)
    context.deleteSharedPreferences(PROTECTED_PREFERENCES)
    deleteTestKey()
  }

  @Test
  public fun protectedCredentialSurvivesRepositoryRecreationAndMissingKeyFailsClosed(): Unit = runBlocking {
    val secret = "synthetic-direct-secret"
    val first = AndroidKeystoreCredentialRepository(context, TEST_KEY_ALIAS)
    first.write("environment-a", EnvironmentCredential.directBearer(RedactedSecret.from(secret)))

    val storedValues = context.getSharedPreferences(PROTECTED_PREFERENCES, 0).all.values
      .filterIsInstance<String>()
    assertEquals(1, storedValues.size)
    assertFalse(storedValues.single().contains(secret))

    val swappedKey = Base64.encodeToString(
      "environment-b".encodeToByteArray(),
      Base64.NO_WRAP or Base64.URL_SAFE,
    )
    assertTrue(
      context.getSharedPreferences(PROTECTED_PREFERENCES, 0)
        .edit()
        .putString(swappedKey, storedValues.single())
        .commit(),
    )
    val swapped = captureCredentialFailure { first.read("environment-b") }
    assertEquals(CredentialUnavailableReason.CORRUPT_CIPHERTEXT, swapped.reason)

    val recreated = AndroidKeystoreCredentialRepository(context, TEST_KEY_ALIAS)
    assertEquals(secret, recreated.read("environment-a")?.secret?.reveal())

    deleteTestKey()
    val failure = captureCredentialFailure { recreated.read("environment-a") }
    assertEquals(CredentialUnavailableReason.MISSING_KEY_MATERIAL, failure.reason)
  }

  @Test
  public fun processRecreationRestoresTwoCredentialsActiveSelectionAndCollidingScopedRows(): Unit =
    runBlocking {
    val first = EnvironmentDatabaseFactory.create(context, databaseName)
    val firstCredentials = AndroidKeystoreCredentialRepository(context, TEST_KEY_ALIAS)
    val coordinator = EnvironmentPersistenceCoordinator(firstCredentials, first)
    val firstPairing = pairing("environment-a", "first-process-secret")
    val secondPairing = pairing("environment-b", "second-process-secret")
    coordinator.savePairing(firstPairing, makeActive = true)
    coordinator.savePairing(secondPairing, makeActive = true)
    val firstShell = collidingShellSnapshot(41, "First restored project")
    val secondShell = collidingShellSnapshot(42, "Second restored project")
    first.saveLastKnownShell("environment-a", firstShell)
    first.saveLastKnownShell("environment-b", secondShell)
    first.close()

    val recreated = EnvironmentDatabaseFactory.create(context, databaseName)
    val state = recreated.read()
    val recreatedCredentials = AndroidKeystoreCredentialRepository(context, TEST_KEY_ALIAS)
    recreated.close()

    assertEquals(listOf("environment-a", "environment-b"), state.environments.map { it.environmentId })
    assertEquals("environment-b", state.activeEnvironmentId)
    assertEquals(firstShell, state.lastKnownShell["environment-a"])
    assertEquals(secondShell, state.lastKnownShell["environment-b"])
    assertEquals("first-process-secret", recreatedCredentials.read("environment-a")?.secret?.reveal())
    assertEquals("second-process-secret", recreatedCredentials.read("environment-b")?.secret?.reveal())

    val restored = state.environments.fold(ShellAggregateState()) { current, environment ->
      ShellReducer.reduce(
        current,
        ShellMutation.RegisterEnvironment(environment, state.lastKnownShell[environment.environmentId]),
      )
    }
    assertEquals(2, restored.projects.size)
    assertEquals(2, restored.threads.size)
    assertTrue(restored.resolveRaw(ScopedEntityKind.PROJECT, "colliding-project") is RouteResolution.Ambiguous)
    assertTrue(restored.resolveRaw(ScopedEntityKind.THREAD, "colliding-thread") is RouteResolution.Ambiguous)
  }

  @Test
  public fun versionOneCatalogMigratesAndCorruptDescriptorFailsTyped(): Unit = runBlocking {
    createVersionOneDatabase()

    val migrated = EnvironmentDatabaseFactory.create(context, databaseName)
    val state = migrated.read()
    assertEquals("environment-a", state.activeEnvironmentId)
    assertTrue(state.lastKnownShell.isEmpty())
    migrated.saveLastKnownShell("environment-a", shellSnapshot(3))
    migrated.close()

    SQLiteDatabase.openDatabase(
      context.getDatabasePath(databaseName).path,
      null,
      SQLiteDatabase.OPEN_READWRITE,
    ).use { database ->
      database.execSQL(
        "UPDATE environments SET descriptorJson = ? WHERE environmentId = ?",
        arrayOf("not-json", "environment-a"),
      )
    }

    val corrupt = EnvironmentDatabaseFactory.create(context, databaseName)
    try {
      captureCorruptFailure { corrupt.read() }
    } finally {
      corrupt.close()
    }
  }

  @Test
  public fun coordinatedRemovalIsEnvironmentScopedAndSelectsTheRemainingEnvironment(): Unit = runBlocking {
    val catalog = EnvironmentDatabaseFactory.create(context, databaseName)
    val credentials = AndroidKeystoreCredentialRepository(context, TEST_KEY_ALIAS)
    val coordinator = EnvironmentPersistenceCoordinator(credentials, catalog)
    val first = pairing("environment-a", "first-secret")
    val second = pairing("environment-b", "second-secret")
    coordinator.savePairing(first, makeActive = true)
    coordinator.savePairing(second, makeActive = false)
    catalog.saveLastKnownShell("environment-a", shellSnapshot(1))
    val secondShell = shellSnapshot(2)
    catalog.saveLastKnownShell("environment-b", secondShell)

    coordinator.remove("environment-a")
    val remaining = catalog.read()
    catalog.close()

    assertEquals(listOf("environment-b"), remaining.environments.map(SavedEnvironment::environmentId))
    assertEquals("environment-b", remaining.activeEnvironmentId)
    assertNull(remaining.lastKnownShell["environment-a"])
    assertEquals(secondShell, remaining.lastKnownShell["environment-b"])
    assertNull(credentials.read("environment-a"))
    assertEquals("second-secret", credentials.read("environment-b")?.secret?.reveal())
  }

  private fun createVersionOneDatabase() {
    val descriptorJson = ContractJson.format.encodeToString(descriptor("environment-a"))
    SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { database ->
      database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS environments (
          environmentId TEXT NOT NULL,
          label TEXT NOT NULL,
          httpBaseUrl TEXT NOT NULL,
          webSocketBaseUrl TEXT NOT NULL,
          descriptorJson TEXT NOT NULL,
          PRIMARY KEY(environmentId)
        )
        """.trimIndent(),
      )
      database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS application_state (
          singletonId INTEGER NOT NULL,
          activeEnvironmentId TEXT,
          PRIMARY KEY(singletonId)
        )
        """.trimIndent(),
      )
      database.execSQL(
        "INSERT INTO environments VALUES (?, ?, ?, ?, ?)",
        arrayOf(
          "environment-a",
          "Environment environment-a",
          "https://environment-a.test/",
          "wss://environment-a.test/",
          descriptorJson,
        ),
      )
      database.execSQL("INSERT INTO application_state VALUES (1, ?)", arrayOf("environment-a"))
      database.version = 1
    }
  }

  private suspend fun captureCredentialFailure(
    block: suspend () -> Unit,
  ): CredentialUnavailableFailure = try {
    block()
    throw AssertionError("Expected CredentialUnavailableFailure")
  } catch (error: CredentialUnavailableFailure) {
    error
  }

  private suspend fun captureCorruptFailure(
    block: suspend () -> Unit,
  ): CorruptEnvironmentStateFailure = try {
    block()
    throw AssertionError("Expected CorruptEnvironmentStateFailure")
  } catch (error: CorruptEnvironmentStateFailure) {
    error
  }

  private fun pairing(environmentId: String, secret: String): DirectPairingResult = DirectPairingResult(
    descriptor = descriptor(environmentId),
    httpBaseUrl = "https://$environmentId.test/",
    webSocketBaseUrl = "wss://$environmentId.test/",
    accessCredential = RedactedSecret.from(secret),
    expiresInSeconds = 3600.0,
    scopes = listOf("environment:read"),
  )

  private fun savedEnvironment(environmentId: String): SavedEnvironment = SavedEnvironment(
    environmentId = environmentId,
    label = "Environment $environmentId",
    httpBaseUrl = "https://$environmentId.test/",
    webSocketBaseUrl = "wss://$environmentId.test/",
    descriptor = descriptor(environmentId),
  )

  private fun descriptor(environmentId: String): ExecutionEnvironmentDescriptor =
    ExecutionEnvironmentDescriptor(
      environmentId = environmentId,
      label = "Environment $environmentId",
      platform = ExecutionEnvironmentPlatform(
        os = ExecutionEnvironmentPlatformOs.LINUX,
        arch = ExecutionEnvironmentPlatformArch.X64,
      ),
      serverVersion = "test",
      capabilities = ExecutionEnvironmentCapabilities(repositoryIdentity = true),
    )

  private fun shellSnapshot(sequence: Long): OrchestrationShellSnapshot = OrchestrationShellSnapshot(
    snapshotSequence = sequence,
    projects = emptyList(),
    threads = emptyList(),
    updatedAt = "2026-08-08T00:00:00Z",
  )

  private fun collidingShellSnapshot(
    sequence: Long,
    projectTitle: String,
  ): OrchestrationShellSnapshot = OrchestrationShellSnapshot(
    snapshotSequence = sequence,
    projects = listOf(
      buildJsonObject {
        put("id", "colliding-project")
        put("title", projectTitle)
      },
    ),
    threads = listOf(
      buildJsonObject {
        put("id", "colliding-thread")
        put("projectId", "colliding-project")
        put("title", "Restored collision thread")
      },
    ),
    updatedAt = "2026-08-08T00:00:00Z",
  )

  private fun deleteTestKey() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (keyStore.containsAlias(TEST_KEY_ALIAS)) keyStore.deleteEntry(TEST_KEY_ALIAS)
  }

  private companion object {
    const val TEST_KEY_ALIAS = "t3-code-compose-instrumentation-test-key"
    const val PROTECTED_PREFERENCES = "protected-environment-credentials"
  }
}
