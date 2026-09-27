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

    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<VideoEntity>)

    @Query("""UPDATE videos SET uri = :uri, displayName = :name, durationMs = :duration,
        sizeBytes = :size, folderName = :folder, width = :w, height = :h, lastSeenScan = :scan
        WHERE id = :id""")
    suspend fun touchMetadata(
        id: Long, uri: String, name: String, duration: Long, size: Long,
        folder: String, w: Int, h: Int, scan: Long
    )

    /** Stamp every id we actually saw this run, in one statement per chunk. */
    @Query("UPDATE videos SET lastSeenScan = :scan WHERE id IN (:ids)")
    suspend fun stampSeen(ids: List<Long>, scan: Long)

    /** Mark-and-sweep: anything not stamped this run is gone from the device. */
    @Query("DELETE FROM videos WHERE lastSeenScan < :scanId")
    suspend fun sweepMissing(scanId: Long)

    @Query("SELECT * FROM videos WHERE brainLevel < 1 ORDER BY dateAddedSec DESC LIMIT :limit")
    suspend fun pendingPerceptual(limit: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE pendingSemantic = 1 ORDER BY priority DESC, dateAddedSec DESC LIMIT :limit")
    suspend fun pendingSemantic(limit: Int): List<VideoEntity>

    @Query("SELECT COUNT(*) FROM videos WHERE brainLevel < 1")
    fun observePerceptualPending(): Flow<Int>

    @Query("SELECT COUNT(*) FROM videos WHERE brainLevel < 1")
    suspend fun perceptualPendingCount(): Int

    @Query("SELECT COUNT(*) FROM videos WHERE pendingSemantic = 1")
    suspend fun semanticPendingCount(): Int

    @Query("SELECT COUNT(*) FROM videos WHERE pendingSemantic = 1")
    fun observeSemanticPending(): Flow<Int>

    @Query("""UPDATE videos SET lastWatchedMs = :now, watchCount = watchCount + 1,
        completionSum = completionSum + :completion, skipCount = skipCount + :skipped
        WHERE id = :id""")
    suspend fun recordWatch(id: Long, now: Long, completion: Float, skipped: Int)

    @Query("UPDATE videos SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM videos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Wipe everything the brain derived, keep favourites + history (Tier 1). */
    @Query("""UPDATE videos SET category = 'unknown', tags = '', about = '', confidence = 0,
        brainLevel = 0, pendingSemantic = 0, priority = 0, faceCount = 0, smileCount = 0,
        phash = 0, sharpness = 0, personId = -1""")
    suspend fun resetDerived()

    @Query("""UPDATE videos SET category = 'unknown', tags = '', about = '', confidence = 0,
        brainLevel = 0, pendingSemantic = 0, priority = 0, faceCount = 0, smileCount = 0,
        phash = 0, sharpness = 0, personId = -1, watchCount = 0, completionSum = 0,
        skipCount = 0, lastWatchedMs = 0, isFavorite = 0""")
    suspend fun resetEverything()

    @Query("SELECT * FROM videos WHERE personId = :person ORDER BY dateAddedSec DESC")
    suspend fun personGroup(person: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE faceCount > 0 ORDER BY smileCount DESC, faceCount DESC")
    suspend fun people(): List<VideoEntity>

    @Query("UPDATE videos SET personId = :person WHERE id = :id")
    suspend fun setPerson(id: Long, person: Int)

    @Query("UPDATE videos SET personId = :to WHERE personId = :from")
    suspend fun mergePersons(from: Int, to: Int)

    @Query("""UPDATE videos SET
        category = :category, tags = :tags, about = :about, confidence = :confidence,
        brainLevel = :level, junkScore = :junk, faceCount = :faces, smileCount = :smiles,
        phash = :phash, sharpness = :sharpness, priority = :priority, pendingSemantic = :pending
        WHERE id = :id""")
    suspend fun applyPerceptual(
        id: Long, category: String, tags: String, about: String, confidence: Float,
        level: Int, junk: Float, faces: Int, smiles: Int, phash: Long,
        sharpness: Float, priority: Float, pending: Boolean
    )

    @Query("""UPDATE videos SET category = :category, tags = :tags, about = :about,
        confidence = :confidence, brainLevel = :level, pendingSemantic = 0
        WHERE id = :id""")
    suspend fun applySemantic(
        id: Long, category: String, tags: String, about: String,
        confidence: Float, level: Int
    )

    @Query("""SELECT * FROM videos WHERE displayName LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%'
        OR category LIKE '%' || :q || '%' OR folderName LIKE '%' || :q || '%'
        ORDER BY watchCount DESC, dateAddedSec DESC LIMIT 60""")
    suspend fun search(q: String): List<VideoEntity>
}
