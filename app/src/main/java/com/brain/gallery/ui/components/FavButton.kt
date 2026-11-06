package com.brain.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brain.gallery.ui.hero.rememberReducedMotion
import com.brain.gallery.ui.theme.Pink

/** Ray angles emitted when the heart is said yes to — eight short sparks. */
private val SPARK_ANGLES = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
private val SPARK_COLOR = Color(0xFFFFC857)

/**
 * The heart's whole act: a spring landing on the fill and eight small rays.
 * No redraw of the layout, no measurable cost on a grid of thousands — the
 * scale and the rays live in graphicsLayer/Canvas only.
 */
@Composable
fun FavIcon(
    favorite: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    activeColor: Color = Pink,
    tint: Color = Color.White,
    contentDescription: String? = null
) {
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(1f) }
    var burst by remember { mutableStateOf(false) }

    LaunchedEffect(favorite) {
        if (reduced) return@LaunchedEffect
        if (favorite) {
            burst = true
            pop.snapTo(0.42f)
            pop.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 620f))
            kotlinx.coroutines.delay(560)
            burst = false
        } else {
            pop.snapTo(0.78f)
            pop.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 760f))
        }
    }
    val burstProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (burst) 1f else 0f,
        animationSpec = if (burst) androidx.compose.animation.core.tween(520) else androidx.compose.animation.core.tween(0),
        label = "favBurst"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        if (burstProgress > 0.01f) {
            Canvas(Modifier.size(size * 2.4f)) {
                val inner = size.toPx() / 2f
                val outer = size.toPx() * 1.22f
                val r = outer * (0.30f + 0.70f * burstProgress)
                val alpha = (1f - burstProgress) * 0.9f
                SPARK_ANGLES.forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val dx = kotlin.math.cos(rad).toFloat()
                    val dy = kotlin.math.sin(rad).toFloat()
                    drawLine(
                        color = SPARK_COLOR.copy(alpha = alpha),
                        start = Offset(inner + dx * r * 0.5f, inner + dy * r * 0.5f),
                        end = Offset(inner + dx * r, inner + dy * r),
                        strokeWidth = size.toPx() * 0.11f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Icon(
            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = contentDescription,
            tint = if (favorite) activeColor else tint,
            modifier = Modifier.size(size).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
        )
    }
}

/**
 * The favorite action, everywhere the app offers it: tap, spring, haptic.
 * Bigger target than the icon suggests, actual press feedback instead of
 * nothing, and one vocabulary for "this one is loved".
 */
@Composable
fun FavButton(
    favorite: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tapTarget: Dp = 44.dp,
    activeColor: Color = Pink,
    tint: Color = Color.White,
    contentDescription: String? = if (favorite) "Unfavorite" else "Favorite"
) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(
                if (favorite) HapticFeedbackType.TextHandleMove
                else HapticFeedbackType.LongPress
            )
            onToggle(!favorite)
        },
        modifier = modifier.size(tapTarget)
    ) {
        FavIcon(
            favorite = favorite,
            size = size,
            activeColor = activeColor,
            tint = tint,
            contentDescription = contentDescription
        )
    }
}
