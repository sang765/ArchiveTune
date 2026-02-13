/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.widget

import android.content.ComponentName
import android.content.Context
import moe.koiverse.archivetune.playback.MusicService

/**
 * Constants and utilities for the player widget
 */
object WidgetContract {

    // Action constants for widget broadcasts
    const val ACTION_PLAY_PAUSE = "moe.koiverse.archivetune.WIDGET_PLAY_PAUSE"
    const val ACTION_NEXT = "moe.koiverse.archivetune.WIDGET_NEXT"
    const val ACTION_PREVIOUS = "moe.koiverse.archivetune.WIDGET_PREVIOUS"
    const val ACTION_UPDATE = "moe.koiverse.archivetune.WIDGET_UPDATE"

    // Extra keys
    const val EXTRA_PLAY_WHEN_READY = "extra_play_when_ready"
    const val EXTRA_PLAYBACK_STATE = "extra_playback_state"

    // Widget IDs
    const val WIDGET_PROVIDER_CLASS = "PlayerWidgetProvider"
    const val WIDGET_UPDATE_SERVICE_CLASS = "PlayerWidgetUpdateService"

    /**
     * Get the ComponentName for the MusicService
     */
    fun getMusicServiceComponent(context: Context): ComponentName {
        return ComponentName(context, MusicService::class.java)
    }

    /**
     * Check if the service is running (placeholder - actual implementation
     * would check ActivityManager)
     */
    fun isServiceRunning(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            services.any { it.service.className == MusicService::class.java.name }
        } catch (e: Exception) {
            false
        }
    }
}
