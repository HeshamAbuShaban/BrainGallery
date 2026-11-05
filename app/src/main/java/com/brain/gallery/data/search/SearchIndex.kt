package com.brain.gallery.data.search

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.engine.FtsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The retrieval index, in plain SQLite FTS4.
 *
 * Room deliberately knows nothing about these two tables: virtual-table DDL
 * that Room didn't generate is schema Room will reject, so the tables live
 * outside Room's validation and are created by the v10 migration (upgrade) and
 * the onCreate callback (fresh install), then read through [SearchFtsDao]'s
 * raw query. The stored text is the same document the MemoryDoc builder writes
 * in Kotlin — name, folder, category, tags, about, voice tags, who (the person
 * this clip belongs to), year-month — so the FTS hop *narrows* and the Kotlin
 * side keeps *ranking*, exactly as before, on candidates instead of the
 * library. Recall from the index, precision from the scorer you can explain.
 */
object FtsSchema {

    const val VIDEO_FTS_DDL = "CREATE VIRTUAL TABLE IF NOT EXISTS `video_fts` USING FTS4(`text`)"
    const val PHOTO_FTS_DDL = "CREATE VIRTUAL TABLE IF NOT EXISTS `photo_fts` USING FTS4(`text`)"

    /**
     * The document, written in SQL so Kotlin and SQLite never disagree about
     * what a row says. Person names ride the same row as a scalar subquery, so
     * a rename anywhere in the library stays one rebuild away.
     */
    const val VIDEO_DOC =
        "lower(" +
        "ifnull(v.`displayName`,'') || ' ' || ifnull(v.`folderName`,'') || ' ' || " +
        "ifnull(v.`category`,'') || ' ' || ifnull(v.`tags`,'') || ' ' || " +
        "ifnull(v.`about`,'') || ' ' || ifnull(v.`prosodyTags`,'') || ' ' || " +
        "ifnull(v.`transcript`,'') || ' ' || " +
        "ifnull((SELECT trim(ifnull(pp.`suggestedName`,'') || ' ' || ifnull(pp.`name`,'')) " +
        "FROM `persons` pp WHERE pp.`id` = v.`personId`), '') || ' ' || " +
        "strftime('%Y-%m', v.`dateAddedSec`, 'unixepoch'))"

    const val PHOTO_DOC =
        "lower(" +
        "ifnull(p.`displayName`,'') || ' ' || ifnull(p.`folderName`,'') || ' ' || " +
        "ifnull(p.`category`,'') || ' ' || ifnull(p.`tags`,'') || ' ' || " +
        "ifnull(p.`about`,'') || ' ' || " +
        "strftime('%Y-%m', p.`dateAddedSec`, 'unixepoch'))"

    const val VIDEO_FILL =
        "INSERT INTO `video_fts`(`rowid`, `text`) SELECT v.`id`, $VIDEO_DOC FROM `videos` v"
    const val PHOTO_FILL =
        "INSERT INTO `photo_fts`(`rowid`, `text`) SELECT p.`id`, $PHOTO_DOC FROM `photos` p"

    /** Prefix MATCH over one table; null when the query holds no usable terms. */
    fun videoMatch(raw: String): SimpleSQLiteQuery? = match("video_fts", raw)
    fun photoMatch(raw: String): SimpleSQLiteQuery? = match("photo_fts", raw)

    private fun match(table: String, raw: String): SimpleSQLiteQuery? {
        val terms = FtsQuery.matchTerms(raw) ?: return null
        return SimpleSQLiteQuery(
            "SELECT `rowid` FROM `$table` WHERE `$table` MATCH ? LIMIT 500",
            arrayOf(terms))
    }
}

/** One raw hop into FTS, table picked by whichever query was built. */
@Dao
interface SearchFtsDao {
    @RawQuery
    suspend fun ids(q: SimpleSQLiteQuery): List<Long>
}

/**
 * Keeps the FTS tables in step with content. Rebuilds are wholesale and cheap:
 * one DELETE plus one INSERT..SELECT per table, well under a second at ten
 * thousand rows — simple enough to be honest about what is stored, which is
 * worth more here than an incrementally maintained index nobody can prove.
 */
@Singleton
class SearchIndexer @Inject constructor(private val db: BrainDatabase) {

    /** Rebuild both tables from current content. */
    suspend fun rebuildAll() = withContext(Dispatchers.IO) {
        db.withTransaction {
            val w = db.openHelper.writableDatabase
            w.execSQL("DELETE FROM `video_fts`")
            w.execSQL(FtsSchema.VIDEO_FILL)
            w.execSQL("DELETE FROM `photo_fts`")
            w.execSQL(FtsSchema.PHOTO_FILL)
        }
    }

    /** Row-count guard: media added or removed without a run are caught here. */
    suspend fun ensureFresh() = withContext(Dispatchers.IO) {
        val w = db.openHelper.writableDatabase
        val inStep = runCatching {
            w.compileStatement("SELECT COUNT(*) FROM `videos`").simpleQueryForLong() ==
                w.compileStatement("SELECT COUNT(*) FROM `video_fts`").simpleQueryForLong() &&
            w.compileStatement("SELECT COUNT(*) FROM `photos`").simpleQueryForLong() ==
                w.compileStatement("SELECT COUNT(*) FROM `photo_fts`").simpleQueryForLong()
        }.getOrDefault(false)
        if (!inStep) rebuildAll()
    }

    /** Re-derive the documents for exactly these videos (e.g. after a rename). */
    suspend fun rebuildVideoIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.chunked(300).forEach { chunk ->
            replace(FtsSchema.VIDEO_DOC, chunk, "`video_fts`", "videos", "v")
        }
    }

    suspend fun rebuildPhotoIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.chunked(300).forEach { chunk ->
            replace(FtsSchema.PHOTO_DOC, chunk, "`photo_fts`", "photos", "p")
        }
    }

    private suspend fun replace(
        docExpr: String, ids: List<Long>, ftsTable: String, mediaTable: String, alias: String
    ) {
        val marks = ids.joinToString(",") { "?" }
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO $ftsTable(`rowid`, `text`) " +
                "SELECT $alias.`id`, $docExpr FROM `$mediaTable` $alias WHERE $alias.`id` IN ($marks)",
            ids.toTypedArray())
    }
}
