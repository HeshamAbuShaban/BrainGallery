package com.brain.gallery.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.domain.organize.GroupKind
import com.brain.gallery.engine.keeperReason
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.domain.organize.fmtSize
import com.brain.gallery.ui.components.VideoActionsSheet
import com.brain.gallery.ui.components.VideoDetailsDialog
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.components.MoveToGroupSheet
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.theme.Yellow

@Composable
fun GroupDetail(
    group: SmartGroup,
    person: PersonEntity?,
    onBack: () -> Unit,
    onFav: (VideoEntity) -> Unit,
    onDeleteRedundant: (List<Long>) -> Unit = {},
    onPlay: (VideoEntity) -> Unit = {},
    onDeleteOne: (VideoEntity) -> Unit = {},
    onSimilar: (VideoEntity) -> Unit = {},
    onRename: (Int, String) -> Unit = { _, _ -> },
    onSplitOut: (Int, Long) -> Unit = { _, _ -> },
    onDismissSplit: (Int) -> Unit = {},
    onMoveVideo: ((Long, Int) -> Unit)? = null,
    pickedIds: Set<Long> = emptySet(),
    onTogglePick: ((Long) -> Unit)? = null,
    groupChoices: List<String> = emptyList(),
    onMoveGroup: ((List<Long>, String) -> Unit)? = null,
    onSwipeNotInterested: ((Long) -> Unit)? = null,
    persons: List<PersonEntity> = emptyList(),
    personCounts: Map<Int, Int> = emptyMap()
) {
    BackHandler(enabled = true) { onBack() }
    val isDups = group.kind == GroupKind.DUPLICATES
    val redundant = group.videos.filter { it.id in group.redundantIds }
    var menuFor by remember { mutableStateOf<VideoEntity?>(null) }
    var detailsFor by remember { mutableStateOf<VideoEntity?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<Long?>(null) }
    var moveGroupIds by remember { mutableStateOf<List<Long>?>(null) }

    Column(Modifier.fillMaxSize().background(Bg)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(bottom = 84.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 16.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack,
                modifier = Modifier.background(Color(0xFF1D2534), CircleShape).size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Text1,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.title, color = Text1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(group.subtitle, color = Text2, fontSize = 12.sp)
            }
            if (person != null) {
                IconButton(onClick = { renameOpen = true },
                    modifier = Modifier.background(Color(0xFF1D2534), CircleShape).size(34.dp)) {
                    Icon(Icons.Default.Edit, null, tint = Text1, modifier = Modifier.size(15.dp))
                }
            }
        }
        if (group.splitSuggested) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 0.dp).background(
                Color(0x33243B1B), CardShape).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("This may be two different people. Tap one that looks wrong to split it out.",
                    color = Text1, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Got it", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismissSplit(person?.id ?: -1) }.padding(6.dp))
            }
        }
        if (isDups && redundant.isNotEmpty()) {
            Button(onClick = { onDeleteRedundant(redundant.map { it.id }) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2410),
                    contentColor = Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete ${redundant.size} redundant • free ${fmtSize(group.savingsBytes)}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyVerticalGrid(GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.videos, key = { it.id }) { v ->
                val keeper = v.id in group.keeperIds
                val redund = v.id in group.redundantIds
                Box(Modifier.aspectRatio(0.7f).alpha(if (redund) 0.55f else 1f)) {
                    VideoThumb(v, Modifier.fillMaxSize(),
                        onClick = {
                            if (pickedIds.isNotEmpty() && onTogglePick != null) onTogglePick(v.id)
                            else onPlay(v)
                        },
                        onLongClick = { menuFor = v },
                        onSwipeRight = { onFav(v) },
                        onSwipeLeft = { onSwipeNotInterested?.let { it(v.id) } ?: onDeleteOne(v) })
                    if (v.id in pickedIds) {
                        Box(Modifier.fillMaxSize().background(Accent.copy(alpha = 0.28f)))
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp))
                    }
                    if (keeper) {
                        Text("KEEPER", color = Color.Black, fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                .background(Green, CircleShape).padding(6.dp, 2.dp))
                        if (isDups) {
                            Text(
                                keeperReason(v, group.videos.filter { it.id in group.redundantIds }),
                                color = Color.White, fontSize = 9.sp,
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .padding(start = 5.dp, bottom = 22.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CardShape)
                                    .padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    if (v.faceCount > 0 && !keeper) {
                        Text("☺ ${v.faceCount}", color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(6.dp, 2.dp))
                    }
                    if (person != null && group.splitSuggested) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.001f))
                            .clickable { onSplitOut(person.id, v.id) })
                    }
                    IconButton(onClick = { onFav(v) },
                        modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                        Icon(if (v.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null, tint = if (v.isFavorite) Pink else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    menuFor?.let { mv ->
        VideoActionsSheet(video = mv,
            onDismiss = { menuFor = null },
            onPlay = { menuFor = null; onPlay(mv) },
            onFav = { menuFor = null; onFav(mv) },
            onSimilar = { menuFor = null; onSimilar(mv) },
            onDetails = { detailsFor = mv; menuFor = null },
            onDelete = { menuFor = null; onDeleteOne(mv) },
            hasIdentity = mv.faceCount > 0,
            inPersonGroup = person != null,
            onMovePerson = { menuFor = null; moveTarget = mv.id },
            onRemoveFromPerson = {
                menuFor = null
                person?.let { onSplitOut(it.id, mv.id) }
            },
            onMoveGroup = { menuFor = null; moveGroupIds = listOf(mv.id) },
            onPick = { menuFor = null; onTogglePick?.let { it(mv.id) } },
            picked = mv.id in pickedIds)
    }
    val movingIds = moveGroupIds
    if (movingIds != null) {
        MoveToGroupSheet(
            current = if (movingIds.size == 1) {
                group.videos.firstOrNull { it.id == movingIds.first() }?.effectiveGroup.orEmpty()
            } else "",
            choices = groupChoices,
            selectedCount = movingIds.size,
            onDismiss = { moveGroupIds = null },
            onPick = { g ->
                moveGroupIds = null
                onMoveGroup?.invoke(movingIds, g)
            })
    }
    moveTarget?.let { vid ->
        MovePersonDialog(persons = persons, counts = personCounts,
            onDismiss = { moveTarget = null },
            onPick = { target -> moveTarget = null; onMoveVideo?.let { it(vid, target) } })
    }
    detailsFor?.let { VideoDetailsDialog(video = it, onDismiss = { detailsFor = null }) }
    if (renameOpen && person != null) {
        RenameDialog(initial = person.name.ifBlank { person.suggestedName },
            onDismiss = { renameOpen = false },
            onSave = { n -> onRename(person.id, n); renameOpen = false })
    }
}

@Composable
fun RenameGroupDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    heading: String = "Name this group",
    hint: String = "Your name sticks everywhere and survives a rebuild.",
    placeholder: String = "e.g. Music & clips",
    onReset: (() -> Unit)? = null
) {
    RenameDialog(initial, onDismiss, onSave, heading, hint, placeholder, onReset)
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    heading: String = "Name this person",
    hint: String = "",
    placeholder: String = "e.g. Sara",
    onReset: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(CardShape).background(Color(0xFF151B26)).padding(20.dp)) {
            Text(heading, color = Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(hint, color = Text2, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(10.dp))
            TextField(value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text(placeholder, color = Text2) },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onReset != null) {
                    Text("Reset", color = Yellow, fontSize = 14.sp,
                        modifier = Modifier.clickable { onReset(); onDismiss() }.padding(10.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Cancel", color = Text2, fontSize = 14.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(10.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSave(text) }.padding(10.dp))
            }
        }
    }
}
