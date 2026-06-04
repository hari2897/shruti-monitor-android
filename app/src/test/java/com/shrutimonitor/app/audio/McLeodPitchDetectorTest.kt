package com.shrutimonitor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class McLeodPitchDetectorTest {

    private val detector = McLeodPitchDetector()

    @Test
    fun testSineWave440Hz() {
        val sampleRate = 44100
        val freq = 440.0f
        val buffer = FloatArray(2048)

        // Generate 440Hz pure sine wave
        for (i in buffer.indices) {
            buffer[i] = sin(2.0 * PI * freq.toDouble() * i / sampleRate).toFloat()
        }

        val result = detector.detectPitch(buffer)

        assertTrue("Should be detected as voiced", result.isVoiced)
        assertTrue("Confidence should be very high", result.confidence > 0.85f)
        assertEquals("Detected frequency should be ~440Hz", freq, result.frequency, 1.0f)
    }

    @Test
    fun testSilence() {
        val buffer = FloatArray(2048) // All zeros
        val result = detector.detectPitch(buffer)
        assertFalse("Silence should be unvoiced", result.isVoiced)
    }
}
