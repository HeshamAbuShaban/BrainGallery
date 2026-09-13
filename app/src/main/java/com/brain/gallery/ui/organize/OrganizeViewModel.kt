package com.brain.gallery.ui.organize

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.domain.organize.GroupBuilder
import com.brain.gallery.domain.organize.SmartGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryStats(
    val total: Int = 0, val memories: Int = 0, val clutter: Int = 0,
    val understoodPct: Int = 0, val pending: Int = 0
)

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val groups: GroupBuilder,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    private val _groups = MutableStateFlow<List<SmartGroup>>(emptyList())
    val groupList: StateFlow<List<SmartGroup>> = _groups
    private val _stats = MutableStateFlow(LibraryStats())
    val stats: StateFlow<LibraryStats> = _stats
    private val _selected = MutableStateFlow<SmartGroup?>(null)
    val selected: StateFlow<SmartGroup?> = _selected
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        viewModelScope.launch {
            db.videoDao().observeAll().collect { all ->
                _groups.value = groups.build(all)
                val understood = all.count { it.brainLevel >= 1 }
                _stats.value = LibraryStats(
                    total = all.size,
                    memories = all.count { it.junkScore < 0.5f },
                    clutter = all.count { it.junkScore >= 0.5f },
                    understoodPct = if (all.isEmpty()) 0 else understood * 100 / all.size,
                    pending = all.size - understood
                )
                _loading.value = false
                // Keep selection fresh.
                _selected.value?.let { sel ->
                    _selected.value = _groups.value.firstOrNull { it.id == sel.id }
                }
            }
        }
    }

    fun open(g: SmartGroup) { _selected.value = g }
    fun close() { _selected.value = null }
    fun rescan() { BrainScanService.start(ctx) }
    fun toggleFav(v: VideoEntity) {
        viewModelScope.launch { db.videoDao().setFavorite(v.id, !v.isFavorite) }
    }

    // --- Delete redundant (system consent on API 30+) ---
    private val _deleteAsk = MutableStateFlow<IntentSender?>(null)
    val deleteAsk: StateFlow<IntentSender?> = _deleteAsk
    private var pendingIds: List<Long> = emptyList()

    fun requestDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            pendingIds = ids
            if (Build.VERSION.SDK_INT >= 30) {
                val uris = ids.mapNotNull { id ->
                    db.videoDao().getById(id)?.uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                }
                if (uris.isEmpty()) return@launch
                val req = MediaStore.createDeleteRequest(ctx.contentResolver, uris)
                _deleteAsk.value = req.intentSender
            } else {
                ids.forEach { id ->
                    db.videoDao().getById(id)?.let { v ->
                        runCatching { ctx.contentResolver.delete(Uri.parse(v.uri), null, null) }
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

    suspend fun freedBytes(ids: List<Long>): Long = withContext(Dispatchers.IO) {
        ids.sumOf { db.videoDao().getById(it)?.sizeBytes ?: 0L }
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val db: BrainDatabase) : ViewModel() {
    private val _q = MutableStateFlow("")
    val query: StateFlow<String> = _q
    private val _results = MutableStateFlow<List<VideoEntity>>(emptyList())
    val results: StateFlow<List<VideoEntity>> = _results

    init {
        viewModelScope.launch {
            _q.debounce(300).collect { q ->
                _results.value = if (q.length < 2) emptyList() else db.videoDao().search(q)
            }
        }
    }

    fun query(s: String) { _q.value = s }
}
