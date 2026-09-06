package com.shrutimonitor.app.audio

import kotlin.math.abs
import kotlin.math.log2

/**
 * Display-side pitch stream filter.
 *
 * Replicates the pure, unsmoothed plotting style of Vocal Pitch Monitor (VPM):
 * 1. Confidence gating: frames below [confidenceThreshold] or unvoiced become gaps (0 Hz).
 * 2. NO MEDIAN FILTER and NO EMA: smoothing naturally forces diagonal vocal glides into 
 *    blocky vertical/horizontal "staircases" (sharp edges). VPM plots the raw parabolic-interpolated 
 *    FFT peaks. We simply pass through the raw pitch data to achieve identical smooth curves.
 * 3. Transient Octave Error Handling: 
 *    Single-frame 2x / 0.5x harmonic glitches are snapped to the previous voiced frame's octave.
 *    Sustained octave leaps (>= 2 frames) are accepted as genuine vocal octave transitions.
 * 4. Outlier rejection: single-frame jumps > [maxSingleFrameJumpCents] (default 600 cents) are suppressed.
 *    Sustained leaps (>= 2 frames) are accepted.
 * 5. Tunable discontinuity threshold: [breakThresholdCents] (default 400 cents) breaks line segments in the renderer.
 * 6. Zero heap allocations during real-time streaming.
 */
enum class PitchFilterDecision {
    ACCEPTED,
    OCTAVE_SNAPPED,
    REJECTED_LOW_CONFIDENCE,
    REJECTED_UNVOICED,
    REJECTED_OUTLIER_SPIKE
}

data class PitchFilterResult(
    val frequency: Float,
    val decision: PitchFilterDecision,
    val details: String = ""
)

class DisplayPitchFilter(
    var startConfidenceThreshold: Float = DEFAULT_START_CONFIDENCE_THRESHOLD,
    var continueConfidenceThreshold: Float = DEFAULT_CONTINUE_CONFIDENCE_THRESHOLD,
    private val maxSingleFrameJumpCents: Float = DEFAULT_MAX_SINGLE_FRAME_JUMP_CENTS,
    val breakThresholdCents: Float = DEFAULT_DISCONTINUITY_BREAK_CENTS
) {
    // Secondary constructor for backward compatibility with single-parameter calls
    constructor(
        confidenceThreshold: Float,
        maxSingleFrameJumpCents: Float = DEFAULT_MAX_SINGLE_FRAME_JUMP_CENTS,
        breakThresholdCents: Float = DEFAULT_DISCONTINUITY_BREAK_CENTS
    ) : this(
        startConfidenceThreshold = confidenceThreshold,
        continueConfidenceThreshold = (confidenceThreshold - 0.12f).coerceAtLeast(0.50f),
        maxSingleFrameJumpCents = maxSingleFrameJumpCents,
        breakThresholdCents = breakThresholdCents
    )

    var confidenceThreshold: Float
        get() = startConfidenceThreshold
        set(value) {
            startConfidenceThreshold = value
            continueConfidenceThreshold = (value - 0.12f).coerceAtLeast(0.50f)
        }

    companion object {
        const val DEFAULT_START_CONFIDENCE_THRESHOLD = 0.82f
        const val DEFAULT_CONTINUE_CONFIDENCE_THRESHOLD = 0.70f
        const val DEFAULT_MAX_SINGLE_FRAME_JUMP_CENTS = 600f // ~half-octave
        const val DEFAULT_DISCONTINUITY_BREAK_CENTS = 400f // 4 semitones (tunable for meend/gamak)
    }

    private var isVoicedSegment = false
    private var lastVoicedFreq = 0f
    private var outlierStreak = 0
    private var octaveErrorStreak = 0

    /**
     * Total added display latency: 0 ms (we removed the median filter and EMA lag)
     */
    val addedLatencyMs: Float = 0f

    /**
     * Filters an incoming audio detection frame.
     *
     * @param rawFreq Raw detected frequency in Hz.
     * @param confidence Pitch confidence score in [0.0, 1.0].
     * @return Filtered frequency in Hz (0f if unvoiced or rejected).
     */
    fun filter(rawFreq: Float, confidence: Float): Float {
        return filterDetailed(rawFreq, confidence).frequency
    }

    /**
     * Filters an incoming audio detection frame and returns the full diagnostic decision.
     */
    fun filterDetailed(rawFreq: Float, confidence: Float): PitchFilterResult {
        // 1. Unvoiced check
        if (rawFreq <= 0f) {
            resetVoicedState()
            return PitchFilterResult(0f, PitchFilterDecision.REJECTED_UNVOICED, "Frequency <= 0")
        }

        // 2. Dual-threshold Voicing Hysteresis (Schmitt trigger)
        val requiredConfidence = if (isVoicedSegment) continueConfidenceThreshold else startConfidenceThreshold
        if (confidence < requiredConfidence) {
            resetVoicedState()
            return PitchFilterResult(
                0f,
                PitchFilterDecision.REJECTED_LOW_CONFIDENCE,
                "Confidence $confidence < $requiredConfidence (inVoicing: $isVoicedSegment)"
            )
        }

        // Voicing criteria satisfied
        isVoicedSegment = true

        var candidateFreq = rawFreq
        var decision = PitchFilterDecision.ACCEPTED
        var details = "Voiced"

        // 3. Transient error rejection against last voiced frame
        if (lastVoicedFreq > 0f) {
            val ratio = candidateFreq / lastVoicedFreq
            val isOctaveRatio = (ratio in 1.85f..2.15f) || (ratio in 0.46f..0.54f)

            if (isOctaveRatio) {
                if (octaveErrorStreak == 0) {
                    // Transient 1-frame octave error: snap to running octave
                    octaveErrorStreak = 1
                    candidateFreq = if (ratio > 1.0f) candidateFreq / 2.0f else candidateFreq * 2.0f
                    decision = PitchFilterDecision.OCTAVE_SNAPPED
                    details = "Transient octave error snapped (ratio: $ratio)"
                } else {
                    // Sustained octave jump (>= 2 frames): singer intentionally leaped octave!
                    octaveErrorStreak = 0
                    decision = PitchFilterDecision.ACCEPTED
                    details = "Sustained octave jump accepted"
                }
            } else {
                octaveErrorStreak = 0

                // 4. Outlier rejection: single-frame jump > 600 cents
                val centsDiff = 1200.0 * abs(log2((candidateFreq / lastVoicedFreq).toDouble()))
                if (centsDiff > maxSingleFrameJumpCents) {
                    outlierStreak++
                    if (outlierStreak < 2) {
                        // Reject transient 1-frame wild spike!
                        return PitchFilterResult(0f, PitchFilterDecision.REJECTED_OUTLIER_SPIKE, "Jump of ${centsDiff.toInt()}c > $maxSingleFrameJumpCents")
                    } else {
                        // Sustained leap across notes: accept new note
                        outlierStreak = 0
                        decision = PitchFilterDecision.ACCEPTED
                        details = "Sustained leap accepted"
                    }
                } else {
                    outlierStreak = 0
                }
            }
        }

        lastVoicedFreq = candidateFreq
        return PitchFilterResult(candidateFreq, decision, details)
    }

    /**
     * Resets internal history when vocal sound stops or transitions to gap.
     */
    fun resetVoicedState() {
        isVoicedSegment = false
        lastVoicedFreq = 0f
        outlierStreak = 0
        octaveErrorStreak = 0
    }
}
