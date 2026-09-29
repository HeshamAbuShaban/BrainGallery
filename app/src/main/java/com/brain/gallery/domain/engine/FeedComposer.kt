package com.brain.gallery.domain.engine

import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.WatchEventEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class FeedItem(val video: VideoEntity, val score: Float, val why: String)

/**
 * Builds the For You reel.
 *
 * The ordering rule matters as much as the scoring. Sorting purely by score made
 * the same few clips lead every single session, which is what "the feed is stuck"
 * feels like. Instead the ranked list is cut into small score bands and shuffled
 * inside each band: strong matches still surface first, but the order within a
 * tier is different every run, so two sessions never play out the same way.
 */
@Singleton
class FeedComposer @Inject constructor() {

    private val random = Random.Default

    fun compose(
        videos: List<VideoEntity>,
        events: List<WatchEventEntity>,
        suppressed: Set<Long> = emptySet(),
        limit: Int = 60
    ): List<FeedItem> {
        if (videos.isEmpty()) return emptyList()
        val pool = if (suppressed.isEmpty()) videos else videos.filter { it.id !in suppressed }
        if (pool.isEmpty()) return emptyList()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val recentIds = events.take(40).map { it.videoId }.toSet()

        val ranked = pool.asSequence()
            .filter { it.junkScore < 0.85f }
            .map { v -> score(v, recentIds, hour) }
            .sortedByDescending { it.score }
            .take(limit * 2)
            .toList()

        val out = mutableListOf<FeedItem>()
        var lastCat = ""

        // Walk the ranked list in bands, shuffling inside each one. Adjacent
        // bands never reorder relative to each other, so quality ordering holds.
        for (band in ranked.chunked(BAND_SIZE)) {
            for (item in band.shuffled(random)) {
                if (out.size >= limit) break
                if (item.video.category == lastCat) continue          // palette cleanse
                if (item.score < 0.25f && random.nextFloat() > 0.2f) continue // 20% explore
                out += item
                lastCat = item.video.category
            }
            if (out.size >= limit) break
        }
        // Fill any remainder without the cleanse rule, still unordered.
        if (out.size < limit) for (item in ranked.shuffled(random)) {
            if (out.size >= limit) break
            if (out.none { it.video.id == item.video.id }) out += item
        }
        return out
    }

    private fun score(v: VideoEntity, recentIds: Set<Long>, hour: Int): FeedItem {
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
        // People matter: faces, especially smiles, earn a lift.
        if (v.faceCount > 0) {
            s += 0.08f
            if (why == "Fresh pick" || why == "Unwatched") why = "People you film"
        }
        if (v.smileCount > 0) { s += 0.05f; if (why == "People you film") why = "Happy moment" }
        // Nostalgia: buried gem
        val ageDays = (System.currentTimeMillis() / 1000 - v.dateAddedSec) / 86400
        if (ageDays > 180 && v.watchCount == 0 && v.junkScore < 0.4f) {
            s += 0.25f; why = "Buried gem • ${ageDays / 30}mo ago"
        }
        if (isOnThisDay(v.dateAddedSec)) { s += 0.2f; why = "On this day • ${ageDays / 365}y ago" }
        // Time-of-day fit
        val short = v.durationMs < 25_000
        if ((hour < 11 && short) || (hour >= 20 && v.durationMs > 60_000)) s += 0.1f
        // Confidence penalty: uncertain brain ranks lower until L1/L2 done
        s *= (0.7f + v.confidence * 0.3f)
        // Anti-repeat-session: watched recently sinks
        if (v.id in recentIds) s *= 0.35f
        return FeedItem(v, s.coerceIn(0f, 1f), why)
    }

    private fun isOnThisDay(dateAddedSec: Long): Boolean {
        if (dateAddedSec <= 0) return false
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = dateAddedSec * 1000 }
        return now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) != then.get(Calendar.YEAR)
    }

    private companion object {
        /** How many similarly-scored clips are shuffled together. */
        const val BAND_SIZE = 12
    }
}
