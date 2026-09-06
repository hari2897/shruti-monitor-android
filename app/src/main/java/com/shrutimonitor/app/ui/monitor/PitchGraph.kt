package com.shrutimonitor.app.ui.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import com.shrutimonitor.app.audio.PitchPoint
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.TextSecondary
import kotlin.math.ceil
import kotlin.math.floor

import com.shrutimonitor.app.audio.PitchRingBuffer

/**
 * Custom 60FPS Canvas for real-time scrolling pitch trace visualization.
 *
 * Implements:
 * - Scrolling timeline (right to left)
 * - Auto-Follow mode which centers the vocal range
 * - Grid lines at Just Intonation positions relative to Sa
 * - Dimmed inactive swaras for Raga filtering
 * - Pinch-to-zoom on X-axis and dragging for panning (both horizontal and vertical)
 * - LIVE indicator and toggle control chips
 */
@Composable
fun PitchGraph(
    pitchHistory: PitchRingBuffer,
    saFrequency: Float,
    activeRagaSwaras: Set<Int>?,
    isLive: Boolean,
    autoFollow: Boolean,
    nomenclature: Nomenclature,
    onLiveClick: () -> Unit,
    onScrollStart: () -> Unit,
    onAutoFollowToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Gesture Zoom & Offset variables
    var zoomX by remember { mutableFloatStateOf(1.0f) }
    var scrollOffsetX by remember { mutableFloatStateOf(0f) } // ms offset from end
    var scrollOffsetY by remember { mutableFloatStateOf(0f) } // cents shift from Sa

    // Smooth centering for auto-follow
    var smoothedCenterCents by remember { mutableFloatStateOf(0f) }

    var lastVoicedFreq by remember { mutableFloatStateOf(0f) }
    for (i in pitchHistory.size - 1 downTo 0) {
        val f = pitchHistory.freqAt(i)
        if (f > 0f) {
            lastVoicedFreq = f
            break
        }
    }
    
    LaunchedEffect(lastVoicedFreq, autoFollow, saFrequency) {
        if (autoFollow && lastVoicedFreq > 0f && saFrequency > 0f) {
            // Smoothly interpolate the center cents position to prevent jitter
            val centsFromSa = 1200.0 * kotlin.math.log2(lastVoicedFreq.toDouble() / saFrequency.toDouble())
            val targetCenter = Swara.actualToVisualCents(centsFromSa).toFloat()
            val alpha = 0.15f
            smoothedCenterCents = smoothedCenterCents + alpha * (targetCenter - smoothedCenterCents)
        } else if (!autoFollow) {
            smoothedCenterCents = 0f
        }
    }

    // Track whether first drag has happened to trigger onScrollStart
    var hasTriggeredScrollExit by remember { mutableStateOf(false) }

    // Reset scroll-exit flag when switching back to live
    LaunchedEffect(isLive) {
        if (isLive) hasTriggeredScrollExit = false
    }

    // Calculate Y-axis range
    val centerY = if (autoFollow) smoothedCenterCents else scrollOffsetY
    val centsMin = centerY - 600f
    val centsMax = centerY + 600f
    val centsRange = 1200f

    // Timeline duration visible on screen (default 5 seconds = 5000ms, stretched by zoom)
    val timeWindowMs = (5000 / zoomX).toLong()

    // Capture theme colors to avoid calling Composable functions in draw/touch lambdas
    val graphBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Precalculate and cache drawing resources outside draw loop
    val density = androidx.compose.ui.platform.LocalDensity.current
    val glowStroke = remember(density) {
        with(density) { Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round) }
    }
    val coreStroke = remember(density) {
        with(density) { Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round) }
    }
    val thickGridStroke = remember(density) { with(density) { 1.5.dp.toPx() } }
    val thinGridStroke = remember(density) { with(density) { 0.8.dp.toPx() } }
    val saLineStroke = remember(density) { with(density) { 2.dp.toPx() } }
    val textSizePx = remember(density) { with(density) { 10.dp.toPx() } }
    val textXPx = remember(density) { with(density) { 8.dp.toPx() } }
    val textYOffsetPx = remember(density) { with(density) { 3.dp.toPx() } }

    val textPaint = remember {
        android.graphics.Paint().apply {
            typeface = android.graphics.Typeface.SANS_SERIF
            isAntiAlias = true
        }
    }
    val tracePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(graphBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Pinch-to-zoom on X axis
                    zoomX = (zoomX * zoom).coerceIn(0.5f, 3.0f)

                    // Any drag exits live mode so user can inspect history
                    if (isLive && (pan.x != 0f || pan.y != 0f) && !hasTriggeredScrollExit) {
                        hasTriggeredScrollExit = true
                        onScrollStart()
                    }

                    // Horizontal scroll: dragging right pulls content right to reveal older data
                    scrollOffsetX = (scrollOffsetX + pan.x * 12 / zoomX).coerceAtLeast(0f)

                    // Vertical scroll (only when auto-follow is off)
                    if (!autoFollow) {
                        scrollOffsetY += pan.y * 1.5f
                    }
                }
            }
    ) {
        // Custom Canvas drawing
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bufferSize = pitchHistory.size
            val latestTime = if (bufferSize > 0) pitchHistory.timeAt(bufferSize - 1) else 0L
            val endTime = if (isLive) latestTime else latestTime - scrollOffsetX.toLong()
            val startTime = endTime - timeWindowMs

            // Precompute frame-level scale factors
            val heightScale = height / centsRange
            val timeScale = width / timeWindowMs.toFloat()

            // ── DRAW GRID LINES & LABELS ──
            val startOctave = floor(centsMin / 1200.0).toInt()
            val endOctave = ceil(centsMax / 1200.0).toInt()

            textPaint.color = primaryColor.copy(alpha = 0.5f).toArgb()
            textPaint.textSize = textSizePx

            for (oct in startOctave..endOctave) {
                for (i in 0..11) {
                    val absoluteCents = oct * 1200.0 + (i * 100.0) // Visually equally spaced swaras

                    if (absoluteCents in centsMin..centsMax) {
                        val y = height - (absoluteCents.toFloat() - centsMin) * heightScale
                        
                        // Check Raga filter
                        val isInRaga = activeRagaSwaras == null || activeRagaSwaras.contains(i)
                        
                        // Pick color intensity based on Raga and whether it is Sa/Pa
                        val lineOpacity = if (isInRaga) {
                            if (i == 0) 0.35f else 0.20f
                        } else {
                            0.05f
                        }
                        val strokeW = if (i == 0 && isInRaga) thickGridStroke else thinGridStroke

                        // Grid horizontal line
                        drawLine(
                            color = primaryColor.copy(alpha = lineOpacity),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = strokeW
                        )

                        // Draw swara abbreviation and octave designation
                        if (isInRaga) {
                            val swara = Swara.fromIndex(i)
                            val swaraAbbr = if (nomenclature == Nomenclature.HINDUSTANI) {
                                swara.hindustaniAbbr
                            } else {
                                swara.carnaticAbbr
                            }
                            
                            val labelText = when (oct) {
                                -1 -> "$swaraAbbr."
                                1 -> "'$swaraAbbr"
                                else -> swaraAbbr
                            }

                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                textXPx,
                                y - textYOffsetPx,
                                textPaint
                            )
                        }
                    }
                }
            }

            // Draw Central Reference "Sa" Indicator (Mundu / Madhya / Tara)
            val saY = height - (0f - centsMin) * heightScale
            if (saY in 0f..height) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.4f),
                    start = Offset(0f, saY),
                    end = Offset(width, saY),
                    strokeWidth = saLineStroke
                )
            }

            // ── DRAW PITCH TRACE ──
            if (bufferSize > 0 && saFrequency > 0f) {
                val firstVisible = pitchHistory.findFirstIndexAtOrAfter(startTime)
                val lastVisible = pitchHistory.findLastIndexAtOrBefore(endTime)

                if (firstVisible <= lastVisible && firstVisible < bufferSize && lastVisible >= 0) {
                    tracePath.reset()
                    var isPathStarted = false

                    val logSa = kotlin.math.ln(saFrequency.toDouble())
                    val ln2Inv = 1200.0 / kotlin.math.ln(2.0)

                    for (i in firstVisible..lastVisible) {
                        val freq = pitchHistory.freqAt(i)
                        if (freq <= 0f) {
                            // Unvoiced gap - breaks the trace
                            isPathStarted = false
                            continue
                        }

                        val time = pitchHistory.timeAt(i)
                        val x = (time - startTime) * timeScale
                        val centsFromSa = (kotlin.math.ln(freq.toDouble()) - logSa) * ln2Inv
                        val visualCent = Swara.actualToVisualCents(centsFromSa).toFloat()
                        val y = height - (visualCent - centsMin) * heightScale

                        if (!isPathStarted) {
                            tracePath.moveTo(x, y)
                            isPathStarted = true
                        } else {
                            tracePath.lineTo(x, y)
                        }
                    }

                    if (isPathStarted) {
                        val traceBrush = Brush.horizontalGradient(
                            colors = listOf(secondaryColor.copy(alpha = 0.6f), primaryColor),
                            startX = 0f,
                            endX = width
                        )

                        // 1. Draw Glow trace (blur effect simulation with thin semi-transparent path)
                        drawPath(
                            path = tracePath,
                            brush = traceBrush,
                            style = glowStroke,
                            alpha = 0.2f
                        )

                        // 2. Draw Sharp core trace
                        drawPath(
                            path = tracePath,
                            brush = traceBrush,
                            style = coreStroke
                        )
                    }
                }
            }
        }

        // ── SLEEK GLASSMORPHIC CONTROL CAPSULE ──
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(
                    color = Color(0xDC0A0A0F), // Sleek, dark capsule background matching app background
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Auto Follow Toggle Item
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (autoFollow) PrimarySaffron.copy(alpha = 0.15f) else Color.Transparent
                    )
                    .clickable { onAutoFollowToggle() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CompassCalibration,
                    contentDescription = null,
                    tint = if (autoFollow) PrimarySaffron else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Follow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (autoFollow) PrimarySaffron else TextSecondary.copy(alpha = 0.8f)
                )
            }

            // Elegant capsule divider
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            // LIVE / SCROLL Toggle Item
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isLive) OutOfTuneCoral.copy(alpha = 0.12f) else Color.Transparent
                    )
                    .clickable {
                        if (!isLive) {
                            scrollOffsetX = 0f
                            onLiveClick()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    // Pulsing Red Recording Dot
                    Canvas(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(8.dp)
                    ) {
                        drawCircle(
                            color = OutOfTuneCoral,
                            radius = size.minDimension / 2f,
                            alpha = pulseAlpha
                        )
                    }
                } else {
                    // Non-live static indicator icon
                    Icon(
                        imageVector = Icons.Default.RadioButtonChecked,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLive) "LIVE" else "SCROLL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) OutOfTuneCoral else TextSecondary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
