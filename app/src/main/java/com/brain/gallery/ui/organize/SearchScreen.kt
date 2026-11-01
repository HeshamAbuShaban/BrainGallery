package com.brain.gallery.ui.organize

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.ui.components.SpotlightPlayer
import com.brain.gallery.ui.components.VideoActionsSheet
import com.brain.gallery.ui.components.VideoDetailsDialog
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.GlassSegmented
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.glass.glassReserve
import androidx.hilt.navigation.compose.hiltViewModel
import com.brain.gallery.ui.spotlight.SpotlightController
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2

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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Bg).padding(top = 20.dp)
        .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 84.dp)) {
            Text("Search", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text("Names, tags, people, voice — try \"talking\"", color = Text2, fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            TextField(value = q, onValueChange = { vm.query(it) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { vm.rememberSearch(q) }),
                placeholder = { Text("birthday, beach, concert…", color = Text2) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Text2) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Text1, unfocusedTextColor = Text1),
                modifier = Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 4.dp)
                    .glass(RoundedCornerShape(16.dp), Glass.onDark(Accent)))
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
                // empty field should invite, not blankly wait.
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
                FlowRow(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.suggestions.forEach { sg ->
                        Text(sg, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.06f))
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
            LazyVerticalGrid(GridCells.Fixed(3),
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { v ->
                    VideoThumb(v, Modifier.aspectRatio(0.7f),
                        onClick = { spotlight.open(v) }, onLongClick = { menuFor = v })
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
    }
}
