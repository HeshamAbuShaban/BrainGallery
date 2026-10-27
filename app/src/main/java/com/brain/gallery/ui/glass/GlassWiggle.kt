package com.brain.gallery.ui.glass
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
@Composable
fun rememberLiquidWiggle(enabled: Boolean = true): Pair<Float, Float> {
    if (!enabled) return 0f to 1f
    val t = rememberInfiniteTransition(label = "wiggle")
    val rot = t.animateFloat(-1.8f, 1.8f, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "wR")
    val s = t.animateFloat(0.985f, 1.015f, infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse), label = "wS")
    return rot.value to s.value
}
