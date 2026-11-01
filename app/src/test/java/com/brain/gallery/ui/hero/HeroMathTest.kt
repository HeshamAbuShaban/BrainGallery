package com.brain.gallery.ui.hero

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The hero mechanics are decorative, so their math must be proven boring:
 * exact at the anchors, monotone where it claims to be, symmetric where it
 * claims to be. One angle per test.
 */
class HeroMathTest {

    // ---- shared ----

    @Test
    fun `ease curve hits its anchors and midpoint exactly`() {
        assertEquals(0f, HeroMath.easeInOutCubic(0f), 1e-6f)
        assertEquals(0.5f, HeroMath.easeInOutCubic(0.5f), 1e-6f)
        assertEquals(1f, HeroMath.easeInOutCubic(1f), 1e-6f)
    }

    @Test
    fun `ease curve never turns back`() {
        var prev = -1f
        for (i in 0..100) {
            val v = HeroMath.easeInOutCubic(i / 100f)
            assertTrue("regressed at $i: $v < $prev", v >= prev)
            prev = v
        }
    }

    @Test
    fun `momentum loses all but five percent in one second`() {
        assertEquals(5f, HeroMath.decayVelocity(100f, 1f), 0.01f)
        assertTrue(HeroMath.decayVelocity(50f, 0.1f) < 50f)
    }

    // ---- mechanic A: cylindrical shelf ----

    @Test
    fun `depth is zero center and one at edges, symmetric`() {
        val w = 1080f
        assertEquals(0f, HeroMath.cylindricalDepth(w / 2f, w), 1e-6f)
        assertEquals(1f, HeroMath.cylindricalDepth(0f, w), 1e-6f)
        assertEquals(1f, HeroMath.cylindricalDepth(w, w), 1e-6f)
        assertEquals(
            HeroMath.cylindricalDepth(w / 4f, w),
            HeroMath.cylindricalDepth(3f * w / 4f, w), 1e-6f)
    }

    @Test
    fun `push spreads edges outward symmetrically and keeps center fixed`() {
        val xc = 540f
        assertEquals(xc, HeroMath.pushedX(0f, xc), 1e-6f)
        val right = HeroMath.pushedX(300f, xc)
        val left = HeroMath.pushedX(-300f, xc)
        assertEquals(xc - (right - xc), left, 1e-4f)
        assertTrue("edge card must move outward", abs(right - xc) > 300f)
    }

    @Test
    fun `edge lift starts flat and grows super-linearly`() {
        assertEquals(0f, HeroMath.edgeLift(0f), 1e-6f)
        // q^2.6 below the diagonal until q=1: gentle at the hinge, tall at the rim
        assertTrue(HeroMath.edgeLift(0.5f) < 0.5f)
        assertEquals(1f, HeroMath.edgeLift(1f), 1e-6f)
    }

    // ---- mechanic B: card wave ----

    @Test
    fun `influence peaks at the pointer and dies with distance`() {
        val s = 120f
        assertEquals(1f, HeroMath.calculateGaussianInfluence(0f, 0f, 0f, 0f, s), 1e-6f)
        assertTrue(HeroMath.calculateGaussianInfluence(300f, 0f, 0f, 0f, s) < 0.01f)
    }

    @Test
    fun `vertical proximity is forgiven more than horizontal`() {
        val s = 120f
        val horizontal = HeroMath.calculateGaussianInfluence(100f, 0f, 0f, 0f, s)
        val vertical = HeroMath.calculateGaussianInfluence(0f, 100f, 0f, 0f, s)
        assertTrue("same distance, y must feel closer", vertical > horizontal)
    }

    @Test
    fun `a dead pointer has no spread and zero influence`() {
        assertEquals(0f, HeroMath.calculateGaussianInfluence(0f, 0f, 0f, 0f, 0f), 1e-6f)
    }

    @Test
    fun `lean opposes velocity`() {
        assertTrue(HeroMath.leanFromVelocity(500f) < 0f)
        assertEquals(0f, HeroMath.leanFromVelocity(0f), 1e-6f)
    }

    // ---- mechanic C: portal ----

    @Test
    fun `no tilt is a plain translation`() {
        val p = HeroMath.project3DPoint(100f, -50f, 540f, 960f, 0f, 0f)
        assertEquals(640f, p.x, 1e-4f)
        assertEquals(910f, p.y, 1e-4f)
    }

    @Test
    fun `yaw pushes near-side points outward`() {
        // right side of the window when yawing right (positive rotY): z > 0 for x > 0,
        // so p < 1 and the point pulls in; the left side comes forward and spreads.
        val right = HeroMath.project3DPoint(200f, 0f, 540f, 960f, 0f, 20f)
        val flat = HeroMath.project3DPoint(200f, 0f, 540f, 960f, 0f, 0f)
        assertTrue("right edge should pull toward center", right.x < flat.x)
    }

    @Test
    fun `rounded rect sampling is 44 points inside bounds`() {
        val pts = HeroMath.sampleRoundedRectPoints(300f, 400f, 40f)
        assertEquals(44, pts.size)
        pts.forEach { p ->
            assertTrue("x out of bounds: ${p.x}", p.x >= -0.01f && p.x <= 300.01f)
            assertTrue("y out of bounds: ${p.y}", p.y >= -0.01f && p.y <= 400.01f)
        }
    }

    @Test
    fun `tilt targets match the constitution's degrees`() {
        assertEquals(37.4f / 2f, HeroMath.tiltYaw(1f), 1e-4f)
        assertEquals(-37.4f / 2f, HeroMath.tiltYaw(0f), 1e-4f)
        assertEquals(33f / 2f, HeroMath.tiltPitch(0f), 1e-4f)
        assertEquals(-33f / 2f, HeroMath.tiltPitch(1f), 1e-4f)
        assertEquals(0f, HeroMath.tiltYaw(0.5f), 1e-4f)
    }

    @Test
    fun `expansion collapses radius and stretches size linearly in e`() {
        assertEquals(300f, HeroMath.portalWidth(0f, 300f, 1080f), 1e-4f)
        assertEquals(1080f, HeroMath.portalWidth(1f, 300f, 1080f), 1e-4f)
        assertEquals(0f, HeroMath.portalCorner(1f, 44f), 1e-4f)
        assertEquals(44f, HeroMath.portalCorner(0f, 44f), 1e-4f)
    }
}
