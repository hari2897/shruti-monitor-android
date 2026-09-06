package com.shrutimonitor.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages real-time microphone audio capture using Android's AudioRecord API.
 *
 * It captures audio at 44.1kHz mono in 16-bit PCM, converts samples to floating point,
 * and maintains a sliding window of 2048 samples with 50% overlap (hop size of 1024),
 * emitting complete frames via a SharedFlow.
 */
data class CapturedAudioFrame(
    val samples: FloatArray,
    val timestampMs: Long
)

class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 2048
        private const val HOP_SIZE = 1024
    }

    private val _audioFrames = MutableSharedFlow<CapturedAudioFrame>(extraBufferCapacity = 64)
    /**
     * Exposes a stream of captured audio frames with hardware capture timestamps.
     */
    val audioFrames: SharedFlow<CapturedAudioFrame> = _audioFrames.asSharedFlow()


    private val isRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Returns true if audio capture is currently active.
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Starts audio capture if permissions are granted. Runs on a background thread.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) return true

        // Verify record audio permission
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission is not granted.")
            return false
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            val recordBufferSize = maxOf(minBufferSize, BUFFER_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                recordBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord.")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRunning.set(true)

            // Start capture loop in coroutine
            captureJob = scope.launch {
                runCaptureLoop()
            }

            Log.d(TAG, "Audio capture started successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio capture", e)
            stop()
            return false
        }
    }

    /**
     * Stops the audio capture and releases resources.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.get()) return

        isRunning.set(false)
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
        Log.d(TAG, "Audio capture stopped.")
    }

    /**
     * Inner capture loop that handles sliding buffer window (50% overlap).
     */
    private suspend fun runCaptureLoop() {
        val record = audioRecord ?: return
        
        // Circular-like sliding buffer of 2048 elements
        val analysisBuffer = FloatArray(BUFFER_SIZE)
        val readBuffer = ShortArray(HOP_SIZE)

        while (isRunning.get() && captureJob?.isActive == true) {
            val readResult = record.read(readBuffer, 0, HOP_SIZE)
            
            if (readResult < 0) {
                Log.e(TAG, "AudioRecord read error: $readResult")
                break
            }

            if (readResult > 0) {
                // 1. Shift old samples in the analysis buffer: index 1024..2047 moves to 0..1023
                System.arraycopy(analysisBuffer, HOP_SIZE, analysisBuffer, 0, HOP_SIZE)

                // 2. Convert and load new samples into 1024..2047
                for (i in 0 until readResult) {
                    analysisBuffer[HOP_SIZE + i] = readBuffer[i].toFloat() / 32768.0f
                }

                // If we didn't read a full hop (e.g. read error/short read), pad the rest with zeroes
                for (i in readResult until HOP_SIZE) {
                    analysisBuffer[HOP_SIZE + i] = 0.0f
                }

                val captureTimeMs = android.os.SystemClock.uptimeMillis()

                // 3. Emit a copy of the complete 2048-sample analysis window with capture timestamp
                val frameCopy = analysisBuffer.clone()
                _audioFrames.emit(CapturedAudioFrame(frameCopy, captureTimeMs))
            }
        }
    }
}