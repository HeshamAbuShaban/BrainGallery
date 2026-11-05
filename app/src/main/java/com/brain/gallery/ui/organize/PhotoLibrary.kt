package com.brain.gallery.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import coil.compose.AsyncImage
import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.ui.components.ShimmerBar
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.glass.glassReserve
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.ChipFill
import com.brain.gallery.ui.theme.Text2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

/**
 * The still half of the library. Organized, not dumped: a count that admits
 * what is still being read, folder chips so a wall of 3.8k thumbnails can be
 * narrowed, and month headers so scrolling reads like time passing. No
 * mechanic — a browse surface people move through quickly gets none.
 */
@Composable
fun PhotoLibrary(
    photos: List<PhotoEntity>,
    pending: Int,
    onOpen: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var folder by remember { mutableStateOf<String?>(null) }
    val folders = remember(photos) {
        photos.groupingBy { it.folderName.ifEmpty { "Other" } }
            .eachCount().entries.sortedByDescending { it.value }
            .take(6).map { it.key }
    }
    val shown = remember(photos, folder) {
        if (folder == null) photos
        else photos.filter { it.folderName.ifEmpty { "Other" } == folder }
    }

    Column(modifier.fillMaxSize()) {
        Text(
            if (pending > 0) "${fmtCount(photos.size)} photos · $pending still being read"
            else "${fmtCount(photos.size)} photos",
            color = Text2, fontSize = 11.5.sp,
            modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 0.dp))

        if (folders.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                item { FolderChip("All", folder == null) { folder = null } }
                folders.forEach { f ->
                    item(key = f) { FolderChip(f, folder == f) { folder = if (folder == f) null else f } }
                }
            }
        }

        if (photos.isEmpty()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (pending > 0) "The indexer is still reading your photos…"
                    else "No photos found yet — grant image access and rescan.",
                    color = Text2, fontSize = 12.5.sp)
                repeat(3) { ShimmerBar(Modifier.fillMaxWidth(), 110.dp, 20.dp) }
            }
            return
        }
        if (shown.isEmpty()) {
            Text("Nothing in \"$folder\" yet.", color = Text2, fontSize = 12.5.sp,
                modifier = Modifier.padding(20.dp, 14.dp))
            return
        }

        LazyVerticalGrid(
            GridCells.Fixed(3),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Months read as chapters: a header span, then that month's grid.
            shown.groupBy { monthFormat.format(Date(it.dateAddedSec * 1000)) }
                .forEach { (month, list) ->
                    item(span = { GridItemSpan(3) }, key = "m_$month") {
                        Text(month, color = Text1, fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    }
                    items(list, key = { "p_${it.id}" }) { p ->
                        AsyncImage(
                            model = p.uri,
                            contentDescription = p.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpen(p) }
                        )
                    }
                }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (selected) Color.White else Text2,
        fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else ChipFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
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
    androidx.activity.compose.BackHandler(enabled = true) { onClose() }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (photo.animated == true && Build.VERSION.SDK_INT >= 28) {
            // Animated media plays here — decoding through ImageDecoder, no
            // extra artifact, no network. The shelf stays a still frame;
            // opening one is the moment it moves.
            GifCanvas(photo.uri, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding()
            .padding(8.dp, 8.dp, 8.dp, 0.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            }, modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(38.dp)) {
                Icon(Icons.Default.Close,
                    "Close", tint = Text1, modifier = Modifier.size(18.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(photo.displayName, color = Text1, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(photo.folderName.ifEmpty { "photo" }, color = Text2,
                    fontSize = 10.5.sp, maxLines = 1)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter)
            .padding(bottom = 24.dp + com.brain.gallery.ui.glass.glassReserve()),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onFav(!photo.isFavorite)
            }, modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(46.dp)) {
                Icon(
                    if (photo.isFavorite) Icons.Default.Favorite
                    else Icons.Default.FavoriteBorder,
                    "Favorite", tint = if (photo.isFavorite) Pink else Text1,
                    modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }, modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(46.dp)) {
                Icon(Icons.Default.Delete,
                    "Delete", tint = Text1, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Plays an animated image in place. ImageDecoder hands back an
 * [android.graphics.drawable.AnimatedImageDrawable]; an ImageView advances it
 * on its own draw clock, so the decode is the only work this does. Any
 * failure of the decode falls through to nothing loud — a broken GIF is
 * still a photo.
 */
@Composable
private fun GifCanvas(uri: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var drawable by remember(uri) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(uri) {
        runCatching {
            withContext(Dispatchers.IO) {
                ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(ctx.contentResolver, Uri.parse(uri)))
            }
        }.onSuccess { drawable = it }
    }
    AndroidView(
        factory = { viewCtx ->
            ImageView(viewCtx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { iv ->
            iv.setImageDrawable(drawable)
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
        },
        modifier = modifier
    )
}
