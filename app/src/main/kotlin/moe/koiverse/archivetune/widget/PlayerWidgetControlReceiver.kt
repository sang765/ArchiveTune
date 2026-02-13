/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Internal receiver for widget playback control actions.
 * This receiver is not exported and requires signature permission.
 */
class PlayerWidgetControlReceiver : android.content.BroadcastReceiver() {

    companion object {
        /**
         * Send play/pause action to the internal receiver
         */
        fun sendPlayPause(context: Context) {
            val intent = Intent(context, PlayerWidgetControlReceiver::class.java).apply {
                action = PlayerWidgetProvider.ACTION_PLAY_PAUSE
            }
            context.sendBroadcast(intent)
        }

        /**
         * Send next track action to the internal receiver
         */
        fun sendNext(context: Context) {
            val intent = Intent(context, PlayerWidgetControlReceiver::class.java).apply {
                action = PlayerWidgetProvider.ACTION_NEXT
            }
            context.sendBroadcast(intent)
        }

        /**
         * Send previous track action to the internal receiver
         */
        fun sendPrevious(context: Context) {
            val intent = Intent(context, PlayerWidgetControlReceiver::class.java).apply {
                action = PlayerWidgetProvider.ACTION_PREVIOUS
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val sessionToken = SessionToken(context, ComponentName(context, moe.koiverse.archivetune.playback.MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                when (action) {
                    PlayerWidgetProvider.ACTION_PLAY_PAUSE -> {
                        if (controller.isPlaying) {
                            controller.pause()
                        } else {
                            controller.play()
                        }
                    }
                    PlayerWidgetProvider.ACTION_NEXT -> {
                        controller.seekToNext()
                    }
                    PlayerWidgetProvider.ACTION_PREVIOUS -> {
                        controller.seekToPrevious()
                    }
                }
                // Release the controller after use
                MediaController.releaseFuture(controllerFuture)
            } catch (e: Exception) {
                // Failed to control playback - ignore
            }
        }, MoreExecutors.directExecutor())
    }
}
