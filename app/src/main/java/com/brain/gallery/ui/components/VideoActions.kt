package com.brain.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.engine.MemoryDocBuilder
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Surface
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Best-practice long-press menu: one sheet, actions matched to the place via flags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionsSheet(
    video: VideoEntity,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onFav: () -> Unit,
    onSimilar: () -> Unit,
    onDetails: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    /** Shown only when the clip actually has a face vector to re-place. */
    hasIdentity: Boolean = false,
    inPersonGroup: Boolean = false,
    onMovePerson: (() -> Unit)? = null,
    onRemoveFromPerson: (() -> Unit)? = null,
    /** Put this clip in a group by hand. */
    onMoveGroup: (() -> Unit)? = null,
    /** Tick it for a bulk action instead of opening it. */
    onPick: (() -> Unit)? = null,
    picked: Boolean = false,

    // ---- sub-views, drawn by this same sheet ----
    // These used to be a second ModalBottomSheet opened from the first. Two
    // sheets in the same frame is a race: the first starts animating out and the
    // second is dropped, so the tap looked like it did nothing at all. One sheet
    // that switches content cannot lose itself.
    mode: SheetMode = SheetMode.ROOT,
    groupChoices: List<String> = emptyList(),
    onBack: () -> Unit = {},
    onPickGroup: (String) -> Unit = {},
    onPickPerson: (Int) -> Unit = {},
    persons: List<com.brain.gallery.data.local.PersonEntity> = emptyList(),
    personCounts: Map<Int, Int> = emptyMap()
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = Surface, contentColor = Text1) {
      when (mode) {
        SheetMode.GROUP -> {
            MoveToGroupBody(video, groupChoices, 1, onBack, onPickGroup)
        }
        SheetMode.PERSON -> {
            MovePersonBody(persons, personCounts, onBack, onPickPerson)
        }
        SheetMode.ROOT -> {
        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VideoThumb(video, Modifier.size(56.dp, 76.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.about.ifEmpty { video.displayName }, color = Text1,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${video.category} • ${video.folderName}", color = Text2, fontSize = 11.5.sp)
            }
        }
        SheetRow(Icons.Default.PlayArrow, "Play", Text1, onPlay)
        SheetRow(if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (video.isFavorite) "Remove favorite" else "Favorite",
            if (video.isFavorite) Pink else Text1, onFav)
        SheetRow(Icons.Default.Search, "More like this", Text1, onSimilar)
        SheetRow(Icons.Default.Info, "Details", Text1, onDetails)
        if (onNotInterested != null)
            SheetRow(Icons.Default.VisibilityOff, "Not interested — show less", Text1, onNotInterested)
        if (hasIdentity && onMovePerson != null) {
            SheetRow(Icons.Default.PersonSearch,
                if (inPersonGroup) "Wrong person — move to…" else "Add to a person…", Cyan,
                { onMovePerson() })
        }
        if (inPersonGroup && onRemoveFromPerson != null) {
            SheetRow(Icons.Default.PersonOff, "Not this person", Text1, onRemoveFromPerson)
        }
        if (onMoveGroup != null) {
            SheetRow(Icons.Default.DriveFileMove, "Move to another group…", Cyan, onMoveGroup)
        }
        if (onPick != null) {
            SheetRow(
                if (picked) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                if (picked) "Unselect" else "Select for a group of actions",
                if (picked) Green else Text1, onPick
            )
        }
        if (onDelete != null)
            SheetRow(Icons.Default.Delete, "Delete from device", Color(0xFFF87171), onDelete)
        } // ROOT
      } // when
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(18.dp, 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Text1, fontSize = 14.5.sp)
    }
}

@Composable
fun VideoDetailsDialog(video: VideoEntity, onDismiss: () -> Unit) {
    val doc = MemoryDocBuilder.build(video)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(CardShape).background(Surface).padding(20.dp)
            .verticalScroll(rememberScrollState())) {
            Text(video.displayName, color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(video.about, color = Text2, fontSize = 12.5.sp)
            Spacer(Modifier.height(12.dp))
            DetailLine("Who", doc.who.ifEmpty { if (video.faceCount > 0) "${video.faceCount} faces" else "—" })
            DetailLine("What", doc.what.ifEmpty { "—" })
            DetailLine("Where", doc.where)
            DetailLine("When", doc.whenText)
            DetailLine("Vibe", doc.vibe.ifEmpty { "—" })
            DetailLine("Length", fmtDur(video.durationMs).ifEmpty { "—" })
            DetailLine("Size", com.brain.gallery.domain.organize.fmtSize(video.sizeBytes))
            DetailLine("Added", SimpleDateFormat("d MMM yyyy", Locale.US)
                .format(Date(video.dateAddedSec * 1000)))
            DetailLine("Watched", "${video.watchCount}×${if (video.watchCount > 0) " • ${(video.avgCompletion * 100).toInt()}% avg" else ""}")
            DetailLine("Brain", "level ${video.brainLevel} • ${(video.confidence * 100).toInt()}%")
            if (video.tagList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(video.tagList.joinToString("  •  "), color = Text2, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("Close", color = Text1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, color = Text2, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(v, color = Text1, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f))
    }
}

/**
 * Where should this clip live? Offers the groups already in use, the group it is
 * in now, and the option to invent one. Choosing the current group clears the
 * hand-placement and hands the clip back to the brain.
 */
/** Which view the actions sheet is showing. */
enum class SheetMode { ROOT, GROUP, PERSON }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToGroupSheet(
    current: String,
    choices: List<String>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val noun = if (selectedCount == 1) "this clip" else "these $selectedCount clips"

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface, contentColor = Text1) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DriveFileMove, null, tint = Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Move $noun to…", color = Text1, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (current.isBlank()) "The brain decides now"
                    else "Currently in \"$current\"",
                    color = Text2, fontSize = 11.5.sp, maxLines = 1
                )
            }
        }

        if (current.isNotBlank()) {
            SheetRow(Icons.Default.Undo, "Hand it back to the brain", Text1) {
                onPick("")
            }
        }

        choices.filter { it != current }.take(12).forEach { g ->
            SheetRow(Icons.Default.Label, g, Text1) { onPick(g) }
        }

        if (creating) {
            Row(Modifier.fillMaxWidth().padding(18.dp, 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Name this group", color = Text2) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text("Save", color = if (newName.isBlank()) Text2 else Green,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = newName.isNotBlank()) {
                        onPick(newName.trim())
                    }.padding(6.dp)
                )
            }
        } else {
            SheetRow(Icons.Default.Add, "New group…", Cyan) { creating = true }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** The group sub-view, drawn inside the actions sheet rather than over it. */
@Composable
private fun MoveToGroupBody(
    video: VideoEntity,
    choices: List<String>,
    count: Int,
    onBack: () -> Unit,
    onPick: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val current = video.effectiveGroup

    SheetHeader(Icons.Default.ArrowBack, "Move this clip to…", onBack)
    if (current.isNotBlank()) {
        SheetRow(Icons.Default.Undo, "Hand it back to the brain", Text1) { onPick("") }
    }
    choices.filter { it != current }.take(12).forEach { g ->
        SheetRow(Icons.Default.Label, g, Text1) { onPick(g) }
    }
    if (creating) {
        Row(Modifier.fillMaxWidth().padding(18.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newName, onValueChange = { newName = it },
                singleLine = true, placeholder = { Text("Name this group", color = Text2) },
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text("Save", color = if (newName.isBlank()) Text2 else Green,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = newName.isNotBlank()) { onPick(newName.trim()) }
                    .padding(6.dp))
        }
    } else {
        SheetRow(Icons.Default.Add, "New group…", Cyan) { creating = true }
    }
}

/** The person sub-view, drawn inside the actions sheet. */
@Composable
private fun MovePersonBody(
    persons: List<com.brain.gallery.data.local.PersonEntity>,
    counts: Map<Int, Int>,
    onBack: () -> Unit,
    onPick: (Int) -> Unit
) {
    SheetHeader(Icons.Default.ArrowBack, "Move to which person?", onBack)
    persons.sortedByDescending { counts[it.id] ?: 0 }.forEach { p ->
        Row(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPick(p.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val initial = p.name.ifBlank { p.suggestedName.ifBlank { "?" } }.take(1).uppercase()
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF26324A)),
                contentAlignment = Alignment.Center) {
                Text(initial, color = Text1, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name.ifBlank { p.suggestedName.ifBlank { "Person ${p.id}" } },
                    color = Text1, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${counts[p.id] ?: 0} videos" + if (p.verified) " • you named this" else "",
                    color = Text2, fontSize = 11.5.sp)
            }
        }
    }
    Row(Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable { onPick(-1) }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PersonAdd, null, tint = Green, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text("Someone new", color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SheetHeader(icon: ImageVector, title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable { onBack() }
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Text1, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = Text1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
