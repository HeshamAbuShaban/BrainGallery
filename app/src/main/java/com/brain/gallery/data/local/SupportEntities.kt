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

/**
 * A name the user gave a group, keyed by the group's stable id
 * ("cat_pet", "person_12", "event_..."). Group titles used to be recomputed from
 * the engine's label on every rebuild, so renaming a non-person group wrote
 * nowhere and silently did nothing. This is where a name now lives, which also
 * means it survives a full reindex.
 */
@Entity(tableName = "group_overrides")
data class GroupOverrideEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val updatedAt: Long = 0
)

/** Set while the user is picking several clips at once. */
@Entity(tableName = "selection")
data class SelectionEntity(
    @PrimaryKey val videoId: Long,
    val pickedAt: Long = 0
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

    @Query("SELECT * FROM group_overrides")
    fun observeGroupOverrides(): Flow<List<GroupOverrideEntity>>

    @Query("SELECT * FROM group_overrides")
    suspend fun groupOverrides(): List<GroupOverrideEntity>

    @Query("SELECT name FROM group_overrides WHERE groupId = :groupId")
    suspend fun groupName(groupId: String): String?

    @Query("INSERT OR REPLACE INTO group_overrides (groupId, name, updatedAt) VALUES (:groupId, :name, :at)")
    suspend fun setGroupName(groupId: String, name: String, at: Long)

    @Query("DELETE FROM group_overrides WHERE groupId = :groupId")
    suspend fun clearGroupName(groupId: String)

    // ---- multi-select ----
    @Query("SELECT videoId FROM selection")
    fun observeSelection(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pick(e: SelectionEntity)

    @Query("DELETE FROM selection")
    suspend fun clearSelection()

    @Query("DELETE FROM selection WHERE videoId = :id")
    suspend fun unpick(id: Long)
}
