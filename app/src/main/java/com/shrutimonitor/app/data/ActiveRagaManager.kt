package com.shrutimonitor.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global singleton manager to share the active Raga state across different ViewModels
 * (Monitor, Play, Raga Library, etc.).
 */
object ActiveRagaManager {
    private val _activeRaga = MutableStateFlow<Raga?>(null)
    
    /**
     * Exposes the currently active Raga as a read-only StateFlow.
     */
    val activeRaga: StateFlow<Raga?> = _activeRaga.asStateFlow()

    /**
     * Sets or clears the currently active Raga.
     */
    fun setActiveRaga(raga: Raga?) {
        _activeRaga.value = raga
    }
}
