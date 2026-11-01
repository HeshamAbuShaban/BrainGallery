@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.brain.gallery.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Elevated
import com.brain.gallery.ui.theme.Line
import com.brain.gallery.ui.theme.Text2

/**
 * The slider that belongs to this app. The stock M3 slider is a hairline and
 * a bead — cheap against a glass identity. This one is a pill of the same
 * family as the nav: a 10dp track that fills with the accent, step dots that
 * light up as the thumb passes, and a thumb that is a small glass button.
 *
 * The interaction is stock M3 — dragging, steps, semantics, a11y — only the
 * paint is ours. Every settings screen uses this; none carries its own
 * slider styling.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        interactionSource = interaction,
        modifier = modifier,
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
            }
        },
        track = { st ->
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
            val frac = ((st.value - valueRange.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                val y = size.height / 2f
                val th = 10.dp.toPx()
                val r = th / 2f
                drawRoundRect(
                    color = Line,
                    cornerRadius = CornerRadius(r),
                    topLeft = Offset(0f, y - th / 2f),
                    size = Size(size.width, th)
                )
                if (frac > 0f) {
                    drawRoundRect(
                        color = Accent,
                        cornerRadius = CornerRadius(r),
                        topLeft = Offset(0f, y - th / 2f),
                        size = Size(size.width * frac, th)
                    )
                }
                if (steps > 0) for (i in 1..steps) {
                    val x = size.width * i / (steps + 1f)
                    drawCircle(
                        color = if (x <= size.width * frac) Color.White.copy(alpha = 0.9f) else Elevated,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    )
}

/**
 * The switch that belongs to this app: accent track when on, a quiet Line
 * track when off, a white thumb either way. One definition, used by every
 * settings surface — the colors can never drift apart again.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Accent.copy(alpha = 0.85f),
            uncheckedThumbColor = Text2,
            uncheckedTrackColor = Line,
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent
        )
    )
}
