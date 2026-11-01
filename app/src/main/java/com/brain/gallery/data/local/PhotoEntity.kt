package com.brain.gallery.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A photo, as the brain sees it. The video table stays video-shaped on
 * purpose; photos are cheaper to read (no frames to decode) so they carry
 * their own lighter row and their own lighter pass.
 */
@Entity(tableName = "photos", indices = [Index("folderName"), Index("brainLevel")])
data class PhotoEntity(
    @PrimaryKey val id: Long, // MediaStore image id
    val uri: String,
    val displayName: String,
    val folderName: String,
    val dateAddedSec: Long,
    val width: Int = 0,
    val height: Int = 0,

    // Brain fields — same vocabulary as videos, lighter pass
    val category: String = "unknown",
    val tags: String = "",
    val about: String = "",
    val confidence: Float = 0f,
    /** 0 = filename/folder only, 1 = the image pass has read the pixels. */
    val brainLevel: Int = 0,
    val junkScore: Float = 0f,
    val faceCount: Int = 0,
    val smileCount: Int = 0,
    val isFavorite: Boolean = false
) {
    val tagList: List<String> get() = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateAddedSec DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY dateAddedSec DESC")
    suspend fun all(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE brainLevel < 1 AND junkScore < 0.9 ORDER BY dateAddedSec DESC LIMIT :limit")
    suspend fun pendingBrain(limit: Int): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE brainLevel < 1")
    suspend fun pendingCount(): Int

    @Query("UPDATE photos SET category = :category, tags = :tags, about = :about, confidence = :confidence, brainLevel = 1, faceCount = :faces, smileCount = :smiles WHERE id = :id")
    suspend fun markRead(id: Long, category: String, tags: String, about: String, confidence: Float, faces: Int, smiles: Int)

    @Query("UPDATE photos SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Upsert
    suspend fun upsertAll(photos: List<PhotoEntity>)
}
