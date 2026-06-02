package moe.koiverse.archivetune.playback

import moe.koiverse.archivetune.constants.AutomixMode
import moe.koiverse.archivetune.models.MediaMetadata
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin

data class TransitionPlan(
    val crossfadeDurationMs: Long,
    val incomingStartOffsetMs: Long = 0L,
    val outgoingFadeCurve: FadeCurve = FadeCurve.SINUSOIDAL,
    val incomingFadeCurve: FadeCurve = FadeCurve.SINUSOIDAL,
    val lowCutOnOutgoing: Boolean = false,
    val lowCutFrequency: Float = 0f,
)

enum class FadeCurve {
    SINUSOIDAL,
    LINEAR,
    LOGARITHMIC,
    HOLD_THEN_FADE,
}

class AutomixEngine(
    private val mode: AutomixMode,
    private val baseCrossfadeDurationMs: Long,
) {
    private val analysisCache = mutableMapOf<String, TrackAnalysis>()

    fun cacheAnalysis(mediaId: String, analysis: TrackAnalysis) {
        analysisCache[mediaId] = analysis
    }

    fun getAnalysis(mediaId: String): TrackAnalysis? = analysisCache[mediaId]

    fun planTransition(
        outgoing: MediaMetadata?,
        incoming: MediaMetadata?,
        outgoingAnalysis: TrackAnalysis?,
        incomingAnalysis: TrackAnalysis?,
    ): TransitionPlan {
        if (mode == AutomixMode.OFF) {
            return TransitionPlan(crossfadeDurationMs = 0)
        }

        val matchScore = computeMatchScore(outgoingAnalysis, incomingAnalysis)
        val energyDelta = computeEnergyDelta(outgoingAnalysis, incomingAnalysis)
        val isSameArtist = outgoing?.artists?.firstOrNull()?.name == incoming?.artists?.firstOrNull()?.name
        val isSameAlbum = outgoing?.album?.id == incoming?.album?.id

        return when (mode) {
            AutomixMode.OFF -> TransitionPlan(crossfadeDurationMs = 0)
            AutomixMode.SMOOTH -> planSmoothTransition(matchScore, isSameArtist, isSameAlbum, energyDelta)
            AutomixMode.ENERGY -> planEnergyTransition(matchScore, energyDelta)
            AutomixMode.HARMONIC -> planHarmonicTransition(matchScore, outgoingAnalysis, incomingAnalysis, isSameArtist)
        }
    }

    private fun planSmoothTransition(
        matchScore: Float,
        isSameArtist: Boolean,
        isSameAlbum: Boolean,
        energyDelta: Float,
    ): TransitionPlan {
        val duration = when {
            isSameAlbum || isSameArtist -> baseCrossfadeDurationMs + 2000L
            matchScore > 0.7f -> baseCrossfadeDurationMs + 1000L
            matchScore > 0.4f -> baseCrossfadeDurationMs
            energyDelta > 0.3f -> (baseCrossfadeDurationMs * 1.5f).toLong()
            else -> (baseCrossfadeDurationMs * 0.7f).toLong()
        }.coerceIn(500L, 10000L)

        return TransitionPlan(
            crossfadeDurationMs = duration,
            outgoingFadeCurve = if (isSameAlbum) FadeCurve.LOGARITHMIC else FadeCurve.SINUSOIDAL,
            incomingFadeCurve = if (isSameAlbum) FadeCurve.LOGARITHMIC else FadeCurve.SINUSOIDAL,
        )
    }

    private fun planEnergyTransition(
        matchScore: Float,
        energyDelta: Float,
    ): TransitionPlan {
        val energyRising = energyDelta > 0.15f
        val energyDropping = energyDelta < -0.15f

        val duration = when {
            energyRising -> (baseCrossfadeDurationMs * 0.6f).toLong()
            energyDropping -> (baseCrossfadeDurationMs * 1.3f).toLong()
            matchScore > 0.5f -> baseCrossfadeDurationMs
            else -> (baseCrossfadeDurationMs * 0.8f).toLong()
        }.coerceIn(500L, 10000L)

        return TransitionPlan(
            crossfadeDurationMs = duration,
            outgoingFadeCurve = if (energyRising) FadeCurve.LINEAR else FadeCurve.SINUSOIDAL,
            incomingFadeCurve = if (energyDropping) FadeCurve.LINEAR else FadeCurve.SINUSOIDAL,
            lowCutOnOutgoing = energyRising,
        )
    }

    private fun planHarmonicTransition(
        matchScore: Float,
        outgoingAnalysis: TrackAnalysis?,
        incomingAnalysis: TrackAnalysis?,
        isSameArtist: Boolean,
    ): TransitionPlan {
        val bpmRatio = computeBpmRatio(outgoingAnalysis, incomingAnalysis)
        val isBpmMatch = bpmRatio in 0.9f..1.1f || abs(bpmRatio - 1.5f) < 0.1f || abs(bpmRatio - 0.667f) < 0.1f
        val isHalfTime = bpmRatio in 0.45f..0.55f || bpmRatio in 1.9f..2.1f

        val duration = when {
            isSameArtist -> baseCrossfadeDurationMs + 2000L
            isBpmMatch -> (baseCrossfadeDurationMs * 1.3f).toLong()
            isHalfTime -> baseCrossfadeDurationMs
            matchScore > 0.5f -> baseCrossfadeDurationMs
            else -> (baseCrossfadeDurationMs * 0.6f).toLong()
        }.coerceIn(500L, 10000L)

        val chosenCurve = if (isBpmMatch) FadeCurve.LOGARITHMIC else FadeCurve.SINUSOIDAL
        return TransitionPlan(
            crossfadeDurationMs = duration,
            incomingStartOffsetMs = if (isBpmMatch) computeBeatAlignedOffset(outgoingAnalysis, incomingAnalysis) else 0L,
            outgoingFadeCurve = chosenCurve,
            incomingFadeCurve = chosenCurve,
        )
    }

    fun computeCrossfadeVolume(
        progress: Float,
        baseVolume: Float,
        plan: TransitionPlan,
        outgoing: Boolean,
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        val curve = if (outgoing) plan.outgoingFadeCurve else plan.incomingFadeCurve
        val factor = when (curve) {
            FadeCurve.SINUSOIDAL -> {
                val rad = p * (PI / 2.0)
                (if (outgoing) cos(rad) else sin(rad)).toFloat()
            }
            FadeCurve.LINEAR -> if (outgoing) 1f - p else p
            FadeCurve.LOGARITHMIC -> {
                if (outgoing) 1f - (p * p) else p * p
            }
            FadeCurve.HOLD_THEN_FADE -> {
                if (outgoing) (1f - p.coerceAtLeast(0.3f)) / 0.7f else p
            }
        }
        return (baseVolume * factor.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    fun clearCache() {
        analysisCache.clear()
    }

    fun removeFromCache(mediaId: String) {
        analysisCache.remove(mediaId)
    }

    companion object {
        private const val CAMELOT_KEYS = 12

        private val harmonicKeys = mapOf(
            "C" to 0, "G" to 1, "D" to 2, "A" to 3, "E" to 4, "B" to 5,
            "Gb" to 6, "Db" to 7, "Ab" to 8, "Eb" to 9, "Bb" to 10, "F" to 11,
            "Cm" to 0, "Gm" to 1, "Dm" to 2, "Am" to 3, "Em" to 4, "Bm" to 5,
            "Fbm" to 6, "C#m" to 7, "G#m" to 8, "D#m" to 9, "A#m" to 10, "Fm" to 11,
        )

        fun computeMatchScore(
            outgoing: TrackAnalysis?,
            incoming: TrackAnalysis?,
        ): Float {
            if (outgoing == null || incoming == null) return 0.5f
            var score = 0f
            var factors = 0

            if (outgoing.bpm > 0f && incoming.bpm > 0f) {
                val ratio = min(outgoing.bpm, incoming.bpm) / max(outgoing.bpm, incoming.bpm)
                score += ratio.coerceIn(0f, 1f)
                factors++
            }

            if (outgoing.energy > 0f && incoming.energy > 0f) {
                val energySimilarity = 1f - abs(outgoing.energy - incoming.energy)
                score += energySimilarity.coerceIn(0f, 1f)
                factors++
            }

            if (outgoing.spectralCentroid > 0f && incoming.spectralCentroid > 0f) {
                val centroidRatio = min(outgoing.spectralCentroid, incoming.spectralCentroid) /
                    max(outgoing.spectralCentroid, incoming.spectralCentroid)
                score += centroidRatio.coerceIn(0f, 1f)
                factors++
            }

            return if (factors > 0) score / factors else 0.5f
        }

        private fun computeEnergyDelta(
            outgoing: TrackAnalysis?,
            incoming: TrackAnalysis?,
        ): Float {
            if (outgoing == null || incoming == null) return 0f
            return incoming.energy - outgoing.energy
        }

        private fun computeBpmRatio(
            outgoing: TrackAnalysis?,
            incoming: TrackAnalysis?,
        ): Float {
            if (outgoing == null || incoming == null || outgoing.bpm <= 0f || incoming.bpm <= 0f) return 0f
            return min(outgoing.bpm, incoming.bpm) / max(outgoing.bpm, incoming.bpm)
        }

        private fun computeBeatAlignedOffset(
            outgoing: TrackAnalysis?,
            incoming: TrackAnalysis?,
        ): Long {
            if (outgoing == null || incoming == null || outgoing.bpm <= 0f || incoming.bpm <= 0f) return 0L
            val beatIntervalMs = (60_000f / incoming.bpm).toLong()
            return beatIntervalMs / 2
        }
    }
}
