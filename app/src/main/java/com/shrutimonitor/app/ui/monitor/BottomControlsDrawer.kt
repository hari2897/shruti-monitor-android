package com.shrutimonitor.app.ui.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.util.HapticManager
import java.util.Locale

/**
 * Slide-up bottom control drawer sitting over the Monitor Screen.
 *
 * Implements:
 * - Expanding/collapsing via swipe or tapping the handle.
 * - Pulsing recording animation.
 * - Raga activation status chip (with clear function).
 * - Elapsed recording time formatting.
 * - Share options.
 */
@Composable
fun BottomControlsDrawer(
    isRecording: Boolean,
    recordingDuration: Long,
    activeRagaName: String?,
    onRecordToggle: () -> Unit,
    onClearRaga: () -> Unit,
    onRagaClick: () -> Unit = {},
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Pulse animation when recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRecord")
    val pulseScale by if (isRecording) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(1.0f)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount.y < -15) {
                        isExpanded = true
                    } else if (dragAmount.y > 15) {
                        isExpanded = false
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag / Tap handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        HapticManager.tick(view)
                        isExpanded = !isExpanded
                    }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Always Visible Header / Collapsed Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Record icon button
                IconButton(
                    onClick = {
                        HapticManager.heavyClick(view)
                        onRecordToggle()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isRecording) OutOfTuneCoral.copy(alpha = 0.15f) else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = "Record",
                        tint = OutOfTuneCoral,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(pulseScale)
                    )
                }

                // Raga Indicator Chip
                AssistChip(
                    onClick = {
                        HapticManager.tick(view)
                        onRagaClick()
                    },
                    label = {
                        Text(
                            text = activeRagaName ?: "No Raga Filter",
                            color = if (activeRagaName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (activeRagaName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (activeRagaName != null) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Raga",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        HapticManager.tick(view)
                                        onClearRaga()
                                    }
                            )
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Share Button (Quick Action)
                IconButton(
                    onClick = {
                        HapticManager.tick(view)
                        onShareClick()
                    },
                    enabled = !isRecording,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share last recording",
                        tint = if (isRecording) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Expanded panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Large Pulsing Record Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) OutOfTuneCoral.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .clickable {
                                HapticManager.heavyClick(view)
                                onRecordToggle()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(OutOfTuneCoral)
                                .scale(pulseScale)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Timer / Status
                    if (isRecording) {
                        val minutes = recordingDuration / 60
                        val seconds = recordingDuration % 60
                        val timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                        
                        Text(
                            text = "RECORDING $timeStr",
                            color = OutOfTuneCoral,
                            fontSize = 14.sp,
                            style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.5.sp)
                        )
                    } else {
                        Text(
                            text = "Tap to Record Practice Session",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Share button row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                HapticManager.tick(view)
                                onShareClick()
                            },
                            enabled = !isRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Share Recording", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
