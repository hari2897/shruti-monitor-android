package com.shrutimonitor.app.audio

import com.shrutimonitor.app.data.Swara
import org.junit.Assert.assertEquals
import org.junit.Test

class SwaraMapperTest {

    private val mapper = SwaraMapper()
    private val saFreq = 261.63f // C4

    @Test
    fun testExactTonicSa() {
        val result = mapper.mapFrequency(saFreq, saFreq)
        assertEquals("Should map to SA", Swara.SA, result.swara)
        assertEquals("Deviation should be close to 0", 0f, result.centDeviation, 0.1f)
        assertEquals("Should be Madhya saptak", Saptak.MADHYA, result.saptak)
    }

    @Test
    fun testExactPanchamPa() {
        val paFreq = saFreq * 1.5f // 3/2 ratio
        val result = mapper.mapFrequency(saFreq, paFreq)
        assertEquals("Should map to PA", Swara.PA, result.swara)
        assertEquals("Deviation should be close to 0", 0f, result.centDeviation, 0.1f)
        assertEquals("Should be Madhya saptak", Saptak.MADHYA, result.saptak)
    }

    @Test
    fun testDetunedSa() {
        // C4 + 10 cents = 261.63 * 2^(10/1200) = 263.14 Hz
        val detunedFreq = 263.14f
        val result = mapper.mapFrequency(saFreq, detunedFreq)
        assertEquals("Should map to SA", Swara.SA, result.swara)
        assertEquals("Deviation should be close to +10", 10.0f, result.centDeviation, 0.5f)
    }

    @Test
    fun testTaraSaptakSa() {
        val taraSaFreq = saFreq * 2.0f
        val result = mapper.mapFrequency(saFreq, taraSaFreq)
        assertEquals("Should map to SA", Swara.SA, result.swara)
        assertEquals("Deviation should be close to 0", 0f, result.centDeviation, 0.1f)
        assertEquals("Should be Tara saptak", Saptak.TARA, result.saptak)
    }

    @Test
    fun testTaraSaptakGandharamCentDeviationBounded() {
        // Antara Gandharam (G2 / Shuddh Ga) in Tara Saptak (1 octave above Madhya Sa)
        // Ideal cents from Base Sa: 1200 + 386.31 = 1586.31 cents
        // With +15 cents deviation: 1601.31 cents
        val taraG2WithDeviationFreq = (saFreq * 2.0 * (5.0 / 4.0) * Math.pow(2.0, 15.0 / 1200.0)).toFloat()
        val result = mapper.mapFrequency(saFreq, taraG2WithDeviationFreq)
        assertEquals("Should map to SHUDDH_GA", Swara.SHUDDH_GA, result.swara)
        assertEquals("Deviation should be +15 cents, NOT cumulative +1600 cents", 15.0f, result.centDeviation, 0.5f)
        assertEquals("Should be Tara saptak", Saptak.TARA, result.saptak)
    }

    @Test
    fun testMultiOctaveHighPitchesDoNotAccumulateCents() {
        // Pitch 3 octaves above Base Sa (~ +3600 cents) + Pancham (702 cents) -> ~ +4302 cents from Sa
        // e.g. Base Sa = 123.5 Hz (B2), 3 octaves up Pa = 123.5 * 8 * 1.5 = 1482 Hz
        val highPaFreq = (saFreq * 8.0 * 1.5).toFloat()
        val result = mapper.mapFrequency(saFreq, highPaFreq)
        assertEquals("Should map to PA", Swara.PA, result.swara)
        assertEquals("Deviation should be 0, NOT +4300 cents", 0.0f, result.centDeviation, 0.5f)
    }
}
