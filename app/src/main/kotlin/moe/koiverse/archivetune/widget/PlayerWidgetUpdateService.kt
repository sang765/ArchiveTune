/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.IBinder
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.playback.MusicService
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for updating the player widget with current playback state.
 * Handles connection to MediaController and periodic updates.
 */
class PlayerWidgetUpdateService : Service() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null

    companion object {
        private const val UPDATE_INTERVAL_MS = 1000L // Update every second when playing

        /**
         * Start the widget update service
         */
        fun start(context: Context) {
            val intent = Intent(context, PlayerWidgetUpdateService::class.java)
            context.startService(intent)
        }

        /**
         * Stop the widget update service
         */
        fun stop(context: Context) {
            val intent = Intent(context, PlayerWidgetUpdateService::class.java)
            context.stopService(intent)
        }

        /**
         * Request an immediate widget update
         */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, PlayerWidgetUpdateService::class.java).apply {
                action = "ACTION_UPDATE_NOW"
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectToMediaController()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_UPDATE_NOW" -> updateWidgetsNow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectToMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)
                startPeriodicUpdates()
            } catch (e: Exception) {
                // Failed to connect - will retry on next service start
                stopSelf()
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateWidgetsNow()
            handlePlaybackStateChange(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateWidgetsNow()
            if (isPlaying) {
                startPeriodicUpdates()
            } else {
                stopPeriodicUpdates()
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateWidgetsNow()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateWidgetsNow()
        }
    }

    private fun handlePlaybackStateChange(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY, Player.STATE_BUFFERING -> {
                // Start or continue periodic updates
            }
            Player.STATE_IDLE, Player.STATE_ENDED -> {
                stopPeriodicUpdates()
            }
        }
    }

    private fun startPeriodicUpdates() {
        if (updateJob?.isActive == true) return

        updateJob = scope.launch {
            while (isActive && mediaController?.isPlaying == true) {
                updateWidgetsNow()
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateWidgetsNow() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, PlayerWidgetProvider::class.java)
        )

        if (appWidgetIds.isEmpty()) return

        val controller = mediaController
        if (controller == null) {
            updateWidgetsToDefaultState(appWidgetManager, appWidgetIds)
            return
        }

        val currentMediaItem = controller.currentMediaItem

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.widget_player)

            // Update track info
            if (currentMediaItem != null) {
                // Title
                val title = currentMediaItem.mediaMetadata.title?.toString()
                    ?: getString(R.string.no_music_playing)
                views.setTextViewText(R.id.widget_track_title, title)

                // Artist
                val artist = currentMediaItem.mediaMetadata.artist?.toString()
                    ?: getString(R.string.unknown_artist)
                views.setTextViewText(R.id.widget_artist_name, artist)

                // Artwork - handle both content and remote URIs
                currentMediaItem.mediaMetadata.artworkUri?.let { artworkUri ->
                    val bitmap = loadArtwork(artworkUri)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    }
                }
            } else {
                views.setTextViewText(R.id.widget_track_title, getString(R.string.no_music_playing))
                views.setTextViewText(R.id.widget_artist_name, getString(R.string.unknown_artist))
            }

            // Play/Pause button
            val playPauseIcon = if (controller.isPlaying) R.drawable.pause else R.drawable.play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            // Progress
            val duration = controller.duration
            if (duration > 0) {
                val position = controller.position
                val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt()
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Load artwork from URI, supporting both content:// and http/https URIs
     */
    private fun loadArtwork(artworkUri: Uri): Bitmap? {
        return try {
            when (artworkUri.scheme) {
                "content" -> {
                    contentResolver.openInputStream(artworkUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                "http", "https" -> {
                    // Fetch from network
                    loadBitmapFromNetwork(artworkUri.toString())
                }
                "file" -> {
                    BitmapFactory.decodeFile(artworkUri.path)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load bitmap from network URL
     */
    private fun loadBitmapFromNetwork(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            val inputStream = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            connection.disconnect()

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun updateWidgetsToDefaultState(
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.widget_player)

            views.setTextViewText(R.id.widget_track_title, getString(R.string.no_music_playing))
            views.setTextViewText(R.id.widget_artist_name, getString(R.string.unknown_artist))
            views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.play)
            views.setProgressBar(R.id.widget_progress, 100, 0, false)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDestroy() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controllerFuture = null
        mediaController = null
        stopPeriodicUpdates()
        scope.cancel()
        super.onDestroy()
    }
}
