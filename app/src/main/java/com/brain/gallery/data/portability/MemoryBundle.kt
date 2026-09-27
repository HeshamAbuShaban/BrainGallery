package com.brain.gallery.data.portability

import android.content.Context
import android.net.Uri
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.NotInterestedEntity
import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.data.local.WatchEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1 portability. Exports ONLY the irreplaceable human layer (names,
 * merges, favourites, watch history, not-interested) so wiping the app costs the
 * user nothing that matters. Machine data (embeddings, hashes) is intentionally
 * excluded — it is recomputed from the media files by the indexer.
 */
@Singleton
class MemoryBundle @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: BrainDatabase
) {

    suspend fun exportToFile(): File = withContext(Dispatchers.IO) {
        val json = exportJson()
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "memory-bundle.json")
        f.writeText(json)
        f
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val people = JSONArray()
        for (p in db.personDao().allPersons()) {
            people.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("verified", p.verified)
            })
        }
        root.put("persons", people)

        val events = JSONArray()
        for (e in db.watchDao().all()) {
            events.put(JSONObject().apply {
                put("videoId", e.videoId); put("atMs", e.atMs)
                put("completion", e.completion.toDouble()); put("skipped", e.skipped)
                put("hour", e.hourOfDay)
            })
        }
        root.put("watchEvents", events)

        val favs = JSONArray()
        for (v in db.videoDao().getAllSync()) {
            if (v.isFavorite) favs.put(v.id)
        }
        root.put("favorites", favs)

        val ni = JSONArray()
        for (e in db.supportDao().notInterested()) ni.put(e.videoId)
        root.put("notInterested", ni)

        root.toString()
    }

    /** Restore human layer. Videos/vectors are re-derived by the indexer afterwards. */
    suspend fun importJson(text: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        var people = 0; var events = 0
        root.optJSONArray("persons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val existing = db.personDao().allPersons().firstOrNull { it.id == o.optInt("id") }
                if (existing == null) {
                    db.personDao().insertPerson(PersonEntity(
                        id = o.optInt("id"), name = o.optString("name"),
                        verified = o.optBoolean("verified"), createdAt = System.currentTimeMillis()
                    ))
                } else if (o.optString("name").isNotBlank() && !existing.verified) {
                    db.personDao().rename(existing.id, o.optString("name"))
                }
                people++
            }
        }
        root.optJSONArray("watchEvents")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.watchDao().insert(WatchEventEntity(
                    videoId = o.optLong("videoId"), atMs = o.optLong("atMs"),
                    completion = o.optDouble("completion", 0.0).toFloat(),
                    skipped = o.optBoolean("skipped"), hourOfDay = o.optInt("hour")
                ))
                events++
            }
        }
        root.optJSONArray("favorites")?.let { arr ->
            for (i in 0 until arr.length()) db.videoDao().setFavorite(arr.optLong(i), true)
        }
        root.optJSONArray("notInterested")?.let { arr ->
            for (i in 0 until arr.length()) db.supportDao().addNotInterested(
                NotInterestedEntity(arr.optLong(i), System.currentTimeMillis()))
        }
        "Imported $people people, $events watch events"
    }

    fun fileUri(f: File): Uri = Uri.fromFile(f)
}
