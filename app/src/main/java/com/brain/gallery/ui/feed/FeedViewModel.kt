package com.brain.gallery.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.data.work.IndexWorker
import com.brain.gallery.domain.engine.FeedComposer
import com.brain.gallery.domain.engine.FeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val composer: FeedComposer,
    private val wm: WorkManager
) : ViewModel() {
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        wm.enqueue(OneTimeWorkRequestBuilder<IndexWorker>().build())
        viewModelScope.launch {
            db.videoDao().observeAll().collect { videos ->
                val events = db.watchDao().recent()
                _feed.value = composer.compose(videos, events)
                _loading.value = false
            }
        }
    }

    fun refresh() { wm.enqueue(OneTimeWorkRequestBuilder<IndexWorker>().build()) }

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
}
