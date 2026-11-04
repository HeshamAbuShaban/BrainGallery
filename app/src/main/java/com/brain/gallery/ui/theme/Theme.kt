package com.brain.gallery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The palette switch. One boolean flips every token in the app — the tokens
 * below are getters over this state, so a composable that reads a color
 * subscribes and repaints the moment the viewer flips the switch in Tune.
 * The reel, the spotlight and the photo viewer stay dark in both themes:
 * they are video surfaces, and video surfaces are dark.
 */
object PaletteHolder { var dark by mutableStateOf(true) }

val Bg: Color get() = if (PaletteHolder.dark) Color(0xFF0B0E14) else Color(0xFFF3F5F9)
val Surface: Color get() = if (PaletteHolder.dark) Color(0xFF151B26) else Color(0xFFFFFFFF)
val Surface2: Color get() = if (PaletteHolder.dark) Color(0xFF1D2534) else Color(0xFFE7ECF3)
val Elevated: Color get() = if (PaletteHolder.dark) Color(0xFF222B3C) else Color(0xFFFFFFFF)
val Line: Color get() = if (PaletteHolder.dark) Color(0xFF2A3446) else Color(0xFFD5DCE6)
val Text1: Color get() = if (PaletteHolder.dark) Color(0xFFF2F5FA) else Color(0xFF12161F)
val Text2: Color get() = if (PaletteHolder.dark) Color(0xFF9AA6B8) else Color(0xFF59637A)
val Text3: Color get() = if (PaletteHolder.dark) Color(0xFF5F6B7E) else Color(0xFF8C97AB)

/** Fill for interactive chips on flat pages — subtle on either theme. */
val ChipFill: Color get() =
    if (PaletteHolder.dark) Color.White.copy(alpha = 0.06f)
    else Color(0xFF12161F).copy(alpha = 0.06f)
/**
 * The one accent everything reads. It is a getter over observable state, not
 * a constant, so picking a color in settings repaints the whole app without
 * touching a single call site — and non-composable readers just get the
 * current value.
 */
object AccentHolder { var argb by mutableLongStateOf(0xFF8B5CF6L) }
val Accent: Color get() = Color(AccentHolder.argb)
val AccentSoft: Color get() = Accent.copy(alpha = if (PaletteHolder.dark) 0.2f else 0.12f)
// Accents shift a step darker on light paper so text and fills keep their
// weight; the viewer's accent (AccentHolder) stays exactly as picked.
val Pink: Color get() = if (PaletteHolder.dark) Color(0xFFEC4899) else Color(0xFFDB2777)
val Green: Color get() = if (PaletteHolder.dark) Color(0xFF10B981) else Color(0xFF059669)
val Cyan: Color get() = if (PaletteHolder.dark) Color(0xFF06B6D4) else Color(0xFF0E7490)
val Yellow: Color get() = if (PaletteHolder.dark) Color(0xFFF59E0B) else Color(0xFFB45309)
val Danger: Color get() = if (PaletteHolder.dark) Color(0xFFF87171) else Color(0xFFDC2626)
val DangerContainer: Color get() =
    if (PaletteHolder.dark) Color(0xFF3A2410) else Color(0xFFFEF3E2)
/** The hairline every divider in the app sits on. */
val Hairline = Color(0xFF1A2231)

val CardShape = RoundedCornerShape(20.dp)
val ChipShape = RoundedCornerShape(50.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

private fun darkScheme(accent: Color) = darkColorScheme(
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    surfaceContainerHigh = Elevated,
    primary = accent,
    secondary = Cyan,
    tertiary = Pink,
    error = Danger,
    onBackground = Text1,
    onSurface = Text1,
    onSurfaceVariant = Text2,
    outline = Line
)

private fun lightScheme(accent: Color) = lightColorScheme(
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    surfaceContainerHigh = Elevated,
    primary = accent,
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
    // Reading the holders subscribes: a new accent or a flipped theme
    // rebuilds the scheme once.
    val scheme = if (PaletteHolder.dark)
        remember(AccentHolder.argb) { darkScheme(Accent) }
    else
        remember(AccentHolder.argb) { lightScheme(Accent) }

    // The system bars read the theme too: dark ink icons over light paper,
    // light icons over the dark canvas. The reel and the players stay dark
    // regardless — they paint their own full-bleed world.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.LaunchedEffect(PaletteHolder.dark) {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !PaletteHolder.dark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
