package com.brain.gallery.ui.organize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import androidx.hilt.navigation.compose.hiltViewModel
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.ui.components.ShimmerBar
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.theme.Yellow

@Composable
fun OrganizeScreen(vm: OrganizeViewModel = hiltViewModel()) {
    val groups by vm.groupList.collectAsState()
    val stats by vm.stats.collectAsState()
    val loading by vm.loading.collectAsState()
    val selected by vm.selected.collectAsState()
    val deleteAsk by vm.deleteAsk.collectAsState()

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

    if (selected != null) {
        GroupDetail(group = selected!!, onBack = { vm.close() }, onFav = { vm.toggleFav(it) },
            onDeleteRedundant = { vm.requestDelete(it) })
        return
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Organized by your on-device brain", color = Text2, fontSize = 12.5.sp)
            }
            IconButton(onClick = { vm.rescan() },
                modifier = Modifier.background(Color(0xFF1D2534), CircleShape).size(40.dp)) {
                Icon(Icons.Default.Refresh, null, tint = Text1, modifier = Modifier.size(18.dp))
            }
        }
        // Stats strip
        if (loading) {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { ShimmerBar(Modifier.weight(1f), 56.dp, 16.dp) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(20.dp, 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("${stats.total}", "videos", Accent, Modifier.weight(1f))
                StatCard("${stats.memories}", "memories", Green, Modifier.weight(1f))
                StatCard("${stats.clutter}", "clutter", Yellow, Modifier.weight(1f))
            }
            if (stats.pending > 0) {
                Row(Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Understanding ${stats.pending} videos…",
                            color = Text2, fontSize = 11.5.sp)
                        LinearProgressIndicator(
                            progress = { stats.understoodPct / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = Cyan, trackColor = Color(0xFF1D2534))
                    }
                }
            }
        }
        // Groups grid
        if (loading) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { ShimmerBar(Modifier.fillMaxWidth(), 120.dp, 20.dp) }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                    val vis = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(vis,
                        enter = fadeIn(tween(350, i * 60)) +
                            slideInVertically(tween(350, i * 60)) { it / 3 }) {
                        GroupCard(g) { vm.open(g) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF151B26), CardShape).padding(12.dp)) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Text2, fontSize = 11.sp)
    }
}

@Composable
fun GroupCard(g: SmartGroup, onClick: () -> Unit) {
    val accent = Color(g.accent)
    Box(Modifier
        .fillMaxWidth()
        .aspectRatio(0.86f)
        .clip(CardShape)
        .background(Color(0xFF151B26))
        .clickable { onClick() }) {
        val cover = g.videos.firstOrNull()
        if (cover != null) VideoThumb(cover, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
            startY = 120f)))
        Box(Modifier.align(Alignment.TopStart).padding(10.dp)
            .background(accent.copy(alpha = 0.9f), CircleShape).padding(8.dp, 3.dp)) {
            Text("${g.videos.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(g.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(g.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2)
        }
    }
}
