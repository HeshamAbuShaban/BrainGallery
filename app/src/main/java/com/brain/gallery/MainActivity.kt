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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.brain.gallery.ui.organize.OrganizeViewModel
import com.brain.gallery.ui.glass.GlassNav
import com.brain.gallery.ui.glass.glassReserve
import com.brain.gallery.ui.glass.GlassTab
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

private typealias Tab = GlassTab

@Composable
private fun Root(spotlight: SpotlightController) {
    val vm: OrganizeViewModel = hiltViewModel()
    var tab by remember { mutableIntStateOf(1) }
    var prev by remember { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val open by spotlight.current.collectAsState()
    var reelChrome by remember { mutableStateOf(true) }
    val similar by spotlight.similar.collectAsState()
    val tabs = listOf(
        GlassTab("For You", Icons.Default.PlayArrow),
        GlassTab("Organize", Icons.Default.GridView),
        GlassTab("Search", Icons.Default.Search)
    )

    fun go(i: Int) {
        if (i == tab) return
        prev = tab
        tab = i
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // The pill is always reachable except in two situations: a spotlight is
    // covering the screen, or a reel is playing with its own chrome hidden.
    // Tapping the reel brings its chrome back, which brings this back too.
    val navVisible = when {
        open != null -> 0f
        tab == 0 && !reelChrome -> 0f
        else -> 1f
    }

    // No Scaffold bottom bar any more: the nav floats over the content so the
    // reel gets the whole screen and the library keeps its bottom row.
    val forward = tab >= prev
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
                        onChrome = { reelChrome = it },
                        bottomOverlay = glassReserve()
                    )
                }
                1 -> Box(Modifier.fillMaxSize()) { OrganizeScreen(spotlight) }
                else -> Box(Modifier.fillMaxSize()) { SearchScreen(spotlight) }
            }
        }

        GlassNav(
            tabs = tabs,
            selected = tab,
            onSelect = { go(it) },
            visible = navVisible
        )
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
            onFav = { vm.toggleFav(it) },
            hasPrev = spotlight.hasPrev(),
            hasNext = spotlight.hasNext(),
            onPrev = { spotlight.prev() },
            onNext = { spotlight.next() },
            onMoreLikeThis = { spotlight.open(it, similar) },
            onDelete = { vm.requestDelete(listOf(it.id)) },
            onNotInterested = { vm.markNotInterested(it.id) }
        )
    }
}
