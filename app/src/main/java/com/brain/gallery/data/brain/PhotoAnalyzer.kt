package com.brain.gallery.data.brain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.brain.gallery.data.vision.FaceAnalyzer
import com.brain.gallery.data.vision.FaceReading
import com.brain.gallery.data.vision.VisionUtils
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
    val smiles: Int,
    /** dHash of the decoded pixels — the photo's fingerprint for Content ID. */
    val phash: Long = 0
)

/**
 * The photo pass — deliberately lighter than the video cascade. One upright
 * decode (512px cap) for the senses, one small unrotated decode for the
 * Content ID fingerprint, one ML Kit face read, one label read. No frames,
 * no embeddings, no prosody; photos are their own medium with their own
 * budget.
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
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(PhotoPolicy.ML_FLOOR)
                .build()
        )
    }

    suspend fun analyze(uri: String, folder: String, name: String): PhotoReading? =
        withContext(Dispatchers.IO) {
            val decoded = decode(uri) ?: return@withContext null
            val bmp = decoded.bmp
            try {
                // Content ID compares against ContentHasher's picked-image
                // hash, which is still a BitmapFactory read with EXIF ignored.
                // The stored fingerprint must stay in that same dialect or a
                // portrait photo hashes as a rotation of itself and every
                // picked portrait becomes "new to you". When the pixels are
                // already upright, the legacy decode is repeated for the hash
                // alone; a small decode is cheaper than a broken match.
                val phash = if (decoded.upright) fingerprint(uri)
                else runCatching { VisionUtils.dHash(bmp) }.getOrDefault(0L)
                // Sharpness is not a column on a photo, but it is evidence:
                // a decode with no detail must not hand its label a confident
                // voice. Same mush bar L1a uses before trusting a face crop.
                val sharpness = runCatching { VisionUtils.brennerSharpness(bmp) }
                    .getOrDefault(0f)
                val image = InputImage.fromBitmap(bmp, 0)

                val faceReading = runCatching { faces.analyze(bmp) }.getOrNull()
                    ?: FaceReading(0, 0, emptyList())

                val labels = runCatching {
                    labeler.process(image).await()
                }.getOrNull() ?: emptyList()
                val solid = labels.filter { it.confidence >= PhotoPolicy.ML_FLOOR }
                    .sortedByDescending { it.confidence }
                    .map { PhotoPolicy.Label(it.text, it.confidence) }

                val verdict = PhotoPolicy.decide(folder, name, solid, sharpness)
                PhotoReading(
                    category = verdict.category,
                    tags = verdict.tags,
                    about = verdict.about,
                    // A face says "someone was there" even when the category
                    // never resolves; it does not invent a category.
                    confidence = maxOf(
                        verdict.confidence,
                        if (faceReading.count > 0) 0.5f else 0f
                    ),
                    faces = faceReading.count,
                    smiles = faceReading.smiles,
                    phash = phash
                )
            } finally {
                bmp.recycle()
            }
        }

    private data class Decoded(val bmp: Bitmap, val upright: Boolean)

    /**
     * One upright decode, downsampled toward a ~512px long edge.
     *
     * API 28+ goes through ImageDecoder, which applies EXIF orientation. The
     * old BitmapFactory path handed a portrait camera shot to the face
     * detector on its side — a rotated face is a missed face, and a rotated
     * label is a wrong label. Below 28, or if the decoder refuses the file,
     * BitmapFactory still reads pixels, just without the rotation.
     */
    private fun decode(uri: String): Decoded? {
        val target = 512
        val parsed = Uri.parse(uri)
        if (Build.VERSION.SDK_INT >= 28) {
            val upright = runCatching {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(ctx.contentResolver, parsed)
                ) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val longest = maxOf(w, h)
                    // The cap is a cap, never an upscale.
                    if (longest > target) {
                        val scale = target.toFloat() / longest
                        decoder.setTargetSize(
                            (w * scale).toInt().coerceAtLeast(1),
                            (h * scale).toInt().coerceAtLeast(1)
                        )
                    }
                    // ML Kit reads these pixels back on the CPU; a hardware
                    // bitmap is not guaranteed to be readable there.
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                }
            }.getOrNull()
            if (upright != null) return Decoded(upright, upright = true)
        }
        return decodeLegacy(parsed, target)?.let { Decoded(it, upright = false) }
    }

    /** The fingerprint dHash, in ContentHasher's exact (unrotated) dialect. */
    private fun fingerprint(uri: String): Long {
        val bmp = decodeLegacy(Uri.parse(uri), 512) ?: return 0L
        return try {
            runCatching { VisionUtils.dHash(bmp) }.getOrDefault(0L)
        } finally {
            bmp.recycle()
        }
    }

    /** The pre-28 fallback; also the safety net for files ImageDecoder rejects. */
    private fun decodeLegacy(parsed: Uri, target: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns a null bitmap — that is the API,
        // not a failure. Guard the stream, never the result.
        val boundsStream = ctx.contentResolver.openInputStream(parsed)
            ?: return@runCatching null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= target) { sample *= 2; longest /= 2 }
        ctx.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(
                it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}

/**
 * The one place a photo's two senses are reconciled: the words the file was
 * given (L0, free) and the labels the pixels earned (L1, budgeted). Videos
 * already work this way in [Level0Analyzer] — a folder named "Birthday 2024"
 * is a stronger statement than a generic "Dog 0.62" label, and a photo that
 * lived in a birthday folder should not become a pet because the cake had a
 * dog in it. Both claims survive as tags, so search can still find the dog.
 *
 * Deliberately pure: no Context, no Bitmap, no clock. The fusion rule can be
 * read, argued with, and pinned by tests without a device.
 */
object PhotoPolicy {
    /** Same L0 confidences as the video pass, so one vocabulary means one thing. */
    const val NAME_CONF = 0.85f
    const val FOLDER_CONF = 0.70f

    /** The labeler's own bar; below it a label is a guess in a lab coat. */
    const val ML_FLOOR = 0.60f

    /** Below this a decode has no detail worth trusting a label on (L1a's bar). */
    const val MUSH = 8f

    /** Tags stay a short, searchable sentence, like everywhere else. */
    const val MAX_TAGS = 6

    data class Label(val text: String, val confidence: Float)

    data class Verdict(
        val category: String,
        val tags: String,
        val about: String,
        val confidence: Float
    )

    /**
     * Category precedence is name, then folder, then the pixels. The ontology
     * categories are curated buckets ("birthday", "trip"); a raw label like
     * "dress" is a useful tag but a poor shelf, so it only becomes the
     * category when neither the name, the folder, nor the labels themselves
     * map into the ontology.
     */
    fun decide(
        folder: String,
        name: String,
        labels: List<Label>,
        sharpness: Float = 0f
    ): Verdict {
        // A decode with no detail earns no label: the pixels were not
        // evidence, and brainLevel never retries a row, so a label invented
        // now would be permanent. 0 means "not measured" and stays neutral.
        val mush = sharpness > 0f && sharpness < MUSH
        val solid = if (mush) emptyList()
        else labels.filter { it.confidence >= ML_FLOOR }
            .sortedByDescending { it.confidence }

        val nameCat = CategoryOntology.matchFile(name)
        val folderCat = CategoryOntology.matchFile(folder)
        // The first (strongest) label that speaks the ontology's language
        // carries its own confidence — not the top label's, which may be a
        // different word entirely.
        val mapped = solid.firstNotNullOfOrNull { label ->
            CategoryOntology.matchFile(label.text)?.let { cat -> cat to label }
        }

        val winner = when {
            nameCat != null -> nameCat
            folderCat != null -> folderCat
            mapped != null -> mapped.first
            solid.isNotEmpty() -> normalize(solid.first().text)
            else -> "unknown"
        }
        val confidence = when {
            nameCat != null -> NAME_CONF
            folderCat != null -> FOLDER_CONF
            mapped != null -> mapped.second.confidence
            solid.isNotEmpty() -> solid.first().confidence
            else -> 0f
        }

        // Both senses merge into tags: the curated bucket and the specific
        // words, deduped, capped. A losing claim stays findable.
        val tagSet = LinkedHashSet<String>()
        solid.take(5).forEach { tagSet += normalize(it.text) }
        nameCat?.let(tagSet::add)
        folderCat?.let(tagSet::add)
        mapped?.first?.let(tagSet::add)
        if (winner != "unknown") tagSet += winner

        val about = when {
            winner == "unknown" -> ""
            // Pixels led and there was no human word: say the specific word.
            nameCat == null && folderCat == null ->
                pretty(solid.first().text)
            folder.isBlank() -> pretty(winner)
            else -> "${pretty(winner)} • $folder"
        }

        return Verdict(
            category = winner,
            tags = tagSet.take(MAX_TAGS).joinToString(","),
            about = about,
            confidence = confidence
        )
    }

    /**
     * L0's junk price, with the one signal a filename cannot always carry:
     * shape. A phone screen's pixels are taller than any camera's and land
     * on the same handful of resolutions, so a 20:9 still that no camera
     * named is a screenshot or an export, not a memory. Exact screen pixels
     * reach the 0.9 bar on purpose — the image pass then spends its budget
     * on photographs instead of on screenshots that never change.
     */
    fun junkScore(folder: String, name: String, width: Int, height: Int): Float {
        var junk = CategoryOntology.junkScore(folder, name)
        if (utilityNamed(name) || utilityNamed(folder)) junk = maxOf(junk, 0.75f)
        return maxOf(junk, screenShape(name, width, height))
    }

    /**
     * 0 when there is no shape evidence, 0.9 for exact phone-screen pixels,
     * 0.7 for the tall-but-unlisted shapes. Camera-named files are exempt:
     * a panorama is wide, and a 16:9 camera still can share a screen size.
     */
    private fun screenShape(name: String, width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 0f
        if (cameraNamed(name)) return 0f
        val long = maxOf(width, height)
        val short = minOf(width, height)
        val aspect = long.toFloat() / short
        return when {
            short in SCREEN_WIDTHS && long in SCREEN_LONGS &&
                aspect in 1.6f..2.5f -> 0.9f
            aspect in 1.9f..2.45f && short in 600..1600 -> 0.7f
            else -> 0f
        }
    }

    private fun cameraNamed(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("img_") || n.startsWith("dsc") ||
            n.startsWith("pxl_") || n.startsWith("pano") ||
            n.startsWith("vid_") || n.startsWith("mmvideo")
    }

    /**
     * Scans, receipts and screenshots are records, not moments. Tokens are
     * matched whole — "Scandinavia" is a trip, "scan" is a document.
     */
    private fun utilityNamed(path: String): Boolean {
        val tokens = path.lowercase().substringBeforeLast('.')
            .split(Regex("[^\\p{L}\\p{N}]+"))
        return tokens.any { it in UTILITY_TOKENS }
    }

    private val UTILITY_TOKENS = setOf(
        "screenshot", "screenshots", "screencap", "screencaptures",
        "scan", "scans", "scanned", "receipt", "receipts",
        "invoice", "invoices", "barcode", "qrcode"
    )

    // Every screen ships its own size; this is the union of the widths and
    // long edges Android phones have used, not a guess at one device.
    private val SCREEN_WIDTHS = setOf(720, 1080, 1170, 1179, 1242, 1284, 1440)
    private val SCREEN_LONGS = setOf(
        1280, 1520, 1600, 1920, 2160, 2208, 2340, 2400, 2436,
        2520, 2532, 2560, 2668, 2778, 2880, 3040, 3120, 3200, 3840
    )

    private fun normalize(label: String): String =
        label.lowercase().trim().replace(Regex("\\s+"), "_").trim('_')

    private fun pretty(word: String): String =
        word.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
