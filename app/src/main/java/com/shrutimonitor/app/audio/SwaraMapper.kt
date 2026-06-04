package com.shrutimonitor.app.audio

import com.shrutimonitor.app.data.Swara
import kotlin.math.floor
import kotlin.math.log2

/**
 * Maps a frequency in Hz to its nearest Just Intonation swara, given a base Sa frequency.
 */
class SwaraMapper {

    companion object {
        const val DEFAULT_SA_FREQUENCY = 261.63f // Middle C (C4)

        /**
         * Calculates the ideal Just Intonation frequency of a swara in a given saptak,
         * relative to the base Sa frequency.
         */
        fun idealFrequency(swara: Swara, saptak: Saptak, saFrequency: Float): Float {
            val octaveOffset = saptak.ordinal - Saptak.MADHYA.ordinal
            val octaveMultiplier = Math.pow(2.0, octaveOffset.toDouble())
            return (saFrequency.toDouble() * swara.jiRatio * octaveMultiplier).toFloat()
        }
    }

    /**
     * Maps the detected frequency to a [SwaraResult].
     *
     * @param saFrequency The base tonic frequency (Sa) in Hz.
     * @param detectedFrequency The frequency to map in Hz.
     * @return The [SwaraResult] containing the mapped swara, cent deviation, saptak, etc.
     */
    fun mapFrequency(saFrequency: Float, detectedFrequency: Float): SwaraResult {
        val timestamp = System.currentTimeMillis()
        
        if (detectedFrequency <= 0.0f || saFrequency <= 0.0f) {
            // Return dummy/fallback result
            return SwaraResult(
                swara = Swara.SA,
                swaraIndex = 0,
                centDeviation = 0.0f,
                frequency = detectedFrequency,
                idealFrequency = saFrequency,
                saptak = Saptak.MADHYA,
                timestamp = timestamp
            )
        }

        // Calculate cents from Sa
        val centsFromSa = 1200.0 * log2(detectedFrequency.toDouble() / saFrequency.toDouble())
        
        // Find the rough octave offset
        val baseOctave = floor(centsFromSa / 1200.0).toInt()

        var bestSwara = Swara.SA
        var bestOctave = baseOctave
        var minDiff = Double.MAX_VALUE
        var bestIdealCents = 0.0

        // Test the swaras in the base octave and its neighboring octaves
        for (oct in (baseOctave - 1)..(baseOctave + 1)) {
            for (swara in Swara.entries) {
                val swaraCents = Swara.justIntonationCents(swara)
                val idealCents = oct * 1200.0 + swaraCents
                val diff = Math.abs(centsFromSa - idealCents)
                if (diff < minDiff) {
                    minDiff = diff
                    bestSwara = swara
                    bestOctave = oct
                    bestIdealCents = idealCents
                }
            }
        }

        // Signed deviation from the nearest swara
        val centDeviation = (centsFromSa - bestIdealCents).toFloat()

        // Map octave offset to Saptak enum
        val saptak = when {
            bestOctave < 0 -> Saptak.MANDRA
            bestOctave > 0 -> Saptak.TARA
            else -> Saptak.MADHYA
        }

        // Ideal frequency: Sa * swaraRatio * 2^octave
        val octaveMultiplier = Math.pow(2.0, bestOctave.toDouble())
        val idealFrequency = (saFrequency.toDouble() * bestSwara.jiRatio * octaveMultiplier).toFloat()

        return SwaraResult(
            swara = bestSwara,
            swaraIndex = bestSwara.index,
            centDeviation = centDeviation,
            frequency = detectedFrequency,
            idealFrequency = idealFrequency,
            saptak = saptak,
            timestamp = timestamp
        )
    }
}