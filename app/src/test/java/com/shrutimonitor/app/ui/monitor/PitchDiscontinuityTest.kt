package com.shrutimonitor.app.ui.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchDiscontinuityTest {

    @Test
    fun testScaleStepsStayConnected() {
        // Median callback interval on device (~19-20ms)
        val maxDelta19 = maxPlausibleDeltaCents(19L)
        // 100-cent semitone step (e.g. Sa to Komal Re, Ga to Ma) must be connected
        assertTrue("Semitone step must stay connected at 19ms", 100f <= maxDelta19)

        // 204-cent whole tone step (e.g. Sa to Shuddha Re, Ma to Pa) must be connected
        assertTrue("Whole tone step (204c) must stay connected at 19ms", 204f <= maxDelta19)

        // Nominal 23ms interval
        val maxDelta23 = maxPlausibleDeltaCents(23L)
        assertTrue("Whole tone step (204c) must stay connected at 23ms", 204f <= maxDelta23)

        // Minor third glide (300c) across 55ms interval
        val maxDelta55 = maxPlausibleDeltaCents(55L)
        assertTrue("Minor third glide (300c) must stay connected at 55ms", 300f <= maxDelta55)
    }

    @Test
    fun testGamakaStayConnected() {
        // Fast gamaka of 180 cents in 20ms
        val maxDelta20 = maxPlausibleDeltaCents(20L)
        assertTrue("Fast gamaka (180c in 20ms) must stay connected", 180f <= maxDelta20)

        // Wide gamaka of 240 cents in 20ms
        assertTrue("Wide gamaka (240c in 20ms) must stay connected", 240f <= maxDelta20)

        // Rapid portamento of 350 cents across 60ms
        val maxDelta60 = maxPlausibleDeltaCents(60L)
        assertTrue("Portamento (350c in 60ms) must stay connected", 350f <= maxDelta60)
    }

    @Test
    fun testOctaveErrorsAndGlitchLeapsBreak() {
        val maxDelta20 = maxPlausibleDeltaCents(20L)

        // Octave glitch (1200 cents in 20ms) must break
        assertFalse("Octave error (1200c in 20ms) must break", 1200f <= maxDelta20)

        // Fifth false harmonic (700 cents in 20ms) must break
        assertFalse("Fifth harmonic (700c in 20ms) must break", 700f <= maxDelta20)

        // Fourth jump (500 cents in 20ms) must break
        assertFalse("Fourth jump (500c in 20ms) must break", 500f <= maxDelta20)

        // Major third abrupt jump (400 cents in 20ms) must break
        assertFalse("Major third jump (400c in 20ms) must break", 400f <= maxDelta20)
    }

    @Test
    fun testFrameSpikesScaleThreshold() {
        // Actual telemetry shows interval spikes up to 44-58ms
        val maxDelta45 = maxPlausibleDeltaCents(45L)
        assertEquals(270f, maxDelta45, 1e-3f)

        val maxDelta58 = maxPlausibleDeltaCents(58L)
        assertEquals(348f, maxDelta58, 1e-3f)
    }

    @Test
    fun testBridgeGapThreshold() {
        // Bridged unvoiced consonant gaps (up to 80ms)
        val maxDelta60 = maxPlausibleDeltaCents(60L)
        assertEquals(360f, maxDelta60, 1e-3f)

        val maxDelta80 = maxPlausibleDeltaCents(80L)
        assertEquals(480f, maxDelta80, 1e-3f)

        // Capped at MAX_DISCONTINUITY_CENTS (500 cents)
        val maxDelta120 = maxPlausibleDeltaCents(120L)
        assertEquals(MAX_DISCONTINUITY_CENTS, maxDelta120, 1e-3f)
    }
}
