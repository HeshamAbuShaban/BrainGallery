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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2

@Composable
fun GroupDetail(group: SmartGroup, onBack: () -> Unit, onFav: (VideoEntity) -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg)) {
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
        }
        LazyVerticalGrid(GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.videos, key = { it.id }) { v ->
                Box(Modifier.aspectRatio(0.7f)) {
                    VideoThumb(v, Modifier.fillMaxSize())
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
