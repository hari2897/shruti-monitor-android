package com.shrutimonitor.app.audio

import kotlin.math.abs

data class SwaraAccuracyStat(
    val swaraIndex: Int,
    val sampleCount: Int,
    val accuracyPercent: Float,
    val meanDeviationCents: Float
)

data class RiyazSessionSummary(
    val overallAccuracyPercent: Float = 0f,
    val totalVoicedFrames: Int = 0,
    val varjyaSlipCount: Int = 0,
    val swaraStats: List<SwaraAccuracyStat> = emptyList()
)

class RiyazScorecardTracker {
    private val frameCounts = IntArray(12)
    private val inTuneCounts = IntArray(12)
    private val sumAbsCents = FloatArray(12)
    private val sumSignedCents = FloatArray(12)

    private var consecutiveVarjyaFrames = 0
    private var totalVarjyaSlips = 0
    @Volatile var isVarjyaWarningActive: Boolean = false
        private set

    @Synchronized
    fun reset() {
        frameCounts.fill(0)
        inTuneCounts.fill(0)
        sumAbsCents.fill(0f)
        sumSignedCents.fill(0f)
        consecutiveVarjyaFrames = 0
        totalVarjyaSlips = 0
        isVarjyaWarningActive = false
    }

    /**
     * Records a voiced frame and returns true if the user is currently sustaining
     * a forbidden (Varjya) swara outside [activeRagaSwaras] for >= 12 frames (~275ms).
     */
    @Synchronized
    fun recordFrame(
        swaraIndex: Int,
        centDeviation: Float,
        inTuneTolerance: Float,
        activeRagaSwaras: Set<Int>?
    ): Boolean {
        if (swaraIndex !in 0..11) return false

        frameCounts[swaraIndex]++
        sumAbsCents[swaraIndex] += abs(centDeviation)
        sumSignedCents[swaraIndex] += centDeviation
        if (abs(centDeviation) <= inTuneTolerance) {
            inTuneCounts[swaraIndex]++
        }

        if (activeRagaSwaras != null && !activeRagaSwaras.contains(swaraIndex)) {
            consecutiveVarjyaFrames++
            if (consecutiveVarjyaFrames == 12) {
                totalVarjyaSlips++
            }
            isVarjyaWarningActive = consecutiveVarjyaFrames >= 12
        } else {
            consecutiveVarjyaFrames = 0
            isVarjyaWarningActive = false
        }

        return isVarjyaWarningActive
    }

    @Synchronized
    fun clearVarjyaOnSilence() {
        consecutiveVarjyaFrames = 0
        isVarjyaWarningActive = false
    }

    @Synchronized
    fun buildSummary(): RiyazSessionSummary {
        var totalFrames = 0
        var weightedAccuracySum = 0f
        val stats = mutableListOf<SwaraAccuracyStat>()

        for (i in 0..11) {
            val count = frameCounts[i]
            if (count > 0) {
                val avgAbs = sumAbsCents[i] / count
                val acc = (100f - avgAbs * 2.0f).coerceIn(0f, 100f)
                val meanSigned = sumSignedCents[i] / count
                stats.add(
                    SwaraAccuracyStat(
                        swaraIndex = i,
                        sampleCount = count,
                        accuracyPercent = acc,
                        meanDeviationCents = meanSigned
                    )
                )
                totalFrames += count
                weightedAccuracySum += acc * count
            }
        }

        val overall = if (totalFrames > 0) weightedAccuracySum / totalFrames else 0f
        return RiyazSessionSummary(
            overallAccuracyPercent = overall,
            totalVoicedFrames = totalFrames,
            varjyaSlipCount = totalVarjyaSlips,
            swaraStats = stats
        )
    }
}
