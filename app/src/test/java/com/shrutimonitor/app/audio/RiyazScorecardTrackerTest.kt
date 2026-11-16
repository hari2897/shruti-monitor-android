package com.shrutimonitor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RiyazScorecardTrackerTest {

    private lateinit var tracker: RiyazScorecardTracker

    @Before
    fun setUp() {
        tracker = RiyazScorecardTracker()
    }

    @Test
    fun testPerSwaraAccuracyCalculation() {
        val activeSwaras = setOf(0, 2, 4, 5, 7, 9, 11) // Bilawal scale

        // Record 10 frames of Sa (swara 0) with zero deviation
        for (i in 1..10) {
            tracker.recordFrame(
                swaraIndex = 0,
                centDeviation = 0f,
                inTuneTolerance = 10f,
                activeRagaSwaras = activeSwaras
            )
        }

        // Record 10 frames of Pa (swara 7) with constant +5.0 cents deviation
        // Formula: acc = (100 - avgAbs * 2.0) -> 100 - 5 * 2.0 = 90%
        for (i in 1..10) {
            tracker.recordFrame(
                swaraIndex = 7,
                centDeviation = 5.0f,
                inTuneTolerance = 10f,
                activeRagaSwaras = activeSwaras
            )
        }

        val summary = tracker.buildSummary()
        assertEquals(20, summary.totalVoicedFrames)
        assertEquals(0, summary.varjyaSlipCount)

        val saStat = summary.swaraStats.firstOrNull { it.swaraIndex == 0 }
        val paStat = summary.swaraStats.firstOrNull { it.swaraIndex == 7 }

        requireNotNull(saStat)
        requireNotNull(paStat)

        assertEquals(10, saStat.sampleCount)
        assertEquals(100f, saStat.accuracyPercent, 0.01f)
        assertEquals(0f, saStat.meanDeviationCents, 0.01f)

        assertEquals(10, paStat.sampleCount)
        assertEquals(90f, paStat.accuracyPercent, 0.01f)
        assertEquals(5.0f, paStat.meanDeviationCents, 0.01f)

        // Overall weighted accuracy: (100 * 10 + 90 * 10) / 20 = 95%
        assertEquals(95f, summary.overallAccuracyPercent, 0.01f)
    }

    @Test
    fun testVarjyaWarningTriggersAtExact12thFrame() {
        // Active raga with only Shuddh notes (Bilawal): Komal Re (1) is Varjya (forbidden)
        val activeSwaras = setOf(0, 2, 4, 5, 7, 9, 11)

        // Frames 1 to 11 should NOT trigger the warning
        for (frame in 1..11) {
            val isWarn = tracker.recordFrame(
                swaraIndex = 1, // Komal Re (Varjya)
                centDeviation = 0f,
                inTuneTolerance = 10f,
                activeRagaSwaras = activeSwaras
            )
            assertFalse("Frame $frame must not trigger Varjya warning yet", isWarn)
            assertFalse(tracker.isVarjyaWarningActive)
        }

        // Frame 12 must trigger the warning and increment slip count
        val isWarn12 = tracker.recordFrame(
            swaraIndex = 1,
            centDeviation = 0f,
            inTuneTolerance = 10f,
            activeRagaSwaras = activeSwaras
        )
        assertTrue("Frame 12 must trigger Varjya warning", isWarn12)
        assertTrue(tracker.isVarjyaWarningActive)
        assertEquals(1, tracker.buildSummary().varjyaSlipCount)

        // Frame 13 maintains the warning without double-counting slip
        val isWarn13 = tracker.recordFrame(
            swaraIndex = 1,
            centDeviation = 0f,
            inTuneTolerance = 10f,
            activeRagaSwaras = activeSwaras
        )
        assertTrue("Frame 13 should maintain Varjya warning", isWarn13)
        assertEquals(1, tracker.buildSummary().varjyaSlipCount)

        // Returning to an in-raga note (Sa = 0) immediately resets consecutive count and warning
        val inRagaWarn = tracker.recordFrame(
            swaraIndex = 0,
            centDeviation = 0f,
            inTuneTolerance = 10f,
            activeRagaSwaras = activeSwaras
        )
        assertFalse("In-raga note must deactivate Varjya warning", inRagaWarn)
        assertFalse(tracker.isVarjyaWarningActive)
    }

    @Test
    fun testClearVarjyaOnSilence() {
        val activeSwaras = setOf(0, 2, 4, 5, 7, 9, 11)

        for (frame in 1..12) {
            tracker.recordFrame(
                swaraIndex = 1,
                centDeviation = 0f,
                inTuneTolerance = 10f,
                activeRagaSwaras = activeSwaras
            )
        }
        assertTrue(tracker.isVarjyaWarningActive)

        tracker.clearVarjyaOnSilence()
        assertFalse("Silence must deactivate Varjya warning", tracker.isVarjyaWarningActive)
    }

    @Test
    fun testResetClearsEverything() {
        val activeSwaras = setOf(0, 7)

        for (i in 1..15) {
            tracker.recordFrame(3, 10f, 5f, activeSwaras)
        }

        var summary = tracker.buildSummary()
        assertTrue(summary.totalVoicedFrames > 0)
        assertTrue(summary.varjyaSlipCount > 0)

        tracker.reset()
        summary = tracker.buildSummary()

        assertEquals(0, summary.totalVoicedFrames)
        assertEquals(0, summary.varjyaSlipCount)
        assertEquals(0f, summary.overallAccuracyPercent, 0.001f)
        assertTrue(summary.swaraStats.isEmpty())
        assertFalse(tracker.isVarjyaWarningActive)
    }
}
