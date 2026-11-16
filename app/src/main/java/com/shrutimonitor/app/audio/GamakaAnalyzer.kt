package com.shrutimonitor.app.audio

import kotlin.math.abs
import kotlin.math.log2

/**
 * Zero-allocation sliding window analyzer that classifies Carnatic & Hindustani
 * microtonal ornaments (Kampita oscillation, Jaaru/Meend glide, Spurita accent, or Steady hold)
 * over the last ~450ms of voiced frames.
 */
class GamakaAnalyzer {
    companion object {
        private const val WINDOW_CAPACITY = 20 // ~460ms at 43Hz hop rate
    }

    private val centsWindow = FloatArray(WINDOW_CAPACITY)
    private val timesWindow = LongArray(WINDOW_CAPACITY)
    private var head = 0
    private var count = 0

    @Synchronized
    fun reset() {
        head = 0
        count = 0
    }

    @Synchronized
    fun pushAndAnalyze(timestampMs: Long, freqHz: Float, saFreqHz: Float): GamakaMetrics {
        if (freqHz <= 0f || saFreqHz <= 0f) {
            reset()
            return GamakaMetrics()
        }

        val cents = (1200.0 * log2(freqHz.toDouble() / saFreqHz.toDouble())).toFloat()
        centsWindow[head] = cents
        timesWindow[head] = timestampMs
        head = (head + 1) % WINDOW_CAPACITY
        if (count < WINDOW_CAPACITY) count++

        if (count < 6) {
            return GamakaMetrics(GamakaType.STEADY, 0f, 0f, 100f)
        }

        val startIdx = (head - count + WINDOW_CAPACITY) % WINDOW_CAPACITY
        var minCents = Float.MAX_VALUE
        var maxCents = -Float.MAX_VALUE
        var sumCents = 0f

        for (i in 0 until count) {
            val c = centsWindow[(startIdx + i) % WINDOW_CAPACITY]
            if (c < minCents) minCents = c
            if (c > maxCents) maxCents = c
            sumCents += c
        }

        val meanCents = sumCents / count
        val excursion = maxCents - minCents
        val durationSec = ((timesWindow[(startIdx + count - 1) % WINDOW_CAPACITY] - timesWindow[startIdx]).coerceAtLeast(1L)) / 1000.0f

        // Count zero-crossings around meanCents to estimate Kampita oscillation frequency
        var zeroCrossings = 0
        var prevDiff = centsWindow[startIdx] - meanCents
        for (i in 1 until count) {
            val currDiff = centsWindow[(startIdx + i) % WINDOW_CAPACITY] - meanCents
            if ((prevDiff < -4f && currDiff > 4f) || (prevDiff > 4f && currDiff < -4f)) {
                zeroCrossings++
                prevDiff = currDiff
            } else if (abs(currDiff) > 4f) {
                prevDiff = currDiff
            }
        }

        val netDisplacement = abs(centsWindow[(startIdx + count - 1) % WINDOW_CAPACITY] - centsWindow[startIdx])
        val oscRateHz = if (durationSec > 0.08f) (zeroCrossings / 2.0f) / durationSec else 0f

        val type = when {
            excursion <= 18f -> GamakaType.STEADY
            zeroCrossings >= 3 && excursion in 22f..320f -> GamakaType.KAMPITA
            netDisplacement > excursion * 0.72f && excursion > 45f -> GamakaType.JAARU
            excursion > 25f -> GamakaType.SPURITA
            else -> GamakaType.STEADY
        }

        val stability = if (type == GamakaType.STEADY) {
            (100f - excursion * 2.2f).coerceIn(0f, 100f)
        } else {
            (100f - (excursion - 100f).coerceAtLeast(0f) * 0.2f).coerceIn(50f, 100f)
        }

        return GamakaMetrics(
            type = type,
            oscillationRateHz = oscRateHz,
            excursionCents = excursion,
            stabilityScore = stability
        )
    }
}
