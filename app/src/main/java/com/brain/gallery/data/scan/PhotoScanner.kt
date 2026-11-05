package com.brain.gallery.data.scan

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.data.brain.CategoryOntology
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedPhoto(
    val id: Long, val uri: String, val name: String, val folder: String,
    val dateAddedSec: Long, val width: Int, val height: Int, val mime: String = ""
)

/**
 * The image side of MediaStore. Same contract as [MediaScanner]: a full scan
 * or a delta by id, rows the service can mark-and-sweep against the table.
 * The L0 brain fields (category guess, junk score from folder/name tokens)
 * are applied right here — reading a filename is free.
 */
@Singleton
class PhotoScanner @Inject constructor(@ApplicationContext private val ctx: Context) {

    private fun collection() =
        if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    suspend fun scan(): List<ScannedPhoto> = withContext(Dispatchers.IO) { query(null) }

    suspend fun scanIds(ids: List<Long>): List<ScannedPhoto> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else query(ids)
    }

    private fun query(deltaIds: List<Long>?): List<ScannedPhoto> {
        val out = ArrayList<ScannedPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE)
        val sel: String?
        val args: Array<String>?
        if (deltaIds != null) {
            sel = "${MediaStore.Images.Media._ID} IN (${deltaIds.joinToString(",") { "?" }})"
            args = deltaIds.map { it.toString() }.toTypedArray()
        } else { sel = null; args = null }
        ctx.contentResolver.query(collection(), projection, sel, args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val name = c.getString(iName) ?: continue
                val folder = c.getString(iPath)?.trimEnd('/')?.substringAfterLast('/') ?: ""
                out += ScannedPhoto(
                    id = id,
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString()).build().toString(),
                    name = name, folder = folder,
                    dateAddedSec = c.getLong(iDate),
                    width = c.getInt(iW), height = c.getInt(iH),
                    mime = c.getString(iMime) ?: "")
            }
        }
        return out
    }
}

/** L0 for photos: folder/name tokens decide the first guess, for free. */
fun ScannedPhoto.toEntity(): PhotoEntity {
    val junk = CategoryOntology.junkScore(folder, name)
    val cat = CategoryOntology.matchFile("$folder $name") ?: "unknown"
    val animated = mime == "image/gif" || mime == "image/webp" ||
        name.lowercase().endsWith(".gif")
    return PhotoEntity(
        id = id, uri = uri, displayName = name, folderName = folder,
        dateAddedSec = dateAddedSec, width = width, height = height,
        category = cat, junkScore = junk, confidence = if (cat == "unknown") 0f else 0.6f,
        animated = animated
    )
}
