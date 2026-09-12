package com.brain.gallery.domain.engine

import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.WatchEventEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class FeedItem(val video: VideoEntity, val score: Float, val why: String)

@Singleton
class FeedComposer @Inject constructor() {

    fun compose(videos: List<VideoEntity>, events: List<WatchEventEntity>, limit: Int = 60): List<FeedItem> {
        if (videos.isEmpty()) return emptyList()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val recentIds = events.take(40).map { it.videoId }.toSet()
        val scored = videos.filter { it.junkScore < 0.85f }.map { v ->
            var s = 0.3f // base exploration
            var why = "Fresh pick"
            // Behavior: completion is king
            if (v.watchCount > 0) {
                s += v.avgCompletion * 0.35f
                if (v.avgCompletion > 0.7f) { s += 0.15f; why = "You finished this before" }
                if (v.skipCount > 2) s -= 0.2f
            } else {
                s += 0.12f; why = "Unwatched"
            }
            if (v.isFavorite) { s += 0.2f; why = "One of your favorites" }
            // Nostalgia: buried gem
            val ageDays = (System.currentTimeMillis() / 1000 - v.dateAddedSec) / 86400
            if (ageDays > 180 && v.watchCount == 0 && v.junkScore < 0.4f) { s += 0.25f; why = "Buried gem • ${ageDays / 30}mo ago" }
            // On this day
            if (isOnThisDay(v.dateAddedSec)) { s += 0.2f; why = "On this day • ${ageDays / 365}y ago" }
            // Time-of-day fit
            val short = v.durationMs < 25_000
            if ((hour < 11 && short) || (hour >= 20 && v.durationMs > 60_000)) s += 0.1f
            // Confidence penalty: uncertain brain ranks lower until L1/L2 done
            s *= (0.7f + v.confidence * 0.3f)
            // Anti-repeat-session: watched recently sinks
            if (v.id in recentIds) s *= 0.35f
            FeedItem(v, s.coerceIn(0f, 1f), why)
        }.sortedByDescending { it.score }.take(limit * 2)

        // Palette cleanse: no same category twice in a row, 80/20 explore
        val out = mutableListOf<FeedItem>()
        var lastCat = ""
        val shuffled = scored.shuffled()
        for (item in scored) {
            if (out.size >= limit) break
            if (item.video.category == lastCat) continue
            if (item.score < 0.25f && Math.random() > 0.2) continue // 20% explore pass
            out += item; lastCat = item.video.category
        }
        // Fill remainder ignoring cleanse
        if (out.size < limit) for (item in shuffled) {
            if (out.size >= limit) break
            if (out.none { it.video.id == item.video.id }) out += item
        }
        return out
    }

    private fun isOnThisDay(dateAddedSec: Long): Boolean {
        if (dateAddedSec <= 0) return false
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = dateAddedSec * 1000 }
        return now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) != then.get(Calendar.YEAR)
    }
}
