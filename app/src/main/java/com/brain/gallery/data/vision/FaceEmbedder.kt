package com.brain.gallery.data.vision

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MobileFaceNet identity embedding (112x112 in, 192-d L2-normalized out).
 * Model ships in assets (fully offline). Null-safe: returns null when unavailable.
 */
@Singleton
class FaceEmbedder @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var broken = false

    private fun interp(): Interpreter? = synchronized(lock) {
        if (interpreter == null && !broken) {
            interpreter = try {
                Interpreter(loadModel(), Interpreter.Options().setNumThreads(2))
            } catch (_: Exception) {
                broken = true
                null
            }
        }
        interpreter
    }

    suspend fun embed(faceBmp: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        val it = interp() ?: return@withContext null
        try {
            val input = preprocess(faceBmp)
            val out = Array(1) { FloatArray(192) }
            synchronized(lock) { it.run(input, out) }
            l2norm(out[0])
        } catch (_: Exception) {
            null
        }
    }

    private fun preprocess(bmp: Bitmap): ByteBuffer {
        val size = 112
        val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
        val buf = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)
        if (!scaled.isRecycled) scaled.recycle()
        for (c in px) {
            buf.putFloat((((c shr 16) and 0xFF) - 127.5f) / 128f)
            buf.putFloat((((c shr 8) and 0xFF) - 127.5f) / 128f)
            buf.putFloat(((c and 0xFF) - 127.5f) / 128f)
        }
        return buf
    }

    private fun l2norm(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = kotlin.math.sqrt(s)
        if (n < 1e-9f) return v
        for (i in v.indices) v[i] = v[i] / n
        return v
    }

    private fun loadModel(): ByteBuffer {
        val fd = ctx.assets.openFd("mobilefacenet.tflite")
        val stream = FileInputStream(fd.fileDescriptor)
        val channel = stream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    companion object {
        fun toBytes(v: FloatArray): ByteArray {
            val b = ByteBuffer.allocate(v.size * 4).order(ByteOrder.nativeOrder())
            v.forEach { b.putFloat(it) }
            return b.array()
        }

        fun fromBytes(bytes: ByteArray?): FloatArray? {
            if (bytes == null || bytes.size % 4 != 0 || bytes.isEmpty()) return null
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            return FloatArray(bytes.size / 4) { b.float }
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot.coerceIn(-1f, 1f)
        }
    }
}
