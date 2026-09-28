package com.brain.gallery.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One ExoPlayer for the whole app, with a single owner at a time.
 *
 * The player is shared so audio never overlaps, but a shared player also means
 * whoever acquires it last decides what is playing. Pages must therefore claim it
 * explicitly: a reel page that is off-screen or covered by the spotlight must
 * release it, so when the spotlight closes the reel re-acquires its own clip
 * instead of continuing to play whatever the spotlight left behind.
 */
@Singleton
class PlayerManager @Inject constructor(@ApplicationContext private val ctx: Context) {
    private var player: ExoPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private var currentUri: String? = null
    private var owner: String? = null

    /** True when [ownerId] is the surface currently driving playback. */
    fun isOwner(ownerId: String): Boolean = owner == ownerId

    /**
     * Claim the player for [ownerId] and point it at [uri]. Safe to call on every
     * recomposition: it only touches the media item when the URI actually changed.
     */
    @OptIn(UnstableApi::class)
    fun acquire(ownerId: String, uri: String): ExoPlayer {
        val p = player ?: ExoPlayer.Builder(ctx).build().also {
            it.repeatMode = Player.REPEAT_MODE_OFF
            it.volume = 1f
            it.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(v: Boolean) { _isPlaying.value = v }
            })
            player = it
        }
        owner = ownerId
        if (currentUri != uri) {
            currentUri = uri
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
        }
        return p
    }

    /** Give the player up, but only if [ownerId] is the one holding it. */
    fun release(ownerId: String) {
        if (owner == ownerId) {
            player?.pause()
            owner = null
        }
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun toggle() { player?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun seekTo(ms: Long) { player?.seekTo(ms) }
    fun durationMs(): Long = player?.duration?.coerceAtLeast(0L) ?: 0L
    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }
    fun speed(): Float = player?.playbackParameters?.speed ?: 1f
    fun setLoopMode(mode: Int) { player?.repeatMode = mode }

    /** Fraction 0..1 of current item watched (0 if unknown). */
    fun completion(): Float {
        val p = player ?: return 0f
        val d = p.duration
        if (d <= 0) return 0f
        return (p.currentPosition.toFloat() / d).coerceIn(0f, 1f)
    }

    fun release() { player?.release(); player = null; currentUri = null; owner = null }
}
