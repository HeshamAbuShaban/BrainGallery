package com.brain.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.VideoFrameDecoder
import com.brain.gallery.data.service.BrainScanService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BrainGalleryApp : Application(), ImageLoaderFactory {

    private val handler = Handler(Looper.getMainLooper())
    private var rescanPending = false
    private val pendingIds = mutableSetOf<Long>()

    /**
     * One loader for the whole app: a single thumbnail cache instead of one per
     * composable. This is what keeps memory and disk bounded on a 1k library.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbs"))
                .maxSizeBytes(96L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

    private val mediaObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            uri?.pathSegments?.lastOrNull()?.toLongOrNull()?.let { pendingIds.add(it) }
            if (rescanPending) return
            rescanPending = true
            handler.postDelayed({
                rescanPending = false
                val ids = pendingIds.toList().take(200)
                pendingIds.clear()
                // Delta rescan when we know exactly what changed; full sweep otherwise.
                BrainScanService.start(this@BrainGalleryApp, if (ids.isEmpty()) null else ids)
            }, 8000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
        )
    }
}
