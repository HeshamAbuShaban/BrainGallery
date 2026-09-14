package com.brain.gallery.engine

import com.brain.gallery.data.local.VideoEntity

/**
 * Portable engine seam: "clip pulls its siblings" ranking.
 * Order of affinity: same person -> same event -> same category -> visual neighbor -> shared tag.
 */
object SimilarFinder {

    fun find(target: VideoEntity, all: List<VideoEntity>, limit: Int = 12): List<VideoEntity> {
        val targetTags = target.tagList.toSet()
        return all
            .filter { it.id != target.id }
            .map { v ->
                var s = 0f
                if (target.personId >= 0 && v.personId == target.personId) s += 3f
                if (v.folderName.equals(target.folderName, true) &&
                    kotlin.math.abs(v.dateAddedSec - target.dateAddedSec) < 3 * 86400
                ) s += 2f
                if (v.category == target.category && v.category != "unknown") s += 1.5f
                if (target.phash != 0L && v.phash != 0L &&
                    ClusterMath.hamming(target.phash, v.phash) <= 10
                ) s += 1f
                val shared = v.tagList.count { it in targetTags }
                s += (shared * 0.25f).coerceAtMost(1f)
                v to s
            }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
