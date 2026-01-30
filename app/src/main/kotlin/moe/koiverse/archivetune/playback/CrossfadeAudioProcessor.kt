package moe.koiverse.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Custom audio processor that applies crossfade between tracks
 */
@UnstableApi
class CrossfadeAudioProcessor : AudioProcessor {
    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var isActive = false
    private var isEnding = false
    
    @Volatile
    var crossfadeDurationMs: Int = 0
        set(value) {
            field = value
            updateCrossfadeSamples()
        }
    
    @Volatile
    var smoothPlayPauseDurationMs: Int = 0
        set(value) {
            field = value
            updateFadeSamples()
        }
    
    private var crossfadeSamples = 0
    private var fadeSamples = 0
    private var isFadingIn = false
    private var isFadingOut = false
    private var fadeCurrentSample = 0
    private var shouldStartFadeIn = false
    private var currentSample = 0
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    
    private fun updateCrossfadeSamples() {
        if (inputAudioFormat != AudioFormat.NOT_SET && crossfadeDurationMs > 0) {
            crossfadeSamples = (inputAudioFormat.sampleRate * crossfadeDurationMs) / 1000
        } else {
            crossfadeSamples = 0
        }
    }

    private fun updateFadeSamples() {
        if (inputAudioFormat != AudioFormat.NOT_SET && smoothPlayPauseDurationMs > 0) {
            fadeSamples = (inputAudioFormat.sampleRate * smoothPlayPauseDurationMs) / 1000
        } else {
            fadeSamples = 0
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (crossfadeDurationMs == 0 && smoothPlayPauseDurationMs == 0) {
            return AudioFormat.NOT_SET
        }
        
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        updateCrossfadeSamples()
        updateFadeSamples()
        
        return outputAudioFormat
    }

    override fun isActive(): Boolean = crossfadeDurationMs > 0 || smoothPlayPauseDurationMs > 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || crossfadeDurationMs == 0) {
            return
        }
        
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        
        buffer = replaceOutputBuffer(remaining)
        
        // Simple fade processing (this is a basic implementation)
        // In a real crossfade, we'd need to mix audio from the previous track
        if (isEnding && currentSample < crossfadeSamples) {
            // Fade out
            val samplesThisPass = min(crossfadeSamples - currentSample, remaining / 2)
            for (i in 0 until samplesThisPass) {
                val sample = inputBuffer.short
                val factor = 1.0f - (currentSample + i).toFloat() / crossfadeSamples
                val fadedSample = (sample * factor).toInt().toShort()
                buffer.putShort(fadedSample)
                currentSample++
            }
        } else {
            // Pass through
            buffer.put(inputBuffer)
        }
        
        buffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val output = buffer
        buffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return isEnding && buffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        buffer = AudioProcessor.EMPTY_BUFFER
        currentSample = 0
        isEnding = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    override fun queueEndOfStream() {
        isEnding = true
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }
}
    
    /**
     * Start fade in effect for smooth play
     */
    fun startFadeIn() {
        if (smoothPlayPauseDurationMs > 0) {
            isFadingIn = true
            fadeCurrentSample = 0
        }
    }
    
    /**
     * Start fade out effect for smooth pause
     */
    fun startFadeOut() {
        if (smoothPlayPauseDurationMs > 0) {
            isFadingOut = true
            fadeCurrentSample = 0
        }
    }
