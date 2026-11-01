package com.brain.gallery.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.NotInterestedEntity
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.domain.SettingsStore
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.engine.FeedComposer
import com.brain.gallery.domain.engine.FeedItem
import com.brain.gallery.engine.ClusterMath
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val composer: FeedComposer,
    private val settings: SettingsStore,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed
    private val _showWhy = MutableStateFlow(true)
    val showWhy: StateFlow<Boolean> = _showWhy
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    /** Bumped when the feed runs dry so the composer can reshuffle a fresh batch. */
    private val _nonce = MutableStateFlow(0)
    val nonce: StateFlow<Int> = _nonce

    // Clips this session has already served. The reel walks the library:
    // a reshuffle serves what is left, and wraps only when the unseen pool
    // runs thin. Session-scoped on purpose — a fresh app run may revisit.
    private val servedIds = LinkedHashSet<Long>()

    init {
        BrainScanService.start(ctx)
        viewModelScope.launch {
            var lastSig: String? = null
            combine(
                db.videoDao().observeAll().conflate(),
                db.supportDao().observeNotInterested().conflate(),
                settings.observe().conflate(),
                _nonce
            ) { videos, notInterested, cfg, n -> listOf(videos, notInterested, cfg) to n }
                .collect { (payload, nonce) ->
                    @Suppress("UNCHECKED_CAST")
                    val videos = payload[0] as List<com.brain.gallery.data.local.VideoEntity>
                    @Suppress("UNCHECKED_CAST")
                    val notInterested = payload[1] as List<Long>
                    val cfg = payload[2] as EngineSettings
                    _showWhy.value = cfg.showWhy
                    val suppressed = suppressionSet(videos, notInterested)

                    // The signature covers only what legitimately reshapes a
                    // running reel: membership (clips added or removed),
                    // suppression, the taught weights, and an explicit
                    // reshuffle. Watch events write scores for the NEXT
                    // composition — the reel under the viewer's thumb is
                    // never torn down mid-watch. Its entity snapshots still
                    // refresh in place, so the heart and metadata stay true.
                    val sig = "${videos.size}:${videos.sumOf { it.id }}:${videos.sumOf { it.dateAddedSec }}" +
                        "|${suppressed.size}|${cfg.tuning.hashCode()}|${cfg.mood}|$nonce"
                    if (sig == lastSig) {
                        refreshInPlace(videos)
                        return@collect
                    }
                    lastSig = sig

                    val events = db.watchDao().recent()
                    val composed = composer.compose(
                        videos, events, suppressed,
                        tuning = cfg.tuning, exclude = servedIds.toSet())
                    servedIds.addAll(composed.map { it.video.id })
                    _feed.value = composed
                    _loading.value = false
                }
        }
    }

    /** Same order, honest snapshots: drops clips that left the library. */
    private fun refreshInPlace(videos: List<com.brain.gallery.data.local.VideoEntity>) {
        val byId = videos.associateBy { it.id }
        _feed.value = _feed.value.mapNotNull { fi ->
            byId[fi.video.id]?.let { fi.copy(video = it) }
        }
    }

    /**
     * "Not interested" is sticky and contagious: suppressing one clip also
     * suppresses its near-duplicates, so the same footage stops resurfacing.
     */
    private suspend fun suppressionSet(
        videos: List<com.brain.gallery.data.local.VideoEntity>,
        notInterested: List<Long>
    ): Set<Long> {
        if (notInterested.isEmpty()) return emptySet()
        val seeds = videos.filter { it.id in notInterested }
        if (seeds.isEmpty()) return notInterested.toSet()
        val seedHashes = seeds.mapNotNull { if (it.phash != 0L) it.phash else null }
        val out = HashSet(notInterested)
        if (seedHashes.isNotEmpty()) {
            for (v in videos) {
                if (v.id in out || v.phash == 0L) continue
                if (seedHashes.any { ClusterMath.hamming(it, v.phash) <= 6 }) out += v.id
            }
        }
        return out
    }

    fun reshuffle() { _nonce.value += 1 }

    fun refresh() { BrainScanService.start(ctx) }

    fun onWatched(videoId: Long, completion: Float, skipped: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            db.watchDao().insert(WatchEventEntity(videoId = videoId, atMs = System.currentTimeMillis(),
                completion = completion, skipped = skipped, hourOfDay = cal.get(Calendar.HOUR_OF_DAY)))
            db.videoDao().recordWatch(
                id = videoId, now = System.currentTimeMillis(),
                completion = completion, skipped = if (skipped) 1 else 0
            )
        }
    }

    fun toggleFav(id: Long, fav: Boolean) {
        viewModelScope.launch { db.videoDao().setFavorite(id, fav) }
    }

    /** Real, persistent dismissal: hides this clip and its duplicates for good. */
    fun markNotInterested(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.supportDao().addNotInterested(NotInterestedEntity(id, System.currentTimeMillis()))
        }
    }

    // Delete-with-consent (same pattern as Organize).
    private val _deleteAsk = MutableStateFlow<android.content.IntentSender?>(null)
    val deleteAsk: StateFlow<android.content.IntentSender?> = _deleteAsk
    private var pendingIds: List<Long> = emptyList()

    fun requestDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            pendingIds = ids
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val uris = ids.mapNotNull { id ->
                    db.videoDao().getById(id)?.uri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                }
                if (uris.isEmpty()) return@launch
                _deleteAsk.value = android.provider.MediaStore.createDeleteRequest(
                    ctx.contentResolver, uris).intentSender
            } else {
                ids.forEach { id ->
                    db.videoDao().getById(id)?.let { v ->
                        runCatching { ctx.contentResolver.delete(android.net.Uri.parse(v.uri), null, null) }
                    }
                }
                db.videoDao().deleteByIds(ids)
            }
        }
    }

    fun consumeDeleteAsk() { _deleteAsk.value = null }

    fun confirmDelete() {
        val ids = pendingIds
        pendingIds = emptyList()
        viewModelScope.launch(Dispatchers.IO) { db.videoDao().deleteByIds(ids) }
    }
}
