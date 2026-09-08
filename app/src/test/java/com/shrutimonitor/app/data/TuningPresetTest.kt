package com.shrutimonitor.app.data

import com.shrutimonitor.app.audio.SwaraMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

class TuningPresetTest {

    private val saFreq = 261.63f // C4
    private val mapper = SwaraMapper()

    @Test
    fun testSyntonicCommaMathematicalDifference() {
        // Mathematical proof of the Syntonic Comma (Pramana Shruti):
        // Ratio = 81 / 80 = 1.0125
        // Cents = 1200 * log2(81/80) ≈ 21.50629 cents
        val syntonicCommaRatio = 81.0 / 80.0
        val syntonicCommaCents = 1200.0 * log2(syntonicCommaRatio)
        assertEquals(21.506, syntonicCommaCents, 0.001)

        // 1. G2 (Komal Ga / Sadharana Gandharam):
        // Harmonic 5-limit = 6/5
        // Pythagorean 3-limit (cycle of 4ths & 5ths) = 32/27
        // (6/5) / (32/27) = 162/160 = 81/80
        val g2HarmonicRatio = TuningPreset.HARMONIC_5LIMIT.ratioForSwara(Swara.KOMAL_GA)
        val g2PythagoreanRatio = TuningPreset.PYTHAGOREAN_3LIMIT.ratioForSwara(Swara.KOMAL_GA)
        assertEquals(syntonicCommaRatio, g2HarmonicRatio / g2PythagoreanRatio, 1e-6)

        // 2. D1 (Komal Dha / Shuddha Dhaivatam):
        // Harmonic 5-limit = 8/5
        // Pythagorean 3-limit (cycle of 4ths & 5ths) = 128/81
        // (8/5) / (128/81) = 648/640 = 81/80
        val d1HarmonicRatio = TuningPreset.HARMONIC_5LIMIT.ratioForSwara(Swara.KOMAL_DHA)
        val d1PythagoreanRatio = TuningPreset.PYTHAGOREAN_3LIMIT.ratioForSwara(Swara.KOMAL_DHA)
        assertEquals(syntonicCommaRatio, d1HarmonicRatio / d1PythagoreanRatio, 1e-6)

        // 3. N2 (Komal Ni / Kaisiki Nishadam):
        // Harmonic 5-limit = 9/5
        // Pythagorean 3-limit (cycle of 4ths & 5ths) = 16/9
        // (9/5) / (16/9) = 81/80
        val n2HarmonicRatio = TuningPreset.HARMONIC_5LIMIT.ratioForSwara(Swara.KOMAL_NI)
        val n2PythagoreanRatio = TuningPreset.PYTHAGOREAN_3LIMIT.ratioForSwara(Swara.KOMAL_NI)
        assertEquals(syntonicCommaRatio, n2HarmonicRatio / n2PythagoreanRatio, 1e-6)
    }

    @Test
    fun testSharedSwarasHaveIdenticalRatiosInHarmonicAndPythagorean() {
        // The remaining 9 swaras (S, R1, R2, G3, M1, M2, P, D2, N3) must have identical cents & ratios
        val identicalSwaras = listOf(
            Swara.SA,
            Swara.KOMAL_RE,
            Swara.SHUDDH_RE,
            Swara.SHUDDH_GA,
            Swara.SHUDDH_MA,
            Swara.TIVRA_MA,
            Swara.PA,
            Swara.SHUDDH_DHA,
            Swara.SHUDDH_NI
        )

        for (swara in identicalSwaras) {
            val harmonicCents = TuningPreset.HARMONIC_5LIMIT.centsForSwara(swara)
            val pythagoreanCents = TuningPreset.PYTHAGOREAN_3LIMIT.centsForSwara(swara)
            val harmonicRatio = TuningPreset.HARMONIC_5LIMIT.ratioForSwara(swara)
            val pythagoreanRatio = TuningPreset.PYTHAGOREAN_3LIMIT.ratioForSwara(swara)

            assertEquals("Cents should match for ${swara.name}", harmonicCents, pythagoreanCents, 0.001)
            assertEquals("Ratios should match for ${swara.name}", harmonicRatio, pythagoreanRatio, 1e-6)
        }
    }

    @Test
    fun testPythagoreanRatiosProduceZeroCentDeviationInPythagoreanPreset() {
        // 3-Limit swarasthanas: G2 (32/27), D1 (128/81), N2 (16/9)
        // must register exactly 0.0 cents deviation in PYTHAGOREAN_3LIMIT preset

        // G2 at Pythagorean ratio (32/27)
        val pythagoreanG2Freq = (saFreq * (32.0 / 27.0)).toFloat()
        val resultPythagoreanG2 = mapper.mapFrequency(saFreq, pythagoreanG2Freq, tuningPreset = TuningPreset.PYTHAGOREAN_3LIMIT)
        assertEquals(Swara.KOMAL_GA, resultPythagoreanG2.swara)
        assertEquals(0.0f, resultPythagoreanG2.centDeviation, 0.05f)

        // D1 at Pythagorean ratio (128/81)
        val pythagoreanD1Freq = (saFreq * (128.0 / 81.0)).toFloat()
        val resultPythagoreanD1 = mapper.mapFrequency(saFreq, pythagoreanD1Freq, tuningPreset = TuningPreset.PYTHAGOREAN_3LIMIT)
        assertEquals(Swara.KOMAL_DHA, resultPythagoreanD1.swara)
        assertEquals(0.0f, resultPythagoreanD1.centDeviation, 0.05f)

        // N2 at Pythagorean ratio (16/9)
        val pythagoreanN2Freq = (saFreq * (16.0 / 9.0)).toFloat()
        val resultPythagoreanN2 = mapper.mapFrequency(saFreq, pythagoreanN2Freq, tuningPreset = TuningPreset.PYTHAGOREAN_3LIMIT)
        assertEquals(Swara.KOMAL_NI, resultPythagoreanN2.swara)
        assertEquals(0.0f, resultPythagoreanN2.centDeviation, 0.05f)
    }

    @Test
    fun testPythagoreanFrequenciesInHarmonicPresetShowExpected21CentDifference() {
        // When analyzed with HARMONIC_5LIMIT preset (5-limit JI), 3-limit Pythagorean frequencies should show -21.51 cents deviation
        // because 3-limit ratios are lower by one Syntonic Comma (81/80)
        val pythagoreanG2Freq = (saFreq * (32.0 / 27.0)).toFloat()
        val resultStandardG2 = mapper.mapFrequency(saFreq, pythagoreanG2Freq, tuningPreset = TuningPreset.HARMONIC_5LIMIT)
        assertEquals(Swara.KOMAL_GA, resultStandardG2.swara)
        assertEquals(-21.51f, resultStandardG2.centDeviation, 0.05f)

        val pythagoreanD1Freq = (saFreq * (128.0 / 81.0)).toFloat()
        val resultStandardD1 = mapper.mapFrequency(saFreq, pythagoreanD1Freq, tuningPreset = TuningPreset.HARMONIC_5LIMIT)
        assertEquals(Swara.KOMAL_DHA, resultStandardD1.swara)
        assertEquals(-21.51f, resultStandardD1.centDeviation, 0.05f)

        val pythagoreanN2Freq = (saFreq * (16.0 / 9.0)).toFloat()
        val resultStandardN2 = mapper.mapFrequency(saFreq, pythagoreanN2Freq, tuningPreset = TuningPreset.HARMONIC_5LIMIT)
        assertEquals(Swara.KOMAL_NI, resultStandardN2.swara)
        assertEquals(-21.51f, resultStandardN2.centDeviation, 0.05f)
    }

    @Test
    fun testHarmonicFrequenciesInPythagoreanPresetShowExpectedPlus21CentDifference() {
        // When analyzed with PYTHAGOREAN_3LIMIT preset, 5-limit harmonic frequencies show +21.51 cents deviation
        val harmonicG2Freq = (saFreq * (6.0 / 5.0)).toFloat()
        val result = mapper.mapFrequency(saFreq, harmonicG2Freq, tuningPreset = TuningPreset.PYTHAGOREAN_3LIMIT)
        assertEquals(Swara.KOMAL_GA, result.swara)
        assertEquals(+21.51f, result.centDeviation, 0.05f)
    }

    @Test
    fun testEqualTemperamentSteps() {
        for (index in 0..11) {
            val swara = Swara.fromIndex(index)
            val expectedCents = (index * 100.0).toFloat()
            val actualCents = TuningPreset.EQUAL_TEMPERAMENT.centsForSwara(swara).toFloat()
            assertEquals("ET cents for swara $index should be ${index * 100}", expectedCents, actualCents, 0.01f)

            val expectedRatio = 2.0.pow(index / 12.0)
            val actualRatio = TuningPreset.EQUAL_TEMPERAMENT.ratioForSwara(swara)
            assertEquals("ET ratio for swara $index", expectedRatio, actualRatio, 1e-4)
        }
    }

    @Test
    fun testActualToVisualCentsGridMapping() {
        // For each preset, the ideal pitch of a swara must align with its visual grid line (swaraIndex * 100)
        for (preset in TuningPreset.entries) {
            for (index in 0..11) {
                val swara = Swara.fromIndex(index)
                val idealActualCents = preset.centsForSwara(swara).toFloat()
                val visualCents = TuningPreset.actualToVisualCents(idealActualCents, preset)
                val expectedVisual = (index * 100.0).toFloat()
                assertEquals(
                    "Ideal cents for ${swara.name} in preset ${preset.id} must map to grid line $expectedVisual",
                    expectedVisual,
                    visualCents,
                    0.1f
                )
            }
        }
    }

    @Test
    fun testFromIdAndBackwardsCompatibility() {
        // New canonical IDs
        assertEquals(TuningPreset.HARMONIC_5LIMIT, TuningPreset.fromId("harmonic_5limit"))
        assertEquals(TuningPreset.PYTHAGOREAN_3LIMIT, TuningPreset.fromId("pythagorean_3limit"))
        assertEquals(TuningPreset.EQUAL_TEMPERAMENT, TuningPreset.fromId("equal_temperament"))

        // Legacy backwards compatibility IDs
        assertEquals(TuningPreset.HARMONIC_5LIMIT, TuningPreset.fromId("standard"))
        assertEquals(TuningPreset.PYTHAGOREAN_3LIMIT, TuningPreset.fromId("carnatic_legacy"))

        // Legacy property aliases
        assertEquals(TuningPreset.HARMONIC_5LIMIT, TuningPreset.STANDARD)

        // Fallback
        assertEquals(TuningPreset.HARMONIC_5LIMIT, TuningPreset.fromId("unknown_fallback"))
    }
}
