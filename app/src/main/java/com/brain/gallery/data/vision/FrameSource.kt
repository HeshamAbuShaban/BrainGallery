package com.brain.gallery.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Frame decoding is the real cost of indexing. Centralize it. */
@Singleton
class FrameSource @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun at(uri: String, atUs: Long): Bitmap? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, Uri.parse(uri))
            retriever.getFrameAtTime(atUs)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    suspend fun mid(uri: String, durationMs: Long): Bitmap? =
        at(uri, (durationMs * 1000L / 2).coerceAtLeast(500_000L))
}

/**
 * Conditional frame cache (Tier 3). Persists a frame ONLY for videos escalated to
 * deep analysis, which are the ones decoded 3-4x. Bounded LRU so disk never grows.
 */
@Singleton
class FrameCache @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val dir: File by lazy { File(ctx.cacheDir, "frames").apply { mkdirs() } }

    private fun file(videoId: Long) = File(dir, "$videoId.jpg")

    suspend fun put(videoId: Long, bmp: Bitmap) = withContext(Dispatchers.IO) {
        try {
            file(videoId).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            prune()
        } catch (_: Exception) {}
    }

    suspend fun get(videoId: Long): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val f = file(videoId)
            if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Exception) { null }
    }

    fun evict(videoId: Long) { try { file(videoId).delete() } catch (_: Exception) {} }

    suspend fun prune(maxFiles: Int = 300) = withContext(Dispatchers.IO) {
        try {
            val files = dir.listFiles() ?: return@withContext
            if (files.size <= maxFiles) return@withContext
            files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
        } catch (_: Exception) {}
    }

    fun sizeBytes(): Long = try {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    } catch (_: Exception) { 0L }
}
