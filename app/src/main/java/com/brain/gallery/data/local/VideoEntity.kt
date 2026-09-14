package com.brain.gallery.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val folderName: String,
    val width: Int = 0,
    val height: Int = 0,
    // Brain fields (cascading levels)
    val category: String = "unknown",
    val tags: String = "", // comma-separated, cheap to store
    val about: String = "",
    val vibeColor: Int = 0,
    val confidence: Float = 0f,
    val brainLevel: Int = 0, // 0=L0, 1=L1, 2=L2 done
    val junkScore: Float = 0f, // 1.0 = meme/screenshot/junk
    // Vision fields (L1, same keyframe pass)
    val faceCount: Int = 0,
    val smileCount: Int = 0,
    val phash: Long = 0L, // 0 = unknown
    val sharpness: Float = 0f,
    // Identity (dominant face MobileFaceNet embedding; null = unknown)
    val faceEmbedding: ByteArray? = null,
    val personId: Int = -1, // cluster index, -1 = unknown/singleton
    // Reserved: semantic text embedding over the Memory Document (future)
    val textEmbedding: ByteArray? = null,
    // Behavior fields with decay
    val lastWatchedMs: Long = 0,
    val watchCount: Int = 0,
    val completionSum: Float = 0f, // sum of completion fractions
    val skipCount: Int = 0,
    val isFavorite: Boolean = false
) {
    val avgCompletion: Float get() = if (watchCount == 0) 0f else completionSum / watchCount
    val tagList: List<String> get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
