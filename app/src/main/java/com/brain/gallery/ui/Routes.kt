package com.brain.gallery.ui

/**
 * The route table. One place, string routes: the app is small enough that
 * type-safe @Serializable routes would only add a serialization plugin for
 * two arguments (ROADMAP Phase 1 notes the upgrade path).
 *
 * Top-level tabs: feed, organize, search, tune. Stacked destinations:
 * organize/group/{groupId} and brain. The spotlight is not a route — it is
 * an app-level player overlay, never a place in the back stack.
 */
object Routes {
    const val FEED = "feed"
    const val ORGANIZE = "organize"
    const val GROUP = "organize/group/{groupId}"
    const val SEARCH = "search"
    const val TUNE = "tune"
    const val BRAIN = "brain"

    fun group(id: String) = "organize/group/$id"

    /** Which nav-pill tab a route belongs to. null (before first emission) = start. */
    fun tabIndex(route: String?): Int = when (route) {
        FEED -> 0
        SEARCH -> 2
        TUNE, BRAIN -> 3
        else -> 1 // organize, group detail, and the pre-first-emission state
    }

    /** Deeper destinations rank higher, so transitions know which way to slide. */
    fun rank(route: String?): Int = when (route) {
        FEED -> 0
        ORGANIZE -> 2
        GROUP -> 3
        SEARCH -> 4
        TUNE -> 6
        BRAIN -> 7
        else -> 2
    }
}
