package com.brain.gallery.domain

import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.domain.engine.TasteProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the learned taste profile in `app_kv`. Deliberately separate from
 * [EngineSettings]: the settings are the user's hand on the knobs; this is the
 * brain's own notebook, filled in by watching, never by a slider.
 */
@Singleton
class TasteStore @Inject constructor(private val db: BrainDatabase) {
    private val dao get() = db.supportDao()

    suspend fun load(): TasteProfile = TasteProfile.fromJson(dao.get(KEY))
    suspend fun save(p: TasteProfile) {
        if (p.buckets.isEmpty()) dao.put(KEY, "")
        else dao.put(KEY, p.toJson())
    }

    companion object {
        const val KEY = "taste.profile"
        /** Highest watch_events.rowId already folded in; exactly-once by construction. */
        const val CURSOR = "taste.cursor"
    }
}

/**
 * The one writer of the taste profile, callable from anywhere (the feed VM
 * after a watch, the end-of-run maintenance pass), with exactly-once folding:
 * a cursor in `app_kv` records the last consumed watch_events rowId, so an
 * event can never be folded twice no matter who calls, how often, or how
 * many processes race.
 */
@Singleton
class TasteLearner @Inject constructor(private val db: BrainDatabase) {
    private val mutex = Mutex()

    suspend fun learnNewEvents() = mutex.withLock {
        val cursor = db.supportDao().get(TasteStore.CURSOR)?.toLongOrNull() ?: 0L
        val events = db.watchDao().afterRowId(cursor)
        if (events.isEmpty()) return@withLock
        val stored = db.supportDao().get(TasteStore.KEY)
        val folded = TasteProfile.fromJson(stored)
            .fold(events, db.videoDao().getAllSync().associateBy { it.id }, System.currentTimeMillis())
        db.supportDao().put(TasteStore.KEY, folded.buckets.let { if (it.isEmpty()) "" else folded.toJson() })
        db.supportDao().put(TasteStore.CURSOR, events.maxOf { it.rowId }.toString())
    }
}
