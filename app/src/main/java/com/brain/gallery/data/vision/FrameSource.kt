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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frame decoding is the real cost of indexing, and the one place that can wedge
 * the whole pipeline: MediaMetadataRetriever.getFrameAtTime can block for a very
 * long time on long-GOP or damaged files. Three defences:
 *
 *  1. OPTION_CLOSEST_SYNC — seek to the nearest sync frame instead of decoding
 *     forward from the start. Turns a multi-second stall into milliseconds.
 *  2. A hard timeout, so the caller always moves on.
 *  3. A small pool, so one pathological file cannot block every other frame.
 */
@Singleton
class FrameSource @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val pool = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "brain-frame").apply { isDaemon = true }
    }

    suspend fun at(uri: String, atUs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val job = pool.submit<Bitmap?> { extract(uri, atUs) }
        try {
            job.get(EXTRACT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        } finally {
            job.cancel(true)
        }
    }

    suspend fun mid(uri: String, durationMs: Long): Bitmap? =
        at(uri, (durationMs * 1000L / 2).coerceAtLeast(400_000L))

    private fun extract(uri: String, atUs: Long): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, Uri.parse(uri))
            retriever.getFrameAtTime(
                atUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever?.release() } catch (_: Throwable) {}
        }
    }

    companion object { const val EXTRACT_TIMEOUT_MS = 8_000L }
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
