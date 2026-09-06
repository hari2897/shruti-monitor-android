package com.shrutimonitor.app.ui.monitor

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.audio.PitchRingBuffer
import com.shrutimonitor.app.audio.VisibleWindowScratch
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.TextSecondary
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Custom 60/120 FPS Canvas for real-time scrolling pitch trace visualization.
 *
 * Implements:
 * - Decoupled rendering: frame clock driven at display's native refresh rate (60/120Hz)
 * - Single-lock snapshot via copyVisibleWindow for lock-free draw
 * - Deadband auto-follow (2-cent threshold) to prevent vibrato hunting
 * - Continuous horizontal glide mapped to monotonic uptime
 * - Draw-phase invalidation only (0 recompositions/sec of PitchGraph body)
 * - Zero allocations per frame in the draw hot path
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

    // Monotonic frame time (ms) driven by Choreographer withFrameNanos.
    // NOTE: Read ONLY inside the Canvas draw lambda to avoid recomposing PitchGraph body!
    val frameTimeState = remember { mutableLongStateOf(0L) }

    // Smooth centering for auto-follow (visual cents).
    // NOTE: Read ONLY inside Canvas draw lambda!
    val centerYState = remember { mutableFloatStateOf(0f) }

    // Monotonic timestamp when live mode was paused/switched to scroll
    var freezeTimeMs by remember { mutableLongStateOf(0L) }

    // Track whether first drag has happened to trigger onScrollStart
    var hasTriggeredScrollExit by remember { mutableStateOf(false) }

    // Reset scroll-exit flag and freeze/unfreeze time when switching live modes
    LaunchedEffect(isLive) {
        if (isLive) {
            hasTriggeredScrollExit = false
        } else {
            freezeTimeMs = android.os.SystemClock.uptimeMillis()
        }
    }

    // Native display frame-clock loop (60 / 120 Hz)
    // Updates frameTimeState and centerYState (with 2-cent deadband)
    LaunchedEffect(isLive, autoFollow, saFrequency) {
        if (!isLive) return@LaunchedEffect

        var currentCenterY = centerYState.floatValue
        var targetCenterY = currentCenterY
        var lastTargetCenterY = currentCenterY

        while (true) {
            withFrameNanos { frameNanos ->
                val nowMs = frameNanos / 1_000_000L
                frameTimeState.longValue = nowMs

                if (autoFollow && saFrequency > 0f) {
                    val voicedFreq = pitchHistory.lastVoicedFreq()
                    if (voicedFreq > 0f) {
                        val centsFromSa = 1200.0 * kotlin.math.log2(voicedFreq.toDouble() / saFrequency.toDouble())
                        val visualCents = Swara.actualToVisualCents(centsFromSa).toFloat()
                        // 2-cent deadband prevents hunting/jitter on natural vocal vibrato
                        if (kotlin.math.abs(visualCents - lastTargetCenterY) > 2f) {
                            lastTargetCenterY = visualCents
                            targetCenterY = visualCents
                        }
                    }
                    // Smooth exponential follow (~100ms response time at 60Hz)
                    currentCenterY += 0.08f * (targetCenterY - currentCenterY)
                    centerYState.floatValue = currentCenterY
                } else if (!autoFollow) {
                    targetCenterY = 0f
                    currentCenterY = 0f
                    centerYState.floatValue = 0f
                }
            }
        }
    }

    // Recomposition verification: monitor that PitchGraph body stays at 0 recompositions/sec during live singing
    var recompositionCount by remember { mutableIntStateOf(0) }
    SideEffect {
        recompositionCount++
    }
    LaunchedEffect(Unit) {
        var lastCount = 0
        while (true) {
            kotlinx.coroutines.delay(2000)
            val current = recompositionCount
            android.util.Log.d("PitchGraphPerf", "PitchGraph recompositions in last 2s: ${current - lastCount} (total: $current)")
            lastCount = current
        }
    }

    // Capture theme colors to avoid calling Composable functions in draw/touch lambdas
    val graphBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

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
    val windowScratch = remember { VisibleWindowScratch(1200) }

    // Precomputed Swara label table for octaves -2..2 and 12 swaras
    // Eliminates string formatting allocations during 60/120Hz rendering
    val precomputedLabels = remember(nomenclature) {
        Array(5) { octIdx ->
            val oct = octIdx - 2
            Array(12) { swaraIdx ->
                val swara = Swara.fromIndex(swaraIdx)
                val abbr = if (nomenclature == Nomenclature.HINDUSTANI) {
                    swara.hindustaniAbbr
                } else {
                    swara.carnaticAbbr
                }
                when (oct) {
                    -1 -> "$abbr."
                    1 -> "'$abbr"
                    -2 -> "$abbr.."
                    2 -> "''$abbr"
                    else -> abbr
                }
            }
        }
    }

    // Brush cache to avoid per-frame allocations
    var cachedBrush by remember { mutableStateOf<Brush?>(null) }
    var cachedBrushWidth by remember { mutableFloatStateOf(0f) }

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
        // Custom Canvas drawing - invalidated on each vsync frame without Composable body recomposition
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val timeWindowMs = (5000 / zoomX).toLong()

            val nowMs = if (isLive) {
                val ft = frameTimeState.longValue
                if (ft == 0L) android.os.SystemClock.uptimeMillis() else ft
            } else {
                freezeTimeMs
            }
            val endTime = if (isLive) nowMs else freezeTimeMs - scrollOffsetX.toLong()
            val startTime = endTime - timeWindowMs

            val centsRange = 1200f
            val heightScale = height / centsRange
            val timeScale = width / timeWindowMs.toFloat()

            val centerY = if (autoFollow) centerYState.floatValue else scrollOffsetY
            val centsMin = centerY - 600f
            val centsMax = centerY + 600f

            // ── DRAW GRID LINES & LABELS ──
            val startOctave = floor(centsMin / 1200.0).toInt()
            val endOctave = ceil(centsMax / 1200.0).toInt()

            textPaint.color = primaryColor.copy(alpha = 0.5f).toArgb()
            textPaint.textSize = textSizePx

            for (oct in startOctave..endOctave) {
                val octIdx = (oct + 2).coerceIn(0, 4)
                for (i in 0..11) {
                    val absoluteCents = oct * 1200.0 + (i * 100.0)

                    if (absoluteCents in centsMin..centsMax) {
                        val y = height - (absoluteCents.toFloat() - centsMin) * heightScale

                        val isInRaga = activeRagaSwaras == null || activeRagaSwaras.contains(i)
                        val lineOpacity = if (isInRaga) {
                            if (i == 0) 0.35f else 0.20f
                        } else {
                            0.05f
                        }
                        val strokeW = if (i == 0 && isInRaga) thickGridStroke else thinGridStroke

                        drawLine(
                            color = primaryColor.copy(alpha = lineOpacity),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = strokeW
                        )

                        if (isInRaga) {
                            val labelText = precomputedLabels[octIdx][i]
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

            // Central Reference "Sa" Indicator (Mundu / Madhya / Tara)
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
            if (saFrequency > 0f) {
                val numPoints = pitchHistory.copyVisibleWindow(startTime, endTime, windowScratch)

                if (numPoints > 0) {
                    tracePath.reset()
                    var hasSegments = false
                    var isSegmentStarted = false

                    val logSa = kotlin.math.ln(saFrequency.toDouble())
                    val ln2Inv = 1200.0 / kotlin.math.ln(2.0)

                    for (i in 0 until numPoints) {
                        val freq = windowScratch.freqs[i]
                        if (freq <= 0f) {
                            isSegmentStarted = false
                            continue
                        }

                        val time = windowScratch.times[i]
                        val x = (time - startTime) * timeScale
                        val centsFromSa = (kotlin.math.ln(freq.toDouble()) - logSa) * ln2Inv
                        val visualCent = Swara.actualToVisualCents(centsFromSa).toFloat()
                        val y = height - (visualCent - centsMin) * heightScale

                        if (!isSegmentStarted) {
                            tracePath.moveTo(x, y)
                            tracePath.lineTo(x + 0.1f, y) // ensure single isolated sample draws as round dot
                            isSegmentStarted = true
                            hasSegments = true
                        } else {
                            tracePath.lineTo(x, y)
                        }
                    }

                    if (hasSegments) {
                        if (cachedBrush == null || cachedBrushWidth != width) {
                            cachedBrushWidth = width
                            cachedBrush = Brush.horizontalGradient(
                                colors = listOf(secondaryColor.copy(alpha = 0.6f), primaryColor),
                                startX = 0f,
                                endX = width
                            )
                        }
                        val traceBrush = cachedBrush!!

                        // 1. Glow trace
                        drawPath(
                            path = tracePath,
                            brush = traceBrush,
                            style = glowStroke,
                            alpha = 0.2f
                        )

                        // 2. Core sharp trace
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
