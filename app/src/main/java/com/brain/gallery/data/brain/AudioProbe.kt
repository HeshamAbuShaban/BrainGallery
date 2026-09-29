package com.brain.gallery.data.brain

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * What the voice sounds like, measured rather than guessed.
 *
 * This reads the audio track and computes a handful of cheap acoustic features:
 * how much of the clip is speech rather than music or silence, roughly how high
 * the voice sits, how much that pitch moves, and how much the loudness varies.
 * Those together say a lot about delivery — a lecture and a heated argument both
 * have steady speech, but one has a narrow pitch range and the other does not.
 *
 * Deliberately no model and no network. Everything here is arithmetic on
 * decoded samples, which is why it can be turned on for a whole library without
 * anyone worrying about cost, and why the result is a set of readable tags
 * rather than an opaque embedding.
 */
data class Prosody(
    /** 0..1 share of sampled windows that look like someone talking. */
    val speechRatio: Float = 0f,
    /** Median fundamental frequency, Hz. 0 when nothing pitch-like was found. */
    val pitchHz: Float = 0f,
    /** Spread of the pitch, as a fraction of the median. Higher is more animated. */
    val pitchRange: Float = 0f,
    /** Loudness variation, normalised. High means emphatic or uneven. */
    val energyRange: Float = 0f,
    /** Short-window loudness peaks per second. A rough speaking-rate proxy. */
    val gesturesPerSec: Float = 0f,
    val sampled: Boolean = false
) {
    /**
     * Plain-language tags. The point is that a person can read these and agree or
     * disagree, which an embedding could never offer.
     */
    fun tags(): List<String> {
        if (!sampled) return emptyList()
        val out = mutableListOf<String>()
        if (speechRatio > 0.45f) {
            out += "speaking"
            when {
                pitchHz in 60f..165f -> out += "low voice"
                pitchHz in 165f..240f -> out += "steady voice"
                pitchHz >= 240f -> out += "high voice"
            }
            if (pitchRange > 0.28f) out += "expressive"
            else if (pitchRange < 0.10f) out += "flat delivery"
            if (energyRange > 0.55f) out += "emphatic"
            if (gesturesPerSec > 3.2f) out += "fast talker"
            else if (gesturesPerSec in 0.4f..1.6f) out += "unhurried"
        } else if (speechRatio < 0.12f) {
            out += "mostly music or ambience"
        }
        return out
    }

    /** A short sentence for the details sheet. */
    fun summary(): String {
        if (!sampled) return "audio not read"
        val pct = (speechRatio * 100).toInt()
        val pitch = if (pitchHz > 0f) "${pitchHz.toInt()} Hz" else "no clear pitch"
        return "$pct% speech, $pitch, pitch range ${(pitchRange * 100).toInt()}%"
    }
}

/**
 * Decodes a short window of the audio track and reduces it to [Prosody].
 *
 * Reads at most [MAX_SAMPLES] windows spread across the clip rather than the
 * whole thing: enough to characterise a voice, a fraction of the decode cost,
 * and enough to leave a large library re-learnable in minutes.
 */
@Singleton
class AudioProbe @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun probe(uri: String, durationMs: Long): Prosody {
        val window = WINDOW_SAMPLES
        val windows = 24
        val frames = try { readPcm16(uri, window, windows) } catch (_: Throwable) { null }
            ?: return Prosody()
        if (frames.size < 2) return Prosody()

        var speech = 0
        val pitches = mutableListOf<Float>()
        val energies = mutableListOf<Float>()

        for (f in frames) {
            val rms = sqrt(f.sumOf { (it.toDouble() * it).toDouble() }.toFloat() / f.size)
            val db = if (rms > 1e-5f) 20f * kotlin.math.log10(rms) else -60f
            energies += db

            // Too quiet to be a voice, or loud enough to be music.
            if (db < -46f || db > -4f) continue
            val voiced = fractionVoiced(f)
            if (voiced < 0.16f) continue
            speech++
            estimatePitch(f, db)?.let { pitches += it }
        }

        if (speech == 0) return Prosody(sampled = true)

        val medianE = energies.sorted()[energies.size / 2]
        val eRange = if (energies.size > 2) {
            ((energies.max() - energies.min()) / 40f).coerceIn(0f, 1f)
        } else 0f
        // One loudness peak per window, expressed per second.
        val spanSec = (durationMs / 1000f).coerceAtLeast(1f)
        val gestures = energies.count { it > medianE + 6f }

        return Prosody(
            speechRatio = (speech.toFloat() / frames.size).coerceIn(0f, 1f),
            pitchHz = pitches.sorted().getOrElse(pitches.size / 2) { 0f },
            pitchRange = pitchSpread(pitches),
            energyRange = eRange,
            gesturesPerSec = (gestures / spanSec).coerceIn(0f, 12f),
            sampled = true
        )
    }

    /** Share of the window where the signal is clearly periodic, i.e. voiced. */
    private fun fractionVoiced(f: ShortArray): Float {
        val step = 2
        var hits = 0
        var tested = 0
        var i = 0
        while (i + step * 2 < f.size) {
            tested++
            val a = f[i].toFloat()
            val b = f[i + step].toFloat()
            val c = f[i + step * 2].toFloat()
            // A voiced frame rises and falls; silence does not.
            if ((a - b) * (b - c) > 0f) hits++
            i += step
        }
        return if (tested == 0) 0f else hits.toFloat() / tested
    }

    /**
     * Fundamental frequency by autocorrelation over a plausible speech range.
     * Returns null when no clear peak, which is most of what separates a voice
     * from music and noise.
     */
    private fun estimatePitch(f: ShortArray, db: Float): Float? {
        val minLag = (SAMPLE_RATE / 400f).toInt().coerceAtLeast(2)   // up to 400 Hz
        val maxLag = (SAMPLE_RATE / 65f).toInt().coerceAtMost(f.size / 2)
        if (maxLag <= minLag) return null
        var best = 0f
        var bestLag = -1
        var lag = minLag
        while (lag <= maxLag) {
            var sum = 0f
            var n = 0
            var i = 0
            while (i + lag < f.size) {
                sum += f[i] * f[i + lag]
                n++; i += 2
            }
            val norm = if (n == 0) 0f else sum / n
            if (norm > best) { best = norm; bestLag = lag }
            lag += 2
        }
        if (bestLag <= 0 || best <= 0f) return null
        val hz = SAMPLE_RATE.toFloat() / bestLag
        return if (hz in 60f..400f) hz else null
    }

    private fun pitchSpread(pitches: List<Float>): Float {
        if (pitches.size < 4) return 0f
        val sorted = pitches.sorted()
        val median = sorted[sorted.size / 2]
        if (median <= 0f) return 0f
        val mean = sorted.sum() / sorted.size
        val variance = sorted.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / sorted.size
        return (sqrt(variance) / median).coerceIn(0f, 1f)
    }

    /**
     * Pull short PCM windows off the audio track. Uses MediaExtractor to find
     * the track, then samples it at evenly spread positions so the result is not
     * just the first two seconds, which is usually the quietest part.
     */
    private fun readPcm16(uri: String, window: Int, windows: Int): List<ShortArray> {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, android.net.Uri.parse(uri), emptyMap())
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return emptyList()
            val totalUs = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            ex.selectTrack(track)
            val out = mutableListOf<ShortArray>()
            val buf = java.nio.ByteBuffer.allocate(window * 2)
            val info = android.media.MediaCodec.BufferInfo()

            for (w in 0 until windows) {
                val wantUs = totalUs * (w + 1) / (windows + 1)
                ex.seekTo(wantUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var read = 0
                while (read < window * 2 && out.size <= w) {
                    val n = ex.readSampleData(buf, read)
                    if (n < 0) break
                    read += n
                    ex.advance()
                    if (buf.position() >= window * 2) {
                        val shortBuf = buf.asShortBuffer()
                        val arr = ShortArray(shortBuf.remaining())
                        shortBuf.get(arr)
                        out += arr
                        buf.clear()
                        break
                    }
                }
            }
            return out
        } finally {
            ex.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val WINDOW_SAMPLES = 1024
        const val MAX_SAMPLES = 24
    }
}
