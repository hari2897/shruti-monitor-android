package com.shrutimonitor.app.ui.play

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shrutimonitor.app.ui.settings.SaPickerView
import com.shrutimonitor.app.util.HapticManager

/**
 * Main Composable representing the Play screen containing three playable Indian classical instruments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSaBottomSheet by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Clean up focus/active audio on screen dispose
    DisposableEffect(Unit) {
        onDispose {
            // Stops background synthesis tracks if app goes to background
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Access Header Row (Tonic picker & Active Raga)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clickable Sa Tonic Chip
            AssistChip(
                onClick = {
                    HapticManager.tick(view)
                    showSaBottomSheet = true
                },
                label = {
                    Text(
                        text = "Base Sa: ${state.saNoteName}${state.saOctave} (${String.format(java.util.Locale.US, "%.1f", state.saFrequency)} Hz)",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Change Tonic",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    labelColor = MaterialTheme.colorScheme.primary,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )

            if (state.activeRagaName != null) {
                AssistChip(
                    onClick = { HapticManager.tick(view) },
                    label = {
                        Text(
                            text = "Raga: ${state.activeRagaName}",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                        labelColor = MaterialTheme.colorScheme.secondary,
                        leadingIconContentColor = MaterialTheme.colorScheme.secondary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // M3 Segmented Button Row for tabs
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 4.dp)
        ) {
            PlayTab.entries.forEachIndexed { index, tab ->
                val isSelected = state.activeTab == tab
                val title = when (tab) {
                    PlayTab.KEYBOARD -> "Harmonium"
                    PlayTab.SHRUTI_PETTI -> "Shruti Box"
                    PlayTab.TANPURA -> "Tanpura"
                }

                SegmentedButton(
                    selected = isSelected,
                    onClick = {
                        HapticManager.tick(view)
                        viewModel.setActiveTab(tab)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PlayTab.entries.size)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Crossfade animation between instrument interfaces
        Crossfade(
            targetState = state.activeTab,
            label = "tabCrossfade",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { activeTab ->
            when (activeTab) {
                PlayTab.KEYBOARD -> {
                    KeyboardView(
                        activeSwaras = state.activeSwaras,
                        pressedKeys = state.keyboardPressedKeys,
                        nomenclature = state.nomenclature,
                        onKeyDown = { viewModel.keyDown(it) },
                        onKeyUp = { viewModel.keyUp(it) }
                    )
                }
                PlayTab.SHRUTI_PETTI -> {
                    ShrutiPettiView(
                        activeSwaras = state.activeSwaras,
                        playingSwaras = state.playingSwaras,
                        saFrequency = state.saFrequency,
                        nomenclature = state.nomenclature,
                        tuningPreset = state.tuningPreset,
                        onSwaraToggle = { viewModel.toggleSwaraPlay(it) }
                    )
                }
                PlayTab.TANPURA -> {
                    TanpuraView(
                        isPlaying = state.tanpuraPlaying,
                        volume = state.tanpuraVolume,
                        jhalaString = state.jhalaString,
                        saNoteName = state.saNoteName,
                        saOctave = state.saOctave,
                        saFrequency = state.saFrequency,
                        onTonicClick = { showSaBottomSheet = true },
                        onTogglePlay = { viewModel.toggleTanpura() },
                        onVolumeChange = { viewModel.setTanpuraVolume(it) },
                        onJhalaChange = { viewModel.setJhalaString(it) },
                        speed = state.tanpuraSpeed,
                        onSpeedChange = { viewModel.setTanpuraSpeed(it) },
                        fineTuningCents = state.tanpuraFineTuning,
                        onFineTuningChange = { viewModel.setTanpuraFineTuning(it) },
                        is432HzMode = state.tanpura432Hz,
                        on432HzModeChange = { viewModel.setTanpura432Hz(it) },
                        stringPluckTimestamps = state.stringPluckTimestamps
                    )
                }
            }
        }
    }

    // Modal bottom sheet containing visual Sa picker
    if (showSaBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            SaPickerView(
                saFrequency = state.saFrequency,
                saNoteName = state.saNoteName,
                saOctave = state.saOctave,
                onTonicChange = { freq, name, oct ->
                    viewModel.updateSaTonic(freq, name, oct)
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
