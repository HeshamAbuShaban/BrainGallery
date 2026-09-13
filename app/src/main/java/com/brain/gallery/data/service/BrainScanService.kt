package com.brain.gallery.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.brain.gallery.data.brain.Level0Analyzer
import com.brain.gallery.data.brain.Level1Analyzer
import com.brain.gallery.data.brain.Level2Analyzer
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.scan.MediaScanner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BrainScanService : LifecycleService() {

    @Inject lateinit var db: BrainDatabase
    @Inject lateinit var scanner: MediaScanner
    @Inject lateinit var l1: Level1Analyzer
    @Inject lateinit var l2: Level2Analyzer

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat(notify("Scanning gallery…", 0, 0, indeterminate = true))
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                runIndex()
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runIndex() {
        val dao = db.videoDao()
        val scanned = scanner.scan()
        val existing = dao.getAllSync().associateBy { it.id }
        val merged = scanned.map { s ->
            val old = existing[s.id]
            if (old == null) s
            else old.copy(uri = s.uri, displayName = s.displayName, durationMs = s.durationMs,
                sizeBytes = s.sizeBytes, folderName = s.folderName, width = s.width, height = s.height)
        }
        dao.upsertAll(merged)
        if (existing.isNotEmpty()) {
            val ids = scanned.map { it.id }
            if (ids.isNotEmpty()) dao.deleteRemoved(ids)
        }

        // Cascade: L0 instant already done in scan. L1/L2 only for uncertain, budget-capped.
        val fresh = dao.getAllSync().filter { it.brainLevel < 1 }
        var budget = 40
        var done = 0
        for (v in fresh) {
            if (budget <= 0) break
            val l0r = Level0Analyzer.analyze(v.displayName, v.folderName, v.durationMs)
            if (!Level0Analyzer.needsDeeper(l0r, v)) {
                dao.upsert(v.copy(brainLevel = 1, confidence = maxOf(v.confidence, l0r.confidence)))
            } else {
                val r1 = l1.analyze(v.uri, l0r)
                budget--
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
            done++
            if (done % 5 == 0) notify("Understanding videos… $done/${fresh.size}", done, fresh.size, false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, doneNotification(fresh.size))
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

    private fun doneNotification(count: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("BrainGallery")
            .setContentText(if (count == 0) "Library up to date" else "Organized $count videos")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val CHANNEL = "brain_index"
        const val NOTIF_ID = 41
        fun start(ctx: Context) {
            val i = Intent(ctx, BrainScanService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
