package com.shrutimonitor.app.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.sangatkaar.android.OboeEngine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log2
import kotlin.math.round
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuration for the first string (Jhala string) of the Tanpura.
 */
enum class JhalaString {
    PA, MA, NI
}

/**
 * High-fidelity native audio engine for the Indian Tanpura drone.
 * Driven directly by native C++ Oboe audio streaming (liboboe.so)
 * and lossless WAV/FLAC impulse responses from the soundbank archive.
 *
 * Implements:
 * - Direct native low-latency Oboe audio streaming
 * - Continuous microtonal tonic Sa synthesis (folded to sounding range 67..88 with residual cents)
 * - Authentic non-linear bridge Javari acoustic modeling
 * - Dual 5-string Tanpura resonance with stereo panning (-0.48 / +0.48)
 * - Dynamic Jiva selection (Pa=-5, Ma=-7, Ni=-1) and 5th string (Sa=0)
 * - Progressive volume fade-in and graceful stop
 * - Cadence-synchronized UI string pluck animation dispatcher
 */
class TanpuraSynthesizer(
    private val sampleRate: Int = 44100
) {

    companion object {
        private const val TAG = "TanpuraSynthesizer"

        // Singleton instance to prevent multiple overlapping play loops on tab switches
        val shared = TanpuraSynthesizer()
    }

    private var appContext: Context? = null
    private val isRunning = AtomicBoolean(false)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var tickerThread: Thread? = null

    // String plucking visual feedback callback for UI animation
    @Volatile var onStringPlucked: ((Int) -> Unit)? = null

    // ── Tunable Audio Properties ────────────────────────────────────────

    @Volatile var saFrequency: Float = SwaraMapper.DEFAULT_SA_FREQUENCY
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                syncTuningToOboe()
            }
        }

    @Volatile var saNoteName: String = "C"

    @Volatile var saOctave: Int = 4

    @Volatile var jhalaString: JhalaString = JhalaString.PA
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                val jivaInterval = when (value) {
                    JhalaString.PA -> -5
                    JhalaString.MA -> -7
                    JhalaString.NI -> -1
                }
                OboeEngine.setJiva(0, jivaInterval)
                OboeEngine.setJiva(1, jivaInterval)
            }
        }

    @Volatile var volume: Float = 0.5f
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                val clamped = value.coerceIn(0f, 1f)
                OboeEngine.setVolume(0, clamped)
                OboeEngine.setVolume(1, (clamped * 0.85f).coerceIn(0f, 1f))
            }
        }

    @Volatile var speed: Float = 1.0f
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                val bpm = (OboeEngine.DEFAULT_BPM * value.toDouble()).coerceIn(
                    OboeEngine.BPM_RANGE.start,
                    OboeEngine.BPM_RANGE.endInclusive
                )
                OboeEngine.setBpm(bpm)
            }
        }

    @Volatile var paMaNiVolume: Float = 1.0f

    @Volatile var fineTuningCents: Float = 0.0f
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                syncTuningToOboe()
            }
        }

    @Volatile var is432HzMode: Boolean = false
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                syncTuningToOboe()
            }
        }

    @Volatile var fiveStringMode: Boolean = true
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                val fifthInterval = if (value) 0 else null
                OboeEngine.setFifth(0, fifthInterval)
                OboeEngine.setFifth(1, fifthInterval)
            }
        }

    @Volatile var dualTanpuraEnabled: Boolean = false
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                OboeEngine.setPower(1, value && isRunning.get())
            }
        }

    @Volatile var javariSpreadCents: Float = 2.2f
        set(value) {
            field = value
            if (OboeEngine.isReady()) {
                syncJavariToOboe()
            }
        }

    /**
     * Supplies the Application Context for loading native pak assets.
     */
    fun setContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            Log.d(TAG, "Application Context set.")
        }
        ensureLoaded(context.assets)
    }

    /**
     * Ensures native libraries and sound samples in the soundbank archive are loaded.
     */
    fun ensureLoaded(assets: AssetManager? = appContext?.assets): Boolean {
        if (OboeEngine.isReady()) return true
        if (assets == null) return false
        synchronized(this) {
            if (OboeEngine.isReady()) return true
            Log.d(TAG, "Loading native OboeEngine samples from assets...")
            val loaded = OboeEngine.load(assets)
            if (loaded) {
                OboeEngine.shrutiLoadSamples(assets)
                Log.d(TAG, "OboeEngine and ShrutiBox samples loaded successfully.")
            } else {
                Log.w(TAG, "OboeEngine.load(assets) failed or running in non-Android JVM.")
            }
            return loaded
        }
    }

    /**
     * Returns true if the sequencer is active.
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Converts continuous Hz to folded saMidi (67..88) and residual fine-tune cents.
     */
    fun syncTuningToOboe() {
        if (!OboeEngine.isReady()) return

        // Compute exact continuous MIDI from Hz:
        // MIDI = 69 + 12 * log2(Hz / 440) + 24 (since SA_SOUNDING_OFFSET = -24)
        val exactMidi = 69.0 + 12.0 * log2(saFrequency.toDouble() / 440.0) + 24.0

        var foldedMidi = exactMidi
        while (foldedMidi < 67.0) foldedMidi += 12.0
        while (foldedMidi > 88.0) foldedMidi -= 12.0

        val saMidi = round(foldedMidi).toInt().coerceIn(67, 88)
        val residualCents = (foldedMidi - saMidi) * 100.0 + fineTuningCents + (if (is432HzMode) -31.77 else 0.0)

        OboeEngine.setSa(saMidi)
        OboeEngine.setFineTune(residualCents)
    }

    /**
     * Applies non-linear Javari spread and microtonal Jod detuning to strings.
     */
    private fun syncJavariToOboe() {
        if (!OboeEngine.isReady()) return

        val javariVal = (0.5f + (javariSpreadCents / 20.0f)).coerceIn(0.1f, 1.0f)
        for (s in 0..4) {
            OboeEngine.setStringJavari(0, s, javariVal)
            OboeEngine.setStringJavari(1, s, javariVal)
        }

        // Apply acoustic bridge phase detuning between paired Jod strings
        val halfSpread = javariSpreadCents.toDouble() * 0.5
        OboeEngine.setStringCents(0, 1, halfSpread)
        OboeEngine.setStringCents(0, 2, -halfSpread)
        OboeEngine.setStringCents(1, 1, halfSpread)
        OboeEngine.setStringCents(1, 2, -halfSpread)
    }

    /**
     * Synchronizes all active settings to native OboeEngine.
     */
    fun syncParametersToOboe() {
        if (!OboeEngine.isReady()) return

        syncTuningToOboe()

        // Jiva interval (String 0)
        val jivaInterval = when (jhalaString) {
            JhalaString.PA -> -5
            JhalaString.MA -> -7
            JhalaString.NI -> -1
        }
        OboeEngine.setJiva(0, jivaInterval)
        OboeEngine.setJiva(1, jivaInterval)

        // Fifth string (0 = Sa)
        val fifthInterval = if (fiveStringMode) 0 else null
        OboeEngine.setFifth(0, fifthInterval)
        OboeEngine.setFifth(1, fifthInterval)

        // Master level & volumes
        OboeEngine.setMasterLevel(1.0f)
        val clampedVol = volume.coerceIn(0f, 1f)
        OboeEngine.setVolume(0, clampedVol)
        OboeEngine.setVolume(1, (clampedVol * 0.85f).coerceIn(0f, 1f))

        // Stereo pan
        OboeEngine.setPan(0, -0.48f)
        OboeEngine.setPan(1, 0.48f)

        // Tempo
        val bpm = (OboeEngine.DEFAULT_BPM * speed.toDouble()).coerceIn(
            OboeEngine.BPM_RANGE.start,
            OboeEngine.BPM_RANGE.endInclusive
        )
        OboeEngine.setBpm(bpm)

        // Non-linear Javari
        syncJavariToOboe()

        // Power channels
        OboeEngine.setPower(0, true)
        OboeEngine.setPower(1, dualTanpuraEnabled)
    }

    /**
     * Starts the Tanpura audio engine and visual pluck sync ticker.
     */
    @Synchronized
    fun start() {
        if (isRunning.get()) return

        appContext?.assets?.let { ensureLoaded(it) }

        isRunning.set(true)
        _isPlaying.value = true

        if (OboeEngine.isReady()) {
            syncParametersToOboe()
            OboeEngine.start()
            OboeEngine.beginFadeIn()
        } else {
            Log.w(TAG, "OboeEngine is not ready; start() triggered in headless/test mode.")
        }

        // Start visual animation sync ticker
        tickerThread = Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Throwable) {}
            runAnimationTicker()
        }, "TanpuraAnimationTicker").apply {
            start()
        }

        Log.d(TAG, "Tanpura Synthesizer started.")
        appContext?.let { try { DronePlaybackService.startService(it) } catch (_: Exception) {} }
    }

    /**
     * Stops the Tanpura audio engine gracefully.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)
        _isPlaying.value = false

        if (OboeEngine.isReady()) {
            OboeEngine.setPower(0, false)
            OboeEngine.setPower(1, false)
            OboeEngine.requestStop()
        }

        tickerThread?.interrupt()
        tickerThread = null

        Log.d(TAG, "Tanpura Synthesizer stopped.")
        appContext?.let { try { DronePlaybackService.stopService(it) } catch (_: Exception) {} }
    }

    /**
     * Safe sleep checking termination flag every 10ms.
     */
    private fun safeSleep(ms: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < ms) {
            if (!isRunning.get()) return false
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                return false
            }
        }
        return true
    }

    /**
     * Dispatches UI pluck animation timestamps in sync with the Tanpura pluck rhythm.
     */
    private fun runAnimationTicker() {
        try {
            while (isRunning.get()) {
                val currentSpeedVal = speed
                val k = if (currentSpeedVal < 1.0f) {
                    0.75f - (currentSpeedVal - 1.0f) * 0.42f
                } else {
                    0.75f - (currentSpeedVal - 1.0f) * 0.30f
                }.coerceIn(0.45f, 0.96f)

                // Pluck String 0 (Jiva: Pa/Ma/Ni)
                if (!isRunning.get()) break
                onStringPlucked?.invoke(0)

                // Delay 1: T = 1580 * k ms
                val delay1 = (1580.0 * k).toLong()
                if (!safeSleep(delay1)) break

                // Pluck String 3 (Kharaj / Bass Sa)
                onStringPlucked?.invoke(3)

                if (fiveStringMode) {
                    // Delay for 5th string: 520 * k ms
                    val delay5th = (520.0 * k).toLong()
                    if (!safeSleep(delay5th)) break
                    onStringPlucked?.invoke(4)

                    val remainingDelay2 = (960.0 * k).toLong()
                    if (!safeSleep(remainingDelay2)) break
                } else {
                    val delay2 = (1480.0 * k).toLong()
                    if (!safeSleep(delay2)) break
                }

                // Pluck String 1 (Jod 1)
                onStringPlucked?.invoke(1)

                // Delay 3: 780 * k ms
                val delay3 = (780.0 * k).toLong()
                if (!safeSleep(delay3)) break

                // Pluck String 2 (Jod 2)
                onStringPlucked?.invoke(2)

                // Delay 4: 1480 * k ms
                val delay4 = (1480.0 * k).toLong()
                if (!safeSleep(delay4)) break
            }
        } catch (_: InterruptedException) {
            // Normal termination on stop
        } finally {
            isRunning.set(false)
            _isPlaying.value = false
        }
    }
}