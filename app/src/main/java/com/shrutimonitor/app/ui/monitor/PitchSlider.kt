package com.shrutimonitor.app.ui.monitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Swara

/**
 * Custom Canvas-based scrolling horizontal pitch slider (tuner scale).
 *
 * Displays a scrolling octave scale supporting Mandra, Madhya, and Tara octaves
 * and slides smoothly under the thumb using spring animations.
 */
@Composable
fun PitchSlider(
    centsFromSa: Float?,
    activeRagaSwaras: Set<Int>?,
    nomenclature: Nomenclature,
    modifier: Modifier = Modifier
) {
    // 1. Keep track of current active octave for scrolling hysteresis
    var currentOctave by remember { mutableStateOf(0) }

    if (centsFromSa != null) {
        val cents = Swara.actualToVisualCents(centsFromSa.toDouble())
        val thresholdLow = currentOctave * 1200.0 - 50.0
        val thresholdHigh = currentOctave * 1200.0 + 1250.0
        
        if (cents > thresholdHigh) {
            currentOctave = kotlin.math.floor(cents / 1200.0).toInt()
        } else if (cents < thresholdLow) {
            currentOctave = kotlin.math.floor(cents / 1200.0).toInt()
        }
    }

    // 2. Target viewport center cents position
    val targetCenter = currentOctave * 1200.0 + 600.0

    // Animate viewport center smoothly using Spring physics for organic musical feel
    val viewportCenter by animateFloatAsState(
        targetValue = targetCenter.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 80f // Slightly slower stiffness for smooth horizontal slide scrolling
        ),
        label = "viewportCenterCents"
    )

    // 3. Target thumb position on the continuous scale
    val targetCents = if (centsFromSa != null) {
        Swara.actualToVisualCents(centsFromSa.toDouble()).toFloat()
    } else {
        viewportCenter // Default to center of screen when idle
    }

    // Animate the thumb position
    val animatedCents by animateFloatAsState(
        targetValue = targetCents,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 150f
        ),
        label = "thumbSliderPosition"
    )

    // Capture theme colors to avoid accessing Composable functions inside draw scope
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 4. Render Canvas
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 24.dp)
    ) {
        val width = size.width
        val height = size.height
        val padding = 16f
        val trackWidth = width - 2 * padding
        val trackY = height * 0.4f

        // Draw background track line
        drawLine(
            color = outlineColor.copy(alpha = 0.3f),
            start = Offset(padding, trackY),
            end = Offset(width - padding, trackY),
            strokeWidth = 2.dp.toPx()
        )

        // Draw swarasthanas ticks and labels for 5 octaves (-24 to 36 semitones)
        // Visually map them relative to the scrolling viewportCenter
        for (i in -24..36) {
            val tickCents = i * 100.0
            
            // Project cent position to screen coordinates based on animated viewport center
            val ratio = (tickCents - viewportCenter + 600.0) / 1200.0
            val x = padding + ratio.toFloat() * trackWidth

            // Performance: Only draw if within visible canvas bounds
            if (x in -20f..(width + 20f)) {
                val isSa = i % 12 == 0
                val isPa = i % 12 == 7 || i % 12 == -5
                
                val swaraIdx = (i % 12 + 12) % 12
                val swara = Swara.fromIndex(swaraIdx)

                // Check if this swara is in the active raga (Sa is always in the raga)
                val isInRaga = activeRagaSwaras == null || activeRagaSwaras.contains(swaraIdx)
                val opacity = if (isInRaga) 0.85f else 0.20f
                val tickColor = if (isInRaga) primaryColor else outlineColor

                // Draw tick mark
                drawLine(
                    color = tickColor.copy(alpha = opacity),
                    start = Offset(x, trackY - 4.dp.toPx()),
                    end = Offset(x, trackY + 4.dp.toPx()),
                    strokeWidth = (if (isSa || isPa) 2.dp.toPx() else 1.dp.toPx())
                )

                // Draw swara abbreviation label
                val abbr = if (nomenclature == Nomenclature.HINDUSTANI) {
                    swara.hindustaniAbbr
                } else {
                    swara.carnaticAbbr
                }

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = tickColor.copy(alpha = opacity).toArgb()
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.SANS_SERIF
                    }
                    drawText(abbr, x, trackY + 18.dp.toPx(), paint)
                }

                // If this is a Sa tick, draw the floating saptak name above it
                if (isSa) {
                    val saptakLabel = when (i) {
                        -24 -> "Ati-Mandra"
                        -12 -> "Mandra"
                        0 -> "Madhya"
                        12 -> "Tara"
                        24 -> "Ati-Tara"
                        else -> null
                    }
                    if (saptakLabel != null) {
                        drawContext.canvas.nativeCanvas.apply {
                            val labelPaint = android.graphics.Paint().apply {
                                color = onSurfaceVariantColor.copy(alpha = 0.45f).toArgb()
                                textSize = 8.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = android.graphics.Typeface.create(
                                    android.graphics.Typeface.SANS_SERIF,
                                    android.graphics.Typeface.ITALIC
                                )
                            }
                            drawText(saptakLabel, x, trackY - 8.dp.toPx(), labelPaint)
                        }
                    }
                }
            }
        }

        // Draw active glowing thumb representing current pitch position
        if (centsFromSa != null) {
            val thumbRatio = (animatedCents - viewportCenter + 600f) / 1200f
            val thumbX = padding + thumbRatio * trackWidth

            // Performance: Only draw if within screen limits
            if (thumbX in 0f..width) {
                // Glow ring (outer circle)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    radius = 10.dp.toPx(),
                    center = Offset(thumbX, trackY)
                )

                // Inner solid dot
                drawCircle(
                    color = primaryColor,
                    radius = 5.dp.toPx(),
                    center = Offset(thumbX, trackY)
                )

                // Thin border ring for premium contrast
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 5.dp.toPx(),
                    center = Offset(thumbX, trackY),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
