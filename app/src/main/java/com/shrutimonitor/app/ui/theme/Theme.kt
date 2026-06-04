package com.shrutimonitor.app.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.shrutimonitor.app.data.AppTheme
import com.shrutimonitor.app.data.Nomenclature

// Custom dark Material 3 theme colors for the Deep Indigo / Saffron theme
private val DarkColorScheme = darkColorScheme(
    primary = PrimarySaffron,
    secondary = SecondaryViolet,
    tertiary = PrimarySaffron,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceContainerLowest = GraphBg,
    surfaceContainerLow = SurfaceDark,
    surfaceContainerHigh = SurfaceLightDark,
    error = OutOfTuneCoral,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = Color.White,
    surfaceVariant = SurfaceLightDark,
    onSurfaceVariant = TextSecondary,
    outline = TextDisabled,
    primaryContainer = Color(0xFF3D2E00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondaryContainer = Color(0xFF1D1B3F),
    onSecondaryContainer = Color(0xFFE0DEFF),
    tertiaryContainer = Color(0xFF3D2E00),
    onTertiaryContainer = Color(0xFFFFDDB3)
)

// AMOLED Black theme colors (uses pure black for maximum contrast and battery savings)
private val AmoledColorScheme = darkColorScheme(
    primary = PrimarySaffron,
    secondary = SecondaryViolet,
    tertiary = PrimarySaffron,
    background = Color.Black,
    surface = Color(0xFF0B0B10), // Extremely dark grey
    surfaceContainerLowest = Color(0xFF050508),
    surfaceContainerLow = Color(0xFF0B0B10),
    surfaceContainerHigh = Color(0xFF12121A),
    error = OutOfTuneCoral,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = Color.White,
    surfaceVariant = Color(0xFF12121A),
    onSurfaceVariant = TextSecondary,
    outline = TextDisabled,
    primaryContainer = Color(0xFF3D2E00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondaryContainer = Color(0xFF1D1B3F),
    onSecondaryContainer = Color(0xFFE0DEFF),
    tertiaryContainer = Color(0xFF3D2E00),
    onTertiaryContainer = Color(0xFFFFDDB3)
)

// Hindustani accent overrides — warm saffron/gold tertiary emphasis
private val HindustaniTertiaryOverrides = mapOf(
    "tertiary" to PrimarySaffron,
    "tertiaryContainer" to Color(0xFF3D2E00),
    "onTertiary" to Color.Black,
    "onTertiaryContainer" to Color(0xFFFFDDB3)
)

// Carnatic accent overrides — cool violet/indigo secondary emphasis
private val CarnaticSecondaryOverrides = mapOf(
    "secondary" to SecondaryViolet,
    "secondaryContainer" to Color(0xFF1D1B3F),
    "onSecondary" to Color.White,
    "onSecondaryContainer" to Color(0xFFE0DEFF)
)

/**
 * Custom dark Material 3 theme for the Shruti Monitor app.
 *
 * Dynamically switches theme styles (Deep Indigo, AMOLED Black, or Dynamic System colors)
 * based on the user's preference. Additionally applies Hindustani (tertiary warm) or
 * Carnatic (secondary cool) color role differentiation based on the active nomenclature.
 *
 * @param appTheme The user's selected theme: DARK, AMOLED, or AUTO (dynamic).
 * @param nomenclature The active nomenclature system affecting color accents.
 */
@Composable
fun ShrutiMonitorTheme(
    appTheme: AppTheme = AppTheme.AUTO,
    nomenclature: Nomenclature = Nomenclature.HINDUSTANI,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when (appTheme) {
        AppTheme.AMOLED -> AmoledColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.AUTO -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                DarkColorScheme
            }
        }
    }

    // Apply nomenclature-specific color role overrides with smooth transitions
    val targetTertiary by animateColorAsState(
        targetValue = when (nomenclature) {
            Nomenclature.HINDUSTANI -> PrimarySaffron
            Nomenclature.CARNATIC -> baseScheme.tertiary
        },
        animationSpec = tween(500),
        label = "tertiaryColor"
    )

    val targetSecondary by animateColorAsState(
        targetValue = when (nomenclature) {
            Nomenclature.CARNATIC -> SecondaryViolet
            Nomenclature.HINDUSTANI -> baseScheme.secondary
        },
        animationSpec = tween(500),
        label = "secondaryColor"
    )

    val colorScheme = baseScheme.copy(
        tertiary = targetTertiary,
        secondary = targetSecondary,
        tertiaryContainer = when (nomenclature) {
            Nomenclature.HINDUSTANI -> Color(0xFF3D2E00)
            Nomenclature.CARNATIC -> baseScheme.tertiaryContainer
        },
        secondaryContainer = when (nomenclature) {
            Nomenclature.CARNATIC -> Color(0xFF1D1B3F)
            Nomenclature.HINDUSTANI -> baseScheme.secondaryContainer
        }
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
