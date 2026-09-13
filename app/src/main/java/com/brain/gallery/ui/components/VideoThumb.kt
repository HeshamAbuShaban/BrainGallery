package com.brain.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Pink

@Composable
fun VideoThumb(video: VideoEntity, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build()
    }
    Box(modifier
        .clip(CardShape)
        .background(Color(0xFF151B26))
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(video.uri).videoFrameMillis(500).build(),
            imageLoader = loader, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            startY = 300f)))
        if (video.isFavorite) {
            Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier
                .align(Alignment.TopEnd).padding(6.dp).size(14.dp))
        }
        Text(fmtDur(video.durationMs), color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

fun fmtDur(ms: Long): String {
    if (ms <= 0) return ""
    val s = (ms / 1000).toInt()
    return if (s < 3600) "%d:%02d".format(s / 60, s % 60)
    else "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
