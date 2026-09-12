package com.brain.gallery.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brain.gallery.data.brain.Level0Analyzer
import com.brain.gallery.data.brain.Level1Analyzer
import com.brain.gallery.data.brain.Level2Analyzer
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.scan.MediaScanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IndexWorkerEntryPoint {
    fun db(): BrainDatabase
    fun scanner(): MediaScanner
    fun l1(): Level1Analyzer
    fun l2(): Level2Analyzer
}

class IndexWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext, IndexWorkerEntryPoint::class.java)
        val dao = entry.db().videoDao()
        val scanner = entry.scanner()
        val l1 = entry.l1()
        val l2 = entry.l2()

        val scanned = scanner.scan()
        val existing = dao.getAllSync().associateBy { it.id }
        // Merge: keep brain/behavior fields for known videos, insert L0 for new
        val merged = scanned.map { s ->
            val old = existing[s.id]
            if (old == null) s
            else old.copy(uri = s.uri, displayName = s.displayName, durationMs = s.durationMs,
                sizeBytes = s.sizeBytes, folderName = s.folderName, width = s.width, height = s.height)
        }
        dao.upsertAll(merged)
        if (existing.isNotEmpty()) dao.deleteRemoved(scanned.map { it.id })

        // Cascade: L1 for uncertain, L2 only if still uncertain + worthy
        val fresh = dao.getAllSync()
        var l1Budget = 25 // battery cap per run
        for (v in fresh) {
            if (l1Budget <= 0) break
            if (v.brainLevel >= 1) continue
            val l0r = Level0Analyzer.analyze(v.displayName, v.folderName, v.durationMs)
            if (!Level0Analyzer.needsDeeper(l0r, v)) {
                dao.upsert(v.copy(brainLevel = 1, confidence = maxOf(v.confidence, l0r.confidence)))
                continue
            }
            val r1 = l1.analyze(v.uri, l0r)
            l1Budget--
            val needL2 = r1.confidence < 0.75f && v.junkScore < 0.5f && v.durationMs > 8_000
            if (!needL2) {
                dao.upsert(v.copy(category = r1.category, tags = r1.tags.joinToString(","),
                    about = r1.about, confidence = r1.confidence, brainLevel = 1, junkScore = r1.junkScore))
            } else {
                val r2 = l2.analyze(v.uri, v.durationMs, r1)
                dao.upsert(v.copy(category = r2.category, tags = r2.tags.joinToString(","),
                    about = r2.about, confidence = r2.confidence, brainLevel = 2, junkScore = r2.junkScore))
            }
        }
        return Result.success()
    }
}
