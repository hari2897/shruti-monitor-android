package com.shrutimonitor.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Nomenclature system used for displaying swara names.
 */
enum class Nomenclature {
    HINDUSTANI,
    CARNATIC
}

/**
 * App theme options.
 */
enum class AppTheme {
    DARK,
    AMOLED,
    AUTO
}

// Top-level DataStore extension — single instance per process
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shruti_monitor_settings"
)

/**
 * Repository for persisting and reading user settings via Jetpack DataStore.
 *
 * Each setting is exposed as a [Flow] that emits the current value and any
 * subsequent updates. Writes are performed via suspend functions.
 *
 * @param context Application context (used to access the DataStore).
 */
class SettingsRepository(private val context: Context) {

    // ── Preference Keys ───────────────────────────────────────────────

    private object Keys {
        val SA_FREQUENCY = floatPreferencesKey("sa_frequency")
        val SA_NOTE_NAME = stringPreferencesKey("sa_note_name")
        val SA_OCTAVE = intPreferencesKey("sa_octave")
        val NOMENCLATURE = stringPreferencesKey("nomenclature")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val AUTO_FOLLOW = booleanPreferencesKey("auto_follow")
        val MIC_SENSITIVITY = floatPreferencesKey("mic_sensitivity")
        val THEME = stringPreferencesKey("theme")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val MONITOR_CONTROLS_VISIBLE = booleanPreferencesKey("monitor_controls_visible")
        val IN_TUNE_TOLERANCE = floatPreferencesKey("in_tune_tolerance")
        val TANPURA_FINE_TUNING = floatPreferencesKey("tanpura_fine_tuning")
        val TANPURA_432HZ = booleanPreferencesKey("tanpura_432hz")
        val TUNING_PRESET = stringPreferencesKey("tuning_preset")
        val USER_EXPLICIT_TUNING = booleanPreferencesKey("user_explicit_tuning")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        val IGNORED_UPDATE_VERSION = stringPreferencesKey("ignored_update_version")
    }

    // ── Default Values ────────────────────────────────────────────────

    companion object Defaults {
        const val DEFAULT_SA_FREQUENCY: Float = 261.63f    // C4 in Hz
        const val DEFAULT_SA_NOTE_NAME: String = "C"
        const val DEFAULT_SA_OCTAVE: Int = 4
        val DEFAULT_NOMENCLATURE: Nomenclature = Nomenclature.HINDUSTANI
        val DEFAULT_TUNING_PRESET: TuningPreset = TuningPreset.HARMONIC_5LIMIT
        const val DEFAULT_USER_EXPLICIT_TUNING: Boolean = false
        const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.5f
        const val DEFAULT_AUTO_FOLLOW: Boolean = true
        const val DEFAULT_MIC_SENSITIVITY: Float = 0.5f
        val DEFAULT_THEME: AppTheme = AppTheme.DARK
        const val DEFAULT_HAPTIC_ENABLED: Boolean = true
        const val DEFAULT_MONITOR_CONTROLS_VISIBLE: Boolean = true
        const val DEFAULT_IN_TUNE_TOLERANCE: Float = 10.0f
        const val DEFAULT_TANPURA_FINE_TUNING: Float = 0.0f
        const val DEFAULT_TANPURA_432HZ: Boolean = false
        const val DEFAULT_LAST_UPDATE_CHECK_TIME: Long = 0L
        const val DEFAULT_IGNORED_UPDATE_VERSION: String = ""
    }

    // ── Flows (read) ──────────────────────────────────────────────────

    /**
     * The frequency of Sa in Hz. Default: 261.63 Hz (C4).
     */
    val saFrequency: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.SA_FREQUENCY] ?: DEFAULT_SA_FREQUENCY
    }

    /**
     * The Western note name that Sa is mapped to (e.g., "C", "C#", "D").
     */
    val saNoteName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SA_NOTE_NAME] ?: DEFAULT_SA_NOTE_NAME
    }

    /**
     * The octave number for Sa. Default: 4 (middle octave).
     */
    val saOctave: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SA_OCTAVE] ?: DEFAULT_SA_OCTAVE
    }

    /**
     * The nomenclature system: HINDUSTANI or CARNATIC.
     */
    val nomenclature: Flow<Nomenclature> = context.dataStore.data.map { prefs ->
        val value = prefs[Keys.NOMENCLATURE] ?: DEFAULT_NOMENCLATURE.name
        try {
            Nomenclature.valueOf(value)
        } catch (e: IllegalArgumentException) {
            DEFAULT_NOMENCLATURE
        }
    }

    /**
     * Minimum pitch detection confidence (0.0–1.0) to accept a detected note.
     */
    val confidenceThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONFIDENCE_THRESHOLD] ?: DEFAULT_CONFIDENCE_THRESHOLD
    }

    /**
     * Whether the display auto-follows the detected pitch.
     */
    val autoFollow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_FOLLOW] ?: DEFAULT_AUTO_FOLLOW
    }

    /**
     * Microphone input sensitivity (0.0–1.0). Higher = more sensitive.
     */
    val micSensitivity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.MIC_SENSITIVITY] ?: DEFAULT_MIC_SENSITIVITY
    }

    /**
     * App theme: DARK, AMOLED, or AUTO.
     */
    val theme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        val value = prefs[Keys.THEME] ?: DEFAULT_THEME.name
        try {
            AppTheme.valueOf(value)
        } catch (e: IllegalArgumentException) {
            DEFAULT_THEME
        }
    }

    /**
     * Whether haptic feedback is enabled for touch interactions.
     */
    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAPTIC_ENABLED] ?: DEFAULT_HAPTIC_ENABLED
    }

    /**
     * Whether the floating controls and bottom drawer on the Monitor screen are visible.
     */
    val monitorControlsVisible: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MONITOR_CONTROLS_VISIBLE] ?: DEFAULT_MONITOR_CONTROLS_VISIBLE
    }

    /**
     * The in-tune tolerance in cents (±). Pitches within this range of a JI swara are 'in tune'.
     */
    val inTuneTolerance: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.IN_TUNE_TOLERANCE] ?: DEFAULT_IN_TUNE_TOLERANCE
    }

    /**
     * Fine tuning offset in cents for the Tanpura.
     */
    val tanpuraFineTuning: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.TANPURA_FINE_TUNING] ?: DEFAULT_TANPURA_FINE_TUNING
    }

    /**
     * Whether the A4=432Hz mode is enabled for the Tanpura.
     */
    val tanpura432Hz: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.TANPURA_432HZ] ?: DEFAULT_TANPURA_432HZ
    }

    /**
     * Active tuning system / Shruti preset (Harmonic 5-Limit JI, Pythagorean 3-Limit JI, or 12-EDO).
     */
    val tuningPreset: Flow<TuningPreset> = context.dataStore.data.map { prefs ->
        val value = prefs[Keys.TUNING_PRESET]
        TuningPreset.fromId(value)
    }

    /**
     * Whether the user has explicitly selected a tuning preset (prevents automatic raga overrides).
     */
    val userExplicitTuning: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.USER_EXPLICIT_TUNING] ?: DEFAULT_USER_EXPLICIT_TUNING
    }

    /**
     * Timestamp in milliseconds of the last successful update check.
     */
    val lastUpdateCheckTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK_TIME] ?: DEFAULT_LAST_UPDATE_CHECK_TIME
    }

    /**
     * Version name of an update the user opted to ignore/skip.
     */
    val ignoredUpdateVersion: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.IGNORED_UPDATE_VERSION] ?: DEFAULT_IGNORED_UPDATE_VERSION
    }

    // ── Update Functions (write) ──────────────────────────────────────

    /**
     * Updates the Sa frequency in Hz.
     * @param frequency Must be positive (typically 100–500 Hz for vocal range).
     */
    suspend fun updateSaFrequency(frequency: Float) {
        require(frequency > 0f) { "Sa frequency must be positive, got $frequency" }
        context.dataStore.edit { prefs ->
            prefs[Keys.SA_FREQUENCY] = frequency
        }
    }

    /**
     * Updates the Sa note name.
     * @param noteName Western note name (e.g., "C", "C#", "D", "Eb").
     */
    suspend fun updateSaNoteName(noteName: String) {
        require(noteName.isNotBlank()) { "Note name must not be blank" }
        context.dataStore.edit { prefs ->
            prefs[Keys.SA_NOTE_NAME] = noteName
        }
    }

    /**
     * Updates the Sa octave.
     * @param octave Typically 1–8.
     */
    suspend fun updateSaOctave(octave: Int) {
        require(octave in 0..9) { "Octave must be 0-9, got $octave" }
        context.dataStore.edit { prefs ->
            prefs[Keys.SA_OCTAVE] = octave
        }
    }

    /**
     * Updates the nomenclature system.
     */
    suspend fun updateNomenclature(nomenclature: Nomenclature) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOMENCLATURE] = nomenclature.name
        }
    }

    /**
     * Updates the pitch detection confidence threshold.
     * @param threshold Must be in range [0.0, 1.0].
     */
    suspend fun updateConfidenceThreshold(threshold: Float) {
        require(threshold in 0f..1f) { "Confidence threshold must be 0.0–1.0, got $threshold" }
        context.dataStore.edit { prefs ->
            prefs[Keys.CONFIDENCE_THRESHOLD] = threshold
        }
    }

    /**
     * Toggles or sets the auto-follow mode.
     */
    suspend fun updateAutoFollow(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_FOLLOW] = enabled
        }
    }

    /**
     * Updates the microphone sensitivity.
     * @param sensitivity Must be in range [0.0, 1.0].
     */
    suspend fun updateMicSensitivity(sensitivity: Float) {
        require(sensitivity in 0f..1f) { "Mic sensitivity must be 0.0–1.0, got $sensitivity" }
        context.dataStore.edit { prefs ->
            prefs[Keys.MIC_SENSITIVITY] = sensitivity
        }
    }

    /**
     * Updates the app theme.
     */
    suspend fun updateTheme(theme: AppTheme) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = theme.name
        }
    }

    /**
     * Updates the haptic feedback toggle.
     */
    suspend fun updateHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_ENABLED] = enabled
        }
    }

    /**
     * Updates the monitor controls visibility toggle.
     */
    suspend fun updateMonitorControlsVisible(visible: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MONITOR_CONTROLS_VISIBLE] = visible
        }
    }

    /**
     * Updates the in-tune tolerance in cents.
     * @param cents Must be in range [5.0, 25.0].
     */
    suspend fun updateInTuneTolerance(cents: Float) {
        require(cents in 5f..25f) { "In-tune tolerance must be 5–25 cents, got $cents" }
        context.dataStore.edit { prefs ->
            prefs[Keys.IN_TUNE_TOLERANCE] = cents
        }
    }

    /**
     * Convenience: Updates Sa frequency, note name, and octave together
     * (e.g., when the user selects a new tonic from the picker).
     */
    suspend fun updateSaTonic(frequency: Float, noteName: String, octave: Int) {
        require(frequency > 0f) { "Sa frequency must be positive" }
        require(noteName.isNotBlank()) { "Note name must not be blank" }
        require(octave in 0..9) { "Octave must be 0-9" }
        context.dataStore.edit { prefs ->
            prefs[Keys.SA_FREQUENCY] = frequency
            prefs[Keys.SA_NOTE_NAME] = noteName
            prefs[Keys.SA_OCTAVE] = octave
        }
    }

    /**
     * Updates the Tanpura fine tuning offset in cents.
     */
    suspend fun updateTanpuraFineTuning(cents: Float) {
        require(cents in -50f..50f) { "Fine tuning must be in range [-50, 50], got $cents" }
        context.dataStore.edit { prefs ->
            prefs[Keys.TANPURA_FINE_TUNING] = cents
        }
    }

    /**
     * Updates the Tanpura A4=432Hz mode toggle.
     */
    suspend fun updateTanpura432Hz(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TANPURA_432HZ] = enabled
        }
    }

    /**
     * Updates the tuning preset.
     * @param preset The chosen [TuningPreset].
     * @param isUserExplicit If true, marks that the user explicitly chose this preset,
     *                       preventing automatic changes when selecting ragas.
     */
    suspend fun updateTuningPreset(preset: TuningPreset, isUserExplicit: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TUNING_PRESET] = preset.id
            if (isUserExplicit) {
                prefs[Keys.USER_EXPLICIT_TUNING] = true
            }
        }
    }

    /**
     * Updates tuning preset automatically for a raga system (e.g. Carnatic -> Pythagorean 3-Limit),
     * only if the user has not explicitly locked in a preferred tuning.
     */
    suspend fun setTuningPresetIfNotOverridden(preset: TuningPreset) {
        context.dataStore.edit { prefs ->
            val explicitlySet = prefs[Keys.USER_EXPLICIT_TUNING] ?: DEFAULT_USER_EXPLICIT_TUNING
            if (!explicitlySet) {
                prefs[Keys.TUNING_PRESET] = preset.id
            }
        }
    }

    /**
     * Updates the timestamp of the last successful update check.
     */
    suspend fun updateLastUpdateCheckTime(timestampMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK_TIME] = timestampMs
        }
    }

    /**
     * Updates the version string that the user chose to ignore.
     */
    suspend fun updateIgnoredUpdateVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IGNORED_UPDATE_VERSION] = version
        }
    }

    /**
     * Resets all settings to their default values.
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            prefs[Keys.SA_FREQUENCY] = DEFAULT_SA_FREQUENCY
            prefs[Keys.SA_NOTE_NAME] = DEFAULT_SA_NOTE_NAME
            prefs[Keys.SA_OCTAVE] = DEFAULT_SA_OCTAVE
            prefs[Keys.NOMENCLATURE] = DEFAULT_NOMENCLATURE.name
            prefs[Keys.TUNING_PRESET] = DEFAULT_TUNING_PRESET.id
            prefs[Keys.USER_EXPLICIT_TUNING] = DEFAULT_USER_EXPLICIT_TUNING
            prefs[Keys.CONFIDENCE_THRESHOLD] = DEFAULT_CONFIDENCE_THRESHOLD
            prefs[Keys.AUTO_FOLLOW] = DEFAULT_AUTO_FOLLOW
            prefs[Keys.MIC_SENSITIVITY] = DEFAULT_MIC_SENSITIVITY
            prefs[Keys.THEME] = DEFAULT_THEME.name
            prefs[Keys.HAPTIC_ENABLED] = DEFAULT_HAPTIC_ENABLED
            prefs[Keys.MONITOR_CONTROLS_VISIBLE] = DEFAULT_MONITOR_CONTROLS_VISIBLE
            prefs[Keys.IN_TUNE_TOLERANCE] = DEFAULT_IN_TUNE_TOLERANCE
            prefs[Keys.TANPURA_FINE_TUNING] = DEFAULT_TANPURA_FINE_TUNING
            prefs[Keys.TANPURA_432HZ] = DEFAULT_TANPURA_432HZ
            prefs[Keys.LAST_UPDATE_CHECK_TIME] = DEFAULT_LAST_UPDATE_CHECK_TIME
            prefs[Keys.IGNORED_UPDATE_VERSION] = DEFAULT_IGNORED_UPDATE_VERSION
        }
    }
}
