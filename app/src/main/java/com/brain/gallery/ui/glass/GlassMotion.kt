package com.brain.gallery.ui.glass

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens for the kit: durations in ms, easings named by their intent.
 * A small named system beats raw numbers scattered through screens — one place
 * to tune the feel, and no guesswork about which duration a call site meant.
 * Lives here so the folder carries its own motion when copied elsewhere.
 */
object GlassMotion {
    // Durations
    const val instantMs = 120
    const val fastMs = 180
    const val mediumMs = 260
    const val slowMs = 420
    const val deliberateMs = 620

    // Easings
    /** Material 3 "emphasized" — quick departure, gentle arrival. Good for hero moves. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Standard curve for size/opacity changes. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
    /** Sharp exit. */
    val exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    /** Playful overshoot-free spring for toggles and nav. */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Standard tween of [mediumMs] using the emphasized curve. */
    fun <T> enter(): FiniteAnimationSpec<T> = tween(mediumMs, easing = emphasized)

    /**
     * Underdamped on purpose: the value overshoots and wobbles once or twice
     * before settling. A tween lands flat, and a pill that lands flat reads as a
     * highlight being switched on. This is the droplet arriving, splashing a
     * little past its mark, and coming to rest — the feel the nav needs.
     */
    fun <T> liquid(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
}
