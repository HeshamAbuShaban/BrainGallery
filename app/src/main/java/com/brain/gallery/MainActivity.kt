package com.brain.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.ui.feed.FeedScreen
import com.brain.gallery.ui.organize.OrganizeScreen
import com.brain.gallery.ui.organize.SearchScreen
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.BrainTheme
import com.brain.gallery.ui.theme.Motion
import com.brain.gallery.ui.theme.Text2
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var player: PlayerManager

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) BrainScanService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) BrainScanService.start(this)
        else permLauncher.launch(missing.toTypedArray())
        setContent { BrainTheme { Root(player) } }
    }

    override fun onPause() { super.onPause(); player.pause() }
    override fun onDestroy() { super.onDestroy(); player.release() }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun Root(player: PlayerManager) {
    var tab by remember { mutableIntStateOf(1) } // land on Organize: the product
    var prev by remember { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val tabs = listOf(
        Tab("For You", Icons.Default.PlayArrow),
        Tab("Organize", Icons.Default.GridView),
        Tab("Search", Icons.Default.Search)
    )

    fun go(i: Int) {
        if (i == tab) return
        prev = tab
        tab = i
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF10151F),
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { go(i) },
                        icon = {
                            Icon(t.icon, null, modifier = Modifier.size(22.dp))
                        },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            unselectedIconColor = Text2,
                            unselectedTextColor = Text2,
                            indicatorColor = Color(0x228B5CF6)
                        )
                    )
                }
            }
        }
    ) { pad ->
        val forward = tab >= prev
        Box(Modifier.fillMaxSize().background(Bg)) {
            // Feed is edge-to-edge; the other tabs respect the bar.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val off = if (forward) 1 else -1
                    (slideInHorizontally(tween(Motion.mediumMs, easing = Motion.emphasized)) { it / 6 * off } +
                            fadeIn(tween(Motion.mediumMs)))
                        .togetherWith(
                            slideOutHorizontally(tween(Motion.mediumMs, easing = Motion.emphasized)) { -it / 6 * off } +
                                    fadeOut(tween(Motion.fastMs))
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "tab"
            ) { t ->
                when (t) {
                    0 -> FeedScreen(player)
                    1 -> Box(Modifier.fillMaxSize().padding(pad)) { OrganizeScreen(player) }
                    else -> Box(Modifier.fillMaxSize().padding(pad)) { SearchScreen(player) }
                }
            }
        }
    }
}
