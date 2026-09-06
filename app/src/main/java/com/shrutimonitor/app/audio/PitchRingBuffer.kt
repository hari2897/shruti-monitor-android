package com.shrutimonitor.app.audio

import androidx.compose.runtime.Stable

/**
 * Fixed-capacity primitive ring buffer for pitch history.
 * Backed by parallel primitive arrays to eliminate GC allocations during real-time tracking.
 *
 * @param capacity Maximum number of history points retained (e.g. 1200 points).
 */
@Stable
class PitchRingBuffer(val capacity: Int) {
    companion object {
        const val UNVOICED_GAP = -1.0f  // Voiced transition/consonant dip above noise floor
        const val SILENCE_GAP = -2.0f   // Confirmed silence at noise floor
    }

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
        val safeTime = if (count > 0) {
            val prevIdx = (head - 1 + capacity) % capacity
            maxOf(timeMs, times[prevIdx])
        } else {
            timeMs
        }
        times[head] = safeTime
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

    /**
     * Finds the first index i in 0 until size where timeAt(i) >= [targetTimeMs].
     * If all points have time < targetTimeMs, returns [size].
     * Runs in O(log N) time via binary search.
     */
    @Synchronized
    fun findFirstIndexAtOrAfter(targetTimeMs: Long): Int {
        var low = 0
        var high = count - 1
        var result = count

        while (low <= high) {
            val mid = (low + high) ushr 1
            val t = times[physicalIndex(mid)]
            if (t >= targetTimeMs) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return result
    }

    /**
     * Finds the last index i in 0 until size where timeAt(i) <= [targetTimeMs].
     * If all points have time > targetTimeMs, returns -1.
     * Runs in O(log N) time via binary search.
     */
    @Synchronized
    fun findLastIndexAtOrBefore(targetTimeMs: Long): Int {
        var low = 0
        var high = count - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val t = times[physicalIndex(mid)]
            if (t <= targetTimeMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Copies the visible window between [startTimeMs] and [endTimeMs] into [scratch]
     * under a single monitor lock. This allows callers to render completely lock-free.
     *
     * @return the number of points copied into [scratch].
     */
    @Synchronized
    fun copyVisibleWindow(
        startTimeMs: Long,
        endTimeMs: Long,
        scratch: VisibleWindowScratch
    ): Int {
        val first = findFirstIndexAtOrAfter(startTimeMs)
        val last = findLastIndexAtOrBefore(endTimeMs)

        if (first > last || first >= count || last < 0) {
            scratch.count = 0
            return 0
        }

        val numPoints = (last - first + 1).coerceAtMost(scratch.maxPoints)
        val startPos = (head - count + first + capacity * 2) % capacity

        for (i in 0 until numPoints) {
            val physicalIdx = (startPos + i) % capacity
            scratch.times[i] = times[physicalIdx]
            scratch.freqs[i] = freqs[physicalIdx]
        }
        scratch.count = numPoints
        return numPoints
    }

    /**
     * Returns the most recent voiced frequency in Hz (where freq > 0),
     * or 0f if none found.
     */
    @Synchronized
    fun lastVoicedFreq(): Float {
        for (i in count - 1 downTo 0) {
            val f = freqs[physicalIndex(i)]
            if (f > 0f) return f
        }
        return 0f
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

/**
 * Reusable container for a lock-free snapshot of visible pitch points for rendering.
 */
class VisibleWindowScratch(val maxPoints: Int = 1200) {
    val times = LongArray(maxPoints)
    val freqs = FloatArray(maxPoints)
    var count = 0
}
