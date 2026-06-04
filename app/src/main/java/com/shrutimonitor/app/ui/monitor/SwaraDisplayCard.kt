package com.shrutimonitor.app.ui.monitor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.ui.theme.InTuneMint
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.util.HapticManager
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Compact premium animated card showing the currently detected pitch/swara.
 *
 * Implements:
 * - Breathing scale animation (when active) using spring physics
 * - Radial golden ambient glow behind the card
 * - Fixed card height of 72dp to prevent layout shifting
 * - Row-based layout for neat spacing
 * - Color-coded cent deviation display with smooth transitions
 * - TunerProgressBar with green in-tune zone
 * - Sustained in-tune haptic feedback after hold threshold
 */
@Composable
fun SwaraDisplayCard(
    swaraAbbr: String?,
    swaraFullName: String?,
    swaraIndex: Int?,
    saptakName: String?,
    centDeviation: Float,
    isMicActive: Boolean,
    saNoteName: String,
    saOctave: Int,
    inTuneTolerance: Float = 10f,
    modifier: Modifier = Modifier
) {
    // Calculate Western note name corresponding to the active pitch
    val westernNoteName = if (swaraIndex != null && saptakName != null) {
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val saIndex = noteNames.indexOf(saNoteName).coerceAtLeast(0)
        val absoluteSwaraIndex = saIndex + swaraIndex
        val noteName = noteNames[absoluteSwaraIndex % 12]
        val octaveShift = when (saptakName) {
            "MANDRA" -> -1
            "TARA" -> 1
            else -> 0
        }
        val octave = saOctave + (absoluteSwaraIndex / 12) + octaveShift
        "$noteName$octave"
    } else {
        null
    }

    val view = LocalView.current

    // ── In-Tune Sustained Hold Detection ──
    val isInTune = swaraAbbr != null && abs(centDeviation) <= inTuneTolerance
    var inTuneStartTime by remember { mutableLongStateOf(0L) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    val holdThresholdMs = 1500L // 1.5 seconds sustained hold

    // Progress (0f to 1f) for the sustained hold
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(isInTune, swaraAbbr) {
        if (isInTune) {
            if (inTuneStartTime == 0L) {
                inTuneStartTime = System.currentTimeMillis()
                hasTriggeredHaptic = false
            }
            // Animate the progress bar fill
            holdProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = holdThresholdMs.toInt())
            )
            // If we reach 1f, the user sustained the note
            if (!hasTriggeredHaptic) {
                hasTriggeredHaptic = true
                HapticManager.heavyClick(view)
            }
        } else {
            inTuneStartTime = 0L
            hasTriggeredHaptic = false
            holdProgress.snapTo(0f)
        }
    }

    // Breathing loop toggled state
    var breatheIn by remember { mutableStateOf(false) }
    LaunchedEffect(isMicActive, swaraAbbr) {
        if (isMicActive && swaraAbbr != null) {
            while (true) {
                breatheIn = !breatheIn
                delay(1200)
            }
        } else {
            breatheIn = false
        }
    }

    // Breathing glow scale animation with spring physics
    val scaleFactor by animateFloatAsState(
        targetValue = if (breatheIn) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "scale"
    )

    // Cent deviation color coding
    val devAbs = abs(centDeviation)
    val targetColor = when {
        swaraAbbr == null -> MaterialTheme.colorScheme.onSurfaceVariant
        devAbs <= inTuneTolerance * 0.8f -> InTuneMint
        devAbs <= inTuneTolerance * 2.2f -> MaterialTheme.colorScheme.primary
        else -> OutOfTuneCoral
    }

    val centColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "centColor"
    )

    // Draw ambient glow behind the card using drawBehind
    val primaryColor = MaterialTheme.colorScheme.primary
    val glowBrush = Brush.radialGradient(
        colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent),
        center = Offset.Unspecified,
        radius = 240f
    )

    // Celebration glow: golden burst when hold completes
    val celebrationGlowAlpha by animateFloatAsState(
        targetValue = if (hasTriggeredHaptic) 0.25f else 0f,
        animationSpec = tween(400),
        label = "celebration"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (isMicActive && swaraAbbr != null) {
                    drawCircle(
                        brush = glowBrush,
                        radius = size.width * 0.45f,
                        center = center
                    )
                }
                // Celebration golden burst
                if (celebrationGlowAlpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                InTuneMint.copy(alpha = celebrationGlowAlpha),
                                Color.Transparent
                            ),
                            radius = size.width * 0.5f
                        ),
                        radius = size.width * 0.5f,
                        center = center
                    )
                }
            }
            .scale(scaleFactor),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Part: Swara Abbreviation and Western Note (with vertical slide transition)
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        AnimatedContent(
                            targetState = Pair(swaraAbbr ?: "--", westernNoteName),
                            transitionSpec = {
                                if (targetState.first != initialState.first) {
                                    (slideInVertically { height -> height } + fadeIn(tween(150))) togetherWith
                                            slideOutVertically { height -> -height } + fadeOut(tween(150))
                                } else {
                                    fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                                }.using(
                                    SizeTransform(clip = false)
                                )
                            },
                            label = "swaraAbbrChange"
                        ) { (targetAbbr, noteName) ->
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = targetAbbr,
                                    fontSize = 36.sp,
                                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (swaraAbbr != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                                if (noteName != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "($noteName)",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Middle Part: Swara Full Name
                    Text(
                        text = swaraFullName ?: if (isMicActive) "Listening..." else "Mic Inactive",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    // Right Part: Cent Deviation
                    if (swaraAbbr != null) {
                        val sign = if (centDeviation >= 0) "+" else ""
                        val devText = "$sign${centDeviation.toInt()}¢"
                        Text(
                            text = devText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = centColor,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(60.dp)
                        )
                    } else {
                        Text(
                            text = "0¢",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }

                // ── Tuner Progress Bar ──
                TunerProgressBar(
                    centDeviation = centDeviation,
                    inTuneTolerance = inTuneTolerance,
                    isActive = swaraAbbr != null,
                    holdProgress = holdProgress.value,
                    hasCompleted = hasTriggeredHaptic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Horizontal tuner bar showing ±50 cent range.
 *
 * Features:
 * - Green target zone sized to [inTuneTolerance]
 * - Animated indicator dot at current deviation
 * - Fill animation when sustaining in-tune
 */
@Composable
private fun TunerProgressBar(
    centDeviation: Float,
    inTuneTolerance: Float,
    isActive: Boolean,
    holdProgress: Float,
    hasCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val tunerRange = 50f // ±50 cents visible range

    // Animate the indicator position smoothly
    val animatedPosition by animateFloatAsState(
        targetValue = if (isActive) (centDeviation.coerceIn(-tunerRange, tunerRange) / tunerRange) else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "tunerPos"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val inTuneColor = InTuneMint
    val indicatorColor by animateColorAsState(
        targetValue = when {
            !isActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            hasCompleted -> InTuneMint
            abs(centDeviation) <= inTuneTolerance -> InTuneMint.copy(alpha = 0.8f)
            else -> PrimarySaffron
        },
        animationSpec = tween(200),
        label = "indicatorColor"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width
        val barHeight = size.height
        val cornerRadius = barHeight / 2f
        val centerX = barWidth / 2f

        // 1. Background track
        drawRoundRect(
            color = surfaceColor,
            cornerRadius = CornerRadius(cornerRadius),
            size = Size(barWidth, barHeight)
        )

        // 2. Green target zone
        val toleranceFraction = inTuneTolerance / tunerRange
        val zoneWidth = barWidth * toleranceFraction
        val zoneLeft = centerX - zoneWidth / 2f
        drawRoundRect(
            color = inTuneColor.copy(alpha = 0.2f),
            topLeft = Offset(zoneLeft, 0f),
            size = Size(zoneWidth, barHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )

        // 3. Hold progress fill (grows from center outward within the green zone)
        if (holdProgress > 0f && isActive) {
            val fillWidth = zoneWidth * holdProgress
            val fillLeft = centerX - fillWidth / 2f
            drawRoundRect(
                color = inTuneColor.copy(alpha = 0.45f),
                topLeft = Offset(fillLeft, 0f),
                size = Size(fillWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // 4. Center tick mark
        drawLine(
            color = inTuneColor.copy(alpha = 0.5f),
            start = Offset(centerX, 1f),
            end = Offset(centerX, barHeight - 1f),
            strokeWidth = 1.5f
        )

        // 5. Indicator dot
        if (isActive) {
            val dotX = centerX + (animatedPosition * barWidth / 2f)
            val dotRadius = barHeight * 0.42f
            // Glow
            drawCircle(
                color = indicatorColor.copy(alpha = 0.3f),
                radius = dotRadius * 1.6f,
                center = Offset(dotX.coerceIn(dotRadius, barWidth - dotRadius), barHeight / 2f)
            )
            // Solid dot
            drawCircle(
                color = indicatorColor,
                radius = dotRadius,
                center = Offset(dotX.coerceIn(dotRadius, barWidth - dotRadius), barHeight / 2f)
            )
        }
    }
}
