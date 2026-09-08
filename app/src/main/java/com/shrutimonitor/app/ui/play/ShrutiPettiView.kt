package com.shrutimonitor.app.ui.play

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.data.TuningPreset
import com.shrutimonitor.app.util.HapticManager
import java.util.Locale

/**
 * 3x4 Grid view of sustained tone generators (Shruti Petti / Box).
 *
 * Each swara button glows and pulses scale when active. Tapping toggles tone.
 * Non-raga swaras are locked and greyed out.
 */
@Composable
fun ShrutiPettiView(
    activeSwaras: Set<Int>?,
    playingSwaras: Set<Int>,
    saFrequency: Float,
    nomenclature: Nomenclature,
    tuningPreset: TuningPreset = TuningPreset.STANDARD,
    onSwaraToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎶 Tap to toggle sustained reference drone notes (Madhya)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(12) { index ->
                val swara = Swara.fromIndex(index)
                
                val isPlaying = playingSwaras.contains(index)
                val isInRaga = activeSwaras == null || activeSwaras.contains(index)
                
                val label = if (nomenclature == Nomenclature.HINDUSTANI) {
                    swara.hindustaniAbbr
                } else {
                    swara.carnaticAbbr
                }

                // Calculate note frequency in Hz based on selected tuning preset
                val noteHz = saFrequency * tuningPreset.ratioForSwara(swara)

                // Continuous pulse scaling when playing
                val infiniteTransition = rememberInfiniteTransition(label = "pettiPulse")
                val pulseScale by if (isPlaying) {
                    infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pettiScale"
                    )
                } else {
                    androidx.compose.runtime.mutableStateOf(1.0f)
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(pulseScale)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isInRaga) {
                                HapticManager.tick(view)
                                onSwaraToggle(index)
                            }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isPlaying) 2.dp else 1.dp,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .alpha(if (isInRaga) 1.0f else 0.40f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isInRaga) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked in Raga",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = label,
                                    fontSize = 22.sp,
                                    fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = String.format(Locale.US, "%.1f Hz", noteHz),
                            fontSize = 11.sp,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
