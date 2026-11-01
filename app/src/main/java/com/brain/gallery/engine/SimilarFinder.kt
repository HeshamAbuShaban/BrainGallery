package com.brain.gallery.engine

import com.brain.gallery.data.local.VideoEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Portable engine seam: "clip pulls its siblings" ranking.
 * Order of affinity: same person -> same event -> same category -> visual neighbor -> shared tag.
 */
@Singleton
class SimilarFinder @Inject constructor() {

    fun find(
        target: VideoEntity,
        all: List<VideoEntity>,
        limit: Int = 12,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): List<VideoEntity> {
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
            // Same clip, same scores — but never the same rail twice in a
            // row: equals swap inside their tier, so every open breathes.
            .fold(mutableListOf<MutableList<Pair<VideoEntity, Float>>>()) { tiers, item ->
                val last = tiers.lastOrNull()
                if (last != null && (last.first().second - item.second) < 0.25f) last += item
                else tiers += mutableListOf(item)
                tiers
            }
            .flatMap { tier -> if (tier.size == 1) tier else tier.shuffled(random) }
            .take(limit)
            .map { it.first }
    }
}
