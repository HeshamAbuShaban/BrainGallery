package com.brain.gallery.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tier 2 — machine-derived, recomputable from media files.
 * Deliberately holds NO embeddings: vectors live in [FaceVectorEntity] so that
 * UI queries (SELECT *) never pull blob megabytes into the view layer.
 */
@Entity(
    tableName = "videos",
    indices = [
        Index("dateAddedSec"),
        Index("personId"),
        Index("brainLevel"),
        Index("category"),
        Index("junkScore")
    ]
)
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

    // Brain fields
    val category: String = "unknown",
    val tags: String = "",
    val about: String = "",
    val vibeColor: Int = 0,
    val confidence: Float = 0f,
    /** 0 = L0 only, 1 = perceptual done (L1a), 2 = semantic done (L1b), 3 = deep done (L2) */
    val brainLevel: Int = 0,
    val junkScore: Float = 0f,
    /** L1b still owed (ML labels). Kept separate from brainLevel for queueing. */
    val pendingSemantic: Boolean = false,
    /** Expected-value score for the L1b queue; higher runs first. */
    val priority: Float = 0f,

    // Perceptual fields (L1a)
    val faceCount: Int = 0,
    val smileCount: Int = 0,
    val phash: Long = 0L,
    val sharpness: Float = 0f,

    // Identity (denormalized primary person for fast grouping)
    val personId: Int = -1,

    // Behaviour (Tier 1, exported in the memory bundle)
    val lastWatchedMs: Long = 0,
    val watchCount: Int = 0,
    val completionSum: Float = 0f,
    val skipCount: Int = 0,
    val isFavorite: Boolean = false,

    /**
     * A group the user put this clip in by hand. Empty means "wherever the engine
     * thinks it belongs". Kept apart from [category] on purpose: a reindex
     * recomputes category, and a hand-made decision has to outlast that.
     */
    val manualGroup: String = "",

    // Mark-and-sweep bookkeeping
    val lastSeenScan: Long = 0
) {
    /** The group this clip actually belongs to, hand-placed or inferred. */
    val effectiveGroup: String get() = manualGroup.ifBlank { category }
    val avgCompletion: Float get() = if (watchCount == 0) 0f else completionSum / watchCount
    val tagList: List<String> get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
