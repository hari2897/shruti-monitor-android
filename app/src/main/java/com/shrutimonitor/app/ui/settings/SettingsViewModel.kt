package com.shrutimonitor.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrutimonitor.app.data.AppTheme
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State representing all custom app settings.
 */
data class SettingsUiState(
    val saFrequency: Float = SettingsRepository.DEFAULT_SA_FREQUENCY,
    val saNoteName: String = SettingsRepository.DEFAULT_SA_NOTE_NAME,
    val saOctave: Int = SettingsRepository.DEFAULT_SA_OCTAVE,
    val nomenclature: Nomenclature = SettingsRepository.DEFAULT_NOMENCLATURE,
    val confidenceThreshold: Float = SettingsRepository.DEFAULT_CONFIDENCE_THRESHOLD,
    val micSensitivity: Float = SettingsRepository.DEFAULT_MIC_SENSITIVITY,
    val theme: AppTheme = SettingsRepository.DEFAULT_THEME,
    val hapticEnabled: Boolean = SettingsRepository.DEFAULT_HAPTIC_ENABLED,
    val inTuneTolerance: Float = SettingsRepository.DEFAULT_IN_TUNE_TOLERANCE
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Collect all individual settings flows from DataStore
        viewModelScope.launch {
            settingsRepository.saFrequency.collect { freq ->
                _uiState.update { it.copy(saFrequency = freq) }
            }
        }
        viewModelScope.launch {
            settingsRepository.saNoteName.collect { name ->
                _uiState.update { it.copy(saNoteName = name) }
            }
        }
        viewModelScope.launch {
            settingsRepository.saOctave.collect { oct ->
                _uiState.update { it.copy(saOctave = oct) }
            }
        }
        viewModelScope.launch {
            settingsRepository.nomenclature.collect { nom ->
                _uiState.update { it.copy(nomenclature = nom) }
            }
        }
        viewModelScope.launch {
            settingsRepository.confidenceThreshold.collect { threshold ->
                _uiState.update { it.copy(confidenceThreshold = threshold) }
            }
        }
        viewModelScope.launch {
            settingsRepository.micSensitivity.collect { sens ->
                _uiState.update { it.copy(micSensitivity = sens) }
            }
        }
        viewModelScope.launch {
            settingsRepository.theme.collect { theme ->
                _uiState.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hapticEnabled.collect { haptic ->
                _uiState.update { it.copy(hapticEnabled = haptic) }
            }
        }
        viewModelScope.launch {
            settingsRepository.inTuneTolerance.collect { tol ->
                _uiState.update { it.copy(inTuneTolerance = tol) }
            }
        }
    }

    /**
     * Updates the tonic Sa configuration.
     */
    fun updateSaTonic(frequency: Float, noteName: String, octave: Int) {
        viewModelScope.launch {
            settingsRepository.updateSaTonic(frequency, noteName, octave)
        }
    }

    fun updateNomenclature(nom: Nomenclature) {
        viewModelScope.launch {
            settingsRepository.updateNomenclature(nom)
        }
    }

    fun updateConfidenceThreshold(threshold: Float) {
        viewModelScope.launch {
            settingsRepository.updateConfidenceThreshold(threshold)
        }
    }

    fun updateMicSensitivity(sens: Float) {
        viewModelScope.launch {
            settingsRepository.updateMicSensitivity(sens)
        }
    }

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun updateHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHapticEnabled(enabled)
        }
    }

    fun updateInTuneTolerance(cents: Float) {
        viewModelScope.launch {
            settingsRepository.updateInTuneTolerance(cents)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
    }
}
