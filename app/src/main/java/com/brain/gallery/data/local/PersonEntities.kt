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
    val createdAt: Long = 0
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

    @Query("SELECT * FROM face_vectors WHERE videoId = :videoId")
    suspend fun vectorsForVideo(videoId: Long): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE personId = :personId")
    suspend fun vectorsForPerson(personId: Int): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors WHERE personId < 0")
    suspend fun unassignedVectors(): List<FaceVectorEntity>

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
