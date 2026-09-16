package com.brain.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Surface
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
    onNotInterested: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = Surface, contentColor = Text1) {
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
        if (onDelete != null)
            SheetRow(Icons.Default.Delete, "Delete from device", Color(0xFFF87171), onDelete)
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
