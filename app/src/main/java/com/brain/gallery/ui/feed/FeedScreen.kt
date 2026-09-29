package com.brain.gallery.ui.feed

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.domain.engine.FeedItem
import com.brain.gallery.ui.components.VideoActionsSheet
import com.brain.gallery.ui.components.VideoDetailsDialog
import com.brain.gallery.ui.components.fmtDur
import com.brain.gallery.ui.components.thumbAtMs
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.player.ShareHelper
import com.brain.gallery.ui.spotlight.SpotlightController
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Motion
import com.brain.gallery.ui.theme.Pink
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val CHROME_IDLE_MS = 2500L
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    player: PlayerManager,
    spotlight: SpotlightController,
    onLeaveFeed: () -> Unit,
    onChrome: (Boolean) -> Unit = {},
    bottomOverlay: androidx.compose.ui.unit.Dp = 0.dp,
    vm: FeedViewModel = hiltViewModel()
) {
    val feed by vm.feed.collectAsState()
    val loading by vm.loading.collectAsState()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        (ctx as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { (ctx as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (loading) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF8B5CF6))
        }
        return
    }
    if (feed.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to show", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.feed.value.isEmpty()) "Everything got filtered out — tap refresh"
                    else "Grant access, then rescan",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = { vm.refresh() },
                    modifier = Modifier.background(Color(0xFF1D2534), CircleShape)) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
        }
        return
    }

    val spotlightOpen by spotlight.isOpen.collectAsState()
    val pagerState = rememberPagerState(pageCount = { feed.size })
    val scope = rememberCoroutineScope()
    val deleteAsk by vm.deleteAsk.collectAsState()
    var menuFor by remember { mutableStateOf<VideoEntity?>(null) }
    var detailsFor by remember { mutableStateOf<VideoEntity?>(null) }

    val delLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) vm.confirmDelete()
    }
    LaunchedEffect(deleteAsk) {
        deleteAsk?.let {
            delLauncher.launch(IntentSenderRequest.Builder(it).build())
            vm.consumeDeleteAsk()
        }
    }

    fun advance() {
        val next = pagerState.currentPage + 1
        scope.launch {
            if (next < feed.size) pagerState.animateScrollToPage(next)
            else {
                // Feed exhausted: reshuffle a fresh batch rather than stopping dead.
                vm.reshuffle()
                pagerState.scrollToPage(0)
            }
        }
    }

    // ---- swipe-down to leave the feed ----
    // The pager owns vertical drags, so this observes them in the Final pass
    // instead of consuming them: the reel still pages normally, and a long
    // downward pull additionally scales the stage away and exits.
    //
    // The gesture scope is restricted (it may only await pointer events), so the
    // drag only writes a plain float here and the animations run outside it.
    val dismissAnim = remember { Animatable(0f) }
    var dragLive by remember { mutableFloatStateOf(0f) }
    var stageH by remember { mutableFloatStateOf(0f) }
    var exitRequested by remember { mutableIntStateOf(0) }
    var backRequested by remember { mutableIntStateOf(0) }

    LaunchedEffect(exitRequested) {
        if (exitRequested > 0) {
            dismissAnim.snapTo(dragLive)
            dragLive = 0f
            dismissAnim.animateTo(stageH * 1.15f, tween(220))
            onLeaveFeed()
        }
    }
    LaunchedEffect(backRequested) {
        if (backRequested > 0) {
            dismissAnim.snapTo(dragLive)
            dragLive = 0f
            dismissAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .onSizeChanged { stageH = it.height.toFloat() }
            .graphicsLayer {
                val off = if (dragLive != 0f) dragLive else dismissAnim.value
                val p = if (stageH > 0f) (off / stageH).coerceIn(0f, 1f) else 0f
                translationY = off
                scaleX = 1f - p * 0.22f
                scaleY = 1f - p * 0.22f
                alpha = 1f - p * 0.75f
            }
            .pointerInput(pagerState, stageH) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    val basePage = pagerState.currentPage
                    var armed = false
                    val baseY = down.position.y
                    var travelled = 0f
                    // Velocity tracked by hand: PointerInputChange has no velocity
                    // in this Compose version, and a fast flick should dismiss even
                    // when it did not travel far.
                    var lastY = baseY
                    var lastT = down.uptimeMillis
                    var vel = 0f
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Final)
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = c.position.y - baseY
                        val dx = c.position.x - down.position.x
                        // Arm only on a clearly vertical, downward pull.
                        if (dy > 6f && dy > kotlin.math.abs(dx) * 1.2f) armed = true
                        if (armed && c.pressed) {
                            travelled = dy.coerceAtLeast(0f)
                            val dt = (c.uptimeMillis - lastT).coerceAtLeast(1L)
                            val inst = (c.position.y - lastY) / dt * 1000f
                            vel = vel * 0.6f + inst * 0.4f
                            lastY = c.position.y
                            lastT = c.uptimeMillis
                            dragLive = travelled * 0.55f
                        }
                    } while (c.pressed)

                    val far = stageH > 0f && travelled > stageH * 0.22f
                    val fling = vel > 1400f && travelled > stageH * 0.10f
                    // If the pager already turned the page, this was a page swipe.
                    val paged = pagerState.currentPage != basePage
                    if (armed && (far || fling) && !paged) exitRequested++
                    else if (dragLive != 0f) backRequested++
                }
            }
    ) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = feed[page]
            ReelPage(
                item = item,
                isActive = pagerState.currentPage == page,
                covered = spotlightOpen,
                page = page,
                manager = player,
                onFav = { vm.toggleFav(item.video.id, !item.video.isFavorite) },
                onReport = { c -> vm.onWatched(item.video.id, c, c < 0.15f) },
                onMenu = { menuFor = item.video },
                onEnded = { advance() },
                onSimilar = {
                    spotlight.openPlaylist(feed.map { it.video }, page)
                },
                onDismiss = onLeaveFeed,
                onChrome = { c -> if (pagerState.currentPage == page) onChrome(c) },
                bottomOverlay = bottomOverlay
            )
        }

        menuFor?.let { mv ->
            val live = feed.firstOrNull { it.video.id == mv.id }?.video ?: mv
            VideoActionsSheet(video = live,
                onDismiss = { menuFor = null },
                onPlay = { menuFor = null },
                onFav = {
                    menuFor = null
                    vm.toggleFav(live.id, !live.isFavorite)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onSimilar = { menuFor = null; spotlight.open(live, feed.map { it.video }) },
                onDetails = { detailsFor = live; menuFor = null },
                onDelete = { menuFor = null; vm.requestDelete(listOf(live.id)) },
                onNotInterested = {
                    menuFor = null
                    vm.markNotInterested(live.id)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                })
        }
        detailsFor?.let { VideoDetailsDialog(video = it, onDismiss = { detailsFor = null }) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ReelPage(
    item: FeedItem,
    isActive: Boolean,
    covered: Boolean,
    page: Int,
    manager: PlayerManager,
    onFav: () -> Unit,
    onReport: (Float) -> Unit,
    onMenu: () -> Unit,
    onEnded: () -> Unit,
    onSimilar: () -> Unit,
    onDismiss: () -> Unit,
    onChrome: (Boolean) -> Unit = {},
    bottomOverlay: androidx.compose.ui.unit.Dp = 0.dp
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val v = item.video

    var paused by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(chrome) { onChrome(chrome) }
    var tick by remember { mutableIntStateOf(0) }        // restarts the chrome timer
    var progress by remember { mutableFloatStateOf(0f) }
    var maxSeen by remember { mutableFloatStateOf(0f) }
    var heartAt by remember { mutableStateOf<Offset?>(null) }
    var scrubbing by remember { mutableFloatStateOf(-1f) }
    var seekMs by remember { mutableStateOf(0L) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var speed by remember { mutableFloatStateOf(1f) }
    val heartScale = remember { Animatable(0.4f) }

    // Only the active, uncovered page claims the shared player. An adjacent page
    // would swap the media item out from under the current video, and a page
    // hidden behind the spotlight must stay out of the way so the reel gets its
    // own clip back the moment the spotlight closes.
    val ownerId = "feed:$page"
    val exo = remember(v.uri, isActive, covered) {
        if (isActive && !covered) manager.acquire(ownerId, v.uri) else null
    }
    DisposableEffect(exo, ownerId) {
        if (exo == null) manager.release(ownerId)
        onDispose { if (exo != null) manager.release(ownerId) }
    }

    // First frame gate + end-of-item advance.
    DisposableEffect(exo) {
        if (exo == null) return@DisposableEffect onDispose { }
        ready = false
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    // Playback state + honest progress measurement.
    LaunchedEffect(exo, paused, speed) {
        val p = exo ?: return@LaunchedEffect
        p.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        if (paused) p.pause() else p.play()
        while (true) {
            delay(300)
            val c = manager.completion()
            progress = c
            if (c > maxSeen) maxSeen = c
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            exo?.pause()
            if (maxSeen > 0.02f) { onReport(maxSeen); maxSeen = 0f }
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (maxSeen > 0.02f) onReport(maxSeen) }
    }

    // Chrome auto-hides while playing, like the real thing. Paused or paused by
    // the spotlight, it stays put: a paused frame with no controls is just a still.
    LaunchedEffect(paused, isActive, tick) {
        if (paused || !isActive) return@LaunchedEffect
        delay(CHROME_IDLE_MS)
        chrome = false
    }

    LaunchedEffect(heartAt) {
        if (heartAt != null) {
            heartScale.snapTo(0.4f)
            heartScale.animateTo(1.15f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            heartScale.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
            delay(420)
            heartAt = null
        }
    }

    val railAlpha by animateFloatAsState(
        targetValue = if (chrome) 1f else 0f,
        animationSpec = tween(Motion.mediumMs), label = "rail")

    Box(Modifier.fillMaxSize()
        .onSizeChanged { stageSize = it }
        .pointerInput(v.uri) {
        detectTapGestures(
            onTap = {
                // Any touch brings the chrome back. Without this it hid once and
                // never came back until the page changed, leaving the reel with no
                // rail and no way to reach the nav.
                chrome = true
                paused = !paused
                tick++
                manager.toggle()
            },
            onDoubleTap = {
                chrome = true
                heartAt = Offset(size.width / 2f, size.height / 2f)
                onFav()
                tick++
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onLongPress = {
                chrome = true
                paused = true
                tick++
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onMenu()
            }
        )
    }) {
        // ---- stage ----
        if (!ready) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(v.uri)
                    .videoFrameMillis(thumbAtMs(v)).build(),
                imageLoader = ctx.imageLoader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (exo != null) {
            AndroidView(factory = { c ->
                PlayerView(c).also { pv ->
                    pv.player = exo
                    pv.useController = false
                    // Fill the screen like a short-video player, crop the overflow.
                    pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            }, modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f))
        }

        // ---- double-tap heart, at the touch point ----
        heartAt?.let { pos ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Favorite, null, tint = Pink,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(heartScale.value)
                        // pos is in pixels; the icon is centred, so shift by the delta.
                        .offset {
                            IntOffset(
                                (pos.x - stageSize.width / 2f).roundToInt(),
                                (pos.y - stageSize.height / 2f).roundToInt()
                            )
                        })
            }
        }

        // ---- centred play/pause (translucent, no chip) ----
        if (paused && isActive) {
            Icon(
                if (exo?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                null, tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.Center).size(76.dp))
        }

        // ---- chrome ----
        AnimatedVisibility(visible = chrome, enter = fadeIn(tween(160)), exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(railAlpha)) {
                // top: single-feed label + swipe-down-to-leave affordance
                Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(6.dp))
                    Text("For You", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Icon(
                    Icons.Default.KeyboardArrowDown, "Back to library",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopCenter).padding(top = 46.dp)
                        .size(30.dp)
                        .clickable { onDismiss() }
                )

                // right action rail
                Column(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 96.dp + bottomOverlay),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    RailButton(
                        icon = if (v.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        tint = if (v.isFavorite) Pink else Color.White,
                        count = if (v.watchCount > 0) "${v.watchCount}" else null
                    ) { onFav(); tick++ }
                    RailButton(Icons.Default.AutoAwesome, Cyan, null) { onSimilar() }
                    RailButton(Icons.Default.Speed, if (speed != 1f) Color(0xFFF59E0B) else Color.White,
                        "${speed}x") {
                        val i = SPEEDS.indexOf(speed)
                        speed = SPEEDS[(i + 1) % SPEEDS.size]
                        manager.setSpeed(speed)
                        tick++
                    }
                    RailButton(Icons.Default.Share, Color.White, null) { ShareHelper.share(ctx, v) }
                    RailButton(Icons.Default.MoreHoriz, Color.White, null) { onMenu() }
                }

                // bottom caption block
                Column(Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        startY = 240f))
                    .padding(start = 14.dp, end = 86.dp, bottom = 26.dp + bottomOverlay)) {
                    Text(item.why, color = Cyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(v.about.ifEmpty { v.displayName }, color = Color.White,
                        fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (v.tagList.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(v.tagList.take(4).joinToString("  •  "), color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text("${v.folderName} • ${fmtDur(v.durationMs)}", color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---- scrub bar: 2px visual, full-width, drag to seek ----
        val shown = if (scrubbing >= 0f) scrubbing else progress
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(bottom = bottomOverlay).height(26.dp)
            .pointerInput(v.uri) {
                detectTapGestures(
                    onTap = { off ->
                        val frac = (off.x / size.width).coerceIn(0f, 1f)
                        manager.seekTo((manager.durationMs() * frac).toLong())
                        tick++
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
            .pointerInput(v.uri) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        val frac = (off.x / size.width).coerceIn(0f, 1f)
                        chrome = true
                        scrubbing = frac
                        seekMs = (manager.durationMs() * frac).toLong()
                    },
                    onDragEnd = {
                        if (scrubbing >= 0f) { manager.seekTo(seekMs); scrubbing = -1f; tick++ }
                    },
                    onDragCancel = { scrubbing = -1f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val base = if (scrubbing >= 0f) scrubbing else progress
                        scrubbing = ((base * size.width + amount) / size.width).coerceIn(0f, 1f)
                        seekMs = (manager.durationMs() * scrubbing).toLong()
                    }
                )
            },
            contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(2.dp).background(Color.White))
            if (scrubbing >= 0f) {
                Text(fmtDur(seekMs), color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-34).dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: String?,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(31.dp))
        }
        if (count != null) {
            Text(count, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
