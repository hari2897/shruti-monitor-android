package com.shrutimonitor.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.R
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.SecondaryViolet
import com.shrutimonitor.app.ui.theme.TextPrimary
import com.shrutimonitor.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Premium Splash Screen. Animates a glowing audio waveform drawing left-to-right.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Progress of the drawing waveform (0.0 to 1.0)
    val drawProgress = remember { Animatable(0f) }
    
    // Logo animation properties (fade in & scale up)
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        // Delay slightly, then animate logo fade-in & scale-up together with the waveform
        delay(200)
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = EaseInOut)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = EaseInOut)
            )
        }
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = EaseInOut)
        )
        // Hold for a moment, then transition to home
        delay(600)
        onSplashFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo Image
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer(
                    alpha = logoAlpha.value,
                    scaleX = logoScale.value,
                    scaleY = logoScale.value
                )
                .clip(RoundedCornerShape(24.dp))
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // App Title
        Text(
            text = "SHRUTI MONITOR",
            fontSize = 32.sp,
            fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(letterSpacing = 4.sp)
        )

        Text(
            text = "Just Intonation Tuner & Instruments",
            fontSize = 13.sp,
            color = TextSecondary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.sp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Animated Waveform Draw Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f
                val amplitude = height * 0.4f
                val cycles = 3.5f

                val path = Path()
                path.moveTo(0f, centerY)

                val limitX = (width * drawProgress.value).toInt()
                
                for (x in 0..limitX) {
                    val progress = x.toFloat() / width
                    
                    // Waveform = sine wave scaled by envelope (sin(pi * progress))
                    // so it starts/ends cleanly at 0 amplitude.
                    val envelope = sin(PI * progress).toFloat()
                    val y = centerY + amplitude * envelope * sin(2.0 * PI * cycles * progress).toFloat()
                    path.lineTo(x.toFloat(), y)
                }

                val lineBrush = Brush.horizontalGradient(
                    colors = listOf(SecondaryViolet, PrimarySaffron)
                )

                // Draw outer glowing line
                drawPath(
                    path = path,
                    brush = lineBrush,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw sharp inner line
                drawPath(
                    path = path,
                    brush = lineBrush,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

// Inline helper because Box was missing import
@Composable
private fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = modifier, contentAlignment = contentAlignment) {
        content()
    }
}
