package com.brain.gallery.engine

/**
 * Turns a human query into a safe FTS4 MATCH expression.
 *
 * Everything here is containment by construction: the query is split into
 * unicode letter/number tokens (Arabic filenames must survive — the split
 * therefore keeps every unicode letter, not just ASCII), operators and quotes
 * cannot sneak through, and terms become prefix queries so "beach" finds a
 * clip the watcher never typed in full.
 */
object FtsQuery {

    private val SPLIT = Regex("[^\\p{L}\\p{Nd}]+")

    /** Clean tokens: lowercase, 2+ chars, deduped, capped so a paste cannot stall the pass. */
    fun tokens(raw: String): List<String> = raw.lowercase()
        .split(SPLIT)
        .filter { it.length >= 2 }
        .distinct()
        .take(8)

    /**
     * The MATCH bound argument: `tok1* OR tok2*`. Null when nothing usable
     * survived, which means "fall back to the LIKE path".
     */
    fun matchTerms(raw: String): String? =
        tokens(raw).takeIf { it.isNotEmpty() }?.joinToString(" OR ") { "$it*" }
}
