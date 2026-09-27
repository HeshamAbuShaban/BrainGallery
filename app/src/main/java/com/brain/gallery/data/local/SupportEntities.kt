package com.brain.gallery.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "not_interested")
data class NotInterestedEntity(
    @PrimaryKey val videoId: Long,
    val createdAt: Long
)

/** Reserved for the semantic layer so that upgrade is a migration, not a rewrite. */
@Entity(tableName = "semantic_vectors", indices = [Index("mediaKey")])
data class SemanticVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaKey: String,
    val vec: ByteArray,
    val createdAt: Long = 0
)

/** Small key/value for diagnostics and later taste weights. */
@Entity(tableName = "app_kv")
data class AppKvEntity(
    @PrimaryKey val k: String,
    val v: String
)

@Dao
interface SupportDao {
    @Query("SELECT * FROM not_interested")
    suspend fun notInterested(): List<NotInterestedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addNotInterested(e: NotInterestedEntity)

    @Query("SELECT videoId FROM not_interested")
    fun observeNotInterested(): Flow<List<Long>>

    @Query("SELECT videoId FROM not_interested")
    suspend fun notInterestedIds(): List<Long>

    @Query("DELETE FROM watch_events WHERE rowId NOT IN (SELECT rowId FROM watch_events ORDER BY atMs DESC LIMIT :keep)")
    suspend fun pruneEvents(keep: Int)

    @Query("DELETE FROM watch_events")
    suspend fun clearEvents()

    @Query("SELECT v FROM app_kv WHERE k = :k")
    suspend fun get(k: String): String?

    @Query("INSERT OR REPLACE INTO app_kv (k, v) VALUES (:k, :v)")
    suspend fun put(k: String, v: String)

    @Query("DELETE FROM semantic_vectors")
    suspend fun clearSemanticVectors()
}
