package com.shrutimonitor.app.audio

import com.shrutimonitor.app.data.Swara

/**
 * Result of a raw pitch detection calculation.
 *
 * @property frequency Detected fundamental frequency in Hz (0.0f if unvoiced).
 * @property confidence Match clarity metric between 0.0f (noise) and 1.0f (pure tone).
 * @property isVoiced Whether the frame contains periodic pitched voice data.
 */
data class PitchResult(
    val frequency: Float,
    val confidence: Float,
    val isVoiced: Boolean
)

/**
 * The register/octave division relative to the middle (Madhya) saptak.
 */
enum class Saptak {
    /** Lower octave (mandra saptak) */
    MANDRA,
    /** Middle octave (madhya saptak) */
    MADHYA,
    /** Upper octave (tara saptak) */
    TARA
}

/**
 * The mapping of a detected pitch frequency to its nearest Just Intonation swara.
 *
 * @property swara The corresponding [Swara] from the raga data layer.
 * @property swaraIndex Chromatic index from 0 to 11.
 * @property centDeviation Difference in cents between the detected pitch and the ideal JI frequency.
 * @property frequency The actual detected pitch frequency in Hz.
 * @property idealFrequency The ideal Just Intonation frequency of the swara in Hz.
 * @property saptak The detected octave register.
 * @property timestamp Epoch timestamp in milliseconds.
 */
data class SwaraResult(
    val swara: Swara,
    val swaraIndex: Int,
    val centDeviation: Float,
    val frequency: Float,
    val idealFrequency: Float,
    val saptak: Saptak,
    val timestamp: Long
)

/**
 * A data point representing the history of tracked pitch over time.
 *
 * @property timestamp Offset timestamp in milliseconds from the session start.
 * @property frequency Detected pitch frequency in Hz.
 * @property centFromSa Cent offset of this pitch from the tonic (Sa). Used for graph plotting.
 * @property confidence Confidence level of the detection.
 */
data class PitchPoint(
    val timestamp: Long,
    val frequency: Float,
    val centFromSa: Float,
    val confidence: Float
)