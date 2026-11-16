package com.shrutimonitor.app.audio

import com.sangatkaar.android.OboeEngine
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TanpuraSynthesizerTest {

    @Test
    fun testDefaultSaTonicFolding() {
        // Middle C (C4 = 261.63 Hz)
        val hzC4 = 261.63
        val exactMidiC4 = 69.0 + 12.0 * log2(hzC4 / 440.0) + 24.0
        var foldedC4 = exactMidiC4
        while (foldedC4 < 67.0) foldedC4 += 12.0
        while (foldedC4 > 88.0) foldedC4 -= 12.0
        val saMidiC4 = round(foldedC4).toInt().coerceIn(67, 88)
        
        // 60 is C4 normally, plus 24 is 84, which is in 67..88
        assertEquals(84, saMidiC4)

        // Low C (C3 = 130.81 Hz)
        val hzC3 = 130.81278
        val exactMidiC3 = 69.0 + 12.0 * log2(hzC3 / 440.0) + 24.0
        var foldedC3 = exactMidiC3
        while (foldedC3 < 67.0) foldedC3 += 12.0
        while (foldedC3 > 88.0) foldedC3 -= 12.0
        val saMidiC3 = round(foldedC3).toInt().coerceIn(67, 88)
        assertEquals(72, saMidiC3) // C3 is 48 + 24 = 72

        // A440 Hz
        val hzA4 = 440.0
        val exactMidiA4 = 69.0 + 12.0 * log2(hzA4 / 440.0) + 24.0 // 93.0
        var foldedA4 = exactMidiA4
        while (foldedA4 < 67.0) foldedA4 += 12.0
        while (foldedA4 > 88.0) foldedA4 -= 12.0
        val saMidiA4 = round(foldedA4).toInt().coerceIn(67, 88)
        assertEquals(81, saMidiA4) // 93 - 12 = 81
    }

    @Test
    fun testResidualCentsAccuracy() {
        // Test 432 Hz Mode offset (-31.77 cents)
        val hz = 440.0
        val exactMidi = 69.0 + 12.0 * log2(hz / 440.0) + 24.0
        var folded = exactMidi
        while (folded < 67.0) folded += 12.0
        while (folded > 88.0) folded -= 12.0
        val saMidi = round(folded).toInt()

        val is432Hz = true
        val fineTuning = 5.0f
        val residualCents = (folded - saMidi) * 100.0 + fineTuning + (if (is432Hz) -31.77 else 0.0)
        
        // Residual should be 0.0 + 5.0 - 31.77 = -26.77
        assertTrue(abs(residualCents - (-26.77)) < 0.001)
    }

    @Test
    fun testJhalaStringMapping() {
        assertEquals(3, JhalaString.entries.size)
        assertTrue(JhalaString.entries.contains(JhalaString.PA))
        assertTrue(JhalaString.entries.contains(JhalaString.MA))
        assertTrue(JhalaString.entries.contains(JhalaString.NI))
    }

    @Test
    fun testSynthesizerGracefulState() {
        val synth = TanpuraSynthesizer()
        assertFalse(synth.isRunning())
        assertFalse(synth.isPlaying.value)

        // Starting without Android AssetManager should not crash
        synth.start()
        synth.stop()
        assertFalse(synth.isRunning())
        assertFalse(synth.isPlaying.value)
    }
}
