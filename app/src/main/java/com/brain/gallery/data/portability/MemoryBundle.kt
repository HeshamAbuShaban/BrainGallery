package com.brain.gallery.data.portability

import android.content.Context
import android.net.Uri
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.GroupOverrideEntity
import com.brain.gallery.data.local.NotInterestedEntity
import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.SettingsStore
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
 * merges, favourites, watch history, not-interested, chosen settings, renamed
 * groups) so wiping the app costs the user nothing that matters. Machine data
 * (embeddings, hashes) is intentionally excluded — it is recomputed from the
 * media files by the indexer.
 */
@Singleton
class MemoryBundle @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: BrainDatabase,
    private val store: SettingsStore
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
        root.put("version", 2)
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

        // Bundles v2: the taught settings (feed weights included) and every
        // group name the user chose travel too. A restored brain starts
        // already knowing what the viewer taught it.
        root.put("settings", JSONObject(store.load().toJson()))
        val overrides = JSONArray()
        for (o in db.supportDao().groupOverrides()) {
            overrides.put(JSONObject().apply {
                put("groupId", o.groupId); put("name", o.name); put("updatedAt", o.updatedAt)
            })
        }
        root.put("groupOverrides", overrides)

        // Taught identity lessons: the user's own "this clip is not that
        // person" corrections. Human layer — without them a restore would
        // politely re-adopt exactly the vectors the user pushed away.
        val lessonsOut = JSONArray()
        for (l in db.personDao().allLessons()) {
            lessonsOut.put(JSONObject().apply {
                put("videoId", l.videoId); put("personId", l.personId)
            })
        }
        root.put("lessons", lessonsOut)

        root.toString()
    }

    /** Restore human layer. Videos/vectors are re-derived by the indexer afterwards. */
    suspend fun importJson(text: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        var people = 0; var events = 0; var favs = 0; var overrides = 0; var lessons = 0
        var settingsRestored = false
        // One read for the whole import: a non-empty local human layer means
        // the bundle's ids cannot be trusted below.
        val locals = db.personDao().allPersons()
        root.optJSONArray("persons")?.let { arr ->
            // Names the user fixed locally, by lower-case form for matching.
            val claimedNames = locals.filter { it.verified && it.name.isNotBlank() }
                .associateBy { it.name.lowercase() }
            val adoptables = locals.filter { it.name.isBlank() && it.suggestedName.isNotBlank() }
                .associateBy { it.suggestedName.lowercase() }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (locals.isEmpty()) {
                    // No local human layer yet: restore ids exactly. Vector
                    // rows are re-derived by the indexer later, so the ids'
                    // integrity belongs to this bundle.
                    db.personDao().insertPerson(PersonEntity(
                        id = o.optInt("id"), name = o.optString("name"),
                        verified = o.optBoolean("verified"), createdAt = System.currentTimeMillis()
                    ))
                } else {
                    // Ids can never be trusted across libraries: person #3 in
                    // the bundle may be someone entirely different from
                    // person #3 here, and a name silently pasted onto the
                    // wrong cluster is not repairable — a lost name is.
                    // Merge only on a verified name; identities that would
                    // be new rows are left out (they carry no vectors, so a
                    // zombie person row would never attach to a face again).
                    val name = o.optString("name")
                    if (name.isNotBlank() && claimedNames[name.lowercase()] == null) {
                        // A name nobody here claims: if a local cluster was
                        // suggested exactly this name, adopt it there.
                        adoptables[name.lowercase()]?.let { a ->
                            db.personDao().rename(a.id, name)
                        }
                    }
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
            for (i in 0 until arr.length()) {
                db.videoDao().setFavorite(arr.optLong(i), true); favs++
            }
        }
        root.optJSONArray("notInterested")?.let { arr ->
            for (i in 0 until arr.length()) db.supportDao().addNotInterested(
                NotInterestedEntity(arr.optLong(i), System.currentTimeMillis()))
        }
        // v2 additions — absent in older bundles, in which case current
        // settings and group names stand.
        root.optJSONObject("settings")?.let { s ->
            store.save(EngineSettings.fromJson(s.toString())); settingsRestored = true
        }
        root.optJSONArray("groupOverrides")?.let { arr ->
            val existing = db.supportDao().groupOverrides().associateBy { it.groupId }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val groupId = o.optString("groupId")
                if (groupId.isBlank() || existing.containsKey(groupId)) continue
                db.supportDao().setGroupName(
                    groupId, o.optString("name"), o.optLong("updatedAt"))
                overrides++
            }
        }
        // Taught lessons travel only when this restore can honor the bundle's
        // person ids — a fresh install, where ids were restored exactly. On a
        // lived-in library the ids would point at strangers, and a lesson
        // about nobody is worse than none.
        root.optJSONArray("lessons")?.let { arr ->
            if (locals.isEmpty()) {
                val items = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    com.brain.gallery.data.local.IdentityLessonEntity(
                        videoId = o.optLong("videoId"), personId = o.optInt("personId"),
                        createdAt = System.currentTimeMillis())
                }
                if (items.isNotEmpty()) {
                    db.personDao().insertLessons(items)
                    lessons = items.size
                }
            }
        }
        buildString {
            append("Imported $people people, $events watch events, $favs favorites")
            if (settingsRestored) append(", your settings")
            if (overrides > 0) append(", $overrides group names")
            if (lessons > 0) append(", $lessons taught lessons")
        }
    }

    fun fileUri(f: File): Uri = Uri.fromFile(f)
}
