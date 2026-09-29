package com.brain.gallery.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.brain.gallery.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * The moving half of the nav: the pill that travels between tabs, and the small
 * reactions a tab makes when it becomes — or stops being — the selected one.
 *
 * Nothing here is tied to an app's palette. Colours arrive as parameters and the
 * only thing borrowed from the app is [Motion], so this file travels with the
 * glass into another project.
 *
 * Every animated value is exposed as a `State` or a lambda rather than a value,
 * and is read inside a layout or layer block. That is deliberate: reading an
 * animation in a composable body would recompose that composable on every frame,
 * which is exactly the cost a bar floating over a video cannot afford.
 */

/** How far the bar shrinks as it fades out. It recedes rather than blinking. */
private const val RecedeScale = 0.82f

/** The pill's animation, as values to be read rather than as values to be held. */
@Stable
class GlassNavIndicatorMotion internal constructor(
    private val travel: State<Float>,
    private val pillWidth: State<Float>,
    private val slotPx: () -> Float
) {
    /** Pixels from the bar's left edge to the pill's centre. */
    fun offsetX(): Float = slotPx() * travel.value

    /** One tab's share of the bar, in pixels. */
    fun slot(): Float = slotPx()

    /** The pill's current width. */
    fun width(): Float = pillWidth.value
}

/**
 * Where the pill should be.
 *
 * Position and width animate separately. Driving both from one fraction makes the
 * pill slide as a rigid block, and a pill that only slides reads as an overlay
 * being moved rather than an object being carried. Width follows the selected
 * tab's own content, so a tab with a long label gets a wider pill.
 *
 * The widths arrive as lambdas so a bar that has not been measured yet, or one
 * with a single tab, settles instead of dividing by nothing.
 */
@Composable
fun rememberGlassNavIndicatorMotion(
    selected: Int,
    count: Int,
    trackWidthPx: () -> Float,
    selectedWidthPx: () -> Float,
    insetPx: Float,
    minFraction: Float = 0.62f,
    fallbackFraction: Float = 0.74f
): GlassNavIndicatorMotion {
    val index = if (count > 0) selected.coerceIn(0, count - 1) else 0
    val slot = if (count > 0) trackWidthPx() / count else 0f
    val ceiling = (slot - insetPx * 2f).coerceAtLeast(0f)
    val tabWidth = selectedWidthPx()
    val wanted = when {
        ceiling <= 0f -> 0f
        tabWidth > 0f -> tabWidth + insetPx * 2f
        else -> slot * fallbackFraction
    }
    val targetWidth = if (ceiling > 0f) {
        val floor = (ceiling * minFraction).coerceIn(0f, ceiling)
        wanted.coerceIn(floor, ceiling)
    } else {
        0f
    }

    return GlassNavIndicatorMotion(
        travel = animateFloatAsState(
            targetValue = index.toFloat(),
            animationSpec = Motion.enter(),
            label = "navIndicatorTravel"
        ),
        pillWidth = animateFloatAsState(
            targetValue = targetWidth,
            animationSpec = Motion.bouncy(),
            label = "navIndicatorWidth"
        ),
        slotPx = { slot }
    )
}

/**
 * The bar's own opacity. Decoupled from the pill so hiding the bar never has to
 * think about the selection state, and so a hidden bar is cheap to draw.
 */
@Composable
fun rememberGlassNavAlpha(visible: Float): State<Float> = animateFloatAsState(
    targetValue = visible.coerceIn(0f, 1f),
    animationSpec = tween(Motion.mediumMs, easing = Motion.standard),
    label = "navAlpha"
)

/**
 * Fades the bar and shrinks it towards its bottom edge, so it looks like it has
 * stepped aside rather than vanished. Both are layer properties, so neither
 * relayouts the row of tabs.
 */
fun Modifier.glassNavReveal(alpha: () -> Float): Modifier = graphicsLayer {
    val a = alpha()
    this.alpha = a
    val s = RecedeScale + (1f - RecedeScale) * a
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/** 1f to 1.06f. A spring, so landing on a tab has a small overshoot to it. */
@Composable
fun rememberGlassNavIconScale(selected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (selected) 1.06f else 1f,
    animationSpec = Motion.bouncy(),
    label = "navIconScale"
)

/**
 * The label crossfade. Kept separate from the colour animation so an idle label
 * dims without its tint drifting, which keeps the row of labels even.
 */
@Composable
fun rememberGlassNavLabelAlpha(selected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (selected) 1f else 0.62f,
    animationSpec = Motion.enter(),
    label = "navLabelAlpha"
)

/** Scales a selected tab's icon without moving anything around it. */
fun Modifier.glassNavIconLayer(scale: () -> Float): Modifier = graphicsLayer {
    val s = scale()
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(0.5f, 0.5f)
}

fun Modifier.glassNavLabelLayer(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha()
}

/**
 * The pill's own glass: a shade lighter than the bar it rests on, with a smaller
 * shadow. Brightness is what separates it — on iOS the selected item is a second
 * piece of the material lying on top of the first, not a hole in it.
 */
@Composable
fun rememberGlassNavIndicatorStyle(style: GlassStyle): GlassStyle = remember(style) {
    style.copy(
        alpha = (style.alpha * 1.7f).coerceIn(0.06f, 0.24f),
        borderAlpha = (style.borderAlpha * 2.2f).coerceIn(0.16f, 0.42f),
        elevation = 8.dp,
        shadowAlpha = style.shadowAlpha * 0.6f
    )
}

/**
 * The travelling pill.
 *
 * The layout block always reports one tab's width, whatever the pill is drawn at,
 * so the row of tabs above it is never remeasured while it travels. Only this
 * node remeasures, and it has no children to walk. Travel is then a layer
 * translation on top of a fixed slot rather than a placement change, which is why
 * the pill cannot tear away from its tab mid-flight.
 */
@Composable
fun GlassNavIndicatorLayer(
    motion: GlassNavIndicatorMotion,
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassStyle
) {
    Box(
        modifier
            .layout { measurable, constraints ->
                val slot = motion.slot().roundToInt().coerceAtLeast(0)
                val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
                val w = motion.width().roundToInt().coerceIn(0, slot)
                if (w <= 0 || h <= 0) {
                    layout(0, 0) { }
                } else {
                    val pill = measurable.measure(Constraints.fixed(w, h))
                    layout(slot, h) { pill.placeRelative((slot - w) / 2, 0) }
                }
            }
            .graphicsLayer { translationX = motion.offsetX() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .glass(shape, style)
        )
    }
}
