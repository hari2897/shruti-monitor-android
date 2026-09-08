package com.shrutimonitor.app.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shrutimonitor.app.data.AppTheme
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.TuningPreset
import com.shrutimonitor.app.data.update.UpdateCheckStatus
import com.shrutimonitor.app.ui.update.UpdateDialog
import com.shrutimonitor.app.util.HapticManager
import java.util.Locale

/**
 * App Settings & Configuration Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // 1. Sa Tonic Picker
        SaPickerView(
            saFrequency = state.saFrequency,
            saNoteName = state.saNoteName,
            saOctave = state.saOctave,
            onTonicChange = { freq, name, oct ->
                HapticManager.thud(view)
                viewModel.updateSaTonic(freq, name, oct)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Nomenclature Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Nomenclature (Terminology)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose names for display (Sa Re Ga vs. Shadjam Rishabham)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Nomenclature.entries.forEachIndexed { index, nom ->
                        val label = if (nom == Nomenclature.HINDUSTANI) "Hindustani (Sa Re Ga)" else "Carnatic (Sa Ri Ga)"

                        SegmentedButton(
                            selected = state.nomenclature == nom,
                            onClick = {
                                HapticManager.tick(view)
                                viewModel.updateNomenclature(nom)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Nomenclature.entries.size)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Tuning Preset Card (Shruti System)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tuning Preset (Shruti System)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Mathematical ratio system for swarasthanas and grid lines",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TuningPreset.entries.forEachIndexed { index, preset ->
                        SegmentedButton(
                            selected = state.tuningPreset == preset,
                            onClick = {
                                HapticManager.tick(view)
                                viewModel.updateTuningPreset(preset)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = TuningPreset.entries.size)
                        ) {
                            Text(
                                text = preset.shortName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail explanation box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = state.tuningPreset.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (state.tuningPreset) {
                                TuningPreset.HARMONIC_5LIMIT ->
                                    "Pure harmonic thirds & fifths. Consonant with drone; common in Hindustani & vocal riyaz (G2 = 6/5, 315.6¢)."
                                TuningPreset.PYTHAGOREAN_3LIMIT ->
                                    "Derived via cycle of 4ths & 5ths. Aligns with standard Carnatic Veena swarasthana charts & classical treatises (G2 = 32/27, 294.1¢)."
                                TuningPreset.EQUAL_TEMPERAMENT ->
                                    "Standard chromatic reference with equal 100¢ semitones (G2 = 300¢). Ideal for Western tempered instruments."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Audio & Detection Tuning Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detection Pipeline Calibration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Pitch Confidence Threshold Slider
                Text(
                    text = String.format(Locale.US, "Pitch Confidence Filter: %.2f", state.confidenceThreshold),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Filters background noise. Higher = fewer false detections.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.confidenceThreshold,
                    onValueChange = {
                        HapticManager.tick(view)
                        viewModel.updateConfidenceThreshold(it)
                    },
                    valueRange = 0.2f..0.9f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mic Sensitivity Slider
                Text(
                    text = String.format(Locale.US, "Microphone Input Sensitivity: %.2f", state.micSensitivity),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Applies a digital gain to incoming audio levels.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.micSensitivity,
                    onValueChange = {
                        HapticManager.tick(view)
                        viewModel.updateMicSensitivity(it)
                    },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // In-Tune Tolerance Slider
                Text(
                    text = String.format(Locale.US, "In-Tune Tolerance: ±%.0f¢", state.inTuneTolerance),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Cents range around a swara that counts as 'in tune' for the green indicator.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.inTuneTolerance,
                    onValueChange = {
                        HapticManager.tick(view)
                        viewModel.updateInTuneTolerance(it)
                    },
                    valueRange = 5f..25f,
                    steps = 3, // 5, 10, 15, 20, 25
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Haptic Feedback Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Haptic Feedback",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Vibrate on button clicks and keyboard key presses.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.hapticEnabled,
                        onCheckedChange = {
                            HapticManager.tick(view)
                            viewModel.updateHapticEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. App Theme Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Visual Interface Theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppTheme.entries.forEachIndexed { index, themeOption ->
                        val label = when (themeOption) {
                            AppTheme.DARK -> "Deep Indigo"
                            AppTheme.AMOLED -> "AMOLED Black"
                            AppTheme.AUTO -> "System Default"
                        }

                        SegmentedButton(
                            selected = state.theme == themeOption,
                            onClick = {
                                HapticManager.tick(view)
                                viewModel.updateTheme(themeOption)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.entries.size)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. About Details Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About Shruti Monitor",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "A professional pitch analysis tool designed specifically for Indian Classical music. Built natively for Android using Jetpack Compose, running real-time McLeod pitch tracking (MPM) and mapping frequencies onto mathematical Just Intonation (JI) swarasthana boundaries.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scale Tuning: Just Intonation (Harmonic 5-Limit & Pythagorean 3-Limit presets)\nEngine Latency: 46 ms (2048 samples at 44.1kHz)\nVersion: ${viewModel.currentVersionName} (Native Android)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // App Updates Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Software Updates",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        when (val status = updateCheckStatus) {
                            is UpdateCheckStatus.Idle -> {
                                Text(
                                    text = "Tap to check GitHub for new releases",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is UpdateCheckStatus.Checking -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Checking for updates...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            is UpdateCheckStatus.UpToDate -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Up to date (v${status.currentVersionName})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            is UpdateCheckStatus.UpdateAvailable -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Update v${status.updateInfo.latestVersionName} available!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            is UpdateCheckStatus.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Check failed: ${status.message.take(30)}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    when (updateCheckStatus) {
                        is UpdateCheckStatus.UpdateAvailable -> {
                            Button(
                                onClick = {
                                    HapticManager.tick(view)
                                    viewModel.checkForUpdates()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateCheckStatus.Checking -> {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Checking...", fontSize = 12.sp)
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = {
                                    HapticManager.tick(view)
                                    viewModel.checkForUpdates()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Reset settings button
        Button(
            onClick = {
                HapticManager.heavyClick(view)
                viewModel.resetToDefaults()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Icon(
                imageVector = Icons.Default.SettingsBackupRestore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Restore Factory Defaults", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(80.dp))
    }

    // Render Update Dialog if triggered
    if (showUpdateDialog && updateCheckStatus is UpdateCheckStatus.UpdateAvailable) {
        val updateInfo = (updateCheckStatus as UpdateCheckStatus.UpdateAvailable).updateInfo
        UpdateDialog(
            updateInfo = updateInfo,
            downloadStatus = downloadStatus,
            onDownloadAndInstall = { viewModel.downloadAndInstallUpdate(updateInfo) },
            onInstallNow = { file -> viewModel.installDownloadedApk(file) },
            onOpenInBrowser = { url -> viewModel.openWebUrl(url) },
            onDismiss = { viewModel.dismissUpdateDialog() },
            onSkipVersion = { viewModel.skipUpdate(updateInfo.latestVersionName) }
        )
    }
}
