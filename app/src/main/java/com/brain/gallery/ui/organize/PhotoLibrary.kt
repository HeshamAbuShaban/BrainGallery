package com.brain.gallery.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.brain.gallery.ui.components.FavButton
import com.brain.gallery.ui.components.ShimmerBar
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.glass.glassReserve
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.ChipFill
import com.brain.gallery.domain.organize.PhotoGroupBuilder
import com.brain.gallery.domain.organize.PhotoSmartGroup
import com.brain.gallery.ui.theme.Hairline
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.components.RailPrefs
import com.brain.gallery.ui.theme.Yellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

/**
 * The still half of the library. Organized, not dumped: a count that admits
 * what is still being read, folder chips so a wall of 3.8k thumbnails can be
 * narrowed, and month headers so scrolling reads like time passing. No
 * mechanic — a browse surface people move through quickly gets none.
 */
/**
 * The still half of the library. Organized, not dumped: a count that admits
 * what is still being read, folder chips so a wall of 3.8k thumbnails can be
 * narrowed, and month headers so scrolling reads like time passing. No
 * mechanic — a browse surface people move through quickly gets none.
 *
 * The count and the chips live inside the grid now. They used to be a fixed
 * lid over a grid that already had one, and the lid never moved.
 */
@Composable
fun PhotoLibrary(
    photos: List<PhotoEntity>,
    pending: Int,
    onOpen: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var folder by remember { mutableStateOf<String?>(null) }
    val folders = remember(photos) {
        photos.groupingBy { it.folderName.ifEmpty { "Other" } }
            .eachCount().entries.sortedByDescending { it.value }
            .take(6).map { it.key }
    }
    // The brain's shelves for stills — tap a shelf and the grid narrows to
    // it, exactly as the folder chips do; the shelf cards sit above them.
    val smartGroups = remember(photos) { PhotoGroupBuilder.build(photos) }
    var groupFilter by remember { mutableStateOf<PhotoSmartGroup?>(null) }
    val shown = remember(photos, folder, groupFilter) {
        val gf = groupFilter
        when {
            gf != null -> gf.photos
            folder == null -> photos
            else -> photos.filter { it.folderName.ifEmpty { "Other" } == folder }
        }
    }

    // Switching shelves or folders breathes in rather than pops.
    val gridFade = remember { Animatable(1f) }
    LaunchedEffect(folder, groupFilter) {
        gridFade.snapTo(0.25f)
        gridFade.animateTo(1f, tween(220))
    }

    if (photos.isEmpty()) {
        Column(modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, top = 2.dp,
            bottom = glassReserve() + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = gridFade.value }
    ) {
        item(span = { GridItemSpan(3) }, key = "count") {
            Text(
                if (pending > 0) "${fmtCount(photos.size)} photos · $pending still being read"
                else "${fmtCount(photos.size)} photos",
                color = Text2, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 4.dp))
        }

        // The brain's shelves as cards — tap to walk in, tap again to leave.
        if (smartGroups.isNotEmpty() && groupFilter == null) {
            item(span = { GridItemSpan(3) }, key = "shelves") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    smartGroups.forEach { g ->
                        item(key = g.id) {
                            PhotoGroupCard(
                                group = g,
                                selected = false,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    groupFilter = g
                                }
                            )
                        }
                    }
                }
            }
        }
        val openShelf = groupFilter
        if (openShelf != null) {
            item(span = { GridItemSpan(3) }, key = "shelf-open") {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${openShelf.title} · ${openShelf.subtitle}",
                        color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    Text("Leave", color = Text2, fontSize = 12.sp,
                        modifier = Modifier.clickable { groupFilter = null }.padding(6.dp))
                }
            }
        }

        if (folders.size > 1) {
            item(span = { GridItemSpan(3) }, key = "folders") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    item { FolderChip("All", folder == null) { folder = null } }
                    folders.forEach { f ->
                        item(key = f) {
                            FolderChip(f, folder == f) { folder = if (folder == f) null else f }
                        }
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item(span = { GridItemSpan(3) }, key = "empty") {
                Text("Nothing in \"$folder\" yet.", color = Text2, fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 14.dp))
            }
        }

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
/**
 * Full-screen look at one photo. Close, favorite, delete — and the same
 * "More like this" rail the player has had, because a still has neighbours
 * too. The viewer is not the reel: it is a look, and looking ends with a tap.
 */
@Composable
fun PhotoViewer(
    photo: PhotoEntity,
    onClose: () -> Unit,
    onFav: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    similar: List<PhotoEntity> = emptyList(),
    onPickSimilar: (PhotoEntity) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var infoOpen by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = true) { onClose() }
    // The pill steps aside for a photo, the way it does for the spotlight.
    // It is chrome for the grid; over a full-frame still it is in the way.
    androidx.compose.runtime.DisposableEffect(Unit) {
        PhotoViewerState.set(true)
        onDispose { PhotoViewerState.set(false) }
    }
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
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    startY = 90f))
                .navigationBarsPadding()
                .padding(top = 18.dp, bottom = 16.dp)
        ) {
            // The neighbours rail folds behind its header, same shared switch
            // as the video spotlight: the still comes first, neighbours on
            // request.
            if (similar.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        RailPrefs.expanded = !RailPrefs.expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("More like this", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("ⓘ", color = Text2, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 12.dp)
                            .clickable { infoOpen = !infoOpen })
                    Icon(if (RailPrefs.expanded) Icons.Default.KeyboardArrowDown
                         else Icons.Default.KeyboardArrowUp, null,
                        tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
                androidx.compose.animation.AnimatedVisibility(visible = RailPrefs.expanded) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(similar, key = { "s_${it.id}" }) { s ->
                                AsyncImage(
                                    model = s.uri,
                                    contentDescription = s.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove)
                                            onPickSimilar(s)
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = infoOpen) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("Taken ${dayFormat.format(Date(photo.dateAddedSec * 1000))}" +
                        (if (photo.width > 0) " · ${photo.width}×${photo.height}" else ""),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    if (photo.about.isNotBlank()) {
                        Text(photo.about.replaceFirstChar { it.uppercase() },
                            color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                    if (photo.tagList.isNotEmpty()) {
                        Text(photo.tagList.take(4).joinToString("  ·  "),
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    if (photo.isFavorite) {
                        Text("★ In your favorites", color = Yellow, fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                // Same vocabulary as every other heart in the app: spring and haptic.
                FavButton(
                    favorite = photo.isFavorite,
                    onToggle = { onFav(!photo.isFavorite) },
                    modifier = Modifier.glass(CircleShape, Glass.pane(Accent)),
                    size = 22.dp,
                    tapTarget = 46.dp,
                    activeColor = Pink,
                    tint = Text1,
                    contentDescription = "Favorite"
                )
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }, modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(46.dp)) {
                    Icon(Icons.Default.Delete,
                        "Delete", tint = Text1, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    sharePhoto(ctx, photo)
                }, modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(46.dp)) {
                    Icon(Icons.Default.Share,
                        "Share", tint = Text1, modifier = Modifier.size(22.dp))
                }
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

/**
 * A brain shelf for stills, as a card: cover, count, name. The photo twin
 * of the video group card — tap in, and the grid narrows to the shelf.
 */
@Composable
private fun PhotoGroupCard(
    group: PhotoSmartGroup,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .width(132.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        val cover = group.photos.firstOrNull()
        if (cover != null) {
            AsyncImage(
                model = cover.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Hairline))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
            startY = 60f)))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(group.title, color = Color.White, fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(group.subtitle, color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Send one still out of the device, through the system share sheet. */
fun sharePhoto(ctx: android.content.Context, photo: PhotoEntity) {
    val file = runCatching {
        val input = ctx.contentResolver.openInputStream(android.net.Uri.parse(photo.uri))
        val out = java.io.File(ctx.cacheDir, "share_${photo.id}_${photo.displayName}")
        input?.use { ins -> out.outputStream().use { outs -> ins.copyTo(outs) } }
        out
    }.getOrDefault(java.io.File(ctx.cacheDir, "share.bin"))
    val uri = androidx.core.content.FileProvider.getUriForFile(
        ctx, "com.brain.gallery.fileprovider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(android.content.Intent.createChooser(send, "Share photo"))
}
