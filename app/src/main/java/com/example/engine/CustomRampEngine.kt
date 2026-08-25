package com.example.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Custom Color & Luminance Ramp Engine for the 2.5D Isometric ASCII Renderer.
 *
 * Provides:
 * - Quantized luminance curves and stepped tonal ramps
 * - Custom chromatic color palettes (Cyber Neon, Toxic Hazard, Phosphor, Amber, Thermal, Cold Steel)
 * - ASCII character density ramps matching tonal gradients
 * - Edge isolation threshold and tint parameters
 */
object CustomRampEngine {

    enum class RampType(
        val displayName: String,
        val description: String,
        val densityRamp: String,
        val quantizationSteps: Float,
        val shadowTint: Triple<Float, Float, Float>,
        val highlightTint: Triple<Float, Float, Float>,
        val edgeTint: Triple<Float, Float, Float>,
        val gamma: Float = 1.0f
    ) {
        CYBER_NEON(
            displayName = "Cyber Neon",
            description = "High-voltage synthwave with magenta/cyan spectral highlights",
            densityRamp = " .:-=+*#%@█",
            quantizationSteps = 6.0f,
            shadowTint = Triple(0.06f, 0.02f, 0.12f),     // Deep violet shadow
            highlightTint = Triple(0.15f, 0.95f, 0.90f),  // Electric cyan highlight
            edgeTint = Triple(1.0f, 0.20f, 0.70f),       // Hot neon pink edge
            gamma = 0.9f
        ),

        TOXIC_HAZARD(
            displayName = "Toxic Hazard",
            description = "Biohazard chemical palette with radioactive phosphor ramps",
            densityRamp = " .'`^:~+=*#@█",
            quantizationSteps = 5.0f,
            shadowTint = Triple(0.02f, 0.08f, 0.03f),     // Deep viridian shadow
            highlightTint = Triple(0.40f, 1.0f, 0.25f),   // Toxic lime highlight
            edgeTint = Triple(0.85f, 1.0f, 0.10f),       // Acid yellow edge
            gamma = 0.85f
        ),

        PHOSPHOR_GREEN(
            displayName = "P1 Phosphor CRT",
            description = "Monochrome military green CRT phosphor tube response",
            densityRamp = " .:-=+*#%@█",
            quantizationSteps = 8.0f,
            shadowTint = Triple(0.01f, 0.04f, 0.02f),     // Cathode black-green
            highlightTint = Triple(0.20f, 0.98f, 0.40f),  // P1 Phosphor peak
            edgeTint = Triple(0.45f, 1.0f, 0.60f),       // Saturated green contour
            gamma = 1.1f
        ),

        AMBER_TERMINAL(
            displayName = "Amber Cathode",
            description = "Warm retro amber terminal phosphor with soft glow ramps",
            densityRamp = " .:-=+*#%@█",
            quantizationSteps = 8.0f,
            shadowTint = Triple(0.05f, 0.02f, 0.0f),      // Dark amber soot
            highlightTint = Triple(1.0f, 0.72f, 0.15f),   // Bright amber glow
            edgeTint = Triple(1.0f, 0.90f, 0.40f),       // Incandescent gold edge
            gamma = 1.0f
        ),

        COLD_STEEL(
            displayName = "Cold Titanium",
            description = "Brutal industrial slate to crisp titanium white ramps",
            densityRamp = " .·:;+=xX#█",
            quantizationSteps = 7.0f,
            shadowTint = Triple(0.05f, 0.08f, 0.14f),     // Deep navy shadow
            highlightTint = Triple(0.80f, 0.90f, 1.0f),   // Titanium white-blue
            edgeTint = Triple(0.35f, 0.75f, 1.0f),       // Cyan edge line
            gamma = 0.95f
        ),

        THERMAL_HEATMAP(
            displayName = "Infrared Thermal",
            description = "Multi-band thermal gradient from cold indigo to white hot",
            densityRamp = " .:-=+*#%@█",
            quantizationSteps = 10.0f,
            shadowTint = Triple(0.08f, 0.0f, 0.25f),      // Infrared deep purple
            highlightTint = Triple(1.0f, 0.95f, 0.80f),   // White hot core
            edgeTint = Triple(1.0f, 0.40f, 0.0f),        // Thermal orange rim
            gamma = 0.8f
        ),

        MONOCHROME_STARK(
            displayName = "Stark Monochrome",
            description = "High-contrast ink-and-paper with extreme edge isolation",
            densityRamp = " .:+*#@█",
            quantizationSteps = 4.0f,
            shadowTint = Triple(0.02f, 0.02f, 0.02f),     // Void black
            highlightTint = Triple(0.98f, 0.98f, 0.98f),  // Pure paper white
            edgeTint = Triple(1.0f, 1.0f, 1.0f),         // Sharp white outline
            gamma = 1.2f
        );

        companion object {
            fun fromIndex(index: Int): RampType {
                val values = values()
                return values[index.coerceIn(0, values.size - 1)]
            }
        }
    }

    /**
     * Evaluates a custom luminance ramp for a given normalized light input (0.0 to 1.0).
     * Applies gamma curve and step quantization.
     */
    fun evaluateRamp(inputLuminance: Float, ramp: RampType): Float {
        val clamped = inputLuminance.coerceIn(0.0f, 2.0f)
        val normalized = min(1.0f, clamped)
        val gammaCorrected = normalized.pow(ramp.gamma)

        return if (ramp.quantizationSteps > 0.0f) {
            val step = floor(gammaCorrected * ramp.quantizationSteps + 0.5f) / ramp.quantizationSteps
            step.coerceIn(0.0f, 1.0f)
        } else {
            gammaCorrected
        }
    }

    /**
     * Maps an evaluated luminance level to the corresponding character in the custom ASCII density ramp.
     */
    fun mapLuminanceToGlyph(luminance: Float, ramp: RampType): Char {
        val rampChars = ramp.densityRamp
        if (rampChars.isEmpty()) return ' '
        val eval = evaluateRamp(luminance, ramp)
        val idx = (eval * (rampChars.length - 1)).toInt().coerceIn(0, rampChars.length - 1)
        return rampChars[idx]
    }

    /**
     * Interpolates color stops along the custom ramp for a given photon intensity.
     */
    fun evaluateRampColor(
        intensity: Float,
        baseR: Float,
        baseG: Float,
        baseB: Float,
        ramp: RampType
    ): Triple<Float, Float, Float> {
        val eval = evaluateRamp(intensity, ramp)

        val (shR, shG, shB) = ramp.shadowTint
        val (hiR, hiG, hiB) = ramp.highlightTint

        val rampR = shR + (hiR - shR) * eval
        val rampG = shG + (hiG - shG) * eval
        val rampB = shB + (hiB - shB) * eval

        val finalR = baseR * rampR * (1.0f + eval * 0.5f)
        val finalG = baseG * rampG * (1.0f + eval * 0.5f)
        val finalB = baseB * rampB * (1.0f + eval * 0.5f)

        return Triple(finalR.coerceIn(0f, 1f), finalG.coerceIn(0f, 1f), finalB.coerceIn(0f, 1f))
    }
}
