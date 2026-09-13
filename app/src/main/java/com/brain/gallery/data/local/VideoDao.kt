package com.brain.gallery.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY dateAddedSec DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY dateAddedSec DESC")
    suspend fun getAllSync(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: Long): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VideoEntity)

    @Query("DELETE FROM videos WHERE id NOT IN (:keepIds)")
    suspend fun deleteRemoved(keepIds: List<Long>)

    @Query("SELECT * FROM videos WHERE brainLevel < :maxLevel ORDER BY junkScore ASC, dateAddedSec DESC LIMIT :limit")
    suspend fun pendingBrain(maxLevel: Int = 1, limit: Int = 50): List<VideoEntity>

    @Query("UPDATE videos SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM videos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("""SELECT * FROM videos WHERE displayName LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%'
        OR category LIKE '%' || :q || '%' OR folderName LIKE '%' || :q || '%'
        ORDER BY watchCount DESC, dateAddedSec DESC LIMIT 60""")
    suspend fun search(q: String): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE category = :cat ORDER BY dateAddedSec DESC")
    suspend fun byCategory(cat: String): List<VideoEntity>

    @Query("SELECT DISTINCT category FROM videos WHERE category != 'unknown' ORDER BY category")
    suspend fun categories(): List<String>
}
