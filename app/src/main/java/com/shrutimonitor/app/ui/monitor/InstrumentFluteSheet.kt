package com.shrutimonitor.app.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.data.FluteRegister
import com.shrutimonitor.app.data.InstrumentCategory
import com.shrutimonitor.app.util.HapticManager
import java.util.Locale

@Composable
fun InstrumentFluteSheet(
    selectedCategory: InstrumentCategory,
    saFrequency: Float,
    saNoteName: String,
    saOctave: Int,
    shaderVisualsEnabled: Boolean,
    onSelectCategory: (InstrumentCategory) -> Unit,
    onOpenSaPicker: () -> Unit,
    onToggleShaderVisuals: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val activeFluteRegister = FluteRegister.forFrequency(saFrequency)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Acoustic Instrument & DSP Profile",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Dynamically tunes MPM harmonic clarity, breath-noise gate, and frequency bounds",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InstrumentCategory.entries.forEach { category ->
                val selected = category == selectedCategory
                FilterChip(
                    selected = selected,
                    onClick = {
                        HapticManager.tick(view)
                        onSelectCategory(category)
                    },
                    label = {
                        Text(
                            text = category.shortLabel,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Acoustic Calibration Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            if (selectedCategory == InstrumentCategory.BAMBOO_FLUTE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeFluteRegister.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sa = $saNoteName$saOctave (${String.format(Locale.US, "%.1f Hz", saFrequency)})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = activeFluteRegister.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "MPM Clarity: %.2f  •  Breath Gate: %.4f  •  Range: %.0f–%.0f Hz",
                        activeFluteRegister.clarityThreshold,
                        activeFluteRegister.rmsNoiseGate,
                        activeFluteRegister.minFreqHz,
                        activeFluteRegister.maxFreqHz
                    ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        HapticManager.tick(view)
                        onOpenSaPicker()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Change Flute Scale / Tonic Sa (Any Pitch or Hz)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = selectedCategory.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "MPM Clarity: %.2f  •  Noise Gate: %.4f  •  Range: %.0f–%.0f Hz",
                        selectedCategory.mpmClarityThreshold,
                        selectedCategory.rmsNoiseGate,
                        selectedCategory.minFreqHz,
                        selectedCategory.maxFreqHz
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AGSL GPU Shader Visuals Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AGSL GPU Aurora Resonance Field",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hardware-accelerated harmonic standing wave shader behind pitch trace",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = shaderVisualsEnabled,
                onCheckedChange = {
                    HapticManager.tick(view)
                    onToggleShaderVisuals(it)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
