package com.brain.gallery.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.domain.engine.FeedComposer
import com.brain.gallery.domain.engine.FeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val composer: FeedComposer,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        BrainScanService.start(ctx)
        viewModelScope.launch {
            db.videoDao().observeAll().collect { videos ->
                val events = db.watchDao().recent()
                _feed.value = composer.compose(videos, events)
                _loading.value = false
            }
        }
    }

    fun refresh() { BrainScanService.start(ctx) }

    fun onWatched(videoId: Long, completion: Float, skipped: Boolean) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            db.watchDao().insert(WatchEventEntity(videoId = videoId, atMs = System.currentTimeMillis(),
                completion = completion, skipped = skipped, hourOfDay = cal.get(Calendar.HOUR_OF_DAY)))
            val v = db.videoDao().getById(videoId) ?: return@launch
            db.videoDao().upsert(v.copy(
                lastWatchedMs = System.currentTimeMillis(), watchCount = v.watchCount + 1,
                completionSum = v.completionSum + completion,
                skipCount = v.skipCount + if (skipped) 1 else 0))
        }
    }

    fun toggleFav(id: Long, fav: Boolean) {
        viewModelScope.launch { db.videoDao().setFavorite(id, fav) }
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
