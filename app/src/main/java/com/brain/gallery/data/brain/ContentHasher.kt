package com.brain.gallery.data.brain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.brain.gallery.data.vision.VisionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fingerprints a file from OUTSIDE the library — the "listen" step of
 * Content ID. The picked clip is sampled at three time offsets because any
 * single frame can lie: a re-encode, a dark intro, a watermark title card.
 * Three agreeing frames do not guarantee identity, but three frames that
 * never agree are an honest "not this one".
 *
 * Same dHash dialect as the library's own fingerprints; nothing leaves
 * the device.
 */
@Singleton
class ContentHasher @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun hashPicked(uri: Uri, isVideo: Boolean): List<Long> = withContext(Dispatchers.IO) {
        runCatching {
            if (isVideo) hashVideo(uri) else listOfNotNull(hashImage(uri))
        }.onFailure { Log.w(TAG, "hashing picked content failed", it) }
            .getOrDefault(emptyList())
    }

    /** What kind of picked content MediaStore says this is; images by default. */
    fun isMediaVideo(uri: Uri): Boolean = runCatching {
        ctx.contentResolver.getType(uri)?.startsWith("video/") == true
    }.getOrDefault(false)

    private fun hashVideo(uri: Uri): List<Long> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val durMs = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durMs <= 0) return emptyList()
            // Early, middle, late: a trim changes the ends, not the middle.
            val offsets = listOf(0.15f, 0.5f, 0.85f).map { (durMs * it).toLong() }
            return offsets.mapNotNull { offMs ->
                r.getFrameAtTime(offMs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { frame ->
                        runCatching {
                            VisionUtils.dHash(VisionUtils.downscaleMaxWidth(frame, 256))
                        }.getOrDefault(0L).also { if (frame is Bitmap && !frame.isRecycled) frame.recycle() }
                    }
            }
        } finally {
            runCatching { r.release() }
        }
    }

    private fun hashImage(uri: Uri): Long? {
        // Two-pass decode with an inSampleSize cap: big camera originals
        // must not be loaded twice into memory to hash once.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val hash = runCatching { VisionUtils.dHash(bmp) }.getOrDefault(0L)
        bmp.recycle()
        return hash
    }

    companion object { private const val TAG = "hash" }
}
