package com.brain.gallery.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Pink

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoThumb(
    video: VideoEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val loader = ctx.imageLoader // single app-level cache, not one per cell
    var dragX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(modifier
        .offset { androidx.compose.ui.unit.IntOffset(dragX.roundToInt(), 0) }
        .glass(CardShape, Glass.onDark(Accent))
        .then(
            if (onClick != null || onLongClick != null)
                Modifier.combinedClickable(onClick = { onClick?.invoke() },
                    onLongClick = { onLongClick?.invoke() })
            else Modifier
        )        ) {
        // Dead files (0 bytes / 0 duration) have no frame to show; a placeholder
        // reads better than a black rectangle.
        val unplayable = video.durationMs <= 0L || video.sizeBytes <= 0L
        if (unplayable) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A2130)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.VideocamOff, null, tint = Color.White.copy(alpha = 0.28f),
                    modifier = Modifier.size(22.dp))
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(video.uri)
                    .videoFrameMillis(thumbAtMs(video)).build(),
                imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
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

/** A frame a third in is almost always content; 0.5s is often black. */
fun thumbAtMs(v: VideoEntity): Long =
    if (v.durationMs <= 0) 400L else (v.durationMs / 3).coerceIn(300L, 4000L)

fun fmtDur(ms: Long): String {
    if (ms <= 0) return ""
    val s = (ms / 1000).toInt()
    return if (s < 3600) "%d:%02d".format(s / 60, s % 60)
    else "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
