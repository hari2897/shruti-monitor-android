package com.shrutimonitor.app.audio

import android.util.Log
import com.shrutimonitor.app.data.Swara
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * High-performance, zero-allocation telemetry recorder for the pitch pipeline.
 *
 * Captures per-callback frame diagnostics:
 * 1. sample timestamp (ms)
 * 2. raw frequency (Hz)
 * 3. raw cents (relative to tonic Sa)
 * 4. confidence / clarity value
 * 5. RMS amplitude
 * 6. accepted or rejected + rejection reason
 * 7. mapped visual cents (output of Swara.actualToVisualCents)
 *
 * And aggregates per-second pipeline stats:
 * - detector callbacks per second
 * - accepted pitch points per second
 * - render FPS
 * - median and maximum callback interval (ms)
 */
object PitchPipelineTelemetry {

    private const val TAG = "PitchTelemetry"
    private const val STATS_TAG = "PitchPipelineStats"
    private const val BUFFER_CAPACITY = 600 // Retains ~14 seconds of frames at 43Hz

    data class FrameRecord(
        var timestampMs: Long = 0L,
        var rawFreqHz: Float = 0f,
        var rawCents: Double = 0.0,
        var confidence: Float = 0f,
        var rmsAmplitude: Float = 0f,
        var decision: PitchFilterDecision = PitchFilterDecision.REJECTED_UNVOICED,
        var filteredFreqHz: Float = 0f,
        var mappedVisualCents: Double = 0.0
    )

    // Pre-allocated ring buffer to avoid GC allocations during telemetry capture
    private val frameBuffer = Array(BUFFER_CAPACITY) { FrameRecord() }
    private var bufferHead = 0
    private var totalFramesRecorded = 0L

    // Interval and per-second statistics tracking
    private var lastCallbackTimeMs = 0L
    private val intervalsMs = IntArray(120) // Holds up to 120 callbacks per second
    private var intervalCount = 0

    private var callbacksThisSecond = 0
    private var acceptedThisSecond = 0
    private val renderFramesThisSecond = AtomicInteger(0)
    private var lastStatsLogTimeMs = 0L

    // Detailed per-frame log throttler (logs 1 out of every N frames in Logcat, but keeps 100% in buffer)
    private var logSampleCounter = 0
    var isVerboseLogcatEnabled = false

    /**
     * Called by PitchGraph on every Canvas draw pass to track display render FPS.
     */
    fun recordRenderFrame() {
        renderFramesThisSecond.incrementAndGet()
    }

    /**
     * Calculates the Root Mean Square (RMS) amplitude of an audio frame.
     */
    fun computeRms(buffer: FloatArray): Float {
        if (buffer.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in buffer) {
            sumSquares += (sample * sample).toDouble()
        }
        return sqrt(sumSquares / buffer.size).toFloat()
    }

    /**
     * Records a single detector callback through the pipeline.
     */
    @Synchronized
    fun recordCallback(
        timestampMs: Long,
        rawFreqHz: Float,
        confidence: Float,
        audioFrame: FloatArray,
        filterResult: PitchFilterResult,
        saFrequency: Float
    ) {
        val rms = computeRms(audioFrame)

        val rawCents = if (rawFreqHz > 0f && saFrequency > 0f) {
            1200.0 * log2((rawFreqHz / saFrequency).toDouble())
        } else {
            0.0
        }

        val visualCents = if (rawFreqHz > 0f) {
            Swara.actualToVisualCents(rawCents)
        } else {
            0.0
        }

        // Store into ring buffer
        val record = frameBuffer[bufferHead]
        record.timestampMs = timestampMs
        record.rawFreqHz = rawFreqHz
        record.rawCents = rawCents
        record.confidence = confidence
        record.rmsAmplitude = rms
        record.decision = filterResult.decision
        record.filteredFreqHz = filterResult.frequency
        record.mappedVisualCents = visualCents

        bufferHead = (bufferHead + 1) % BUFFER_CAPACITY
        totalFramesRecorded++

        // Callback interval tracking
        if (lastCallbackTimeMs > 0L) {
            val interval = (timestampMs - lastCallbackTimeMs).toInt().coerceAtLeast(0)
            if (intervalCount < intervalsMs.size) {
                intervalsMs[intervalCount++] = interval
            }
        }
        lastCallbackTimeMs = timestampMs

        callbacksThisSecond++
        if (filterResult.decision == PitchFilterDecision.ACCEPTED || filterResult.decision == PitchFilterDecision.OCTAVE_SNAPPED) {
            acceptedThisSecond++
        }

        // Log sample to Logcat (or all if verbose)
        if (isVerboseLogcatEnabled || (++logSampleCounter % 10 == 0)) {
            Log.v(
                TAG,
                String.format(
                    "t=%d | raw=%.1fHz (%.1fc) | conf=%.2f | rms=%.4f | dec=%s -> out=%.1fHz | vis=%.1fc",
                    timestampMs,
                    rawFreqHz,
                    rawCents,
                    confidence,
                    rms,
                    filterResult.decision.name,
                    filterResult.frequency,
                    visualCents
                )
            )
        }

        // Per-second telemetry aggregation and logging
        if (lastStatsLogTimeMs == 0L) {
            lastStatsLogTimeMs = timestampMs
        } else if (timestampMs - lastStatsLogTimeMs >= 1000L) {
            val elapsedSec = (timestampMs - lastStatsLogTimeMs) / 1000.0
            val callbacksPerSec = (callbacksThisSecond / elapsedSec).toFloat()
            val acceptedPerSec = (acceptedThisSecond / elapsedSec).toFloat()
            val renderFps = (renderFramesThisSecond.getAndSet(0) / elapsedSec).toInt()

            val medianInterval = computeMedianInterval()
            val maxInterval = computeMaxInterval()

            Log.i(
                STATS_TAG,
                String.format(
                    "STATS: Callbacks/sec: %.1f | Accepted/sec: %.1f | Render FPS: %d | Interval median: %d ms, max: %d ms",
                    callbacksPerSec,
                    acceptedPerSec,
                    renderFps,
                    medianInterval,
                    maxInterval
                )
            )

            // Reset per-second counters
            callbacksThisSecond = 0
            acceptedThisSecond = 0
            intervalCount = 0
            lastStatsLogTimeMs = timestampMs
        }
    }

    private fun computeMedianInterval(): Int {
        if (intervalCount == 0) return 0
        val sorted = intervalsMs.copyOf(intervalCount)
        sorted.sort()
        return sorted[intervalCount / 2]
    }

    private fun computeMaxInterval(): Int {
        if (intervalCount == 0) return 0
        var max = 0
        for (i in 0 until intervalCount) {
            if (intervalsMs[i] > max) max = intervalsMs[i]
        }
        return max
    }

    /**
     * Dumps the recent in-memory frame buffer to logcat for analysis.
     */
    @Synchronized
    fun dumpRecentFrames(limit: Int = 100) {
        val count = minOf(limit, minOf(totalFramesRecorded.toInt(), BUFFER_CAPACITY))
        Log.i(TAG, "=== DUMPING RECENT $count PITCH PIPELINE FRAMES ===")
        val start = (bufferHead - count + BUFFER_CAPACITY) % BUFFER_CAPACITY
        for (i in 0 until count) {
            val idx = (start + i) % BUFFER_CAPACITY
            val r = frameBuffer[idx]
            Log.d(
                TAG,
                "Frame[$i]: t=${r.timestampMs} ms, raw=${r.rawFreqHz} Hz, cents=${String.format("%.1f", r.rawCents)}, conf=${String.format("%.2f", r.confidence)}, rms=${String.format("%.4f", r.rmsAmplitude)}, dec=${r.decision}, out=${r.filteredFreqHz} Hz, vis=${String.format("%.1f", r.mappedVisualCents)}"
            )
        }
    }
}
