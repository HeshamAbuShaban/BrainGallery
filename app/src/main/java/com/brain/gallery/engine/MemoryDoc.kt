package com.brain.gallery.engine

import com.brain.gallery.data.local.VideoEntity

/**
 * Portable engine seam: one Memory Document per video.
 * Retrieval = filter on hard keys (who/when/where) + rank on meaning (what/words).
 * New signals plug into the document; nothing bolts on beside it.
 */
data class MemoryDoc(
    val videoId: Long,
    val who: String,      // "Person A" or "" when unknown
    val what: String,     // category + tags + about
    val where: String,    // folder + event hint
    val whenText: String, // year-month + age bucket
    val vibe: String,     // faces/smiles/completion/favorite markers
    val text: String      // concatenated searchable document
)

object MemoryDocBuilder {

    fun personLabel(personId: Int): String =
        if (personId < 0) "" else "Person ${'A' + (personId % 26)}${if (personId >= 26) (personId / 26 + 1).toString() else ""}"

    fun build(v: VideoEntity): MemoryDoc {
        val who = personLabel(v.personId)
        val what = listOf(v.category, v.tagList.joinToString(" "), v.about)
            .filter { it.isNotBlank() && it != "unknown" }.joinToString(" ")
        val where = v.folderName
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = v.dateAddedSec * 1000 }
        val whenText = "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}"
        val vibe = buildList {
            if (v.faceCount > 0) add("${v.faceCount} faces")
            if (v.smileCount > 0) add("smiling")
            if (v.isFavorite) add("favorite")
            if (v.watchCount > 2) add("rewatched")
        }.joinToString(" ")
        val text = listOf(who, what, where, whenText, vibe)
            .filter { it.isNotBlank() }.joinToString(" ").lowercase()
        return MemoryDoc(v.id, who, what, where, whenText, vibe, text)
    }

    /** Lexical doc match score for a multi-token query. */
    fun matchScore(docText: String, query: String): Float {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
        if (tokens.isEmpty()) return 0f
        var hits = 0
        for (t in tokens) if (docText.contains(t)) hits++
        return hits.toFloat() / tokens.size
    }
}
