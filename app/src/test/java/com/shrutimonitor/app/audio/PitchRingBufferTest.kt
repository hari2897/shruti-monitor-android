package com.shrutimonitor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PitchRingBufferTest {

    @Test
    fun testInitialState() {
        val buffer = PitchRingBuffer(10)
        assertEquals(0, buffer.size)
        assertTrue(buffer.isEmpty)
        assertFalse(buffer.isNotEmpty)
        assertEquals(10, buffer.capacity)
    }

    @Test
    fun testPushUnderCapacity() {
        val buffer = PitchRingBuffer(5)
        buffer.push(100L, 220.0f, 0.9f)
        buffer.push(200L, 221.0f, 0.92f)
        buffer.push(300L, 222.0f, 0.95f)

        assertEquals(3, buffer.size)
        assertFalse(buffer.isEmpty)
        assertTrue(buffer.isNotEmpty)

        // 0 = oldest, size-1 = newest
        assertEquals(100L, buffer.timeAt(0))
        assertEquals(220.0f, buffer.freqAt(0), 1e-4f)
        assertEquals(0.9f, buffer.confidenceAt(0), 1e-4f)

        assertEquals(200L, buffer.timeAt(1))
        assertEquals(221.0f, buffer.freqAt(1), 1e-4f)
        assertEquals(0.92f, buffer.confidenceAt(1), 1e-4f)

        assertEquals(300L, buffer.timeAt(2))
        assertEquals(222.0f, buffer.freqAt(2), 1e-4f)
        assertEquals(0.95f, buffer.confidenceAt(2), 1e-4f)
    }

    @Test
    fun testPushExceedingCapacityWrapsCorrectly() {
        val capacity = 5
        val buffer = PitchRingBuffer(capacity)

        // Push 8 items (0 until 8)
        for (i in 0 until 8) {
            buffer.push(i * 100L, i * 10.0f, i * 0.1f)
        }

        assertEquals(capacity, buffer.size)

        // Oldest 3 items (0, 1, 2) should have been overwritten.
        // Remaining items should be 3, 4, 5, 6, 7.
        for (i in 0 until capacity) {
            val originalVal = i + 3
            assertEquals(originalVal * 100L, buffer.timeAt(i))
            assertEquals(originalVal * 10.0f, buffer.freqAt(i), 1e-4f)
            assertEquals(originalVal * 0.1f, buffer.confidenceAt(i), 1e-4f)
        }
    }

    @Test
    fun testClear() {
        val buffer = PitchRingBuffer(5)
        buffer.push(100L, 440f, 0.8f)
        buffer.push(200L, 441f, 0.85f)
        assertEquals(2, buffer.size)

        buffer.clear()
        assertEquals(0, buffer.size)
        assertTrue(buffer.isEmpty)
        assertFalse(buffer.isNotEmpty)

        // Re-push after clear
        buffer.push(300L, 442f, 0.9f)
        assertEquals(1, buffer.size)
        assertEquals(300L, buffer.timeAt(0))
        assertEquals(442f, buffer.freqAt(0), 1e-4f)
    }

    @Test
    fun testForEachIndexed() {
        val capacity = 4
        val buffer = PitchRingBuffer(capacity)
        for (i in 0 until 6) {
            buffer.push(i * 100L, i * 50.0f, i * 0.15f)
        }

        // Active items are 2, 3, 4, 5
        val collectedIndices = mutableListOf<Int>()
        val collectedFreqs = mutableListOf<Float>()
        val collectedConfs = mutableListOf<Float>()

        buffer.forEachIndexed { i, freq, conf ->
            collectedIndices.add(i)
            collectedFreqs.add(freq)
            collectedConfs.add(conf)
        }

        assertEquals(listOf(0, 1, 2, 3), collectedIndices)
        assertEquals(listOf(100.0f, 150.0f, 200.0f, 250.0f), collectedFreqs)
        assertEquals(4, collectedConfs.size)
    }

    @Test
    fun testForEachPoint() {
        val buffer = PitchRingBuffer(3)
        buffer.push(10L, 100f, 0.1f)
        buffer.push(20L, 200f, 0.2f)
        buffer.push(30L, 300f, 0.3f)
        buffer.push(40L, 400f, 0.4f)

        val times = mutableListOf<Long>()
        val freqs = mutableListOf<Float>()

        buffer.forEachPoint { _, time, freq, _ ->
            times.add(time)
            freqs.add(freq)
        }

        assertEquals(listOf(20L, 30L, 40L), times)
        assertEquals(listOf(200f, 300f, 400f), freqs)
    }

    @Test
    fun testIndexOutOfBounds() {
        val buffer = PitchRingBuffer(5)
        buffer.push(10L, 100f, 0.5f)

        try {
            buffer.freqAt(-1)
            fail("Expected IndexOutOfBoundsException")
        } catch (e: IndexOutOfBoundsException) {
            // expected
        }

        try {
            buffer.timeAt(1)
            fail("Expected IndexOutOfBoundsException")
        } catch (e: IndexOutOfBoundsException) {
            // expected
        }
    }

    @Test
    fun testThroughput100kPushes() {
        val capacity = 1200
        val buffer = PitchRingBuffer(capacity)
        val startTime = System.nanoTime()

        for (i in 0 until 100_000) {
            buffer.push(i.toLong(), 440.0f + (i % 100), 0.95f)
        }

        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        assertEquals(1200, buffer.size)
        // 100k pushes should complete in under 50ms on any modern JVM
        assertTrue("Duration was ${durationMs}ms", durationMs < 500.0)
    }

    @Test
    fun testBinarySearchTimeWindow() {
        val buffer = PitchRingBuffer(10)
        // Push 15 items with timestamps 100, 200, ..., 1500
        for (i in 1..15) {
            buffer.push(i * 100L, 440f, 0.9f)
        }
        // Buffer has capacity 10, so items are 600L, 700L, ..., 1500L (indices 0..9)
        assertEquals(10, buffer.size)
        assertEquals(600L, buffer.timeAt(0))
        assertEquals(1500L, buffer.timeAt(9))

        // Search for window 800L..1200L
        val startIdx = buffer.findFirstIndexAtOrAfter(800L)
        val endIdx = buffer.findLastIndexAtOrBefore(1200L)

        assertEquals(2, startIdx) // 800L is at index 2
        assertEquals(6, endIdx)   // 1200L is at index 6
        assertEquals(800L, buffer.timeAt(startIdx))
        assertEquals(1200L, buffer.timeAt(endIdx))

        // Search for timestamps before all items
        assertEquals(0, buffer.findFirstIndexAtOrAfter(100L))
        assertEquals(-1, buffer.findLastIndexAtOrBefore(500L))

        // Search for timestamps after all items
        assertEquals(10, buffer.findFirstIndexAtOrAfter(2000L))
        assertEquals(9, buffer.findLastIndexAtOrBefore(2000L))
    }

    @Test
    fun testCopyVisibleWindow() {
        val buffer = PitchRingBuffer(10)
        for (i in 1..15) {
            buffer.push(i * 100L, i * 10f, 0.8f)
        }
        // Active timestamps: 600..1500 (indices 0..9)
        val scratch = VisibleWindowScratch(10)
        val copied = buffer.copyVisibleWindow(800L, 1200L, scratch)

        assertEquals(5, copied)
        assertEquals(5, scratch.count)
        assertEquals(800L, scratch.times[0])
        assertEquals(80f, scratch.freqs[0], 1e-4f)
        assertEquals(1200L, scratch.times[4])
        assertEquals(120f, scratch.freqs[4], 1e-4f)

        // Non-overlapping window returns 0
        val nonOverlapping = buffer.copyVisibleWindow(2000L, 3000L, scratch)
        assertEquals(0, nonOverlapping)
        assertEquals(0, scratch.count)
    }

    @Test
    fun testLastVoicedFreq() {
        val buffer = PitchRingBuffer(5)
        assertEquals(0f, buffer.lastVoicedFreq(), 1e-4f)

        buffer.push(100L, 0f, 0.1f) // unvoiced
        assertEquals(0f, buffer.lastVoicedFreq(), 1e-4f)

        buffer.push(200L, 130.8f, 0.9f) // voiced
        assertEquals(130.8f, buffer.lastVoicedFreq(), 1e-4f)

        buffer.push(300L, 0f, 0.2f) // unvoiced pause
        // Should retain the previous voiced frequency!
        assertEquals(130.8f, buffer.lastVoicedFreq(), 1e-4f)

        buffer.push(400L, 261.6f, 0.95f) // new voiced note
        assertEquals(261.6f, buffer.lastVoicedFreq(), 1e-4f)
    }
}
