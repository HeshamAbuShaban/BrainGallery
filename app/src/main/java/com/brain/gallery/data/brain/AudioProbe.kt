package com.brain.gallery.data.brain

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
        } else {
            out += "partly speaking"
        }
        // The queue that drives this pass treats an empty tag string as "not yet
        // looked at", so a sampled clip must never describe itself as nothing:
        // it would be picked up again on every run and the pass would never end.
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
        val decoded = try { readPcm16(uri, MAX_SAMPLES) } catch (_: Throwable) { null }
            ?: return Prosody()
        val frames = decoded.frames
        if (frames.size < 2) return Prosody()
        val rate = decoded.sampleRate

        var speech = 0
        val pitches = mutableListOf<Float>()
        val energies = mutableListOf<Float>()

        for (f in frames) {
            val rms = sqrt(f.sumOf { (it.toDouble() * it).toDouble() }.toFloat() / f.size) / Short.MAX_VALUE
            val db = if (rms > 1e-5f) 20f * kotlin.math.log10(rms) else -60f
            energies += db

            // Too quiet to be a voice, or loud enough to be music.
            if (db < -46f || db > -4f) continue
            val voiced = fractionVoiced(f)
            if (voiced < 0.16f) continue
            speech++
            estimatePitch(f, rate)?.let { pitches += it }
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
    private fun estimatePitch(f: ShortArray, rate: Int): Float? {
        if (rate <= 0) return null
        val minLag = (rate / 400f).toInt().coerceAtLeast(2)   // up to 400 Hz
        val maxLag = (rate / 65f).toInt().coerceAtMost(f.size / 2)
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
        val hz = rate.toFloat() / bestLag
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
     * Pull short PCM windows off the audio track.
     *
     * The extractor hands back whatever the file stores — AAC frames for almost
     * every MP4 — so the samples have to be run through a decoder first; reading
     * them raw and calling them audio would produce numbers with no relation to
     * the sound. The track is sampled at evenly spread positions so the result is
     * not just the first two seconds, which is usually the quietest part.
     *
     * Returns null when the track cannot be decoded at all, which is treated as
     * "no audio" rather than as an error.
     */
    private fun readPcm16(uri: String, windows: Int): Decoded? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, android.net.Uri.parse(uri), emptyMap())
            var track = -1
            var format: MediaFormat? = null
            var mime: String? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) { track = i; format = f; mime = m; break }
            }
            if (track < 0 || format == null || mime == null) return null
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return null
            val totalUs = format.getLong(MediaFormat.KEY_DURATION)
            if (totalUs <= 0L) return null
            if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) return null
            val declaredRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (declaredRate <= 0) return null
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1
            ex.selectTrack(track)

            // Pitch needs several whole periods per window, and a period at 65 Hz
            // runs to 700+ samples at 48 kHz — so size the window in time rather
            // than in samples, or the lowest voices become unmeasurable.
            val windowSamples = (declaredRate * 0.06f).toInt().coerceIn(1024, 16384)
            val needBytes = windowSamples * 2 * channels

            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(format, null, null, 0)
            dec.start()
            codec = dec

            val frames = mutableListOf<ShortArray>()
            var rate = declaredRate
            var encodingOk = true

            for (w in 0 until windows) {
                val wantUs = totalUs * (w + 1) / (windows + 1)
                ex.seekTo(wantUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                dec.flush()
                val pcm = java.nio.ByteBuffer.allocate(needBytes)
                val sample = decodeWindow(ex, dec, pcm, needBytes, channels) { outFormat ->
                    val e = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else AudioFormat.ENCODING_PCM_16BIT
                    if (e != AudioFormat.ENCODING_PCM_16BIT) encodingOk = false
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        rate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
                if (!encodingOk) return null
                if (sample != null) frames += sample
            }
            if (frames.isEmpty()) return null
            return Decoded(frames, rate)
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { ex.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Decode until [pcm] holds [needBytes] or the track runs out.
     *
     * Returns the window as samples, or null when too little came out to be worth
     * measuring — a handful of samples would claim to be silent, not be silent.
     */
    private fun decodeWindow(
        ex: MediaExtractor,
        codec: MediaCodec,
        pcm: java.nio.ByteBuffer,
        needBytes: Int,
        channels: Int,
        onFormat: (MediaFormat) -> Unit,
    ): ShortArray? {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var steps = 0
        var stalled = 0
        var filled = 0
        while (!outputDone && pcm.position() < needBytes && steps++ < 4000) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    if (inBuf == null) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0L, 0)
                    } else {
                        inBuf.clear()
                        val size = ex.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
            }
            when (val outIndex = codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat)
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val space = needBytes - pcm.position()
                        if (space > 0) {
                            val toCopy = minOf(info.size, space, outBuf.remaining())
                            val arr = ByteArray(toCopy)
                            outBuf.get(arr)
                            pcm.put(arr)
                        }
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) outputDone = true
                }
            }
            // A decoder that neither produces output nor reaches the end of the
            // track has gone quiet. Waiting on it only holds the whole pass up.
            if (pcm.position() == filled) stalled++ else { stalled = 0; filled = pcm.position() }
            if (stalled > 150) break
        }
        // Half a window is the floor: less than that says the decode stalled,
        // not that the clip is quiet.
        if (pcm.position() < needBytes / 2) return null
        pcm.flip()
        // PCM on disk is little-endian; ByteBuffers default to big-endian, and
        // reading them as such turns any signal into noise.
        pcm.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val raw = ShortArray(pcm.remaining() / 2)
        pcm.asShortBuffer().get(raw)
        if (raw.isEmpty()) return null
        if (channels <= 1) return raw
        // Pitch and voice detection are per-voice, not per-speaker: fold the
        // channels down or an interleaved pair looks like twice the frequency.
        val mono = ShortArray(raw.size / channels)
        for (i in mono.indices) {
            var sum = 0
            for (ch in 0 until channels) sum += raw[i * channels + ch].toInt()
            mono[i] = (sum / channels).toShort()
        }
        return mono
    }

    /** Decoded windows plus the sample rate they were actually produced at. */
    private class Decoded(val frames: List<ShortArray>, val sampleRate: Int)

    companion object {
        const val MAX_SAMPLES = 24
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val OUTPUT_TIMEOUT_US = 10_000L
    }
}
