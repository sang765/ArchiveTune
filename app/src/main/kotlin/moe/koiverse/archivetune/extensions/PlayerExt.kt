package moe.koiverse.archivetune.extensions

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.models.MediaMetadata
import java.util.ArrayDeque
import kotlin.math.roundToInt

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.togglePlayPauseWithFade(fadeDurationMs: Int, scope: CoroutineScope) {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    
    if (fadeDurationMs <= 0) {
        playWhenReady = !playWhenReady
        return
    }
    
    val currentVolume = volume
    val targetState = !playWhenReady
    
    if (targetState) {
        // Fade in: Set volume to 0, start playing, then fade to 1
        volume = 0f
        playWhenReady = true
        scope.launch(Dispatchers.Main) {
            val steps = 20
            val stepDuration = fadeDurationMs / steps
            val volumeStep = 1f / steps
            for (i in 1..steps) {
                if (!isActive) break
                volume = (volumeStep * i).coerceAtMost(1f)
                delay(stepDuration.toLong())
            }
            volume = currentVolume.coerceIn(0f, 1f)
        }
    } else {
        // Fade out: Fade to 0, then pause
        scope.launch(Dispatchers.Main) {
            val steps = 20
            val stepDuration = fadeDurationMs / steps
            val volumeStep = currentVolume / steps
            for (i in 1..steps) {
                if (!isActive) break
                volume = (currentVolume - volumeStep * i).coerceAtLeast(0f)
                delay(stepDuration.toLong())
            }
            volume = currentVolume
            playWhenReady = false
        }
    }
}

/**
 * Extension function to toggle play/pause with smooth fade effect using player's internal scope
 */
fun Player.togglePlayPauseWithFade(fadeDurationMs: Int) {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    
    if (fadeDurationMs <= 0) {
        playWhenReady = !playWhenReady
        return
    }
    
    val currentVolume = volume
    val targetState = !playWhenReady
    
    if (targetState) {
        // Fade in: Set volume to 0, start playing, then fade to 1
        volume = 0f
        playWhenReady = true
        // Use a simple coroutine without explicit scope
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val steps = 20
            val stepDuration = fadeDurationMs / steps
            val volumeStep = 1f / steps
            for (i in 1..steps) {
                volume = (volumeStep * i).coerceAtMost(1f)
                kotlinx.coroutines.delay(stepDuration.toLong())
            }
            volume = currentVolume.coerceIn(0f, 1f)
        }
    } else {
        // Fade out: Fade to 0, then pause
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val steps = 20
            val stepDuration = fadeDurationMs / steps
            val volumeStep = currentVolume / steps
            for (i in 1..steps) {
                volume = (currentVolume - volumeStep * i).coerceAtLeast(0f)
                kotlinx.coroutines.delay(stepDuration.toLong())
            }
            volume = currentVolume
            playWhenReady = false
        }
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
