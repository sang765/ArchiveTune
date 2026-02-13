/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.playback.MusicService
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object WidgetUpdateService {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var context: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateWidget()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateWidget()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateWidget()
        }
    }

    fun initialize(context: Context) {
        if (mediaController != null) return

        this.context = context.applicationContext
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)
                updateWidget()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        controllerFuture = null
        context = null
    }

    private fun updateWidget() {
        val controller = mediaController ?: return
        val context = this.context ?: return

        val title = controller.mediaMetadata.title?.toString()
        val artist = controller.mediaMetadata.artist?.toString()
        val isPlaying = controller.isPlaying
        val progress = if (controller.duration > 0) {
            ((controller.currentPosition.toFloat() / controller.duration) * 100).toInt()
        } else {
            0
        }

        // Load album art asynchronously
        scope.launch(Dispatchers.IO) {
            val albumArt = loadAlbumArt(controller.mediaMetadata)
            launch(Dispatchers.Main) {
                PlayerWidgetProvider.updateAllWidgets(
                    context,
                    title,
                    artist,
                    isPlaying,
                    progress,
                    albumArt
                )
            }
        }
    }

    private fun loadAlbumArt(metadata: MediaMetadata): Bitmap? {
        return try {
            // Try to get album art from metadata
            val artworkData = metadata.artworkData
            if (artworkData != null) {
                return BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            }

            // Try to load from URI
            val artworkUri = metadata.artworkUri
            if (artworkUri != null) {
                val url = URL(artworkUri.toString())
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input: InputStream = connection.inputStream
                return BitmapFactory.decodeStream(input)
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
