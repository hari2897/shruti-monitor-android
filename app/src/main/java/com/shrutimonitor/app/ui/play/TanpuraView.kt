package com.shrutimonitor.app.ui.play

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.audio.JhalaString
import com.shrutimonitor.app.util.HapticManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Premium Tanpura drone synthesizer view.
 *
 * Combines:
 * - Circular ON/OFF switch with pulsing scale animation.
 * - Custom Canvas drawing simulating vibrating strings (sine waves with fixed boundaries).
 * - Jhala string selection.
 * - Dynamic volume slider.
 */
@Composable
fun TanpuraView(
    isPlaying: Boolean,
    volume: Float,
    jhalaString: JhalaString,
    saNoteName: String,
    saOctave: Int,
    saFrequency: Float,
    onTonicClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onJhalaChange: (JhalaString) -> Unit,
    speed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
    fineTuningCents: Float = 0.0f,
    onFineTuningChange: (Float) -> Unit = {},
    is432HzMode: Boolean = false,
    on432HzModeChange: (Boolean) -> Unit = {},
    stringPluckTimestamps: List<Long> = listOf(0L, 0L, 0L, 0L),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val view = LocalView.current

    var lastIsPlaying by remember { mutableStateOf(isPlaying) }
    var stopTime by remember { mutableStateOf(0L) }
    if (lastIsPlaying != isPlaying) {
        if (!isPlaying) {
            stopTime = System.currentTimeMillis()
        } else {
            stopTime = 0L
        }
        lastIsPlaying = isPlaying
    }

    val isAnimating = isPlaying || (stopTime > 0L && (System.currentTimeMillis() - stopTime) < 3000L)

    // 1. Continuous wave oscillation phase for strings
    val infiniteTransition = rememberInfiniteTransition(label = "stringOscillation")
    val stringPhase by if (isAnimating) {
        infiniteTransition.animateFloat(
            initialValue = 0.0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing)
            ),
            label = "phase"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(0.0f)
    }

    // ON/OFF button breathing pulse scale
    val powerButtonPulse by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "powerPulse"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(1.0f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header / title
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Acoustic Tanpura Drone",
                    fontSize = 20.sp,
                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "4-string additive synthesis",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Clickable Active Tonic Card/Chip inside Tanpura Card
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable {
                            HapticManager.tick(view)
                            onTonicClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Change Tonic",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tonic: $saNoteName$saOctave (${String.format(java.util.Locale.US, "%.1f", saFrequency)} Hz)",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ON/OFF Circular Trigger Button
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .scale(powerButtonPulse)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .border(
                            width = 2.dp,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable {
                            HapticManager.heavyClick(view)
                            onTogglePlay()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPlaying) "STOP" else "START",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 2. Animated Vibrating Strings Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    val outlineColor = MaterialTheme.colorScheme.outline

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val numStrings = 4
                        val padding = w * 0.1f
                        val spacing = (w - 2 * padding) / (numStrings - 1)

                        val stringPhaseOffsets = floatArrayOf(
                            0.0f,
                            (PI / 4).toFloat(),
                            (PI / 2).toFloat(),
                            (3 * PI / 4).toFloat()
                        )

                        for (i in 0 until numStrings) {
                            val xPos = padding + i * spacing
                            val path = Path()
                            
                            // Make boundaries fixed at top (0) and bottom (h)
                            path.moveTo(xPos, 0f)

                            // Dynamic amplitude decay calculation per string based on last pluck timestamp
                            val lastPluck = stringPluckTimestamps.getOrNull(i) ?: 0L
                            val elapsedMs = System.currentTimeMillis() - lastPluck
                            val decayTimeMs = 3000.0 // 3 seconds vibration decay
                            val elapsedFraction = (elapsedMs / decayTimeMs).coerceIn(0.0, 1.0)
                            // Exponential decay curve
                            val envelope = if (lastPluck > 0L) Math.exp(-3.0 * elapsedFraction) else 0.0
                            val stopElapsed = if (stopTime > 0L) System.currentTimeMillis() - stopTime else 0L
                            val stopFade = if (!isPlaying) {
                                (1.0 - stopElapsed / 3000.0).coerceIn(0.0, 1.0)
                            } else {
                                1.0
                            }
                            val deflection = (envelope * volume * 15f * stopFade).toFloat()

                            val step = 5
                            for (y in 0..h.toInt() step step) {
                                val fraction = y / h
                                // Boundary factor: fixed endpoints (sin(0) = sin(pi) = 0)
                                val boundaryScale = sin(PI * fraction).toFloat()
                                
                                // Calculate displacement via transverse wave equation
                                val displacement = deflection * boundaryScale * sin(stringPhase + stringPhaseOffsets[i])
                                path.lineTo(xPos + displacement, y.toFloat())
                            }
                            path.lineTo(xPos, h)

                            // Pick color: active strings show gradient/saffron, else grey
                            val strColor = if (isAnimating) {
                                if (i == 0) primaryColor else secondaryColor.copy(alpha = 0.8f)
                            } else {
                                outlineColor.copy(alpha = 0.5f)
                            }
                            
                            drawPath(
                                path = path,
                                color = strColor,
                                style = Stroke(width = if (isAnimating) 1.5.dp.toPx() else 1.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 3. Jhala String Selector (Pa / Ma / Ni)
                Text(
                    text = "Jhala String Tune (First String)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JhalaString.entries.forEach { stringTune ->
                        val isSelected = jhalaString == stringTune
                        ElevatedButton(
                            onClick = {
                                HapticManager.tick(view)
                                onJhalaChange(stringTune)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringTune.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Pluck Speed Slider
                Text(
                    text = "Pluck Speed",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Pluck Speed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = speed,
                        onValueChange = { newSpeed ->
                            HapticManager.tick(view)
                            onSpeedChange(newSpeed)
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", speed),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 6. Fine Tuning Slider
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Fine Tuning",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Fine Tuning",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = fineTuningCents,
                        onValueChange = { newCents ->
                            HapticManager.tick(view)
                            onFineTuningChange(newCents)
                        },
                        valueRange = -50f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%+.0f¢", fineTuningCents),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 7. 432 Hz Mode Switch
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "A4 = 432 Hz Tuning",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Lowers reference pitch from 440 Hz by -32 cents.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = is432HzMode,
                        onCheckedChange = {
                            HapticManager.tick(view)
                            on432HzModeChange(it)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.secondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    }
}
