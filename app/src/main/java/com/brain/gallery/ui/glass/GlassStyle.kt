package com.brain.gallery.ui.glass

import androidx.compose.ui.graphics.Color
import com.brain.gallery.ui.theme.PaletteHolder
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look of the glass, in one place.
 *
 * The values are the ones that make the material read as frosted on a dark UI:
 * a dark base that holds the pane together when it floats over bright content,
 * a low-opacity white wash, a lit top rim, a hairline edge to catch the
 * boundary, and a soft shadow to lift it off whatever is behind. Nothing here is
 * tied to the app's palette, so the material can be dropped into another project
 * and given that project's colours.
 */
data class GlassStyle(
    /**
     * The defaults read the palette switch, so a bare `GlassStyle()` — the
     * way every design-system widget builds its pane — is correct on both
     * themes with no call site thinking about it. Dark: a whisper of white
     * wash over the canvas and a real dark scrim under bright video. Light:
     * a true white frost over paper, the scrim dropped to a whisper (dark
     * ink on frost is readable by itself), and a soft cool shadow instead
     * of a black drop.
     */
    val tint: Color = Color.White,
    val alpha: Float = if (PaletteHolder.dark) 0.10f else 0.50f,
    val borderAlpha: Float = if (PaletteHolder.dark) 0.13f else 0.30f,
    val borderWidth: Dp = 1.dp,
    val bands: List<Float> = listOf(0.115f, 0.055f, 0.085f),
    val corner: Dp = 30.dp,
    val elevation: Dp = 20.dp,
    val shadowColor: Color =
        if (PaletteHolder.dark) Color.Black else Color(0xFF33415C),
    val shadowAlpha: Float = if (PaletteHolder.dark) 1f else 0.35f,
    /**
     * Dark laid under the wash, 0f..1f. The contrast guarantee over video:
     * a white wash alone can only lighten its backdrop, and a pane that
     * makes a bright frame brighter is a pane whose labels stop being
     * readable. Nearly invisible over dark content, doing its work exactly
     * where contrast fails. On the light theme the paper is already bright,
     * so it drops to a whisper.
     */
    val scrimAlpha: Float = if (PaletteHolder.dark) 0.6f else 0.08f,
    /** Lit top edge, 0f..1f, drawn in [tint]. See [glassSheen] for the moving half. */
    val rimAlpha: Float = if (PaletteHolder.dark) 0.17f else 0.5f
) {
    /** Band alphas, scaled by [alpha] so one knob governs the whole wash. */
    fun scaledBands(): List<Float> = bands.map { it * (alpha / 0.10f) }

    fun borderColor(): Color = tint.copy(alpha = borderAlpha)
    fun shadowTint(): Color = shadowColor.copy(alpha = shadowAlpha)
}

object Glass {
    /**
     * The pane this app uses everywhere. It reads the palette switch and
     * hands back the material tuned for what it will sit on: a white wash
     * with an accent glow over dark surfaces, a stronger frost with a soft
     * cool shadow over light ones. Same geometry either way — the glass is
     * the glass.
     */
    fun pane(accent: Color): GlassStyle = if (PaletteHolder.dark) GlassStyle(
        tint = Color.White,
        shadowColor = accent,
        shadowAlpha = 0.30f
    ) else GlassStyle(
        shadowColor = Color(0xFF33415C),
        shadowAlpha = 0.24f
    )

    /** A quieter pane for dense content that must stay readable. */
    fun subtle(): GlassStyle = if (PaletteHolder.dark) GlassStyle(
        alpha = 0.07f,
        borderAlpha = 0.10f,
        corner = 22.dp,
        elevation = 14.dp
    ) else GlassStyle(
        alpha = 0.42f,
        borderAlpha = 0.26f,
        corner = 22.dp,
        elevation = 12.dp,
        shadowColor = Color(0xFF33415C),
        shadowAlpha = 0.18f
    )

    /** A raised pane, for content that should sit clearly above the page. */
    fun raised(accent: Color): GlassStyle = if (PaletteHolder.dark) GlassStyle(
        alpha = 0.14f,
        borderAlpha = 0.18f,
        corner = 28.dp,
        elevation = 26.dp,
        shadowColor = accent,
        shadowAlpha = 0.35f
    ) else GlassStyle(
        alpha = 0.58f,
        borderAlpha = 0.34f,
        corner = 28.dp,
        elevation = 22.dp,
        shadowColor = Color(0xFF33415C),
        shadowAlpha = 0.26f
    )
}
