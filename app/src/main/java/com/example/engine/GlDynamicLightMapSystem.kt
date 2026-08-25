package com.example.engine

import android.opengl.GLES20
import com.example.data.Enemy
import com.example.data.Player
import com.example.data.TileType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-Performance GPU Dynamic Light-Map Subsystem for OpenGL ES 2.0.
 *
 * Computes real-time multi-source photon irradiance around the player character and environment:
 * 1. Player Tactical Flashlight (Directional spotlight with penumbra falloff, battery flicker, and micro-pulse).
 * 2. Player Bio-Suit Proximity Aura (360-degree soft localized radiance).
 * 3. Dynamic Raymarched Shadow Occlusions through solid wall geometry.
 * 4. Environmental Point Lights (Toxic pools, chemical emergency flares, extraction beacons).
 * 5. Direct GPU upload into a filtered GL_RGBA texture with hardware bilinear interpolation (GL_LINEAR).
 */
class GlDynamicLightMapSystem(
    val mapWidth: Int = 32,
    val mapHeight: Int = 32
) {
    companion object {
        const val LIGHTMAP_WIDTH = 32
        const val LIGHTMAP_HEIGHT = 32
        const val BYTES_PER_PIXEL = 4 // RGBA
        const val BUFFER_SIZE = LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT * BYTES_PER_PIXEL
    }

    // Direct native memory buffer for zero-allocation GL texture streaming
    private val pixelByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        .order(ByteOrder.nativeOrder())

    private val rawBytes = ByteArray(BUFFER_SIZE)

    var textureId: Int = 0
        private set

    private var isInitialized = false

    // Cached per-tile float arrays for fast lighting calculations
    private val totalIntensity = FloatArray(LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT)
    private val lightRed = FloatArray(LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT)
    private val lightGreen = FloatArray(LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT)
    private val lightBlue = FloatArray(LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT)
    private val shadowOcclusion = FloatArray(LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT) { 1.0f }

    /**
     * Initializes the OpenGL ES 2.0 Light-Map Texture with bilinear filtering and edge clamping.
     */
    fun initGl() {
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Clear buffer with baseline ambient wasteland darkness
        pixelByteBuffer.clear()
        for (i in 0 until LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT) {
            pixelByteBuffer.put(18.toByte()) // R
            pixelByteBuffer.put(24.toByte()) // G
            pixelByteBuffer.put(32.toByte()) // B
            pixelByteBuffer.put(45.toByte()) // Intensity
        }
        pixelByteBuffer.position(0)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            LIGHTMAP_WIDTH,
            LIGHTMAP_HEIGHT,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixelByteBuffer
        )

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        isInitialized = true
    }

    /**
     * Calculates dynamic lighting around the player and uploads the updated light-map texture to the GPU.
     */
    fun updateAndUpload(
        mapGrid: List<List<TileType>>,
        player: Player,
        enemies: List<Enemy>,
        externalLights: List<LightSource>,
        discoveredTiles: Set<Pair<Int, Int>>,
        animTime: Float
    ) {
        if (!isInitialized || textureId == 0) {
            initGl()
        }

        val rows = min(mapGrid.size, LIGHTMAP_HEIGHT)
        val cols = if (rows > 0) min(mapGrid[0].size, LIGHTMAP_WIDTH) else 0

        // 1. Reset working buffers with ambient baseline
        val ambientBaseR = 0.08f
        val ambientBaseG = 0.11f
        val ambientBaseB = 0.16f
        val ambientBaseInt = 0.18f

        for (i in 0 until LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT) {
            totalIntensity[i] = ambientBaseInt
            lightRed[i] = ambientBaseR
            lightGreen[i] = ambientBaseG
            lightBlue[i] = ambientBaseB
            shadowOcclusion[i] = 1.0f
        }

        // 2. Synthesize Player Flashlight & Aura Parameters
        val flicker = sin(animTime * 14.5f) * 0.04f + sin(animTime * 28.0f) * 0.02f
        val playerFacingRad = Math.toRadians(player.angleDegrees.toDouble()).toFloat()
        val flashDirX = cos(playerFacingRad)
        val flashDirY = sin(playerFacingRad)

        val playerFlashlightRange = 9.0f
        val playerFlashlightIntensity = 1.45f + flicker
        val innerConeCos = cos(Math.toRadians(26.0)).toFloat() // ~52 deg full bright core
        val outerConeCos = cos(Math.toRadians(48.0)).toFloat() // ~96 deg feathered penumbra

        val playerAuraRange = 2.8f
        val playerAuraIntensity = 0.55f

        // 3. Compute dynamic lighting per cell
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val index = y * LIGHTMAP_WIDTH + x
                val cellCenterX = x + 0.5f
                val cellCenterY = y + 0.5f

                val isDiscovered = discoveredTiles.contains(Pair(x, y))
                if (!isDiscovered) {
                    // Fog of War shroud
                    totalIntensity[index] = 0.0f
                    lightRed[index] = 0.0f
                    lightGreen[index] = 0.0f
                    lightBlue[index] = 0.0f
                    continue
                }

                // Vector from player to cell center
                val toCellX = cellCenterX - player.x
                val toCellY = cellCenterY - player.y
                val distSq = toCellX * toCellX + toCellY * toCellY
                val dist = sqrt(distSq)

                var cellInt = ambientBaseInt
                var rAcc = ambientBaseR
                var gAcc = ambientBaseG
                var bAcc = ambientBaseB

                // A. Player Bio-Suit Proximity Aura (360 degrees)
                if (dist <= playerAuraRange) {
                    val auraAtten = (1.0f - (dist / playerAuraRange)).coerceIn(0f, 1f)
                    val auraPower = auraAtten * playerAuraIntensity
                    cellInt += auraPower
                    rAcc += 0.45f * auraPower
                    gAcc += 0.85f * auraPower
                    bAcc += 0.95f * auraPower // Cyan bio-suit hue
                }

                // B. Player Tactical Flashlight (Directional Cone with Shadows)
                if (dist > 0.05f && dist <= playerFlashlightRange) {
                    val dirNormX = toCellX / dist
                    val dirNormY = toCellY / dist
                    val dotProduct = dirNormX * flashDirX + dirNormY * flashDirY

                    if (dotProduct > outerConeCos) {
                        // Raycast shadow test from player to cell
                        val isOccluded = isRayOccludedByWalls(player.x, player.y, cellCenterX, cellCenterY, mapGrid)

                        if (!isOccluded) {
                            // Spotlight angular falloff
                            val spotFactor = ((dotProduct - outerConeCos) / (innerConeCos - outerConeCos)).coerceIn(0f, 1f)
                            val distAtten = 1.0f / (1.0f + 0.10f * dist + 0.04f * distSq)
                            val lightPower = spotFactor * distAtten * playerFlashlightIntensity

                            cellInt += lightPower
                            rAcc += 0.90f * lightPower
                            gAcc += 0.96f * lightPower
                            bAcc += 1.00f * lightPower // Clean daylight photon beam
                        }
                    }
                }

                // C. Environmental Light Sources (Flares, Toxic pools, Lift beacons)
                for (light in externalLights) {
                    val ldx = cellCenterX - light.gridX
                    val ldy = cellCenterY - light.gridY
                    val lDistSq = ldx * ldx + ldy * ldy

                    if (lDistSq <= light.radius * light.radius) {
                        val lDist = sqrt(lDistSq)
                        val isLightOccluded = if (light.castsShadows) {
                            isRayOccludedByWalls(light.gridX, light.gridY, cellCenterX, cellCenterY, mapGrid)
                        } else false

                        if (!isLightOccluded) {
                            val pulse = if (light.pulseSpeed > 0f) {
                                sin(animTime * light.pulseSpeed) * 0.15f + 0.85f
                            } else 1.0f

                            val lAtten = (1.0f - (lDist / light.radius)).coerceIn(0f, 1f) * pulse
                            val lPower = lAtten * light.intensity

                            cellInt += lPower
                            rAcc += (light.colorR / 255f) * lPower
                            gAcc += (light.colorG / 255f) * lPower
                            bAcc += (light.colorB / 255f) * lPower
                        }
                    }
                }

                // Store normalized results
                totalIntensity[index] = cellInt.coerceIn(0f, 2.5f)
                lightRed[index] = rAcc.coerceIn(0f, 1.5f)
                lightGreen[index] = gAcc.coerceIn(0f, 1.5f)
                lightBlue[index] = bAcc.coerceIn(0f, 1.5f)
            }
        }

        // 4. Pack into Byte buffer
        var byteIdx = 0
        for (y in 0 until LIGHTMAP_HEIGHT) {
            for (x in 0 until LIGHTMAP_WIDTH) {
                val index = y * LIGHTMAP_WIDTH + x
                val intensity = totalIntensity[index]
                val r = (lightRed[index] * 255f).toInt().coerceIn(0, 255)
                val g = (lightGreen[index] * 255f).toInt().coerceIn(0, 255)
                val b = (lightBlue[index] * 255f).toInt().coerceIn(0, 255)
                val a = (intensity * 128f).toInt().coerceIn(0, 255)

                rawBytes[byteIdx++] = r.toByte()
                rawBytes[byteIdx++] = g.toByte()
                rawBytes[byteIdx++] = b.toByte()
                rawBytes[byteIdx++] = a.toByte()
            }
        }

        pixelByteBuffer.clear()
        pixelByteBuffer.put(rawBytes)
        pixelByteBuffer.position(0)

        // 5. Upload sub-image to GPU Light-Map Texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            LIGHTMAP_WIDTH,
            LIGHTMAP_HEIGHT,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixelByteBuffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /**
     * Fast Bresenham/DDA line-of-sight raycaster to check if a ray from (startX, startY) to (endX, endY)
     * intersects any solid wall tiles.
     */
    private fun isRayOccludedByWalls(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        mapGrid: List<List<TileType>>
    ): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.5f) return false

        val steps = max(1, (dist * 2.5f).toInt())
        val stepX = dx / steps
        val stepY = dy / steps

        val rows = mapGrid.size
        val cols = if (rows > 0) mapGrid[0].size else 0

        var curX = startX
        var curY = startY

        // Traverse the ray (excluding origin and target tile)
        for (i in 1 until steps - 1) {
            curX += stepX
            curY += stepY

            val gx = curX.toInt()
            val gy = curY.toInt()

            if (gy in 0 until rows && gx in 0 until cols) {
                if (mapGrid[gy][gx] == TileType.WALL) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Samples the continuous interpolated light-map value at arbitrary grid floating coordinates (gx, gy).
     */
    fun sampleInterpolatedLighting(gx: Float, gy: Float): TileLighting {
        val clampedX = gx.coerceIn(0f, (LIGHTMAP_WIDTH - 1).toFloat())
        val clampedY = gy.coerceIn(0f, (LIGHTMAP_HEIGHT - 1).toFloat())

        val x0 = clampedX.toInt()
        val y0 = clampedY.toInt()
        val x1 = min(x0 + 1, LIGHTMAP_WIDTH - 1)
        val y1 = min(y0 + 1, LIGHTMAP_HEIGHT - 1)

        val tx = clampedX - x0
        val ty = clampedY - y0

        val idx00 = y0 * LIGHTMAP_WIDTH + x0
        val idx10 = y0 * LIGHTMAP_WIDTH + x1
        val idx01 = y1 * LIGHTMAP_WIDTH + x0
        val idx11 = y1 * LIGHTMAP_WIDTH + x1

        val int00 = totalIntensity[idx00]
        val int10 = totalIntensity[idx10]
        val int01 = totalIntensity[idx01]
        val int11 = totalIntensity[idx11]

        val interpolatedInt = (int00 * (1f - tx) + int10 * tx) * (1f - ty) +
                (int01 * (1f - tx) + int11 * tx) * ty

        val r00 = lightRed[idx00]
        val r10 = lightRed[idx10]
        val r01 = lightRed[idx01]
        val r11 = lightRed[idx11]
        val interpolatedR = ((r00 * (1f - tx) + r10 * tx) * (1f - ty) + (r01 * (1f - tx) + r11 * tx) * ty) * 255f

        val g00 = lightGreen[idx00]
        val g10 = lightGreen[idx10]
        val g01 = lightGreen[idx01]
        val g11 = lightGreen[idx11]
        val interpolatedG = ((g00 * (1f - tx) + g10 * tx) * (1f - ty) + (g01 * (1f - tx) + g11 * tx) * ty) * 255f

        val b00 = lightBlue[idx00]
        val b10 = lightBlue[idx10]
        val b01 = lightBlue[idx01]
        val b11 = lightBlue[idx11]
        val interpolatedB = ((b00 * (1f - tx) + b10 * tx) * (1f - ty) + (b01 * (1f - tx) + b11 * tx) * ty) * 255f

        return TileLighting(
            totalIntensity = interpolatedInt,
            colorR = interpolatedR.toInt().coerceIn(0, 255),
            colorG = interpolatedG.toInt().coerceIn(0, 255),
            colorB = interpolatedB.toInt().coerceIn(0, 255),
            inDirectLight = interpolatedInt > 0.25f,
            isFOWHidden = interpolatedInt <= 0.01f,
            shadowFactor = 1.0f,
            ambientOcclusion = 1.0f
        )
    }

    fun releaseGl() {
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = 0
        }
        isInitialized = false
    }
}
