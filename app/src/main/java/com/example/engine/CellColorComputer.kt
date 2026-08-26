package com.example.engine

import com.example.data.TileType
import kotlin.math.max

/**
 * Computes per-cell foreground/background colors from lighting, tile type, and player state.
 * Feeds into the sub-pixel pipeline to colorize each glyph.
 */
object CellColorComputer {

    data class CellColor(
        val fgR: Float, val fgG: Float, val fgB: Float, val fgA: Float,
        val bgR: Float, val bgG: Float, val bgB: Float, val bgA: Float,
        val lightIntensity: Float,
        val glow: Float
    )

    /**
     * Default tile colors (unlit, base palette).
     */
    fun tileBaseColor(type: TileType): CellColor {
        return when (type) {
            TileType.WALL -> CellColor(
                fgR = 0.45f, fgG = 0.48f, fgB = 0.55f, fgA = 1f,
                bgR = 0.12f, bgG = 0.13f, bgB = 0.16f, bgA = 1f,
                lightIntensity = 1f, glow = 0f
            )
            TileType.FLOOR -> CellColor(
                fgR = 0.18f, fgG = 0.2f, fgB = 0.24f, fgA = 1f,
                bgR = 0.06f, bgG = 0.07f, bgB = 0.08f, bgA = 1f,
                lightIntensity = 1f, glow = 0f
            )
            TileType.TOXIC_POOL -> CellColor(
                fgR = 0.1f, fgG = 0.7f, fgB = 0.4f, fgA = 1f,
                bgR = 0.02f, bgG = 0.15f, bgB = 0.08f, bgA = 1f,
                lightIntensity = 1f, glow = 0.3f
            )
            TileType.DOOR -> CellColor(
                fgR = 0.6f, fgG = 0.5f, fgB = 0.3f, fgA = 1f,
                bgR = 0.15f, bgG = 0.12f, bgB = 0.08f, bgA = 1f,
                lightIntensity = 1f, glow = 0f
            )
            TileType.EXTRACTION_LIFT -> CellColor(
                fgR = 0.8f, fgG = 0.7f, fgB = 0.2f, fgA = 1f,
                bgR = 0.2f, bgG = 0.18f, bgB = 0.05f, bgA = 1f,
                lightIntensity = 1f, glow = 0.4f
            )
            TileType.INTERACTIVE -> CellColor(
                fgR = 0.3f, fgG = 0.5f, fgB = 0.8f, fgA = 1f,
                bgR = 0.08f, bgG = 0.1f, bgB = 0.15f, bgA = 1f,
                lightIntensity = 1f, glow = 0.2f
            )
        }
    }

    /**
     * Applies dynamic lighting from the light source list to a cell color.
     *
     * @param baseColor unlit cell color
     * @param cellWorldX world X of cell center
     * @param cellWorldY world Y of cell center
     * @param lightSources active lights in the scene
     * @param ambientLight global ambient light level (0..1)
     */
    fun applyLighting(
        baseColor: CellColor,
        cellWorldX: Float,
        cellWorldY: Float,
        lightSources: List<LightSource>,
        ambientLight: Float = 0.15f
    ): CellColor {
        var totalR = baseColor.fgR * ambientLight
        var totalG = baseColor.fgG * ambientLight
        var totalB = baseColor.fgB * ambientLight
        var totalIntensity = ambientLight
        var totalGlow = baseColor.glow

        for (light in lightSources) {
            val dx = cellWorldX - light.gridX
            val dy = cellWorldY - light.gridY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

            if (dist > light.radius) continue

            val attenuation = 1f - (dist / light.radius).coerceIn(0f, 1f)
            val attenSq = attenuation * attenuation  // quadratic falloff
            val lightContrib = light.intensity * attenSq

            if (lightContrib <= 0f) continue

            val lr = light.colorR / 255f
            val lg = light.colorG / 255f
            val lb = light.colorB / 255f

            totalR += baseColor.fgR * lr * lightContrib
            totalG += baseColor.fgG * lg * lightContrib
            totalB += baseColor.fgB * lb * lightContrib
            totalIntensity += lightContrib
            totalGlow += lightContrib * 0.3f
        }

        val inv = if (totalIntensity > 1f) 1f / totalIntensity else 1f

        return CellColor(
            fgR = (totalR * inv).coerceIn(0f, 1f),
            fgG = (totalG * inv).coerceIn(0f, 1f),
            fgB = (totalB * inv).coerceIn(0f, 1f),
            fgA = baseColor.fgA,
            bgR = (baseColor.bgR * totalIntensity * inv).coerceIn(0f, 1f),
            bgG = (baseColor.bgG * totalIntensity * inv).coerceIn(0f, 1f),
            bgB = (baseColor.bgB * totalIntensity * inv).coerceIn(0f, 1f),
            bgA = baseColor.bgA,
            lightIntensity = totalIntensity.coerceIn(0f, 2f),
            glow = totalGlow.coerceIn(0f, 1f)
        )
    }

    /**
     * Blends a primitive's color with the cell's base color using the primitive's weight.
     */
    fun blendPrimitiveColor(
        cellColor: CellColor,
        primR: Float, primG: Float, primB: Float,
        weight: Float,
        primGlow: Float = 0f
    ): CellColor {
        val w = weight.coerceIn(0f, 1f)
        val iw = 1f - w
        return cellColor.copy(
            fgR = cellColor.fgR * iw + primR * w,
            fgG = cellColor.fgG * iw + primG * w,
            fgB = cellColor.fgB * iw + primB * w,
            glow = (cellColor.glow + primGlow * w).coerceIn(0f, 1f)
        )
    }

    /**
     * Returns player-specific green-tinted color with pulsing glow.
     */
    fun playerColor(timeMs: Long): CellColor {
        val pulse = 0.5f + 0.5f * kotlin.math.sin(timeMs / 300.0).toFloat()
        return CellColor(
            fgR = 0.2f, fgG = 0.9f + pulse * 0.1f, fgB = 0.5f, fgA = 1f,
            bgR = 0.03f, bgG = 0.08f, bgB = 0.04f, bgA = 1f,
            lightIntensity = 1.2f + pulse * 0.3f,
            glow = 0.3f + pulse * 0.2f
        )
    }

    /**
     * Returns enemy-specific color based on enemy name/type.
     */
    fun enemyColor(enemyName: String, hpRatio: Float): CellColor {
        val baseColor = when {
            enemyName.contains("Rat", ignoreCase = true) ->
                Triple(0.7f, 0.3f, 0.2f)
            enemyName.contains("Crawler", ignoreCase = true) ->
                Triple(0.5f, 0.2f, 0.6f)
            enemyName.contains("Slime", ignoreCase = true) || enemyName.contains("Blob", ignoreCase = true) ->
                Triple(0.2f, 0.8f, 0.3f)
            else ->
                Triple(0.9f, 0.2f, 0.2f)
        }

        // Low HP makes enemies appear dimmer/redder
        val dimFactor = 0.5f + 0.5f * hpRatio

        return CellColor(
            fgR = baseColor.first * dimFactor,
            fgG = baseColor.second * dimFactor,
            fgB = baseColor.third * dimFactor,
            fgA = 1f,
            bgR = baseColor.first * 0.1f,
            bgG = baseColor.second * 0.1f,
            bgB = baseColor.third * 0.1f,
            bgA = 1f,
            lightIntensity = dimFactor,
            glow = (1f - hpRatio) * 0.3f  // dying enemies glow red
        )
    }
}
