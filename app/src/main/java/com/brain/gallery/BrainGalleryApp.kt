package com.brain.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.brain.gallery.data.service.BrainScanService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BrainGalleryApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private var rescanPending = false

    private val mediaObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // Gallery changed -> wake the foreground indexer (debounced 8s).
            if (rescanPending) return
            rescanPending = true
            handler.postDelayed({
                rescanPending = false
                BrainScanService.start(this@BrainGalleryApp)
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
