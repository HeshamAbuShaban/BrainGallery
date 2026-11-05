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
    val name: String = "",
    val suggestedName: String = "",
    val verified: Boolean = false,
    val splitSuggested: Boolean = false,
    val createdAt: Long = 0,
    /**
     * Learned silence: the user looked at a split warning and said "no".
     * Nullable on purpose — a schema-migrated column with a default fails
     * Room's validation, so absence reads as "not silenced" and silence is
     * written as true. Permanent by design: the engine never re-flags a
     * person the user decided about, unless the library itself changes hands
     * via import.
     */
    val splitSilenced: Boolean? = null
)

/**
 * Teaching table: video → person pairs the user explicitly rejected
 * ("this clip is not that person"). Without it the adopt pass would quietly
 * re-adopt the vector right back — the engine would correct the user.
 * Lives in the human layer: exported and imported with the bundle.
 */
@Entity(tableName = "identity_lessons", primaryKeys = ["videoId", "personId"])
data class IdentityLessonEntity(
    val videoId: Long,
    val personId: Int,
    val createdAt: Long
)

/** Multiple vectors per person and per video; matched by max-similarity, not centroid. */
@Entity(tableName = "face_vectors", indices = [Index("videoId"), Index("personId")])
data class FaceVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val personId: Int = -1,
    val vec: ByteArray,
    val quality: Float = 0f,
    val createdAt: Long = 0
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY id")
    fun observePersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY id")
    suspend fun allPersons(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(p: PersonEntity): Long

    @Query("UPDATE persons SET name = :name, verified = 1 WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("UPDATE persons SET suggestedName = :name WHERE id = :id")
    suspend fun suggestName(id: Int, name: String)

    @Query("UPDATE persons SET splitSuggested = 1 WHERE id = :id")
    suspend fun setSplitSuggested(id: Int)

    @Query("UPDATE persons SET splitSuggested = 0 WHERE id = :id")
    suspend fun clearSplitSuggested(id: Int)

    /** The user said "no" to the split warning: clear the flag AND teach it. */
    @Query("UPDATE persons SET splitSuggested = 0, splitSilenced = 1 WHERE id = :id")
    suspend fun silenceSplit(id: Int)

    /** The user confirmed this person by hand: the engine stops second-guessing. */
    @Query("UPDATE persons SET verified = 1 WHERE id = :id")
    suspend fun verify(id: Int)

    @Query("UPDATE persons SET verified = 0, splitSilenced = NULL WHERE id = :id")
    suspend fun forgetLessons(id: Int)

    @Query("SELECT * FROM face_vectors WHERE videoId = :videoId")
    suspend fun vectorsForVideo(videoId: Long): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE personId = :personId")
    suspend fun vectorsForPerson(personId: Int): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE personId < 0")
    suspend fun unassignedVectors(): List<FaceVectorEntity>

    /** Every assigned vector, one hop — the batched feed for the matchers. */
    @Query("SELECT * FROM face_vectors WHERE personId >= 0")
    suspend fun assignedVectors(): List<FaceVectorEntity>

    @Query("SELECT * FROM identity_lessons")
    suspend fun allLessons(): List<IdentityLessonEntity>

    @Query("SELECT personId FROM identity_lessons WHERE videoId = :videoId")
    suspend fun deniedPersonsFor(videoId: Long): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLessons(items: List<IdentityLessonEntity>)

    @Query("SELECT COUNT(*) FROM face_vectors")
    suspend fun vectorCount(): Int

    @Insert
    suspend fun insertVectors(items: List<FaceVectorEntity>)

    @Query("UPDATE face_vectors SET personId = :personId WHERE id = :id")
    suspend fun assignVector(id: Long, personId: Int)

    @Query("UPDATE face_vectors SET personId = :to WHERE personId = :from")
    suspend fun mergePersons(from: Int, to: Int)

    @Query("DELETE FROM face_vectors WHERE videoId = :videoId")
    suspend fun deleteVectorsForVideo(videoId: Long)

    @Query("DELETE FROM face_vectors")
    suspend fun deleteAllVectors()

    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()

    /** Merged-away people leave empty rows behind; drop them so counts stay honest. */
    @Query("DELETE FROM persons WHERE id NOT IN (SELECT DISTINCT personId FROM face_vectors WHERE personId >= 0)")
    suspend fun deleteEmptyPersons()
}
