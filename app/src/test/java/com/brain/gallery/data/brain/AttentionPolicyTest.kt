package com.brain.gallery.data.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deepening queue's attention numbers, pinned. Break vectors: one
 * obsessive session owning the budget, favorites cheating past the cap,
 * a null history copy failing silently.
 */
class AttentionPolicyTest {

    @Test
    fun `a watch lifts, a favorite lifts harder`() {
        val w = AttentionPolicy.bump(0f, AttentionPolicy.Bump.WATCH)
        val f = AttentionPolicy.bump(0f, AttentionPolicy.Bump.FAVORITE)
        assertTrue("favorite ($f) must outrank watch ($w)", f > w)
    }

    @Test
    fun `attention accumulates from nothing`() {
        assertEquals(AttentionPolicy.WATCH_BUMP,
            AttentionPolicy.bump(null, AttentionPolicy.Bump.WATCH), 1e-5f)
    }

    @Test
    fun `an obsessive session tops out and never monopolizes the budget`() {
        var a: Float? = null
        repeat(10) { a = AttentionPolicy.bump(a, AttentionPolicy.Bump.WATCH) }
        assertEquals(AttentionPolicy.CAP, a!!, 1e-5f)
    }

    @Test
    fun `the queue floor ignores whispers and reads honest signals`() {
        assertFalse(AttentionPolicy.pullsQueue(null))
        assertFalse(AttentionPolicy.pullsQueue(0f))
        assertTrue(AttentionPolicy.pullsQueue(AttentionPolicy.QUEUE_FLOOR))
    }
}
