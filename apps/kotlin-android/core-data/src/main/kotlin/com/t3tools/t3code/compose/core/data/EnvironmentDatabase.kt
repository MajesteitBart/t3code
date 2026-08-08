package com.t3tools.t3code.compose.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.t3tools.t3code.compose.core.protocol.ContractJson
import com.t3tools.t3code.compose.core.protocol.DirectPairingResult
import com.t3tools.t3code.compose.core.protocol.ExecutionEnvironmentDescriptor
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

public data class SavedEnvironment(
  public val environmentId: String,
  public val label: String,
  public val httpBaseUrl: String,
  public val webSocketBaseUrl: String,
  public val descriptor: ExecutionEnvironmentDescriptor,
) {
  public companion object {
    public fun from(pairing: DirectPairingResult): SavedEnvironment = SavedEnvironment(
      environmentId = pairing.descriptor.environmentId,
      label = pairing.descriptor.label,
      httpBaseUrl = pairing.httpBaseUrl,
      webSocketBaseUrl = pairing.webSocketBaseUrl,
      descriptor = pairing.descriptor,
    )
  }
}

public data class SavedShellState(
  public val environmentId: String,
  public val snapshot: OrchestrationShellSnapshot,
)

public data class EnvironmentCatalogState(
  public val environments: List<SavedEnvironment>,
  public val activeEnvironmentId: String?,
  public val lastKnownShell: Map<String, OrchestrationShellSnapshot>,
)

public data class RemovedEnvironmentState(
  public val environment: SavedEnvironment?,
  public val previousActiveEnvironmentId: String?,
  public val lastKnownShell: OrchestrationShellSnapshot?,
)

public interface EnvironmentCatalogRepository {
  public suspend fun read(): EnvironmentCatalogState

  public suspend fun save(environment: SavedEnvironment, makeActive: Boolean)

  public suspend fun activate(environmentId: String)

  public suspend fun saveLastKnownShell(environmentId: String, snapshot: OrchestrationShellSnapshot)

  public suspend fun remove(environmentId: String): RemovedEnvironmentState

  public suspend fun restore(removed: RemovedEnvironmentState)
}

public class CorruptEnvironmentStateFailure(
  cause: Throwable? = null,
) : IOException("Saved environment state is incompatible or corrupt.", cause)

@Entity(tableName = "environments")
internal data class EnvironmentEntity(
  @PrimaryKey val environmentId: String,
  val label: String,
  val httpBaseUrl: String,
  val webSocketBaseUrl: String,
  val descriptorJson: String,
)

@Entity(tableName = "application_state")
internal data class ApplicationStateEntity(
  @PrimaryKey val singletonId: Int = SINGLETON_ID,
  val activeEnvironmentId: String?,
) {
  companion object {
    const val SINGLETON_ID = 1
  }
}

@Entity(
  tableName = "last_known_shell",
  foreignKeys = [
    ForeignKey(
      entity = EnvironmentEntity::class,
      parentColumns = ["environmentId"],
      childColumns = ["environmentId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("environmentId")],
)
internal data class LastKnownShellEntity(
  @PrimaryKey val environmentId: String,
  val snapshotSequence: Long,
  val snapshotJson: String,
)

@Dao
internal interface EnvironmentDao {
  @Query("SELECT * FROM environments ORDER BY environmentId")
  suspend fun allEnvironments(): List<EnvironmentEntity>

  @Query("SELECT * FROM environments WHERE environmentId = :environmentId")
  suspend fun environment(environmentId: String): EnvironmentEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putEnvironment(environment: EnvironmentEntity)

  @Query("DELETE FROM environments WHERE environmentId = :environmentId")
  suspend fun deleteEnvironment(environmentId: String)

  @Query("SELECT * FROM application_state WHERE singletonId = 1")
  suspend fun applicationState(): ApplicationStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putApplicationState(state: ApplicationStateEntity)

  @Query("SELECT * FROM last_known_shell ORDER BY environmentId")
  suspend fun allLastKnownShell(): List<LastKnownShellEntity>

  @Query("SELECT * FROM last_known_shell WHERE environmentId = :environmentId")
  suspend fun lastKnownShell(environmentId: String): LastKnownShellEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putLastKnownShell(snapshot: LastKnownShellEntity)
}

@Database(
  entities = [EnvironmentEntity::class, ApplicationStateEntity::class, LastKnownShellEntity::class],
  version = 2,
  exportSchema = true,
)
internal abstract class EnvironmentDatabase : RoomDatabase() {
  abstract fun environmentDao(): EnvironmentDao
}

public object EnvironmentDatabaseFactory {
  public const val DEFAULT_DATABASE_NAME: String = "native-android-environments.db"

  public fun create(
    context: Context,
    databaseName: String = DEFAULT_DATABASE_NAME,
  ): RoomEnvironmentCatalogRepository {
    val database = Room.databaseBuilder(
      context.applicationContext,
      EnvironmentDatabase::class.java,
      databaseName,
    )
      .addMigrations(MIGRATION_1_2)
      .build()
    return RoomEnvironmentCatalogRepository(database)
  }

  public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `last_known_shell` (
          `environmentId` TEXT NOT NULL,
          `snapshotSequence` INTEGER NOT NULL,
          `snapshotJson` TEXT NOT NULL,
          PRIMARY KEY(`environmentId`),
          FOREIGN KEY(`environmentId`) REFERENCES `environments`(`environmentId`)
            ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_last_known_shell_environmentId` " +
          "ON `last_known_shell` (`environmentId`)",
      )
    }
  }
}

public class RoomEnvironmentCatalogRepository internal constructor(
  private val database: EnvironmentDatabase,
) : EnvironmentCatalogRepository, AutoCloseable {
  private val dao = database.environmentDao()
  private val json = ContractJson.format

  override suspend fun read(): EnvironmentCatalogState = database.withTransaction {
    val environments = dao.allEnvironments().map(::decodeEnvironment)
    val environmentIds = environments.mapTo(mutableSetOf(), SavedEnvironment::environmentId)
    val active = dao.applicationState()?.activeEnvironmentId?.takeIf(environmentIds::contains)
    val shell = dao.allLastKnownShell().associate { entity ->
      entity.environmentId to decodeSnapshot(entity.snapshotJson)
    }
    EnvironmentCatalogState(environments, active, shell)
  }

  override suspend fun save(environment: SavedEnvironment, makeActive: Boolean) {
    database.withTransaction {
      dao.putEnvironment(encodeEnvironment(environment))
      val current = dao.applicationState()?.activeEnvironmentId
      if (makeActive || current == null) {
        dao.putApplicationState(ApplicationStateEntity(activeEnvironmentId = environment.environmentId))
      }
    }
  }

  override suspend fun activate(environmentId: String) {
    database.withTransaction {
      checkNotNull(dao.environment(environmentId)) { "Cannot activate an unknown environment." }
      dao.putApplicationState(ApplicationStateEntity(activeEnvironmentId = environmentId))
    }
  }

  override suspend fun saveLastKnownShell(
    environmentId: String,
    snapshot: OrchestrationShellSnapshot,
  ) {
    database.withTransaction {
      checkNotNull(dao.environment(environmentId)) { "Cannot save shell state for an unknown environment." }
      dao.putLastKnownShell(
        LastKnownShellEntity(
          environmentId = environmentId,
          snapshotSequence = snapshot.snapshotSequence,
          snapshotJson = json.encodeToString(snapshot),
        ),
      )
    }
  }

  override suspend fun remove(environmentId: String): RemovedEnvironmentState =
    database.withTransaction {
      val environment = dao.environment(environmentId)?.let(::decodeEnvironment)
      val previousActive = dao.applicationState()?.activeEnvironmentId
      val shell = dao.lastKnownShell(environmentId)?.let { decodeSnapshot(it.snapshotJson) }
      dao.deleteEnvironment(environmentId)
      if (previousActive == environmentId) {
        val replacement = dao.allEnvironments().firstOrNull()?.environmentId
        dao.putApplicationState(ApplicationStateEntity(activeEnvironmentId = replacement))
      }
      RemovedEnvironmentState(environment, previousActive, shell)
    }

  override suspend fun restore(removed: RemovedEnvironmentState) {
    database.withTransaction {
      removed.environment?.let { dao.putEnvironment(encodeEnvironment(it)) }
      removed.lastKnownShell?.let { snapshot ->
        val environmentId = checkNotNull(removed.environment?.environmentId)
        dao.putLastKnownShell(
          LastKnownShellEntity(
            environmentId = environmentId,
            snapshotSequence = snapshot.snapshotSequence,
            snapshotJson = json.encodeToString(snapshot),
          ),
        )
      }
      dao.putApplicationState(ApplicationStateEntity(activeEnvironmentId = removed.previousActiveEnvironmentId))
    }
  }

  override fun close() {
    database.close()
  }

  private fun encodeEnvironment(environment: SavedEnvironment): EnvironmentEntity = EnvironmentEntity(
    environmentId = environment.environmentId,
    label = environment.label,
    httpBaseUrl = environment.httpBaseUrl,
    webSocketBaseUrl = environment.webSocketBaseUrl,
    descriptorJson = json.encodeToString(environment.descriptor),
  )

  private fun decodeEnvironment(entity: EnvironmentEntity): SavedEnvironment = try {
    SavedEnvironment(
      environmentId = entity.environmentId,
      label = entity.label,
      httpBaseUrl = entity.httpBaseUrl,
      webSocketBaseUrl = entity.webSocketBaseUrl,
      descriptor = json.decodeFromString(entity.descriptorJson),
    )
  } catch (error: SerializationException) {
    throw CorruptEnvironmentStateFailure(error)
  } catch (error: IllegalArgumentException) {
    throw CorruptEnvironmentStateFailure(error)
  }

  private fun decodeSnapshot(value: String): OrchestrationShellSnapshot = try {
    json.decodeFromString(value)
  } catch (error: SerializationException) {
    throw CorruptEnvironmentStateFailure(error)
  } catch (error: IllegalArgumentException) {
    throw CorruptEnvironmentStateFailure(error)
  }
}
