package com.brain.gallery.ui.hero

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The hero mechanics' math, in one pure object.
 *
 * The three mechanics (cylindrical shelf, card-wave stack, portal) render
 * with Canvas/graphicsLayer and must never recompose on pointer coordinates —
 * so their formulas live here where they can be unit-tested without a
 * device. Screens call these; no inline trigonometry in UI code.
 *
 * Reference: the three-mechanic hero constitution (GATE 4 / mechanics A–C).
 */
object HeroMath {

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    /** Ease-in-out cubic, the only expansion curve the portal uses. */
    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).pow(3) / 2f

    /**
     * Momentum decay for drag releases: velocity shrinks to 5% of itself
     * every second. Below [STOP_VELOCITY] the gesture is over.
     */
    const val STOP_VELOCITY = 10f // dp/s

    fun decayVelocity(velocityDpPerS: Float, dtSeconds: Float): Float =
        velocityDpPerS * 0.05f.pow(dtSeconds)

    // ------------------------------------------------------------------
    // Mechanic A — cylindrical shelf
    // ------------------------------------------------------------------

    /** Normalized distance from the carousel center: 0 dead center, 1 at
     * either edge. The squaring comes later, at the consumers. */
    fun cylindricalDepth(x: Float, width: Float): Float {
        if (width <= 0f) return 0f
        val half = width / 2f
        return abs(x - half) / half
    }

    /**
     * Horizontal push: cards spread slightly wider toward the edges, as if
     * wrapped on a cylinder seen from inside. t is the card's flat x offset
     * from center; the extra factor grows with the square of distance.
     */
    fun pushedX(t: Float, centerX: Float): Float {
        val ratio = t / centerX
        return centerX + t * (1f + 0.083f * ratio * ratio)
    }

    /** Card width gain: outer cards are wider than inner ones, by d². */
    fun cardWidthGain(d: Float): Float = d * d

    /** Vertical edge lift for one card edge at param q (0..1): q^2.6. */
    fun edgeLift(q: Float): Float = if (q <= 0f) 0f else q.pow(2.6f)

    /** Center-line rise: outer cards sit higher, by d². */
    fun centerYRise(d: Float): Float = d * d

    // ------------------------------------------------------------------
    // Mechanic B — card wave
    // ------------------------------------------------------------------

    /**
     * Pointer influence on a card, Gaussian with a squashed y axis (the
     * finger reads vertical proximity more leniently). 1 at the pointer,
     * fading to ~0 at the spread's edge.
     */
    fun calculateGaussianInfluence(
        itemX: Float, itemY: Float,
        pointerX: Float, pointerY: Float, spread: Float
    ): Float {
        if (spread <= 0f) return 0f
        val dx = itemX - pointerX
        val dy = (itemY - pointerY) * 0.55f
        return exp(-(dx * dx + dy * dy) / (spread * spread))
    }

    /** Stack lean: cards tip into their own motion, from live velocity. */
    fun leanFromVelocity(velocityDpPerS: Float): Float = -velocityDpPerS * 0.012f

    /**
     * The idle sweep's virtual cursor: a slow sine across the pile, period
     * owned here so the UI never writes trig.
     */
    fun sweepX(elapsedSeconds: Float, centerX: Float, amplitude: Float): Float =
        centerX + sin(elapsedSeconds * 0.45f) * amplitude

    /** Stack geometry: card i sits back-left of card 0. */
    fun stackOffsetX(index: Int, step: Float): Float = -index * step
    fun stackOffsetZ(index: Int, step: Float): Float = -index * step

    // ------------------------------------------------------------------
    // Mechanic C — portal
    // ------------------------------------------------------------------

    /**
     * Perspective projection of a point tilted by rotX (pitch) and rotY
     * (yaw), in degrees, around the screen point (cx, cy). Points whose
     * rotated z recedes from the viewer pull toward the center; those that
     * come forward spread out.
     */
    fun project3DPoint(
        x: Float, y: Float, cx: Float, cy: Float,
        rotXDeg: Float, rotYDeg: Float, focal: Float = 850f
    ): Offset {
        val ax = Math.toRadians(rotXDeg.toDouble()).toFloat()
        val ay = Math.toRadians(rotYDeg.toDouble()).toFloat()
        val xx = x * cos(ay)
        val yy = y * cos(ax)
        val z = x * sin(ay) - y * sin(ax)
        val p = focal / (focal + z)
        return Offset(cx + xx * p, cy + yy * p)
    }

    /**
     * Perimeter samples of a rounded rect, clockwise from the top-right
     * corner: 4 corners × (steps + 1) points, 44 at the default. This is
     * the portal's window outline, ready for [project3DPoint].
     */
    fun sampleRoundedRectPoints(
        w: Float, h: Float, r: Float, steps: Int = 10
    ): List<Offset> {
        val rC = r.coerceAtMost(w / 2f).coerceAtMost(h / 2f)
        val out = ArrayList<Offset>(4 * (steps + 1))
        // corner arcs: (centerX, centerY, startAngle) clockwise from top-right
        val corners = listOf(
            Triple(w - rC, rC, -90f),        // top-right: 270°→360°
            Triple(w - rC, h - rC, 0f),      // bottom-right: 0°→90°
            Triple(rC, h - rC, 90f),         // bottom-left: 90°→180°
            Triple(rC, rC, 180f),            // top-left: 180°→270°
        )
        for ((cx0, cy0, start) in corners) {
            for (i in 0..steps) {
                val a = Math.toRadians((start + 90f * i / steps).toDouble())
                out += Offset(
                    cx0 + rC * cos(a).toFloat(),
                    cy0 + rC * sin(a).toFloat()
                )
            }
        }
        return out
    }

    /** Portal tilt targets, degrees, from a normalized pointer (0..1). */
    fun tiltYaw(pointerNX: Float): Float = (pointerNX - 0.5f) * 37.4f
    fun tiltPitch(pointerNY: Float): Float = (pointerNY - 0.5f) * -33f

    /** Expansion geometry at progress e (already eased): size and corner collapse to full screen. */
    fun portalWidth(e: Float, base: Float, screen: Float): Float = base + (screen - base) * e
    fun portalHeight(e: Float, base: Float, screen: Float): Float = base + (screen - base) * e
    fun portalCorner(e: Float, baseRadius: Float): Float = baseRadius * (1f - e)
}
