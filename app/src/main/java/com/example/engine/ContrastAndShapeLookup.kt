package com.example.engine

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Contrast Tone Curve Modes.
 */
enum class ContrastCurveMode(val displayName: String) {
    LINEAR("Linear Pass-through"),
    S_CURVE_PUNCHY("High-Contrast S-Curve"),
    FILMIC_ACES("Filmic ACES Tone-Mapping"),
    CRT_GAMMA("CRT Gamma 2.2"),
    VIBRANT_EXPONENTIAL("Vibrant Dynamic Boost")
}

/**
 * Contrast LUTs & Morphological Shape Lookup Engine for 2.5D Isometric ASCII Graphics.
 *
 * Features:
 * - Precomputed 256-entry zero-allocation Lookup Tables (LUTs) for contrast curves, gamma, and tone mapping.
 * - Morphological character lookups for dynamic terrain edges, wall junctions, corners, and surface textures.
 * - Fluid and particle animation shape matrices for toxic pools, sparks, and steam.
 * - High-speed O(1) density-to-glyph mappings for ASCII rendering passes.
 */
object ContrastAndShapeLookup {

    // --- 1. PRECOMPUTED CONTRAST LOOKUP TABLES (256-entry IntArrays) ---

    val LINEAR_LUT = IntArray(256) { it }

    /**
     * S-Curve Contrast Enhancement: Smooth cubic Hermite sigmoid $f(x) = 3x^2 - 2x^3$
     * Increases separation between dark void shadows and bright illumination zones.
     */
    val S_CURVE_LUT = IntArray(256) { i ->
        val x = i / 255.0
        val s = (x * x * (3.0 - 2.0 * x))
        // Apply subtle contrast stretch
        val stretched = ((s - 0.5) * 1.15 + 0.5).coerceIn(0.0, 1.0)
        (stretched * 255.0).roundToInt()
    }

    /**
     * Filmic ACES Tone-Mapping Curve: Compresses extreme photon highlights (flares, flashlights)
     * without blowing out color channels into pure white clipping.
     */
    val FILMIC_ACES_LUT = IntArray(256) { i ->
        val x = i / 255.0 * 1.4 // Allow HDR headroom
        val a = 2.51
        val b = 0.03
        val c = 2.43
        val d = 0.59
        val e = 0.14
        val mapped = ((x * (a * x + b)) / (x * (c * x + d) + e)).coerceIn(0.0, 1.0)
        (mapped * 255.0).roundToInt()
    }

    /**
     * Vintage CRT Phosphor Gamma Curve (Gamma ~ 2.2 with slight black-level lift)
     */
    val CRT_GAMMA_LUT = IntArray(256) { i ->
        val x = i / 255.0
        val gamma = x.pow(1.0 / 1.35) // Gamma correction for punchy CRT phosphor glow
        (gamma * 255.0).roundToInt().coerceIn(0, 255)
    }

    /**
     * Vibrant Dynamic Boost LUT: Elevates midtones and enhances saturation in low-light environments.
     */
    val VIBRANT_LUT = IntArray(256) { i ->
        val x = i / 255.0
        val boosted = if (x < 0.5) {
            x * 1.25
        } else {
            1.0 - (1.0 - x) * 0.75
        }
        (boosted.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    }

    fun getContrastLUT(mode: ContrastCurveMode): IntArray {
        return when (mode) {
            ContrastCurveMode.LINEAR -> LINEAR_LUT
            ContrastCurveMode.S_CURVE_PUNCHY -> S_CURVE_LUT
            ContrastCurveMode.FILMIC_ACES -> FILMIC_ACES_LUT
            ContrastCurveMode.CRT_GAMMA -> CRT_GAMMA_LUT
            ContrastCurveMode.VIBRANT_EXPONENTIAL -> VIBRANT_LUT
        }
    }

    // --- 2. DENSITY & LUMINANCE GLYPH LOOKUP RAMPS ---

    // 16-level ultra-fine ASCII density ramp
    val DENSITY_RAMP_FINE = arrayOf(
        " ", "·", "˙", ":", "÷", "-", "=", "+", "*", "o", "%", "a", "#", "@", "M", "█"
    )

    // 5-level block shading ramp
    val DENSITY_RAMP_BLOCKS = arrayOf(
        " ", "░", "▒", "▓", "█"
    )

    // Technical terminal matrix ramp
    val DENSITY_RAMP_TECH = arrayOf(
        " ", ".", ":", ";", "=", "x", "$", "&", "#", "■"
    )

    /**
     * Maps a scalar intensity (0.0 to 1.0+) to a high-contrast density glyph.
     */
    fun getDensityGlyph(intensity: Float, ramp: Array<String> = DENSITY_RAMP_FINE): String {
        val clamped = intensity.coerceIn(0f, 1f)
        val idx = (clamped * (ramp.size - 1)).roundToInt()
        return ramp[idx]
    }

    // --- 3. MORPHOLOGICAL SHAPE LOOKUPS (Walls, Corners, Grates, Doors) ---

    /**
     * Returns the 2.5D wall isometric glyph set based on structural neighbor flags.
     * Neighbor mask: bit 0: North, bit 1: East, bit 2: South, bit 3: West
     */
    data class WallShape(
        val topCap: String,
        val midSection: String,
        val basePillar: String
    )

    fun getWallShape(neighborMask: Int): WallShape {
        return when (neighborMask) {
            // Isolated single pillar
            0 -> WallShape(
                topCap = "/[■]\\",
                midSection = "|#==#|",
                basePillar = "[|###|]"
            )
            // North-South corridor wall
            1, 4, 5 -> WallShape(
                topCap = "/║═║\\",
                midSection = "║###║",
                basePillar = "[|═══|]"
            )
            // East-West corridor wall
            2, 8, 10 -> WallShape(
                topCap = "/═══\\",
                midSection = "|===|",
                basePillar = "[|###|]"
            )
            // Corner junctions
            3 -> WallShape(topCap = "/╗══\\", midSection = "║#==|", basePillar = "[|#╝#|]")
            6 -> WallShape(topCap = "/══╝\\", midSection = "|==#║", basePillar = "[|#╚#|]")
            9 -> WallShape(topCap = "/╔══\\", midSection = "║#==|", basePillar = "[|#╗#|]")
            12 -> WallShape(topCap = "/══╗\\", midSection = "|==#║", basePillar = "[|#╔#|]")
            // 3-way & 4-way intersections
            else -> WallShape(
                topCap = "/╬═╬\\",
                midSection = "╬###╬",
                basePillar = "[|###|]"
            )
        }
    }

    /**
     * Surface texture patterns for metal grates, circuit board conduits, and hazard tiles.
     */
    data class FloorPattern(
        val topRow: String,
        val centerRow: String,
        val bottomRow: String
    )

    fun getFloorPattern(styleVariant: Int): FloorPattern {
        return when (styleVariant % 6) {
            0 -> FloorPattern("/ · \\", "· : ·", "\\ · /")
            1 -> FloorPattern("/ + \\", "+ # +", "\\ + /")
            2 -> FloorPattern("/ - \\", "- · -", "\\ - /")
            3 -> FloorPattern("/ ¤ \\", "¤ = ¤", "\\ ¤ /")
            4 -> FloorPattern("/ ░ \\", "░ ▒ ░", "\\ ░ /")
            else -> FloorPattern("/ · \\", "· * ·", "\\ · /")
        }
    }

    // --- 4. FLUID & TOXIC POOL MORPHOLOGY LOOKUPS ---

    private val FLUID_FRAMES = arrayOf(
        arrayOf("≈ ~ ≈", "% ≈ %", "~ % ~", "≈ % ≈"),
        arrayOf("~ ≈ ~", "¤ % ¤", "≈ ~ ≈", "% ¤ %"),
        arrayOf("· ≈ ·", "≈ % ≈", "· ~ ·", "≈ ¤ ≈"),
        arrayOf("~ · ~", "% ¤ %", "~ · ~", "¤ % ¤")
    )

    fun getFluidCurrentGlyphs(gridX: Int, gridY: Int, animTime: Float): Pair<String, String> {
        val frameIdx = ((animTime * 4.5f + gridX * 1.7f + gridY * 2.3f).toInt() % 4).let { if (it < 0) it + 4 else it }
        val waveIdx = (frameIdx + (gridX xor gridY)) % 4
        val coreGlyph = FLUID_FRAMES[frameIdx][waveIdx]
        val rimGlyph = if (frameIdx % 2 == 0) "/ ~ · \\" else "/ · ~ \\"
        return Pair(coreGlyph, rimGlyph)
    }

    // --- 5. PARTICLE & ATMOSPHERIC EMBER SHAPES ---

    private val PARTICLE_SHAPES = arrayOf("°", "•", "¤", "*", "⁺", "·", "≈", "▲", "˙")

    fun getAtmosphericParticle(progress: Float, seed: Int): String {
        val idx = ((progress * (PARTICLE_SHAPES.size - 1)) + (seed % 3)).toInt().coerceIn(0, PARTICLE_SHAPES.size - 1)
        return PARTICLE_SHAPES[idx]
    }
}
