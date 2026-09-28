package com.brain.gallery.ui.spotlight

import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.engine.SimilarFinder
import com.brain.gallery.ui.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Whole-library pool for similarity. "Find more similar" must never come
     * back empty, so this is always the fallback and the context pool is only
     * used before the library has loaded once.
     */
    @Volatile
    private var library: List<VideoEntity> = emptyList()

    private var libraryLoadedAt = 0L

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

    /**
     * Open a single clip. [pool] is the surface it was opened from (group,
     * search results, grid); it only stands in until the library loads, because
     * the useful answer to "more like this" reaches outside the source group.
     */
    fun open(video: VideoEntity, pool: List<VideoEntity>? = null) {
        _playlist.value = emptyList()
        _index.value = 0
        _current.value = video
        _similar.value = finder.find(video, resolvePool(pool))
        if (pool != null) contextPool = pool
        warmLibrary()
    }

    /** Open a group as a playable playlist, starting at [startIndex]. */
    fun openPlaylist(videos: List<VideoEntity>, startIndex: Int = 0) {
        if (videos.isEmpty()) return
        val i = startIndex.coerceIn(0, videos.lastIndex)
        _playlist.value = videos
        _index.value = i
        _current.value = videos[i]
        _similar.value = finder.find(videos[i], resolvePool(videos))
        contextPool = videos
        warmLibrary()
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
        _similar.value = finder.find(video, resolvePool(null))
    }

    fun next() {
        val pl = _playlist.value
        if (pl.isEmpty()) return
        val i = (_index.value + 1).coerceAtMost(pl.lastIndex)
        _index.value = i
        _current.value = pl[i]
        _similar.value = finder.find(pl[i], resolvePool(null))
    }

    fun prev() {
        val pl = _playlist.value
        if (pl.isEmpty()) return
        val i = (_index.value - 1).coerceAtLeast(0)
        _index.value = i
        _current.value = pl[i]
        _similar.value = finder.find(pl[i], resolvePool(null))
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

    /**
     * The pool similarity ranks against: the whole library once it is warm,
     * otherwise whatever surface the clip was opened from.
     */
    private var contextPool: List<VideoEntity> = emptyList()

    private fun resolvePool(fallback: List<VideoEntity>?): List<VideoEntity> =
        library.ifEmpty { fallback ?: contextPool }

    /**
     * Load the library pool once and refresh the rail against it. The first
     * open still returns immediately from whatever pool is already in hand, so
     * the player never waits on this.
     */
    private fun warmLibrary() {
        val stale = library.isEmpty() || System.currentTimeMillis() - libraryLoadedAt > 120_000
        if (!stale) return
        val target = _current.value ?: return
        scope.launch {
            runCatching { db.videoDao().getAllSync() }.onSuccess { all ->
                if (all.isEmpty()) return@onSuccess
                library = all
                libraryLoadedAt = System.currentTimeMillis()
                if (_current.value?.id == target.id) {
                    _similar.value = finder.find(_current.value!!, all)
                }
            }
        }
    }
}
