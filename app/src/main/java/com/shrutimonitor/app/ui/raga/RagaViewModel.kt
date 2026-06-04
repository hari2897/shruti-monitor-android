package com.shrutimonitor.app.ui.raga

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrutimonitor.app.data.ActiveRagaManager
import com.shrutimonitor.app.data.MelaKarta
import com.shrutimonitor.app.data.Raga
import com.shrutimonitor.app.data.RagaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for the Raga Library screen.
 */
data class RagaUiState(
    val searchQuery: String = "",
    val activeRaga: Raga? = null,
    val filteredHindustaniRagas: List<Raga> = emptyList(),
    val filteredCarnaticMelakartas: List<MelaKarta> = emptyList()
)

class RagaViewModel(application: Application) : AndroidViewModel(application) {

    private val ragaRepository = RagaRepository(application)

    private val _uiState = MutableStateFlow(RagaUiState())
    val uiState: StateFlow<RagaUiState> = _uiState.asStateFlow()

    private var allHindustani: List<Raga> = emptyList()
    private var allCarnatic: List<MelaKarta> = emptyList()

    init {
        // Load data in a background coroutine
        viewModelScope.launch {
            allHindustani = ragaRepository.getHindustaniRagas()
            allCarnatic = ragaRepository.getCarnaticMelakartas()
            
            // Initial filter (empty query means display all)
            applyFilter("")
        }

        // Collect global active raga changes
        viewModelScope.launch {
            ActiveRagaManager.activeRaga.collect { raga ->
                _uiState.update { it.copy(activeRaga = raga) }
            }
        }
    }

    /**
     * Updates the search query and applies the filter.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter(query)
    }

    /**
     * Activates a Raga.
     */
    fun activateRaga(raga: Raga) {
        ActiveRagaManager.setActiveRaga(raga)
    }

    /**
     * Clears any active Raga filter.
     */
    fun deactivateRaga() {
        ActiveRagaManager.setActiveRaga(null)
    }

    /**
     * Filter implementation logic.
     */
    private fun applyFilter(query: String) {
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    filteredHindustaniRagas = allHindustani,
                    filteredCarnaticMelakartas = allCarnatic
                )
            }
            return
        }

        val q = query.trim()
        val filteredHindustani = allHindustani.filter { raga ->
            raga.name.contains(q, ignoreCase = true) ||
                    raga.altNames.any { it.contains(q, ignoreCase = true) } ||
                    raga.thaat?.contains(q, ignoreCase = true) == true
        }

        val filteredCarnatic = allCarnatic.filter { melakarta ->
            melakarta.name.contains(q, ignoreCase = true) ||
                    melakarta.chakra.contains(q, ignoreCase = true) ||
                    melakarta.janyaRagas.any { janya ->
                        janya.name.contains(q, ignoreCase = true)
                    }
        }

        _uiState.update {
            it.copy(
                filteredHindustaniRagas = filteredHindustani,
                filteredCarnaticMelakartas = filteredCarnatic
            )
        }
    }
}
