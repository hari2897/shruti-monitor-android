package com.shrutimonitor.app.data

import kotlin.math.floor
import kotlin.math.pow

/**
 * Tuning systems and Shruti ratio presets supported by Shruti Monitor.
 *
 * Implements:
 * 1. [HARMONIC_5LIMIT]: 5-Limit Just Intonation using pure harmonic intervals
 *    (G2 = 6/5, D1 = 8/5, N2 = 9/5). Eliminates beating against the drone; common in Hindustani & vocal riyaz.
 * 2. [PYTHAGOREAN_3LIMIT]: 3-Limit Just Intonation derived via the cycle of 4ths & 5ths
 *    (G2 = 32/27, D1 = 128/81, N2 = 16/9). Aligns with standard Carnatic Veena swarasthana charts and treatises.
 *    The difference is precisely the Pramāna Shruti (Syntonic Comma 81/80 ≈ 21.51 cents).
 * 3. [EQUAL_TEMPERAMENT]: 12-Tone Equal Temperament (12-EDO / Western) with equal 100¢ semitones.
 */
enum class TuningPreset(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String
) {
    HARMONIC_5LIMIT(
        id = "harmonic_5limit",
        displayName = "Harmonic (5-Limit JI)",
        shortName = "5-Limit JI",
        description = "Pure harmonic thirds & fifths. Consonant with drone; common in Hindustani & vocal riyaz (G2 = 6/5, 315.6¢)."
    ),
    PYTHAGOREAN_3LIMIT(
        id = "pythagorean_3limit",
        displayName = "Pythagorean (3-Limit JI)",
        shortName = "3-Limit JI",
        description = "Derived via cycle of 4ths & 5ths. Aligns with standard Carnatic Veena swarasthana charts & classical treatises (G2 = 32/27, 294.1¢)."
    ),
    EQUAL_TEMPERAMENT(
        id = "equal_temperament",
        displayName = "12-EDO / Western (Equal Temperament)",
        shortName = "12-EDO",
        description = "Standard chromatic reference: equal 100¢ semitones (G2 = 300¢)."
    );

    /**
     * Returns the frequency ratio relative to Sa for a given swara under this tuning preset.
     */
    fun ratioForSwara(swara: Swara): Double {
        return when (this) {
            HARMONIC_5LIMIT -> swara.jiRatio
            PYTHAGOREAN_3LIMIT -> when (swara) {
                Swara.KOMAL_GA -> 32.0 / 27.0
                Swara.KOMAL_DHA -> 128.0 / 81.0
                Swara.KOMAL_NI -> 16.0 / 9.0
                else -> swara.jiRatio
            }
            EQUAL_TEMPERAMENT -> 2.0.pow(swara.index / 12.0)
        }
    }

    /**
     * Returns the cent value (relative to Sa) for a given swara under this tuning preset.
     */
    fun centsForSwara(swara: Swara): Double {
        return when (this) {
            HARMONIC_5LIMIT -> Swara.justIntonationCents(swara)
            PYTHAGOREAN_3LIMIT -> 1200.0 * (kotlin.math.ln(ratioForSwara(swara)) / kotlin.math.ln(2.0))
            EQUAL_TEMPERAMENT -> swara.index * 100.0
        }
    }

    /**
     * 13-element array of swara cents from Sa (index 0 = 0.0¢) through Upper Sa (index 12 = 1200.0¢).
     */
    val centsArray: DoubleArray by lazy {
        DoubleArray(13) { i ->
            if (i == 12) 1200.0 else centsForSwara(Swara.fromIndex(i))
        }
    }

    /**
     * Maps actual cents relative to Sa into visual cents where each semitone occupies 100 visual cents,
     * interpolating continuously between swaras based on this preset's exact cents scale.
     */
    fun actualToVisualCents(actualCents: Double): Double {
        val octave = floor(actualCents / 1200.0).toInt()
        val remainder = actualCents - octave * 1200.0
        val table = centsArray

        var k = 0
        while (k < 12 && remainder >= table[k + 1]) {
            k++
        }

        val lowJI = table[k]
        val highJI = table[k + 1]
        val lowVisual = k * 100.0
        val highVisual = (k + 1) * 100.0

        val range = highJI - lowJI
        val fraction = if (range > 1e-9) (remainder - lowJI) / range else 0.0
        val visualRemainder = lowVisual + fraction * (highVisual - lowVisual)

        return octave * 1200.0 + visualRemainder
    }

    fun actualToVisualCents(actualCents: Float): Float {
        return actualToVisualCents(actualCents.toDouble()).toFloat()
    }

    companion object {
        // Backwards compatibility alias
        val STANDARD: TuningPreset get() = HARMONIC_5LIMIT

        /**
         * Resolves preset from string identifier or returns [HARMONIC_5LIMIT] as fallback.
         */
        fun fromId(id: String?): TuningPreset {
            val clean = id?.lowercase() ?: return HARMONIC_5LIMIT
            return when {
                clean.contains("3limit") || clean.contains("pythagorean") || clean.contains("carnatic") -> PYTHAGOREAN_3LIMIT
                clean.contains("equal") || clean.contains("12edo") || clean.contains("western") -> EQUAL_TEMPERAMENT
                clean.contains("5limit") || clean.contains("harmonic") || clean.contains("standard") -> HARMONIC_5LIMIT
                else -> HARMONIC_5LIMIT
            }
        }

        fun actualToVisualCents(actualCents: Double, preset: TuningPreset = HARMONIC_5LIMIT): Double {
            return preset.actualToVisualCents(actualCents)
        }

        fun actualToVisualCents(actualCents: Float, preset: TuningPreset = HARMONIC_5LIMIT): Float {
            return preset.actualToVisualCents(actualCents)
        }
    }
}

/**
 * Complete 22-Shruti (Dvaavimshati Shruti) microtonal reference table
 * based on Bharata / Sarangadeva & Sambamoorthy's Carnatic-Hindustani Shruti ratios.
 */
enum class Shruti22(
    val shrutiNumber: Int,
    val sanskritName: String,
    val swaraIndex: Int,
    val ratio: Double
) {
    TIVRA(1, "Tivra (Sa)", 0, 1.0),
    KUMUDVATI(2, "Kumudvati (R1-)", 1, 256.0 / 243.0),
    MANDA(3, "Manda (R1+)", 1, 16.0 / 15.0),
    CHANDOVATI(4, "Chandovati (R2-)", 2, 10.0 / 9.0),
    DAYAVATI(5, "Dayavati (R2+ / G1)", 2, 9.0 / 8.0),
    RANJANI(6, "Ranjani (G2-)", 3, 32.0 / 27.0),
    RATIKA(7, "Ratika (G2+ / R3)", 3, 6.0 / 5.0),
    RAUDRI(8, "Raudri (G3-)", 4, 5.0 / 4.0),
    KRODHA(9, "Krodha (G3+)", 4, 81.0 / 64.0),
    VAJRIKA(10, "Vajrika (M1-)", 5, 4.0 / 3.0),
    PRASARINI(11, "Prasarini (M1+)", 5, 27.0 / 20.0),
    PRITI(12, "Priti (M2-)", 6, 45.0 / 32.0),
    MARJANI(13, "Marjani (M2+)", 6, 729.0 / 512.0),
    KSHITI(14, "Kshiti (Pa)", 7, 3.0 / 2.0),
    RAKTA(15, "Rakta (D1-)", 8, 128.0 / 81.0),
    SANDIPINI(16, "Sandipini (D1+)", 8, 8.0 / 5.0),
    ALAPINI(17, "Alapini (D2-)", 9, 5.0 / 3.0),
    MADANTI(18, "Madanti (D2+ / N1)", 9, 27.0 / 16.0),
    ROHINI(19, "Rohini (N2-)", 10, 16.0 / 9.0),
    RAMYA(20, "Ramya (N2+ / D3)", 10, 9.0 / 5.0),
    UGRA(21, "Ugra (N3-)", 11, 15.0 / 8.0),
    KSHOBHINI(22, "Kshobhini (N3+)", 11, 243.0 / 128.0);

    val cents: Double = 1200.0 * (kotlin.math.ln(ratio) / kotlin.math.ln(2.0))

    companion object {
        fun nearestForCentsInOctave(centsInOctave: Double): Shruti22 {
            val normalized = ((centsInOctave % 1200.0) + 1200.0) % 1200.0
            var best = TIVRA
            var minDiff = Double.MAX_VALUE
            for (s in entries) {
                val diff = kotlin.math.abs(normalized - s.cents)
                val wrapDiff = kotlin.math.abs(normalized - (s.cents + 1200.0))
                val actualDiff = minOf(diff, wrapDiff)
                if (actualDiff < minDiff) {
                    minDiff = actualDiff
                    best = s
                }
            }
            return best
        }
    }
}

