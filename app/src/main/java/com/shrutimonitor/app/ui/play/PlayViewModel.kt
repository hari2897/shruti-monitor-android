package com.shrutimonitor.app.ui.play

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrutimonitor.app.audio.JhalaString
import com.shrutimonitor.app.audio.KeyboardPlayer
import com.shrutimonitor.app.audio.Saptak
import com.shrutimonitor.app.audio.ShrutiPettiPlayer
import com.shrutimonitor.app.audio.TanpuraSynthesizer
import com.shrutimonitor.app.data.ActiveRagaManager
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tab options on the Play Instruments screen.
 */
enum class PlayTab {
    KEYBOARD,
    SHRUTI_PETTI,
    TANPURA
}

/**
 * UI State for the play instruments screen.
 */
data class PlayUiState(
    val activeTab: PlayTab = PlayTab.KEYBOARD,
    val activeSwaras: Set<Int>? = null,
    val activeRagaName: String? = null,
    val saFrequency: Float = 261.63f,
    val saNoteName: String = "C",
    val saOctave: Int = 4,
    val nomenclature: Nomenclature = Nomenclature.HINDUSTANI,
    val tanpuraPlaying: Boolean = false,
    val tanpuraVolume: Float = 0.5f,
    val tanpuraSpeed: Float = 1.0f,
    val jhalaString: JhalaString = JhalaString.PA,
    val tanpuraFineTuning: Float = 0.0f,
    val tanpura432Hz: Boolean = false,
    val stringPluckTimestamps: List<Long> = listOf(0L, 0L, 0L, 0L),
    val playingSwaras: Set<Int> = emptySet(), // Toggled swaras in Shruti Petti (Mandra/Madhya/Tara separate)
    val keyboardPressedKeys: Set<Int> = emptySet() // Pressed keys absolute indexes
)

class PlayViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    
    // Synthesis engines - use shared singletons to prevent overlapping playback loops on tab switches
    private val tanpuraSynthesizer = TanpuraSynthesizer.shared
    private val shrutiPettiPlayer = ShrutiPettiPlayer.shared
    private val keyboardPlayer = KeyboardPlayer.shared

    private val _uiState = MutableStateFlow(PlayUiState())
    val uiState: StateFlow<PlayUiState> = _uiState.asStateFlow()

    init {
        // Supply application context to Tanpura engine
        tanpuraSynthesizer.setContext(application)

        // Connect pluck callback to update timestamps in UI state
        tanpuraSynthesizer.onStringPlucked = { stringIndex ->
            _uiState.update { state ->
                val list = state.stringPluckTimestamps.toMutableList()
                if (stringIndex in 0..3) {
                    list[stringIndex] = System.currentTimeMillis()
                }
                state.copy(stringPluckTimestamps = list)
            }
        }

        // Collect configuration preferences
        viewModelScope.launch {
            settingsRepository.saFrequency.collect { freq ->
                _uiState.update { it.copy(saFrequency = freq) }
                // Propagate tonic to engines
                tanpuraSynthesizer.saFrequency = freq
                shrutiPettiPlayer.saFrequency = freq
                keyboardPlayer.setSaFrequency(freq)
            }
        }
        viewModelScope.launch {
            settingsRepository.saNoteName.collect { name ->
                _uiState.update { it.copy(saNoteName = name) }
                tanpuraSynthesizer.saNoteName = name
            }
        }
        viewModelScope.launch {
            settingsRepository.saOctave.collect { oct ->
                _uiState.update { it.copy(saOctave = oct) }
                tanpuraSynthesizer.saOctave = oct
            }
        }
        viewModelScope.launch {
            settingsRepository.nomenclature.collect { nom ->
                _uiState.update { it.copy(nomenclature = nom) }
            }
        }
        viewModelScope.launch {
            settingsRepository.tanpuraFineTuning.collect { cents ->
                _uiState.update { it.copy(tanpuraFineTuning = cents) }
                tanpuraSynthesizer.fineTuningCents = cents
            }
        }
        viewModelScope.launch {
            settingsRepository.tanpura432Hz.collect { enabled ->
                _uiState.update { it.copy(tanpura432Hz = enabled) }
                tanpuraSynthesizer.is432HzMode = enabled
            }
        }

        // Collect active raga selections
        viewModelScope.launch {
            ActiveRagaManager.activeRaga.collect { raga ->
                _uiState.update { state ->
                    state.copy(
                        activeRagaName = raga?.name,
                        activeSwaras = raga?.activeSwaras?.map { it.index }?.toSet()
                    )
                }
            }
        }
    }

    fun setActiveTab(tab: PlayTab) {
        _uiState.update { it.copy(activeTab = tab) }
        
        // Optimize resources by auto-starting/stopping engines based on tab focus
        when (tab) {
            PlayTab.KEYBOARD -> {
                keyboardPlayer.start()
            }
            PlayTab.SHRUTI_PETTI -> {
                shrutiPettiPlayer.start()
            }
            PlayTab.TANPURA -> {
                // Tanpura has its own explicit ON/OFF button, so we don't force start it here.
            }
        }
    }

    // ── Tanpura Controls ───────────────────────────────────────────────

    fun toggleTanpura() {
        val nextPlaying = !_uiState.value.tanpuraPlaying
        if (nextPlaying) {
            tanpuraSynthesizer.start()
        } else {
            tanpuraSynthesizer.stop()
        }
        _uiState.update { it.copy(tanpuraPlaying = tanpuraSynthesizer.isRunning()) }
    }

    fun setTanpuraVolume(volume: Float) {
        tanpuraSynthesizer.volume = volume
        _uiState.update { it.copy(tanpuraVolume = volume) }
    }

    fun setTanpuraSpeed(speed: Float) {
        tanpuraSynthesizer.speed = speed
        _uiState.update { it.copy(tanpuraSpeed = speed) }
    }

    fun setJhalaString(jhala: JhalaString) {
        tanpuraSynthesizer.jhalaString = jhala
        _uiState.update { it.copy(jhalaString = jhala) }
    }

    fun setTanpuraFineTuning(cents: Float) {
        viewModelScope.launch {
            settingsRepository.updateTanpuraFineTuning(cents)
        }
    }

    fun setTanpura432Hz(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTanpura432Hz(enabled)
        }
    }

    /**
     * Updates the tonic Sa configuration directly.
     */
    fun updateSaTonic(frequency: Float, noteName: String, octave: Int) {
        viewModelScope.launch {
            settingsRepository.updateSaTonic(frequency, noteName, octave)
        }
    }

    // ── Shruti Petti Controls ──────────────────────────────────────────

    /**
     * Toggles play/stop on a swara in the middle octave.
     */
    fun toggleSwaraPlay(swaraIndex: Int) {
        _uiState.update { state ->
            val isPlaying = state.playingSwaras.contains(swaraIndex)
            val updated = if (isPlaying) {
                shrutiPettiPlayer.stopSwara(swaraIndex, Saptak.MADHYA)
                state.playingSwaras - swaraIndex
            } else {
                shrutiPettiPlayer.playSwara(swaraIndex, Saptak.MADHYA)
                state.playingSwaras + swaraIndex
            }
            state.copy(playingSwaras = updated)
        }
    }

    // ── Keyboard Controls ──────────────────────────────────────────────

    fun keyDown(noteIndex: Int) {
        keyboardPlayer.noteOn(noteIndex)
        _uiState.update { state ->
            state.copy(keyboardPressedKeys = state.keyboardPressedKeys + noteIndex)
        }
    }

    fun keyUp(noteIndex: Int) {
        keyboardPlayer.noteOff(noteIndex)
        _uiState.update { state ->
            state.copy(keyboardPressedKeys = state.keyboardPressedKeys - noteIndex)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure all audio generators are released to prevent memory leaks or audio clipping
        tanpuraSynthesizer.stop()
        shrutiPettiPlayer.stop()
        keyboardPlayer.stop()
    }
}
