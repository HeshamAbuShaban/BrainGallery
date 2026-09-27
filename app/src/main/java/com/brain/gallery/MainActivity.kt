package com.brain.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.ui.components.SpotlightPlayer
import com.brain.gallery.ui.feed.FeedScreen
import com.brain.gallery.ui.organize.OrganizeScreen
import com.brain.gallery.ui.organize.SearchScreen
import com.brain.gallery.ui.spotlight.SpotlightController
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.BrainTheme
import com.brain.gallery.ui.theme.Motion
import com.brain.gallery.ui.theme.Text2
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var spotlight: SpotlightController

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
        setContent { BrainTheme { Root(spotlight) } }
    }

    override fun onPause() { super.onPause(); spotlight.player.pause() }
    override fun onDestroy() { super.onDestroy(); spotlight.player.release() }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun Root(spotlight: SpotlightController) {
    var tab by remember { mutableIntStateOf(1) }
    var prev by remember { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val open by spotlight.current.collectAsState()
    val similar by spotlight.similar.collectAsState()
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

    // The reel is immersive: no bottom bar stealing the scrubber, like a
    // full-screen short-video player.
    val chromeVisible by animateFloatAsState(
        targetValue = if (open != null) 0f else 1f,
        animationSpec = tween<Float>(Motion.fastMs), label = "chrome"
    )
    val density = LocalDensity.current
    val navHeight = with(density) { 80.dp.toPx() }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            Box(Modifier.graphicsLayer { alpha = chromeVisible; translationY = navHeight * (1f - chromeVisible) }) {
                NavigationBar(
                    containerColor = if (tab == 0) Color(0xB310151F) else Color(0xFF10151F),
                    tonalElevation = 0.dp) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { go(i) },
                            icon = { Icon(t.icon, null, modifier = Modifier.size(22.dp)) },
                            label = {
                                Text(t.label, style = MaterialTheme.typography.labelMedium)
                            },
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
        }
    ) { _ ->
        val forward = tab >= prev
        // Feed is edge-to-edge but must clear the system gesture bar.
        val systemBottom = WindowInsets.navigationBars
        Box(Modifier.fillMaxSize().background(Bg)) {
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
                },
                label = "tab"
            ) { t ->
                when (t) {
                    0 -> Box(Modifier.fillMaxSize().windowInsetsPadding(systemBottom)) {
                        FeedScreen(
                            player = spotlight.player,
                            spotlight = spotlight,
                            onLeaveFeed = { go(1) },
                            bottomOverlay = 80.dp
                        )
                    }
                    1 -> Box(Modifier.fillMaxSize()) { OrganizeScreen(spotlight) }
                    else -> Box(Modifier.fillMaxSize()) { SearchScreen(spotlight) }
                }
            }
        }
    }

    // One spotlight for the whole app. Back closes it before anything else.
    BackHandler(enabled = open != null) { spotlight.close() }
    open?.let { v ->
        SpotlightPlayer(
            video = v,
            similar = similar,
            manager = spotlight.player,
            onClose = { spotlight.close() },
            onPick = { spotlight.pick(it) },
            onFav = { },
            hasPrev = spotlight.hasPrev(),
            hasNext = spotlight.hasNext(),
            onPrev = { spotlight.prev() },
            onNext = { spotlight.next() },
            onMoreLikeThis = { spotlight.open(it) },
            onDelete = { },
            onNotInterested = { }
        )
    }
}
