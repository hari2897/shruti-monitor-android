package com.shrutimonitor.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.shrutimonitor.app.data.Swara
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Polyphonic, Just Intonation–tuned keyboard for harmonium-style play.
 * High-quality dual-reed physical modeling with low latency.
 */
class KeyboardPlayer(
    private val sampleRate: Int = McLeodPitchDetector.SAMPLE_RATE,
    private val onHaptic: (() -> Unit)? = null
) {

    @Volatile private var saFrequency: Float = SwaraMapper.DEFAULT_SA_FREQUENCY

    private val _running = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    /** Active keyboard voices keyed by noteIndex. */
    private val voices = ConcurrentHashMap<Int, KeyVoice>()

    val isRunning: Boolean get() = _running.get()

    companion object {
        private const val TAG = "KeyboardPlayer"
        private const val TWO_PI = (2.0 * PI).toFloat()

        // Bellows envelope parameters in seconds
        private const val ATTACK_S = 0.030f // 30ms warm wind bellows buildup
        private const val DECAY_S = 0.080f  // 80ms decay
        private const val SUSTAIN_LEVEL = 0.8f
        private const val RELEASE_S = 0.050f // 50ms quick release when key is lifted

        private const val NUM_HARMONICS = 12

        // Harmonic amplitudes styled after physical brass reeds (nasal, rich in 2nd/3rd harmonics)
        private val HARMONIC_AMPS = floatArrayOf(
            1.00f, // 1st (Fundamental)
            0.85f, // 2nd
            0.95f, // 3rd (prominent odd harmonic for reed quality)
            0.60f, // 4th
            0.50f, // 5th
            0.40f, // 6th
            0.30f, // 7th
            0.25f, // 8th
            0.18f, // 9th
            0.12f, // 10th
            0.08f, // 11th
            0.05f  // 12th
        )

        // Singleton instance to prevent overlapping streams on screen transitions
        val shared = KeyboardPlayer()
    }

    // ════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════════════

    /** Starts the low-latency audio engine. */
    fun start() {
        if (_running.getAndSet(true)) return

        val bufSamples = 256 // Extremely compact synthesis chunk size (~5.8ms)
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        // Set buffer size to minimum required for double-buffering at lowest latency
        val bufSizeInBytes = maxOf(minBufferSize, bufSamples * 4 * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY) // Enable low-latency framework flag
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) // Force low latency hardware path
            .build()

        audioTrack = track
        track.play()

        playbackThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            renderLoop(track, bufSamples)
        }, "KeyboardPlayerThread").also { it.start() }

        Log.d(TAG, "Low latency keyboard engine started")
    }

    /** Stops all notes and releases audio resources. */
    fun stop() {
        voices.values.forEach { it.release() }
        if (!_running.getAndSet(false)) return

        // Pause/flush to immediately unblock write thread
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}

        try {
            playbackThread?.join(500)
        } catch (_: InterruptedException) {}
        playbackThread = null

        audioTrack?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        audioTrack = null

        voices.clear()
        Log.d(TAG, "Keyboard engine stopped")
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Triggers a note.
     *
     * @param noteIndex Absolute note index (0–47 spans ~4 octaves).
     */
    fun noteOn(noteIndex: Int) {
        if (!_running.get()) start()

        val freq = noteIndexToFrequency(noteIndex)
        if (freq <= 0f) return

        val voice = KeyVoice(
            frequency = freq,
            sampleRate = sampleRate,
            attackSamples = (ATTACK_S * sampleRate).toInt(),
            decaySamples = (DECAY_S * sampleRate).toInt(),
            sustainLevel = SUSTAIN_LEVEL,
            releaseSamples = (RELEASE_S * sampleRate).toInt()
        )

        voices[noteIndex] = voice
        onHaptic?.invoke()

        Log.d(TAG, "Note ON: idx=$noteIndex freq=${"%.2f".format(freq)} Hz")
    }

    /**
     * Releases a note.
     */
    fun noteOff(noteIndex: Int) {
        voices[noteIndex]?.release()
        Log.d(TAG, "Note OFF: idx=$noteIndex")
    }

    /**
     * Sets the tonic frequency.
     */
    fun setSaFrequency(freq: Float) {
        saFrequency = freq.coerceIn(50f, 1000f)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Frequency mapping
    // ════════════════════════════════════════════════════════════════════

    private fun noteIndexToFrequency(noteIndex: Int): Float {
        if (noteIndex < 0) return 0f

        val swaraIndex = noteIndex % 12
        val octave = noteIndex / 12

        val swara = Swara.fromIndex(swaraIndex)
        val sa = saFrequency.toDouble()

        val octaveMultiplier = Math.pow(2.0, (octave - 1).toDouble())
        return (sa * swara.jiRatio * octaveMultiplier).toFloat()
    }

    // ════════════════════════════════════════════════════════════════════
    //  Render loop
    // ════════════════════════════════════════════════════════════════════

    private fun renderLoop(track: AudioTrack, bufSamples: Int) {
        val buf = FloatArray(bufSamples)

        while (_running.get()) {
            buf.fill(0f)

            val toRemove = mutableListOf<Int>()

            for ((key, voice) in voices) {
                if (voice.isDone) {
                    toRemove.add(key)
                    continue
                }
                for (i in buf.indices) {
                    buf[i] += voice.nextSample()
                }
            }

            toRemove.forEach { voices.remove(it) }

            // Dynamic limiter/scaling to prevent clipping with multiple keys pressed
            val numVoices = voices.size
            val scale = if (numVoices > 0) 1.0f / Math.sqrt(numVoices.toDouble()).toFloat() else 1.0f
            for (i in buf.indices) {
                buf[i] = (buf[i] * scale).coerceIn(-1.0f, 1.0f)
            }

            if (!_running.get()) break
            
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val written = track.write(buf, 0, bufSamples, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack.write error: $written")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception writing to AudioTrack", e)
                break
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  KeyVoice — ADSR + detuned dual reed additive synthesis
    // ════════════════════════════════════════════════════════════════════

    private class KeyVoice(
        val frequency: Float,
        val sampleRate: Int,
        val attackSamples: Int,
        val decaySamples: Int,
        val sustainLevel: Float,
        val releaseSamples: Int
    ) {
        // Dual reed phases to simulate a harmonium's twin-reed stop layout (detuned chorus)
        private val phasesA = FloatArray(NUM_HARMONICS)
        private val phasesB = FloatArray(NUM_HARMONICS)
        private var sampleIndex = 0L
        private var state = State.ATTACK
        private var envelopeLevel = 0f
        var isDone = false
            private set

        private enum class State { ATTACK, DECAY, SUSTAIN, RELEASE, DONE }

        fun release() {
            if (state != State.DONE && state != State.RELEASE) {
                state = State.RELEASE
                sampleIndex = 0
            }
        }

        fun nextSample(): Float {
            if (isDone) return 0f

            // Advance ADSR envelope
            when (state) {
                State.ATTACK -> {
                    envelopeLevel = sampleIndex.toFloat() / attackSamples
                    if (sampleIndex >= attackSamples) {
                        state = State.DECAY
                        sampleIndex = 0
                        envelopeLevel = 1f
                    }
                }
                State.DECAY -> {
                    val t = sampleIndex.toFloat() / decaySamples
                    envelopeLevel = 1f - (1f - sustainLevel) * t
                    if (sampleIndex >= decaySamples) {
                        state = State.SUSTAIN
                        envelopeLevel = sustainLevel
                    }
                }
                State.SUSTAIN -> {
                    envelopeLevel = sustainLevel
                }
                State.RELEASE -> {
                    val t = sampleIndex.toFloat() / releaseSamples
                    envelopeLevel = sustainLevel * (1f - t)
                    if (sampleIndex >= releaseSamples) {
                        state = State.DONE
                        isDone = true
                        return 0f
                    }
                }
                State.DONE -> {
                    isDone = true
                    return 0f
                }
            }

            // Detroit chorus factor: 3 cents = 2^(3/1200) = 1.00173
            val frequencyA = frequency
            val frequencyB = frequency * 1.00173f

            var sampleSum = 0f

            // ── REED STOP A ──
            for (n in 0 until NUM_HARMONICS) {
                val harmonicFreq = frequencyA * (n + 1)
                if (harmonicFreq > sampleRate / 2f) break

                val phaseInc = TWO_PI * harmonicFreq / sampleRate
                phasesA[n] += phaseInc
                if (phasesA[n] > TWO_PI) phasesA[n] -= TWO_PI

                sampleSum += HARMONIC_AMPS[n] * sin(phasesA[n].toDouble()).toFloat()
            }

            // ── REED STOP B (Detuned for beating chorus) ──
            for (n in 0 until NUM_HARMONICS) {
                val harmonicFreq = frequencyB * (n + 1)
                if (harmonicFreq > sampleRate / 2f) break

                val phaseInc = TWO_PI * harmonicFreq / sampleRate
                phasesB[n] += phaseInc
                if (phasesB[n] > TWO_PI) phasesB[n] -= TWO_PI

                // Slightly lower amplitude for secondary reed
                sampleSum += HARMONIC_AMPS[n] * 0.75f * sin(phasesB[n].toDouble()).toFloat()
            }

            // Normalize reeds sum
            sampleSum *= 0.5f

            // Mix in a tiny amount of continuous bellows air friction noise
            val bellowsNoise = (Random.nextFloat() * 2f - 1f) * 0.015f
            sampleSum += bellowsNoise

            sampleIndex++
            return sampleSum * envelopeLevel * 0.28f
        }
    }
}
