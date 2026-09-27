package com.brain.gallery.ui.spotlight

import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.engine.SimilarFinder
import com.brain.gallery.ui.player.PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One spotlight, available from every surface. "Find more similar" is a
 * first-class action everywhere because it is always the same operation:
 * take this clip as a seed and walk the graph out from it.
 */
@Singleton
class SpotlightController @Inject constructor(
    private val db: BrainDatabase,
    private val finder: SimilarFinder,
    val player: PlayerManager
) {
    private val _current = MutableStateFlow<VideoEntity?>(null)
    val current: StateFlow<VideoEntity?> = _current

    private val _similar = MutableStateFlow<List<VideoEntity>>(emptyList())
    val similar: StateFlow<List<VideoEntity>> = _similar

    /** When opened from a group, the whole group becomes a playlist. */
    private val _playlist = MutableStateFlow<List<VideoEntity>>(emptyList())
    val playlist: StateFlow<List<VideoEntity>> = _playlist

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    val isOpen: Boolean get() = _current.value != null

    /** Open a single clip and grow similarity from the whole library. */
    fun open(video: VideoEntity) {
        _playlist.value = emptyList()
        _index.value = 0
        _current.value = video
        _similar.value = finder.find(video, emptyList())
    }

    /** Open a group as a playable playlist, starting at [startIndex]. */
    fun openPlaylist(videos: List<VideoEntity>, startIndex: Int = 0) {
        if (videos.isEmpty()) return
        val i = startIndex.coerceIn(0, videos.lastIndex)
        _playlist.value = videos
        _index.value = i
        _current.value = videos[i]
        _similar.value = finder.find(videos[i], videos)
    }

    fun pick(video: VideoEntity) {
        val pl = _playlist.value
        val at = pl.indexOfFirst { it.id == video.id }
        if (at >= 0) {
            _index.value = at
            _current.value = video
        } else {
            _playlist.value = emptyList()
            _index.value = 0
            _current.value = video
        }
        _similar.value = finder.find(video, if (_playlist.value.isEmpty()) emptyList() else _playlist.value)
    }

    fun next() {
        val pl = _playlist.value
        if (pl.isEmpty()) return
        val i = (_index.value + 1).coerceAtMost(pl.lastIndex)
        _index.value = i
        _current.value = pl[i]
        _similar.value = finder.find(pl[i], pl)
    }

    fun prev() {
        val pl = _playlist.value
        if (pl.isEmpty()) return
        val i = (_index.value - 1).coerceAtLeast(0)
        _index.value = i
        _current.value = pl[i]
        _similar.value = finder.find(pl[i], pl)
    }

    fun hasNext(): Boolean = _index.value < _playlist.value.lastIndex
    fun hasPrev(): Boolean = _index.value > 0

    fun close() {
        player.pause()
        _current.value = null
        _similar.value = emptyList()
        _playlist.value = emptyList()
        _index.value = 0
    }
}
