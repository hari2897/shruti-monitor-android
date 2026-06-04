package com.shrutimonitor.app.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.ui.theme.BlackKeyColor
import com.shrutimonitor.app.ui.theme.WhiteKeyColor
import com.shrutimonitor.app.util.HapticManager

/**
 * Harmonium-style scrollable piano keyboard supporting multi-touch note triggers.
 *
 * Keys are drawn overlapping in a scrollable horizontal Box.
 * Active keys glow golden. Keys outside the active Raga are dimmed.
 */
@Composable
fun KeyboardView(
    activeSwaras: Set<Int>?,
    pressedKeys: Set<Int>,
    nomenclature: Nomenclature,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    // 3 Octaves (Mandra, Madhya, Tara) starting from noteIndex 12 (Madhya Sa) to 47
    // Total 36 keys (12 * 3)
    val startNote = 12
    val numKeys = 36

    val whiteKeyWidth = 56.dp
    val blackKeyWidth = 36.dp
    val halfBlackWidth = 18.dp

    // Maps chromatic index (0-11) within an octave to white key index (0-6)
    // Returns null if the chromatic index is a black key
    val chromaticToWhiteMap = intArrayOf(0, -1, 1, -1, 2, 3, -1, 4, -1, 5, -1, 6)

    // Maps white key index in an octave back to chromatic index (0-11)
    val whiteToChromaticMap = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    // Calculate number of white keys to render
    // 3 octaves * 7 white keys = 21 white keys
    val totalWhiteKeys = 21

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "🎵 Just Intonation Harmonium (Madhya & Tara)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .horizontalScroll(scrollState)
        ) {
            // 1. First Layer: White Keys
            Row(
                modifier = Modifier.height(240.dp)
            ) {
                for (wk in 0 until totalWhiteKeys) {
                    val octave = wk / 7
                    val wkOffset = wk % 7
                    val swaraIndex = whiteToChromaticMap[wkOffset]
                    val noteIndex = startNote + octave * 12 + swaraIndex
                    
                    val isPressed = pressedKeys.contains(noteIndex)
                    val isInRaga = activeSwaras == null || activeSwaras.contains(swaraIndex)

                    val swara = Swara.fromIndex(swaraIndex)
                    val label = if (nomenclature == Nomenclature.HINDUSTANI) {
                        swara.hindustaniAbbr
                    } else {
                        swara.carnaticAbbr
                    }

                    Box(
                        modifier = Modifier
                            .width(whiteKeyWidth)
                            .height(230.dp)
                            .padding(horizontal = 1.dp)
                            .shadow(2.dp, shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(
                                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else WhiteKeyColor.copy(alpha = if (isInRaga) 1.0f else 0.45f)
                            )
                            .border(1.dp, Color(0xFFD0C9BC), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            .pointerInput(noteIndex) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    HapticManager.tick(view)
                                    onKeyDown(noteIndex)
                                    waitForUpOrCancellation()
                                    onKeyUp(noteIndex)
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = label,
                            color = if (isPressed) MaterialTheme.colorScheme.primary else if (isInRaga) Color.Black else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            // 2. Second Layer: Black Keys
            for (wk in 0 until totalWhiteKeys) {
                val octave = wk / 7
                val wkOffset = wk % 7

                // Black keys are placed after white keys 0, 1, 3, 4, 5 in each octave
                if (wkOffset == 0 || wkOffset == 1 || wkOffset == 3 || wkOffset == 4 || wkOffset == 5) {
                    val swaraIndex = when (wkOffset) {
                        0 -> 1 // Komal Re
                        1 -> 3 // Komal Ga
                        3 -> 6 // Tivra Ma
                        4 -> 8 // Komal Dha
                        else -> 10 // Komal Ni
                    }
                    val noteIndex = startNote + octave * 12 + swaraIndex
                    
                    val isPressed = pressedKeys.contains(noteIndex)
                    val isInRaga = activeSwaras == null || activeSwaras.contains(swaraIndex)

                    val swara = Swara.fromIndex(swaraIndex)
                    val label = if (nomenclature == Nomenclature.HINDUSTANI) {
                        swara.hindustaniAbbr
                    } else {
                        swara.carnaticAbbr
                    }

                    // Position horizontal offset: wk * whiteKeyWidth + whiteKeyWidth - halfBlackWidth
                    val xOffset = whiteKeyWidth * wk + whiteKeyWidth - halfBlackWidth

                    Box(
                        modifier = Modifier
                            .offset(x = xOffset, y = 0.dp)
                            .width(blackKeyWidth)
                            .height(140.dp)
                            .shadow(3.dp, shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(
                                if (isPressed) MaterialTheme.colorScheme.primary
                                else BlackKeyColor.copy(alpha = if (isInRaga) 1.0f else 0.40f)
                            )
                            .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .pointerInput(noteIndex) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    HapticManager.tick(view)
                                    onKeyDown(noteIndex)
                                    waitForUpOrCancellation()
                                    onKeyUp(noteIndex)
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = label,
                            color = if (isPressed) Color.Black else if (isInRaga) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
