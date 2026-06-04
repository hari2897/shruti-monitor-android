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
}
