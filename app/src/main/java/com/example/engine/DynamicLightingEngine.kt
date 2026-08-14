package com.example.engine

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Supported Light Source types in the wasteland.
 */
enum class LightType {
    PLAYER_FLASHLIGHT,     // Directional cone with ambient halo
    POINT_TORCH,           // Warm flickering wall/ground torch
    FLARE_EMERGENCY,       // Vivid pulsing chemical emergency flare (red / magenta)
    BIO_LUMINESCENT,       // Eerie green/cyan chemical toxic pool glow
    EXTRACTION_BEACON,     // Bright pulsing gold elevator beacon
    EMP_PULSE              // Electric cyan temporary burst
}

/**
 * Representation of a Dynamic Point or Directional Light Source in grid space.
 */
data class LightSource(
    val id: String,
    val gridX: Float,
    val gridY: Float,
    val elevation: Float = 0f,
    val colorR: Int,       // 0..255
    val colorG: Int,       // 0..255
    val colorB: Int,       // 0..255
    val intensity: Float,  // 0.0 .. 2.0+
    val radius: Float,     // radius in tile grid units
    val type: LightType = LightType.POINT_TORCH,
    val isDirectional: Boolean = false,
    val directionAngleDeg: Float = 0f,
    val coneAngleDeg: Float = 65f,
    val flickerFrequency: Float = 4.0f,
    val flickerIntensity: Float = 0.15f,
    val pulseSpeed: Float = 0f,
    val lifetimeMs: Long = -1L,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Resulting Lighting Calculation for a specific tile or character in grid space.
 */
data class TileLighting(
    val totalIntensity: Float, // Combined scalar brightness (0.0 .. 1.8)
    val colorR: Int,           // Weighted combined Red (0..255)
    val colorG: Int,           // Weighted combined Green (0..255)
    val colorB: Int,           // Weighted combined Blue (0..255)
    val inDirectLight: Boolean,// True if illuminated by at least one active light
    val isFOWHidden: Boolean   // True if completely shrouded in fog-of-war
)

/**
 * Dynamic Lighting Engine for the 2.5D ASCII Isometric View.
 * Computes real-time multi-source photon accumulation, distance falloff, directional torch cones,
 * organic flicker oscillations, and fog-of-war occlusion.
 */
class DynamicLightingEngine {

    companion object {
        const val AMBIENT_DARK_INTENSITY = 0.08f   // Completely dark unvisited void
        const val FOW_MEMORY_INTENSITY = 0.22f     // Explored fog-of-war memory
    }

    /**
     * Computes light influence for a single grid coordinate from all active light sources.
     */
    fun calculateLighting(
        gridX: Float,
        gridY: Float,
        lightSources: List<LightSource>,
        discoveredTiles: Set<Pair<Int, Int>>,
        animTime: Float
    ): TileLighting {
        val cellX = gridX.toInt()
        val cellY = gridY.toInt()
        val isDiscovered = discoveredTiles.contains(Pair(cellX, cellY))

        var accumulatedR = 0f
        var accumulatedG = 0f
        var accumulatedB = 0f
        var maxIntensity = 0f
        var anyDirectLight = false

        for (light in lightSources) {
            val dx = gridX - light.gridX
            val dy = gridY - light.gridY
            val dist = hypot(dx, dy)

            if (dist > light.radius) continue

            // Compute distance falloff (Smooth hermite/quadratic falloff)
            val normalizedDist = dist / light.radius
            val falloff = (1.0f - normalizedDist * normalizedDist).coerceIn(0f, 1f)

            // Flicker and Pulse Modulations
            var dynamicIntensity = light.intensity
            if (light.flickerIntensity > 0f) {
                val flicker = (sin(animTime * light.flickerFrequency + light.gridX * 13.1f + light.gridY * 7.7f) * 0.5f +
                        cos(animTime * (light.flickerFrequency * 1.6f) + light.gridX * 3.3f) * 0.5f) * light.flickerIntensity
                dynamicIntensity += flicker
            }
            if (light.pulseSpeed > 0f) {
                val pulse = sin(animTime * light.pulseSpeed) * 0.25f
                dynamicIntensity += pulse
            }
            dynamicIntensity = dynamicIntensity.coerceAtLeast(0f)

            // Check Directional Cone if applicable
            var coneMultiplier = 1.0f
            if (light.isDirectional) {
                if (dist > 0.05f) {
                    val angleToTile = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    // Convert game angle (0° = North = -Y, 90° = East = +X, 180° = South = +Y, 270° = West = -X)
                    val facingVectorX = sin(Math.toRadians(light.directionAngleDeg.toDouble())).toFloat()
                    val facingVectorY = -cos(Math.toRadians(light.directionAngleDeg.toDouble())).toFloat()
                    val dot = (dx * facingVectorX + dy * facingVectorY) / dist

                    // Ambient close circle even when directional
                    val closeAmbient = (1.0f - (dist / 2.0f)).coerceIn(0f, 0.4f)
                    val coneCutoff = cos(Math.toRadians((light.coneAngleDeg * 0.5).toDouble())).toFloat()

                    if (dot >= coneCutoff) {
                        val coneFactor = ((dot - coneCutoff) / (1.0f - coneCutoff)).coerceIn(0f, 1f)
                        coneMultiplier = (coneFactor * 0.85f + 0.15f + closeAmbient).coerceAtMost(1.2f)
                    } else {
                        coneMultiplier = closeAmbient
                    }
                }
            }

            val contribution = falloff * dynamicIntensity * coneMultiplier
            if (contribution > 0.02f) {
                anyDirectLight = true
                accumulatedR += light.colorR * contribution
                accumulatedG += light.colorG * contribution
                accumulatedB += light.colorB * contribution
                maxIntensity = max(maxIntensity, contribution)
            }
        }

        // Apply Fog-of-War / Ambient floor logic
        if (!anyDirectLight) {
            if (isDiscovered) {
                // Explored memory in shadow
                return TileLighting(
                    totalIntensity = FOW_MEMORY_INTENSITY,
                    colorR = 40,
                    colorG = 55,
                    colorB = 65,
                    inDirectLight = false,
                    isFOWHidden = false
                )
            } else {
                // Total Fog-of-War darkness
                return TileLighting(
                    totalIntensity = AMBIENT_DARK_INTENSITY,
                    colorR = 15,
                    colorG = 20,
                    colorB = 25,
                    inDirectLight = false,
                    isFOWHidden = true
                )
            }
        }

        // Add subtle ambient floor when in direct light
        val ambientBase = if (isDiscovered) 0.12f else 0.05f
        val effectiveIntensity = (maxIntensity + ambientBase).coerceIn(0.15f, 1.8f)

        val totalWeight = max(maxIntensity, 0.001f)
        val finalR = (accumulatedR / totalWeight).toInt().coerceIn(0, 255)
        val finalG = (accumulatedG / totalWeight).toInt().coerceIn(0, 255)
        val finalB = (accumulatedB / totalWeight).toInt().coerceIn(0, 255)

        return TileLighting(
            totalIntensity = effectiveIntensity,
            colorR = finalR,
            colorG = finalG,
            colorB = finalB,
            inDirectLight = true,
            isFOWHidden = false
        )
    }

    /**
     * Blends base character color with the dynamic lighting engine calculation and CRT palette.
     */
    fun blendColorWithLighting(
        baseR: Int,
        baseG: Int,
        baseB: Int,
        lighting: TileLighting,
        palette: Int
    ): Int {
        val intensity = lighting.totalIntensity

        // Modulate with point-light source chromatic tint
        val tintedR = ((baseR * 0.4f + lighting.colorR * 0.6f) * intensity).toInt().coerceIn(0, 255)
        val tintedG = ((baseG * 0.4f + lighting.colorG * 0.6f) * intensity).toInt().coerceIn(0, 255)
        val tintedB = ((baseB * 0.4f + lighting.colorB * 0.6f) * intensity).toInt().coerceIn(0, 255)

        return when (palette) {
            1 -> {
                // Phosphor Green CRT: convert luminance & apply green phosphor curve
                val lum = (tintedR * 0.299f + tintedG * 0.587f + tintedB * 0.114f)
                val g = (lum * 1.35f).toInt().coerceIn(0, 255)
                val r = (lum * 0.16f).toInt().coerceIn(0, 255)
                val b = (lum * 0.28f).toInt().coerceIn(0, 255)
                android.graphics.Color.rgb(r, g, b)
            }
            2 -> {
                // Amber Industrial Terminal
                val lum = (tintedR * 0.299f + tintedG * 0.587f + tintedB * 0.114f)
                val r = (lum * 1.35f).toInt().coerceIn(0, 255)
                val g = (lum * 0.88f).toInt().coerceIn(0, 255)
                val b = (lum * 0.12f).toInt().coerceIn(0, 255)
                android.graphics.Color.rgb(r, g, b)
            }
            3 -> {
                // Neon Cyan / Synthwave
                val lum = (tintedR * 0.299f + tintedG * 0.587f + tintedB * 0.114f)
                val r = (lum * 0.15f).toInt().coerceIn(0, 255)
                val g = (lum * 1.05f).toInt().coerceIn(0, 255)
                val b = (lum * 1.35f).toInt().coerceIn(0, 255)
                android.graphics.Color.rgb(r, g, b)
            }
            else -> {
                // Mode 0: Full Chemopunk Multi-Color Dynamics
                android.graphics.Color.rgb(tintedR, tintedG, tintedB)
            }
        }
    }
}
