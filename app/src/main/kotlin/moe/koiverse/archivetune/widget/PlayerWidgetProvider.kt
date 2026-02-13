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
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.playback.MusicService

class PlayerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "moe.koiverse.archivetune.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "moe.koiverse.archivetune.WIDGET_NEXT"
        const val ACTION_PREVIOUS = "moe.koiverse.archivetune.WIDGET_PREVIOUS"
        const val ACTION_UPDATE = "moe.koiverse.archivetune.WIDGET_UPDATE"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
            progress: Int,
            albumArt: Bitmap?
        ) {
            val views = buildRemoteViews(context, title, artist, isPlaying, progress, albumArt)
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        fun updateAllWidgets(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
            progress: Int,
            albumArt: Bitmap?
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PlayerWidgetProvider::class.java)
            )
            
            widgetIds.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId, title, artist, isPlaying, progress, albumArt)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
            progress: Int,
            albumArt: Bitmap?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            // Set track info
            views.setTextViewText(
                R.id.widget_track_title,
                title ?: context.getString(R.string.not_playing)
            )
            views.setTextViewText(R.id.widget_artist_name, artist ?: "")

            // Set play/pause button
            val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            // Set progress
            views.setProgressBar(R.id.widget_progress, 100, progress, false)

            // Set album art
            if (albumArt != null) {
                views.setImageViewBitmap(R.id.widget_album_art, albumArt)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.mipmap.ic_launcher)
            }

            // Set click listeners
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                getPendingIntent(context, ACTION_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                getPendingIntent(context, ACTION_NEXT)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_previous,
                getPendingIntent(context, ACTION_PREVIOUS)
            )

            return views
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlayerWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widgets with current state
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId, null, null, false, 0, null)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> handlePlayPause(context)
            ACTION_NEXT -> handleNext(context)
            ACTION_PREVIOUS -> handlePrevious(context)
            ACTION_UPDATE -> {
                // Trigger widget update
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, PlayerWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, widgetIds)
            }
        }
    }

    private fun handlePlayPause(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun handleNext(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                controller.seekToNext()
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun handlePrevious(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                controller.seekToPrevious()
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onEnabled(context: Context) {
        // First widget instance added
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        // Last widget instance removed
        super.onDisabled(context)
    }
}

