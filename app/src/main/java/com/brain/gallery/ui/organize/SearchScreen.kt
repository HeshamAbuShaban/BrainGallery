package com.brain.gallery.ui.organize

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.ui.components.SpotlightPlayer
import com.brain.gallery.ui.components.VideoActionsSheet
import com.brain.gallery.ui.components.VideoDetailsDialog
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.GlassSegmented
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.glass.glassReserve
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.brain.gallery.ui.spotlight.SpotlightController
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.ChipFill
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.theme.Pink

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    spotlight: SpotlightController,
    vm: SearchViewModel = hiltViewModel()
) {
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val filter by vm.filter.collectAsState()
    val deleteAsk by vm.deleteAsk.collectAsState()
    val haptics = LocalHapticFeedback.current
    var menuFor by remember { mutableStateOf<VideoEntity?>(null) }
    var detailsFor by remember { mutableStateOf<VideoEntity?>(null) }
    var photoFor by remember { mutableStateOf<PhotoEntity?>(null) }

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
    val photoDelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) vm.confirmPhotoDelete()
    }
    val photoDeleteAsk by vm.photoDeleteAsk.collectAsState()
    LaunchedEffect(photoDeleteAsk) {
        photoDeleteAsk?.let {
            photoDelLauncher.launch(IntentSenderRequest.Builder(it).build())
            vm.consumePhotoDeleteAsk()
        }
    }
    // Content ID's "listen" step: the sent file is opened through the system
    // photo picker — no storage permission ever, and nothing is copied.
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.identify(uri)
    }
    val identifyMsg by vm.identifyMsg.collectAsState()
    val identifying by vm.identifying.collectAsState()
    // The rail in the viewer is read from the library, not from the result
    // page: "more like this" should leave the search behind.
    var photoSimilar by remember { mutableStateOf<List<PhotoEntity>>(emptyList()) }
    LaunchedEffect(photoFor?.id) {
        photoSimilar = photoFor?.let { vm.photoNeighbors(it) }.orEmpty()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(top = 4.dp)) {
            Text("Search", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text("Names, tags, people, voice, photos — try \"talking\"", color = Text2, fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            TextField(value = q, onValueChange = { vm.query(it) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { vm.rememberSearch(q) }),
                placeholder = { Text("Search your memories…", color = Text2) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Text2) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Text1, unfocusedTextColor = Text1),
                modifier = Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 4.dp)
                    .glass(RoundedCornerShape(16.dp), Glass.pane(Accent)))
            // The Identify banner: the brain answering "what is this file".
            // It stays until the user types — typing owns the grid again.
            val banner = when {
                identifying -> "Listening to the file…"
                else -> identifyMsg
            }
            banner?.let {
                Text(it, color = Text1, fontSize = 12.5.sp, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(20.dp, 6.dp, 20.dp, 2.dp)
                        .glass(RoundedCornerShape(14.dp), Glass.pane(Accent))
                        .padding(14.dp, 10.dp))
            }
            // The lens row: narrows what was found, never re-queries behind it.
            GlassSegmented(
                labels = listOf("All", "People", "Favorites", "Talking"),
                selectedIndex = VoiceFilter.entries.indexOf(filter).coerceAtLeast(0),
                onSelect = { i ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.filter(VoiceFilter.entries[i])
                },
                modifier = Modifier.fillMaxWidth().padding(20.dp, 8.dp, 20.dp, 0.dp),
                selectedColor = Accent
            )
            if (q.length < 2) {
                // The landing: what you looked for, and where to start. An
                // empty field should invite, not blankly wait. One gifting
                // route too: a file from someone else, answered by content.
                Row(
                    Modifier.fillMaxWidth().padding(20.dp, 10.dp, 20.dp, 0.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            pickFile.launch(PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        .glass(RoundedCornerShape(14.dp), Glass.pane(Accent))
                        .padding(14.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("◎", color = Accent, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Identify a file someone sent you", color = Text1,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("the search that takes no description", color = Text2,
                            fontSize = 11.5.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val recents by vm.recents.collectAsState()
                if (recents.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 0.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", color = Text1, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Clear", color = Text2, fontSize = 12.sp,
                            modifier = Modifier.clickable { vm.clearRecents() }.padding(6.dp))
                    }
                    recents.forEach { r ->
                        Text(r, color = Text1, fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.query(r); vm.rememberSearch(r) }
                                .padding(20.dp, 10.dp))
                    }
                }
                Text(if (recents.isEmpty()) "Try searching for" else "Or try",
                    color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 4.dp))
                val suggestions by vm.suggestions.collectAsState()
                FlowRow(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { sg ->
                        Text(sg, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ChipFill)
                                .clickable { vm.query(sg); vm.rememberSearch(sg) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (q.length >= 2 && results.isEmpty()) {
                Text("No matches for \"$q\"", color = Text2, fontSize = 13.sp,
                    modifier = Modifier.padding(20.dp))
            }
            // One grid, both media: videos keep their tall frame and the
            // long-press sheet, photos keep their plain flat tile — a result
            // is a result, but each medium stays shaped like itself.
            LazyVerticalGrid(GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp,
                    bottom = glassReserve() + 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)) {
                items(results, key = {
                    when (it) {
                        is SearchHit.VideoHit -> "v_${it.video.id}"
                        is SearchHit.PhotoHit -> "p_${it.photo.id}"
                    }
                }) { hit ->
                    when (hit) {
                        is SearchHit.VideoHit -> VideoThumb(hit.video, Modifier.aspectRatio(0.7f),
                            onClick = { spotlight.open(hit.video) },
                            onLongClick = { menuFor = hit.video })
                        is SearchHit.PhotoHit -> Box {
                            AsyncImage(
                                model = hit.photo.uri,
                                contentDescription = hit.photo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        photoFor = hit.photo
                                    })
                            if (hit.photo.isFavorite) {
                                Icon(Icons.Default.Favorite, "favorite",
                                    tint = Pink, modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp))
                            }
                        }
                    }
                }
            }
        }
        menuFor?.let { mv ->
            VideoActionsSheet(video = mv,
                onDismiss = { menuFor = null },
                onPlay = { menuFor = null; spotlight.open(mv) },
                onFav = { menuFor = null; vm.toggleFav(mv) },
                onSimilar = { menuFor = null; spotlight.open(mv) },
                onDetails = { detailsFor = mv; menuFor = null },
                onDelete = { menuFor = null; vm.requestDelete(listOf(mv.id)) })
        }
        detailsFor?.let { VideoDetailsDialog(video = it, onDismiss = { detailsFor = null }) }
        photoFor?.let { p ->
            PhotoViewer(
                photo = p,
                similar = photoSimilar,
                onPickSimilar = { photoFor = it },
                onClose = { photoFor = null },
                onFav = { vm.togglePhotoFav(p) },
                onDelete = { photoFor = null; vm.requestDeletePhotos(listOf(p.id)) })
        }
    }
}
