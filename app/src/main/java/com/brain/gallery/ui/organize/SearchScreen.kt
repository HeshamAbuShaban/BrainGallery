package com.brain.gallery.ui.organize

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.ui.components.FavIcon
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
import com.brain.gallery.ui.theme.inkOn
import com.brain.gallery.ui.theme.Cyan
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
    // The narrowing lenses and the people the grid groups by.
    val lens by vm.lens.collectAsState()
    val personNames by vm.personNames.collectAsState()
    val narrowingCategories by vm.narrowingCategories.collectAsState()
    val narrowingFolders by vm.narrowingFolders.collectAsState()
    val sections = remember(results, personNames) { resultSections(results, personNames) }
    // One breath between answers: the grid lifts out, the next one in.
    val gridFade = remember { Animatable(1f) }
    LaunchedEffect(results) {
        gridFade.snapTo(0.25f)
        gridFade.animateTo(1f, tween(240))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(top = 4.dp)) {
            Text("Search", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text("names · tags · faces · voices — then narrow with chips: this week, this month, who's in frame",
                color = Text2, fontSize = 12.sp, maxLines = 2,
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
            // The narrowing lenses: what the device actually holds narrows the
            // query; chips appear once a query exists. Drawn from the library,
            // never invented.
            if (q.length >= 2) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { LensChip("This week", lens.time == TimeLens.WEEK) { vm.toggleTime(TimeLens.WEEK) } }
                    item { LensChip("This month", lens.time == TimeLens.MONTH) { vm.toggleTime(TimeLens.MONTH) } }
                    item { LensChip("This year", lens.time == TimeLens.YEAR) { vm.toggleTime(TimeLens.YEAR) } }
                    narrowingCategories.forEach { c ->
                        item(key = "cat_$c") {
                            LensChip(c, lens.category == c) { vm.toggleCategory(c) }
                        }
                    }
                    narrowingFolders.forEach { f ->
                        item(key = "fold_$f") {
                            LensChip(f, lens.folder == f) { vm.toggleFolder(f) }
                        }
                    }
                }
            }
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
                Text("Things worth trying",
                    color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 4.dp))
                val suggestions by vm.suggestions.collectAsState()
                FlowRow(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { sg ->
                        Text(sg, color = inkOn(Accent), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Accent.copy(alpha = 0.85f))
                                .clickable { vm.query(sg); vm.rememberSearch(sg) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    // Two compound doorways: the query and the week set in
                    // one tap. They explain chips better than any caption.
                    Text("talking · this week", color = Accent, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ChipFill)
                            .clickable {
                                vm.query("talking"); vm.rememberSearch("talking")
                                vm.toggleTime(TimeLens.WEEK)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                    Text("people · this year", color = Accent, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ChipFill)
                            .clickable {
                                vm.query("people"); vm.rememberSearch("people")
                                vm.toggleTime(TimeLens.YEAR)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (q.length >= 2 && results.isEmpty()) {
                val lensActive = lens.time != TimeLens.ANY ||
                    lens.category != null || lens.folder != null
                Text(
                    if (lensActive) "Nothing under this lens — try clearing it."
                    else "No matches for \"$q\"",
                    color = Text2, fontSize = 13.sp,
                    modifier = Modifier.padding(20.dp))
            }
            // One grid, both media: videos keep their tall frame and the
            // long-press sheet, photos keep their plain flat tile — a result
            // is a result, but each medium stays shaped like itself. Person
            // headers ride full-width when the library knows more than one
            // named face among the matches; a single bucket stays flat.
            LazyVerticalGrid(GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp,
                    bottom = glassReserve() + 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).graphicsLayer { alpha = gridFade.value }) {
                sections.forEachIndexed { i, section ->
                    section.title?.let { t ->
                        item(span = { GridItemSpan(3) }, key = "hdr_$i") {
                            SectionHeader(t, section.hits.size)
                        }
                    }
                    items(section.hits, key = {
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
                                // Pops on newly-appearing state; steady when already known.
                                FavIcon(hit.photo.isFavorite,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp),
                                    size = 14.dp,
                                    contentDescription = "favorite")
                            }
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

/** A chip the library itself drew — the same values that seed the suggestions. */
@Composable
private fun LensChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Text(
        label,
        color = if (selected) Color.Black else Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Accent else ChipFill)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** A person the grid groups by. Chips and captions, never a guess on screen. */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = Cyan, fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.weight(1f))
        Text("$count", color = Text2, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold)
    }
}
