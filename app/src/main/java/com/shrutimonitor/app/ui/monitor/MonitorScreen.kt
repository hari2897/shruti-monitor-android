package com.shrutimonitor.app.ui.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.ui.theme.TextSecondary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shrutimonitor.app.data.InstrumentCategory
import com.shrutimonitor.app.ui.settings.SaPickerView
import com.shrutimonitor.app.util.HapticManager

/**
 * Root Monitor Screen composable.
 *
 * Combines:
 * - SwaraDisplayCard (vocal note readout)
 * - PitchSlider (microtonal cents tuner)
 * - PitchGraph (gamaka sliding waveform trace)
 * - BottomControlsDrawer (drawer overlay for recording and raga filter metadata)
 *
 * Implements:
 * - Fullscreen redesign with compact Swara Card and Pitch Slider.
 * - Floating overlays on top of the PitchGraph.
 * - Immersive sticky mode toggle to hide system bars and bottom nav while singing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    modifier: Modifier = Modifier,
    onNavigateToRagas: () -> Unit = {},
    viewModel: MonitorViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSaBottomSheet by remember { mutableStateOf(false) }
    var showInstrumentSheet by remember { mutableStateOf(false) }
    var showRiyazStudioSheet by remember { mutableStateOf(false) }
    val showBottomDrawer = state.showControls
    val view = LocalView.current

    val onLiveClick = remember(viewModel) { { viewModel.setLiveMode(true) } }
    val onScrollStart = remember(viewModel) { { viewModel.setLiveMode(false) } }
    val onAutoFollowToggle = remember(viewModel) { { viewModel.toggleAutoFollow() } }

    // Synchronize immersive mode with showBottomDrawer state
    LaunchedEffect(showBottomDrawer) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (showBottomDrawer) {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Stop simulation and restore system bars on leave
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPitchSimulation()
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Compact Swara Display Card (Row layout with TunerProgressBar)
            SwaraDisplayCard(
                swaraAbbr = state.currentSwara?.let {
                    if (state.nomenclature == com.shrutimonitor.app.data.Nomenclature.HINDUSTANI) {
                        it.hindustaniAbbr
                    } else {
                        it.carnaticAbbr
                    }
                },
                swaraFullName = state.currentSwara?.let {
                    if (state.nomenclature == com.shrutimonitor.app.data.Nomenclature.HINDUSTANI) {
                        it.hindustaniName
                    } else {
                        it.carnaticName
                    }
                },
                swaraIndex = state.currentSwara?.swaraIndex,
                saptakName = state.currentSwara?.saptak,
                centDeviation = state.centDeviation,
                isMicActive = state.isMicActive,
                saNoteName = state.saNoteName,
                saOctave = state.saOctave,
                inTuneTolerance = state.inTuneTolerance,
                shrutiName = state.shrutiName,
                gamakaMetrics = state.gamakaMetrics,
                frequencyHz = state.frequency,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 2. Thin Horizontal Pitch positions slider (36dp height)
            val centsFromSa = if (state.frequency > 0f) {
                (1200.0 * kotlin.math.log2(state.frequency.toDouble() / state.saFrequency.toDouble())).toFloat()
            } else {
                null
            }
            PitchSlider(
                centsFromSa = centsFromSa,
                activeRagaSwaras = state.activeRagaSwaras,
                nomenclature = state.nomenclature,
                tuningPreset = state.tuningPreset,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 3. Real-time scrolling pitch trace Graph (fills remaining vertical space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PitchGraph(
                    pitchHistory = state.pitchHistory,
                    saFrequency = state.saFrequency,
                    activeRagaSwaras = state.activeRagaSwaras,
                    isLive = state.isLiveMode,
                    autoFollow = state.autoFollow,
                    nomenclature = state.nomenclature,
                    tuningPreset = state.tuningPreset,
                    rmsAmplitude = state.rmsAmplitude,
                    harmonicBrightness = state.harmonicBrightness,
                    inTuneFactor = if (state.currentSwara != null && kotlin.math.abs(state.centDeviation) <= state.inTuneTolerance) 1f else 0f,
                    shaderVisualsEnabled = state.shaderVisualsEnabled,
                    ghostTrace = state.activeGhostTrace,
                    onLiveClick = onLiveClick,
                    onScrollStart = onScrollStart,
                    onAutoFollowToggle = onAutoFollowToggle,
                    modifier = Modifier.fillMaxSize()
                )

                // ── FLOATING OVERLAYS ON TOP OF GRAPH ──
                if (showBottomDrawer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Tonic Sa Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .clickable {
                                    HapticManager.tick(view)
                                    showSaBottomSheet = true
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tonic",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${state.saNoteName}${state.saOctave} (${String.format(java.util.Locale.US, "%.1f", state.saFrequency)})",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // 2. Tuning Preset Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .clickable {
                                    HapticManager.tick(view)
                                    viewModel.cycleTuningPreset()
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.tuningPreset.shortName,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // 3. Instrument Profile Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .clickable {
                                    HapticManager.tick(view)
                                    showInstrumentSheet = true
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.instrumentCategory.shortLabel,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // 4. Riyaz Studio / Scorecard Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                                .clickable {
                                    HapticManager.tick(view)
                                    viewModel.refreshRiyazStudio()
                                    showRiyazStudioSheet = true
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Riyaz Studio & Scorecard",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Stats",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 5. Proper Microphone On/Off Button (Far Right)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (state.isMicActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (state.isMicActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    HapticManager.heavyClick(view)
                                    viewModel.toggleMic()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (state.isMicActive) "Microphone Active" else "Microphone Muted",
                                tint = if (state.isMicActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Floating Varjya warning alert badge
                    if (state.isVarjyaWarning && state.activeRagaName != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OutOfTuneCoral.copy(alpha = 0.92f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Varjya Swara (${state.currentSwara?.carnaticAbbr ?: ""}) — Outside ${state.activeRagaName}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Floating Hide/Show Controls Toggle Capsule (Bottom Left of Graph)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 12.dp)
                        .background(
                            color = Color(0xDC0A0A0F), // Sleek, dark capsule background matching follow/live controls
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (showBottomDrawer) Color.White.copy(alpha = 0.05f) else Color.Transparent
                            )
                            .clickable {
                                HapticManager.tick(view)
                                viewModel.updateShowControls(!showBottomDrawer)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showBottomDrawer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = if (showBottomDrawer) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else TextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showBottomDrawer) "Hide" else "Show",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showBottomDrawer) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else TextSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Bottom drawer (Recording & Raga info)
            if (showBottomDrawer) {
                Spacer(modifier = Modifier.height(4.dp))
                BottomControlsDrawer(
                    isRecording = state.isRecording,
                    recordingDuration = state.recordingDuration,
                    activeRagaName = state.activeRagaName,
                    onRecordToggle = { viewModel.toggleRecording() },
                    onClearRaga = { viewModel.clearRaga() },
                    onRagaClick = onNavigateToRagas,
                    onShareClick = { viewModel.shareRecording(context) }
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp)) // Extra safety spacing at bottom when hidden
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

    if (showInstrumentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInstrumentSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            InstrumentFluteSheet(
                selectedCategory = state.instrumentCategory,
                saFrequency = state.saFrequency,
                saNoteName = state.saNoteName,
                saOctave = state.saOctave,
                shaderVisualsEnabled = state.shaderVisualsEnabled,
                onSelectCategory = { viewModel.selectInstrumentCategory(it) },
                onOpenSaPicker = {
                    showInstrumentSheet = false
                    showSaBottomSheet = true
                },
                onToggleShaderVisuals = { viewModel.updateShaderVisuals(it) }
            )
        }
    }

    if (showRiyazStudioSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRiyazStudioSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            RiyazStudioSheet(
                summary = state.riyazSummary,
                nomenclature = state.nomenclature,
                savedSessions = state.savedSessions,
                activeGhostSessionId = state.activeGhostSessionId,
                playingSessionId = state.playingSessionId,
                onResetScorecard = { viewModel.resetScorecard() },
                onToggleGhostTrace = { viewModel.toggleGhostTrace(it) },
                onTogglePlaySession = { viewModel.togglePlaySession(it) },
                onDeleteSession = { viewModel.deleteSavedSession(it) }
            )
        }
    }
}
