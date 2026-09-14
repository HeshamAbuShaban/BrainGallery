package com.brain.gallery.ui.organize

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.domain.organize.GroupKind
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.domain.organize.fmtSize
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2

@Composable
fun GroupDetail(
    group: SmartGroup,
    onBack: () -> Unit,
    onFav: (VideoEntity) -> Unit,
    onDeleteRedundant: (List<Long>) -> Unit = {},
    onPlay: (VideoEntity) -> Unit = {}
) {
    val isDups = group.kind == GroupKind.DUPLICATES
    val redundant = group.videos.filter { it.id in group.redundantIds }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 16.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack,
                modifier = Modifier.background(Color(0xFF1D2534), CircleShape).size(38.dp)) {
                Icon(Icons.Default.ArrowBack, null, tint = Text1,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.title, color = Text1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(group.subtitle, color = Text2, fontSize = 12.sp)
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
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.videos, key = { it.id }) { v ->
                val keeper = v.id in group.keeperIds
                val redund = v.id in group.redundantIds
                Box(Modifier.aspectRatio(0.7f).alpha(if (redund) 0.55f else 1f)) {
                    VideoThumb(v, Modifier.fillMaxSize()) { onPlay(v) }
                    if (keeper) {
                        Text("KEEPER", color = Color.Black, fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                .background(Green, CircleShape).padding(6.dp, 2.dp))
                    }
                    if (v.faceCount > 0 && !keeper) {
                        Text("☺ ${v.faceCount}", color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(6.dp, 2.dp))
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
}
