package com.brain.gallery.ui.hero

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.brain.gallery.data.local.VideoEntity
import kotlin.math.abs

/**
 * The portal is a once-per-session door: the first clip a person opens from
 * a review grid passes through it, every later entry goes straight in. A
 * process-lifetime flag, deliberately — "session" here means one run of the
 * app, not one screen visit.
 */
object PortalGate {
    var consumed = false
    fun canPortal(): Boolean = !consumed
}

/**
 * Mechanic C — the screen-locked portal.
 *
 * The entry into the player. A squircle window holding the clip's first
 * frame, the screen behind it locked and dark. The window tilts toward the
 * finger ([HeroMath.tiltYaw]/[HeroMath.tiltPitch]); a tap expands it with
 * the ease-in-out cubic — window grows to the full screen, corner radius
 * collapses, tilt dies — and only then does the real player take over, so
 * the sacred screen's first frame is its own, never re-drawn mid-transition.
 *
 * Constitution: math from [HeroMath] only (projection, 44-point perimeter,
 * expansion geometry, the ease); the media is painted full-screen inside the
 * clipped window, never texture-mapped onto it; medium haptic on the tap;
 * reduced motion never reaches this composable (the caller routes direct).
 *
 * One mechanic per surface: this owns the grid-tap transition only — the
 * card wave's tap hands off directly and the two never co-trigger.
 */
@Composable
fun PortalOverlay(
    video: VideoEntity,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val ctx = LocalContext.current

    // Expansion progress, eased where it is consumed. The constitution's
    // mechanic C names the ease-in-out cubic as this mechanic's curve; the
    // clock itself is a frame loop like the other mechanics — no tweens.
    val e = remember { mutableFloatStateOf(0f) }
    // Normalized pointer for the tilt; written by the gesture loop, read in
    // the draw phase only.
    val pointerNX = remember { mutableFloatStateOf(0.5f) }
    val pointerNY = remember { mutableFloatStateOf(0.5f) }
    var expanding by remember { mutableStateOf(false) }

    fun expand() {
        if (expanding) return
        expanding = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(expanding) {
        if (!expanding) return@LaunchedEffect
        var last = -1L
        while (e.floatValue < 1f) {
            withFrameNanos { now ->
                if (last < 0) { last = now; return@withFrameNanos }
                val dt = (now - last) / 1_000_000_000f
                last = now
                e.floatValue = (e.floatValue + dt / 0.46f).coerceAtMost(1f)
            }
        }
        // Consumed on completion, not on attempt: a back-press bailout
        // leaves the door for later in the session. At most one ceremony.
        PortalGate.consumed = true
        onOpen()
    }

    Box(modifier.fillMaxSize()
        .background(Color.Black)
        .pointerInput(video.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var moved = 0f
                while (true) {
                    val change = awaitPointerEvent().changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    moved += abs(change.positionChange().x) + abs(change.positionChange().y)
                    pointerNX.floatValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    pointerNY.floatValue = (change.position.y / size.height).coerceIn(0f, 1f)
                    change.consume()
                }
                if (moved < 12.dp.toPx()) expand()
            }
        }
    ) {
        // The locked screen behind the window: near-black, easing slightly
        // lighter as the portal's own frame takes over the screen.
        Box(Modifier.fillMaxSize().drawBehind {
            val t = HeroMath.easeInOutCubic(e.value)
            drawRect(Color.Black.copy(alpha = (0.92f - 0.12f * t).coerceIn(0f, 1f)))
        })
        // The window: the clip's frame painted full-screen and centered,
        // clipped by the projected squircle. No texture-mapping.
        val model = remember(video.id) {
            ImageRequest.Builder(ctx).data(video.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .videoFrameMillis(500)
                .build()
        }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                shape = PortalShape(
                    t = HeroMath.easeInOutCubic(e.value),
                    pointerNX = pointerNX.floatValue,
                    pointerNY = pointerNY.floatValue,
                    radiusPx = 44.dp.toPx()
                )
                clip = true
            }
        )
    }

    // Back is a door out, same as the tap is a door through.
    androidx.activity.compose.BackHandler(enabled = true) { if (!expanding) onDismiss() }
}

/**
 * The projected squircle: a rounded rect whose perimeter points are pushed
 * through [HeroMath.project3DPoint] with the pointer's tilt, shrinking its
 * radius and growing to the full screen as expansion progresses. Built in
 * the draw phase only.
 */
private class PortalShape(
    private val t: Float,
    private val pointerNX: Float,
    private val pointerNY: Float,
    private val radiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (t >= 1f) {
            return Outline.Rectangle(
                androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
        val baseW = size.width * 0.66f
        val w = HeroMath.portalWidth(t, baseW, size.width)
        val h = HeroMath.portalHeight(t, baseW * 1.25f, size.height)
        val r = HeroMath.portalCorner(t, radiusPx).coerceAtLeast(0.5f)
        val thetaY = HeroMath.tiltYaw(pointerNX) * (1f - t)
        val thetaX = HeroMath.tiltPitch(pointerNY) * (1f - t)
        val cx = size.width / 2f + (pointerNX - 0.5f) * 40f * (1f - t)
        val cy = size.height / 2f + (pointerNY - 0.5f) * 40f * (1f - t)

        val path = Path()
        val pts = HeroMath.sampleRoundedRectPoints(w, h, r)
        pts.forEachIndexed { i, p ->
            val projected = HeroMath.project3DPoint(
                p.x - w / 2f, p.y - h / 2f, cx, cy, thetaX, thetaY)
            if (i == 0) path.moveTo(projected.x, projected.y)
            else path.lineTo(projected.x, projected.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
