package com.brain.gallery.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.ui.components.ShimmerBar
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2

/**
 * The still half of the library: a plain three-column grid of photos, one
 * tap to view, long-press menu nothing — the brain's tags live in the video
 * surfaces for now. Deliberately mechanical: this is a browse surface people
 * move through quickly, so it gets no mechanic (the constitution's daily-
 * screen rule applied early).
 */
@Composable
fun PhotoLibrary(
    photos: List<PhotoEntity>,
    pending: Int,
    onOpen: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (pending > 0) "The indexer is still reading your photos…"
                else "No photos found yet — grant image access and rescan.",
                color = Text2, fontSize = 12.5.sp)
            repeat(3) { ShimmerBar(Modifier.fillMaxWidth(), 110.dp, 20.dp) }
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        items(photos, key = { it.id }) { p ->
            AsyncImage(
                model = p.uri,
                contentDescription = p.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable { onOpen(p) }
            )
        }
    }
}

/**
 * Full-screen look at one photo. Close, favorite, delete — nothing else.
 * The photo fills what the screen gives it; no mechanic, no ceremony. The
 * viewer is not the reel: it is a look, and looking ends with a tap.
 */
@Composable
fun PhotoViewer(
    photo: PhotoEntity,
    onClose: () -> Unit,
    onFav: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    BackHandler(enabled = true) { onClose() }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding()
            .padding(8.dp, 8.dp, 8.dp, 0.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClose() },
                modifier = Modifier.glass(CircleShape, Glass.onDark(Accent)).size(38.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Text1, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(photo.displayName, color = Text1, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(photo.folderName.ifEmpty { "photo" }, color = Text2,
                    fontSize = 10.5.sp, maxLines = 1)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onFav(!photo.isFavorite)
            }, modifier = Modifier.glass(CircleShape, Glass.onDark(Accent)).size(46.dp)) {
                Icon(if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite", tint = if (photo.isFavorite) Pink else Text1,
                    modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }, modifier = Modifier.glass(CircleShape, Glass.onDark(Accent)).size(46.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = Text1, modifier = Modifier.size(22.dp))
            }
        }
    }
}
