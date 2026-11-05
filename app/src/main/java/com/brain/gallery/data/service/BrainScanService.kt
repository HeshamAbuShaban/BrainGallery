package com.brain.gallery.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.brain.gallery.data.brain.L1aAnalyzer
import com.brain.gallery.data.brain.AudioProbe
import com.brain.gallery.data.brain.L1bAnalyzer
import com.brain.gallery.data.brain.Level0Analyzer
import com.brain.gallery.data.brain.PerceptualResult
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.scan.MediaScanner
import com.brain.gallery.data.scan.toEntity
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.SettingsStore
import com.brain.gallery.data.vision.FrameCache
import com.brain.gallery.engine.NameSuggester
import com.brain.gallery.engine.PersonConsolidator
import com.brain.gallery.engine.PersonMatcher
import com.brain.gallery.engine.SplitDetector
import androidx.room.withTransaction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground indexer. Cascade order is by COST, not importance:
 *   sweep -> L0 -> L1a perceptual (unbudgeted, all) -> L1b semantic (budgeted, EV queue)
 * then incremental identity + split detection + name suggestions.
 */
@AndroidEntryPoint
class BrainScanService : LifecycleService() {

    @Inject lateinit var db: BrainDatabase
    @Inject lateinit var scanner: MediaScanner
    @Inject lateinit var l1a: L1aAnalyzer
    @Inject lateinit var l1b: L1bAnalyzer
    @Inject lateinit var photoScanner: com.brain.gallery.data.scan.PhotoScanner
    @Inject lateinit var photoAnalyzer: com.brain.gallery.data.brain.PhotoAnalyzer
    @Inject lateinit var audio: AudioProbe
    @Inject lateinit var matcher: PersonMatcher
    @Inject lateinit var consolidator: PersonConsolidator
    @Inject lateinit var settings: SettingsStore
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    @Inject lateinit var splitter: SplitDetector
    @Inject lateinit var namer: NameSuggester
    @Inject lateinit var frameCache: FrameCache
    @Inject lateinit var searchIndexer: com.brain.gallery.data.search.SearchIndexer
    @Inject lateinit var tasteLearner: com.brain.gallery.domain.TasteLearner
    @Inject lateinit var transcriber: com.brain.gallery.data.brain.Transcriber

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Overlapping runs would fight over the same database writes.
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "run already in progress; ignoring start id=$startId")
            return START_NOT_STICKY
        }
        startForegroundCompat(notify("Scanning gallery…", 0, 0, true))
        val deltaIds = intent?.getLongArrayExtra(EXTRA_IDS)?.toList()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                runIndex(deltaIds)
            } catch (t: Throwable) {
                // Never die silently: a swallowed failure looks like a hang.
                Log.e(TAG, "index run failed", t)
                nm.notify(NOTIF_ID, NotificationCompat.Builder(this@BrainScanService, CHANNEL)
                    .setContentTitle("BrainGallery")
                    .setContentText("Indexing paused — open the app to retry")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setAutoCancel(true).build())
            } finally {
                running.set(false)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun phase(name: String) { Log.i(TAG, "phase: $name") }

    private val nm: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private suspend fun runIndex(deltaIds: List<Long>?) {
        val dao = db.videoDao()
        val t0 = System.currentTimeMillis()
        val scanId = t0
        phase("settings")
        val cfg = settings.load()
        phase("scan")

        val scanned = if (deltaIds != null) scanner.scanIds(deltaIds) else scanner.scan()
        Log.i(TAG, "scanned=${scanned.size} delta=${deltaIds?.size}")
        val byId = dao.getAllSync().associateBy { it.id }
        val fresh = scanned.filter { it.id !in byId }
        val known = scanned.filter { it.id in byId }

        // Brand new videos first, so they exist before stamping/sweeping.
        if (fresh.isNotEmpty()) {
            dao.insertNew(fresh.map { s ->
                val l0 = Level0Analyzer.analyze(s.displayName, s.folderName, s.durationMs)
                VideoEntity(
                    id = s.id, uri = s.uri, displayName = s.displayName,
                    durationMs = s.durationMs, sizeBytes = s.sizeBytes,
                    dateAddedSec = s.dateAddedSec, folderName = s.folderName,
                    width = s.width, height = s.height,
                    category = l0.category, tags = l0.tags.joinToString(","),
                    about = l0.about, confidence = l0.confidence,
                    brainLevel = 0, junkScore = l0.junkScore,
                    lastSeenScan = scanId
                )
            })
        }

        // Order matters: stamp everything we actually saw, THEN sweep, then update
        // only rows whose metadata really changed. Skipping the stamp step here
        // once wiped the whole library, so it is never optional.
        val seenIds = scanned.map { it.id }
        seenIds.chunked(400).forEach { chunk -> dao.stampSeen(chunk, scanId) }

        // Safety: a truncated scan must never be allowed to delete anything.
        val existingCount = byId.size
        if (deltaIds == null && existingCount > 0 && seenIds.size < existingCount / 2) {
            Log.w(TAG, "scan returned ${seenIds.size} of $existingCount known videos; skipping sweep")
        } else if (deltaIds == null) {
            dao.sweepMissing(scanId)
        }

        val changed = known.filter { s ->
            val o = byId[s.id] ?: return@filter true
            o.displayName != s.displayName || o.durationMs != s.durationMs ||
                o.sizeBytes != s.sizeBytes || o.folderName != s.folderName ||
                o.width != s.width || o.height != s.height || o.uri != s.uri
        }
        if (changed.isNotEmpty()) {
            db.withTransaction {
                for (s in changed) {
                    dao.touchMetadata(s.id, s.uri, s.displayName, s.durationMs, s.sizeBytes,
                        s.folderName, s.width, s.height, scanId)
                }
            }
        }
        phase("metadata-merge")
        val tScan = System.currentTimeMillis()

        // ---- L1a: unbudgeted, looped until drained or the time budget runs out ----
        phase("perceptual")
        // Hard outer ceiling: whatever happens, we must reach the bookkeeping below.
        val hardDeadline = t0 + cfg.runBudgetSeconds * 1000L + HARD_GRACE_MS
        val deadline = t0 + cfg.runBudgetSeconds * 1000L
        var totalL1a = 0
        var totalL1b = 0
        var unreadable = 0
        while (System.currentTimeMillis() < hardDeadline) {
            val pending = dao.pendingPerceptual(PERCEPTUAL_BATCH)
            if (pending.isEmpty()) break
            Log.i(TAG, "perceptual batch of ${pending.size}, first id=${pending.first().id} " +
                "\"${pending.first().displayName}\"")
            for ((i, v) in pending.withIndex()) {
                // One pathological file must never cost more than a couple of seconds.
                val r = withTimeoutOrNull(PER_VIDEO_TIMEOUT_MS) {
                    runCatching { l1a.analyze(v.uri, v.durationMs, v.displayName, v.folderName) }
                        .getOrNull()
                }
                if (r == null || !r.frameRead) {
                    // Unreadable frame: park it so we stop retrying it forever.
                    unreadable++
                    dao.applyPerceptual(
                        id = v.id, category = v.category, tags = (v.tagList + "unreadable").joinToString(","),
                        about = "Could not read a frame", confidence = 0f, level = 1,
                        junk = maxOf(v.junkScore, 0.5f), faces = 0, smiles = 0, phash = 0L,
                        sharpness = 0f, priority = 0f, pending = false
                    )
                    continue
                }
                dao.applyPerceptual(
                    id = v.id, category = r.category, tags = r.tags.joinToString(","),
                    about = r.about, confidence = r.confidence, level = 1, junk = r.junkScore,
                    faces = r.faceCount, smiles = r.smileCount, phash = r.phash,
                    sharpness = r.sharpness, priority = r.priority, pending = r.pendingSemantic
                )
                if (cfg.identityEnabled) {
                    for (cand in r.vectors) matcher.assignOrCreate(v.id, cand.vec, cand.quality, cfg.matchSim)
                }
                totalL1a++
                if (i % 25 == 0) {
                    val remaining = dao.perceptualPendingCount()
                    Log.i(TAG, "perceptual ${totalL1a + unreadable}/${pending.size} remaining=$remaining")
                    notify("Understanding videos… ${totalL1a + unreadable}", totalL1a + unreadable,
                        totalL1a + unreadable + remaining, false)
                }
            }
        }
        val tL1a = System.currentTimeMillis()

        // ---- L1b: budget scales with how much of the library is still unknown ----
        phase("semantic")
        val semPending = dao.semanticPendingCount()
        val semBudget = if (cfg.semanticEnabled)
            semPending.coerceIn(SEMANTIC_MIN, cfg.semanticBudget.coerceIn(SEMANTIC_MIN, 600)) else 0
        var semIndex = 0
        var semStalled = 0
        while (semIndex < semBudget && System.currentTimeMillis() < hardDeadline) {
            val batch = dao.pendingSemantic(SEMANTIC_BATCH)
            if (batch.isEmpty()) break
            for ((i, v) in batch.withIndex()) {
                val prev = PerceptualResult(
                    v.phash, v.sharpness, v.faceCount, v.smileCount, v.about, v.category,
                    v.tagList, v.confidence, emptyList(), v.priority, true
                )
                // ML Kit's Task can hang on a damaged file; never wait forever for it.
                val s = withTimeoutOrNull(PER_VIDEO_TIMEOUT_MS) {
                    runCatching { l1b.analyze(v.id, v.uri, v.durationMs, prev) }.getOrNull()
                }
                if (i % 5 == 0) Log.i(TAG, "semantic $semIndex/$semBudget id=${v.id}")
                if (s == null) { semStalled++; continue }
                dao.applySemantic(v.id, s.category, s.tags.joinToString(","), s.about, s.confidence, 2)
                semIndex++
            }
        }
        totalL1b = semIndex
        val tL1b = System.currentTimeMillis()

        // ---- Optional voice layer, only on clips that look like someone talking ----
        // Gated twice: a setting, and a queue that only holds clips the brain
        // already believes have a person in them. Turning it on therefore costs a
        // fraction of a pass rather than a re-index of the library.
        var prosodyDone = 0
        if (cfg.prosodyEnabled) {
            phase("voice")
            var budget = PROSODY_BUDGET
            while (budget > 0 && System.currentTimeMillis() < hardDeadline) {
                val batch = dao.prosodyPending(budget.coerceAtMost(PROSODY_BATCH))
                if (batch.isEmpty()) break
                for (v in batch) {
                    val p = audio.probe(v.uri, v.durationMs)
                    // "not sampled" must still write something, or the clip stays
                    // in the queue forever and the pass never converges.
                    val tags = if (p.sampled) p.tags() else listOf("no readable audio")
                    dao.applyProsody(v.id, tags.joinToString(","), p.speechRatio)
                    prosodyDone++
                    budget--
                    if (budget % 10 == 0) {
                        Log.i(TAG, "voice $prosodyDone pending=${dao.prosodyPendingCount()}")
                    }
                }
            }
        }

        // ---- Transcripts: what a clip says, read wholesale by the phone's ----
        // own speech engine. Same voice switch as the audio pass, but gated by
        // what it measured: only clips that really talk, only short ones, a
        // slice per run — and one attempt per clip ever, so an engine that
        // cannot help still cannot hurt twice. Where the phone has no
        // on-device engine, the pass is simply silent and the queue waits
        // (nothing claims failure that did not happen).
        var transcriptDone = 0
        if (cfg.prosodyEnabled) {
            phase("transcripts")
            runCatching {
                val vdao = db.videoDao()
                for (v in vdao.transcriptPending(
                    TRANSCRIPT_BUDGET, TRANSCRIPT_MIN_SPEECH, TRANSCRIPT_MAX_MS)) {
                    if (System.currentTimeMillis() >= hardDeadline) break
                    if (!transcriber.available()) break // engine gone mid-run: leave the queue honest
                    val words = transcriber.transcribe(
                        Uri.parse(v.uri), v.durationMs) ?: ""
                    val shaped = com.brain.gallery.engine.TranscriptShaper.shape(words)
                    vdao.setTranscript(v.id, shaped)
                    transcriptDone++
                    // The document only has new words when there are words;
                    // an empty tombstone needs no index churn.
                    if (com.brain.gallery.engine.TranscriptShaper.usable(shaped)) {
                        searchIndexer.rebuildVideoIds(listOf(v.id))
                    }
                }
            }.onFailure { Log.w(TAG, "transcript pass failed", it) }
            if (transcriptDone > 0) Log.i(TAG, "transcripts attempted=$transcriptDone")
        }

        // ---- Identity maintenance: repair fragmentation, then look for over-merges ----
        phase("identity")
        val merged = consolidator.consolidate(cfg.mergeSim)
        db.personDao().deleteEmptyPersons()
        val adopted = consolidator.adoptUnassigned()
        syncDenormalisedPersons()
        val flagged = splitter.scanAll(cfg.splitSensitivity)
        namer.applyAllSuggestions()
        // ---- Photos: scan, sweep, then a budgeted image pass ----
        // Photos are their own lighter medium: L0 in the scanner, one
        // downscaled decode + ML read here, capped per run so a library of
        // ten thousand stills costs the same slice of battery as videos do.
        phase("photos")
        var photosRead = 0
        runCatching {
            val pdao = db.photoDao()
            // Delta ContentObserver events carry video ids; image changes
            // arrive via the same debounce but are not addressable per-id,
            // so delta runs refresh the budgeted read pass only and the full
            // scan does the sweep.
            val pscanned = if (deltaIds == null) photoScanner.scan() else emptyList()
            val pbyId = pdao.all().associateBy { it.id }
            val pfresh = pscanned.filter { it.id !in pbyId }
            val freshEntities = pfresh.map { it.toEntity() }
            if (freshEntities.isNotEmpty()) pdao.upsertAll(freshEntities)
            val pseenSet = pscanned.map { it.id }.toSet()
            if (deltaIds == null && pbyId.isNotEmpty() && pseenSet.size < pbyId.size / 2) {
                Log.w(TAG, "photo scan returned ${pseenSet.size} of ${pbyId.size}; skipping sweep")
            } else if (deltaIds == null) {
                val gone = pbyId.keys.filter { it !in pseenSet }
                gone.chunked(400).forEach { pdao.deleteByIds(it) }
            }
            // Junk is settled here, not left queued forever: pendingBrain
            // never returns a row at or above 0.9, but pendingCount counts
            // every brainLevel < 1. Without this, a library of screenshots
            // would keep rescheduling the service over rows it will never
            // read. The L0 verdict is the whole read for them.
            val settledJunk = (pbyId.values + freshEntities)
                .filter { it.brainLevel < 1 && it.junkScore >= 0.9f }
            for (p in settledJunk) {
                pdao.markRead(p.id, p.category, p.tags, p.about, p.confidence, 0, 0, 0L)
            }
            val budget = 40
            val pending = pdao.pendingBrain(budget)
            for (p in pending) {
                // The analyzer re-runs L0's fusion with pixels added: folder
                // and name go in, so a weak label cannot overwrite the shelf
                // the photo was filed on. Its verdict is already honest; the
                // old 0.6 "known category" floor inflated every fallback.
                val r = photoAnalyzer.analyze(p.uri, p.folderName, p.displayName) ?: continue
                pdao.markRead(p.id, r.category, r.tags, r.about,
                    r.confidence, r.faces, r.smiles, r.phash)
                photosRead++
            }
            if (pfresh.isNotEmpty() || photosRead > 0 || settledJunk.isNotEmpty()) {
                Log.i(TAG, "photos scanned=${pscanned.size} fresh=${pfresh.size} " +
                    "settled=${settledJunk.size} read=$photosRead")
            }
        }.onFailure { Log.w(TAG, "photo pass failed", it) }

        db.supportDao().put("diag.lastRun",
            "$totalL1a|$totalL1b|${System.currentTimeMillis() - t0}|$flagged|$merged|$adopted|$unreadable|$semStalled")
        db.supportDao().put("diag.photosRead", "$photosRead")
        db.supportDao().put("diag.tScan", "${tScan - t0}")
        db.supportDao().put("diag.tL1a", "${tL1a - tScan}")
        db.supportDao().put("diag.tL1b", "${tL1b - tL1a}")
        db.supportDao().put("diag.config", cfg.toJson())
        frameCache.prune()

        // ---- Taste: fold any watch events this run never saw ----
        // Exactly-once by cursor, so this is cheap call-or-miss maintenance and
        // the feed VM's on-watch learning is never double counted.
        runCatching { tasteLearner.learnNewEvents() }
            .onFailure { Log.w(TAG, "taste learn failed", it) }

        db.supportDao().pruneEvents(20000)

        // ---- Search index: FTS follows everything this run changed ----
        // Content moved (labels, voices, identity, photos, sweep) and the
        // index is one wholesale rebuild behind it. Wholesale on purpose:
        // it is one DELETE plus one INSERT..SELECT per table, and a
        // half-maintained index is exactly the kind of clever that lies.
        runCatching { searchIndexer.rebuildAll() }
            .onFailure { Log.w(TAG, "search index rebuild failed", it) }

        phase("done")
        // The voice queue has to count here as well, or an otherwise-indexed
        // library cancels the alarm and the voice pass stops the moment the
        // app is closed — it only ever advanced while someone was watching.
        val remaining = dao.perceptualPendingCount() + dao.semanticPendingCount() +
            db.photoDao().pendingCount() +
            if (cfg.prosodyEnabled) dao.prosodyPendingCount() else 0
        if (remaining > 0) {
            // Something is still queued: vendor cleaners kill long runs, so come back.
            RescheduleReceiver.schedule(this)
        } else {
            RescheduleReceiver.cancel(this)
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, doneNotification(totalL1a, totalL1b, merged, remaining))
    }

    /** Keep videos.personId aligned with the best vector per video. */
    private suspend fun syncDenormalisedPersons() = withContext(Dispatchers.IO) {
        val dao = db.videoDao()
        val pdao = db.personDao()
        val personIds = pdao.allPersons().map { it.id }.toSet()
        for (v in dao.getAllSync()) {
            val vs = pdao.vectorsForVideo(v.id)
            val best = vs.filter { it.personId >= 0 }.maxByOrNull { it.quality }?.personId ?: -1
            if (best != v.personId) dao.setPerson(v.id, if (best in personIds) best else -1)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL, "Library indexing", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun notify(text: String, done: Int, total: Int, indeterminate: Boolean): Notification {
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("BrainGallery")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(total, done, indeterminate)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
        return n
    }

    private fun doneNotification(
        perceptual: Int, semantic: Int, merged: Int, remaining: Int
    ): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("BrainGallery")
            .setContentText(
                if (remaining == 0) {
                    if (perceptual == 0 && semantic == 0) "Library up to date"
                    else "Indexed $perceptual · $semantic enriched${if (merged > 0) " · $merged merged" else ""}"
                } else "Indexed $perceptual · $semantic enriched · $remaining left"
            )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(remaining > 0)
            .build()

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val TAG = "BrainScan"
        const val CHANNEL = "brain_index"
        const val NOTIF_ID = 41
        const val EXTRA_IDS = "delta_ids"
        /** Wall-clock budget for one foreground indexing session. */
        const val RUN_BUDGET_MS = 150_000L
        /** Extra grace for the semantic stage once perceptual work is done. */
        const val SEMANTIC_GRACE_MS = 90_000L
        const val PERCEPTUAL_BATCH = 200
        const val SEMANTIC_BATCH = 25
        const val SEMANTIC_MIN = 25
        const val SEMANTIC_MAX = 250
        /** Per-video ceiling so one bad file cannot stall the run. */
        const val PER_VIDEO_TIMEOUT_MS = 12_000L
        /** Ceiling for the optional voice pass, so it can never eat the run. */
        const val PROSODY_BUDGET = 60
        const val PROSODY_BATCH = 20
        /** Ceiling for the words pass: a slice per run, one attempt per clip ever. */
        const val TRANSCRIPT_BUDGET = 8
        /** A clip shorter than this is read wholesale by the recogniser. */
        const val TRANSCRIPT_MAX_MS = 120_000L
        /** Beneath this much speech a recogniser pass is not worth its cost. */
        const val TRANSCRIPT_MIN_SPEECH = 0.1f
        /** Slack on top of the run budget so bookkeeping is always reached. */
        const val HARD_GRACE_MS = 60_000L
        fun start(ctx: Context, deltaIds: List<Long>? = null) {
            val i = Intent(ctx, BrainScanService::class.java)
            deltaIds?.let { i.putExtra(EXTRA_IDS, it.toLongArray()) }
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
