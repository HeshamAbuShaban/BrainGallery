package com.brain.gallery.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look of the glass, in one place.
 *
 * The values are the ones that make the material read as frosted on a dark UI:
 * a low-opacity white wash, a hairline edge to catch the boundary, and a soft
 * shadow to lift it off whatever is behind. Nothing here is tied to the app's
 * palette, so the material can be dropped into another project and given that
 * project's colours.
 */
data class GlassStyle(
    /** Tint of the pane. White works on dark surfaces; on light, pass a dark tint. */
    val tint: Color = Color.White,
    /** How much of [tint] to lay down. Low values are what read as glass. */
    val alpha: Float = 0.10f,
    /** Edge highlight. This is the line that makes the shape legible. */
    val borderAlpha: Float = 0.13f,
    val borderWidth: Dp = 1.dp,
    /** Blur is faked with layered translucency; see [bands]. */
    val bands: List<Float> = listOf(0.115f, 0.055f, 0.085f),
    val corner: Dp = 30.dp,
    val elevation: Dp = 20.dp,
    /** Colour of the shadow underneath. Accent for a glow, black for depth. */
    val shadowColor: Color = Color.Black,
    /** How much of [shadowColor] the shadow carries, 0f..1f. */
    val shadowAlpha: Float = 1f
) {
    /** Band alphas, scaled by [alpha] so one knob governs the whole wash. */
    fun scaledBands(): List<Float> = bands.map { it * (alpha / 0.10f) }

    fun borderColor(): Color = tint.copy(alpha = borderAlpha)
    fun shadowTint(): Color = shadowColor.copy(alpha = shadowAlpha)
}

object Glass {
    /** Read against a dark surface, tinted with the accent. */
    fun onDark(accent: Color) = GlassStyle(
        tint = Color.White,
        shadowColor = accent,
        shadowAlpha = 0.30f
    )

    /** A quieter pane for dense content that must stay readable. */
    fun subtle() = GlassStyle(
        alpha = 0.07f,
        borderAlpha = 0.10f,
        corner = 22.dp,
        elevation = 14.dp
    )

    /** A raised pane, for content that should sit clearly above the page. */
    fun raised(accent: Color) = GlassStyle(
        alpha = 0.14f,
        borderAlpha = 0.18f,
        corner = 28.dp,
        elevation = 26.dp,
        shadowColor = accent,
        shadowAlpha = 0.35f
    )
}
