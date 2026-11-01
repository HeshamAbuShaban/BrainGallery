package com.brain.gallery.ui.hero

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.brain.gallery.data.local.VideoEntity
import kotlinx.coroutines.delay
import kotlin.math.sin

private val WaveCardShape: Shape = RoundedCornerShape(16.dp)

/**
 * Mechanic B — the spring card wave.
 *
 * A group's cover stack at the top of GroupDetail: a short pile of physical
 * cards, spines to the left, the whole stack tilted like a photo pile on a
 * table. The pointer lifts nearby cards through a Gaussian influence
 * ([HeroMath.calculateGaussianInfluence]) and springs settle them back;
 * after three idle seconds a virtual cursor sweeps across and the pile
 * stirs. A tap plays the group from its first clip — the mechanic's only
 * job is to frame the review, then hand off to the player.
 *
 * Constitution: springs only (`Animatable` + spring(0.75, 400), velocity
 * read for lean — no hand-integrated ODE); math from [HeroMath]; no accent
 * fill; hairline border; spine strip 6dp; light haptic is carried by the
 * video surface itself, this mechanic fires medium on its one tap; reduced
 * motion freezes lift and sweep, tap still works.
 *
 * One mechanic per screen — nothing else lands on GroupDetail, and the
 * triage grid below stays untouched.
 */
@Composable
fun CardWaveStack(
    clips: List<VideoEntity>,
    onPlay: (VideoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clips.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()

    val cardW = 104.dp
    val cardH = 138.dp
    val stepX = 16.dp
    val liftMax = 26.dp
    val cardWpx = with(density) { cardW.toPx() }
    val stepXpx = with(density) { stepX.toPx() }
    val liftMaxPx = with(density) { liftMax.toPx() }
    val spreadPx = with(density) { maxOf(cardW, cardH).toPx() * 0.12f }
    val count = clips.size.coerceAtMost(5)
    // The front card's left edge, anchored so the deepest card still clears
    // the 20dp margin: front sits count-1 steps in from there.
    val frontXpx = with(density) { (20.dp + stepX * (count - 1)).toPx() }

    // Pointer position in the stack's own coordinates, null when hands are
    // off. Written by touch and by the idle sweep; read only inside
    // snapshotFlow and the graphicsLayer lambdas.
    val pointer = remember { mutableStateOf<Offset?>(null) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var stackSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(reduced, count) {
        if (reduced) return@LaunchedEffect
        // Idle sweep: after three quiet seconds a virtual cursor drifts a
        // slow sine across the pile. Any real touch stops it mid-sweep.
        while (true) {
            delay(300)
            if (System.currentTimeMillis() - lastTouch < 3_000) continue
            val amp = stackSize.width / 3f
            val cy = stackSize.height / 2f
            val t0 = System.nanoTime()
            sweep@ while (true) {
                if (System.currentTimeMillis() - lastTouch < 3_000) break@sweep
                val dt = (System.nanoTime() - t0) / 1_000_000_000f
                pointer.value = Offset(
                    HeroMath.sweepX(dt, stackSize.width / 2f, amp), cy)
                delay(32)
            }
        }
    }

    Box(modifier
        .fillMaxWidth()
        .height(158.dp)
        .clipToBounds()
        .onSizeChanged { stackSize = it }
        .pointerInput(count, clips) {
            detectTapGestures(
                onPress = { off ->
                    lastTouch = System.currentTimeMillis()
                    pointer.value = off
                    tryAwaitRelease()
                    pointer.value = null
                },
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlay(clips[0])
                })
        }
    ) {
        // Back to front: card 0 is the one you see.
        for (i in count - 1 downTo 0) {
            WaveCard(
                video = clips[i],
                index = i,
                pointer = pointer,
                stackSize = stackSize,
                cardWpx = cardWpx,
                cardHpx = with(density) { cardH.toPx() },
                frontXpx = frontXpx,
                stepXpx = stepXpx,
                liftMaxPx = liftMaxPx,
                spreadPx = spreadPx,
                reduced = reduced,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(HeroMath.stackOffsetX(i, stepXpx).toInt(), 0) }
                    .width(cardW)
                    .height(cardH)
            )
        }
    }
}

@Composable
private fun WaveCard(
    video: VideoEntity,
    index: Int,
    pointer: androidx.compose.runtime.State<Offset?>,
    stackSize: IntSize,
    cardWpx: Float,
    cardHpx: Float,
    frontXpx: Float,
    stepXpx: Float,
    liftMaxPx: Float,
    spreadPx: Float,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    // The card's resting center in the stack's coordinate space. Rendered x
    // is frontXpx + stackOffsetX(index) — this must match it exactly, or the
    // Gaussian lifts cards the finger is not near.
    val centerX = remember(index, cardWpx, frontXpx, stepXpx) {
        frontXpx + HeroMath.stackOffsetX(index, stepXpx) + cardWpx / 2f
    }
    val lift = remember(index) { Animatable(0f) }

    LaunchedEffect(reduced, index, stackSize) {
        if (reduced) return@LaunchedEffect
        snapshotFlow { pointer.value }.collect { p ->
            val target = if (p == null || stackSize == IntSize.Zero) 0f
            else liftMaxPx * HeroMath.calculateGaussianInfluence(
                centerX, stackSize.height / 2f, p.x, p.y, spreadPx)
            lift.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 400f))
        }
    }

    Box(modifier.graphicsLayer {
        translationY = -lift.value
        // Depth as scale: deeper cards sit smaller; a lift brings a card forward.
        val depthScale = 1f - index * 0.045f
        val s = depthScale + (lift.value / liftMaxPx.coerceAtLeast(1f)) * 0.03f
        scaleX = s
        scaleY = s
        rotationY = 20f
        rotationX = HeroMath.leanFromVelocity(lift.velocity / density.density)
        shape = WaveCardShape
        clip = true
    }) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(video.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .videoFrameMillis(500)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // The spine: a physical card's bound edge, 6dp, no accent.
        Box(Modifier.fillMaxHeight().width(6.dp).background(Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent))))
        // Hairline border — the card's cut edge.
        Box(Modifier.fillMaxSize().border(width = 1.dp, color = Color.White.copy(alpha = 0.14f), shape = WaveCardShape))
    }
}
