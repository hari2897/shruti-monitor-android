package com.shrutimonitor.app.ui.monitor

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrutimonitor.app.audio.AudioCaptureManager
import com.shrutimonitor.app.audio.McLeodPitchDetector
import com.shrutimonitor.app.audio.PitchPoint
import com.shrutimonitor.app.audio.SessionRecorder
import com.shrutimonitor.app.audio.SwaraMapper
import com.shrutimonitor.app.audio.SwaraResult
import com.shrutimonitor.app.data.ActiveRagaManager
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Raga
import com.shrutimonitor.app.data.SettingsRepository
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.data.TuningPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.sin

import com.shrutimonitor.app.audio.PitchRingBuffer
import com.shrutimonitor.app.audio.DisplayPitchFilter
import com.shrutimonitor.app.audio.PitchPipelineTelemetry

/**
 * UI State for the pitch monitor screen.
 */
data class MonitorUiState(
    val currentSwara: SwaraInfo? = null,
    val centDeviation: Float = 0.0f,
    val frequency: Float = 0.0f,
    val isMicActive: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val isLiveMode: Boolean = true,
    val autoFollow: Boolean = true,
    val activeRagaName: String? = null,
    val activeRagaSwaras: Set<Int>? = null,
    val pitchHistory: PitchRingBuffer = PitchRingBuffer(1200),
    val historyVersion: Long = 0L,
    val confidenceThreshold: Float = 0.5f,
    val nomenclature: Nomenclature = Nomenclature.HINDUSTANI,
    val tuningPreset: TuningPreset = SettingsRepository.DEFAULT_TUNING_PRESET,
    val saFrequency: Float = SettingsRepository.DEFAULT_SA_FREQUENCY,
    val saNoteName: String = SettingsRepository.DEFAULT_SA_NOTE_NAME,
    val saOctave: Int = SettingsRepository.DEFAULT_SA_OCTAVE,
    val showControls: Boolean = true,
    val inTuneTolerance: Float = SettingsRepository.DEFAULT_IN_TUNE_TOLERANCE
)

/**
 * Serialized friendly swara info for UI representation.
 */
data class SwaraInfo(
    val swaraIndex: Int,
    val hindustaniName: String,
    val hindustaniAbbr: String,
    val carnaticName: String,
    val carnaticAbbr: String,
    val saptak: String
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val audioCaptureManager = AudioCaptureManager(application)
    private val pitchDetector = McLeodPitchDetector()
    private val swaraMapper = SwaraMapper()
    private val sessionRecorder = SessionRecorder(application)

    private val maxHistoryPoints = 1200 // ~1 minute at 20fps or ~30s at 40fps
    private val pitchHistory = PitchRingBuffer(maxHistoryPoints)
    private val displayPitchFilter = DisplayPitchFilter()

    private val _uiState = MutableStateFlow(MonitorUiState(pitchHistory = pitchHistory))
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    // Tonic reference settings
    private var saFrequency: Float = SettingsRepository.DEFAULT_SA_FREQUENCY
    private var confidenceThreshold: Float = SettingsRepository.DEFAULT_CONFIDENCE_THRESHOLD
    @Volatile private var currentTuningPreset: TuningPreset = SettingsRepository.DEFAULT_TUNING_PRESET

    // Coroutine Jobs
    private var audioCollectionJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var simulationJob: Job? = null

    init {
        // Collect preferences updates
        viewModelScope.launch {
            settingsRepository.saFrequency.collect { freq ->
                saFrequency = freq
                sessionRecorder.saFrequency = freq
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
            settingsRepository.confidenceThreshold.collect { threshold ->
                confidenceThreshold = threshold
                displayPitchFilter.confidenceThreshold = threshold
                _uiState.update { it.copy(confidenceThreshold = threshold) }
            }
        }
        viewModelScope.launch {
            settingsRepository.nomenclature.collect { nom ->
                _uiState.update { it.copy(nomenclature = nom) }
            }
        }
        viewModelScope.launch {
            settingsRepository.tuningPreset.collect { preset ->
                currentTuningPreset = preset
                _uiState.update { it.copy(tuningPreset = preset) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoFollow.collect { autoFollow ->
                _uiState.update { it.copy(autoFollow = autoFollow) }
            }
        }
        viewModelScope.launch {
            settingsRepository.monitorControlsVisible.collect { visible ->
                _uiState.update { it.copy(showControls = visible) }
            }
        }
        viewModelScope.launch {
            settingsRepository.inTuneTolerance.collect { tolerance ->
                _uiState.update { it.copy(inTuneTolerance = tolerance) }
            }
        }

        // Collect active raga updates from global manager
        viewModelScope.launch {
            ActiveRagaManager.activeRaga.collect { raga ->
                _uiState.update { state ->
                    state.copy(
                        activeRagaName = raga?.name,
                        activeRagaSwaras = raga?.activeSwaras?.map { it.index }?.toSet()
                    )
                }

                // Smart tuning preset selection if user hasn't explicitly locked it
                if (raga != null) {
                    if (!raga.isHindustani) {
                        settingsRepository.setTuningPresetIfNotOverridden(TuningPreset.PYTHAGOREAN_3LIMIT)
                    } else {
                        settingsRepository.setTuningPresetIfNotOverridden(TuningPreset.HARMONIC_5LIMIT)
                    }
                }
            }
        }

        // Automatically start listening to microphone on launch
        viewModelScope.launch {
            toggleMic()
        }
    }

    /**
     * Toggles microphone monitoring.
     */
    fun toggleMic() {
        val nextActive = !_uiState.value.isMicActive
        if (nextActive) {
            // Stop simulation if running
            stopPitchSimulation()
            
            val started = audioCaptureManager.start()
            if (started) {
                _uiState.update { it.copy(isMicActive = true) }
                startAudioProcessing()
            }
        } else {
            audioCaptureManager.stop()
            stopAudioProcessing()
            if (_uiState.value.isRecording) {
                stopRecording()
            }
            pitchHistory.clear()
            _uiState.update {
                it.copy(
                    isMicActive = false,
                    currentSwara = null,
                    frequency = 0f,
                    centDeviation = 0f,
                    pitchHistory = pitchHistory,
                    historyVersion = it.historyVersion + 1
                )
            }
        }
    }

    /**
     * Starts listening to audio frames from AudioCaptureManager.
     */
    private fun startAudioProcessing() {
        audioCollectionJob?.cancel()
        
        audioCollectionJob = viewModelScope.launch {
            audioCaptureManager.audioFrames.collectLatest { capturedFrame ->
                val pitchResult = withContext(Dispatchers.Default) {
                    pitchDetector.detectPitch(capturedFrame.samples)
                }

                // Preserve the original detector capture timestamp on every pitch point
                val detectorTimestampMs = capturedFrame.timestampMs

                val filterResult = displayPitchFilter.filterDetailed(pitchResult.frequency, pitchResult.confidence)
                val filteredFreq = filterResult.frequency

                // Pipeline telemetry instrumentation (Step 0)
                PitchPipelineTelemetry.recordCallback(
                    timestampMs = detectorTimestampMs,
                    rawFreqHz = pitchResult.frequency,
                    confidence = pitchResult.confidence,
                    audioFrame = capturedFrame.samples,
                    filterResult = filterResult,
                    saFrequency = saFrequency
                )

                if (filteredFreq > 0f) {
                    val swaraResult = swaraMapper.mapFrequency(saFrequency, filteredFreq, currentTuningPreset)

                    // Map to display models
                    val info = SwaraInfo(
                        swaraIndex = swaraResult.swaraIndex,
                        hindustaniName = swaraResult.swara.hindustaniName,
                        hindustaniAbbr = swaraResult.swara.hindustaniAbbr,
                        carnaticName = swaraResult.swara.carnaticName,
                        carnaticAbbr = swaraResult.swara.carnaticAbbr,
                        saptak = swaraResult.saptak.name
                    )

                    pitchHistory.push(detectorTimestampMs, filteredFreq, pitchResult.confidence)

                    _uiState.update { state ->
                        state.copy(
                            currentSwara = info,
                            centDeviation = swaraResult.centDeviation,
                            frequency = filteredFreq,
                            pitchHistory = pitchHistory,
                            historyVersion = state.historyVersion + 1
                        )
                    }

                    // Save to recorder if active (recording gets raw detection)
                    if (sessionRecorder.isRecording) {
                        sessionRecorder.addPitchPoint(swaraResult, pitchResult.confidence)
                    }

                } else {
                    // Store rejected frames as explicit UNVOICED/GAP markers. Do NOT convert them into normal 0 Hz pitch samples inside history.
                    val rms = PitchPipelineTelemetry.computeRms(capturedFrame.samples)
                    val gapMarker = if (rms < 0.005f) PitchRingBuffer.SILENCE_GAP else PitchRingBuffer.UNVOICED_GAP
                    pitchHistory.push(detectorTimestampMs, gapMarker, pitchResult.confidence)

                    _uiState.update { state ->
                        state.copy(
                            currentSwara = null,
                            frequency = 0f,
                            centDeviation = 0f,
                            pitchHistory = pitchHistory,
                            historyVersion = state.historyVersion + 1
                        )
                    }
                }
            }
        }
    }

    private fun stopAudioProcessing() {
        audioCollectionJob?.cancel()
        audioCollectionJob = null
        displayPitchFilter.resetVoicedState()
    }

    /**
     * Toggles session recording.
     */
    fun toggleRecording() {
        val isRecording = _uiState.value.isRecording
        if (isRecording) {
            stopRecording()
        } else {
            // Must have mic active to record pitch
            if (!_uiState.value.isMicActive) {
                toggleMic()
            }
            if (_uiState.value.isMicActive) {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        val file = sessionRecorder.startRecording()
        if (file != null) {
            _uiState.update { it.copy(isRecording = true, recordingDuration = 0L) }
            startRecordingTimer()
        }
    }

    private fun stopRecording() {
        sessionRecorder.stopRecording()
        stopRecordingTimer()
        _uiState.update { it.copy(isRecording = false, recordingDuration = 0L) }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update {
                    it.copy(recordingDuration = sessionRecorder.getElapsedTime() / 1000)
                }
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    fun setLiveMode(live: Boolean) {
        _uiState.update { it.copy(isLiveMode = live) }
    }

    fun toggleAutoFollow() {
        viewModelScope.launch {
            val current = _uiState.value.autoFollow
            settingsRepository.updateAutoFollow(!current)
        }
    }

    fun clearRaga() {
        ActiveRagaManager.setActiveRaga(null)
    }

    /**
     * Updates the active tuning preset directly from the monitor screen.
     */
    fun updateTuningPreset(preset: TuningPreset) {
        viewModelScope.launch {
            settingsRepository.updateTuningPreset(preset, isUserExplicit = true)
        }
    }

    /**
     * Cycles between the 3 tuning presets.
     */
    fun cycleTuningPreset() {
        val nextPreset = when (_uiState.value.tuningPreset) {
            TuningPreset.HARMONIC_5LIMIT -> TuningPreset.PYTHAGOREAN_3LIMIT
            TuningPreset.PYTHAGOREAN_3LIMIT -> TuningPreset.EQUAL_TEMPERAMENT
            TuningPreset.EQUAL_TEMPERAMENT -> TuningPreset.HARMONIC_5LIMIT
        }
        updateTuningPreset(nextPreset)
    }

    /**
     * Updates the tonic Sa configuration directly.
     */
    fun updateSaTonic(frequency: Float, noteName: String, octave: Int) {
        viewModelScope.launch {
            settingsRepository.updateSaTonic(frequency, noteName, octave)
        }
    }

    /**
     * Start generating synthetic pitch data for testing in emulator or without mic.
     */
    fun startPitchSimulation() {
        if (simulationJob != null) return
        
        // Stop actual audio capture
        if (_uiState.value.isMicActive) {
            toggleMic()
        }

        _uiState.update { it.copy(isMicActive = true) }

        simulationJob = viewModelScope.launch {
            var step = 0
            while (true) {
                delay(100)
                step++
                val nowUptime = android.os.SystemClock.uptimeMillis()

                // Generate pitch that sweeps across swaras
                val t = step * 0.05
                // Oscillate between Sa (ratio 1.0) and Pa (ratio 1.5)
                val baseRatio = 1.25 + 0.25 * sin(t)
                // Add minor jitter/vibrato
                val vibrato = 1.0 + 0.01 * sin(step * 0.5)
                val simFreq = (saFrequency * baseRatio * vibrato).toFloat()

                val swaraResult = swaraMapper.mapFrequency(saFrequency, simFreq, currentTuningPreset)
                val centsFromSa = 1200.0 * log2(simFreq.toDouble() / saFrequency.toDouble())

                val info = SwaraInfo(
                    swaraIndex = swaraResult.swaraIndex,
                    hindustaniName = swaraResult.swara.hindustaniName,
                    hindustaniAbbr = swaraResult.swara.hindustaniAbbr,
                    carnaticName = swaraResult.swara.carnaticName,
                    carnaticAbbr = swaraResult.swara.carnaticAbbr,
                    saptak = swaraResult.saptak.name
                )

                pitchHistory.push(nowUptime, simFreq, 0.95f)

                _uiState.update { state ->
                    state.copy(
                        currentSwara = info,
                        centDeviation = swaraResult.centDeviation,
                        frequency = simFreq,
                        pitchHistory = pitchHistory,
                        historyVersion = state.historyVersion + 1
                    )
                }
            }
        }
    }

    fun stopPitchSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    override fun onCleared() {
        super.onCleared()
        audioCaptureManager.stop()
        sessionRecorder.stopRecording()
        stopAudioProcessing()
        stopRecordingTimer()
        stopPitchSimulation()
    }

    /**
     * Shares the last recorded session via native share sheet.
     */
    fun shareRecording(context: Context) {
        val packageName = context.packageName
        val intent = sessionRecorder.createShareIntent("$packageName.fileprovider")
        if (intent != null) {
            val chooser = Intent.createChooser(intent, "Share Practice Recording")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    fun updateShowControls(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateMonitorControlsVisible(visible)
        }
    }
}
