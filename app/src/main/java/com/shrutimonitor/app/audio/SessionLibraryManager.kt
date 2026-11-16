package com.shrutimonitor.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import org.json.JSONObject
import java.io.File

data class SavedSessionInfo(
    val id: String,
    val displayTitle: String,
    val audioFile: File,
    val jsonFile: File,
    val saFrequency: Float,
    val durationMs: Long,
    val pointCount: Int
)

/**
 * Compact, zero-GC snapshot of a reference session's pitch contour
 * used to render a translucent Ghost Trace on PitchGraph.
 */
class GhostTraceData(
    val title: String,
    val saFrequency: Float,
    val durationMs: Long,
    val offsetMs: LongArray,
    val freqsHz: FloatArray
) {
    val size: Int get() = offsetMs.size
}

class SessionLibraryManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun listSavedSessions(): List<SavedSessionInfo> {
        val dir = File(context.cacheDir, "recordings")
        if (!dir.exists()) return emptyList()

        val jsonFiles = dir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()

        val result = mutableListOf<SavedSessionInfo>()
        for (jf in jsonFiles) {
            val baseName = jf.nameWithoutExtension
            val af = File(dir, "$baseName.m4a")
            if (!af.exists()) continue
            try {
                val root = JSONObject(jf.readText())
                val sa = root.optDouble("saFrequency", 261.63).toFloat()
                val dur = root.optLong("durationMs", 0L)
                val pts = root.optJSONArray("points")
                val count = pts?.length() ?: 0
                val cleanTitle = baseName.removePrefix("session_").replace("_", " ")
                result.add(
                    SavedSessionInfo(
                        id = baseName,
                        displayTitle = cleanTitle,
                        audioFile = af,
                        jsonFile = jf,
                        saFrequency = sa,
                        durationMs = dur,
                        pointCount = count
                    )
                )
            } catch (e: Exception) {
                Log.w("SessionLibrary", "Skipping malformed session ${jf.name}", e)
            }
        }
        return result
    }

    fun loadGhostTrace(session: SavedSessionInfo): GhostTraceData? {
        return try {
            val root = JSONObject(session.jsonFile.readText())
            val sa = root.optDouble("saFrequency", session.saFrequency.toDouble()).toFloat()
            val pts = root.optJSONArray("points") ?: return null
            val n = pts.length()
            if (n == 0) return null

            val offsets = LongArray(n)
            val freqs = FloatArray(n)
            for (i in 0 until n) {
                val obj = pts.getJSONObject(i)
                offsets[i] = obj.optLong("t", 0L)
                freqs[i] = obj.optDouble("f", 0.0).toFloat()
            }
            val totalDur = maxOf(session.durationMs, offsets.lastOrNull() ?: 1000L).coerceAtLeast(1000L)
            GhostTraceData(
                title = session.displayTitle,
                saFrequency = sa,
                durationMs = totalDur,
                offsetMs = offsets,
                freqsHz = freqs
            )
        } catch (e: Exception) {
            Log.e("SessionLibrary", "Failed to load ghost trace", e)
            null
        }
    }

    fun playSessionAudio(session: SavedSessionInfo, onComplete: () -> Unit = {}) {
        stopSessionAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(session.audioFile.absolutePath)
                setOnCompletionListener { onComplete() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("SessionLibrary", "Error playing session audio", e)
            onComplete()
        }
    }

    fun stopSessionAudio() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun deleteSession(session: SavedSessionInfo) {
        stopSessionAudio()
        session.audioFile.delete()
        session.jsonFile.delete()
    }
}
