package com.example.engine

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * GPU-Accelerated & Low-Overhead Quantization Pipeline.
 *
 * Provides:
 * 1. Precomputed 4x4 and 8x8 Bayer Dithering Matrices for spatial ordered error diffusion.
 * 2. High-performance bitwise color quantization (RGB888 -> RGB565 / RGB444 / Indexed Palettes).
 * 3. Spatial dithering for photon light cones and fog-of-war gradients, preventing color banding.
 * 4. Zero-allocation memory footprint with precalculated bitwise lookup arrays.
 */
object GpuQuantizationPipeline {

    // 4x4 Normalized Bayer Matrix (scaled from 0..15 to -0.5f .. +0.5f offset)
    private val BAYER_4X4 = floatArrayOf(
         0f / 16f - 0.5f,  8f / 16f - 0.5f,  2f / 16f - 0.5f, 10f / 16f - 0.5f,
        12f / 16f - 0.5f,  4f / 16f - 0.5f, 14f / 16f - 0.5f,  6f / 16f - 0.5f,
         3f / 16f - 0.5f, 11f / 16f - 0.5f,  1f / 16f - 0.5f,  9f / 16f - 0.5f,
        15f / 16f - 0.5f,  7f / 16f - 0.5f, 13f / 16f - 0.5f,  5f / 16f - 0.5f
    )

    // 8x8 Bayer Dither Matrix for ultra-smooth spatial diffusion
    private val BAYER_8X8 = FloatArray(64) { i ->
        val x = i % 8
        val y = i / 8
        // Standard bit-reversal Bayer generator
        val v = (((x xor y) and 1) shl 5) or
                (((y and 1)) shl 4) or
                ((((x xor y) shr 1) and 1) shl 3) or
                (((y shr 1) and 1) shl 2) or
                ((((x xor y) shr 2) and 1) shl 1) or
                ((y shr 2) and 1)
        (v / 64.0f) - 0.5f
    }

    /**
     * Retrieves the 4x4 spatial Bayer dither threshold offset for pixel/tile coordinate (x, y).
     * Returns a float in range [-0.5 .. +0.5].
     */
    fun getBayer4x4Offset(x: Int, y: Int): Float {
        val bx = (x and 3)
        val by = (y and 3)
        return BAYER_4X4[by * 4 + bx]
    }

    /**
     * Retrieves the 8x8 spatial Bayer dither threshold offset for fine-grain photon dithering.
     */
    fun getBayer8x8Offset(x: Int, y: Int): Float {
        val bx = (x and 7)
        val by = (y and 7)
        return BAYER_8X8[by * 8 + bx]
    }

    /**
     * Fast Bitwise RGB565 Quantization (16-bit color quantization with zero overhead).
     * 5 bits Red, 6 bits Green, 5 bits Blue.
     */
    fun quantizeToRgb565(r: Int, g: Int, b: Int): Int {
        val r5 = (r shr 3) and 0x1F
        val g6 = (g shr 2) and 0x3F
        val b5 = (b shr 3) and 0x1F

        // Expand back to 8-bit ARGB for rendering
        val r8 = (r5 shl 3) or (r5 shr 2)
        val g8 = (g6 shl 2) or (g6 shr 4)
        val b8 = (b5 shl 3) or (b5 shr 2)

        return Color.rgb(r8, g8, b8)
    }

    /**
     * Fast Bitwise RGB444 Retro Quantization (12-bit color quantization).
     */
    fun quantizeToRgb444(r: Int, g: Int, b: Int): Int {
        val r4 = (r shr 4) and 0x0F
        val g4 = (g shr 4) and 0x0F
        val b4 = (b shr 4) and 0x0F

        val r8 = (r4 shl 4) or r4
        val g8 = (g4 shl 4) or g4
        val b8 = (b4 shl 4) or b4

        return Color.rgb(r8, g8, b8)
    }

    /**
     * Applies GPU-style Spatial Bayer Dithering and Quantization to a color at grid/screen coordinate (x, y).
     * Eliminates mach banding on dark gradients and flashlight cones.
     */
    fun applyOrderedDitheredQuantization(
        r: Int,
        g: Int,
        b: Int,
        x: Int,
        y: Int,
        ditherStrength: Float = 18.0f,
        colorizerMode: ColorizerMode = ColorizerMode.TRUECOLOR_HDR
    ): Int {
        val ditherOffset = getBayer4x4Offset(x, y) * ditherStrength
        val ditheredR = (r + ditherOffset).roundToInt().coerceIn(0, 255)
        val ditheredG = (g + ditherOffset).roundToInt().coerceIn(0, 255)
        val ditheredB = (b + ditherOffset).roundToInt().coerceIn(0, 255)

        return when (colorizerMode) {
            ColorizerMode.TRUECOLOR_HDR -> {
                // High-performance 24-bit with dithered smooth gradients
                Color.rgb(ditheredR, ditheredG, ditheredB)
            }
            ColorizerMode.ANSI_256 -> {
                val idx = AnsiTrueColorEngine.rgbToAnsi256Index(ditheredR, ditheredG, ditheredB)
                AnsiTrueColorEngine.getAnsi256Color(idx)
            }
            ColorizerMode.ANSI_16 -> {
                val idx = AnsiTrueColorEngine.rgbToAnsi16Index(ditheredR, ditheredG, ditheredB)
                AnsiTrueColorEngine.ANSI_16_PALETTE[idx]
            }
            else -> {
                AnsiTrueColorEngine.colorize(ditheredR, ditheredG, ditheredB, colorizerMode)
            }
        }
    }
}
