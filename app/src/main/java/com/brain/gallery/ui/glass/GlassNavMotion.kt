package com.brain.gallery.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The moving half of the nav: the pill that travels between tabs, and the small
 * reactions a tab makes when it becomes — or stops being — the selected one.
 *
 * Nothing here is tied to an app's palette. Colours arrive as parameters and the
 * motion tokens live in [GlassMotion], so this file travels with the glass into
 * another project.
 *
 * Every animated value is exposed as a `State` or a lambda rather than a value,
 * and is read inside a layout or layer or draw block. That is deliberate: reading
 * an animation in a composable body would recompose that composable on every
 * frame, which is exactly the cost a bar floating over a video cannot afford.
 */

/** How far the bar shrinks as it fades out. It recedes rather than blinking. */
private const val RecedeScale = 0.82f

/**
 * Share of the bar's height the pill keeps clear above and below itself.
 * 11% of the default 56.dp bar is ~6.dp, which is enough for the bar's own
 * fill to show through the gap and read the pill as lying *on* the bar.
 */
const val GlassNavIndicatorVerticalInsetFraction = 0.11f

/** The bar height [GlassNav] defaults to, so the default inset stays ~6.dp. */
private val DefaultBarHeight = 56.dp

/** Never let the gap round itself away, however short the bar is. */
private val MinGlassNavIndicatorVerticalInset = 2.dp

/**
 * Stretch gained per slot-per-second of travel, and the ceiling on it.
 * A full tab-to-tab hop peaks around 6-10 slots/sec, so the cap is what keeps a
 * fast flick from turning the pill into a smear across the whole bar.
 */
private const val VelocityStretch = 0.03f
private const val MaxStretch = 0.22f

/**
 * How much height the pill gives up to gain length. Area is roughly preserved,
 * which is what makes it read as a droplet deforming rather than a box scaling.
 */
private const val SquashRatio = 0.62f

/** Where the highlight sits at rest, and how far travel drags it. */
private const val SheenRest = 0.35f
private const val SheenTravel = 0.05f

/**
 * The vertical gap, top and bottom, between the pill and the bar's edge.
 *
 * Derived from the bar's own height rather than pinned to a constant, so a
 * bar passed a different [Dp] keeps the same proportion. The floor only bites
 * on bars so short that 11% would round to nothing; the layer also clamps the
 * gap to half the measured height, so the pill can never invert or overflow.
 */
fun glassNavIndicatorVerticalInset(barHeight: Dp): Dp =
    (barHeight * GlassNavIndicatorVerticalInsetFraction)
        .coerceAtLeast(MinGlassNavIndicatorVerticalInset)

/**
 * The pill's animation, as values to be read rather than as values to be held.
 *
 * Position comes from a raw [Animatable] rather than `animateFloatAsState`
 * because the deformation needs the velocity, and only [Animatable] exposes it.
 * A tween gives no velocity at all, which is why the pill used to snap between
 * tabs like a highlight being switched on instead of moving like something
 * being carried.
 *
 * Velocity has two sources and they do not overlap. Springing between tabs, the
 * [Animatable] knows its own speed. Under a finger it does not: the pill is
 * snapped to the finger each frame, and a snap completes instantly, so the
 * reported velocity sits at zero for the entire drag — which is the moment the
 * stretch matters most. That case is handed in from the gesture instead.
 */
@Stable
class GlassNavIndicatorMotion internal constructor(
    private val travel: Animatable<Float, AnimationVector1D>,
    private val pillWidth: State<Float>,
    private val slotPx: () -> Float,
    private val isDragging: () -> Boolean,
    private val dragVelocity: () -> Float
) {
    /** Pixels from the bar's left edge to the pill's centre. */
    fun offsetX(): Float = slotPx() * travel.value

    /** One tab's share of the bar, in pixels. */
    fun slot(): Float = slotPx()

    /** The pill's current width. */
    fun width(): Float = pillWidth.value

    /** Travel speed in slots per second, whichever source is live. */
    fun velocity(): Float = if (isDragging()) dragVelocity() else travel.velocity

    /** How much longer than rest the pill currently is. 1f is still. */
    fun stretch(): Float =
        1f + (abs(velocity()) * VelocityStretch).coerceAtMost(MaxStretch)

    /** How much shorter it is, so the two roughly trade against each other. */
    fun squash(): Float = 1f - (stretch() - 1f) * SquashRatio

    /** 0..1 across the pill: where the reflected light is sitting. */
    fun sheen(): Float =
        (SheenRest + velocity() * SheenTravel).coerceIn(0.06f, 0.94f)
}

/**
 * Where the pill should be.
 *
 * Position and width animate separately. Driving both from one fraction makes the
 * pill slide as a rigid block, and a pill that only slides reads as an overlay
 * being moved rather than an object being carried. Width follows the selected
 * tab's own content, so a tab with a long label gets a wider pill.
 *
 * [dragTargetPx] is the finger. While it is non-null the pill tracks it exactly
 * and the spring is bypassed, because a pill that lags behind the finger reads
 * as broken rather than fluid. The moment it goes null the spring takes over and
 * liquid-floats onto the tab the finger left behind.
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
    dragTargetPx: () -> Float? = { null },
    dragVelocityPx: () -> Float = { 0f },
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

    val travel = remember(count) {
        Animatable(if (count > 0) index.toFloat() else 0f)
    }
    val dragging = dragTargetPx() != null

    LaunchedEffect(dragging, index, count) {
        if (dragging) {
            snapshotFlow { dragTargetPx() to trackWidthPx() }
                .collect { (finger, width) ->
                    if (finger != null && count > 0 && width > 0f) {
                        val s = width / count
                        if (s > 0f) {
                            // Half a slot of give either side, so the pill can be
                            // parked between tabs instead of snapping to one.
                            travel.snapTo(
                                (finger / s).coerceIn(-0.5f, count - 0.5f)
                            )
                        }
                    }
                }
        } else {
            travel.animateTo(index.toFloat(), GlassMotion.liquid())
        }
    }

    return GlassNavIndicatorMotion(
        travel = travel,
        pillWidth = animateFloatAsState(
            targetValue = targetWidth,
            animationSpec = GlassMotion.bouncy(),
            label = "navIndicatorWidth"
        ),
        slotPx = { if (count > 0) trackWidthPx() / count else 0f },
        isDragging = { dragTargetPx() != null },
        dragVelocity = dragVelocityPx
    )
}

/**
 * The bar's own opacity. Decoupled from the pill so hiding the bar never has to
 * think about the selection state, and so a hidden bar is cheap to draw.
 */
@Composable
fun rememberGlassNavAlpha(visible: Float): State<Float> = animateFloatAsState(
    targetValue = visible.coerceIn(0f, 1f),
    animationSpec = tween(GlassMotion.mediumMs, easing = GlassMotion.standard),
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
    animationSpec = GlassMotion.bouncy(),
    label = "navIconScale"
)

/**
 * The label crossfade. Kept separate from the colour animation so an idle label
 * dims without its tint drifting, which keeps the row of labels even.
 */
@Composable
fun rememberGlassNavLabelAlpha(selected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (selected) 1f else 0.62f,
    animationSpec = GlassMotion.liquid(),
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
 * The light on the pill, not the pill itself.
 *
 * Two things sell a surface as glass rather than as a grey rounded rect: a
 * brighter rim along the top edge where light catches it, and a specular
 * reflection that slides across it. The second is what makes the pill feel wet —
 * it rides the travel velocity, so the reflection sweeps forward as the pill
 * starts and swings back as it overshoots, exactly the way a highlight behaves
 * on a moving droplet. At rest it sits still, costing nothing.
 *
 * Drawn after the material and clipped back to the same shape, because the
 * sheen has no meaning outside the pill's own outline.
 */
fun Modifier.glassSheen(shape: Shape, sheen: () -> Float): Modifier = this
    .clip(shape)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.17f),
                0.20f to Color.White.copy(alpha = 0.04f),
                0.42f to Color.Transparent,
                1f to Color.Transparent
            )
        )
        val w = size.width
        if (w > 1f) {
            val cx = w * sheen()
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.5f to Color.White.copy(alpha = 0.09f),
                    1f to Color.Transparent
                ),
                radius = size.height * 1.25f,
                center = Offset(cx, size.height * 0.22f)
            )
        }
    }

/**
 * The travelling pill.
 *
 * The layout block always reports one tab's width, whatever the pill is drawn at,
 * so the row of tabs above it is never remeasured while it travels. Only this
 * node remeasures, and it has no children to walk. Travel is then a layer
 * translation on top of a fixed slot rather than a placement change, which is why
 * the pill cannot tear away from its tab mid-flight.
 *
 * The deformation is a layer transform for the same reason: stretching the pill
 * by changing its measured width would ask for a measure pass every frame, while
 * scaling the layer asks for neither. The node is sized to exactly one slot and
 * the pill is centred in it both horizontally and vertically, so scaling about
 * the node's centre scales the pill about its own centre and it cannot drift.
 *
 * [verticalInset] is what keeps the pill a pill: the node still occupies the
 * bar's full height, but the child is measured at that height minus the inset
 * top and bottom and placed that far down, so the bar's fill shows through the
 * gap instead of a second full-bleed border doubling the first.
 */
@Composable
fun GlassNavIndicatorLayer(
    motion: GlassNavIndicatorMotion,
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassStyle,
    verticalInset: Dp = glassNavIndicatorVerticalInset(DefaultBarHeight)
) {
    val insetPx = with(LocalDensity.current) { verticalInset.toPx() }
    Box(
        modifier
            .layout { measurable, constraints ->
                val slot = motion.slot().roundToInt().coerceAtLeast(0)
                val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
                val inset = insetPx.roundToInt().coerceIn(0, h / 2)
                val pillH = h - inset * 2
                val w = motion.width().roundToInt().coerceIn(0, slot)
                if (w <= 0 || pillH <= 0) {
                    layout(0, 0) { }
                } else {
                    val pill = measurable.measure(Constraints.fixed(w, pillH))
                    layout(slot, h) { pill.placeRelative((slot - w) / 2, inset) }
                }
            }
            .graphicsLayer {
                translationX = motion.offsetX()
                scaleX = motion.stretch()
                scaleY = motion.squash()
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .glass(shape, style)
                .glassSheen(shape) { motion.sheen() }
        )
    }
}
