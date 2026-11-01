package com.brain.gallery.ui.hero

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import kotlin.math.abs

/**
 * True when the user asked the system for reduced motion. The mechanics
 * freeze to their static frame when this is on — taps still work, drift,
 * momentum and settle springs do not run.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Mechanic A — the cylindrical shelf.
 *
 * A short ceremonial strip on the Organize hub: featured groups leaning on
 * the inside of a cylinder. Cards near the edges spread wider, their outer
 * vertical edge grows taller (q^2.6), the center line rises by d², and each
 * card turns to face the middle. Drag is 1:1; release velocity decays until
 * it hands over to a gentle ambient drift that bounces at the bounds.
 *
 * Constitution: math lives in [HeroMath]; transforms run inside
 * graphicsLayer lambdas so dragging never recomposes; one gesture authority
 * on the container (the cards are pure visuals); light haptic on drag
 * start, medium on open; reduced motion freezes drift and momentum.
 *
 * One mechanic per screen — nothing else lands on the hub.
 */
@Composable
fun ShelfCarousel(
    items: List<SmartGroup>,
    onOpen: (SmartGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()

    val cardWidth = 132.dp
    val cardHeight = 170.dp
    val gap = 12.dp
    val cardWidthPx = with(density) { cardWidth.toPx() }
    val stepPx = with(density) { (cardWidth + gap).toPx() }
    val contentWidthPx = with(density) { ((cardWidth + gap) * items.size - gap).toPx() }

    // The one moving number. Written by gestures and the frame loop, read
    // only inside graphicsLayer lambdas — dragging never recomposes.
    val scroll = remember { mutableFloatStateOf(0f) }
    val releaseV = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var driftDir by remember { mutableFloatStateOf(-1f) }
    var stripWidthPx by remember { mutableIntStateOf(0) }
    // Release velocity, tracked as an exponential average over drag deltas —
    // foundation's drag detector hands back no velocity, so the gesture
    // carries its own.
    var lastDragMs by remember { mutableLongStateOf(-1L) }
    var emaV by remember { mutableFloatStateOf(0f) }

    // Widest useful scroll: the last card's right edge meets the viewport's.
    fun scrollMax(): Float =
        (contentWidthPx - stripWidthPx + cardWidthPx).coerceAtLeast(0f)

    fun clamp() {
        scroll.floatValue = scroll.floatValue.coerceIn(0f, scrollMax())
    }

    // Momentum decay, then ambient drift (~30dp/s) bouncing at the bounds.
    // Runs only while hands are off; frozen under reduced motion.
    LaunchedEffect(dragging, reduced) {
        if (dragging || reduced) return@LaunchedEffect
        var v = 0f
        var last = -1L
        val stopPx = with(density) { HeroMath.STOP_VELOCITY.dp.toPx() }
        while (true) {
            withFrameNanos { now ->
                if (last < 0) { last = now; return@withFrameNanos }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(1e-4f, 0.05f)
                last = now
                if (releaseV.floatValue != 0f) {
                    v = releaseV.floatValue
                    releaseV.floatValue = 0f
                }
                if (v != 0f) {
                    // emaV tracked the scroll's own velocity during the drag,
                    // so momentum continues it, sign included.
                    scroll.floatValue += v * dt
                    v = HeroMath.decayVelocity(v, dt)
                    if (abs(v) < stopPx) v = 0f
                } else {
                    scroll.floatValue -= with(density) { 30.dp.toPx() } * dt * driftDir
                }
                if (scroll.floatValue <= 0f) { scroll.floatValue = 0f; driftDir = 1f }
                if (scroll.floatValue >= scrollMax()) {
                    scroll.floatValue = scrollMax(); driftDir = -1f
                }
            }
        }
    }

    val liveItems by rememberUpdatedState(items)
    val liveOpen by rememberUpdatedState(onOpen)
    Box(modifier
        .fillMaxWidth()
        .height(cardHeight)
        .onSizeChanged { stripWidthPx = it.width }
        .pointerInput(items.size) {
            // Tap maps a position to a card: the cards themselves carry no
            // gesture handling, so a drag can start anywhere on the strip.
            detectTapGestures { off ->
                val idx = (((off.x + scroll.floatValue) / stepPx).toInt())
                    .coerceIn(liveItems.indices)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                liveOpen(liveItems[idx])
            }
        }
        .pointerInput(items.size) {
            detectDragGestures(
                onDragStart = {
                    dragging = true
                    lastDragMs = -1L
                    emaV = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onDragEnd = {
                    releaseV.floatValue = emaV
                    dragging = false
                    clamp()
                },
                onDragCancel = { dragging = false },
                onDrag = { change, dragAmount ->
                    change.consume()
                    scroll.floatValue -= dragAmount.x
                    val now = change.uptimeMillis
                    if (lastDragMs > 0L) {
                        val dt = (now - lastDragMs) / 1000f
                        if (dt > 0f) {
                            emaV = emaV * 0.65f + (-dragAmount.x / dt) * 0.35f
                        }
                    }
                    lastDragMs = now
                    clamp()
                })
        }
    ) {
        items.forEachIndexed { i, g ->
            ShelfCard(
                group = g,
                scroll = scroll,
                baseX = stepPx * i,
                cardWidthPx = cardWidthPx,
                stripWidthPx = stripWidthPx,
                radiusPx = with(density) { 20.dp.toPx() },
                reduced = reduced,
                modifier = Modifier.align(Alignment.CenterStart)
                    .width(cardWidth)
                    .height(cardHeight)
            )
        }
    }
}

@Composable
private fun ShelfCard(
    group: SmartGroup,
    scroll: State<Float>,
    baseX: Float,
    cardWidthPx: Float,
    stripWidthPx: Int,
    radiusPx: Float,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier.graphicsLayer {
        if (stripWidthPx <= 0) return@graphicsLayer
        val xc = stripWidthPx / 2f
        val x = baseX - scroll.value
        val t = x - xc
        val d = HeroMath.cylindricalDepth(x + cardWidthPx / 2f, stripWidthPx.toFloat())
        val qL = HeroMath.cylindricalDepth(x, stripWidthPx.toFloat())
        val qR = HeroMath.cylindricalDepth(x + cardWidthPx, stripWidthPx.toFloat())

        translationX = HeroMath.pushedX(t, xc)
        translationY = -14.dp.toPx() * HeroMath.centerYRise(d)
        rotationY = -(t / xc).coerceIn(-1f, 1f) * 14f
        scaleX = 1f + 0.06f * HeroMath.cardWidthGain(d)
        scaleY = 1f + 0.05f * HeroMath.cardWidthGain(d)
        alpha = (0.5f + 0.5f * (1f - d.coerceAtMost(1f)))
        shape = ShelfShape(qL, qR, radiusPx)
        clip = true
    }) {
        val cover = group.videos.firstOrNull()
        if (cover != null) {
            VideoThumb(cover, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1D2534)))
        }
        // Raw type only: name and one metadata line. No pills, no chips.
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(group.title, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Text(group.subtitle, color = Text2, fontSize = 10.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The cylindrical silhouette. Each vertical edge carries its own height —
 * the outer edge grows by q^2.6 ([HeroMath.edgeLift]) — so the strip reads
 * as one curved surface. At zero depth this collapses to the plain 20dp
 * rounded container the rest of the app uses.
 */
private class ShelfShape(
    private val qL: Float,
    private val qR: Float,
    private val radiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val hL = size.height * (1f + 0.10f * HeroMath.edgeLift(qL.coerceIn(0f, 1f)))
        val hR = size.height * (1f + 0.10f * HeroMath.edgeLift(qR.coerceIn(0f, 1f)))
        val w = size.width
        val r = radiusPx
            .coerceAtMost(w / 2f)
            .coerceAtMost(minOf(hL, hR) / 2f)
        val leftTop = (size.height - hL) / 2f
        val leftBot = (size.height + hL) / 2f
        val rightTop = (size.height - hR) / 2f
        val rightBot = (size.height + hR) / 2f

        val p = Path()
        p.moveTo(r, leftTop)
        p.lineTo(w - r, rightTop)
        p.quadraticBezierTo(w, rightTop, w, rightTop + r)
        p.lineTo(w, rightBot - r)
        p.quadraticBezierTo(w, rightBot, w - r, rightBot)
        p.lineTo(r, leftBot)
        p.quadraticBezierTo(0f, leftBot, 0f, leftBot - r)
        p.lineTo(0f, leftTop + r)
        p.quadraticBezierTo(0f, leftTop, r, leftTop)
        p.close()
        return Outline.Generic(p)
    }
}
