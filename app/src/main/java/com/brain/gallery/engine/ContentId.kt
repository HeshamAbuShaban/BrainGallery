package com.brain.gallery.engine

/**
 * Content ID — "Shazam for your own library".
 *
 * The idea stolen honestly: the query is a *sample of the thing itself*,
 * not a description. Someone sends you a clip; you cannot name it, so you
 * pick it and the brain answers what/where it is. The engine: fingerprint
 * a few frames of the picked file (dHash, same dialect the library's
 * phashes already speak), then compare hamming distances against every
 * fingerprint the library has. Threshold = DuplicateFinder's keeper bar,
 * because "same content" should mean the same thing everywhere in this app.
 *
 * Pure, so the decisions are pinned: distances, thresholds, rank order.
 * The database, decode and UI live elsewhere.
 */
object ContentId {

    /** One library fingerprint. A video row or a photo row, by hash. */
    data class LibraryHash(val id: Long, val isVideo: Boolean, val phash: Long)

    /** A hit: the library fingerprint and how far it is from the query. */
    data class Scored(val hash: LibraryHash, val distance: Int)

    /** Same bar the duplicate keeper uses: ≤ this is "same content". */
    const val MATCH_DISTANCE = 6

    /**
     * Best frame in, closest library hash out. Videos are queried by several
     * frame fingerprints because a re-encode or a trim shifts any single
     * frame; a library item is a hit when ANY picked frame is close enough.
     * Never-matching-zero is load-bearing: 0L means "no fingerprint yet".
     */
    fun match(queryFrames: List<Long>, library: List<LibraryHash>): List<Scored> {
        val frames = queryFrames.filter { it != 0L }
        if (frames.isEmpty()) return emptyList()
        return library.asSequence()
            .filter { it.phash != 0L }
            .map { h -> Scored(h, frames.minOf { ClusterMath.hamming(it, h.phash) }) }
            .filter { it.distance <= MATCH_DISTANCE }
            .sortedWith(compareBy({ it.distance }, { it.hash.id }))
            .toList()
    }

    /** True when the best hit is so close it is the same pixels, not a cousin. */
    fun nearIdentical(scored: List<Scored>): Boolean =
        scored.firstOrNull()?.distance?.let { it <= 3 } ?: false
}
