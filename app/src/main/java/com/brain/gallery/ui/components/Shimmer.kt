package com.brain.gallery.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brain.gallery.ui.theme.Surface2

@Composable
fun ShimmerBar(modifier: Modifier = Modifier, height: Dp = 16.dp, radius: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "x")
    Box(modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(radius))
        .background(Brush.linearGradient(
            listOf(Surface2, Color(0xFF2A3547), Surface2),
            start = Offset(x * 600f - 200f, 0f), end = Offset(x * 600f + 200f, 0f))))
}
