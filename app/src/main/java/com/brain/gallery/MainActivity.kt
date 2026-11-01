package com.brain.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.ui.Routes
import com.brain.gallery.ui.components.SpotlightPlayer
import com.brain.gallery.ui.feed.FeedScreen
import com.brain.gallery.ui.organize.BrainScreen
import com.brain.gallery.ui.organize.BrainDiagnosticsScreen
import com.brain.gallery.ui.organize.BrainLearningScreen
import com.brain.gallery.ui.organize.BrainStorageScreen
import com.brain.gallery.ui.organize.GroupDetailRoute
import com.brain.gallery.ui.organize.OrganizeScreen
import com.brain.gallery.ui.organize.OrganizeViewModel
import com.brain.gallery.ui.organize.SearchScreen
import com.brain.gallery.ui.glass.GlassNav
import com.brain.gallery.ui.glass.GlassTab
import com.brain.gallery.ui.glass.glassReserve
import com.brain.gallery.ui.spotlight.SpotlightController
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.BrainTheme
import com.brain.gallery.ui.theme.Motion
import com.brain.gallery.ui.tune.TuneScreen
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
                add(Manifest.permission.READ_MEDIA_IMAGES)
                // 34+: the user may grant only partial library access; asking
                // for both lets the system offer that choice honestly.
                if (Build.VERSION.SDK_INT >= 34) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
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

@Composable
private fun Root(spotlight: SpotlightController) {
    // OrganizeViewModel is the library session: hub, group detail and the
    // brain screen share one instance, so an edit made in one is visible the
    // moment back is pressed in another. Feed/Search/Tune scope themselves
    // to their destinations (Phase 1b) — their state dies with the tab,
    // search query excepted, which rides SavedStateHandle.
    val orgVm: OrganizeViewModel = hiltViewModel()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val haptics = LocalHapticFeedback.current

    val open by spotlight.current.collectAsState()
    val similar by spotlight.similar.collectAsState()
    var reelChrome by remember { mutableStateOf(true) }

    val tabs = listOf(
        GlassTab("For You", Icons.Default.PlayArrow),
        GlassTab("Organize", Icons.Default.GridView),
        GlassTab("Search", Icons.Default.Search),
        GlassTab("Tune", Icons.Default.Tune)
    )

    // Tab switches keep each tab's stack and state alive: pop to the start
    // with saveState, restore on the way back. Stacked destinations (group,
    // brain) ride on top of their tab's stack.
    fun goTab(i: Int) {
        if (i == Routes.tabIndex(route)) return
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        navController.navigate(
            when (i) { 0 -> Routes.FEED; 2 -> Routes.SEARCH; 3 -> Routes.TUNE; else -> Routes.ORGANIZE }
        ) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The pill is always reachable except in two situations: a spotlight is
    // covering the screen, or a reel is playing with its own chrome hidden.
    // Tapping the reel brings its chrome back, which brings this back too.
    val navVisible = when {
        open != null -> 0f
        route == Routes.FEED && !reelChrome -> 0f
        else -> 1f
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        NavHost(
            navController = navController,
            // The opening beat is the reel: the app's whole bet is that your
            // memories surface before you finish wanting them, so the first
            // thing on screen is them playing — not a card wall.
            startDestination = Routes.FEED,
            enterTransition = {
                val fwd = Routes.rank(targetState.destination.route) >= Routes.rank(initialState.destination.route)
                slideInHorizontally(tween(Motion.mediumMs, easing = Motion.emphasized)) {
                    it / 6 * if (fwd) 1 else -1
                } + fadeIn(tween(Motion.mediumMs))
            },
            exitTransition = { fadeOut(tween(Motion.fastMs)) },
            popEnterTransition = {
                slideInHorizontally(tween(Motion.mediumMs, easing = Motion.emphasized)) { -it / 6 } +
                        fadeIn(tween(Motion.mediumMs))
            },
            popExitTransition = {
                slideOutHorizontally(tween(Motion.mediumMs, easing = Motion.emphasized)) { -it / 6 } +
                        fadeOut(tween(Motion.fastMs))
            }
        ) {
            composable(Routes.FEED) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                    FeedScreen(
                        player = spotlight.player,
                        spotlight = spotlight,
                        onLeaveFeed = { goTab(1) },
                        onChrome = { reelChrome = it },
                        bottomOverlay = glassReserve()
                    )
                }
            }
            composable(Routes.ORGANIZE) {
                OrganizeScreen(
                    spotlight = spotlight,
                    vm = orgVm,
                    onOpenGroup = { navController.navigate(Routes.group(it)) },
                    onOpenBrain = { navController.navigate(Routes.BRAIN) }
                )
            }
            composable(Routes.GROUP) { entry ->
                GroupDetailRoute(
                    groupId = entry.arguments?.getString("groupId").orEmpty(),
                    spotlight = spotlight,
                    vm = orgVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(spotlight = spotlight)
            }
            composable(Routes.TUNE) {
                TuneScreen(onOpenBrain = { navController.navigate(Routes.BRAIN) })
            }
            composable(Routes.BRAIN) {
                BrainScreen(
                    vm = orgVm,
                    onBack = { navController.popBackStack() },
                    onOpenLearning = { navController.navigate(Routes.BRAIN_LEARNING) },
                    onOpenDiagnostics = { navController.navigate(Routes.BRAIN_DIAGNOSTICS) },
                    onOpenStorage = { navController.navigate(Routes.BRAIN_STORAGE) }
                )
            }
            composable(Routes.BRAIN_LEARNING) {
                BrainLearningScreen(vm = orgVm, onBack = { navController.popBackStack() })
            }
            composable(Routes.BRAIN_DIAGNOSTICS) {
                BrainDiagnosticsScreen(vm = orgVm, onBack = { navController.popBackStack() })
            }
            composable(Routes.BRAIN_STORAGE) {
                BrainStorageScreen(vm = orgVm, onBack = { navController.popBackStack() })
            }
        }

        GlassNav(
            tabs = tabs,
            selected = Routes.tabIndex(route),
            onSelect = { goTab(it) },
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
            onFav = { orgVm.toggleFav(it) },
            hasPrev = spotlight.hasPrev(),
            hasNext = spotlight.hasNext(),
            onPrev = { spotlight.prev() },
            onNext = { spotlight.next() },
            onMoreLikeThis = { spotlight.open(it, similar) },
            onDelete = { orgVm.requestDelete(listOf(it.id)) },
            onNotInterested = { orgVm.markNotInterested(it.id) }
        )
    }
}
