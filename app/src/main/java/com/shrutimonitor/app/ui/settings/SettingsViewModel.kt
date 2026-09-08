package com.shrutimonitor.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrutimonitor.app.BuildConfig
import com.shrutimonitor.app.data.AppTheme
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.SettingsRepository
import com.shrutimonitor.app.data.TuningPreset
import com.shrutimonitor.app.data.update.AppUpdateManager
import com.shrutimonitor.app.data.update.DownloadStatus
import com.shrutimonitor.app.data.update.UpdateCheckStatus
import com.shrutimonitor.app.data.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI State representing all custom app settings.
 */
data class SettingsUiState(
    val saFrequency: Float = SettingsRepository.DEFAULT_SA_FREQUENCY,
    val saNoteName: String = SettingsRepository.DEFAULT_SA_NOTE_NAME,
    val saOctave: Int = SettingsRepository.DEFAULT_SA_OCTAVE,
    val nomenclature: Nomenclature = SettingsRepository.DEFAULT_NOMENCLATURE,
    val tuningPreset: TuningPreset = SettingsRepository.DEFAULT_TUNING_PRESET,
    val confidenceThreshold: Float = SettingsRepository.DEFAULT_CONFIDENCE_THRESHOLD,
    val micSensitivity: Float = SettingsRepository.DEFAULT_MIC_SENSITIVITY,
    val theme: AppTheme = SettingsRepository.DEFAULT_THEME,
    val hapticEnabled: Boolean = SettingsRepository.DEFAULT_HAPTIC_ENABLED,
    val inTuneTolerance: Float = SettingsRepository.DEFAULT_IN_TUNE_TOLERANCE
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    val appUpdateManager = AppUpdateManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _updateCheckStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    val updateCheckStatus: StateFlow<UpdateCheckStatus> = _updateCheckStatus.asStateFlow()

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.NotStarted)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    val currentVersionName: String by lazy {
        try {
            BuildConfig.VERSION_NAME
        } catch (e: Throwable) {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            pInfo.versionName ?: "1.1.1"
        }
    }

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
        viewModelScope.launch {
            settingsRepository.tuningPreset.collect { preset ->
                _uiState.update { it.copy(tuningPreset = preset) }
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
            val defaultPreset = if (nom == Nomenclature.CARNATIC) {
                TuningPreset.PYTHAGOREAN_3LIMIT
            } else {
                TuningPreset.HARMONIC_5LIMIT
            }
            settingsRepository.setTuningPresetIfNotOverridden(defaultPreset)
        }
    }

    fun updateTuningPreset(preset: TuningPreset) {
        viewModelScope.launch {
            settingsRepository.updateTuningPreset(preset, isUserExplicit = true)
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

    // ── In-App Updates ────────────────────────────────────────────────

    fun checkForUpdates() {
        _updateCheckStatus.value = UpdateCheckStatus.Checking
        viewModelScope.launch {
            val status = appUpdateManager.checkForUpdate(currentVersionName)
            _updateCheckStatus.value = status
            if (status is UpdateCheckStatus.UpdateAvailable) {
                _showUpdateDialog.value = true
            }
            settingsRepository.updateLastUpdateCheckTime(System.currentTimeMillis())
        }
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        viewModelScope.launch {
            appUpdateManager.downloadApk(updateInfo).collect { status ->
                _downloadStatus.value = status
                if (status is DownloadStatus.ReadyToInstall) {
                    installDownloadedApk(status.apkFile)
                }
            }
        }
    }

    fun installDownloadedApk(file: File) {
        if (!appUpdateManager.canRequestPackageInstalls()) {
            appUpdateManager.requestInstallPermission()
        } else {
            appUpdateManager.installApk(file)
        }
    }

    fun openWebUrl(url: String) {
        appUpdateManager.openWebUrl(url)
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
        _downloadStatus.value = DownloadStatus.NotStarted
    }

    fun skipUpdate(version: String) {
        viewModelScope.launch {
            settingsRepository.updateIgnoredUpdateVersion(version)
            dismissUpdateDialog()
        }
    }
}
