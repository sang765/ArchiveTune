package moe.koiverse.archivetune.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackAnalysis(
    val bpm: Float = 0f,
    val energy: Float = 0f,
    val spectralCentroid: Float = 0f,
    val estimatedKey: String? = null,
)

object AudioAnalyzer {
    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 2048
    private const val HOP_SIZE = 512
    private const val MIN_BPM = 60
    private const val MAX_BPM = 180

    suspend fun analyze(pcmSamples: ShortArray): TrackAnalysis = withContext(Dispatchers.Default) {
        if (pcmSamples.isEmpty()) return@withContext TrackAnalysis()

        val bpm = estimateBpm(pcmSamples)
        val energy = computeEnergy(pcmSamples)
        val centroid = computeSpectralCentroid(pcmSamples)

        TrackAnalysis(
            bpm = bpm,
            energy = energy,
            spectralCentroid = centroid,
        )
    }

    fun estimateBpm(samples: ShortArray): Float {
        val envelope = computeEnergyEnvelope(samples)
        if (envelope.size < 4) return 0f

        val acf = autocorrelate(envelope)
        val framesPerSecond = SAMPLE_RATE.toFloat() / HOP_SIZE
        val minLag = (60f * framesPerSecond / MAX_BPM).roundToInt().coerceAtLeast(1)
        val maxLag = (60f * framesPerSecond / MIN_BPM).roundToInt().coerceAtMost(acf.size - 1)
        if (minLag >= maxLag || maxLag > acf.size) return 0f

        var peakLag = minLag
        var peakVal = 0f
        for (i in minLag..maxLag) {
            if (acf[i] > peakVal) {
                peakVal = acf[i]
                peakLag = i
            }
        }
        if (peakVal < 0.1f) return 0f

        val bpm = framesPerSecond / peakLag * 60f
        return bpm.coerceIn(MIN_BPM.toFloat(), MAX_BPM.toFloat())
    }

    private fun computeEnergyEnvelope(samples: ShortArray): FloatArray {
        val numFrames = samples.size / HOP_SIZE
        if (numFrames == 0) return FloatArray(0)

        val hann = FloatArray(FRAME_SIZE) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (FRAME_SIZE - 1)))).toFloat()
        }

        return FloatArray(numFrames) { frame ->
            val start = frame * HOP_SIZE
            var sum = 0f
            for (i in 0 until FRAME_SIZE.coerceAtMost(samples.size - start)) {
                val idx = start + i
                if (idx < samples.size) {
                    val windowed = samples[idx].toFloat() / Short.MAX_VALUE * hann[i]
                    sum += windowed * windowed
                }
            }
            sqrt(sum / FRAME_SIZE)
        }
    }

    private fun autocorrelate(signal: FloatArray): FloatArray {
        val n = signal.size
        val acf = FloatArray(n)
        for (lag in 0 until n) {
            var sum = 0f
            for (i in 0 until (n - lag)) {
                sum += signal[i] * signal[i + lag]
            }
            acf[lag] = sum / (n - lag)
        }
        if (acf[0] > 0f) {
            for (i in acf.indices) acf[i] /= acf[0]
        }
        return acf
    }

    private fun computeEnergy(samples: ShortArray): Float {
        var sumSq = 0.0
        for (s in samples) sumSq += (s.toDouble() / Short.MAX_VALUE) * (s.toDouble() / Short.MAX_VALUE)
        return sqrt(sumSq / samples.size).toFloat()
    }

    private fun computeSpectralCentroid(samples: ShortArray): Float {
        val fftSize = 1024
        if (samples.size < fftSize) return 0f

        val hann = FloatArray(fftSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
        }

        val real = FloatArray(fftSize) { i ->
            samples[i].toFloat() / Short.MAX_VALUE * hann[i]
        }
        val imag = FloatArray(fftSize)

        Fft.transform(real, imag)

        var weightedSum = 0f
        var magnitudeSum = 0f
        for (i in 0 until fftSize / 2) {
            val magnitude = sqrt(real[i] * real[i] + imag[i] * imag[i])
            val freq = i.toFloat() * SAMPLE_RATE / fftSize
            weightedSum += freq * magnitude
            magnitudeSum += magnitude
        }
        return if (magnitudeSum > 0f) weightedSum / magnitudeSum else 0f
    }
}

private object Fft {
    fun transform(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        val bits = (Math.log(n.toDouble()) / Math.log(2.0)).roundToInt()
        val reversed = IntArray(n)
        for (i in 0 until n) {
            reversed[i] = (Integer.reverse(i) ushr (32 - bits))
        }
        val reorderedReal = FloatArray(n) { real[reversed[it]] }
        val reorderedImag = FloatArray(n) { imag[reversed[it]] }
        System.arraycopy(reorderedReal, 0, real, 0, n)
        System.arraycopy(reorderedImag, 0, imag, 0, n)

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angleStep = -2.0 * PI / len
            for (i in 0 until n step len) {
                for (j in 0 until halfLen) {
                    val wRe = cos(angleStep * j).toFloat()
                    val wIm = sin(angleStep * j).toFloat()
                    val idx = i + j + halfLen
                    val tRe = wRe * real[idx] - wIm * imag[idx]
                    val tIm = wRe * imag[idx] + wIm * real[idx]
                    real[idx] = real[i + j] - tRe
                    imag[idx] = imag[i + j] - tIm
                    real[i + j] += tRe
                    imag[i + j] += tIm
                }
            }
            len *= 2
        }
    }
}
