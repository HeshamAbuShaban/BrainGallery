package com.brain.gallery.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(@ApplicationContext private val ctx: Context) {
    private var player: ExoPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private var currentUri: String? = null

    @OptIn(UnstableApi::class)
    fun playerFor(uri: String): ExoPlayer {
        val p = player ?: ExoPlayer.Builder(ctx).build().also {
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.volume = 1f
            it.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(v: Boolean) { _isPlaying.value = v }
            })
            player = it
        }
        if (currentUri != uri) {
            currentUri = uri
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
        }
        return p
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun toggle() { player?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun release() { player?.release(); player = null; currentUri = null }
}
