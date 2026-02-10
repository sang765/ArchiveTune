/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.playback

import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Manages smooth volume fade animations for player transitions
 */
class VolumeFadeManager {
    private var currentFadeJob: Job? = null
    private var originalVolume: Float = 1f

    /**
     * Gradually increases volume from 0 to target volume
     */
    suspend fun fadeIn(player: Player, durationMs: Int, targetVolume: Float = 1f) {
        cancelCurrentFade()
        
        val startVolume = 0f
        val steps = max(1, durationMs / 50) // Update every ~50ms
        val stepDuration = durationMs / steps
        val volumeIncrement = (targetVolume - startVolume) / steps
        
        player.volume = startVolume
        
        for (i in 1..steps) {
            if (!coroutineContext.isActive) break
            
            delay(stepDuration.toLong())
            val newVolume = min(targetVolume, startVolume + (volumeIncrement * i))
            player.volume = newVolume
        }
        
        // Ensure we reach the exact target volume
        player.volume = targetVolume
    }

    /**
     * Gradually decreases volume to 0
     */
    suspend fun fadeOut(player: Player, durationMs: Int, onComplete: () -> Unit = {}) {
        cancelCurrentFade()
        
        originalVolume = player.volume
        val startVolume = player.volume
        val targetVolume = 0f
        val steps = max(1, durationMs / 50) // Update every ~50ms
        val stepDuration = durationMs / steps
        val volumeDecrement = (startVolume - targetVolume) / steps
        
        for (i in 1..steps) {
            if (!coroutineContext.isActive) break
            
            delay(stepDuration.toLong())
            val newVolume = max(targetVolume, startVolume - (volumeDecrement * i))
            player.volume = newVolume
        }
        
        // Ensure we reach the exact target volume
        player.volume = targetVolume
        onComplete()
    }

    /**
     * Restore volume to the original level before fade
     */
    fun restoreVolume(player: Player) {
        player.volume = originalVolume
    }

    /**
     * Get the stored original volume
     */
    fun getOriginalVolume(): Float = originalVolume

    /**
     * Store the current volume as the original
     */
    fun storeVolume(player: Player) {
        originalVolume = player.volume
    }

    /**
     * Cancel any ongoing fade operation
     */
    private fun cancelCurrentFade() {
        currentFadeJob?.cancel()
        currentFadeJob = null
    }
}
