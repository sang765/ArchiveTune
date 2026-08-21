/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import leakcanary.ReachabilityWatcher
import moe.rukamori.archivetune.utils.dataStore

internal object LeakCanaryVariant {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val watchersInstalled = AtomicBoolean(false)
  private val trackingEnabled = AtomicBoolean(false)
  private val leakCanaryEnabledKey = booleanPreferencesKey(LeakCanaryToggle.PREFERENCE_KEY)
  private val gatedReachabilityWatcher =
    ReachabilityWatcher { watchedObject, description ->
      if (trackingEnabled.get()) {
        AppWatcher.objectWatcher.expectWeaklyReachable(watchedObject, description)
      }
    }

  private const val LEAK_LAUNCHER_COMPONENT =
    "moe.rukamori.archivetune.nightly/leakcanary.internal.activity.LeakLauncherActivity"
  private const val NOTIFICATION_CHANNEL_ID = "leak_canary_status"
  private const val NOTIFICATION_ID = 9_500

  @JvmStatic
  fun initialize(application: Application) {
    ensureNotificationChannel(application)
    installWatchers(application)
    // Disable icon by default on startup
    setLauncherIconEnabled(application, false)
    applyTrackingEnabled(false)
    scope.launch {
      application.dataStore.data
        .map { preferences -> preferences[leakCanaryEnabledKey] ?: false }
        .distinctUntilChanged()
        .collect { enabled ->
          application.mainExecutor.execute {
            applyTrackingEnabled(enabled)
            setLauncherIconEnabled(application, enabled)
            if (enabled) {
              showLauncherIconNotification(application)
            }
          }
        }
    }
  }

  @JvmStatic
  fun setEnabled(context: Context, enabled: Boolean) {
    (context.applicationContext as? Application)?.let { app ->
      applyTrackingEnabled(enabled)
      setLauncherIconEnabled(app, enabled)
      if (enabled) {
        showLauncherIconNotification(app)
      }
    }
  }

  private fun installWatchers(application: Application) {
    if (watchersInstalled.compareAndSet(false, true)) {
      AppWatcher.manualInstall(
        application = application,
        watchersToInstall = AppWatcher.appDefaultWatchers(application, gatedReachabilityWatcher),
      )
    }
  }

  private fun applyTrackingEnabled(enabled: Boolean) {
    trackingEnabled.set(enabled)
    LeakCanary.config = LeakCanary.config.copy(dumpHeap = enabled)
  }

  private fun setLauncherIconEnabled(context: Context, enabled: Boolean) {
    try {
      val componentName = ComponentName.unflattenFromString(LEAK_LAUNCHER_COMPONENT) ?: return
      val newState = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      }
      context.packageManager.setComponentEnabledSetting(
        componentName,
        newState,
        PackageManager.DONT_KILL_APP,
      )
    } catch (_: Exception) {
      // Component may not exist in this build variant
    }
  }

  private fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
      NOTIFICATION_CHANNEL_ID,
      context.getString(R.string.app_name),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "LeakCanary launcher icon status"
    }
    manager.createNotificationChannel(channel)
  }

  private fun showLauncherIconNotification(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(R.drawable.mic)
      .setContentTitle("LeakCanary enabled")
      .setContentText("Check your launcher app list")
      .setAutoCancel(true)
      .build()
    manager.notify(NOTIFICATION_ID, notification)
  }
}
