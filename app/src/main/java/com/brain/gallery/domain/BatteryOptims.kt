package com.brain.gallery.ui.organize

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mid-range OEM ROMs ship "one tap clean" utilities that kill long background
 * runs. Measured on a Redmi: the indexer was terminated mid-run by exactly that.
 * Surfacing the exemption is the difference between an index that finishes and
 * one that stalls at 20%.
 */
@Singleton
class BatteryOptims @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun isIgnoringOptimizations(): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(false)
    }

    fun requestIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${ctx.packageName}"))

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
