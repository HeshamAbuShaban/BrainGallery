package com.brain.gallery.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.brain.gallery.data.brain.L1aAnalyzer
import com.brain.gallery.data.brain.L1bAnalyzer
import com.brain.gallery.data.brain.Level0Analyzer
import com.brain.gallery.data.brain.PerceptualResult
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.scan.MediaScanner
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
    @Inject lateinit var matcher: PersonMatcher
    @Inject lateinit var consolidator: PersonConsolidator
    @Inject lateinit var settings: SettingsStore
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    @Inject lateinit var splitter: SplitDetector
    @Inject lateinit var namer: NameSuggester
    @Inject lateinit var frameCache: FrameCache

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
        val existingIds = dao.getAllSync().map { it.id }.toSet()
        val fresh = scanned.filter { it.id !in existingIds }
        val known = scanned.filter { it.id in existingIds }

        // One transaction, and only rows whose metadata actually changed: rewriting
        // 1300 identical rows every run was pure waste.
        val byId = dao.getAllSync().associateBy { it.id }
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
        // Mark-and-sweep only on full scans; a delta scan must not delete unseen rows.
        if (deltaIds == null) dao.sweepMissing(scanId)

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
                    about = r.about, confidence = r.confidence, level = 1, junk = v.junkScore,
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

        // ---- Identity maintenance: repair fragmentation, then look for over-merges ----
        phase("identity")
        val merged = consolidator.consolidate(cfg.mergeSim)
        val adopted = consolidator.adoptUnassigned()
        syncDenormalisedPersons()
        val flagged = splitter.scanAll(cfg.splitSensitivity)
        namer.applyAllSuggestions()
        db.supportDao().put("diag.lastRun",
            "$totalL1a|$totalL1b|${System.currentTimeMillis() - t0}|$flagged|$merged|$adopted|$unreadable|$semStalled")
        db.supportDao().put("diag.tScan", "${tScan - t0}")
        db.supportDao().put("diag.tL1a", "${tL1a - tScan}")
        db.supportDao().put("diag.tL1b", "${tL1b - tL1a}")
        db.supportDao().put("diag.config", cfg.toJson())
        frameCache.prune()
        db.supportDao().pruneEvents(20000)

        phase("done")
        val remaining = dao.perceptualPendingCount() + dao.semanticPendingCount()
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
