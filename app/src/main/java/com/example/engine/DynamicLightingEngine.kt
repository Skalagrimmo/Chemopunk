package com.example.engine

import com.example.data.TileType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D Vector for spatial coordinates, light positions, and surface normal calculations.
 */
data class Vector3(val x: Float, val y: Float, val z: Float) {
    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun distanceTo(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.0001f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 1f)
    }

    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
}

/**
 * Surface Normals for 3D Isometric Geometry (Cubes, Wall Facets, Floors, Entities).
 */
object SurfaceNormals {
    val TOP = Vector3(0f, 0f, 1f)          // Top horizontal roof / cap
    val SOUTH_WEST = Vector3(0f, 1f, 0f)   // Left isometric wall facet
    val SOUTH_EAST = Vector3(1f, 0f, 0f)   // Right isometric wall facet
    val NORTH_WEST = Vector3(-1f, 0f, 0f)  // Back-left wall facet
    val NORTH_EAST = Vector3(0f, -1f, 0f)  // Back-right wall facet
    val FLOOR = Vector3(0f, 0f, 1f)        // Flat ground
}

/**
 * Supported Light Source types in the wasteland.
 */
enum class LightType {
    PLAYER_FLASHLIGHT,     // Directional cone with ambient halo and specular core
    POINT_TORCH,           // Warm flickering wall/ground torch
    FLARE_EMERGENCY,       // Vivid pulsing chemical emergency flare (red / magenta)
    BIO_LUMINESCENT,       // Eerie green/cyan chemical toxic pool glow
    EXTRACTION_BEACON,     // Bright pulsing gold elevator beacon
    EMP_PULSE              // Electric cyan temporary burst
}

/**
 * Representation of a Dynamic Point or Directional Light Source in 3D grid space.
 */
data class LightSource(
    val id: String,
    val gridX: Float,
    val gridY: Float,
    val elevation: Float = 0.5f,  // 3D Z elevation (0.0 = ground, 0.5 = chest height, 1.0 = ceiling/wall top)
    val colorR: Int,              // 0..255
    val colorG: Int,              // 0..255
    val colorB: Int,              // 0..255
    val intensity: Float,         // 0.0 .. 2.0+
    val radius: Float,            // radius in tile grid units
    val type: LightType = LightType.POINT_TORCH,
    val isDirectional: Boolean = false,
    val directionAngleDeg: Float = 0f,
    val coneAngleDeg: Float = 65f,
    val flickerFrequency: Float = 4.0f,
    val flickerIntensity: Float = 0.15f,
    val pulseSpeed: Float = 0f,
    val castsShadows: Boolean = true,
    val lifetimeMs: Long = -1L,
    val createdAt: Long = System.currentTimeMillis()
) {
    val position3D: Vector3
        get() = Vector3(gridX, gridY, elevation)
}

/**
 * Projected Ground Shadow data for extruded 3D objects (walls, player, enemies).
 */
data class ProjectedShadow(
    val originX: Float,
    val originY: Float,
    val shadowDirX: Float,
    val shadowDirY: Float,
    val length: Float,
    val opacity: Float,
    val shadowGlyph: String,
    val lightColorR: Int,
    val lightColorG: Int,
    val lightColorB: Int
)

/**
 * Resulting Lighting Calculation for a specific tile or 3D surface in grid space.
 */
data class TileLighting(
    val totalIntensity: Float,      // Combined scalar brightness (0.0 .. 1.8)
    val colorR: Int,                // Weighted combined Red (0..255)
    val colorG: Int,                // Weighted combined Green (0..255)
    val colorB: Int,                // Weighted combined Blue (0..255)
    val inDirectLight: Boolean,     // True if illuminated by at least one active light
    val isFOWHidden: Boolean,        // True if completely shrouded in fog-of-war
    val shadowFactor: Float = 1.0f, // 1.0 = fully lit, 0.0 = complete shadow occlusion
    val ambientOcclusion: Float = 1.0f // 1.0 = open space, 0.5 = corner/base contact shadow
)

/**
 * Dynamic 3D Lighting Engine for the 2.5D ASCII Isometric View.
 *
 * Core Capabilities:
 * 1. Physically-based 3D Distance Falloff (Inverse Square + Quadratic Attenuation + Smooth Hermite cutoff).
 * 2. 3D Raymarched Dynamic Shadow Casting: Traces occlusion rays through the 3D map grid to cast real-time shadows behind walls and obstacles.
 * 3. Soft Penumbra Falloff: Calculates fractional penumbra shadows based on light size, occluder distance, and receiver depth.
 * 4. Lambertian Cosine Shading with Surface Normals for 3D Voxels (Top face, Left facet, Right facet, Ambient base).
 * 5. Ambient Occlusion: Geometric contact shadowing at wall corners and floor junctions.
 * 6. Projected Isometric Ground Shadows: Calculates directional shadow geometry cast by 3D objects on surrounding floor tiles.
 * 7. Multi-Source Photon Accumulation & Fog-of-War Integration.
 */
class DynamicLightingEngine {

    companion object {
        const val AMBIENT_DARK_INTENSITY = 0.08f   // Completely dark unvisited void
        const val FOW_MEMORY_INTENSITY = 0.22f     // Explored fog-of-war memory
        const val SHADOW_AMBIENT_FLOOR = 0.18f     // Base ambient level in shadow
        const val RAY_STEP_SIZE = 0.25f            // Grid step size for 3D shadow raymarching
    }

    /**
     * Computes comprehensive 3D lighting for a surface point, incorporating:
     * - 3D distance falloff from all active light sources
     * - 3D surface normal Lambertian dot-product shading
     * - 3D raymarching shadow occlusion from walls
     * - Ambient occlusion from neighboring geometry
     */
    fun calculate3DLighting(
        gridX: Float,
        gridY: Float,
        elevation: Float = 0f,
        normal: Vector3 = SurfaceNormals.TOP,
        mapGrid: List<List<TileType>>? = null,
        lightSources: List<LightSource>,
        discoveredTiles: Set<Pair<Int, Int>>,
        animTime: Float,
        enableShadows: Boolean = true
    ): TileLighting {
        val cellX = gridX.toInt()
        val cellY = gridY.toInt()
        val isDiscovered = discoveredTiles.contains(Pair(cellX, cellY))

        val surfacePos = Vector3(gridX, gridY, elevation)
        val normalizedNormal = normal.normalized()

        var accumulatedR = 0f
        var accumulatedG = 0f
        var accumulatedB = 0f
        var maxIntensity = 0f
        var minShadowFactor = 1.0f
        var anyDirectLight = false

        // Compute Ambient Occlusion factor if map grid is available
        val aoFactor = if (mapGrid != null && elevation < 0.2f) {
            calculateAmbientOcclusion(cellX, cellY, mapGrid)
        } else {
            1.0f
        }

        for (light in lightSources) {
            val lightPos = light.position3D
            val toLight = lightPos - surfacePos
            val dist3D = toLight.length()

            if (dist3D > light.radius) continue

            // 1. Physically-based 3D Distance Falloff (Smooth inverse polynomial with radius cutoff)
            val normalizedDist = dist3D / light.radius
            val distanceFalloff = ((1.0f - normalizedDist * normalizedDist).pow(2) / (1.0f + 0.35f * dist3D + 0.15f * dist3D * dist3D)).coerceIn(0f, 1.0f)

            // 2. Dynamic Modulation (Organic flicker and pulse)
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

            // 3. 3D Lambertian Cosine Shading (N • L)
            val lightDirNormalized = toLight.normalized()
            val nDotL = max(0f, normalizedNormal.dot(lightDirNormalized))
            // Ambient wrap lighting so backfaces retain subtle shape definition
            val lambertianShading = (nDotL * 0.75f + 0.25f).coerceIn(0f, 1.2f)

            // 4. Directional Cone Calculation (for Player Flashlight)
            var coneMultiplier = 1.0f
            if (light.isDirectional) {
                if (dist3D > 0.05f) {
                    val facingVectorX = sin(Math.toRadians(light.directionAngleDeg.toDouble())).toFloat()
                    val facingVectorY = -cos(Math.toRadians(light.directionAngleDeg.toDouble())).toFloat()
                    val facingDir2D = Vector3(facingVectorX, facingVectorY, -0.15f).normalized()
                    
                    val toTargetFromLight = (surfacePos - lightPos).normalized()
                    val dotCone = facingDir2D.dot(toTargetFromLight)

                    val coneCutoff = cos(Math.toRadians((light.coneAngleDeg * 0.5).toDouble())).toFloat()
                    val closeAmbient = (1.0f - (dist3D / 2.5f)).coerceIn(0f, 0.45f)

                    if (dotCone >= coneCutoff) {
                        val coneFactor = ((dotCone - coneCutoff) / (1.0f - coneCutoff)).coerceIn(0f, 1f)
                        // Smooth cubic hermite curve for beam edge
                        val smoothCone = coneFactor * coneFactor * (3.0f - 2.0f * coneFactor)
                        coneMultiplier = (smoothCone * 0.85f + 0.15f + closeAmbient).coerceAtMost(1.35f)
                    } else {
                        coneMultiplier = closeAmbient
                    }
                }
            }

            // 5. 3D Raymarched Dynamic Shadow Casting with Soft Penumbra
            var shadowFactor = 1.0f
            if (enableShadows && light.castsShadows && mapGrid != null) {
                shadowFactor = calculate3DRayShadow(
                    lightPos = lightPos,
                    targetPos = surfacePos,
                    mapGrid = mapGrid,
                    lightRadius = light.radius
                )
                minShadowFactor = min(minShadowFactor, shadowFactor)
            }

            // Combine all photon contribution factors
            val lightContribution = distanceFalloff * dynamicIntensity * lambertianShading * coneMultiplier * shadowFactor * aoFactor

            if (lightContribution > 0.015f) {
                anyDirectLight = true
                accumulatedR += light.colorR * lightContribution
                accumulatedG += light.colorG * lightContribution
                accumulatedB += light.colorB * lightContribution
                maxIntensity = max(maxIntensity, lightContribution)
            }
        }

        // Apply Fog-of-War / Ambient floor logic
        if (!anyDirectLight) {
            return if (isDiscovered) {
                TileLighting(
                    totalIntensity = FOW_MEMORY_INTENSITY * aoFactor,
                    colorR = 40,
                    colorG = 55,
                    colorB = 65,
                    inDirectLight = false,
                    isFOWHidden = false,
                    shadowFactor = 0.3f,
                    ambientOcclusion = aoFactor
                )
            } else {
                TileLighting(
                    totalIntensity = AMBIENT_DARK_INTENSITY,
                    colorR = 15,
                    colorG = 20,
                    colorB = 25,
                    inDirectLight = false,
                    isFOWHidden = true,
                    shadowFactor = 0f,
                    ambientOcclusion = aoFactor
                )
            }
        }

        val ambientBase = if (isDiscovered) 0.12f else 0.05f
        val effectiveIntensity = (maxIntensity + ambientBase * aoFactor).coerceIn(0.15f, 1.85f)

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
            isFOWHidden = false,
            shadowFactor = minShadowFactor,
            ambientOcclusion = aoFactor
        )
    }

    /**
     * Backward-compatible 2D lighting calculation that calls calculate3DLighting.
     */
    fun calculateLighting(
        gridX: Float,
        gridY: Float,
        lightSources: List<LightSource>,
        discoveredTiles: Set<Pair<Int, Int>>,
        animTime: Float
    ): TileLighting {
        return calculate3DLighting(
            gridX = gridX,
            gridY = gridY,
            elevation = 0f,
            normal = SurfaceNormals.TOP,
            mapGrid = null,
            lightSources = lightSources,
            discoveredTiles = discoveredTiles,
            animTime = animTime,
            enableShadows = false
        )
    }

    /**
     * 3D Raymarching Shadow Occlusion calculation.
     * Traces a ray from lightPos (L) to targetPos (P) through the 3D map grid.
     * Detects wall occluders (Height = 1.0) and calculates soft penumbra falloff.
     */
    fun calculate3DRayShadow(
        lightPos: Vector3,
        targetPos: Vector3,
        mapGrid: List<List<TileType>>,
        lightRadius: Float
    ): Float {
        val rows = mapGrid.size
        if (rows == 0) return 1.0f
        val cols = mapGrid[0].size

        val ray = targetPos - lightPos
        val totalDist = ray.length()
        if (totalDist < 0.15f) return 1.0f // Very close to light source

        val rayDir = ray.normalized()
        val numSteps = (totalDist / RAY_STEP_SIZE).toInt().coerceIn(2, 48)
        val actualStepSize = totalDist / numSteps

        var currentPos = lightPos
        var shadowOcclusion = 1.0f

        // Step through grid from light towards target
        for (step in 1 until numSteps) {
            currentPos = currentPos + (rayDir * actualStepSize)
            val cx = currentPos.x.toInt()
            val cy = currentPos.y.toInt()

            // Check if current ray point is inside grid boundaries
            if (cy in 0 until rows && cx in 0 until cols) {
                // Ignore the starting tile and exact destination tile
                val isStartCell = (cx == lightPos.x.toInt() && cy == lightPos.y.toInt())
                val isTargetCell = (cx == targetPos.x.toInt() && cy == targetPos.y.toInt())

                if (!isStartCell && !isTargetCell) {
                    val tile = mapGrid[cy][cx]
                    if (tile == TileType.WALL) {
                        // Wall has full height (0.0 to 1.0)
                        val wallHeight = 1.0f
                        if (currentPos.z < wallHeight) {
                            // Ray hit a solid wall! Calculate soft penumbra based on relative distances
                            val distFromLight = (currentPos - lightPos).length()
                            val distToTarget = (targetPos - currentPos).length()
                            
                            // Soft shadow factor: shadow softens as distance from occluder to receiver increases
                            val penumbra = (distToTarget / (distFromLight + distToTarget + 0.1f)).coerceIn(0.1f, 0.45f)
                            shadowOcclusion = (penumbra * SHADOW_AMBIENT_FLOOR).coerceIn(0.08f, 0.35f)
                            return shadowOcclusion
                        }
                    }
                }
            }
        }

        return shadowOcclusion
    }

    /**
     * Computes Ambient Occlusion (AO) factor for ground floor tiles based on neighboring solid walls.
     * Provides subtle contact shadow depth at the base and corners of 3D geometry.
     */
    fun calculateAmbientOcclusion(
        cellX: Int,
        cellY: Int,
        mapGrid: List<List<TileType>>
    ): Float {
        val rows = mapGrid.size
        val cols = if (rows > 0) mapGrid[0].size else 0

        var wallNeighbors = 0
        val offsets = arrayOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1),
            Pair(-1, -1), Pair(1, -1), Pair(-1, 1), Pair(1, 1)
        )

        for ((dx, dy) in offsets) {
            val nx = cellX + dx
            val ny = cellY + dy
            if (ny in 0 until rows && nx in 0 until cols) {
                if (mapGrid[ny][nx] == TileType.WALL) {
                    wallNeighbors++
                }
            }
        }

        // Return AO factor: 1.0 (no neighbor walls) down to 0.58 (surrounded by walls)
        return (1.0f - (wallNeighbors * 0.052f)).coerceIn(0.58f, 1.0f)
    }

    /**
     * Calculates dynamic projected isometric ground shadows cast by a 3D object (wall, player, enemy)
     * based on active dynamic light sources.
     */
    fun calculateProjectedShadows(
        objectX: Float,
        objectY: Float,
        objectHeight: Float,
        lightSources: List<LightSource>
    ): List<ProjectedShadow> {
        val shadows = mutableListOf<ProjectedShadow>()

        for (light in lightSources) {
            val dx = objectX - light.gridX
            val dy = objectY - light.gridY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist > light.radius || dist < 0.08f) continue

            // Direction vector pointing away from light source
            val dirX = dx / dist
            val dirY = dy / dist

            // Shadow length proportional to object height and relative light elevation
            val lightZ = max(light.elevation, 0.4f)
            val shadowLength = ((objectHeight / lightZ) * (1.0f - (dist / light.radius) * 0.35f)).coerceIn(0.4f, 2.2f)

            // Opacity decreases with distance from light
            val opacity = ((1.0f - (dist / light.radius)) * light.intensity * 0.75f).coerceIn(0.15f, 0.85f)

            val shadowGlyph = when {
                opacity > 0.6f -> "▓▓▓"
                opacity > 0.35f -> "▒▒▒"
                else -> "░░░"
            }

            shadows.add(
                ProjectedShadow(
                    originX = objectX,
                    originY = objectY,
                    shadowDirX = dirX,
                    shadowDirY = dirY,
                    length = shadowLength,
                    opacity = opacity,
                    shadowGlyph = shadowGlyph,
                    lightColorR = light.colorR,
                    lightColorG = light.colorG,
                    lightColorB = light.colorB
                )
            )
        }

        return shadows
    }

    /**
     * Blends base character/surface color with 3D dynamic lighting, shadow occlusion,
     * ANSI/TrueColor palettes, contrast lookups, and GPU spatial dithering.
     */
    fun blendColorWithLighting(
        baseR: Int,
        baseG: Int,
        baseB: Int,
        lighting: TileLighting,
        palette: Int,
        gridX: Int = 0,
        gridY: Int = 0,
        enableDither: Boolean = true
    ): Int {
        val intensity = lighting.totalIntensity * lighting.shadowFactor * lighting.ambientOcclusion

        // 1. Modulate with photon chromatic tint
        val rawR = ((baseR * 0.42f + lighting.colorR * 0.58f) * intensity).toInt().coerceIn(0, 255)
        val rawG = ((baseG * 0.42f + lighting.colorG * 0.58f) * intensity).toInt().coerceIn(0, 255)
        val rawB = ((baseB * 0.42f + lighting.colorB * 0.58f) * intensity).toInt().coerceIn(0, 255)

        // 2. Select Contrast Curve LUT (S-Curve for crisp depth, Filmic for intense lights)
        val contrastLUT = if (intensity > 1.25f) {
            ContrastAndShapeLookup.FILMIC_ACES_LUT
        } else {
            ContrastAndShapeLookup.S_CURVE_LUT
        }

        val contrastR = contrastLUT[rawR]
        val contrastG = contrastLUT[rawG]
        val contrastB = contrastLUT[rawB]

        // 3. Map palette index to ColorizerMode
        val colorizerMode = when (palette) {
            1 -> ColorizerMode.ANSI_256
            2 -> ColorizerMode.ANSI_16
            3 -> ColorizerMode.PHOSPHOR_GREEN
            4 -> ColorizerMode.AMBER_TERMINAL
            5 -> ColorizerMode.NEON_CYAN
            6 -> ColorizerMode.MATRIX_RAIN
            else -> ColorizerMode.TRUECOLOR_HDR
        }

        // 4. Apply Spatial Bayer Dithering & GPU-accelerated Palette Quantization
        return if (enableDither && !lighting.isFOWHidden) {
            GpuQuantizationPipeline.applyOrderedDitheredQuantization(
                r = contrastR,
                g = contrastG,
                b = contrastB,
                x = gridX,
                y = gridY,
                ditherStrength = 14f,
                colorizerMode = colorizerMode
            )
        } else {
            AnsiTrueColorEngine.colorize(
                r = contrastR,
                g = contrastG,
                b = contrastB,
                mode = colorizerMode
            )
        }
    }

    /**
     * Creates a new dedicated LightMapBuffer instance tied to this lighting engine.
     */
    fun createLightMapBuffer(width: Int = 32, height: Int = 32): LightMapBuffer {
        return LightMapBuffer(width = width, height = height, lightingEngine = this)
    }
}
