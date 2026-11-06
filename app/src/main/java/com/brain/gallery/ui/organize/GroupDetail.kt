package com.brain.gallery.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.brain.gallery.ui.components.FavButton
import com.brain.gallery.ui.components.VideoActionsSheet
import com.brain.gallery.ui.components.VideoDetailsDialog
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.hero.CardWaveStack
import com.brain.gallery.ui.hero.PortalGate
import com.brain.gallery.ui.hero.PortalOverlay
import com.brain.gallery.ui.hero.rememberReducedMotion
import com.brain.gallery.ui.components.SheetMode
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.glass.glassReserve
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.DangerContainer
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.ChipShape
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
    var sheetMode by remember { mutableStateOf(SheetMode.ROOT) }
    // Big groups page: 60 thumbs at a time, so opening a 700-clip person
    // does not compose the whole library at once.
    var shown by remember(group.id) { mutableStateOf(60) }
    val visible = group.videos.take(shown)

    // Mechanic C: the session's first grid entry into the player goes
    // through the portal; everyone after goes straight in. Reduced motion
    // skips the door entirely — direct navigation, as the constitution says.
    val reducedMotion = rememberReducedMotion()
    var portalFor by remember { mutableStateOf<VideoEntity?>(null) }
    fun enterSacred(v: VideoEntity) {
        if (PortalGate.canPortal() && !reducedMotion) portalFor = v else onPlay(v)
    }

    // The whole crown is grid content now: title, cover pile and the warning
    // banners scroll away with the clips. Before, a 320dp stack held the
    // screen while a 3-column grid tried to live in what was left.
    val gridState = rememberLazyGridState()
    val crownGone by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 24
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyVerticalGrid(
            GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = topInset + 8.dp,
                bottom = glassReserve() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(3) }, key = "header") {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack,
                        modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(38.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Text1,
                            modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.title, color = Text1, fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold)
                        Text(group.subtitle, color = Text2, fontSize = 12.sp)
                    }
                    if (person != null) {
                        IconButton(onClick = { renameOpen = true },
                            modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(34.dp)) {
                            Icon(Icons.Default.Edit, null, tint = Text1,
                                modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            // Mechanic B lives here and only here: the group's cover pile
            // frames the review, then hands off to the player. One mechanic
            // per screen — and it travels with the review now.
            if (group.videos.isNotEmpty()) {
                item(span = { GridItemSpan(3) }, key = "hero") {
                    CardWaveStack(
                        clips = group.videos,
                        onPlay = onPlay,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            if (group.splitSuggested) {
                item(span = { GridItemSpan(3) }, key = "split") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .glass(CardShape, Glass.pane(Accent)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("This may be two different people. Tap one that looks wrong to split it out.",
                            color = Text1, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Got it", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onDismissSplit(person?.id ?: -1) }.padding(6.dp))
                    }
                }
            }
            if (isDups && redundant.isNotEmpty()) {
                item(span = { GridItemSpan(3) }, key = "redundant") {
                    Button(onClick = { onDeleteRedundant(redundant.map { it.id }) },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerContainer,
                            contentColor = Yellow),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete ${redundant.size} redundant • free ${fmtSize(group.savingsBytes)}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(visible, key = { it.id }) { v ->
                val keeper = v.id in group.keeperIds
                val redund = v.id in group.redundantIds
                Box(Modifier.aspectRatio(0.7f).alpha(if (redund) 0.55f else 1f)) {
                    VideoThumb(v, Modifier.fillMaxSize(),
                        onClick = {
                            if (pickedIds.isNotEmpty() && onTogglePick != null) onTogglePick(v.id)
                            else enterSacred(v)
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
                    // One heart, everywhere: tap springs it, haptics answer.
                    FavButton(
                        favorite = v.isFavorite,
                        onToggle = { onFav(v) },
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = 15.dp,
                        tapTarget = 30.dp,
                        tint = Color.White.copy(alpha = 0.8f),
                        contentDescription = if (v.isFavorite) "Unfavorite" else "Favorite"
                    )
                }
            }
            if (shown < group.videos.size) {
                item(span = { GridItemSpan(3) }, key = "more") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Show 60 more (${group.videos.size - shown} left)",
                            color = Accent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .glass(ChipShape, Glass.pane(Accent))
                                .clickable { shown += 60 }
                                .padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
            }
        }
        // Once the crown is gone the way back must not require scrolling up:
        // one small pane appears in its place, and leaves when the crown does.
        AnimatedVisibility(
            visible = crownGone,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding().padding(10.dp)
        ) {
            IconButton(onClick = onBack,
                modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Text1,
                    modifier = Modifier.size(18.dp))
            }
        }
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
            onMovePerson = { sheetMode = SheetMode.PERSON },
            onRemoveFromPerson = {
                menuFor = null
                person?.let { onSplitOut(it.id, mv.id) }
            },
            onMoveGroup = { sheetMode = SheetMode.GROUP },
            onPick = { menuFor = null; onTogglePick?.let { it(mv.id) } },
            picked = mv.id in pickedIds,
            mode = sheetMode,
            groupChoices = groupChoices,
            onBack = { sheetMode = SheetMode.ROOT },
            onPickGroup = { g ->
                sheetMode = SheetMode.ROOT
                menuFor = null
                onMoveGroup?.invoke(listOf(mv.id), g)
            },
            onPickPerson = { pid ->
                sheetMode = SheetMode.ROOT
                menuFor = null
                onMoveVideo?.invoke(mv.id, pid)
            },
            persons = persons,
            personCounts = personCounts)
    }
    detailsFor?.let { VideoDetailsDialog(video = it, onDismiss = { detailsFor = null }) }

    // The one door with ceremony: the session's first grid opening.
    portalFor?.let { v ->
        PortalOverlay(
            video = v,
            onOpen = { portalFor = null; onPlay(v) },
            onDismiss = { portalFor = null })
    }
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
    placeholder: String = "",
    onReset: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.glass(CardShape, Glass.raised(Accent)).padding(20.dp)) {
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
