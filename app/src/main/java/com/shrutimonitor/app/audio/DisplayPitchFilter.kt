package com.shrutimonitor.app.audio

import kotlin.math.abs
import kotlin.math.log2

/**
 * Display-side pitch stream filter for smoothing and glitch/octave-error rejection.
 *
 * Replicates the filtering and line-break architecture of Vocal Pitch Monitor (VPM):
 * 1. Confidence gating: frames below [confidenceThreshold] or unvoiced become gaps (0 Hz).
 * 2. 3-frame running median filter (capped latency: 1 frame delay ≈ 23ms).
 * 3. Transient vs. Sustained Octave Error Handling:
 *    - Single-frame 2x / 0.5x harmonic glitches are snapped to the running median octave.
 *    - Sustained octave leaps (>= 2 frames) are accepted as genuine vocal octave transitions.
 * 4. Outlier rejection: single-frame jumps > [maxSingleFrameJumpCents] (default 600 cents) are suppressed.
 *    Sustained leaps (>= 2 frames) are accepted.
 * 5. Light temporal exponential smoothing (EMA, tau ≈ 20ms) on continuous voiced notes.
 * 6. Tunable discontinuity threshold: [breakThresholdCents] (default 400 cents) breaks line segments
 *    and resets EMA so real leaps/meends never lag or smear.
 * 7. Zero heap allocations during real-time streaming.
 */
class DisplayPitchFilter(
    var confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val emaAlpha: Float = DEFAULT_EMA_ALPHA,
    private val maxSingleFrameJumpCents: Float = DEFAULT_MAX_SINGLE_FRAME_JUMP_CENTS,
    val breakThresholdCents: Float = DEFAULT_DISCONTINUITY_BREAK_CENTS
) {
    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.85f
        const val DEFAULT_EMA_ALPHA = 0.65f // tau ≈ 20ms at 43Hz
        const val DEFAULT_MAX_SINGLE_FRAME_JUMP_CENTS = 600f // ~half-octave in 23ms
        const val DEFAULT_DISCONTINUITY_BREAK_CENTS = 400f // 4 semitones (tunable for meend/gamak)
    }

    // 3-frame circular buffer for running median calculation (1 frame delay ≈ 23ms)
    private val medianBuf = FloatArray(3)
    private val sortBuf = FloatArray(3)
    private var medianBufCount = 0
    private var medianBufHead = 0

    private var smoothedFreq = 0f
    private var lastVoicedFreq = 0f
    private var outlierStreak = 0
    private var octaveErrorStreak = 0

    /**
     * Total added display latency estimation:
     * - 3-frame median filter: 1 frame delay ≈ 23 ms
     * - Light EMA (alpha 0.65): time constant tau ≈ 20 ms
     * Total added display latency ≈ 23 - 40 ms (well within musical singing feedback threshold).
     */
    val addedLatencyMs: Float = 23f + 20f

    /**
     * Filters an incoming audio detection frame.
     *
     * @param rawFreq Raw detected frequency in Hz.
     * @param confidence Pitch confidence score in [0.0, 1.0].
     * @return Filtered frequency in Hz (0f if unvoiced or rejected).
     */
    fun filter(rawFreq: Float, confidence: Float): Float {
        // 1. Confidence gating: below-threshold frames become gaps
        if (confidence < confidenceThreshold || rawFreq <= 0f) {
            resetVoicedState()
            return 0f
        }

        var candidateFreq = rawFreq

        // 2. Compute running median if we have previous voiced history
        if (medianBufCount > 0) {
            val median = computeMedian()

            if (median > 0f) {
                val ratio = candidateFreq / median
                val isOctaveRatio = (ratio in 1.85f..2.15f) || (ratio in 0.46f..0.54f)

                if (isOctaveRatio) {
                    if (octaveErrorStreak == 0) {
                        // Transient 1-frame octave error: snap to running median octave
                        octaveErrorStreak = 1
                        candidateFreq = if (ratio > 1.0f) candidateFreq / 2.0f else candidateFreq * 2.0f
                    } else {
                        // Sustained octave jump (>= 2 frames): singer intentionally leaped octave!
                        // Accept the new octave and reset median history to the new octave
                        octaveErrorStreak = 0
                        resetMedianHistory(candidateFreq)
                    }
                } else {
                    octaveErrorStreak = 0

                    // 4. Outlier rejection: single-frame jump > 600 cents
                    val centsDiff = 1200.0 * abs(log2((candidateFreq / median).toDouble()))
                    if (centsDiff > maxSingleFrameJumpCents) {
                        outlierStreak++
                        if (outlierStreak < 2) {
                            // Reject transient 1-frame wild spike!
                            return 0f
                        } else {
                            // Sustained leap across notes: accept new note and reset median
                            outlierStreak = 0
                            resetMedianHistory(candidateFreq)
                        }
                    } else {
                        outlierStreak = 0
                    }
                }
            }
        }

        // Add accepted candidate to 3-frame median buffer
        pushMedian(candidateFreq)

        // 5. Light EMA temporal smoothing
        if (lastVoicedFreq <= 0f) {
            // Starting from gap: no EMA
            smoothedFreq = candidateFreq
        } else {
            val centsFromPrev = 1200.0 * abs(log2((candidateFreq / lastVoicedFreq).toDouble()))
            if (centsFromPrev > breakThresholdCents) {
                // Large jump (> breakThresholdCents): break discontinuity, do not smooth across boundary
                smoothedFreq = candidateFreq
            } else {
                smoothedFreq = emaAlpha * candidateFreq + (1f - emaAlpha) * smoothedFreq
            }
        }

        lastVoicedFreq = smoothedFreq
        return smoothedFreq
    }

    private fun pushMedian(freq: Float) {
        medianBuf[medianBufHead] = freq
        medianBufHead = (medianBufHead + 1) % 3
        if (medianBufCount < 3) {
            medianBufCount++
        }
    }

    private fun computeMedian(): Float {
        if (medianBufCount == 0) return 0f
        for (i in 0 until medianBufCount) {
            sortBuf[i] = medianBuf[i]
        }
        // Small in-place insertion sort (3 elements, ~5 CPU cycles, 0 allocations)
        for (i in 1 until medianBufCount) {
            val key = sortBuf[i]
            var j = i - 1
            while (j >= 0 && sortBuf[j] > key) {
                sortBuf[j + 1] = sortBuf[j]
                j--
            }
            sortBuf[j + 1] = key
        }
        return sortBuf[medianBufCount / 2]
    }

    private fun resetMedianHistory(seedFreq: Float) {
        medianBuf[0] = seedFreq
        medianBufCount = 1
        medianBufHead = 1
    }

    /**
     * Resets internal history when vocal sound stops or transitions to gap.
     */
    fun resetVoicedState() {
        medianBufCount = 0
        medianBufHead = 0
        lastVoicedFreq = 0f
        smoothedFreq = 0f
        outlierStreak = 0
        octaveErrorStreak = 0
    }
}
