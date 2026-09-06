package com.shrutimonitor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DisplayPitchFilterTest {

    @Test
    fun testConfidenceGating() {
        val filter = DisplayPitchFilter(confidenceThreshold = 0.85f)

        // Voiced with high confidence -> accepted
        val f1 = filter.filter(220f, 0.90f)
        assertEquals(220f, f1, 1.0f)

        // Low confidence -> rejected (0f gap)
        val f2 = filter.filter(220f, 0.70f)
        assertEquals(0f, f2, 1e-4f)

        // Zero or negative frequency -> rejected
        val f3 = filter.filter(0f, 0.95f)
        assertEquals(0f, f3, 1e-4f)
    }

    @Test
    fun testTransientOctaveErrorSnappedVsSustainedOctaveLeapAccepted() {
        val filter = DisplayPitchFilter(confidenceThreshold = 0.80f)

        // Establish stable pitch at 220 Hz (A3)
        for (i in 1..3) {
            filter.filter(220f, 0.95f)
        }

        // 1. Transient single-frame 2x octave glitch (440 Hz)
        val snappedGlitch = filter.filter(440f, 0.95f)
        // Must snap back to ~220 Hz!
        assertTrue("Single-frame octave glitch must snap to ~220Hz, was $snappedGlitch", abs(snappedGlitch - 220f) < 5f)

        // Next frame returns to 220 Hz -> stays rock-steady at 220 Hz
        val resumed = filter.filter(220f, 0.95f)
        assertTrue("Expected 220Hz, was $resumed", abs(resumed - 220f) < 5f)

        // 2. Real sustained octave leap: singer intentionally jumps to 440 Hz (Tara Sa / A4)
        // Frame 1 of leap: snaps as transient
        val leapFrame1 = filter.filter(440f, 0.95f)
        assertTrue("Frame 1 of leap snaps as transient, was $leapFrame1", abs(leapFrame1 - 220f) < 5f)

        // Frame 2 of leap: sustained! Must be accepted at the new octave (~440 Hz)
        val leapFrame2 = filter.filter(440f, 0.95f)
        assertTrue("Frame 2 of sustained leap must accept new octave near 440Hz, was $leapFrame2", abs(leapFrame2 - 440f) < 10f)

        // Frame 3 of leap: continues smoothly at 440 Hz
        val leapFrame3 = filter.filter(440f, 0.95f)
        assertTrue("Frame 3 continues at 440Hz, was $leapFrame3", abs(leapFrame3 - 440f) < 5f)
    }

    @Test
    fun testSingleFrameWildSpikeRejected() {
        val filter = DisplayPitchFilter(confidenceThreshold = 0.80f)

        // Establish stable pitch at 150 Hz
        for (i in 1..3) {
            filter.filter(150f, 0.95f)
        }

        // Detector momentarily produces a 750 Hz consonant/breath spike (> 600 cents) for 1 frame
        val spikeResult = filter.filter(750f, 0.95f)
        // Must be rejected as 0f gap!
        assertEquals(0f, spikeResult, 1e-4f)

        // Next frame returns to normal singing at 150 Hz
        val returnResult = filter.filter(150f, 0.95f)
        assertTrue("Expected pitch to resume at ~150Hz, was $returnResult", abs(returnResult - 150f) < 5f)
    }

    @Test
    fun testIntentionalLeapAcceptedOnSecondFrame() {
        val filter = DisplayPitchFilter(confidenceThreshold = 0.80f)

        // Establish pitch at 130 Hz
        for (i in 1..3) {
            filter.filter(130f, 0.95f)
        }

        // Singer intentionally leaps to 230 Hz (~980 cents jump)
        // Frame 1: treated as tentative spike
        val f1 = filter.filter(230f, 0.95f)
        assertEquals(0f, f1, 1e-4f)

        // Frame 2: note is sustained at 230 Hz -> accepted!
        val f2 = filter.filter(230f, 0.95f)
        assertTrue("Expected second frame of leap to be accepted near 230Hz, was $f2", abs(f2 - 230f) < 10f)
    }

    @Test
    fun testDiscontinuityResetNoSmoothingAcrossGap() {
        val filter = DisplayPitchFilter(confidenceThreshold = 0.80f)

        // Sing at 200 Hz
        filter.filter(200f, 0.95f)
        filter.filter(200f, 0.95f)

        // Gap (breath pause)
        filter.filter(0f, 0.1f)

        // Resume at 300 Hz
        val fResume = filter.filter(300f, 0.95f)
        // Should immediately be 300f, NOT an EMA blend with 200f!
        assertEquals(300f, fResume, 0.01f)
    }

    @Test
    fun testVoicingHysteresisDualThresholds() {
        // Default start=0.82, continue=0.70
        val filter = DisplayPitchFilter(startConfidenceThreshold = 0.82f, continueConfidenceThreshold = 0.70f)

        // 1. Below start threshold (0.80 < 0.82) -> rejected
        val f1 = filter.filter(200f, 0.80f)
        assertEquals(0f, f1, 1e-4f)

        // 2. Crosses start threshold (0.83 >= 0.82) -> enters voiced segment!
        val f2 = filter.filter(200f, 0.83f)
        assertEquals(200f, f2, 1e-4f)

        // 3. Drops below start threshold but stays above continue threshold (0.75 >= 0.70) -> stays voiced!
        val f3 = filter.filter(200f, 0.75f)
        assertEquals(200f, f3, 1e-4f)

        // 4. Drops below continue threshold (0.68 < 0.70) -> exits voiced segment
        val f4 = filter.filter(200f, 0.68f)
        assertEquals(0f, f4, 1e-4f)

        // 5. Subsequent frame at 0.78 is below start threshold (0.78 < 0.82) -> stays rejected until 0.82 reached!
        val f5 = filter.filter(200f, 0.78f)
        assertEquals(0f, f5, 1e-4f)

        // 6. Strong attack at 0.85 -> voiced again
        val f6 = filter.filter(200f, 0.85f)
        assertEquals(200f, f6, 1e-4f)
    }
}

