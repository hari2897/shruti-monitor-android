package com.shrutimonitor.app.audio

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records audio sessions to .m4a files and stores timestamped pitch data
 * in a companion JSON sidecar file.
 *
 * ### File layout
 * For a recording started at 2024-01-15_14-30-00:
 * ```
 * cache/recordings/
 *   session_2024-01-15_14-30-00.m4a      ← audio
 *   session_2024-01-15_14-30-00.json      ← pitch data
 * ```
 *
 * ### Pitch sidecar format (JSON)
 * ```json
 * {
 *   "saFrequency": 261.63,
 *   "sampleRate": 44100,
 *   "startTime": 1705312200000,
 *   "points": [
 *     { "t": 0, "f": 262.1, "c": 0.95, "s": "Sa", "d": 3.1 },
 *     ...
 *   ]
 * }
 * ```
 * - `t` = offset in ms from start
 * - `f` = frequency Hz
 * - `c` = confidence
 * - `s` = swara name
 * - `d` = cent deviation from ideal
 *
 * @param context Android context for file access and sharing intents.
 */
class SessionRecorder(
    private val context: Context
) {

    // ── State ──────────────────────────────────────────────────────────
    private val _recording = AtomicBoolean(false)
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var jsonFile: File? = null
    private var startTimeMs = 0L
    private var elapsedJob: Job? = null
    private var scope: CoroutineScope? = null
    @Volatile private var elapsedMs = 0L

    /** Collected pitch data points during the session. */
    private val pitchPoints = CopyOnWriteArrayList<PitchDataPoint>()

    /** Sa frequency at the time recording started (for the JSON sidecar). */
    @Volatile var saFrequency: Float = SwaraMapper.DEFAULT_SA_FREQUENCY

    /** Whether a recording is in progress. */
    val isRecording: Boolean get() = _recording.get()

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Starts recording audio to an .m4a file.
     *
     * @param outputDir Optional directory for the output file. Defaults to
     *                  `context.cacheDir/recordings`.
     * @return The output [File] that will contain the recording, or `null` on failure.
     */
    fun startRecording(outputDir: File? = null): File? {
        if (_recording.getAndSet(true)) {
            Log.w(TAG, "Already recording")
            return outputFile
        }

        val dir = outputDir ?: File(context.cacheDir, "recordings")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val audioFile = File(dir, "session_$timestamp.m4a")
        val sidecar = File(dir, "session_$timestamp.json")

        outputFile = audioFile
        jsonFile = sidecar
        startTimeMs = System.currentTimeMillis()
        elapsedMs = 0
        pitchPoints.clear()

        val recorder = createMediaRecorder(audioFile)
        if (recorder == null) {
            Log.e(TAG, "Failed to create MediaRecorder")
            _recording.set(false)
            return null
        }

        try {
            recorder.prepare()
            recorder.start()
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder.start() failed", e)
            recorder.release()
            _recording.set(false)
            return null
        }

        mediaRecorder = recorder

        // Elapsed time tracker
        val s = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = s
        elapsedJob = s.launch {
            while (isActive && _recording.get()) {
                elapsedMs = System.currentTimeMillis() - startTimeMs
                delay(100)
            }
        }

        Log.d(TAG, "Recording started: ${audioFile.absolutePath}")
        return audioFile
    }

    /**
     * Stops recording and finalises both the audio and JSON sidecar files.
     *
     * @return The output .m4a [File], or `null` if nothing was recording.
     */
    fun stopRecording(): File? {
        if (!_recording.getAndSet(false)) return null

        elapsedJob?.cancel()
        scope?.cancel()

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder.stop() failed", e)
        }
        mediaRecorder?.release()
        mediaRecorder = null

        // Write pitch sidecar JSON
        writePitchSidecar()

        val file = outputFile
        Log.d(TAG, "Recording stopped: ${file?.absolutePath}")
        return file
    }

    /**
     * Returns the elapsed recording time in milliseconds.
     */
    fun getElapsedTime(): Long = elapsedMs

    /**
     * Adds a pitch data point to the session sidecar.
     * Call this from the pitch detection pipeline during recording.
     *
     * @param result The swara mapping result for the current frame.
     */
    fun addPitchPoint(result: SwaraResult) {
        if (!_recording.get()) return

        pitchPoints.add(
            PitchDataPoint(
                offsetMs = System.currentTimeMillis() - startTimeMs,
                frequency = result.frequency,
                confidence = 0f, // filled by caller if available
                swaraName = result.swara.hindustaniName,
                centDeviation = result.centDeviation
            )
        )
    }

    /**
     * Adds a pitch data point with explicit confidence.
     */
    fun addPitchPoint(result: SwaraResult, confidence: Float) {
        if (!_recording.get()) return

        pitchPoints.add(
            PitchDataPoint(
                offsetMs = System.currentTimeMillis() - startTimeMs,
                frequency = result.frequency,
                confidence = confidence,
                swaraName = result.swara.hindustaniName,
                centDeviation = result.centDeviation
            )
        )
    }

    /**
     * Creates an [Intent] to share the recorded .m4a file via Android's share sheet.
     *
     * @param authority FileProvider authority string (e.g. `"${packageName}.fileprovider"`).
     * @return A share [Intent], or `null` if no recording exists.
     */
    fun createShareIntent(authority: String): Intent? {
        val file = outputFile ?: return null
        if (!file.exists()) return null

        val uri = FileProvider.getUriForFile(context, authority, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/m4a"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Returns the companion JSON sidecar file path, or `null` if none exists.
     */
    fun getSidecarFile(): File? = jsonFile?.takeIf { it.exists() }

    // ════════════════════════════════════════════════════════════════════
    //  Internal
    // ════════════════════════════════════════════════════════════════════

    /**
     * Creates and configures a [MediaRecorder] for AAC / .m4a output.
     */
    @Suppress("DEPRECATION")
    private fun createMediaRecorder(outputFile: File): MediaRecorder? {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder configuration failed", e)
            null
        }
    }

    /**
     * Writes the pitch data sidecar JSON file.
     */
    private fun writePitchSidecar() {
        val file = jsonFile ?: return

        try {
            val root = JSONObject().apply {
                put("saFrequency", saFrequency.toDouble())
                put("sampleRate", McLeodPitchDetector.SAMPLE_RATE)
                put("startTime", startTimeMs)
                put("durationMs", elapsedMs)

                val pointsArray = JSONArray()
                for (p in pitchPoints) {
                    pointsArray.put(JSONObject().apply {
                        put("t", p.offsetMs)
                        put("f", p.frequency.toDouble())
                        put("c", p.confidence.toDouble())
                        put("s", p.swaraName)
                        put("d", p.centDeviation.toDouble())
                    })
                }
                put("points", pointsArray)
            }

            file.writeText(root.toString(2))
            Log.d(TAG, "Pitch sidecar written: ${file.absolutePath} (${pitchPoints.size} points)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write pitch sidecar", e)
        }
    }

    /** Internal data holder for a single pitch measurement. */
    private data class PitchDataPoint(
        val offsetMs: Long,
        val frequency: Float,
        val confidence: Float,
        val swaraName: String,
        val centDeviation: Float
    )

    companion object {
        private const val TAG = "SessionRecorder"
    }
}
