package com.shrutimonitor.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.shrutimonitor.app.data.Swara
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays sustained reference tones (shruti petti / swara generator).
 *
 * Each active swara is rendered as a fundamental + 2 harmonics and output via
 * a shared [AudioTrack]. Multiple swaras can sound simultaneously.
 *
 * ### Synthesis
 * - Fundamental + 2nd harmonic (0.5×) + 3rd harmonic (0.25×)
 * - Smooth 50 ms fade-in on start, 100 ms fade-out on stop
 * - All frequencies are Just Intonation, computed relative to [saFrequency]
 *
 * @param sampleRate Audio sample rate (default 44100).
 */
class ShrutiPettiPlayer(
    private val sampleRate: Int = McLeodPitchDetector.SAMPLE_RATE
) {

    @Volatile var saFrequency: Float = SwaraMapper.DEFAULT_SA_FREQUENCY

    private val _running = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    /**
     * Key: encoded as `swaraIndex * 10 + saptak.ordinal` for uniqueness.
     * Value: the [ToneVoice].
     */
    private val activeVoices = ConcurrentHashMap<Int, ToneVoice>()

    /** Whether the audio engine is active. */
    val isRunning: Boolean get() = _running.get()

    companion object {
        const val TAG = "ShrutiPettiPlayer"
        const val FADE_IN_MS = 50f
        const val FADE_OUT_MS = 100f
        const val NUM_HARMONICS = 3
        val HARMONIC_AMPS = floatArrayOf(1.0f, 0.5f, 0.25f)
        const val TWO_PI = (2.0 * PI).toFloat()

        val shared = ShrutiPettiPlayer()
    }

    // ════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════════════

    /**
     * Initialises the audio output. Must be called before [playSwara].
     * Idempotent — safe to call multiple times.
     */
    fun start() {
        if (_running.getAndSet(true)) return

        val bufSamples = sampleRate / 10
        val bufBytes = bufSamples * 4

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        playbackThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            renderLoop(track, bufSamples)
        }, "ShrutiPetti").also { it.start() }

        Log.d(TAG, "Shruti petti engine started")
    }

    /**
     * Stops all tones and releases audio resources.
     */
    fun stop() {
        stopAll()
        if (!_running.getAndSet(false)) return

        playbackThread?.join(500)
        playbackThread = null

        audioTrack?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        audioTrack = null

        Log.d(TAG, "Shruti petti engine stopped")
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Starts playing a sustained swara tone.
     *
     * @param swaraIndex Index 0–11 matching [Swara.entries] order.
     * @param saptak Octave register.
     */
    fun playSwara(swaraIndex: Int, saptak: Saptak = Saptak.MADHYA) {
        if (!_running.get()) {
            start() // auto-start engine
        }

        val swara = Swara.entries.getOrNull(swaraIndex) ?: return
        val freq = SwaraMapper.idealFrequency(swara, saptak, saFrequency)
        val key = voiceKey(swaraIndex, saptak)

        // If already playing this swara, don't restart
        if (activeVoices.containsKey(key)) return

        val voice = ToneVoice(
            frequency = freq,
            sampleRate = sampleRate,
            fadeInSamples = (FADE_IN_MS / 1000f * sampleRate).toInt(),
            fadeOutSamples = (FADE_OUT_MS / 1000f * sampleRate).toInt()
        )
        activeVoices[key] = voice

        Log.d(TAG, "Playing ${swara.hindustaniName} (${saptak.name}) at ${"%.2f".format(freq)} Hz")
    }

    /**
     * Stops a specific swara (initiates fade-out).
     *
     * @param swaraIndex Index 0–11.
     * @param saptak Octave register.
     */
    fun stopSwara(swaraIndex: Int, saptak: Saptak = Saptak.MADHYA) {
        val key = voiceKey(swaraIndex, saptak)
        activeVoices[key]?.requestStop()
    }

    /**
     * Stops all currently sounding swaras.
     */
    fun stopAll() {
        activeVoices.values.forEach { it.requestStop() }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Render loop
    // ════════════════════════════════════════════════════════════════════

    private fun renderLoop(track: AudioTrack, bufSamples: Int) {
        val buf = FloatArray(bufSamples)

        while (_running.get()) {
            buf.fill(0f)

            // Mix all active voices
            val voicesToRemove = mutableListOf<Int>()

            for ((key, voice) in activeVoices) {
                if (voice.isDone) {
                    voicesToRemove.add(key)
                    continue
                }
                for (i in buf.indices) {
                    buf[i] += voice.nextSample()
                }
            }

            // Clean up finished voices
            voicesToRemove.forEach { activeVoices.remove(it) }

            // Clamp
            for (i in buf.indices) {
                buf[i] = buf[i].coerceIn(-1f, 1f)
            }

            val written = track.write(buf, 0, bufSamples, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.e(TAG, "AudioTrack.write error: $written")
                break
            }
        }
    }

    private fun voiceKey(swaraIndex: Int, saptak: Saptak): Int = swaraIndex * 10 + saptak.ordinal

    // ════════════════════════════════════════════════════════════════════
    //  ToneVoice — sustained tone with fade in/out
    // ════════════════════════════════════════════════════════════════════

    /**
     * A single sustained tone with fade-in and fade-out.
     */
    private class ToneVoice(
        val frequency: Float,
        val sampleRate: Int,
        val fadeInSamples: Int,
        val fadeOutSamples: Int
    ) {
        private val phases = FloatArray(NUM_HARMONICS)
        private var sampleIndex = 0L
        private var stopping = false
        private var fadeOutCounter = 0
        var isDone = false
            private set

        /** Requests a smooth fade-out. */
        fun requestStop() {
            if (!stopping) {
                stopping = true
                fadeOutCounter = fadeOutSamples
            }
        }

        /** Renders the next sample. */
        fun nextSample(): Float {
            if (isDone) return 0f

            // Fade-in envelope
            val fadeIn = if (sampleIndex < fadeInSamples) {
                sampleIndex.toFloat() / fadeInSamples
            } else {
                1f
            }

            // Fade-out envelope
            val fadeOut = if (stopping) {
                val ratio = fadeOutCounter.toFloat() / fadeOutSamples
                fadeOutCounter--
                if (fadeOutCounter <= 0) {
                    isDone = true
                }
                ratio
            } else {
                1f
            }

            // Additive synthesis: fundamental + harmonics
            var sample = 0f
            for (n in 0 until NUM_HARMONICS) {
                val harmonicFreq = frequency * (n + 1)
                if (harmonicFreq > sampleRate / 2f) break

                val phaseInc = TWO_PI * harmonicFreq / sampleRate
                phases[n] += phaseInc
                if (phases[n] > TWO_PI) phases[n] -= TWO_PI

                sample += HARMONIC_AMPS[n] * sin(phases[n].toDouble()).toFloat()
            }

            sampleIndex++
            return sample * fadeIn * fadeOut * 0.3f // volume scaling
        }
    }
}
