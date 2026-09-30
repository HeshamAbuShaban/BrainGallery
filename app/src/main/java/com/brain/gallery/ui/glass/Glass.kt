package com.brain.gallery.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Paints the glass onto any shape.
 *
 * True backdrop blur needs a platform window effect, which costs a blurred copy
 * of everything behind the pane. Layered translucency gets the same read for a
 * handful of draws: a dark base that keeps bright content from shining through,
 * a vertical white wash, a lit rim along the top edge, a hairline edge, and a
 * shadow underneath to lift it off the content. That is what every dark UI here
 * uses, and it is why the nav can float over a playing video without the frame
 * rate noticing.
 *
 * The base and the rim are both static and both drawn from brushes built while
 * the modifier is built, so a pane at rest costs one draw pass and no per-frame
 * allocation. [GlassStyle.scrimAlpha] is what keeps text on the pane legible
 * over bright video — the wash alone only lightens. [GlassStyle.rimAlpha] is the
 * light catch; the moving half of the reflection is the opt-in [glassSheen],
 * which a pane carries only while it is actually moving.
 *
 * Order matters: shadow first so it is cast by the unclipped shape, then clip,
 * then the layers, then the edge on top of them.
 */
fun Modifier.glass(
    shape: Shape,
    style: GlassStyle = GlassStyle(),
    accentGlow: Boolean = true
): Modifier {
    var painted = this
        .shadow(
            elevation = style.elevation,
            shape = shape,
            spotColor = if (accentGlow) style.shadowTint() else style.shadowTint().copy(alpha = 0.5f),
            ambientColor = Color.Black
        )
        .clip(shape)

    if (style.scrimAlpha > 0f) {
        painted = painted.background(Color.Black.copy(alpha = style.scrimAlpha))
    }

    painted = painted.background(
        Brush.verticalGradient(
            *style.scaledBands().let { alphas ->
                val n = alphas.size
                Array<Pair<Float, Color>>(n) { i ->
                    val at = if (n == 1) 0f else i.toFloat() / (n - 1).toFloat()
                    at to style.tint.copy(alpha = alphas[i].coerceIn(0f, 1f))
                }
            }
        )
    )

    if (style.rimAlpha > 0f) {
        painted = painted.background(
            Brush.verticalGradient(
                0f to style.tint.copy(alpha = style.rimAlpha),
                0.05f to style.tint.copy(alpha = style.rimAlpha * 0.45f),
                0.16f to Color.Transparent
            )
        )
    }

    return painted.border(style.borderWidth, style.borderColor(), shape)
}
