package moe.koiverse.archivetune.discord

import android.app.Activity
import timber.log.Timber

object DiscordSocialSdkAndroidInitializer {
    private const val TAG = "DiscordSocialSdkInit"

    fun setEngineActivity(activity: Activity) {
        // No-op: Discord Social SDK is pure Kotlin, no native engine needed.
        Timber.tag(TAG).v("Discord Social SDK (Kotlin) — no engine initialization required")
    }
}
