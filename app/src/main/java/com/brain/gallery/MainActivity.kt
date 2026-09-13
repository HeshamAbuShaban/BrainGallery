package com.brain.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.ui.feed.FeedScreen
import com.brain.gallery.ui.organize.OrganizeScreen
import com.brain.gallery.ui.organize.SearchScreen
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.BrainTheme
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

@Composable
private fun Root(player: PlayerManager) {
    var tab by remember { mutableIntStateOf(1) } // land on Organize: the product
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF10151F)) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent, selectedTextColor = Accent,
                    unselectedIconColor = Text2, unselectedTextColor = Text2,
                    indicatorColor = Color(0x228B5CF6))
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("For You") },
                    colors = colors)
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.ViewModule, null) }, label = { Text("Organize") },
                    colors = colors)
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") },
                    colors = colors)
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().background(Bg).then(if (tab == 0) Modifier else Modifier.padding(pad))) {
            when (tab) {
                0 -> FeedScreen(player)
                1 -> OrganizeScreen()
                else -> SearchScreen()
            }
        }
    }
}
