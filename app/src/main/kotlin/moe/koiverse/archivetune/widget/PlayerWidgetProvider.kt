/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.playback.MusicService
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppWidgetProvider for the ArchiveTune music player widget.
 * Handles widget lifecycle. Playback controls are handled by PlayerWidgetControlReceiver.
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    companion object {
        // Intent actions
        const val ACTION_PLAY_PAUSE = "moe.koiverse.archivetune.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "moe.koiverse.archivetune.WIDGET_NEXT"
        const val ACTION_PREVIOUS = "moe.koiverse.archivetune.WIDGET_PREVIOUS"
        const val ACTION_UPDATE = "moe.koiverse.archivetune.WIDGET_UPDATE"

        // Request code for pending intents
        private const val REQUEST_CODE_PLAY_PAUSE = 1
        private const val REQUEST_CODE_NEXT = 2
        private const val REQUEST_CODE_PREVIOUS = 3

        /**
         * Helper method to update all widget instances
         */
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, PlayerWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Create RemoteViews for the main widget
        val views = RemoteViews(context.packageName, R.layout.widget_player)

        // Set up click intents for controls - use internal receiver for playback actions
        setupControlButtons(context, views)

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Also update with current playback state
        updateWidgetWithPlaybackState(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun setupControlButtons(context: Context, views: RemoteViews) {
        // Play/Pause button - use internal control receiver
        val playPauseIntent = Intent(context, PlayerWidgetControlReceiver::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PLAY_PAUSE,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

        // Next button - use internal control receiver
        val nextIntent = Intent(context, PlayerWidgetControlReceiver::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

        // Previous button - use internal control receiver
        val previousIntent = Intent(context, PlayerWidgetControlReceiver::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PREVIOUS,
            previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_previous, previousPendingIntent)
    }

    override fun onEnabled(context: Context) {
        // First widget instance created
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        // Last widget instance removed
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_UPDATE -> handleUpdate(context)
        }
    }

    private fun handleUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, PlayerWidgetProvider::class.java)
        )
        updateWidgetWithPlaybackState(context, appWidgetManager, appWidgetIds)
    }

    private fun updateWidgetWithPlaybackState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                updateWidgetsFromController(context, appWidgetManager, appWidgetIds, controller)
                // Release controller after use to prevent leaks
                MediaController.releaseFuture(controllerFuture)
            } catch (e: Exception) {
                // Service not running or not connected - show default state
                updateWidgetsToDefaultState(context, appWidgetManager, appWidgetIds)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateWidgetsFromController(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        controller: MediaController
    ) {
        val currentMediaItem = controller.currentMediaItem

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            // Update track info
            if (currentMediaItem != null) {
                // Get title from media item
                val title = currentMediaItem.mediaMetadata.title?.toString()
                    ?: context.getString(R.string.no_music_playing)
                views.setTextViewText(R.id.widget_track_title, title)

                // Get artist from media item
                val artist = currentMediaItem.mediaMetadata.artist?.toString()
                    ?: context.getString(R.string.unknown_artist)
                views.setTextViewText(R.id.widget_artist_name, artist)

                // Get artwork - handle both content and remote URIs
                currentMediaItem.mediaMetadata.artworkUri?.let { artworkUri ->
                    val bitmap = loadArtwork(context, artworkUri)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    }
                }
            } else {
                views.setTextViewText(R.id.widget_track_title, context.getString(R.string.no_music_playing))
                views.setTextViewText(R.id.widget_artist_name, context.getString(R.string.unknown_artist))
            }

            // Update play/pause button
            val playPauseIcon = if (controller.isPlaying) {
                R.drawable.pause
            } else {
                R.drawable.play
            }
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            // Update progress (simplified - in production would track position)
            val duration = controller.duration
            if (duration > 0) {
                val position = controller.position
                val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt()
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateWidgetsToDefaultState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            views.setTextViewText(R.id.widget_track_title, context.getString(R.string.no_music_playing))
            views.setTextViewText(R.id.widget_artist_name, context.getString(R.string.unknown_artist))
            views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.play)
            views.setProgressBar(R.id.widget_progress, 100, 0, false)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Load artwork from URI, supporting both content:// and http/https URIs
     */
    private fun loadArtwork(context: Context, artworkUri: Uri): Bitmap? {
        return try {
            when (artworkUri.scheme) {
                "content" -> {
                    context.contentResolver.openInputStream(artworkUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                "http", "https" -> {
                    // Fetch from network on background thread
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
}
