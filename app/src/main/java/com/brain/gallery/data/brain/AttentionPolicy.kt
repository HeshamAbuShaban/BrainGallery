package com.brain.gallery.data.brain

/**
 * Attention — the third learning tier.
 *
 * Watching a clip is the strongest statement a viewer can make about what the
 * brain should understand better. These constants and this function are the
 * ONLY place the attention numbers live, so the deepening queue can be
 * reasoned about from one screen of code: a watch lifts the clip toward the
 * front of the L1b (semantic labels) queue, a favorite lifts harder, and the
 * cap keeps one obsessive session from monopolizing the budget forever.
 *
 * Attention belongs to the scan queue only. It must never leak into the feed:
 * the reel is taught by FeedComposer's tunable weights, and the two systems
 * reading the same number would double-count one hint.
 */
object AttentionPolicy {
    /** One honest full watch. */
    const val WATCH_BUMP = 1.0f
    /** A favorite is a considered choice, worth more than an itch. */
    const val FAV_BUMP = 1.5f
    /** Per-clip ceiling: budget goes to the curious, not the obsessive. */
    const val CAP = 3.0f
    /** Below this the clip stops pulling the queue (arbitrary queue floor). */
    const val QUEUE_FLOOR = 0.25f

    /** New accumulated attention after one event. Pure, pinned by tests. */
    fun bump(current: Float?, event: Bump): Float {
        val add = when (event) {
            Bump.WATCH -> WATCH_BUMP
            Bump.FAVORITE -> FAV_BUMP
        }
        return ((current ?: 0f) + add).coerceIn(0f, CAP)
    }

    /** True when accumulated attention should actively pull the queue. */
    fun pullsQueue(attention: Float?): Boolean = (attention ?: 0f) >= QUEUE_FLOOR

    enum class Bump { WATCH, FAVORITE }
}
