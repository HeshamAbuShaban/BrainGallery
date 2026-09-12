package com.brain.gallery.data.scan

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.brain.gallery.data.brain.Level0Analyzer
import com.brain.gallery.data.local.VideoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun scan(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val out = mutableListOf<VideoEntity>()
        val coll = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT
        )
        ctx.contentResolver.query(coll, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
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
                val uri = ContentUris.withAppendedId(coll, id).toString()
                val name = c.getString(iName) ?: "video_$id"
                val rel: String = if (iRel >= 0) c.getString(iRel) ?: "" else ""
                val folder = rel.trim('/').split("/").lastOrNull()?.takeIf { it.isNotEmpty() } ?: "Videos"
                val dur = try { c.getLong(iDur) } catch (_: Exception) { 0L }
                val l0 = Level0Analyzer.analyze(name, folder, dur)
                out += VideoEntity(
                    id = id, uri = uri, displayName = name, durationMs = dur,
                    sizeBytes = try { c.getLong(iSize) } catch (_: Exception) { 0L },
                    dateAddedSec = try { c.getLong(iAdded) } catch (_: Exception) { 0L },
                    folderName = folder,
                    width = if (iW >= 0) try { c.getInt(iW) } catch (_: Exception) { 0 } else 0,
                    height = if (iH >= 0) try { c.getInt(iH) } catch (_: Exception) { 0 } else 0,
                    category = l0.category, tags = l0.tags.joinToString(","),
                    about = l0.about, confidence = l0.confidence,
                    brainLevel = 0, junkScore = l0.junkScore
                )
            }
        }
        out
    }
}
