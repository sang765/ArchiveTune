/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.playback

import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayPauseFadeManager(
    private val player: ExoPlayer,
    private val playbackFadeFactor: MutableStateFlow<Float>,
    private val fadeDurationMs: MutableStateFlow<Int>,
    private val suppressFadeReset: MutableStateFlow<Boolean>
) {
    private var fadeJob: Job? = null

    fun requestPlay(scope: CoroutineScope) {
        fadeJob?.cancel()
        
        val duration = fadeDurationMs.value
        if (duration <= 0) {
            player.play()
            return
        }

        player.play()
        suppressFadeReset.value = true
        playbackFadeFactor.value = 0f

        fadeJob = scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            val durationMs = duration.toLong()
            
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                
                playbackFadeFactor.value = t
                
                if (t >= 1f) {
                    break
                }
                
                delay(16) // ~60fps
            }
            
            playbackFadeFactor.value = 1f
            suppressFadeReset.value = false
        }
    }

    fun requestPause(scope: CoroutineScope) {
        fadeJob?.cancel()
        
        val duration = fadeDurationMs.value
        if (duration <= 0) {
            player.pause()
            return
        }

        val startFactor = playbackFadeFactor.value
        if (startFactor <= 0f) {
            player.pause()
            return
        }

        fadeJob = scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            val durationMs = duration.toLong()
            
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                
                playbackFadeFactor.value = startFactor * (1f - t)
                
                if (t >= 1f) {
                    break
                }
                
                delay(16) // ~60fps
            }
            
            player.pause()
        }
    }

    fun cancel() {
        fadeJob?.cancel()
        fadeJob = null
        suppressFadeReset.value = false
    }
}
