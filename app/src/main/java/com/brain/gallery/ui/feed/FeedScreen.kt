package com.brain.gallery.ui.feed

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import coil.ImageLoader
import coil.request.videoFrameMillis
import com.brain.gallery.domain.engine.FeedItem
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Cyan
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(player: PlayerManager, vm: FeedViewModel = hiltViewModel()) {
    val feed by vm.feed.collectAsState()
    val loading by vm.loading.collectAsState()
    val ctx = LocalContext.current

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
                Text("No videos yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Grant access, then rescan", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = { vm.refresh() },
                    modifier = Modifier.background(Color(0xFF1D2534), CircleShape)) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { feed.size })
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Bg)) { page ->
        val item = feed[page]
        ReelPage(
            item = item,
            isActive = pagerState.currentPage == page,
            position = "${page + 1}/${feed.size}",
            manager = player,
            onFav = { vm.toggleFav(item.video.id, !item.video.isFavorite) },
            onReport = { completion -> vm.onWatched(item.video.id, completion, completion < 0.15f) }
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ReelPage(
    item: FeedItem, isActive: Boolean, position: String,
    manager: PlayerManager, onFav: () -> Unit, onReport: (Float) -> Unit
) {
    val ctx = LocalContext.current
    val v = item.video
    var paused by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var maxSeen by remember { mutableFloatStateOf(0f) }
    var heart by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0.6f) }

    val exo = remember(v.uri) { manager.playerFor(v.uri) }

    // First frame -> hide thumbnail.
    DisposableEffect(exo, v.uri) {
        ready = false
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    // Active: play + track progress. Inactive: report real completion once.
    LaunchedEffect(isActive) {
        if (isActive) {
            if (!paused) exo.play() else exo.pause()
            while (true) {
                delay(500)
                val c = manager.completion()
                progress = c
                if (c > maxSeen) maxSeen = c
            }
        } else {
            exo.pause()
            if (maxSeen > 0.02f) { onReport(maxSeen); maxSeen = 0f }
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (maxSeen > 0.02f) { onReport(maxSeen) } }
    }

    LaunchedEffect(heart) {
        if (heart) {
            heartScale.snapTo(0.6f)
            heartScale.animateTo(1.15f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            heartScale.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
            delay(500)
            heart = false
        }
    }

    val loader = remember {
        ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    Box(Modifier.fillMaxSize().pointerInput(v.uri) {
        detectTapGestures(
            onTap = { paused = !paused; manager.toggle() },
            onDoubleTap = { onFav(); heart = true }
        )
    }) {
        // Thumbnail underneath until first frame renders.
        if (!ready) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(v.uri).videoFrameMillis(500).build(),
                imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (isActive || ready) {
            AndroidView(factory = { c ->
                PlayerView(c).also { pv -> pv.player = exo; pv.useController = false } },
                modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f))
        }
        // Top gradient + position + brain chip.
        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(position, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (v.brainLevel >= 1) {
                Text("✦ ${v.category}", color = Color.Black, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.9f), CircleShape)
                        .padding(9.dp, 4.dp))
            }
        }
        if (paused && isActive) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val icon = if (exo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                Box(Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(14.dp)) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
        if (heart) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFFEC4899),
                    modifier = Modifier.size(96.dp).scale(heartScale.value))
            }
        }
        // Bottom info.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .padding(16.dp, 26.dp, 16.dp, 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.why, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(9.dp, 4.dp))
                Spacer(Modifier.width(8.dp))
                Text(v.folderName, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(v.about.ifEmpty { v.displayName }, color = Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            Text(v.tagList.take(4).joinToString("  •  "),
                color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onFav(); if (!v.isFavorite) heart = true },
                    modifier = Modifier.size(44.dp)) {
                    Icon(if (v.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                        tint = if (v.isFavorite) Color(0xFFEC4899) else Color.White,
                        modifier = Modifier.size(26.dp))
                }
            }
        }
        LinearProgressIndicator(progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.5.dp),
            color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
    }
}
