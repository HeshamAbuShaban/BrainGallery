package com.brain.gallery.ui.feed

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.video.VideoFrameDecoder
import coil.ImageLoader
import com.brain.gallery.domain.engine.FeedItem
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Bg

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
            CircularProgressIndicator()
        }
        return
    }
    if (feed.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No videos indexed yet", color = Color.White)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "rescan", tint = Color.White)
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { feed.size })
    // Report watch on settle (completion unknown here -> 0.5 heuristic; detail screen reports exact)
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled > 0) {
            val prev = feed.getOrNull(settled - 1)
            if (prev != null) vm.onWatched(prev.video.id, 0.5f, false)
        }
    }

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Bg)) { page ->
        ReelPage(item = feed[page], isActive = pagerState.currentPage == page,
            player = player, onFav = { vm.toggleFav(feed[page].video.id, !feed[page].video.isFavorite) })
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ReelPage(item: FeedItem, isActive: Boolean, player: PlayerManager, onFav: () -> Unit) {
    val ctx = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    val v = item.video

    val exo = remember(item.video.uri) { player.playerFor(item.video.uri) }
    LaunchedEffect(isActive) { if (isActive && !paused) exo.play() else exo.pause() }
    DisposableEffect(isActive) { onDispose { if (!isActive) exo.pause() } }

    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onTap = { paused = !paused; player.toggle() })
        }) {
        if (isActive) {
            AndroidView(factory = { c -> PlayerView(c).apply { player = exo; useController = false } },
                modifier = Modifier.fillMaxSize())
        } else {
            val loader = remember {
                ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build()
            }
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(v.uri).build(),
                imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        if (paused && isActive) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(72.dp))
            }
        }
        // Bottom info: about + why + fav
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f)).padding(12.dp)) {
            Text(item.why, color = Color(0xFF06B6D4), fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            Text(v.about.ifEmpty { v.displayName }, color = Color.White, fontSize = 14.sp, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Text("${v.category} • ${v.tagList.take(3).joinToString(", ")}",
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
                    Icon(if (v.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                        tint = if (v.isFavorite) Color(0xFFEC4899) else Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(v.folderName, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}
