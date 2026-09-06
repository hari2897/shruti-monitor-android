package com.shrutimonitor.app.audio

/**
 * Fixed-capacity primitive ring buffer for pitch history.
 * Backed by parallel primitive arrays to eliminate GC allocations during real-time tracking.
 *
 * @param capacity Maximum number of history points retained (e.g. 1200 points).
 */
class PitchRingBuffer(val capacity: Int) {
    init {
        require(capacity > 0) { "Capacity must be greater than 0, was $capacity" }
    }

    @PublishedApi internal val times = LongArray(capacity)
    @PublishedApi internal val freqs = FloatArray(capacity)
    @PublishedApi internal val confidences = FloatArray(capacity)

    @PublishedApi internal var head = 0
    @PublishedApi internal var count = 0

    val size: Int
        @Synchronized get() = count

    val isEmpty: Boolean
        @Synchronized get() = count == 0

    val isNotEmpty: Boolean
        @Synchronized get() = count > 0

    @Synchronized
    fun push(timeMs: Long, freqHz: Float, confidence: Float) {
        times[head] = timeMs
        freqs[head] = freqHz
        confidences[head] = confidence
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    @Synchronized
    fun clear() {
        head = 0
        count = 0
    }

    /**
     * Returns the frequency in Hz at the specified logical [index].
     * 0 = oldest entry, [size] - 1 = newest entry.
     */
    @Synchronized
    fun freqAt(index: Int): Float {
        checkIndex(index)
        return freqs[physicalIndex(index)]
    }

    /**
     * Returns the timestamp in milliseconds at the specified logical [index].
     * 0 = oldest entry, [size] - 1 = newest entry.
     */
    @Synchronized
    fun timeAt(index: Int): Long {
        checkIndex(index)
        return times[physicalIndex(index)]
    }

    /**
     * Returns the confidence metric at the specified logical [index].
     * 0 = oldest entry, [size] - 1 = newest entry.
     */
    @Synchronized
    fun confidenceAt(index: Int): Float {
        checkIndex(index)
        return confidences[physicalIndex(index)]
    }

    /**
     * Iterates over all stored points from oldest (i = 0) to newest (i = size - 1)
     * without creating iterator or wrapper objects.
     */
    inline fun forEachIndexed(action: (i: Int, freq: Float, conf: Float) -> Unit) {
        synchronized(this) {
            val n = count
            val start = (head - n + capacity * 2) % capacity
            for (i in 0 until n) {
                val idx = (start + i) % capacity
                action(i, freqs[idx], confidences[idx])
            }
        }
    }

    /**
     * Iterates over all stored points including timeMs from oldest to newest.
     */
    inline fun forEachPoint(action: (i: Int, timeMs: Long, freq: Float, conf: Float) -> Unit) {
        synchronized(this) {
            val n = count
            val start = (head - n + capacity * 2) % capacity
            for (i in 0 until n) {
                val idx = (start + i) % capacity
                action(i, times[idx], freqs[idx], confidences[idx])
            }
        }
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $count (capacity $capacity)")
        }
    }

    private fun physicalIndex(index: Int): Int {
        return (head - count + index + capacity * 2) % capacity
    }
}
