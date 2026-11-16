package com.shrutimonitor.app.audio

import com.shrutimonitor.app.data.Swara

/**
 * Result of a raw pitch detection calculation.
 *
 * @property frequency Detected fundamental frequency in Hz (0.0f if unvoiced).
 * @property confidence Match clarity metric between 0.0f (noise) and 1.0f (pure tone).
 * @property isVoiced Whether the frame contains periodic pitched voice/instrument data.
 * @property rmsAmplitude Root-Mean-Square signal amplitude in [0.0f, 1.0f].
 * @property harmonicBrightness Ratio of overtone energy (H2+H3) to fundamental (H1) in [0.0f, 1.0f].
 */
data class PitchResult(
    val frequency: Float,
    val confidence: Float,
    val isVoiced: Boolean,
    val rmsAmplitude: Float = 0.0f,
    val harmonicBrightness: Float = 0.0f
)

/**
 * The register/octave division relative to the middle (Madhya) saptak.
 */
enum class Saptak(val octaveOffset: Int, val label: String) {
    /** Double lower octave (ati-mandra saptak) */
    ATI_MANDRA(-2, "Ati-Mandra"),
    /** Lower octave (mandra saptak) */
    MANDRA(-1, "Mandra"),
    /** Middle octave (madhya saptak) */
    MADHYA(0, "Madhya"),
    /** Upper octave (tara saptak) */
    TARA(1, "Tara"),
    /** Double upper octave (ati-tara saptak) */
    ATI_TARA(2, "Ati-Tara")
}

/**
 * Real-time classification of Indian classical microtonal movement (Gamaka / Meend).
 */
enum class GamakaType(val label: String) {
    STEADY("Steady Swara"),
    KAMPITA("Kampita (Oscillation)"),
    JAARU("Jaaru / Meend (Glide)"),
    SPURITA("Spurita / Nok (Accent)")
}

/**
 * Telemetry snapshot of the current microtonal ornament.
 */
data class GamakaMetrics(
    val type: GamakaType = GamakaType.STEADY,
    val oscillationRateHz: Float = 0.0f,
    val excursionCents: Float = 0.0f,
    val stabilityScore: Float = 100.0f
)

/**
 * The mapping of a detected pitch frequency to its nearest Just Intonation swara.
 */
data class SwaraResult(
    val swara: Swara,
    val swaraIndex: Int,
    val centDeviation: Float,
    val frequency: Float,
    val idealFrequency: Float,
    val saptak: Saptak,
    val timestamp: Long,
    val shrutiName: String = ""
)

/**
 * A data point representing the history of tracked pitch over time.
 */
data class PitchPoint(
    val timestamp: Long,
    val frequency: Float,
    val centFromSa: Float,
    val confidence: Float
)