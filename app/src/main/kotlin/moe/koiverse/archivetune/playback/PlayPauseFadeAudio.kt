/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Handles play/pause fade effect - smoothly fades volume when playing or pausing
 */
internal class PlayPauseFadeAudio(
    private val player: ExoPlayer,
    private val playPauseFadeDurationMs: MutableStateFlow<Int>,
    private val playbackFadeFactor: MutableStateFlow<Float>,
) {
    private var fadeJob: Job? = null
    private var wasPlaying = false
    private var isFading = false
    
    fun start(scope: CoroutineScope) {
        if (fadeJob?.isActive == true) return
        fadeJob = scope.launch { runLoop() }
    }
    
    fun release() {
        fadeJob?.cancel()
        fadeJob = null
        // Reset fade factor when releasing
        playbackFadeFactor.value = 1f
    }
    
    private suspend fun runLoop() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            val fadeDurationMs = playPauseFadeDurationMs.value
            
            if (fadeDurationMs <= 0) {
                // Reset to full volume if fade is disabled
                if (playbackFadeFactor.value != 1f) {
                    playbackFadeFactor.value = 1f
                }
                delay(250)
                continue
            }
            
            val currentIsPlaying = player.isPlaying
            
            // Check if we need to start a fade
            if (!isFading) {
                if (currentIsPlaying && !wasPlaying) {
                    // Transition from paused/stopped to playing - fade in
                    startFadeIn(fadeDurationMs)
                } else if (!currentIsPlaying && wasPlaying && player.playbackState == Player.STATE_READY) {
                    // Transition from playing to paused - fade out
                    startFadeOut(fadeDurationMs)
                }
            }
            
            wasPlaying = currentIsPlaying
            delay(100)
        }
    }
    
    private suspend fun startFadeIn(durationMs: Int) {
        if (isFading) return
        isFading = true
        
        val steps = 20
        val stepDelayMs = durationMs.toLong() / steps
        
        for (i in 0..steps) {
            if (!player.isPlaying || playPauseFadeDurationMs.value <= 0) {
                // Stopped or fade disabled, reset
                playbackFadeFactor.value = 1f
                isFading = false
                return
            }
            
            // Exponential fade in for more natural feel
            val t = i.toFloat() / steps
            val volume = t.pow(2) // Quadratic curve for smoother fade
            playbackFadeFactor.value = volume
            
            delay(stepDelayMs)
        }
        
        playbackFadeFactor.value = 1f
        isFading = false
    }
    
    private suspend fun startFadeOut(durationMs: Int) {
        if (isFading) return
        isFading = true
        
        val steps = 20
        val stepDelayMs = durationMs.toLong() / steps
        
        for (i in steps downTo 0) {
            if (player.isPlaying || player.playbackState != Player.STATE_READY) {
                // Started playing or stopped, reset
                playbackFadeFactor.value = 1f
                isFading = false
                return
            }
            
            // Exponential fade out for more natural feel
            val t = i.toFloat() / steps
            val volume = t.pow(2) // Quadratic curve for smoother fade
            playbackFadeFactor.value = volume
            
            delay(stepDelayMs)
        }
        
        playbackFadeFactor.value = 0f
        isFading = false
    }
}
