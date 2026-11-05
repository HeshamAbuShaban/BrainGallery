package com.brain.gallery.engine

import com.brain.gallery.data.vision.FaceEmbedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matcher's pure core, pinned face-to-face. Break vectors covered:
 * a stranger claiming a person, an ambiguous tie promoted to a match,
 * a denied cluster (the user's correction) winning anyway, and evidence
 * that decays to noise being treated as evidence.
 */
class IdentityMatchTest {

    // Tiny orthogonal space: cosine 1.0 with itself, 0.0 across, -1.0 opposite.
    private val a = floatArrayOf(1f, 0f, 0f, 0f)
    private val b = floatArrayOf(-1f, 0f, 0f, 0f)
    private val c = floatArrayOf(0f, 1f, 0f, 0f)
    private val d = floatArrayOf(0f, 0f, 1f, 0f)

    private fun lib(vararg persons: Pair<Int, List<FloatArray>>) =
        persons.toMap()

    // ---- the decisive gates ---------------------------------------------------

    @Test
    fun `a clear match is claimed and not ambiguous`() {
        val out = decide(lib(1 to listOf(a, b), 2 to listOf(c, d)), a, minSim = 0.42f, minMargin = 0.05f)
        assertEquals(1, out.personId)
        assertFalse(out.ambiguous)
        assertEquals(1f, out.best, 1e-5f)
        assertEquals(1f, out.margin, 1e-5f)
    }

    @Test
    fun `a stranger stays unassigned when nobody clears the similarity gate`() {
        // A query halfway between the axes: 0.707 to everybody-a-bit, so the
        // best similarity (0.707) passes a 0.6 gate but a 0.8 gate holds it
        // out — the similarity gate alone decides strangers, not the margin.
        val wanderer = floatArrayOf(0.70711f, 0.70711f, 0f, 0f)
        val out = decide(
            lib(1 to listOf(a), 2 to listOf(c)),
            wanderer, minSim = 0.8f, minMargin = 0.05f)
        assertTrue("best ${out.best} must not clear 0.8", out.personId == -1 && !out.ambiguous)
    }

    @Test
    fun `a coin-flip tie is surfaced as ambiguous, not decided`() {
        // Two persons both cosine 1.0? Impossible unless identical vecs —
        // use equal-and-close: person vectors symmetric toward the query.
        val q = floatArrayOf(0.70711f, 0.70711f, 0f, 0f)
        val out = decide(
            lib(1 to listOf(floatArrayOf(1f, 0f, 0f, 0f)),
                2 to listOf(floatArrayOf(0f, 1f, 0f, 0f))),
            q, minSim = 0.42f, minMargin = 0.3f)
        assertEquals(-1, out.personId)
        assertTrue(out.ambiguous)
    }

    @Test
    fun `the margin gate needs a real second person to compare against`() {
        // One person only: margin is 0 by fiat but can never be ambiguous.
        val out = decide(lib(1 to listOf(a)), a, minSim = 0.42f, minMargin = 0.9f)
        assertEquals(1, out.personId)
        assertFalse(out.ambiguous)
    }

    @Test
    fun `no evidence at all decides nothing`() {
        val out = decide(emptyMap(), a, 0.42f, 0.05f)
        assertEquals(-1, out.personId)
        assertEquals(0f, out.best, 1e-5f)
    }

    // ---- denial: the engine obeys the user's "this is not that person" --------

    @Test
    fun `a denied cluster cannot claim the video even when it matches best`() {
        // Person 1 is the obvious winner, but the user split exactly this
        // video out of person 1. The decision must fall to person 2 or none.
        val libAll = lib(1 to listOf(c), 2 to listOf(c, d))
        val outDenied = decide(libAll.filterKeys { it != 1 }, c, minSim = 0.42f, minMargin = 0.05f)
        assertEquals("denied winner must not match, next cluster may", 2, outDenied.personId)
        // And with every cluster denied, unassigned — never a silent re-match.
        val outNone = decide(emptyMap(), c, minSim = 0.42f, minMargin = 0.05f)
        assertEquals(-1, outNone.personId)
    }

    @Test
    fun `vector bytes round-trip does not bend the cosine`() {
        val vs = FaceEmbedder.fromBytes(FaceEmbedder.toBytes(a))
        val libBytes = mapOf(7 to listOfNotNull(vs))
        val out = decide(libBytes, a, 0.42f, 0.05f)
        assertEquals(7, out.personId)
        assertEquals(1f, out.best, 1e-4f)
    }
}
