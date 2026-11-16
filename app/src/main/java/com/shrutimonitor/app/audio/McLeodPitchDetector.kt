package com.shrutimonitor.app.audio

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-Performance Zero-Allocation McLeod Pitch Method (MPM) Engine.
 *
 * Uses an in-place Radix-2 Cooley-Tukey FFT (N = 4096 zero-padded) to compute
 * the exact autocorrelation r(t) via the Wiener-Khinchin theorem in O(N log N) time,
 * followed by O(N) cumulative square-sum m(t) recurrence and parabolic peak interpolation.
 * Also extracts real-time harmonic overtone energy from the NSDF half-lag correlation.
 */
class McLeodPitchDetector(
    private val sampleRate: Int = SAMPLE_RATE,
    @Volatile var clarityThreshold: Float = DEFAULT_CLARITY_THRESHOLD,
    @Volatile var minFrequency: Float = MIN_FREQUENCY,
    @Volatile var maxFrequency: Float = MAX_FREQUENCY,
    @Volatile var rmsGate: Float = 0.002f
) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val DEFAULT_CLARITY_THRESHOLD = 0.85f
        const val VOICING_THRESHOLD = 0.50f
        const val MIN_FREQUENCY = 50.0f
        const val MAX_FREQUENCY = 2800.0f
        private const val MAX_BUFFER_SIZE = 2048
        private const val FFT_SIZE = 4096 // 2 * MAX_BUFFER_SIZE for linear autocorrelation without circular wrap
    }

    // Pre-allocated zero-GC working buffers
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val nsdf = FloatArray(MAX_BUFFER_SIZE)
    private val cumulativeSq = FloatArray(MAX_BUFFER_SIZE + 1)
    private val peakIndices = IntArray(512)

    // Precomputed Radix-2 twiddle factors & bit-reversal table
    private val bitReverse = IntArray(FFT_SIZE)
    private val cosTable = FloatArray(FFT_SIZE / 2)
    private val sinTable = FloatArray(FFT_SIZE / 2)

    init {
        val bits = 12 // 2^12 = 4096
        for (i in 0 until FFT_SIZE) {
            var rev = 0
            var x = i
            for (b in 0 until bits) {
                rev = (rev shl 1) or (x and 1)
                x = x shr 1
            }
            bitReverse[i] = rev
        }
        val twoPiOverN = 2.0 * Math.PI / FFT_SIZE
        for (i in 0 until FFT_SIZE / 2) {
            val angle = twoPiOverN * i
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
    }

    /**
     * Detects the fundamental pitch of [buffer] with zero heap allocations.
     */
    @Synchronized
    fun detectPitch(buffer: FloatArray): PitchResult {
        val size = min(buffer.size, MAX_BUFFER_SIZE)
        val maxLag = (sampleRate / minFrequency).toInt().coerceAtLeast(4)
        val minLag = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
        val limit = min(size / 2, maxLag)

        if (size < 2 * limit || limit <= minLag) {
            return PitchResult(0.0f, 0.0f, false, 0.0f, 0.0f)
        }

        // 1. Compute prefix sum of squares for O(1) m(t) calculation + RMS energy
        var totalEnergy = 0.0f
        cumulativeSq[0] = 0.0f
        for (i in 0 until size) {
            val s = buffer[i]
            val sq = s * s
            totalEnergy += sq
            cumulativeSq[i + 1] = cumulativeSq[i] + sq
        }

        val rms = sqrt(totalEnergy / size)
        if (totalEnergy < 1e-5f || rms < rmsGate) {
            return PitchResult(0.0f, 0.0f, false, rms, 0.0f)
        }

        // 2. Compute exact Autocorrelation r(t) via FFT (Wiener-Khinchin Theorem)
        for (i in 0 until FFT_SIZE) {
            val srcIdx = bitReverse[i]
            fftReal[i] = if (srcIdx < size) buffer[srcIdx] else 0.0f
            fftImag[i] = 0.0f
        }

        inPlaceFft(inverse = false)

        // Power spectrum |X[k]|^2
        for (i in 0 until FFT_SIZE) {
            val re = fftReal[i]
            val im = fftImag[i]
            fftReal[i] = re * re + im * im
            fftImag[i] = 0.0f
        }

        // Re-apply bit-reversal before inverse FFT
        for (i in 0 until FFT_SIZE) {
            val j = bitReverse[i]
            if (i < j) {
                val tmp = fftReal[i]
                fftReal[i] = fftReal[j]
                fftReal[j] = tmp
            }
        }

        inPlaceFft(inverse = true)

        // 3. Build NSDF(t) = 2 * r(t) / m(t) in O(limit)
        nsdf[0] = 1.0f
        val totalSq = cumulativeSq[size]
        for (t in 1 until limit) {
            val r_t = fftReal[t]
            val m_t = cumulativeSq[size - t] + (totalSq - cumulativeSq[t])
            nsdf[t] = if (m_t > 1e-6f) (2.0f * r_t / m_t).coerceIn(-1.0f, 1.0f) else 0.0f
        }

        // 4. Find key maxima (positive-lobe peaks)
        var peakCount = 0
        var highestPeakValue = -1.0f

        var t = minLag
        while (t < limit - 1 && nsdf[t] > 0.0f) {
            if (nsdf[t] > nsdf[t - 1] && nsdf[t] >= nsdf[t + 1]) {
                if (peakCount < peakIndices.size) {
                    peakIndices[peakCount++] = t
                    if (nsdf[t] > highestPeakValue) highestPeakValue = nsdf[t]
                }
            }
            t++
        }

        while (t < limit - 1) {
            while (t < limit - 1 && nsdf[t] <= 0.0f) t++
            var maxLagInLobe = -1
            var maxValInLobe = 0.0f
            while (t < limit - 1 && nsdf[t] > 0.0f) {
                if (nsdf[t] > nsdf[t - 1] && nsdf[t] >= nsdf[t + 1]) {
                    if (nsdf[t] > maxValInLobe) {
                        maxValInLobe = nsdf[t]
                        maxLagInLobe = t
                    }
                }
                t++
            }
            if (maxLagInLobe != -1 && peakCount < peakIndices.size) {
                peakIndices[peakCount++] = maxLagInLobe
                if (maxValInLobe > highestPeakValue) {
                    highestPeakValue = maxValInLobe
                }
            }
        }

        if (peakCount == 0 || highestPeakValue < VOICING_THRESHOLD) {
            return PitchResult(0.0f, 0.0f, false, rms, 0.0f)
        }

        // 5. Select first peak exceeding clarityThreshold * highestPeakValue
        val threshold = clarityThreshold * highestPeakValue
        var selectedPeak = peakIndices[0]
        for (i in 0 until peakCount) {
            val pk = peakIndices[i]
            if (nsdf[pk] >= threshold) {
                selectedPeak = pk
                break
            }
        }

        // 6. Parabolic interpolation for sub-sample precision
        val alpha = nsdf[selectedPeak - 1]
        val beta = nsdf[selectedPeak]
        val gamma = nsdf[selectedPeak + 1]

        val denominator = alpha - 2.0f * beta + gamma
        val delta = if (kotlin.math.abs(denominator) > 1e-7f) {
            0.5f * (alpha - gamma) / denominator
        } else {
            0.0f
        }

        val refinedLag = selectedPeak.toFloat() + delta.coerceIn(-0.5f, 0.5f)
        val frequency = sampleRate.toFloat() / refinedLag
        val confidence = (beta + 0.25f * delta * (alpha - gamma)).coerceIn(0.0f, 1.0f)

        // Estimate harmonic richness from half-lag (2nd harmonic) correlation
        val halfLag = (selectedPeak / 2).coerceAtLeast(1)
        val harmonicBrightness = ((nsdf[halfLag] + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)

        val isVoiced = frequency in minFrequency..maxFrequency && confidence >= VOICING_THRESHOLD

        return if (isVoiced) {
            PitchResult(frequency, confidence, true, rms, harmonicBrightness)
        } else {
            PitchResult(0.0f, confidence, false, rms, harmonicBrightness)
        }
    }

    private fun inPlaceFft(inverse: Boolean) {
        var halfSize = 1
        var tableStep = FFT_SIZE / 2
        while (halfSize < FFT_SIZE) {
            val step = halfSize shl 1
            var i = 0
            while (i < FFT_SIZE) {
                var k = 0
                for (j in i until i + halfSize) {
                    val l = j + halfSize
                    val twiddleCos = cosTable[k]
                    val twiddleSin = if (inverse) sinTable[k] else -sinTable[k]

                    val tre = fftReal[l] * twiddleCos - fftImag[l] * twiddleSin
                    val tim = fftReal[l] * twiddleSin + fftImag[l] * twiddleCos

                    fftReal[l] = fftReal[j] - tre
                    fftImag[l] = fftImag[j] - tim
                    fftReal[j] += tre
                    fftImag[j] += tim
                    k += tableStep
                }
                i += step
            }
            halfSize = step
            tableStep = tableStep shr 1
        }
        if (inverse) {
            val scale = 1.0f / FFT_SIZE
            for (i in 0 until FFT_SIZE) {
                fftReal[i] *= scale
            }
        }
    }
}