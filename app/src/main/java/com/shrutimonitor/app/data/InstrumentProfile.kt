package com.shrutimonitor.app.data

/**
 * Acoustic instrument profiles that dynamically tune the DSP pitch detector,
 * confidence hysteresis, frequency bounds, and harmonic overtone weighting.
 */
enum class InstrumentCategory(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val minFreqHz: Float,
    val maxFreqHz: Float,
    val mpmClarityThreshold: Float,
    val rmsNoiseGate: Float,
    val maxSingleFrameJumpCents: Float
) {
    VOCAL(
        id = "vocal",
        displayName = "Vocal Riyaz / Voice",
        shortLabel = "Vocal",
        minFreqHz = 50.0f,
        maxFreqHz = 1100.0f,
        mpmClarityThreshold = 0.85f,
        rmsNoiseGate = 0.0085f,
        maxSingleFrameJumpCents = 600f
    ),
    BAMBOO_FLUTE(
        id = "bamboo_flute",
        displayName = "Bamboo Flute (Dynamic Register)",
        shortLabel = "Flute",
        minFreqHz = 50.0f,
        maxFreqHz = 2800.0f,
        mpmClarityThreshold = 0.90f, // Dynamically refined per Sa register via FluteRegister
        rmsNoiseGate = 0.010f,
        maxSingleFrameJumpCents = 750f
    ),
    VEENA(
        id = "veena",
        displayName = "Saraswati Veena",
        shortLabel = "Veena",
        minFreqHz = 70.0f,
        maxFreqHz = 1400.0f,
        mpmClarityThreshold = 0.82f,
        rmsNoiseGate = 0.0085f,
        maxSingleFrameJumpCents = 550f
    ),
    GUITAR(
        id = "guitar",
        displayName = "Acoustic Guitar",
        shortLabel = "Guitar",
        minFreqHz = 75.0f,
        maxFreqHz = 1350.0f,
        mpmClarityThreshold = 0.86f,
        rmsNoiseGate = 0.0085f,
        maxSingleFrameJumpCents = 600f
    );

    companion object {
        fun fromId(id: String?): InstrumentCategory {
            return entries.firstOrNull { it.id == id } ?: VOCAL
        }
    }
}

/**
 * Continuous acoustic register classification derived dynamically from any active Sa frequency.
 * Automatically adapts MPM clarity thresholds and breath-noise gates for any flute scale
 * (from Sub-Bass & Deep Bass through Concert Medium to Soprano/Piccolo).
 */
enum class FluteRegister(
    val label: String,
    val clarityThreshold: Float,
    val rmsNoiseGate: Float,
    val minFreqHz: Float,
    val maxFreqHz: Float,
    val description: String
) {
    DEEP_BASS(
        label = "Deep Bass Register",
        clarityThreshold = 0.87f,
        rmsNoiseGate = 0.010f,
        minFreqHz = 55.0f,
        maxFreqHz = 1400.0f,
        description = "Tuned for long air columns (<155 Hz Sa): captures deep fundamental resonance with relaxed transient gating."
    ),
    BASS(
        label = "Bass Register",
        clarityThreshold = 0.89f,
        rmsNoiseGate = 0.010f,
        minFreqHz = 55.0f,
        maxFreqHz = 1800.0f,
        description = "Tuned for Bass scales (155–215 Hz Sa): balances warm low-end tracking with overblown Tara protection."
    ),
    MEDIUM(
        label = "Concert / Medium Register",
        clarityThreshold = 0.91f,
        rmsNoiseGate = 0.010f,
        minFreqHz = 55.0f,
        maxFreqHz = 2400.0f,
        description = "Tuned for Concert scales (215–340 Hz Sa): rejects embouchure breath chuffing and 2nd-harmonic octave jumps."
    ),
    SOPRANO(
        label = "Soprano / High Register",
        clarityThreshold = 0.93f,
        rmsNoiseGate = 0.010f,
        minFreqHz = 55.0f,
        maxFreqHz = 2800.0f,
        description = "Tuned for Soprano & Piccolo scales (>340 Hz Sa): strict harmonic suppression against strong upper overtones."
    );

    companion object {
        fun forFrequency(saFreqHz: Float): FluteRegister {
            return when {
                saFreqHz < 155f -> DEEP_BASS
                saFreqHz < 215f -> BASS
                saFreqHz < 340f -> MEDIUM
                else -> SOPRANO
            }
        }
    }
}
