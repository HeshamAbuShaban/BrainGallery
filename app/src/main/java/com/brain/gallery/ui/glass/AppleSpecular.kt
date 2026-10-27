package com.brain.gallery.ui.glass
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
@Composable
fun rememberAppleSpecular(velocityPx: Float = 0f): Brush {
    return Brush.radialGradient(colors = listOf(Color.White.copy(alpha=0.6f), Color.Transparent), center = Offset(0.5f,0.2f), radius = 0.8f)
}
