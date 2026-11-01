package com.brain.gallery.data.brain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.brain.gallery.data.vision.FaceAnalyzer
import com.brain.gallery.data.vision.FaceReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PhotoReading(
    val category: String,
    val tags: String,
    val about: String,
    val confidence: Float,
    val faces: Int,
    val smiles: Int
)

/**
 * The photo pass — deliberately lighter than the video cascade. One
 * downscaled decode (512px cap), one ML Kit face read, one label read. No
 * frames, no embeddings, no prosody; photos are their own medium with their
 * own budget.
 *
 * Reads are squircles-down of the video pipeline's vocabulary: same
 * FaceAnalyzer, same labeler, same ontology. The brain stays one brain.
 */
@Singleton
class PhotoAnalyzer @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val faces: FaceAnalyzer
) {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(uri: String): PhotoReading? = withContext(Dispatchers.IO) {
        val bmp = decode(uri) ?: return@withContext null
        try {
            val image = InputImage.fromBitmap(bmp, 0)

            val faceReading = runCatching { faces.analyze(bmp) }.getOrNull()
                ?: FaceReading(0, 0, emptyList())

            val labels = runCatching {
                labeler.process(image).await()
            }.getOrNull() ?: emptyList()
            val solid = labels.filter { it.confidence >= 0.6f }.sortedByDescending { it.confidence }
            val tagList = solid.take(5).map { it.text.lowercase().replace(" ", "_") }
            val top = solid.firstOrNull()?.text?.lowercase()?.replace(" ", "_")

            // The filename guess leads when the pixels agree with it, the
            // pixels lead when the filename had nothing to say.
            val reading = PhotoReading(
                category = top ?: "unknown",
                tags = tagList.joinToString(","),
                about = top?.replace("_", " ") ?: "",
                confidence = (solid.firstOrNull()?.confidence ?: 0f)
                    .coerceAtLeast(if (faceReading.count > 0) 0.5f else 0f),
                faces = faceReading.count,
                smiles = faceReading.smiles
            )
            reading
        } finally {
            bmp.recycle()
        }
    }

    /** One decode, downsampled toward a ~512px long edge. */
    private fun decode(uri: String): Bitmap? = runCatching {
        val target = 512
        val parsed = android.net.Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return@runCatching null
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= target) { sample *= 2; longest /= 2 }
        ctx.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
