package com.brain.gallery.ui.organize

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.portability.MemoryBundle
import com.brain.gallery.data.service.BrainScanService
import com.brain.gallery.domain.BatteryOptims
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.SettingsStore
import com.brain.gallery.domain.organize.Album
import com.brain.gallery.domain.organize.GroupBuilder
import com.brain.gallery.domain.organize.SmartGroup
import com.brain.gallery.engine.MemoryDocBuilder
import com.brain.gallery.engine.ContentId
import com.brain.gallery.data.search.FtsSchema
import com.brain.gallery.data.search.SearchIndexer
import com.brain.gallery.data.brain.ContentHasher
import com.brain.gallery.ui.theme.AccentHolder
import com.brain.gallery.ui.theme.PaletteHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

/** One consistent frame of everything the group builder needs. */
private data class GroupsSnapshot(
    val videos: List<VideoEntity>,
    val people: List<PersonEntity>,
    val names: Map<String, String>,
    val albums: List<Album>
)

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val groups: GroupBuilder,
    private val bundle: MemoryBundle,
    private val battery: BatteryOptims,
    private val store: SettingsStore,
    private val indexer: SearchIndexer,
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
        // Continuous, not one-shot: Tune and Brain both save through this
        // store, and the accent and the theme must repaint the moment either
        // does — from whichever screen the change came.
        viewModelScope.launch {
            store.observe().collect {
                _settings.value = it
                AccentHolder.argb = it.accentArgb
                PaletteHolder.dark = it.theme != "light"
            }
        }
        viewModelScope.launch {
            // conflate: collapse bursts of writes into one rebuild, and skip the
            // rebuild entirely when nothing structural actually changed.
            combine(
                db.videoDao().observeAll().conflate(),
                db.personDao().observePersons().conflate(),
                db.supportDao().observeGroupOverrides().conflate(),
                db.supportDao().observeAlbums().conflate()
            ) { videos, people, over, albumRows ->
                GroupsSnapshot(
                    videos, people,
                    over.associate { it.groupId to it.name },
                    albumRows.map { Album(it.id, it.name, it.createdAt) }
                )
            }
                .distinctUntilChanged { old, new ->
                    old.videos.size == new.videos.size &&
                        old.people.size == new.people.size &&
                        old.names == new.names &&
                        old.albums == new.albums &&
                        old.videos.sumOf { it.watchCount } == new.videos.sumOf { it.watchCount }
                }
                .collect { snap ->
                    val (videos, people, names, albums) = snap
                    _albums.value = albums
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
                                videos, people, cfg.duplicatesEnabled, cfg.junkSensitivity,
                                names, albums
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
        viewModelScope.launch {
            db.personDao().rename(personId, name)
            // The name is part of that person's documents; the index follows.
            indexer.rebuildVideoIds(db.videoDao().personGroup(personId).map { it.id })
        }
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
                indexer.rebuildVideoIds(db.videoDao().personGroup(pid).map { it.id })
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
            // The user just confirmed these clusters were one person. That
            // confirmation is identity the engine cannot re-derive later.
            db.personDao().verify(toId)
            indexer.rebuildVideoIds(db.videoDao().personGroup(toId).map { it.id })
        }
    }
    /** Split: detach one video's vectors from a person so it can re-match elsewhere. */
    fun splitVideoOut(personId: Int, videoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            for (v in db.personDao().vectorsForVideo(videoId)) {
                if (v.personId == personId) db.personDao().assignVector(v.id, -1)
            }
            db.videoDao().setPerson(videoId, -1)
            // And the correction becomes a lesson: "this clip is not that
            // person" survives every future adopt and re-match pass. Without
            // it, the next engine run would politely undo the user.
            db.personDao().insertLessons(
                listOf(com.brain.gallery.data.local.IdentityLessonEntity(
                    videoId = videoId, personId = personId,
                    createdAt = System.currentTimeMillis())))
        }
    }
    fun dismissSplitWarning(personId: Int) {
        // Learned silence, not a snooze: the splitter would flag this person
        // again on every run otherwise, and a warning you must dismiss forever
        // was never a warning, it was nagging.
        viewModelScope.launch(Dispatchers.IO) { db.personDao().silenceSplit(personId) }
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
        refreshProsody()
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
            refreshProsody()
        }
    }

    // ---- settings previews ----
    // A slider that changes how the library looks should say so before you move
    // it, not after a reindex. These answer "what would this do right now".

    /** How the library would split into memories and clutter at this threshold. */
    fun previewJunkSplit(threshold: Float): Pair<Int, Int> {
        val mem = allVideos.count { it.junkScore < threshold }
        return mem to (allVideos.size - mem)
    }

    /**
     * How many people would survive at this merge strictness. Above the current
     * setting is finer, below is coarser, so the number moves both ways as the
     * slider does.
     */
    fun previewPeopleAt(strictness: Float): Int {
        val byPerson = allVideos.filter { it.personId >= 0 }.groupBy { it.personId }
        return byPerson.count { (_, list) -> list.size >= strictness }
    }

    /** Faces still waiting to be placed; only moves after a reindex, so say so. */
    fun previewUnplaced(): Int = allVideos.count { it.faceCount > 0 && it.personId < 0 }

    private val _prosody = MutableStateFlow(0 to 0)
    val prosody: StateFlow<Pair<Int, Int>> = _prosody

    fun prosodyPending(): Int = _prosody.value.first
    fun prosodyLearned(): Int = _prosody.value.second

    private fun refreshProsody() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                db.videoDao().prosodyPendingCount() to db.videoDao().prosodyDoneCount()
            }.onSuccess { _prosody.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { db.videoDao().transcriptDoneCount() }
                .onSuccess { _transcripts.value = it }
        }
    }

    /** Clips whose words actually landed somewhere searchable. */
    private val _transcripts = MutableStateFlow(0)
    fun transcriptDone(): Int = _transcripts.value

    /**
     * Re-derive the voice reading for clips it got wrong. This is the answer to
     * a subject the brain set badly: requeue only those, rather than wiping and
     * rebuilding a library that is otherwise fine.
     */
    fun relearnUnclear() {
        viewModelScope.launch(Dispatchers.IO) {
            db.videoDao().requeueUnclear()
            refreshProsody()
            BrainScanService.start(ctx)
        }
    }

    fun relearnAllVoice() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.videoDao().getAllSync()
            db.videoDao().requeueProsody(all.map { it.id })
            refreshProsody()
            BrainScanService.start(ctx)
        }
    }

    /** Semantic work still owed, at the current budget. */
    fun previewSemanticPending(): Int = allVideos.count { it.pendingSemantic || it.brainLevel < 2 }

    // ---- Albums: the user's own shelves ----

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    fun createAlbum(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { db.supportDao().insertAlbum(clean, System.currentTimeMillis()) }
    }

    /** Deleting a shelf keeps its clips: they go back to no album. */
    fun deleteAlbum(id: Long) {
        viewModelScope.launch {
            db.supportDao().clearAlbumMembers(id)
            db.supportDao().deleteAlbum(id)
        }
    }

    /** Create a shelf and drop the selection straight into it, one gesture. */
    fun moveSelectionToNewAlbum(name: String) {
        val ids = _selection.value.toList()
        val clean = name.trim()
        if (ids.isEmpty() || clean.isEmpty()) return
        viewModelScope.launch {
            val id = db.supportDao().insertAlbum(clean, System.currentTimeMillis())
            db.supportDao().assignToAlbum(ids, id)
            _selection.value = emptySet()
        }
    }

    fun moveSelectionToAlbum(albumId: Long) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.supportDao().assignToAlbum(ids, albumId)
            _selection.value = emptySet()
        }
    }

    // ---- Photos: the still half of the library ----

    private val _photos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val photos: StateFlow<List<PhotoEntity>> = _photos
    private val _photoPending = MutableStateFlow(0)
    val photoPending: StateFlow<Int> = _photoPending

    init {
        viewModelScope.launch {
            db.photoDao().observeAll().conflate().collect { _photos.value = it }
        }
        viewModelScope.launch {
            db.photoDao().observeAll().conflate().collect {
                _photoPending.value = db.photoDao().pendingCount()
            }
        }
    }

    fun togglePhotoFav(id: Long, fav: Boolean) {
        viewModelScope.launch { db.photoDao().setFavorite(id, fav) }
    }

    /** Delete-with-consent for photos, same contract as the video path. */
    private val _photoDeleteAsk = MutableStateFlow<IntentSender?>(null)
    val photoDeleteAsk: StateFlow<IntentSender?> = _photoDeleteAsk
    private var pendingPhotoIds: List<Long> = emptyList()

    fun requestPhotoDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            pendingPhotoIds = ids
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val uris = ids.mapNotNull { id ->
                    db.photoDao().all().firstOrNull { it.id == id }?.uri
                        ?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                }
                if (uris.isEmpty()) return@launch
                _photoDeleteAsk.value = android.provider.MediaStore.createDeleteRequest(
                    ctx.contentResolver, uris).intentSender
            } else {
                val all = db.photoDao().all()
                ids.forEach { id ->
                    all.firstOrNull { it.id == id }?.let { p ->
                        runCatching { ctx.contentResolver.delete(android.net.Uri.parse(p.uri), null, null) }
                    }
                }
                db.photoDao().deleteByIds(ids)
            }
        }
    }

    fun consumePhotoDeleteAsk() { _photoDeleteAsk.value = null }

    fun confirmPhotoDelete() {
        val ids = pendingPhotoIds
        pendingPhotoIds = emptyList()
        viewModelScope.launch(Dispatchers.IO) { db.photoDao().deleteByIds(ids) }
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
            if (vectors.isEmpty()) {
                // Nothing to re-point: still record the decision, otherwise the
                // tap looks broken. The clip joins the person's group by id.
                if (targetPersonId >= 0) db.videoDao().setPerson(videoId, targetPersonId)
                return@launch
            }
            val target = if (targetPersonId >= 0) targetPersonId else {
                db.personDao().insertPerson(
                    com.brain.gallery.data.local.PersonEntity(createdAt = System.currentTimeMillis())
                ).toInt()
            }
            for (v in vectors) db.personDao().assignVector(v.id, target)
            db.videoDao().setPerson(videoId, target)
            // Hand-placing a clip is a statement about identity: verified.
            db.personDao().verify(target)
            indexer.rebuildVideoIds(listOf(videoId))
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
            indexer.rebuildVideoIds(db.videoDao().personGroup(keep).map { it.id })
        }
    }

    fun updateSettings(s: EngineSettings) {
        _settings.value = s
        // The holder is the live accent; the save below only persists it.
        AccentHolder.argb = s.accentArgb
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

    private val _importMsg = MutableStateFlow<String?>(null)
    val importMsg: StateFlow<String?> = _importMsg

    /** Restore the human layer from a picked memory-bundle file. */
    fun importMemory(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read the picked file")
                bundle.importJson(text)
            }.onSuccess {
                _importMsg.value = it
                // People may have been renamed by the import; the documents follow.
                runCatching { indexer.rebuildAll() }
            }
                .onFailure { _importMsg.value = "Import failed: ${it.message}" }
        }
    }
    fun consumeImportMsg() { _importMsg.value = null }

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

/** The chip row over search results. A lens, not a query. */
enum class VoiceFilter { ALL, PEOPLE, FAVORITES, TALKING }

/**
 * One search result, either way. Its vocabulary is the same in both media:
 * faces, favorites, and (video-only) the voice layer — which is what the lens
 * chips above the grid read, so they keep meaning exactly what they say.
 */
sealed interface SearchHit {
    val faceCount: Int
    val isFavorite: Boolean
    val prosodyTags: String

    data class VideoHit(val video: VideoEntity) : SearchHit {
        override val faceCount get() = video.faceCount
        override val isFavorite get() = video.isFavorite
        override val prosodyTags get() = video.prosodyTags
    }

    data class PhotoHit(val photo: PhotoEntity) : SearchHit {
        override val faceCount get() = photo.faceCount
        override val isFavorite get() = photo.isFavorite
        override val prosodyTags get() = ""
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val db: BrainDatabase,
    private val bundle: MemoryBundle,
    private val battery: BatteryOptims,
    private val store: SettingsStore,
    private val indexer: SearchIndexer,
    private val hasher: ContentHasher,
    private val savedState: androidx.lifecycle.SavedStateHandle,
    @ApplicationContext private val ctx: android.content.Context
) : ViewModel() {
    // The query survives process death: it lives in the destination's
    // SavedStateHandle, restored when the system rebuilds the back stack.
    private val _q = MutableStateFlow(savedState.get<String>("q") ?: "")
    val query: StateFlow<String> = _q
    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    private val _filter = MutableStateFlow(VoiceFilter.ALL)

    // The search landing: what you looked for before, and where to start.
    private val _recents = MutableStateFlow<List<String>>(emptyList())
    val recents: StateFlow<List<String>> = _recents

    // Suggestions never presume a life for the person holding the phone.
    // Two neutral capability routes, then categories that actually exist in
    // THIS library — if it isn't in your clips, it isn't offered.
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    init {
        // The index heals itself before the first query lands: rows that left
        // the library without a service run (manual deletes, sweeps) would
        // otherwise report counts that disagree with the media tables.
        viewModelScope.launch(Dispatchers.IO) { runCatching { indexer.ensureFresh() } }
        viewModelScope.launch {
            _recents.value = parseRecents(db.supportDao().get("search.recents"))
        }
        viewModelScope.launch(Dispatchers.IO) {
            val cats = runCatching { db.videoDao().getAllSync() }
                .getOrDefault(emptyList())
                .map { it.category }
                .filter { it != "unknown" }
                .groupingBy { it }
                .eachCount()
                .entries.sortedByDescending { it.value }
                .take(4).map { it.key }
            _suggestions.value = (listOf("talking", "people") + cats).distinct().take(6)
        }
    }

    private fun parseRecents(raw: String?): List<String> =
        runCatching {
            val arr = org.json.JSONArray(raw ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())

    /** Recorded on a committed search (the keyboard's search action), not per keystroke. */
    fun rememberSearch(q: String) {
        val clean = q.trim()
        if (clean.length < 2) return
        val next = (listOf(clean) + _recents.value).distinct().take(6)
        _recents.value = next
        viewModelScope.launch {
            db.supportDao().put("search.recents",
                org.json.JSONArray().apply { next.forEach { put(it) } }.toString())
        }
    }

    fun clearRecents() {
        _recents.value = emptyList()
        viewModelScope.launch { db.supportDao().put("search.recents", "[]") }
    }
    val filter: StateFlow<VoiceFilter> = _filter
    fun filter(f: VoiceFilter) { _filter.value = f }

    /**
     * The visible grid: raw matches narrowed by the chip row. Client-side on
     * purpose — the filter is a lens on what was found, not a new query.
     */
    val results: StateFlow<List<SearchHit>> = combine(_results, _filter) { r, f ->
        when (f) {
            VoiceFilter.ALL -> r
            VoiceFilter.PEOPLE -> r.filter { it.faceCount > 0 }
            VoiceFilter.FAVORITES -> r.filter { it.isFavorite }
            VoiceFilter.TALKING -> r.filter { it.prosodyTags.contains("speaking") }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            _q.debounce(300).collect { q -> resolve(q) }
        }
    }

    private suspend fun resolve(q: String) {
        if (q.length < 2) { _results.value = emptyList(); return }
        val lower = q.trim().lowercase()
        val asVideos: (List<VideoEntity>) -> List<SearchHit> = { l -> l.map { SearchHit.VideoHit(it) } }
        if (lower in setOf("people", "person", "faces", "face", "smiles", "smiling", "selfie", "selfies")) {
            _results.value = asVideos(db.videoDao().people()); return
        }
        // Voice routes: what the audio pass heard, not what a label guessed.
        if (lower in setOf("talking", "voice", "speech", "someone talking", "speaking")) {
            _results.value = asVideos(db.videoDao().byVoiceTag("speaking")); return
        }
        // Route named-person queries directly to that person's library.
        val named = db.personDao().allPersons()
            .firstOrNull { it.name.equals(q.trim(), true) || it.suggestedName.equals(q.trim(), true) }
        if (named != null) {
            _results.value = asVideos(db.videoDao().personGroup(named.id)); return
        }

        // General path: the FTS index narrows, the Memory Doc scores — on
        // candidates, never on the library. The LIKE nets stay merged in so a
        // fragment the tokenizer would split still lands, and the whole
        // general path degrades to what it was yesterday if the index is
        // unavailable for any reason. Everything here is one sorted list:
        // the query shows the brain's best matches, whatever medium they sit in.
        val like = db.videoDao().search(q)
        val likeIds = like.map { it.id }.toSet()
        val likePhoto = db.photoDao().searchLike(q)
        val likePhotoIds = likePhoto.map { it.id }.toSet()
        val ftsIds = runCatching {
            FtsSchema.videoMatch(q)?.let { db.searchFtsDao().ids(it) }
        }.getOrNull().orEmpty()
        val photoFts = runCatching {
            FtsSchema.photoMatch(q)?.let { db.searchFtsDao().ids(it) }
        }.getOrNull().orEmpty()

        data class Ranked(val hit: SearchHit, val score: Float, val tie: Long)

        val videoRanked = db.videoDao().getByIds((likeIds + ftsIds).distinct().take(500))
            .map { v -> SearchHit.VideoHit(v) to MemoryDocBuilder.matchScore(MemoryDocBuilder.build(v).text, q) }
            .filter { it.second > 0f || it.first.video.id in likeIds }
            .map { Ranked(it.first, it.second, it.first.video.watchCount.toLong()) }
        val photoRanked = db.photoDao().getByIds((likePhotoIds + photoFts).distinct().take(400))
            .map { p -> SearchHit.PhotoHit(p) to MemoryDocBuilder.matchScore(MemoryDocBuilder.buildPhoto(p).text, q) }
            .filter { it.second > 0f || it.first.photo.id in likePhotoIds }
            .map { Ranked(it.first, it.second, it.first.photo.dateAddedSec) }

        _results.value = (videoRanked + photoRanked)
            .sortedWith(compareByDescending<Ranked> { it.score }.thenByDescending { it.tie })
            .take(60)
            .map { it.hit }
    }

    fun query(s: String) {
        _q.value = s
        savedState["q"] = s
        // A typed query owns the grid again — any earlier Identify verdict
        // exits with the banner.
        _identifyMsg.value = null
    }

    // ---- Content ID: someone hands you a file, the library answers. ----
    // The query here is a sample of the thing itself, not a description:
    // fingerprint the picked file at a few offsets, match against every
    // fingerprint this library has, and say plainly what and where it is —
    // or that it is new. No network, no catalog, one hapticless answer.

    private val _identifying = MutableStateFlow(false)
    val identifying: StateFlow<Boolean> = _identifying

    /** The verdict banner: the honest answer, or nothing while typing. */
    private val _identifyMsg = MutableStateFlow<String?>(null)
    val identifyMsg: StateFlow<String?> = _identifyMsg

    fun identify(uri: Uri) {
        if (_identifying.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _identifyMsg.value = null
            _identifying.value = true
            val isVideo = runCatching { hasher.isMediaVideo(uri) }.getOrDefault(false)
            val frames = hasher.hashPicked(uri, isVideo)
            if (frames.isEmpty()) {
                _identifying.value = false
                _identifyMsg.value =
                    "Couldn't read that file's frames — nothing to match against"
                return@launch
            }
            val library = buildList {
                runCatching { db.videoDao().getAllSync() }.getOrDefault(emptyList()).forEach {
                    add(ContentId.LibraryHash(it.id, true, it.phash))
                }
                runCatching { db.photoDao().all() }.getOrDefault(emptyList()).forEach {
                    add(ContentId.LibraryHash(it.id, false, it.phash))
                }
            }
            val hits = ContentId.match(frames, library)
            _identifying.value = false
            if (hits.isEmpty()) {
                _identifyMsg.value = "Not in your library yet — this file is new to you"
                return@launch
            }
            val bestFirst = buildList {
                val seen = mutableSetOf<Pair<Boolean, Long>>()
                for (s in hits) {
                    if (!seen.add(s.hash.isVideo to s.hash.id)) continue
                    val ent = if (s.hash.isVideo)
                        db.videoDao().getById(s.hash.id)?.let { SearchHit.VideoHit(it) }
                    else
                        db.photoDao().getById(s.hash.id)?.let { SearchHit.PhotoHit(it) }
                    if (ent != null) add(ent)
                }
            }.take(12)
            if (bestFirst.isEmpty()) {
                // Every matching row vanished between hashing and lookup —
                // the honest fallback is the same "new" verdict.
                _identifyMsg.value = "Not in your library yet — this file is new to you"
                return@launch
            }
            _filter.value = VoiceFilter.ALL // a lens over a per-file answer would just hide it
            _results.value = bestFirst
            _identifyMsg.value = describe(bestFirst.first(), ContentId.nearIdentical(hits))
        }
    }

    /** Where the best hit lives, and how confident the pixels are. */
    private fun describe(hit: SearchHit, nearIdentical: Boolean): String =
        runCatching {
            val kind: String
            val folder: String
            val dateSec: Long
            when (hit) {
                is SearchHit.VideoHit -> {
                    kind = "clip"; folder = hit.video.folderName
                    dateSec = hit.video.dateAddedSec
                }
                is SearchHit.PhotoHit -> {
                    kind = "photo"; folder = hit.photo.folderName
                    dateSec = hit.photo.dateAddedSec
                }
            }
            val day = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(Date(dateSec * 1000L))
            val confidence = if (nearIdentical) "an almost-exact copy of"
            else "looks like"
            "This is $confidence your $kind in \"$folder\" — $day"
        }.getOrDefault("Found a match below")

    fun toggleFav(v: VideoEntity) {
        viewModelScope.launch { db.videoDao().setFavorite(v.id, !v.isFavorite) }
    }
    fun togglePhotoFav(p: PhotoEntity) {
        viewModelScope.launch { db.photoDao().setFavorite(p.id, !p.isFavorite) }
    }

    // Deleting a photo result: same consent path as videos, own channel.
    private val _photoDeleteAsk = MutableStateFlow<IntentSender?>(null)
    val photoDeleteAsk: StateFlow<IntentSender?> = _photoDeleteAsk
    private var pendingPhotoIds: List<Long> = emptyList()

    fun requestDeletePhotos(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            pendingPhotoIds = ids
            if (Build.VERSION.SDK_INT >= 30) {
                val uris = ids.mapNotNull { id ->
                    db.photoDao().getById(id)?.uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                }
                if (uris.isEmpty()) return@launch
                _photoDeleteAsk.value = MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender
            } else {
                ids.forEach { id ->
                    db.photoDao().getById(id)?.let { p ->
                        runCatching { ctx.contentResolver.delete(Uri.parse(p.uri), null, null) }
                    }
                }
                db.photoDao().deleteByIds(ids)
            }
        }
    }

    fun consumePhotoDeleteAsk() { _photoDeleteAsk.value = null }
    fun confirmPhotoDelete() {
        val ids = pendingPhotoIds
        pendingPhotoIds = emptyList()
        viewModelScope.launch(Dispatchers.IO) { db.photoDao().deleteByIds(ids) }
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
