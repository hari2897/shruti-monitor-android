package com.shrutimonitor.app.ui.monitor

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

/**
 * Hardware-accelerated Android Graphics Shading Language (AGSL) shaders for Shruti Monitor v2.0.
 * Runs natively on the GPU at 60/120fps on Android 13+ (including Android 15 / EvolutionX).
 */
object AgslPitchShaders {

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @Language("AGSL")
    const val AURORA_RESONANCE_SRC = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uRms;
        uniform float uInTuneFactor; // 1.0 = dead-on Swarasthana, 0.0 = off
        uniform float uPitchY;       // Normalized Y position of current pitch [0..1]
        uniform float uBrightness;   // Harmonic overtone richness [0..1]

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
                mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
                u.y
            );
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            
            // Deep obsidian base
            float3 baseBg = float3(0.031, 0.031, 0.055);
            
            // Acoustic wave distortion centered around active pitch Y
            float distY = abs(uv.y - uPitchY);
            float wave = sin(uv.x * 8.0 - uTime * 2.4 + noise(uv * 3.5 + uTime * 0.3) * 3.0);
            float band = exp(-distY * (6.5 - uRms * 2.5)) * (0.15 + uRms * 0.55);

            // Harmonic standing-wave interference ripples
            float harmonicRipple = sin((uv.y - uPitchY) * (28.0 + uBrightness * 36.0) - uTime * 4.0) * 0.5 + 0.5;
            float aura = band * (0.65 + 0.35 * wave * harmonicRipple);

            // Color palette: Saffron Gold, Royal Violet, and In-Tune Mint Emerald
            float3 saffron = float3(0.96, 0.65, 0.14);
            float3 violet  = float3(0.48, 0.17, 0.75);
            float3 mint    = float3(0.00, 0.90, 0.46);

            float3 activeColor = mix(mix(violet, saffron, uv.x), mint, uInTuneFactor * 0.75);
            
            // Subtle right-edge live horizon glow
            float horizon = exp(-(1.0 - uv.x) * 22.0) * (0.03 + uRms * 0.12);
            float3 finalColor = baseBg + activeColor * aura + mix(saffron, mint, uInTuneFactor) * horizon;

            return half4(half3(clamp(finalColor, 0.0, 1.0)), 1.0);
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createAuroraShader(): RuntimeShader = RuntimeShader(AURORA_RESONANCE_SRC)
}
