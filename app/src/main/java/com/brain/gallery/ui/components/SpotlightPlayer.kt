package com.brain.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text2

/**
 * Watch-from-anywhere: fullscreen player reusing the single ExoPlayer,
 * with a More-like-this rail (clip pulls its siblings).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SpotlightPlayer(
    video: VideoEntity,
    similar: List<VideoEntity>,
    manager: PlayerManager,
    onClose: () -> Unit,
    onPick: (VideoEntity) -> Unit,
    onFav: (VideoEntity) -> Unit
) {
    val exo = remember(video.uri) { manager.playerFor(video.uri) }
    LaunchedEffect(video.uri) { exo.play() }
    DisposableEffect(video.uri) { onDispose { exo.pause() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { c ->
            PlayerView(c).also { pv -> pv.player = exo; pv.useController = true } },
            modifier = Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .size(38.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(video.about.ifEmpty { video.displayName }, color = Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${video.category} • ${video.folderName}", color = Text2, fontSize = 11.sp)
            }
            IconButton(onClick = { onFav(video) }, modifier = Modifier.size(38.dp)) {
                Icon(if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null, tint = if (video.isFavorite) Pink else Color.White,
                    modifier = Modifier.size(22.dp))
            }
        }
        if (similar.isNotEmpty()) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(0.dp, 20.dp, 0.dp, 12.dp)) {
                Text("More like this", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(6.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    items(similar, key = { it.id }) { s ->
                        VideoThumb(s, Modifier.size(110.dp, 150.dp)) { onPick(s) }
                    }
                }
            }
        }
    }
}
