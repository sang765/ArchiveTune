/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package moe.koiverse.archivetune.extensions

import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.LinearInterpolator
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.constants.PlayPauseFadeDurationKey
import moe.koiverse.archivetune.constants.SmoothPlayPauseKey
import moe.koiverse.archivetune.models.MediaMetadata
import java.util.ArrayDeque

private var fadeJob: Job? = null

fun Player.togglePlayPause(fadeDurationMs: Int = 0) {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    
    if (fadeDurationMs > 0) {
        fadeJob?.cancel()
        val currentVolume = volume
        val targetWhenReady = !playWhenReady
        
        if (targetWhenReady) {
            // Fading in: Set playWhenReady first, then fade volume from 0 to target
            playWhenReady = true
            fadeJob = CoroutineScope(Dispatchers.Main).launch {
                val steps = 20
                val stepDuration = fadeDurationMs / steps
                volume = 0f
                for (i in 1..steps) {
                    volume = currentVolume * (i.toFloat() / steps)
                    delay(stepDuration.toLong())
                }
                volume = currentVolume
            }
        } else {
            // Fading out: Fade volume to 0, then set playWhenReady
            fadeJob = CoroutineScope(Dispatchers.Main).launch {
                val steps = 20
                val stepDuration = fadeDurationMs / steps
                for (i in 1..steps) {
                    volume = currentVolume * (1f - i.toFloat() / steps)
                    delay(stepDuration.toLong())
                }
                playWhenReady = false
                volume = currentVolume
            }
        }
    } else {
        playWhenReady = !playWhenReady
    }
}

fun Player.togglePlayPauseSmooth(context: Context, dataStore: DataStore<Preferences>) {
    CoroutineScope(Dispatchers.Main).launch {
        val preferences = dataStore.data.first()
        val smoothEnabled = preferences[SmoothPlayPauseKey] ?: false
        val fadeDuration = if (smoothEnabled) {
            preferences[PlayPauseFadeDurationKey] ?: 300
        } else {
            0
        }
        togglePlayPause(fadeDuration)
    }
}

fun Player.toggleRepeatMode() {
    repeatMode =
        when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
}

fun Player.getQueueWindows(): List<Timeline.Window> {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        return emptyList()
    }
    val queue = ArrayDeque<Timeline.Window>()
    val queueSize = timeline.windowCount

    val currentMediaItemIndex: Int = currentMediaItemIndex
    queue.add(timeline.getWindow(currentMediaItemIndex, Timeline.Window()))

    var firstMediaItemIndex = currentMediaItemIndex
    var lastMediaItemIndex = currentMediaItemIndex
    val shuffleModeEnabled = shuffleModeEnabled
    while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) && queue.size < queueSize) {
        if (lastMediaItemIndex != C.INDEX_UNSET) {
            lastMediaItemIndex =
                timeline.getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                queue.add(timeline.getWindow(lastMediaItemIndex, Timeline.Window()))
            }
        }
        if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
            firstMediaItemIndex = timeline.getPreviousWindowIndex(
                firstMediaItemIndex,
                REPEAT_MODE_OFF,
                shuffleModeEnabled
            )
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                queue.addFirst(timeline.getWindow(firstMediaItemIndex, Timeline.Window()))
            }
        }
    }
    return queue.toList()
}

fun Player.getCurrentQueueIndex(): Int {
    if (currentTimeline.isEmpty) {
        return -1
    }
    var index = 0
    var currentMediaItemIndex = currentMediaItemIndex
    while (currentMediaItemIndex != C.INDEX_UNSET) {
        currentMediaItemIndex = currentTimeline.getPreviousWindowIndex(
            currentMediaItemIndex,
            REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (currentMediaItemIndex != C.INDEX_UNSET) {
            index++
        }
    }
    return index
}

val Player.currentMetadata: MediaMetadata?
    get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
    get() =
        object : AbstractList<MediaItem>() {
            override val size: Int
                get() = mediaItemCount

            override fun get(index: Int): MediaItem = getMediaItemAt(index)
        }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}
