package com.shrutimonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.shrutimonitor.app.data.AppTheme
import com.shrutimonitor.app.data.SettingsRepository
import com.shrutimonitor.app.ui.theme.ShrutiMonitorTheme

/**
 * Entry activity of the Shruti Monitor app.
 * Dynamically binds the Material M3 theme configuration based on user settings repository state.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup translucent edge-to-edge system window bars
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(applicationContext)
        
        setContent {
            // Collect settings dynamically
            val themeState by settingsRepository.theme.collectAsState(initial = AppTheme.AUTO)
            val nomenclatureState by settingsRepository.nomenclature.collectAsState(initial = com.shrutimonitor.app.data.Nomenclature.HINDUSTANI)
            val hapticState by settingsRepository.hapticEnabled.collectAsState(initial = true)
            
            // Sync haptic manager state
            com.shrutimonitor.app.util.HapticManager.isEnabled = hapticState
            
            ShrutiMonitorTheme(appTheme = themeState, nomenclature = nomenclatureState) {
                ShruthiMonitorApp()
            }
        }
    }
}
