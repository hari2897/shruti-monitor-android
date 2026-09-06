package com.shrutimonitor.app.ui.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
    val showBottomDrawer = state.showControls
    val view = LocalView.current

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
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

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
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Real-time scrolling pitch trace Graph (fills remaining vertical space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                PitchGraph(
                    pitchHistory = state.pitchHistory,
                    saFrequency = state.saFrequency,
                    activeRagaSwaras = state.activeRagaSwaras,
                    isLive = state.isLiveMode,
                    autoFollow = state.autoFollow,
                    nomenclature = state.nomenclature,
                    onLiveClick = { viewModel.setLiveMode(true) },
                    onScrollStart = { viewModel.setLiveMode(false) },
                    onAutoFollowToggle = { viewModel.toggleAutoFollow() },
                    modifier = Modifier.fillMaxSize()
                )

                // ── FLOATING OVERLAYS ON TOP OF GRAPH ──
                if (showBottomDrawer) {
                    // Floating Tonic Selector Chip (Top Left of Graph)
                    AssistChip(
                        onClick = {
                            HapticManager.tick(view)
                            showSaBottomSheet = true
                        },
                        label = {
                            Text(
                                text = "${state.saNoteName}${state.saOctave} (${String.format(java.util.Locale.US, "%.1f", state.saFrequency)} Hz)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tonic Reference",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )

                    // Floating Mic Status Toggle (Top Right of Graph)
                    FilledTonalIconButton(
                        onClick = {
                            HapticManager.heavyClick(view)
                            viewModel.toggleMic()
                        },
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (state.isMicActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            contentColor = if (state.isMicActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isMicActive) Icons.Default.MusicNote else Icons.Default.Tune,
                            contentDescription = "Toggle Microphone",
                            modifier = Modifier.size(18.dp)
                        )
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
}
