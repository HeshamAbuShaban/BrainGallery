package com.brain.gallery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0E14)
val Surface = Color(0xFF151B26)
val Surface2 = Color(0xFF1D2534)
val Elevated = Color(0xFF222B3C)
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
val Danger = Color(0xFFF87171)

val CardShape = RoundedCornerShape(20.dp)
val ChipShape = RoundedCornerShape(50.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

private val Scheme = darkColorScheme(
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    surfaceContainerHigh = Elevated,
    primary = Accent,
    secondary = Cyan,
    tertiary = Pink,
    error = Danger,
    onBackground = Text1,
    onSurface = Text1,
    onSurfaceVariant = Text2,
    outline = Line
)

/** Monospace is used for anything numeric so counters and durations don't jitter. */
val Numeral = FontFamily.Monospace

private val Type = Typography(
    displaySmall = TextStyle(
        fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Text2),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(
        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
    )
)

@Composable
fun BrainTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
