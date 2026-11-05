package com.brain.gallery.engine

/**
 * Shapes what a speech engine gives back into what a Memory Document can
 * keep. The recogniser's raw text is one best-guess line; but recognisers
 * also emit residue ("…", a lone punctuation, a silent empty), and every
 * character a document keeps is a character the search index trusts. Shaping
 * happens ONCE, at write time — the stored transcript is the honest line, so
 * the SQL document and the Kotlin builder never disagree about cleanup.
 *
 * Pure; the only decisions here are what counts as words and where the cap
 * lands.
 */
object TranscriptShaper {

    /**
     * The Memory Document budget. Search lives on words, not prose — beyond
     * this a transcript is noise with a rank penalty, so the line is cut at
     * the last whole word it can afford.
     */
    const val CAP = 240

    /** Minimum word matter for a line to be worth storing. */
    const val MIN_WORDS = 3

    /** Hoisted: the search path shapes hundreds of documents per keystroke. */
    private val WHITESPACE = Regex("\\s+")

    /**
     * Normalise: every run of whitespace becomes one space, terminal
     * recogniser residue dots fall off, and a too-long line keeps only
     * whole words. An already-passed string goes through unchanged
     * (idempotent), which is what makes re-shaping at read time safe.
     */
    fun shape(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.replace(WHITESPACE, " ").trim()
        // Residue a recogniser sometimes leaves when it heard nothing.
        while (s.endsWith('.') || s.endsWith('…') || s.endsWith(',')) {
            s = s.trimEnd('.', '…', ',').trim()
            if (s.isEmpty()) break
        }
        if (s.length <= CAP) return s
        val cut = s.lastIndexOf(' ', CAP)
        return if (cut > 0) s.substring(0, cut).trim() else s.substring(0, CAP)
    }

    /**
     * True when a shaped line carries at least [MIN_WORDS] letters or digits
     * — collapses the final honest gate: an engine's "…" is not a transcript.
     */
    fun usable(raw: String?): Boolean {
        val shaped = shape(raw)
        if (shaped.length < MIN_WORDS) return false
        return shaped.count { it.isLetterOrDigit() } >= MIN_WORDS
    }
}
