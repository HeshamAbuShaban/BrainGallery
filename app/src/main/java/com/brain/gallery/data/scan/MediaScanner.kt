package com.brain.gallery.data.scan

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedVideo(
    val id: Long, val uri: String, val displayName: String, val durationMs: Long,
    val sizeBytes: Long, val dateAddedSec: Long, val folderName: String,
    val width: Int, val height: Int
)

@Singleton
class MediaScanner @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun scan(): List<ScannedVideo> = withContext(Dispatchers.IO) { query(null) }

    /** Delta rescan: only the ids a ContentObserver told us changed. */
    suspend fun scanIds(ids: List<Long>): List<ScannedVideo> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else query(ids)
    }

    private fun collection() =
        if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun query(ids: List<Long>?): List<ScannedVideo> {
        val out = mutableListOf<ScannedVideo>()
        val coll = collection()
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT
        )
        val selection = if (ids == null) null else ids.joinToString(",") { "_id IN ($it)" }
        try {
            ctx.contentResolver.query(coll, proj, selection, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(proj[0])
                val iName = c.getColumnIndexOrThrow(proj[1])
                val iDur = c.getColumnIndexOrThrow(proj[2])
                val iSize = c.getColumnIndexOrThrow(proj[3])
                val iAdded = c.getColumnIndexOrThrow(proj[4])
                val iRel = try { c.getColumnIndexOrThrow(proj[5]) } catch (_: Exception) { -1 }
                val iW = try { c.getColumnIndexOrThrow(proj[6]) } catch (_: Exception) { -1 }
                val iH = try { c.getColumnIndexOrThrow(proj[7]) } catch (_: Exception) { -1 }
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val rel: String = if (iRel >= 0) c.getString(iRel) ?: "" else ""
                    out += ScannedVideo(
                        id = id,
                        uri = ContentUris.withAppendedId(coll, id).toString(),
                        displayName = c.getString(iName) ?: "video_$id",
                        durationMs = try { c.getLong(iDur) } catch (_: Exception) { 0L },
                        sizeBytes = try { c.getLong(iSize) } catch (_: Exception) { 0L },
                        dateAddedSec = try { c.getLong(iAdded) } catch (_: Exception) { 0L },
                        folderName = rel.trim('/').split("/").lastOrNull()
                            ?.takeIf { it.isNotEmpty() } ?: "Videos",
                        width = if (iW >= 0) try { c.getInt(iW) } catch (_: Exception) { 0 } else 0,
                        height = if (iH >= 0) try { c.getInt(iH) } catch (_: Exception) { 0 } else 0
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MediaStore query failed on ${collection()}", t)
        }
        Log.i(TAG, "scan returned ${out.size} rows")
        return out
    }

    companion object { private const val TAG = "MediaScanner" }
}
