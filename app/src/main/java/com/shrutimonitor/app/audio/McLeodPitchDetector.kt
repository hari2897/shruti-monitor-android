package com.shrutimonitor.app.audio

import kotlin.math.max

/**
 * An implementation of the McLeod Pitch Method (MPM) for real-time pitch detection.
 *
 * MPM computes the Normalized Square Difference Function (NSDF), finds key maxima,
 * and performs parabolic interpolation to estimate the fundamental frequency with sub-sample precision.
 */
class McLeodPitchDetector(
    private val sampleRate: Int = SAMPLE_RATE,
    private val clarityThreshold: Float = DEFAULT_CLARITY_THRESHOLD
) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val DEFAULT_CLARITY_THRESHOLD = 0.85f // Threshold to select the first peak vs. absolute maximum
        const val VOICING_THRESHOLD = 0.50f        // Minimum NSDF peak value to consider a signal voiced
        const val MIN_FREQUENCY = 50.0f             // Hz
        const val MAX_FREQUENCY = 2000.0f           // Hz
    }

    // Pre-calculated lag boundaries based on sample rate and target frequency range
    private val maxLag = (sampleRate / MIN_FREQUENCY).toInt() // e.g. 44100 / 50 = 882
    private val minLag = (sampleRate / MAX_FREQUENCY).toInt() // e.g. 44100 / 2000 = 22

    /**
     * Detects the pitch of a given buffer of audio samples.
     *
     * @param buffer Input float audio samples (typically 2048 samples).
     * @return [PitchResult] containing frequency in Hz, confidence, and voiced status.
     */
    fun detectPitch(buffer: FloatArray): PitchResult {
        val size = buffer.size
        // We limit our NSDF calculation up to the max lag required for MIN_FREQUENCY
        val limit = minOf(size / 2, maxLag)
        
        if (size < 2 * limit) {
            return PitchResult(0.0f, 0.0f, false)
        }

        // 1. Compute NSDF
        val nsdf = FloatArray(limit)
        
        // Calculate r(0) and m(0) to verify we have non-silent audio
        var r0 = 0.0f
        for (i in 0 until size - limit) {
            r0 += buffer[i] * buffer[i]
        }
        
        if (r0 < 1e-5f) {
            return PitchResult(0.0f, 0.0f, false) // Silent buffer
        }

        nsdf[0] = 1.0f

        // Compute r(t) and m(t) for each lag t
        for (t in 1 until limit) {
            var r_t = 0.0f
            var m_t = 0.0f
            for (i in 0 until size - t) {
                r_t += buffer[i] * buffer[i + t]
                m_t += buffer[i] * buffer[i] + buffer[i + t] * buffer[i + t]
            }
            if (m_t > 1e-6f) {
                nsdf[t] = 2.0f * r_t / m_t
            } else {
                nsdf[t] = 0.0f
            }
        }

        // 2. Find peaks (local maxima)
        val peaks = mutableListOf<Int>()
        var highestPeakValue = -1.0f
        
        // Determine peaks where the NSDF crosses positive/negative boundaries
        var isIncreasing = false
        for (t in minLag until limit - 1) {
            if (nsdf[t] > nsdf[t - 1] && nsdf[t] >= nsdf[t + 1]) {
                // We have a peak
                if (nsdf[t] > 0.0f) {
                    peaks.add(t)
                    if (nsdf[t] > highestPeakValue) {
                        highestPeakValue = nsdf[t]
                    }
                }
            }
        }

        if (peaks.isEmpty() || highestPeakValue < VOICING_THRESHOLD) {
            return PitchResult(0.0f, 0.0f, false)
        }

        // 3. Select the best peak
        // The first peak above clarityThreshold * highestPeakValue is selected
        // to avoid octave-halving errors.
        val threshold = clarityThreshold * highestPeakValue
        var selectedPeak = -1
        for (peak in peaks) {
            if (nsdf[peak] >= threshold) {
                selectedPeak = peak
                break
            }
        }

        if (selectedPeak == -1) {
            selectedPeak = peaks[0]
        }

        // 4. Parabolic interpolation
        val alpha = nsdf[selectedPeak - 1]
        val beta = nsdf[selectedPeak]
        val gamma = nsdf[selectedPeak + 1]
        
        val denominator = alpha - 2.0f * beta + gamma
        val delta = if (denominator != 0.0f) {
            0.5f * (alpha - gamma) / denominator
        } else {
            0.0f
        }

        val refinedLag = selectedPeak.toFloat() + delta
        val frequency = sampleRate.toFloat() / refinedLag
        val confidence = beta + 0.5f * delta * (alpha - gamma)

        val isVoiced = frequency in MIN_FREQUENCY..MAX_FREQUENCY && confidence >= VOICING_THRESHOLD

        return if (isVoiced) {
            PitchResult(frequency, confidence, true)
        } else {
            PitchResult(0.0f, confidence, false)
        }
    }
}