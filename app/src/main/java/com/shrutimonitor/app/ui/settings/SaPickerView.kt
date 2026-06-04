package com.shrutimonitor.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.ui.theme.BlackKeyColor
import com.shrutimonitor.app.ui.theme.WhiteKeyColor
import com.shrutimonitor.app.util.HapticManager
import java.util.Locale

/**
 * Visual Tonic Sa selector. Features:
 * - Prominent frequency Hz display
 * - Octave shifting control (C1 to C5)
 * - Compact responsive 1-octave piano strip (C to B) for note selection
 */
@Composable
fun SaPickerView(
    saFrequency: Float,
    saNoteName: String,
    saOctave: Int,
    onTonicChange: (Float, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val view = LocalView.current
    
    // Maps white key index in an octave back to chromatic index (0-11)
    val whiteToChromaticMap = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    
    // Active note index (C=0, C#=1, etc.)
    val activeNoteIndex = noteNames.indexOf(saNoteName).coerceAtLeast(0)

    // Calculate frequency based on note index & octave relative to A440
    fun calculateFrequency(noteIdx: Int, oct: Int): Float {
        // A4 = 440Hz is noteIndex 9 at octave 4
        val halfStepsFromA4 = (oct - 4) * 12 + (noteIdx - 9)
        return (440.0 * Math.pow(2.0, halfStepsFromA4 / 12.0)).toFloat()
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Frequency Display
            Text(
                text = "Tonic Frequency (Sa)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format(Locale.US, "%.2f Hz", saFrequency),
                fontSize = 32.sp,
                fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Pitch Reference: $saNoteName$saOctave",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Octave Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canDecreaseOctave = saOctave > 2
                IconButton(
                    onClick = {
                        HapticManager.tick(view)
                        if (canDecreaseOctave) {
                            val newOct = saOctave - 1
                            val newNoteName = if (newOct == 2 && activeNoteIndex < 9) "A" else saNoteName
                            val newNoteIdx = if (newOct == 2 && activeNoteIndex < 9) 9 else activeNoteIndex
                            val newFreq = calculateFrequency(newNoteIdx, newOct)
                            onTonicChange(newFreq, newNoteName, newOct)
                        }
                    },
                    enabled = canDecreaseOctave
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Lower Octave",
                        tint = if (canDecreaseOctave) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                Text(
                    text = "Octave $saOctave",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        HapticManager.tick(view)
                        if (saOctave < 5) {
                            val newOct = saOctave + 1
                            val newFreq = calculateFrequency(activeNoteIndex, newOct)
                            onTonicChange(newFreq, saNoteName, newOct)
                        }
                    },
                    enabled = saOctave < 5
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Higher Octave",
                        tint = if (saOctave < 5) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Compact Responsive Visual Piano Strip (fits fully on screen)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                val boxWidth = maxWidth
                val totalWhiteKeys = 7
                val whiteKeyWidth = boxWidth / totalWhiteKeys
                val blackKeyWidth = whiteKeyWidth * 0.65f
                val halfBlackWidth = blackKeyWidth / 2

                // 1. Layer: White Keys
                Row(modifier = Modifier.fillMaxSize()) {
                    for (wk in 0 until totalWhiteKeys) {
                        val noteIdx = whiteToChromaticMap[wk]
                        val name = noteNames[noteIdx]
                        val isSelected = saNoteName == name
                        val keyFreq = calculateFrequency(noteIdx, saOctave)
                        val isKeyEnabled = keyFreq >= 109.9f

                        Box(
                            modifier = Modifier
                                .width(whiteKeyWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 0.5.dp)
                                .shadow(1.dp, shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else if (!isKeyEnabled) {
                                        WhiteKeyColor.copy(alpha = 0.35f)
                                    } else {
                                        WhiteKeyColor
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFD0C9BC),
                                    shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                                )
                                .clickable(enabled = isKeyEnabled) {
                                    HapticManager.tick(view)
                                    val newFreq = calculateFrequency(noteIdx, saOctave)
                                    onTonicChange(newFreq, name, saOctave)
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = name,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (!isKeyEnabled) {
                                        Color.Black.copy(alpha = 0.25f)
                                    } else {
                                        Color.Black
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 2. Layer: Black Keys
                // Place black keys at absolute offsets between appropriate white keys
                for (wk in 0 until totalWhiteKeys) {
                    if (wk == 0 || wk == 1 || wk == 3 || wk == 4 || wk == 5) {
                        val noteIdx = when (wk) {
                            0 -> 1 // C#
                            1 -> 3 // D#
                            3 -> 6 // F#
                            4 -> 8 // G#
                            else -> 10 // A#
                        }
                        val name = noteNames[noteIdx]
                        val isSelected = saNoteName == name
                        val keyFreq = calculateFrequency(noteIdx, saOctave)
                        val isKeyEnabled = keyFreq >= 109.9f

                        // Horizontal offset: (wk + 1) * whiteKeyWidth - halfBlackWidth
                        val xOffset = whiteKeyWidth * (wk + 1) - halfBlackWidth

                        Box(
                            modifier = Modifier
                                .offset(x = xOffset, y = 0.dp)
                                .width(blackKeyWidth)
                                .height(70.dp)
                                .shadow(2.dp, shape = RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (!isKeyEnabled) {
                                        BlackKeyColor.copy(alpha = 0.35f)
                                    } else {
                                        BlackKeyColor
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black,
                                    shape = RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                                )
                                .clickable(enabled = isKeyEnabled) {
                                    HapticManager.tick(view)
                                    val newFreq = calculateFrequency(noteIdx, saOctave)
                                    onTonicChange(newFreq, name, saOctave)
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) {
                                    Color.Black
                                } else if (!isKeyEnabled) {
                                    Color.White.copy(alpha = 0.25f)
                                } else {
                                    Color.White.copy(alpha = 0.7f)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
