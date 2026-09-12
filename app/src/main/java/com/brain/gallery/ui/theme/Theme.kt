package com.brain.gallery.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

val Bg = Color(0xFF0D1117)
val Surface = Color(0xFF161B22)
val Accent = Color(0xFF8B5CF6)
val Pink = Color(0xFFEC4899)
val Green = Color(0xFF10B981)
val Cyan = Color(0xFF06B6D4)
val Yellow = Color(0xFFF59E0B)

private val Scheme = darkColorScheme(
    background = Bg, surface = Surface, primary = Accent,
    secondary = Cyan, tertiary = Pink
)

@Composable
fun BrainTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
