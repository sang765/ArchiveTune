package moe.koiverse.archivetune.utils

import android.content.Context
import android.content.res.Configuration
import moe.koiverse.archivetune.ui.screens.settings.DiscordPresenceManager
import java.util.Locale

fun reportException(throwable: Throwable) {
    throwable.printStackTrace()
}

/**
 * Restarts the Discord Presence Manager if it's currently running.
 * This helper eliminates duplicate logic across multiple files.
 */
fun restartDiscordPresenceIfRunning() {
    if (DiscordPresenceManager.isRunning()) {
        try {
            DiscordPresenceManager.restart()
        } catch (_: Exception) {}
    }
}

@Suppress("DEPRECATION")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}