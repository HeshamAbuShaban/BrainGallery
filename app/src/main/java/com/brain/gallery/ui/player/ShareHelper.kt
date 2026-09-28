package com.brain.gallery.ui.player

import android.content.Context
import android.content.Intent
import com.brain.gallery.data.local.VideoEntity

/**
 * Share a clip as a real file. The grant goes out as a content:// URI from our
 * own FileProvider, so the receiving app gets read access to this one video and
 * nothing else on the device.
 */
object ShareHelper {

    fun share(context: Context, video: VideoEntity) {
        // The entity stores a content:// uri, so hand that straight over: the
        // receiver is granted read on this one clip and nothing else.
        val uri = runCatching { android.net.Uri.parse(video.uri) }.getOrNull()

        val send = Intent(Intent.ACTION_SEND)
        if (uri != null && uri.scheme == "content") {
            send.type = "video/*"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.clipData = android.content.ClipData.newRawUri(video.displayName, uri)
        } else {
            send.type = "text/plain"
        }
        send.putExtra(Intent.EXTRA_TEXT, video.about.ifBlank { video.folderName })
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(send, "Share clip").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
