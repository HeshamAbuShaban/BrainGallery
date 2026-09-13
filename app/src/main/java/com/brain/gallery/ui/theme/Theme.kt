package com.brain.gallery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0E14)
val Surface = Color(0xFF151B26)
val Surface2 = Color(0xFF1D2534)
val Line = Color(0xFF2A3446)
val Text1 = Color(0xFFF2F5FA)
val Text2 = Color(0xFF9AA6B8)
val Text3 = Color(0xFF5F6B7E)
val Accent = Color(0xFF8B5CF6)
val AccentSoft = Color(0x338B5CF6)
val Pink = Color(0xFFEC4899)
val Green = Color(0xFF10B981)
val Cyan = Color(0xFF06B6D4)
val Yellow = Color(0xFFF59E0B)

val CardShape = RoundedCornerShape(20.dp)
val ChipShape = RoundedCornerShape(50.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

private val Scheme = darkColorScheme(
    background = Bg, surface = Surface, surfaceVariant = Surface2,
    primary = Accent, secondary = Cyan, tertiary = Pink,
    onBackground = Text1, onSurface = Text1
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
)

@Composable
fun BrainTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
