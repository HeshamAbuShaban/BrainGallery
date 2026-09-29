package com.brain.gallery.ui.organize

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.portability.MemoryBundle
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.domain.BatteryOptims
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.SettingsStore
import com.brain.gallery.domain.organize.GroupBuilder
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.engine.MemoryDocBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryStats(
    val total: Int = 0, val memories: Int = 0, val clutter: Int = 0,
    val understoodPct: Int = 0, val pending: Int = 0, val people: Int = 0
)

data class Diagnostics(
    val dbBytes: Long = 0, val persons: Int = 0, val vectors: Int = 0,
    val unassigned: Int = 0, val splitsFlagged: Int = 0,
    val lastL1a: Int = 0, val lastL1b: Int = 0, val lastMs: Long = 0,
    val tScan: Long = 0, val tL1a: Long = 0, val tL1b: Long = 0,
    val merged: Int = 0, val adopted: Int = 0, val unreadable: Int = 0, val stalled: Int = 0
)

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val groups: GroupBuilder,
    private val bundle: MemoryBundle,
    private val battery: BatteryOptims,
    private val store: SettingsStore,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    private val _groups = MutableStateFlow<List<SmartGroup>>(emptyList())
    val groupList: StateFlow<List<SmartGroup>> = _groups
    private val _stats = MutableStateFlow(LibraryStats())
    val stats: StateFlow<LibraryStats> = _stats
    private val _persons = MutableStateFlow<List<PersonEntity>>(emptyList())
    val persons: StateFlow<List<PersonEntity>> = _persons
    private val _selected = MutableStateFlow<SmartGroup?>(null)
    val selected: StateFlow<SmartGroup?> = _selected
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    private val _diag = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diag
    private val _batteryExempt = MutableStateFlow(false)
    val batteryExempt: StateFlow<Boolean> = _batteryExempt
    private val _settings = MutableStateFlow(EngineSettings())
    val settings: StateFlow<EngineSettings> = _settings
    private val _personCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val personCounts: StateFlow<Map<Int, Int>> = _personCounts
    private val _customNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val customNames: StateFlow<Map<String, String>> = _customNames
    private var allVideos: List<VideoEntity> = emptyList()

    init {
        viewModelScope.launch { _settings.value = store.load() }
        viewModelScope.launch {
            // conflate: collapse bursts of writes into one rebuild, and skip the
            // rebuild entirely when nothing structural actually changed.
            combine(
                db.videoDao().observeAll().conflate(),
                db.personDao().observePersons().conflate(),
                db.supportDao().observeGroupOverrides().conflate()
            ) { videos, people, over ->
                Triple(videos, people, over.associate { it.groupId to it.name })
            }
                .distinctUntilChanged { old, new ->
                    old.first.size == new.first.size &&
                        old.second.size == new.second.size &&
                        old.third == new.third &&
                        old.first.sumOf { it.watchCount } == new.first.sumOf { it.watchCount }
                }
                .collect { (videos, people, names) ->
                    val fingerprint = Triple(videos.hashCode(), people.hashCode(), names.hashCode())
                    if (fingerprint != lastFingerprint) {
                        lastFingerprint = fingerprint
                        allVideos = videos
                        _persons.value = people
                        _customNames.value = names
                        _personCounts.value = videos.filter { it.personId >= 0 }
                            .groupingBy { it.personId }.eachCount()
                        val cfg = _settings.value
                        val built = withContext(Dispatchers.Default) {
                            groups.build(
                                videos, people, cfg.duplicatesEnabled, cfg.junkSensitivity, names
                            )
                        }
                        _groups.value = built
                        _stats.value = withContext(Dispatchers.Default) {
                            LibraryStats(
                                total = videos.size,
                                memories = videos.count { it.junkScore < 0.5f },
                                clutter = videos.count { it.junkScore >= 0.5f },
                                understoodPct = if (videos.isEmpty()) 0
                                else videos.count { it.brainLevel >= 1 } * 100 / videos.size,
                                pending = videos.count { it.brainLevel < 1 },
                                people = people.size
                            )
                        }
                        _loading.value = false
                        _selected.value?.let { sel ->
                            _selected.value = built.firstOrNull { it.id == sel.id }
                        }
                    }
                }
        }
    }

    private var lastFingerprint: Triple<Int, Int, Int>? = null

    fun open(g: SmartGroup) { _selected.value = g }
    fun close() { _selected.value = null }

    // ---- Identity corrections ----
        fun renamePerson(personId: Int, name: String) {
        viewModelScope.launch { db.personDao().rename(personId, name) }
    }

    /**
     * Rename any group, whatever kind it is. Person groups also get the name
     * written to the person row so it travels with export/import; everything
     * else lands in the override table, which survives a reindex.
     */
    fun renameGroup(groupId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.supportDao().setGroupName(groupId, clean, System.currentTimeMillis())
            groupId.removePrefix("person_").toIntOrNull()?.let { pid ->
                db.personDao().rename(pid, clean)
            }
        }
    }

    /** Drop a user-chosen name and fall back to the engine's label. */
    fun resetGroupName(groupId: String) {
        viewModelScope.launch { db.supportDao().clearGroupName(groupId) }
    }

    fun isRenamed(groupId: String): Boolean = _customNames.value.containsKey(groupId)


    fun mergePersons(fromId: Int, toId: Int) {
        if (fromId == toId) return
        viewModelScope.launch(Dispatchers.IO) {
            db.personDao().mergePersons(fromId, toId)
            db.videoDao().mergePersons(fromId, toId)
            db.personDao().deleteEmptyPersons()
        }
    }
    /** Split: detach one video's vectors from a person so it can re-match elsewhere. */
    fun splitVideoOut(personId: Int, videoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            for (v in db.personDao().vectorsForVideo(videoId)) {
                if (v.personId == personId) db.personDao().assignVector(v.id, -1)
            }
            db.videoDao().setPerson(videoId, -1)
        }
    }
    fun dismissSplitWarning(personId: Int) {
        viewModelScope.launch { db.personDao().clearSplitSuggested(personId) }
    }

    /** "Hide this group": suppress every clip in it, now and for good. */
    fun hideGroup(g: com.brain.gallery.domain.organize.SmartGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            g.videos.forEach {
                db.supportDao().addNotInterested(
                    com.brain.gallery.data.local.NotInterestedEntity(it.id, System.currentTimeMillis())
                )
            }
        }
    }

    fun markNotInterested(videoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.supportDao().addNotInterested(
                com.brain.gallery.data.local.NotInterestedEntity(videoId, System.currentTimeMillis())
            )
        }
    }

    fun rescan() { BrainScanService.start(ctx) }

    // ---- hand-placed groups and multi-select ----

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    init {
        viewModelScope.launch {
            db.supportDao().observeSelection().collect { ids ->
                _selection.value = ids.toSet()
            }
        }
    }

    fun togglePick(videoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (videoId in _selection.value) db.supportDao().unpick(videoId)
            else db.supportDao().pick(
                com.brain.gallery.data.local.SelectionEntity(videoId, System.currentTimeMillis())
            )
        }
    }

    fun clearPicks() { viewModelScope.launch(Dispatchers.IO) { db.supportDao().clearSelection() } }

    /** Move the current selection into [group]; empty string hands them back. */
    fun moveSelectionToGroup(group: String) {
        val ids = _selection.value.toList()
        moveIdsToGroup(ids, group)
    }

    /** Move an explicit set of clips, then clear anything that was ticked. */
    fun moveIdsToGroup(ids: List<Long>, group: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.videoDao().setManualGroup(ids, group.trim())
            db.supportDao().clearSelection()
        }
    }

    /** Group names worth offering: the ones already in use, most-used first. */
    val groupChoices: List<String>
        get() = allVideos.map { it.effectiveGroup }
            .filter { it.isNotBlank() && it != "unknown" }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }

    /** Remember a name the user invented, so it shows up in the picker later. */
    fun rememberGroupName(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.supportDao().setGroupName("manual_$clean", clean, System.currentTimeMillis())
        }
    }

    /** Repair: send a wrongly-clustered video to the right person (or a new one). */
    fun moveVideoToPerson(videoId: Long, targetPersonId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val vectors = db.personDao().vectorsForVideo(videoId)
            if (vectors.isEmpty()) return@launch
            val target = if (targetPersonId >= 0) targetPersonId else {
                db.personDao().insertPerson(
                    com.brain.gallery.data.local.PersonEntity(createdAt = System.currentTimeMillis())
                ).toInt()
            }
            for (v in vectors) db.personDao().assignVector(v.id, target)
            db.videoDao().setPerson(videoId, target)
        }
    }

    /** Repair: every cluster that claimed this name is probably one person. */
    fun mergeAllNamed(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val matches = db.personDao().allPersons()
                .filter { it.name.equals(name, true) || it.suggestedName.equals(name, true) }
            if (matches.size < 2) return@launch
            val counts: Map<Int, Int> =
                matches.associate { it.id to db.personDao().vectorsForPerson(it.id).size }
            val keep: Int = counts.maxByOrNull { it.value }?.key ?: return@launch
            for (p in matches) {
                if (p.id == keep) continue
                db.personDao().mergePersons(p.id, keep)
                db.videoDao().mergePersons(p.id, keep)
            }
            db.personDao().rename(keep, name)
            db.personDao().deleteEmptyPersons()
        }
    }

    fun updateSettings(s: EngineSettings) {
        _settings.value = s
        viewModelScope.launch(Dispatchers.IO) { store.save(s) }
    }
    fun resetDerived() {
        viewModelScope.launch(Dispatchers.IO) {
            db.videoDao().resetDerived()
            db.personDao().deleteAllVectors()
            BrainScanService.start(ctx)
        }
    }
    fun resetEverything() {
        viewModelScope.launch(Dispatchers.IO) {
            db.videoDao().resetEverything()
            db.personDao().deleteAllVectors()
            db.supportDao().clearEvents()
            BrainScanService.start(ctx)
        }
    }
    fun refreshBattery() { _batteryExempt.value = battery.isIgnoringOptimizations() }
    fun batteryRequestIntent() = battery.requestIntent()
    fun batterySettingsIntent() = battery.settingsIntent()

    suspend fun refreshDiagnostics() {
        val d = withContext(Dispatchers.IO) {
            val dbFile = ctx.getDatabasePath("brain_gallery.db")
            val pdao = db.personDao()
            val persons = pdao.allPersons()
            val kv = db.supportDao()
            Diagnostics(
                dbBytes = if (dbFile.exists()) dbFile.length() else 0L,
                persons = persons.size,
                vectors = pdao.vectorCount(),
                unassigned = pdao.unassignedVectors().size,
                splitsFlagged = persons.count { it.splitSuggested },
                lastL1a = kv.get("diag.lastRun")?.split("|")?.getOrNull(0)?.toIntOrNull() ?: 0,
                lastL1b = kv.get("diag.lastRun")?.split("|")?.getOrNull(1)?.toIntOrNull() ?: 0,
                lastMs = kv.get("diag.lastRun")?.split("|")?.getOrNull(2)?.toLongOrNull() ?: 0,
                tScan = kv.get("diag.tScan")?.toLongOrNull() ?: 0,
                tL1a = kv.get("diag.tL1a")?.toLongOrNull() ?: 0,
                tL1b = kv.get("diag.tL1b")?.toLongOrNull() ?: 0,
                merged = kv.get("diag.lastRun")?.split("|")?.getOrNull(4)?.toIntOrNull() ?: 0,
                adopted = kv.get("diag.lastRun")?.split("|")?.getOrNull(5)?.toIntOrNull() ?: 0,
                unreadable = kv.get("diag.lastRun")?.split("|")?.getOrNull(6)?.toIntOrNull() ?: 0,
                stalled = kv.get("diag.lastRun")?.split("|")?.getOrNull(7)?.toIntOrNull() ?: 0
            )
        }
        _diag.value = d
    }

    // ---- Portability ----
    private val _exportMsg = MutableStateFlow<String?>(null)
    val exportMsg: StateFlow<String?> = _exportMsg

    fun exportMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val f = bundle.exportToFile()
                "Saved ${f.name} (${f.length() / 1024} KB)"
            }.onSuccess { _exportMsg.value = it }
                .onFailure { _exportMsg.value = "Export failed: ${it.message}" }
        }
    }
    fun consumeExportMsg() { _exportMsg.value = null }

    // ---- Delete with system consent ----
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
                _deleteAsk.value = MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender
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
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { db.personDao().deleteVectorsForVideo(it) }
            db.videoDao().deleteByIds(ids)
        }
    }

    fun toggleFav(v: VideoEntity) {
        viewModelScope.launch { db.videoDao().setFavorite(v.id, !v.isFavorite) }
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val bundle: MemoryBundle,
    private val battery: BatteryOptims,
    private val store: SettingsStore,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    private val _q = MutableStateFlow("")
    val query: StateFlow<String> = _q
    private val _results = MutableStateFlow<List<VideoEntity>>(emptyList())
    val results: StateFlow<List<VideoEntity>> = _results

    init {
        viewModelScope.launch {
            _q.debounce(300).collect { q -> resolve(q) }
        }
    }

    private suspend fun resolve(q: String) {
        if (q.length < 2) { _results.value = emptyList(); return }
        val lower = q.trim().lowercase()
        if (lower in setOf("people", "person", "faces", "face", "smiles", "smiling", "selfie", "selfies")) {
            _results.value = db.videoDao().people(); return
        }
        // Route named-person queries directly to that person's library.
        val named = db.personDao().allPersons()
            .firstOrNull { it.name.equals(q.trim(), true) || it.suggestedName.equals(q.trim(), true) }
        if (named != null) {
            _results.value = db.videoDao().personGroup(named.id); return
        }
        val like = db.videoDao().search(q)
        val likeIds = like.map { it.id }.toSet()
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

    fun query(s: String) { _q.value = s }
    fun toggleFav(v: VideoEntity) {
        viewModelScope.launch { db.videoDao().setFavorite(v.id, !v.isFavorite) }
    }

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
                _deleteAsk.value = MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender
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

    private val _exportMsg = MutableStateFlow<String?>(null)
    val exportMsg: StateFlow<String?> = _exportMsg
    fun exportMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { bundle.exportToFile().name }
                .onSuccess { _exportMsg.value = "Saved $it" }
                .onFailure { _exportMsg.value = "Export failed: ${it.message}" }
        }
    }
    fun consumeExportMsg() { _exportMsg.value = null }
}
