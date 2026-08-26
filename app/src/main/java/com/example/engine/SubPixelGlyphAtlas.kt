package com.example.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generates a sub-pixel glyph atlas texture with 64 glyphs.
 * Each glyph encodes a specific 6-subpixel pattern (2 columns x 3 rows = 6 bits = 64 patterns).
 * Glyphs are rendered as smooth SDF-based shapes rather than binary bitmaps,
 * enabling anti-aliased sub-pixel rendering with intensity gradients.
 *
 * Atlas layout: 8 columns x 8 rows = 64 glyphs
 * Each glyph cell: 16x24 pixels (2:3 aspect ratio matching sub-pixel grid)
 * Total atlas: 128x192 pixels
 */
object SubPixelGlyphAtlas {

    const val ATLAS_COLS = 8
    const val ATLAS_ROWS = 8
    const val ATLAS_SIZE = ATLAS_COLS * ATLAS_ROWS
    const val GLYPH_CELL_W = 16
    const val GLYPH_CELL_H = 24
    const val ATLAS_WIDTH = ATLAS_COLS * GLYPH_CELL_W   // 128
    const val ATLAS_HEIGHT = ATLAS_ROWS * GLYPH_CELL_H  // 192

    // Sub-pixel centers within a cell (normalized 0..1)
    // Layout: 2 columns x 3 rows
    val SUBPIXEL_CENTERS = arrayOf(
        floatArrayOf(0.25f, 0.17f),  // p0: top-left
        floatArrayOf(0.75f, 0.17f),  // p1: top-right
        floatArrayOf(0.25f, 0.50f),  // p2: middle-left
        floatArrayOf(0.75f, 0.50f),  // p3: middle-right
        floatArrayOf(0.25f, 0.83f),  // p4: bottom-left
        floatArrayOf(0.75f, 0.83f)   // p5: bottom-right
    )

    /**
     * Maps 6 intensities to a 6-bit pattern index (0..63).
     * Each sub-pixel is thresholded at 0.5 to produce a bit.
     */
    fun intensitiesToPattern(intensities: FloatArray): Int {
        var pattern = 0
        for (s in 0 until 6) {
            if (intensities[s] > 0.5f) {
                pattern = pattern or (1 shl s)
            }
        }
        return pattern
    }

    /**
     * Returns UV coordinates [uMin, vMin, uMax, vMax] for a sub-pixel pattern index.
     */
    fun getGlyphUv(patternIndex: Int): FloatArray {
        val idx = patternIndex.coerceIn(0, 63)
        val col = idx % ATLAS_COLS
        val row = idx / ATLAS_COLS
        return floatArrayOf(
            col.toFloat() / ATLAS_COLS,
            row.toFloat() / ATLAS_ROWS,
            (col + 1f) / ATLAS_COLS,
            (row + 1f) / ATLAS_ROWS
        )
    }

    /**
     * Generates the sub-pixel atlas texture as a procedural bitmap.
     * Each glyph is rendered as smooth SDF-based shapes with anti-aliasing.
     * Returns the OpenGL texture handle.
     */
    fun createSubPixelAtlasTexture(): Int {
        val rawGrid = ByteArray(ATLAS_WIDTH * ATLAS_HEIGHT * 4)

        for (pattern in 0 until 64) {
            val col = pattern % ATLAS_COLS
            val row = pattern / ATLAS_COLS
            val cellStartX = col * GLYPH_CELL_W
            val cellStartY = row * GLYPH_CELL_H

            // Decode the 6-bit pattern into sub-pixel on/off
            val subpixels = BooleanArray(6) { s -> (pattern and (1 shl s)) != 0 }

            // Render each pixel of the glyph cell
            for (py in 0 until GLYPH_CELL_H) {
                for (px in 0 until GLYPH_CELL_W) {
                    val nx = px.toFloat() / GLYPH_CELL_W   // normalized 0..1
                    val ny = py.toFloat() / GLYPH_CELL_H

                    // Compute intensity at this pixel by sampling all 6 sub-pixels
                    var intensity = 0f
                    for (s in 0 until 6) {
                        if (!subpixels[s]) continue
                        val cx = SUBPIXEL_CENTERS[s][0]
                        val cy = SUBPIXEL_CENTERS[s][1]
                        val dx = nx - cx
                        val dy = ny - cy
                        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        // Smooth falloff from sub-pixel center
                        val contrib = (1f - (dist / 0.35f).coerceIn(0f, 1f))
                        intensity += contrib * contrib  // quadratic falloff for smoother edges
                    }
                    intensity = intensity.coerceIn(0f, 1f)

                    // Apply anti-aliasing at the glyph boundary
                    val edgeDist = computeEdgeDistance(nx, ny, subpixels)
                    val aa = smoothstep(0f, 0.08f, edgeDist)
                    intensity *= aa

                    val alpha = (intensity * 255f).toInt().toByte()
                    val offset = ((cellStartY + py) * ATLAS_WIDTH + (cellStartX + px)) * 4
                    rawGrid[offset] = (-1).toByte()     // R
                    rawGrid[offset + 1] = (-1).toByte() // G
                    rawGrid[offset + 2] = (-1).toByte() // B
                    rawGrid[offset + 3] = alpha         // A
                }
            }
        }

        val byteBuffer = ByteBuffer.allocateDirect(rawGrid.size).order(ByteOrder.nativeOrder())
        byteBuffer.put(rawGrid)
        byteBuffer.position(0)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            ATLAS_WIDTH, ATLAS_HEIGHT, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
        )

        return textureId
    }

    /**
     * Computes the minimum distance from a point to the boundary of the active sub-pixel region.
     * Used for anti-aliasing at glyph edges.
     */
    private fun computeEdgeDistance(nx: Float, ny: Float, subpixels: BooleanArray): Float {
        var minDist = Float.MAX_VALUE
        for (s in 0 until 6) {
            if (!subpixels[s]) continue
            val cx = SUBPIXEL_CENTERS[s][0]
            val cy = SUBPIXEL_CENTERS[s][1]
            val dx = nx - cx
            val dy = ny - cy
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            minDist = minOf(minDist, dist)
        }
        // Distance from the edge of the occupied region
        return if (minDist < Float.MAX_VALUE) {
            val radius = 0.35f  // sub-pixel influence radius
            radius - minDist
        } else {
            0f
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Precomputed UV lookup table for all 64 patterns.
     */
    val glyphUvs: Array<FloatArray> = Array(64) { getGlyphUv(it) }
}
