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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.audio.GamakaMetrics
import com.shrutimonitor.app.audio.GamakaType
import com.shrutimonitor.app.ui.theme.InTuneMint
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.util.HapticManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

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
    shrutiName: String = "",
    gamakaMetrics: GamakaMetrics = GamakaMetrics(),
    frequencyHz: Float = 0f,
    modifier: Modifier = Modifier
) {
    val westernNoteName = if (swaraIndex != null && saptakName != null) {
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val saIndex = noteNames.indexOf(saNoteName).coerceAtLeast(0)
        val absoluteSwaraIndex = saIndex + swaraIndex
        val noteName = noteNames[absoluteSwaraIndex % 12]
        val octaveShift = when (saptakName) {
            "ATI_MANDRA" -> -2
            "MANDRA" -> -1
            "MADHYA" -> 0
            "TARA" -> 1
            "ATI_TARA" -> 2
            else -> 0
        }
        val octave = saOctave + (absoluteSwaraIndex / 12) + octaveShift
        "$noteName$octave"
    } else {
        null
    }

    val view = LocalView.current
    val isInTune = swaraAbbr != null && abs(centDeviation) <= inTuneTolerance
    var inTuneStartTime by remember { mutableLongStateOf(0L) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    val holdThresholdMs = 1500L
    val holdProgress = remember { Animatable(0f) }

    // Continuous phase-strobe accumulator driven by centDeviation
    var strobePhaseDeg by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isMicActive, swaraAbbr != null) {
        if (!isMicActive || swaraAbbr == null) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    val effectiveDev = if (abs(centDeviation) <= inTuneTolerance * 0.45f) 0f else centDeviation
                    strobePhaseDeg = (strobePhaseDeg + effectiveDev * 6.5f * dt) % 360f
                }
                lastNanos = nanos
            }
        }
    }

    LaunchedEffect(isInTune, swaraAbbr) {
        if (isInTune) {
            if (inTuneStartTime == 0L) {
                inTuneStartTime = System.currentTimeMillis()
                hasTriggeredHaptic = false
            }
            holdProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = holdThresholdMs.toInt())
            )
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

    val scaleFactor by animateFloatAsState(
        targetValue = if (breatheIn) 1.015f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "scale"
    )

    val devAbs = abs(centDeviation)
    val targetColor = when {
        swaraAbbr == null -> MaterialTheme.colorScheme.onSurfaceVariant
        devAbs <= inTuneTolerance * 0.8f -> InTuneMint
        devAbs <= inTuneTolerance * 2.2f -> MaterialTheme.colorScheme.primary
        else -> OutOfTuneCoral
    }

    val centColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(250),
        label = "centColor"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val glowBrush = Brush.radialGradient(
        colors = listOf(primaryColor.copy(alpha = 0.14f), Color.Transparent),
        center = Offset.Unspecified,
        radius = 260f
    )

    val celebrationGlowAlpha by animateFloatAsState(
        targetValue = if (hasTriggeredHaptic) 0.28f else 0f,
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
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isInTune) InTuneMint.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Phase-Strobe Ring + Swara Abbr
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val strokePx = 2.5.dp.toPx()
                                drawCircle(
                                    color = centColor.copy(alpha = 0.18f),
                                    style = Stroke(width = strokePx)
                                )
                                if (swaraAbbr != null) {
                                    val segments = 8
                                    val sweep = 22f
                                    for (s in 0 until segments) {
                                        val startAngle = strobePhaseDeg + s * (360f / segments)
                                        drawArc(
                                            color = centColor.copy(alpha = 0.85f),
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                            }

                            AnimatedContent(
                                targetState = swaraAbbr ?: "--",
                                transitionSpec = {
                                    (slideInVertically { h -> h / 2 } + fadeIn(tween(120))) togetherWith
                                        (slideOutVertically { h -> -h / 2 } + fadeOut(tween(120))) using
                                        SizeTransform(clip = false)
                                },
                                label = "swaraAbbr"
                            ) { targetAbbr ->
                                Text(
                                    text = targetAbbr,
                                    fontSize = 24.sp,
                                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (swaraAbbr != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        if (westernNoteName != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = westernNoteName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                if (frequencyHz > 0f) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f Hz", frequencyHz),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Middle: Swara Full Name + 22-Shruti & Gamaka Badge
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = swaraFullName ?: if (isMicActive) "Listening..." else "Mic Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        if (swaraAbbr != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            val gamakaText = when (gamakaMetrics.type) {
                                GamakaType.KAMPITA -> String.format(
                                    Locale.US,
                                    "Kampita %.1fHz (±%.0f¢)",
                                    gamakaMetrics.oscillationRateHz,
                                    gamakaMetrics.excursionCents / 2f
                                )
                                GamakaType.JAARU -> "Jaaru / Meend Glide"
                                GamakaType.SPURITA -> "Spurita Accent"
                                GamakaType.STEADY -> if (shrutiName.isNotEmpty()) shrutiName else "Steady Swara"
                            }
                            Text(
                                text = gamakaText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (gamakaMetrics.type == GamakaType.KAMPITA) PrimarySaffron else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right: Cent Deviation
                    if (swaraAbbr != null) {
                        val sign = if (centDeviation >= 0) "+" else ""
                        val devText = "$sign${centDeviation.toInt()}¢"
                        Text(
                            text = devText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = centColor,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp)
                        )
                    } else {
                        Text(
                            text = "0¢",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp)
                        )
                    }
                }

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

@Composable
private fun TunerProgressBar(
    centDeviation: Float,
    inTuneTolerance: Float,
    isActive: Boolean,
    holdProgress: Float,
    hasCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val tunerRange = 50f
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

        drawRoundRect(
            color = surfaceColor,
            cornerRadius = CornerRadius(cornerRadius),
            size = Size(barWidth, barHeight)
        )

        val toleranceFraction = inTuneTolerance / tunerRange
        val zoneWidth = barWidth * toleranceFraction
        val zoneLeft = centerX - zoneWidth / 2f
        drawRoundRect(
            color = inTuneColor.copy(alpha = 0.2f),
            topLeft = Offset(zoneLeft, 0f),
            size = Size(zoneWidth, barHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )

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

        drawLine(
            color = inTuneColor.copy(alpha = 0.5f),
            start = Offset(centerX, 1f),
            end = Offset(centerX, barHeight - 1f),
            strokeWidth = 1.5f
        )

        if (isActive) {
            val dotX = centerX + (animatedPosition * barWidth / 2f)
            val dotRadius = barHeight * 0.42f
            drawCircle(
                color = indicatorColor.copy(alpha = 0.3f),
                radius = dotRadius * 1.6f,
                center = Offset(dotX.coerceIn(dotRadius, barWidth - dotRadius), barHeight / 2f)
            )
            drawCircle(
                color = indicatorColor,
                radius = dotRadius,
                center = Offset(dotX.coerceIn(dotRadius, barWidth - dotRadius), barHeight / 2f)
            )
        }
    }
}
