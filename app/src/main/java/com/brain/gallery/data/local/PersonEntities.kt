package com.brain.gallery.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Tier 1 — human layer. Irreplaceable: survives app wipe via MemoryBundle export.
 * The brain must never silently destroy these; only the user does.
 */
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",           // user-approved or inferred-and-accepted
    val suggestedName: String = "",  // brain's proposal, not yet trusted
    val verified: Boolean = false,   // user touched it -> never auto-merge away
    val splitSuggested: Boolean = false, // bimodality detected -> offer a split
    val createdAt: Long = 0
)

/** Multiple vectors per person and per video; matched by max-similarity, not centroid. */
@Entity(tableName = "face_vectors", indices = [Index("videoId"), Index("personId")])
data class FaceVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val personId: Int = -1,          // -1 = unassigned (ambiguous, awaiting user)
    val vec: ByteArray,
    val quality: Float = 0f,         // sharpness x face size gate
    val createdAt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceVectorEntity) return false
        return id == other.id && videoId == other.videoId && personId == other.personId &&
            vec.contentEquals(other.vec) && quality == other.quality
    }
    override fun hashCode(): Int = id.hashCode() * 31 + videoId.hashCode()
}

@Entity(tableName = "not_interested")
data class NotInterestedEntity(
    @PrimaryKey val videoId: Long,
    val createdAt: Long
)

/** Reserved for the semantic layer so the upgrade is a migration, not a rewrite. */
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
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY id")
    fun observePersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY id")
    suspend fun allPersons(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.ON_CONFLICT_IGNORE)
    suspend fun insertPerson(p: PersonEntity): Long

    @Query("UPDATE persons SET name = :name, verified = 1 WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("UPDATE persons SET suggestedName = :name WHERE id = :id")
    suspend fun suggestName(id: Int, name: String)

    @Query("UPDATE persons SET splitSuggested = :flag WHERE id = :id")
    suspend fun setSplitSuggested(id: Int, flag: Boolean)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePerson(id: Int)

    @Query("SELECT * FROM face_vectors WHERE videoId = :videoId")
    suspend fun vectorsForVideo(videoId: Long): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE personId = :personId")
    suspend fun vectorsForPerson(personId: Int): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE personId = -1")
    suspend fun unassignedVectors(): List<FaceVectorEntity>

    @Query("SELECT COUNT(*) FROM face_vectors")
    suspend fun vectorCount(): Int

    @Insert
    suspend fun insertVectors(items: List<FaceVectorEntity>)

    @Query("UPDATE face_vectors SET personId = :personId WHERE id = :id")
    suspend fun assignVector(id: Long, personId: Int)

    @Query("DELETE FROM face_vectors WHERE videoId = :videoId")
    suspend fun deleteVectorsForVideo(videoId: Long)

    @Query("UPDATE face_vectors SET personId = -2 WHERE personId = :personId")
    suspend fun detachPerson(personId: Int)
}

@Dao
interface SupportDao {
    @Query("SELECT * FROM not_interested")
    suspend fun notInterested(): List<NotInterestedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addNotInterested(e: NotInterestedEntity)

    @Query("DELETE FROM watch_events WHERE rowId NOT IN (SELECT rowId FROM watch_events ORDER BY atMs DESC LIMIT :keep)")
    suspend fun pruneEvents(keep: Int)

    @Query("DELETE FROM watch_events")
    suspend fun clearEvents()

    @Query("SELECT v FROM app_kv WHERE k = :k")
    suspend fun get(k: String): String?

    @Query("INSERT OR REPLACE INTO app_kv (k, v) VALUES (:k, :v)")
    suspend fun put(k: String, v: String)

    @Query("SELECT * FROM app_kv")
    fun observeAll(): Flow<List<AppKvEntity>>
}
