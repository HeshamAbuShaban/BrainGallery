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
import com.brain.gallery.engine.MemoryDocBuilder
import com.brain.gallery.engine.SimilarFinder
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
    // Spotlight (watch from anywhere) + siblings.
    private val _spotlight = MutableStateFlow<VideoEntity?>(null)
    val spotlight: StateFlow<VideoEntity?> = _spotlight
    private val _similar = MutableStateFlow<List<VideoEntity>>(emptyList())
    val similar: StateFlow<List<VideoEntity>> = _similar
    private var allVideos: List<VideoEntity> = emptyList()

    init {
        viewModelScope.launch {
            db.videoDao().observeAll().collect { all ->
                allVideos = all
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
    fun mergePersons(fromId: Int, toId: Int) {
        if (fromId == toId) return
        viewModelScope.launch(Dispatchers.IO) { db.videoDao().mergePersons(fromId, toId) }
    }
    fun play(v: VideoEntity) {
        _spotlight.value = v
        _similar.value = SimilarFinder.find(v, allVideos)
    }
    fun closeSpotlight() { _spotlight.value = null; _similar.value = emptyList() }
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
class SearchViewModel @Inject constructor(
    private val db: BrainDatabase,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    private val _q = MutableStateFlow("")
    val query: StateFlow<String> = _q
    private val _results = MutableStateFlow<List<VideoEntity>>(emptyList())
    val results: StateFlow<List<VideoEntity>> = _results
    private val _spotlight = MutableStateFlow<VideoEntity?>(null)
    val spotlight: StateFlow<VideoEntity?> = _spotlight
    private val _similar = MutableStateFlow<List<VideoEntity>>(emptyList())
    val similar: StateFlow<List<VideoEntity>> = _similar

    init {
        viewModelScope.launch {
            _q.debounce(300).collect { q ->
                if (q.length < 2) { _results.value = emptyList(); return@collect }
                val lower = q.trim().lowercase()
                // People-key routing: "who" questions bypass keywords.
                if (lower in setOf("people", "person", "faces", "face", "smiles", "smiling",
                        "selfie", "selfies", "person a", "person b", "person c")) {
                    _results.value = db.videoDao().people()
                    return@collect
                }
                val like = db.videoDao().search(q)
                val likeIds = like.map { it.id }.toSet()
                // Doc-aware ranking: LIKE candidates + full-library doc match for
                // who/vibe/year tokens the filename never contains.
                val pool = if (like.size < 20) db.videoDao().getAllSync() else like
                _results.value = pool.map { v ->
                    v to MemoryDocBuilder.matchScore(MemoryDocBuilder.build(v).text, q)
                }
                    .filter { it.second > 0f || it.first.id in likeIds }
                    .sortedWith(compareByDescending<Pair<VideoEntity, Float>> { it.second }
                        .thenByDescending { it.first.watchCount })
                    .take(60)
                    .map { it.first }
            }
        }
    }

    fun query(s: String) { _q.value = s }
    fun toggleFav(v: VideoEntity) {
        viewModelScope.launch { db.videoDao().setFavorite(v.id, !v.isFavorite) }
    }
    fun play(v: VideoEntity) {
        _spotlight.value = v
        _similar.value = SimilarFinder.find(v, _results.value)
    }
    fun closeSpotlight() { _spotlight.value = null; _similar.value = emptyList() }

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
