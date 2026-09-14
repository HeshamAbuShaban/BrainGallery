package com.brain.gallery.engine

import com.brain.gallery.data.local.VideoEntity
import javax.inject.Inject
import javax.inject.Singleton

data class DupSet(
    val keeper: VideoEntity,
    val redundant: List<VideoEntity>
) {
    val savingsBytes: Long get() = redundant.sumOf { it.sizeBytes }
    val all: List<VideoEntity> get() = listOf(keeper) + redundant
}

@Singleton
class DuplicateFinder @Inject constructor() {

    /**
     * Near-duplicate video sets. Conservative by design (Xiaomi-style):
     * same folder + added within 7 days + similar duration + dHash Hamming <= 6.
     * Keeper = sharpest, then highest resolution, then most watched.
     */
    fun find(all: List<VideoEntity>): List<DupSet> {
        val hashed = all.filter { it.phash != 0L }
        if (hashed.size < 2) return emptyList()
        val used = mutableSetOf<Long>()
        val out = mutableListOf<DupSet>()
        hashed.groupBy { it.folderName.lowercase() }.values.forEach { group ->
            val sorted = group.sortedBy { it.dateAddedSec }
            for (a in sorted) {
                if (a.id in used) continue
                val mates = sorted.filter { b ->
                    b.id != a.id && b.id !in used &&
                        kotlin.math.abs(b.dateAddedSec - a.dateAddedSec) < 7 * 86400 &&
                        durationSimilar(a.durationMs, b.durationMs) &&
                        ClusterMath.hamming(a.phash, b.phash) <= 6
                }
                if (mates.isEmpty()) continue
                val set = listOf(a) + mates
                val keeper = set.maxWithOrNull(
                    compareBy({ it.sharpness }, { it.width * it.height }, { it.watchCount })
                ) ?: a
                used += set.map { it.id }
                out += DupSet(keeper, set.filter { it.id != keeper.id })
            }
        }
        return out.sortedByDescending { it.savingsBytes }
    }

    private fun durationSimilar(a: Long, b: Long): Boolean {
        if (a <= 0 || b <= 0) return true
        val ratio = a.toDouble() / b.toDouble()
        return ratio in 0.75..1.34
    }
}
