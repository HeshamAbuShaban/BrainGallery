package com.brain.gallery.data.brain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The words a clip says, as this phone hears them — the third sense of the
 * voice layer, after the audio delivery tags. It is bound to whatever speech
 * engine the phone itself ships (Android's on-device recogniser): nothing is
 * downloaded, nothing arrives over a network, nothing is ever sent back.
 *
 * Every implementation must degrade honestly: a null answer is not an error
 * shown to anyone, it is "attempted, nothing trustworthy came back".
 */
interface Transcriber {
    /** True only where this device can actually transcribe a file — no promises. */
    fun available(): Boolean

    /**
     * Distill words from [uri]'s audio. Returns null when the engine is
     * unavailable, the audio is unreadable, or the answer is not usable —
     * never throws.
     */
    suspend fun transcribe(uri: Uri, durationMs: Long): String?
}

/**
 * The platform implementation: [SpeechRecognizer.createOnDeviceSpeechRecognizer],
 * and instead of the microphone the recogniser is handed an *already opened
 * file descriptor* ([RecognizerIntent.EXTRA_AUDIO_SOURCE]). That is the whole
 * trick for this app's brand: reading a video's audio track from a file needs
 * NO RECORD_AUDIO permission and NO network — the sound already exists.
 *
 * Availability is the strictest honest form: [RecognizerIntent.EXTRA_AUDIO_SOURCE]
 * (a descriptor) replaced the raw-path extra at API 33, so on 31–32 the file
 * route is not reachable through a content:// VideoStore uri and the pass is
 * simply off there — no half-working promise. The probe is runtime anyway:
 * if the phone has no on-device engine installed, this still reports false
 * and the dialogue says so plainly in the Brain screen.
 */
@Singleton
class OnDeviceTranscriber @Inject constructor(
    @ApplicationContext private val ctx: Context
) : Transcriber {

    override fun available(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }
                .getOrDefault(false)

    override suspend fun transcribe(uri: Uri, durationMs: Long): String? {
        if (!available()) return null
        return withTimeoutOrNull(BUDGET_MS(durationMs)) {
            withContext(Dispatchers.Main) {
                // SpeechRecognizer insists on being created and driven on the
                // main thread; only the waiting happens on our coroutine.
                runCatching {
                    val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                    try {
                        listenOnce(recognizer, uri)
                    } finally {
                        runCatching { recognizer.destroy() }
                    }
                }.getOrNull()
            }
        }
    }

    /** One recognition session over one opened descriptor; null = unusable. */
    private suspend fun listenOnce(recognizer: SpeechRecognizer, uri: Uri): String? {
        val pfd: ParcelFileDescriptor = runCatching {
            ctx.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: return null
        val settled = AtomicBoolean(false)
        try {
            return suspendCancellableCoroutine<String?> { cont ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        if (settled.getAndSet(true)) return
                        val words = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION)
                        cont.resume(words?.firstOrNull()
                            ?.trim()?.takeIf { it.isNotEmpty() })
                    }
                    override fun onError(error: Int) {
                        if (settled.getAndSet(true)) return
                        Log.w(TAG, "recogniser error $error")
                        cont.resume(null)
                    }
                    // The rest of the listener is lifecycle noise for this
                    // pass; the answer only arrives through results/error.
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // The audio is already in the file — feed it, do not record it.
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                }
                // If the caller gives up (timeout, service torn down), stop
                // quietly; the descriptor is closed by the finally above.
                cont.invokeOnCancellation {
                    runCatching { recognizer.stopListening() }
                }
                recognizer.startListening(intent)
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    companion object {
        private const val TAG = "transcript"
        /** The phone crunches at least its own playback speed; give it room. */
        fun BUDGET_MS(durationMs: Long): Long = durationMs + 90_000L
    }
}
