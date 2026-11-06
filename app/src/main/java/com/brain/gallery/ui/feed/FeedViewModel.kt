package com.brain.gallery.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.NotInterestedEntity
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.data.brain.AttentionPolicy
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.domain.SettingsStore
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.TasteLearner
import com.brain.gallery.domain.TasteStore
import com.brain.gallery.domain.engine.FeedComposer
import com.brain.gallery.domain.engine.FeedItem
import com.brain.gallery.domain.engine.TasteProfile
import com.brain.gallery.engine.ClusterMath
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val composer: FeedComposer,
    private val settings: SettingsStore,
    private val tasteStore: TasteStore,
    private val tasteLearner: TasteLearner,
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
    /** The learned taste profile, refreshed whenever a composition starts. */
    private val _taste = MutableStateFlow(TasteProfile())

    // Clips this session has already served. The reel walks the library:
    // a reshuffle serves what is left, and wraps only when the unseen pool
    // runs thin. Session-scoped on purpose — a fresh app run may revisit.
    private val servedIds = LinkedHashSet<Long>()

    /** Session-scoped playback speed: what you set on clip 1 is what clip 40 plays at. */
    var playbackSpeed by mutableStateOf(1f)
        private set
    fun cycleSpeed() {
        playbackSpeed = SPEEDS[(SPEEDS.indexOf(playbackSpeed) + 1) % SPEEDS.size]
    }

    /**
     * The page the viewer deliberately paused, null while playing. Cleared
     * the moment a different page becomes active — switching clips is an
     * un-pause — but honored when you come back to the very clip you froze.
     */
    var pausedPage by mutableStateOf<Int?>(null)
        private set
    fun togglePause(page: Int) {
        pausedPage = if (pausedPage == page) null else page
    }
    fun onActivePage(page: Int) {
        val held = pausedPage
        if (held != null && held != page) pausedPage = null
    }

    /** Set by a reshuffle at the end of the reel: the next batch lands async,
     *  and the choosing gate covers the wait instead of flashing old page 0. */
    private val _jumpToStart = MutableStateFlow(false)
    val jumpToStart: StateFlow<Boolean> = _jumpToStart
    fun consumeJump() { _jumpToStart.value = false }

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
                    _loading.value = true

                    // Scoring a whole library is real work — keep it off the
                    // main thread so the seam between batches never janks.
                    val composed = withContext(Dispatchers.Default) {
                        val events = db.watchDao().recent()
                        // This composition reads the freshest profile; a watch
                        // under it re-ranks the NEXT composition, never this one.
                        _taste.value = runCatching { tasteStore.load() }
                            .getOrDefault(TasteProfile())
                        composer.compose(
                            videos, events, suppressed,
                            tuning = cfg.tuning, exclude = servedIds.toSet(),
                            taste = _taste.value)
                    }
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

    fun reshuffle() {
        // The end-of-reel seam: the choosing gate covers the async compose,
        // and the next batch opens on its first clip, not a replay of page 0.
        _loading.value = true
        _jumpToStart.value = true
        _nonce.value += 1
    }

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
            // Tier 3: attention. An honest watch says "understand this one
            // better" — the deep pass bends toward what the viewer actually goes to.
            if (!skipped) db.videoDao().bumpAttention(
                videoId, AttentionPolicy.WATCH_BUMP, AttentionPolicy.CAP)
            // The taste notebook folds events as they happen (debounced so a
            // scrub session is one lesson, not ten). Exactly-once: the learner
            // works from the same cursor the end-of-run pass uses.
            tasteLearnJob?.cancel()
            tasteLearnJob = viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(600)
                runCatching { tasteLearner.learnNewEvents() }
            }
        }
    }

    private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    private var tasteLearnJob: kotlinx.coroutines.Job? = null

    fun toggleFav(id: Long, fav: Boolean) {
        viewModelScope.launch {
            db.videoDao().setFavorite(id, fav)
            // A considered favorite is worth more attention than an itch.
            if (fav) db.videoDao().bumpAttention(id, AttentionPolicy.FAV_BUMP, AttentionPolicy.CAP)
        }
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
