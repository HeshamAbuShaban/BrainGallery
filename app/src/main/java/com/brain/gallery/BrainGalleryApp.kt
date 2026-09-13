package com.brain.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.brain.gallery.data.work.IndexWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BrainGalleryApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private var rescanPending = false

    private val mediaObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // New/removed video -> small background pass (debounced 5s).
            // IndexWorker merges incrementally: L0 instant, L1/L2 only for uncertain.
            if (rescanPending) return
            rescanPending = true
            handler.postDelayed({
                rescanPending = false
                WorkManager.getInstance(this@BrainGalleryApp)
                    .enqueue(OneTimeWorkRequestBuilder<IndexWorker>().build())
            }, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
        )
    }
}
