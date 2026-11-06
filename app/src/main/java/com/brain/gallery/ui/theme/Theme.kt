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
 * A full palette, hand-picked. Six curated themes — the app's own Midnight,
 * four beloved editor-world classics retuned for a gallery, and a paper
 * light. A theme is not just a background: every token below reads from the
 * active palette, so switching repaints the entire app in one state write.
 *
 * The viewer's accent ([AccentHolder]) is deliberately separate from the
 * theme: the theme sets the room, the accent is the one thing you own.
 */
data class Palette(
    val id: String,
    val label: String,
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val elevated: Color,
    val line: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val pink: Color,
    val green: Color,
    val cyan: Color,
    val yellow: Color,
    val danger: Color,
    val dangerContainer: Color,
    val chipFill: Color,
    val hairline: Color
)

val PALETTES = listOf(
    Palette("midnight", "Midnight", true,
        bg = Color(0xFF0B0E14), surface = Color(0xFF151B26), surface2 = Color(0xFF1D2534),
        elevated = Color(0xFF222B3C), line = Color(0xFF2A3446),
        text1 = Color(0xFFF2F5FA), text2 = Color(0xFF9AA6B8), text3 = Color(0xFF5F6B7E),
        pink = Color(0xFFEC4899), green = Color(0xFF10B981), cyan = Color(0xFF06B6D4),
        yellow = Color(0xFFF59E0B), danger = Color(0xFFF87171),
        dangerContainer = Color(0xFF3A2410), chipFill = Color.White.copy(alpha = 0.06f), hairline = Color(0xFF1A2231)),
    Palette("mocha", "Catppuccin Mocha", true,
        bg = Color(0xFF1E1E2E), surface = Color(0xFF181825), surface2 = Color(0xFF313244),
        elevated = Color(0xFF45475A), line = Color(0xFF585B70),
        text1 = Color(0xFFCDD6F4), text2 = Color(0xFFA6ADC8), text3 = Color(0xFF9399B2),
        pink = Color(0xFFF5C2E7), green = Color(0xFFA6E3A1), cyan = Color(0xFF94E2D5),
        yellow = Color(0xFFF9E2AF), danger = Color(0xFFF38BA8),
        dangerContainer = Color(0xFF3B2A3A), chipFill = Color.White.copy(alpha = 0.07f), hairline = Color(0xFF3A2C50)),
    Palette("tokyo", "Tokyo Night", true,
        bg = Color(0xFF1A1B26), surface = Color(0xFF16161E), surface2 = Color(0xFF292E42),
        elevated = Color(0xFF2F334D), line = Color(0xFF3B4261),
        text1 = Color(0xFFC0CAF5), text2 = Color(0xFFA9B1D6), text3 = Color(0xFF565F89),
        pink = Color(0xFFBB9AF7), green = Color(0xFF9ECE6A), cyan = Color(0xFF7DCFFF),
        yellow = Color(0xFFE0AF68), danger = Color(0xFFF7768E),
        dangerContainer = Color(0xFF3A2A3C), chipFill = Color.White.copy(alpha = 0.07f), hairline = Color(0xFF1F2335)),
    Palette("github", "GitHub Dark", true,
        bg = Color(0xFF0D1117), surface = Color(0xFF161B22), surface2 = Color(0xFF21262D),
        elevated = Color(0xFF293039), line = Color(0xFF30363D),
        text1 = Color(0xFFE6EDF3), text2 = Color(0xFF8B949E), text3 = Color(0xFF6E7681),
        pink = Color(0xFFDB61A2), green = Color(0xFF3FB950), cyan = Color(0xFF58A6FF),
        yellow = Color(0xFFD29922), danger = Color(0xFFF85149),
        dangerContainer = Color(0xFF2D1419), chipFill = Color.White.copy(alpha = 0.06f), hairline = Color(0xFF1E242C)),
    Palette("paper", "Paper", false,
        bg = Color(0xFFF3F5F9), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE7ECF3),
        elevated = Color(0xFFFFFFFF), line = Color(0xFFD5DCE6),
        text1 = Color(0xFF12161F), text2 = Color(0xFF59637A), text3 = Color(0xFF8C97AB),
        pink = Color(0xFFDB2777), green = Color(0xFF059669), cyan = Color(0xFF0E7490),
        yellow = Color(0xFFB45309), danger = Color(0xFFDC2626),
        dangerContainer = Color(0xFFFEF3E2), chipFill = Color(0xFF12161F).copy(alpha = 0.06f), hairline = Color(0xFFE1E7EF)),
    Palette("latte", "Catppuccin Latte", false,
        bg = Color(0xFFEFF1F5), surface = Color(0xFFE6E9EF), surface2 = Color(0xFFCCD0DA),
        elevated = Color(0xFFFFFFFF), line = Color(0xFFBCC0CC),
        text1 = Color(0xFF4C4F69), text2 = Color(0xFF5C5F77), text3 = Color(0xFF8C8FA1),
        pink = Color(0xFFEA76CB), green = Color(0xFF40A02B), cyan = Color(0xFF179299),
        yellow = Color(0xFFDF8E1D), danger = Color(0xFFD20F39),
        dangerContainer = Color(0xFFF9E6E6), chipFill = Color(0xFF4C4F69).copy(alpha = 0.07f), hairline = Color(0xFFDCE0E8))
)

object PaletteHolder {
    var theme by mutableStateOf(PALETTES.first())

    /**
     * The old one-boolean seam, kept alive while the theme picker catches up:
     * dark lands on Midnight, light on the last curated light palette. New
     * code should set [theme]; this only exists so a "dark/light" preference
     * already on disk still means something.
     */
    var dark: Boolean
        get() = theme.dark
        set(value) { theme = if (value) PALETTES.first() else PALETTES.last() }

    /** Unknown ids fall back to Midnight — a setting can never break the app. */
    fun byId(id: String): Palette = PALETTES.firstOrNull { it.id == id } ?: PALETTES.first()
}

val Bg: Color get() = PaletteHolder.theme.bg
val Surface: Color get() = PaletteHolder.theme.surface
val Surface2: Color get() = PaletteHolder.theme.surface2
val Elevated: Color get() = PaletteHolder.theme.elevated
val Line: Color get() = PaletteHolder.theme.line
val Text1: Color get() = PaletteHolder.theme.text1
val Text2: Color get() = PaletteHolder.theme.text2
val Text3: Color get() = PaletteHolder.theme.text3

/** Fill for interactive chips on flat pages — subtle on either theme. */
val ChipFill: Color get() = PaletteHolder.theme.chipFill

/**
 * The respectful ink for any fill: black-ish ink on bright accents, white
 * ink on deep ones — decided by luminance, never by taste. One helper so a
 * chip on violet and a chip on amber can never disagree about which ink
 * they carry.
 */
fun inkOn(fill: Color): Color =
    if (1.05 - (0.299 * fill.red + 0.587 * fill.green + 0.114 * fill.blue) * fill.alpha
        > 0.35) Color.White else Color(0xFF12161F)
/**
 * The one accent everything reads. It is a getter over observable state, not
 * a constant, so picking a color in settings repaints the whole app without
 * touching a single call site — and non-composable readers just get the
 * current value.
 */
object AccentHolder { var argb by mutableLongStateOf(0xFF8B5CF6L) }
val Accent: Color get() = Color(AccentHolder.argb)
val AccentSoft: Color get() = Accent.copy(alpha = if (PaletteHolder.theme.dark) 0.2f else 0.12f)
// The semantic accents come from the palette, so Catppuccin reads as
// Catppuccin and Tokyo reads as Tokyo — not as the app wearing a wig.
val Pink: Color get() = PaletteHolder.theme.pink
val Green: Color get() = PaletteHolder.theme.green
val Cyan: Color get() = PaletteHolder.theme.cyan
val Yellow: Color get() = PaletteHolder.theme.yellow
val Danger: Color get() = PaletteHolder.theme.danger
val DangerContainer: Color get() = PaletteHolder.theme.dangerContainer
/** The hairline every divider in the app sits on — derived per palette, so
 * Tokyo's dividers never vanish into Tokyo's background again. */
val Hairline: Color get() = PaletteHolder.theme.hairline

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
    val scheme = if (PaletteHolder.theme.dark)
        remember(AccentHolder.argb, PaletteHolder.theme) { darkScheme(Accent) }
    else
        remember(AccentHolder.argb, PaletteHolder.theme) { lightScheme(Accent) }

    // The system bars read the theme too: dark ink icons over light paper,
    // light icons over the dark canvas. The reel and the players stay dark
    // regardless — they paint their own full-bleed world.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.LaunchedEffect(PaletteHolder.theme) {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !PaletteHolder.theme.dark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
