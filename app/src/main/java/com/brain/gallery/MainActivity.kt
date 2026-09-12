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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.brain.gallery.ui.feed.FeedScreen
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.BrainTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var player: PlayerManager

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permLauncher.launch(perms)
        }
        setContent { BrainTheme { Root(player) } }
    }

    override fun onPause() { super.onPause(); player.pause() }
    override fun onDestroy() { super.onDestroy(); player.release() }
}

@Composable
private fun Root(player: PlayerManager) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Bg) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("For You") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Browse") })
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().background(Bg).then(if (tab == 0) Modifier else Modifier.padding(pad))) {
            if (tab == 0) FeedScreen(player) else BrowseStub()
        }
    }
}

@Composable
private fun BrowseStub() {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Text("Browse: collections + search land here (Phase 2)", color = androidx.compose.ui.graphics.Color.White)
    }
}
