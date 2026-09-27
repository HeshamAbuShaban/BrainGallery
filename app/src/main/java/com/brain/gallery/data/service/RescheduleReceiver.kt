package com.brain.gallery.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Reschedules the indexer when a run is cut short.
 *
 * Measured on a real device: vendor "one tap clean" utilities and aggressive
 * battery managers kill long foreground runs on mid-range phones. The engine is
 * interruption-safe (every video is committed as it completes), so the only
 * thing needed is something that comes back and finishes the queue.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        runCatching { BrainScanService.start(ctx) }
            .onFailure { Log.w(TAG, "reschedule could not start service", it) }
    }

    companion object {
        private const val TAG = "BrainReschedule"
        const val REQ = 7711

        fun schedule(ctx: Context, delayMs: Long = 30_000L) {
            runCatching {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(
                    ctx, REQ,
                    Intent(ctx, RescheduleReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now() + delayMs, pi)
            }
        }

        fun cancel(ctx: Context) {
            runCatching {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(
                    ctx, REQ,
                    Intent(ctx, RescheduleReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
        }

        private fun now(): Long =
            if (Build.VERSION.SDK_INT >= 17) android.os.SystemClock.elapsedRealtime()
            else System.currentTimeMillis()
    }
}
