package com.shrutimonitor.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
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
 * High-fidelity sample-based sequencer for a 4-string Indian Tanpura drone.
 * Modeled after the Dhwani Tanpura application.
 *
 * Implements:
 * - SoundPool sample sequencing with 8 active streams
 * - Double-buffered timing cycles to allow natural decay without clipping
 * - Piecewise linear cents-based playback rate tuning
 * - Dynamic 3-sample load/pruning cache to keep memory footprint under 3 MB
 * - Automatic Male (tone_type 1) vs Female (tone_type 3) mode detection based on Sa pitch
 * - Dynamic octave-shifting for lower Sa notes to keep them within available A2 (110 Hz) sample range
 * - Progressive volume fade-in on startup
 * - Smooth 900ms volume fade-out on stop
 */
class TanpuraSynthesizer(
    private val sampleRate: Int = 44100
) {

    companion object {
        private const val TAG = "TanpuraSequencer"
        
        // Singleton instance to prevent multiple overlapping play loops on tab switches
        val shared = TanpuraSynthesizer()
    }

    // Thread-safe settings
    @Volatile var saFrequency: Float = SwaraMapper.DEFAULT_SA_FREQUENCY
    @Volatile var saNoteName: String = "C"
    @Volatile var saOctave: Int = 4
    @Volatile var jhalaString: JhalaString = JhalaString.PA
    @Volatile var volume: Float = 0.5f
    @Volatile var speed: Float = 1.0f

    // Dhwani Tanpura specific settings
    @Volatile var paMaNiVolume: Float = 1.0f
    @Volatile var fineTuningCents: Float = 0.0f
    @Volatile var is432HzMode: Boolean = false

    // String plucking visual feedback callback
    @Volatile var onStringPlucked: ((Int) -> Unit)? = null

    private val isRunning = AtomicBoolean(false)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private var soundPool: SoundPool? = null
    private var playThread: Thread? = null
    private var appContext: Context? = null

    private val loadLock = Any()
    private val sampleMap = mutableMapOf<String, Int>()
    private val sampleReadyMap = mutableMapOf<Int, Boolean>()

    // Double buffering stream IDs
    private val evenStreams = IntArray(4) { 0 }
    private val oddStreams = IntArray(4) { 0 }

    /**
     * Supplies the Application Context for loading resources.
     */
    fun setContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            Log.d(TAG, "Application Context set.")
        }
    }

    /**
     * Returns true if the sequencer is running.
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Starts the Tanpura sequencing loop.
     */
    @Synchronized
    fun start() {
        if (isRunning.get()) return
        
        if (appContext == null) {
            Log.e(TAG, "Cannot start sequencer without context. Please call setContext() first.")
            return
        }

        isRunning.set(true)
        _isPlaying.value = true

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    synchronized(loadLock) {
                        if (status == 0) {
                            sampleReadyMap[sampleId] = true
                            Log.d(TAG, "Sample $sampleId loaded successfully")
                        } else {
                            Log.e(TAG, "Failed to load sample $sampleId: status $status")
                        }
                    }
                }
            }

        playThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            runSequencerLoop()
        }, "TanpuraSequencerThread").apply {
            start()
        }

        Log.d(TAG, "Tanpura Sequencer started.")
    }

    /**
     * Stops the Tanpura sequencer with a smooth 900ms fade-out.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false) // Signal plucking thread to stop immediately
        _isPlaying.value = false

        // Start a background thread to fade out active streams and release SoundPool
        Thread({
            val fadeSteps = 15
            val stepTime = 60L // 900ms total fade out
            val sp = soundPool
            
            if (sp != null) {
                val currentVol = volume
                for (step in 1..fadeSteps) {
                    val multiplier = (fadeSteps - step).toFloat() / fadeSteps
                    val vol = currentVol * multiplier
                    synchronized(loadLock) {
                        for (streamId in evenStreams + oddStreams) {
                            if (streamId != 0) {
                                try {
                                    sp.setVolume(streamId, vol, vol)
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(stepTime)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }

            // Final release and cleanup of stream state
            synchronized(loadLock) {
                if (soundPool == sp) {
                    stopAndClearStreams(sp, evenStreams)
                    stopAndClearStreams(sp, oddStreams)
                    sampleMap.clear()
                    sampleReadyMap.clear()
                    soundPool = null
                } else {
                    stopAndClearStreams(sp, evenStreams)
                    stopAndClearStreams(sp, oddStreams)
                }
                
                try {
                    sp?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing SoundPool in fade out", e)
                }
            }
            Log.d(TAG, "Tanpura Sequencer stopped and released after fade out.")
        }, "TanpuraStopFadeThread").start()

        playThread = null
    }

    /**
     * Map absolute pitchIndex [-3, 12] to high-fidelity sample filename suffix.
     * Maps to md (Mandra) or sf (Swar) based on isSwar parameter.
     */
    private fun getSampleName(pitchIndex: Int, maleMode: Boolean, isSwar: Boolean): String {
        val actualPitchIndex = if (maleMode) pitchIndex else pitchIndex + 12
        val v0 = actualPitchIndex + 3
       
        val noteNames = arrayOf(
            "a", "as", "b",
            "c", "cs", "d", "ds", "e", "f", "fs", "g", "gs",
            "a", "as", "b",
            "c", "cs", "d", "ds", "e", "f", "fs", "g", "gs",
            "a", "as", "b",
            "c", "cs", "d", "ds", "e", "f", "fs", "g", "gs",
            "a", "as", "b",
            "c"
        )
       
        val octaves = arrayOf(
            "-1", "-1", "-1",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "1", "1", "1", "1", "1", "1", "1", "1", "1", "1", "1", "1",
            "2", "2", "2", "2", "2", "2", "2", "2", "2", "2", "2", "2",
            "3"
        )
       
        val clampedV0 = v0.coerceIn(0, noteNames.size - 1)
        val noteName = noteNames[clampedV0]
        val octaveSuffix = octaves[clampedV0].replace("-1", "m1")
       
        val toneType = if (maleMode) "1" else "3"
        val playType = if (isSwar) "sf" else "md"
       
        return "res_${toneType}_${playType}_pl_${noteName}_${octaveSuffix}"
    }

    /**
     * Check cache and trigger non-blocking SoundPool load if needed.
     */
    private fun loadSampleIfNeeded(resName: String): Int {
        synchronized(loadLock) {
            val existingId = sampleMap[resName]
            if (existingId != null) {
                return existingId
            }

            val context = appContext ?: return 0
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId == 0) {
                Log.e(TAG, "Raw resource not found for name: $resName")
                return 0
            }

            val sp = soundPool ?: return 0
            val sampleId = sp.load(context, resId, 1)
            if (sampleId != 0) {
                sampleMap[resName] = sampleId
                sampleReadyMap[sampleId] = false
                Log.d(TAG, "Initiated load for sample $resName (ID: $sampleId)")
            }
            return sampleId
        }
    }

    /**
     * Wait for a sample to become fully ready (synchronizes async SoundPool loading).
     */
    private fun waitForReady(sampleId: Int, timeoutMs: Long = 1000): Boolean {
        if (sampleId == 0) return false
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val ready = synchronized(loadLock) {
                sampleReadyMap[sampleId] ?: false
            }
            if (ready) return true
            try {
                Thread.sleep(5)
            } catch (e: InterruptedException) {
                return false
            }
        }
        return false
    }

    /**
     * Prune unused samples from SoundPool memory to maintain low heap usage.
     */
    private fun pruneUnusedSamples(keepResNames: List<String>) {
        synchronized(loadLock) {
            val iterator = sampleMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val resName = entry.key
                val sampleId = entry.value
                if (resName !in keepResNames) {
                    try {
                        soundPool?.unload(sampleId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to unload sample ID $sampleId", e)
                    }
                    sampleReadyMap.remove(sampleId)
                    iterator.remove()
                    Log.d(TAG, "Unloaded unused sample: $resName ($sampleId)")
                }
            }
        }
    }

    /**
     * Calculates the piecewise linear playback rate based on cents offset.
     */
    private fun calculateRate(cents: Double): Float {
        val totalCents = cents + fineTuningCents + (if (is432HzMode) -32.0 else 0.0)
        val rate = if (totalCents > 0.0) {
            1.0 + (totalCents * 0.00059463095)
        } else if (totalCents < 0.0) {
            1.0 + (totalCents * 0.00056125689)
        } else {
            1.0
        }
        return rate.coerceIn(0.5, 2.0).toFloat()
    }

    /**
     * Plays a SoundPool sample.
     */
    private fun playSound(sampleId: Int, vol: Float, rate: Float): Int {
        val sp = soundPool ?: return 0
        val clampedVol = vol.coerceIn(0f, 1f)
        val streamId = sp.play(sampleId, clampedVol, clampedVol, 1, 0, rate)
        if (streamId == 0) {
            Log.e(TAG, "SoundPool failed to play sample: ID $sampleId")
        }
        return streamId
    }

    /**
     * Plays a string and notifies the visual animation callback if successful.
     */
    private fun playString(stringIndex: Int, sampleId: Int, vol: Float, rate: Float, activeGroup: IntArray) {
        synchronized(loadLock) {
            activeGroup[stringIndex] = playSound(sampleId, vol, rate)
        }
        onStringPlucked?.invoke(stringIndex)
    }

    /**
     * Stops and clears a group of stream IDs.
     */
    private fun stopAndClearStreams(sp: SoundPool?, streams: IntArray) {
        if (sp == null) return
        for (i in streams.indices) {
            val streamId = streams[i]
            if (streamId != 0) {
                try {
                    sp.stop(streamId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping stream $streamId", e)
                }
                streams[i] = 0
            }
        }
    }

    /**
     * Yields thread execution safely, checking if sequencer was stopped.
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
     * Sequencer loop executing plucks, double buffering, and sleep timing.
     */
    private fun runSequencerLoop() {
        Log.d(TAG, "Sequencer loop thread started")
        try {
            var isEvenCycle = true
            var pluckCount = 0

            // Synchronize initialization of streams
            synchronized(loadLock) {
                for (i in 0..3) {
                    evenStreams[i] = 0
                    oddStreams[i] = 0
                }
            }

            while (isRunning.get()) {
                val currentSa = saFrequency
                val currentNoteName = saNoteName
                val currentOctave = saOctave
                val currentJhala = jhalaString
                val currentVol = volume
                val currentSpeedVal = speed
                
                // 1. Determine Male/Female mode based on saFrequency
                // Threshold around G3/G#3 (180 Hz)
                val isFemale = currentSa >= 180f
                val maleMode = !isFemale
                val toneType = if (isFemale) 3 else 1
                
                // Calculate base pitchIndex relative to base octave (3 for Male, 4 for Female)
                val noteNamesList = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val noteIdx = noteNamesList.indexOf(currentNoteName).coerceAtLeast(0)
                val baseOctave = if (maleMode) 3 else 4
                var pitchIndex = (currentOctave - baseOctave) * 12 + noteIdx
                
                // Fold into valid range [-3, 12]
                while (pitchIndex > 12) {
                    pitchIndex -= 12
                }
                while (pitchIndex < -3) {
                    pitchIndex += 12
                }

                // Define string target pitch offsets relative to root
                val p0 = pitchIndex
                val p1 = pitchIndex + 12
                val p2 = pitchIndex + 12
                val semitones3 = when (currentJhala) {
                    JhalaString.PA -> 7
                    JhalaString.MA -> 5
                    JhalaString.NI -> 11
                }
                val p3 = pitchIndex + semitones3

                // 3. Resolve resource names
                val res0 = getSampleName(p0, maleMode, isSwar = false)
                val res12 = getSampleName(p1, maleMode, isSwar = true)
                val res3 = getSampleName(p3, maleMode, isSwar = false)
                
                val requiredRes = listOf(res0, res12, res3)
                
                // 4. Load samples and prune others to optimize memory heap
                val sampleId0 = loadSampleIfNeeded(res0)
                val sampleId12 = loadSampleIfNeeded(res12)
                val sampleId3 = loadSampleIfNeeded(res3)
                
                pruneUnusedSamples(requiredRes)
                
                // 5. Wait for SoundPool loading to complete for active samples
                val ready0 = waitForReady(sampleId0)
                val ready12 = waitForReady(sampleId12)
                val ready3 = waitForReady(sampleId3)
                
                if (!ready0 || !ready12 || !ready3) {
                    Log.w(TAG, "Audio samples not ready yet. Retrying...")
                    if (!safeSleep(100)) break
                    continue
                }
                
                // 6. Calculate playback rates with cents adjustments (exclusive to cents + 432 Hz)
                val totalCents = fineTuningCents + (if (is432HzMode) -32.0f else 0.0f)
                val rate = if (totalCents > 0.0f) {
                    1.0f + (totalCents * 0.00059463095f)
                } else if (totalCents < 0.0f) {
                    1.0f + (totalCents * 0.00056125689f)
                } else {
                    1.0f
                }
                val coercedRate = rate.coerceIn(0.5f, 2.0f)
                
                // 7. Calculate speed factor k from speed slider [0.5, 2.0]
                val k = if (currentSpeedVal < 1.0f) {
                    0.75f - (currentSpeedVal - 1.0f) * 0.42f
                } else {
                    0.75f - (currentSpeedVal - 1.0f) * 0.30f
                }
                val coercedK = k.coerceIn(0.45f, 0.96f)
                
                // 8. Play sequenced notes using double buffering
                val activeGroup = if (isEvenCycle) evenStreams else oddStreams
                
                // Stop and clear older streams of the same parity before starting plucks
                synchronized(loadLock) {
                    stopAndClearStreams(soundPool, activeGroup)
                }
                
                // Progressive volume fade-in scaling for start phase
                val startFade = when (pluckCount) {
                    0 -> 0.25f
                    1 -> 0.50f
                    2 -> 0.75f
                    else -> 1.0f
                }
                if (pluckCount < 3) {
                    pluckCount++
                }
                
                // Pluck String 0 (T = 0 ms)
                if (!isRunning.get()) break
                playString(0, sampleId0, currentVol * startFade * 1.0f, coercedRate, activeGroup)
                
                // Delay 1: T = 1580 * k ms
                val delay1 = (1580.0 * coercedK).toLong()
                if (!safeSleep(delay1)) break
                
                // Pluck String 3 (Pa/Ma/Ni/Sa)
                val vol3 = currentVol * startFade * paMaNiVolume * 0.96f
                playString(3, sampleId3, vol3, coercedRate, activeGroup)
                
                // Delay 2: T = 3060 * k ms -> Incremental delay = 1480 * k ms
                val delay2 = (1480.0 * coercedK).toLong()
                if (!safeSleep(delay2)) break
                
                // Pluck String 1
                playString(1, sampleId12, currentVol * startFade * 0.65f, coercedRate, activeGroup)
                
                // Delay 3: T = 3840 * k ms -> Incremental delay = 780 * k ms
                val delay3 = (780.0 * coercedK).toLong()
                if (!safeSleep(delay3)) break
                
                // Pluck String 2
                playString(2, sampleId12, currentVol * startFade * 0.90f, coercedRate, activeGroup)
                
                // Delay 4: T = 5320 * k ms -> Incremental delay = 1480 * k ms
                val delay4 = (1480.0 * coercedK).toLong()
                if (!safeSleep(delay4)) break
                
                // Switch stream buffers
                isEvenCycle = !isEvenCycle
            }
        } finally {
            isRunning.set(false)
            _isPlaying.value = false
            Log.d(TAG, "Sequencer loop thread finished")
        }
    }
}