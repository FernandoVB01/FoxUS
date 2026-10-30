package com.foxus.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_tasks")
data class CachedTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val source: String = "manual",
    val deadline: String? = null,
    val needsPressure: Boolean = false,
    val status: String,
    val latestMicrotaskTitle: String?,
    val latestMicrotaskSeconds: Int?,
    val updatedAtEpochMs: Long
)

@Dao
interface FoxusDao {
    @Query("SELECT * FROM cached_tasks ORDER BY needsPressure DESC, updatedAtEpochMs DESC")
    fun observeTasks(): Flow<List<CachedTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(items: List<CachedTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(item: CachedTaskEntity)

    @Query("DELETE FROM cached_tasks")
    suspend fun clearTasks()
}

@Database(
    entities = [CachedTaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FoxusDatabase : RoomDatabase() {
    abstract fun foxusDao(): FoxusDao

    companion object {
        fun build(context: Context): FoxusDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FoxusDatabase::class.java,
                "foxus_local_cache.db"
            ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}
