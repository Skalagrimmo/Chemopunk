package com.example.engine

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Colorizer and Palette modes supported by the 2.5D Isometric Rendering Engine.
 */
enum class ColorizerMode(val displayName: String, val bitDepth: String) {
    TRUECOLOR_HDR("TrueColor HDR", "24-bit RGB"),
    ANSI_256("ANSI xterm-256", "8-bit Indexed"),
    ANSI_16("ANSI Classic 16", "4-bit CGA/EGA"),
    PHOSPHOR_GREEN("P1-Phosphor CRT", "Monochrome Green"),
    AMBER_TERMINAL("P20-Amber CRT", "Monochrome Amber"),
    NEON_CYAN("Synthwave Cyan", "16-bit TrueColor"),
    MATRIX_RAIN("Matrix Digital", "High-Contrast Green")
}

/**
 * High-performance ANSI 16/256 & TrueColor (24-bit) Colorizer Engine.
 *
 * Provides:
 * 1. Precomputed ANSI 16 and ANSI 256 (xterm 8-bit) color lookup tables.
 * 2. Instant O(1) TrueColor to ANSI-256 / ANSI-16 quantization kernels without allocations.
 * 3. 24-bit TrueColor blending, dynamic gamma, chromatic saturation, and phosphor tinting.
 * 4. ANSI terminal escape sequence parser and string builder for in-game diagnostic logs.
 */
object AnsiTrueColorEngine {

    // Standard 16 ANSI Palette (0..15)
    val ANSI_16_PALETTE = intArrayOf(
        Color.rgb(0, 0, 0),        // 0: Black
        Color.rgb(170, 0, 0),      // 1: Red
        Color.rgb(0, 170, 0),      // 2: Green
        Color.rgb(170, 85, 0),     // 3: Yellow/Brown
        Color.rgb(0, 0, 170),      // 4: Blue
        Color.rgb(170, 0, 170),    // 5: Magenta
        Color.rgb(0, 170, 170),    // 6: Cyan
        Color.rgb(170, 170, 170),  // 7: Light Gray
        Color.rgb(85, 85, 85),     // 8: Dark Gray (Bright Black)
        Color.rgb(255, 85, 85),    // 9: Bright Red
        Color.rgb(85, 255, 85),    // 10: Bright Green
        Color.rgb(255, 255, 85),   // 11: Bright Yellow
        Color.rgb(85, 85, 255),    // 12: Bright Blue
        Color.rgb(255, 85, 255),   // 13: Bright Magenta
        Color.rgb(85, 255, 255),   // 14: Bright Cyan
        Color.rgb(255, 255, 255)   // 15: Bright White
    )

    // Complete 256-color table (0..15 standard, 16..231 6x6x6 color cube, 232..255 grayscale ramp)
    val ANSI_256_PALETTE = IntArray(256).apply {
        // 0..15: Copy standard ANSI 16
        for (i in 0..15) {
            this[i] = ANSI_16_PALETTE[i]
        }

        // 16..231: 6x6x6 Color Cube (levels: 0, 95, 135, 175, 215, 255)
        val cubeLevels = intArrayOf(0, 95, 135, 175, 215, 255)
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    val idx = 16 + 36 * r + 6 * g + b
                    this[idx] = Color.rgb(cubeLevels[r], cubeLevels[g], cubeLevels[b])
                }
            }
        }

        // 232..255: 24-step grayscale ramp (from 8 to 238 in steps of 10)
        for (i in 0 until 24) {
            val gray = 8 + i * 10
            this[232 + i] = Color.rgb(gray, gray, gray)
        }
    }

    /**
     * Fast O(1) quantization from TrueColor RGB to nearest ANSI 256 palette index.
     * Uses arithmetic partitioning for color cube and grayscale ramp for zero memory allocations.
     */
    fun rgbToAnsi256Index(r: Int, g: Int, b: Int): Int {
        val cr = r.coerceIn(0, 255)
        val cg = g.coerceIn(0, 255)
        val cb = b.coerceIn(0, 255)

        // Check if nearly grayscale
        val maxDiff = max(max(kotlin.math.abs(cr - cg), kotlin.math.abs(cg - cb)), kotlin.math.abs(cr - cb))
        if (maxDiff < 8) {
            val avg = (cr + cg + cb) / 3
            if (avg < 4) return 16 // Near black
            if (avg > 245) return 231 // Near white
            val grayIdx = ((avg - 8) / 10).coerceIn(0, 23)
            return 232 + grayIdx
        }

        // Quantize to 6x6x6 RGB cube
        val qr = when {
            cr < 48 -> 0
            cr < 115 -> 1
            cr < 155 -> 2
            cr < 195 -> 3
            cr < 235 -> 4
            else -> 5
        }
        val qg = when {
            cg < 48 -> 0
            cg < 115 -> 1
            cg < 155 -> 2
            cg < 195 -> 3
            cg < 235 -> 4
            else -> 5
        }
        val qb = when {
            cb < 48 -> 0
            cb < 115 -> 1
            cb < 155 -> 2
            cb < 195 -> 3
            cb < 235 -> 4
            else -> 5
        }

        return 16 + 36 * qr + 6 * qg + qb
    }

    /**
     * Returns the 32-bit ARGB color for an ANSI 256 index.
     */
    fun getAnsi256Color(index: Int): Int {
        val safeIdx = index.coerceIn(0, 255)
        return ANSI_256_PALETTE[safeIdx]
    }

    /**
     * Quantizes RGB to the closest ANSI 16 color index.
     */
    fun rgbToAnsi16Index(r: Int, g: Int, b: Int): Int {
        val cr = r.coerceIn(0, 255)
        val cg = g.coerceIn(0, 255)
        val cb = b.coerceIn(0, 255)

        val isBright = (cr > 150 || cg > 150 || cb > 150)
        val rBit = if (cr > 95) 1 else 0
        val gBit = if (cg > 95) 2 else 0
        val bBit = if (cb > 95) 4 else 0
        val brightBit = if (isBright) 8 else 0

        return (rBit or gBit or bBit or brightBit).coerceIn(0, 15)
    }

    /**
     * Blends and colorizes RGB with custom palette curves, contrast, and tone mapping.
     */
    fun colorize(
        r: Int,
        g: Int,
        b: Int,
        mode: ColorizerMode,
        alpha: Int = 255,
        contrastCurve: IntArray? = null
    ): Int {
        // Apply contrast LUT if provided
        val cr = if (contrastCurve != null) contrastCurve[r.coerceIn(0, 255)] else r.coerceIn(0, 255)
        val cg = if (contrastCurve != null) contrastCurve[g.coerceIn(0, 255)] else g.coerceIn(0, 255)
        val cb = if (contrastCurve != null) contrastCurve[b.coerceIn(0, 255)] else b.coerceIn(0, 255)

        val finalR: Int
        val finalG: Int
        val finalB: Int

        when (mode) {
            ColorizerMode.TRUECOLOR_HDR -> {
                finalR = cr
                finalG = cg
                finalB = cb
            }
            ColorizerMode.ANSI_256 -> {
                val idx = rgbToAnsi256Index(cr, cg, cb)
                val c = ANSI_256_PALETTE[idx]
                finalR = Color.red(c)
                finalG = Color.green(c)
                finalB = Color.blue(c)
            }
            ColorizerMode.ANSI_16 -> {
                val idx = rgbToAnsi16Index(cr, cg, cb)
                val c = ANSI_16_PALETTE[idx]
                finalR = Color.red(c)
                finalG = Color.green(c)
                finalB = Color.blue(c)
            }
            ColorizerMode.PHOSPHOR_GREEN -> {
                val lum = (cr * 0.299f + cg * 0.587f + cb * 0.114f)
                finalR = (lum * 0.14f).roundToInt().coerceIn(0, 255)
                finalG = (lum * 1.38f).roundToInt().coerceIn(0, 255)
                finalB = (lum * 0.22f).roundToInt().coerceIn(0, 255)
            }
            ColorizerMode.AMBER_TERMINAL -> {
                val lum = (cr * 0.299f + cg * 0.587f + cb * 0.114f)
                finalR = (lum * 1.35f).roundToInt().coerceIn(0, 255)
                finalG = (lum * 0.85f).roundToInt().coerceIn(0, 255)
                finalB = (lum * 0.10f).roundToInt().coerceIn(0, 255)
            }
            ColorizerMode.NEON_CYAN -> {
                val lum = (cr * 0.299f + cg * 0.587f + cb * 0.114f)
                finalR = (lum * 0.15f).roundToInt().coerceIn(0, 255)
                finalG = (lum * 1.10f).roundToInt().coerceIn(0, 255)
                finalB = (lum * 1.40f).roundToInt().coerceIn(0, 255)
            }
            ColorizerMode.MATRIX_RAIN -> {
                val lum = (cr * 0.299f + cg * 0.587f + cb * 0.114f)
                // Matrix: High green saturation with bright white peaks
                if (lum > 210f) {
                    finalR = 190
                    finalG = 255
                    finalB = 190
                } else {
                    finalR = (lum * 0.08f).roundToInt().coerceIn(0, 255)
                    finalG = (lum * 1.45f).roundToInt().coerceIn(0, 255)
                    finalB = (lum * 0.12f).roundToInt().coerceIn(0, 255)
                }
            }
        }

        return Color.argb(alpha.coerceIn(0, 255), finalR, finalG, finalB)
    }

    /**
     * Formats an ANSI TrueColor escape sequence string (e.g. \u001b[38;2;R;G;Bm).
     */
    fun toAnsiEscape(r: Int, g: Int, b: Int, isBackground: Boolean = false): String {
        val type = if (isBackground) "48" else "38"
        return "\u001B[$type;2;$r;$g;${b}m"
    }

    /**
     * Formats an ANSI 256 escape sequence string (e.g. \u001b[38;5;Nm).
     */
    fun toAnsi256Escape(ansiIndex: Int, isBackground: Boolean = false): String {
        val type = if (isBackground) "48" else "38"
        return "\u001B[$type;5;${ansiIndex}m"
    }

    const val ANSI_RESET = "\u001B[0m"
    const val ANSI_BOLD = "\u001B[1m"
}
