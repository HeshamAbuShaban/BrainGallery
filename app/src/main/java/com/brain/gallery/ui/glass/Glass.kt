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
 * of everything behind the pane. Layered translucency gets the same read for one
 * draw: a vertical white wash, a hairline edge, and a shadow underneath to lift
 * it off the content. That is what every dark UI here uses, and it is why the
 * nav can float over a playing video without the frame rate noticing.
 *
 * Order matters: shadow first so it is cast by the unclipped shape, then clip,
 * then the wash, then the edge on top of it.
 */
fun Modifier.glass(
    shape: Shape,
    style: GlassStyle = GlassStyle(),
    accentGlow: Boolean = true
): Modifier = this
    .shadow(
        elevation = style.elevation,
        shape = shape,
        spotColor = if (accentGlow) style.shadowTint() else style.shadowTint().copy(alpha = 0.5f),
        ambientColor = Color.Black
    )
    .clip(shape)
    .background(
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
    .border(style.borderWidth, style.borderColor(), shape)
