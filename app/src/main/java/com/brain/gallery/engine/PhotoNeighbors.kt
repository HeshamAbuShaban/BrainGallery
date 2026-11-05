package com.brain.gallery.engine

import com.brain.gallery.data.local.PhotoEntity

/**
 * "More like this", for stills.
 *
 * The reel has pulled neighbours out of the library since the first build;
 * a photo had no equivalent. Photos carry no person id, so the signals are
 * the ones a shoebox uses: the same folder around the same week, the same
 * label, shared tags, and a fingerprint close enough to be the same scene at
 * another size. Animated frames stick together too — two GIFs from one meme
 * folder are neighbours even when the labels disagree.
 *
 * The last resort is the same shelf, nearest in time. A photo the brain has
 * not read yet still deserves a rail, the way a box of prints does.
 */
object PhotoNeighbors {
    const val CAP = 12

    /** dHash flips that still read as "the same scene" for a still. */
    private const val VISUAL_BAR = 10
    private const val DAY_SEC = 86_400L

    fun of(
        photo: PhotoEntity,
        all: List<PhotoEntity>,
        cap: Int = CAP
    ): List<PhotoEntity> {
        if (cap <= 0) return emptyList()
        val scored = all.asSequence()
            .filter { it.id != photo.id }
            .map { it to score(photo, it) }
            .filter { it.second > 0f }
            .sortedWith(
                compareByDescending<Pair<PhotoEntity, Float>> { it.second }
                    .thenBy { kotlin.math.abs(it.first.dateAddedSec - photo.dateAddedSec) }
                    .thenByDescending { it.first.dateAddedSec }
            )
            .take(cap)
            .map { it.first }
            .toList()
        if (scored.isNotEmpty()) return scored

        // Nothing scored: keep the shoebox rather than an empty rail.
        return all.asSequence()
            .filter { it.id != photo.id && sameFolder(it.folderName, photo.folderName) }
            .sortedBy { kotlin.math.abs(it.dateAddedSec - photo.dateAddedSec) }
            .take(cap)
            .toList()
    }

    /**
     * Pure affinity. Zero means "no reason to put this next to that" — the
     * rail should not invent a relationship out of a shared timestamp alone.
     */
    fun score(a: PhotoEntity, b: PhotoEntity): Float {
        var s = 0f
        val sameFolder = sameFolder(a.folderName, b.folderName)
        val dayGap = kotlin.math.abs(a.dateAddedSec - b.dateAddedSec)
        if (sameFolder && dayGap < 3 * DAY_SEC) s += 2f
        if (a.category != "unknown" && a.category == b.category) s += 1.5f
        if (a.phash != 0L && b.phash != 0L) {
            val d = ClusterMath.hamming(a.phash, b.phash)
            if (d <= VISUAL_BAR) s += 1f + (VISUAL_BAR - d) / VISUAL_BAR.toFloat()
        }
        val aTags = a.tagList.toSet()
        if (aTags.isNotEmpty()) {
            val shared = b.tagList.count { it in aTags }
            s += (shared * 0.25f).coerceAtMost(1f)
        }
        // Two frames of the same animation belong together; a still GIF next
        // to a still JPEG does not.
        if (a.animated == true && b.animated == true) s += 0.5f
        return s
    }

    /** A blank folder is "Other" on screen, but it is not evidence of kinship. */
    private fun sameFolder(a: String, b: String): Boolean =
        a.isNotBlank() && a.equals(b, ignoreCase = true)
}
