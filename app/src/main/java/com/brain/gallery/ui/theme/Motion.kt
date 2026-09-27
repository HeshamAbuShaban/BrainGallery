package com.brain.gallery.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion is a system, not ad-hoc numbers scattered through screens.
 * Durations in ms, easings named after their intent.
 */
object Motion {
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
}
