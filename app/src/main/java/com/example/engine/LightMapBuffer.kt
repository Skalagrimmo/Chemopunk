package com.example.engine

import com.example.data.TileType
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-performance 2D/3D Spatial Light-Map Buffer for ASCII Isometric Viewports.
 *
 * Precomputes and caches:
 * 1. Multi-source photon irradiance and chromatic light colors.
 * 2. Euclidean distance to the nearest active light sources.
 * 3. 3D raymarched shadow occlusions and geometric ambient occlusion.
 * 4. Dynamic modulation for adjusting ASCII glyph brightness, color tint, and density gradation.
 */
class LightMapBuffer(
    val width: Int = 32,
    val height: Int = 32,
    private val lightingEngine: DynamicLightingEngine = DynamicLightingEngine()
) {

    private val totalCells = width * height

    // Per-cell light map buffer arrays
    val intensityBuffer = FloatArray(totalCells)
    val colorRBuffer = FloatArray(totalCells)
    val colorGBuffer = FloatArray(totalCells)
    val colorBBuffer = FloatArray(totalCells)
    val shadowBuffer = FloatArray(totalCells) { 1.0f }
    val aoBuffer = FloatArray(totalCells) { 1.0f }
    val nearestLightDistBuffer = FloatArray(totalCells) { Float.MAX_VALUE }
    val lightCountBuffer = IntArray(totalCells)
    val inDirectLightBuffer = BooleanArray(totalCells)
    val isFOWHiddenBuffer = BooleanArray(totalCells) { true }

    // Multi-elevation buffers for 3D isometric wall facets and top caps
    val topCapIntensityBuffer = FloatArray(totalCells)
    val wallFacetIntensityBuffer = FloatArray(totalCells)

    /**
     * Re-computes and fills the light-map buffer for the entire grid in a single pass.
     */
    fun computeLightMap(
        mapGrid: List<List<TileType>>,
        lightSources: List<LightSource>,
        discoveredTiles: Set<Pair<Int, Int>>,
        animTime: Float,
        enableShadows: Boolean = true
    ) {
        val rows = mapGrid.size
        val cols = if (rows > 0) mapGrid[0].size else 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val cellX = x
                val cellY = y

                val isInsideGrid = (cellY < rows && cellX < cols)
                val tile = if (isInsideGrid) mapGrid[cellY][cellX] else TileType.FLOOR

                // Calculate floor-level lighting (elevation 0.0)
                val floorLighting = lightingEngine.calculate3DLighting(
                    gridX = cellX + 0.5f,
                    gridY = cellY + 0.5f,
                    elevation = 0f,
                    normal = SurfaceNormals.FLOOR,
                    mapGrid = mapGrid,
                    lightSources = lightSources,
                    discoveredTiles = discoveredTiles,
                    animTime = animTime,
                    enableShadows = enableShadows
                )

                intensityBuffer[index] = floorLighting.totalIntensity
                colorRBuffer[index] = floorLighting.colorR.toFloat()
                colorGBuffer[index] = floorLighting.colorG.toFloat()
                colorBBuffer[index] = floorLighting.colorB.toFloat()
                shadowBuffer[index] = floorLighting.shadowFactor
                aoBuffer[index] = floorLighting.ambientOcclusion
                inDirectLightBuffer[index] = floorLighting.inDirectLight
                isFOWHiddenBuffer[index] = floorLighting.isFOWHidden

                // Calculate distance to nearest light source
                var minDist = Float.MAX_VALUE
                var activeLightsCount = 0
                for (light in lightSources) {
                    val dx = (cellX + 0.5f) - light.gridX
                    val dy = (cellY + 0.5f) - light.gridY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < minDist) {
                        minDist = dist
                    }
                    if (dist <= light.radius) {
                        activeLightsCount++
                    }
                }
                nearestLightDistBuffer[index] = minDist
                lightCountBuffer[index] = activeLightsCount

                // Calculate 3D top cap lighting (elevation 1.0)
                val topLighting = lightingEngine.calculate3DLighting(
                    gridX = cellX + 0.5f,
                    gridY = cellY + 0.5f,
                    elevation = 1.0f,
                    normal = SurfaceNormals.TOP,
                    mapGrid = mapGrid,
                    lightSources = lightSources,
                    discoveredTiles = discoveredTiles,
                    animTime = animTime,
                    enableShadows = enableShadows
                )
                topCapIntensityBuffer[index] = topLighting.totalIntensity

                // Calculate 3D wall facet lighting (elevation 0.5)
                val wallLighting = lightingEngine.calculate3DLighting(
                    gridX = cellX + 0.5f,
                    gridY = cellY + 0.5f,
                    elevation = 0.5f,
                    normal = SurfaceNormals.SOUTH_WEST,
                    mapGrid = mapGrid,
                    lightSources = lightSources,
                    discoveredTiles = discoveredTiles,
                    animTime = animTime,
                    enableShadows = enableShadows
                )
                wallFacetIntensityBuffer[index] = wallLighting.totalIntensity
            }
        }
    }

    /**
     * Gets TileLighting at integer grid coordinates directly from the light-map buffer.
     */
    fun getTileLighting(cellX: Int, cellY: Int, elevation: Float = 0f): TileLighting {
        if (cellX !in 0 until width || cellY !in 0 until height) {
            return TileLighting(
                totalIntensity = DynamicLightingEngine.AMBIENT_DARK_INTENSITY,
                colorR = 15,
                colorG = 20,
                colorB = 25,
                inDirectLight = false,
                isFOWHidden = true,
                shadowFactor = 0f,
                ambientOcclusion = 1.0f
            )
        }

        val index = cellY * width + cellX
        val intensity = when {
            elevation >= 0.8f -> topCapIntensityBuffer[index]
            elevation >= 0.3f -> wallFacetIntensityBuffer[index]
            else -> intensityBuffer[index]
        }

        return TileLighting(
            totalIntensity = intensity,
            colorR = colorRBuffer[index].toInt(),
            colorG = colorGBuffer[index].toInt(),
            colorB = colorBBuffer[index].toInt(),
            inDirectLight = inDirectLightBuffer[index],
            isFOWHidden = isFOWHiddenBuffer[index],
            shadowFactor = shadowBuffer[index],
            ambientOcclusion = aoBuffer[index]
        )
    }

    /**
     * Samples the light-map buffer with bilinear interpolation for smooth sub-tile lighting gradients.
     */
    fun sampleInterpolatedLighting(gridX: Float, gridY: Float, elevation: Float = 0f): TileLighting {
        val gx = (gridX - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val gy = (gridY - 0.5f).coerceIn(0f, (height - 1).toFloat())

        val x0 = floor(gx).toInt()
        val y0 = floor(gy).toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)

        val fx = gx - x0
        val fy = gy - y0

        val idx00 = y0 * width + x0
        val idx10 = y0 * width + x1
        val idx01 = y1 * width + x0
        val idx11 = y1 * width + x1

        val buffer = when {
            elevation >= 0.8f -> topCapIntensityBuffer
            elevation >= 0.3f -> wallFacetIntensityBuffer
            else -> intensityBuffer
        }

        // Bilinear interpolation of scalar intensity
        val i0 = buffer[idx00] * (1 - fx) + buffer[idx10] * fx
        val i1 = buffer[idx01] * (1 - fx) + buffer[idx11] * fx
        val totalIntensity = i0 * (1 - fy) + i1 * fy

        // Bilinear interpolation of color channels
        val r0 = colorRBuffer[idx00] * (1 - fx) + colorRBuffer[idx10] * fx
        val r1 = colorRBuffer[idx01] * (1 - fx) + colorRBuffer[idx11] * fx
        val colorR = (r0 * (1 - fy) + r1 * fy).toInt().coerceIn(0, 255)

        val g0 = colorGBuffer[idx00] * (1 - fx) + colorGBuffer[idx10] * fx
        val g1 = colorGBuffer[idx01] * (1 - fx) + colorGBuffer[idx11] * fx
        val colorG = (g0 * (1 - fy) + g1 * fy).toInt().coerceIn(0, 255)

        val b0 = colorBBuffer[idx00] * (1 - fx) + colorBBuffer[idx10] * fx
        val b1 = colorBBuffer[idx01] * (1 - fx) + colorBBuffer[idx11] * fx
        val colorB = (b0 * (1 - fy) + b1 * fy).toInt().coerceIn(0, 255)

        // Bilinear interpolation of shadow factor
        val s0 = shadowBuffer[idx00] * (1 - fx) + shadowBuffer[idx10] * fx
        val s1 = shadowBuffer[idx01] * (1 - fx) + shadowBuffer[idx11] * fx
        val shadowFactor = s0 * (1 - fy) + s1 * fy

        val isHidden = isFOWHiddenBuffer[idx00] && isFOWHiddenBuffer[idx10] && isFOWHiddenBuffer[idx01] && isFOWHiddenBuffer[idx11]
        val inDirect = inDirectLightBuffer[idx00] || inDirectLightBuffer[idx10] || inDirectLightBuffer[idx01] || inDirectLightBuffer[idx11]

        return TileLighting(
            totalIntensity = totalIntensity,
            colorR = colorR,
            colorG = colorG,
            colorB = colorB,
            inDirectLight = inDirect,
            isFOWHidden = isHidden,
            shadowFactor = shadowFactor,
            ambientOcclusion = aoBuffer[idx00]
        )
    }

    /**
     * Adjusts the RGB color of an ASCII glyph based on the distance from light sources and light-map values.
     */
    fun adjustGlyphColor(
        baseR: Int,
        baseG: Int,
        baseB: Int,
        gridX: Float,
        gridY: Float,
        elevation: Float = 0f,
        palette: Int = 0,
        enableDither: Boolean = true
    ): Int {
        val lighting = sampleInterpolatedLighting(gridX, gridY, elevation)
        return lightingEngine.blendColorWithLighting(
            baseR = baseR,
            baseG = baseG,
            baseB = baseB,
            lighting = lighting,
            palette = palette,
            gridX = gridX.toInt(),
            gridY = gridY.toInt(),
            enableDither = enableDither
        )
    }

    /**
     * Computes the brightness factor (0.0 .. 2.0+) for an ASCII glyph based on the light-map buffer.
     */
    fun getGlyphBrightness(gridX: Float, gridY: Float, elevation: Float = 0f): Float {
        val lighting = sampleInterpolatedLighting(gridX, gridY, elevation)
        if (lighting.isFOWHidden) return 0f
        return lighting.totalIntensity * lighting.shadowFactor * lighting.ambientOcclusion
    }

    /**
     * Distance to nearest active light source in grid space.
     */
    fun getDistanceToNearestLight(gridX: Float, gridY: Float): Float {
        val cx = gridX.toInt().coerceIn(0, width - 1)
        val cy = gridY.toInt().coerceIn(0, height - 1)
        return nearestLightDistBuffer[cy * width + cx]
    }

    /**
     * Dynamically selects an ASCII density character from a ramp (e.g., " .:-=+*#%@") based on
     * distance from light sources and buffered brightness intensity.
     */
    fun selectDensityGlyph(
        gridX: Float,
        gridY: Float,
        elevation: Float = 0f,
        densityRamp: String = " .:-=+*#%@",
        fallbackChar: Char = ' '
    ): Char {
        val brightness = getGlyphBrightness(gridX, gridY, elevation)
        if (brightness <= 0.05f || densityRamp.isEmpty()) return fallbackChar

        // Map brightness (0.0 .. 1.5) to density ramp index
        val normalized = (brightness / 1.4f).coerceIn(0f, 1f)
        val index = (normalized * (densityRamp.length - 1)).toInt().coerceIn(0, densityRamp.length - 1)
        return densityRamp[index]
    }

    /**
     * Applies light-map modifications to an ASCII glyph, returning adjusted character, color, and alpha.
     */
    fun applyLightToGlyph(
        baseChar: Char,
        baseR: Int,
        baseG: Int,
        baseB: Int,
        gridX: Float,
        gridY: Float,
        elevation: Float = 0f,
        palette: Int = 0,
        densityRamp: String? = null
    ): LitGlyph {
        val lighting = sampleInterpolatedLighting(gridX, gridY, elevation)
        val adjustedColor = lightingEngine.blendColorWithLighting(
            baseR = baseR,
            baseG = baseG,
            baseB = baseB,
            lighting = lighting,
            palette = palette,
            gridX = gridX.toInt(),
            gridY = gridY.toInt()
        )

        val brightness = (lighting.totalIntensity * lighting.shadowFactor * lighting.ambientOcclusion).coerceIn(0f, 2f)
        val charToRender = if (densityRamp != null) {
            selectDensityGlyph(gridX, gridY, elevation, densityRamp, baseChar)
        } else {
            baseChar
        }

        val alpha = when {
            lighting.isFOWHidden -> 0.15f
            !lighting.inDirectLight -> 0.45f
            else -> (0.6f + brightness * 0.4f).coerceIn(0.2f, 1.0f)
        }

        return LitGlyph(
            char = charToRender,
            colorArgb = adjustedColor,
            alpha = alpha,
            brightness = brightness,
            lighting = lighting
        )
    }
}

/**
 * Encapsulates the resulting rendered state of an ASCII character modified by the LightMapBuffer.
 */
data class LitGlyph(
    val char: Char,
    val colorArgb: Int,
    val alpha: Float,
    val brightness: Float,
    val lighting: TileLighting
)
