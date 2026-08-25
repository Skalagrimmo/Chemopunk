package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * High-performance Double-Buffered Character-based Vertex Engine for OpenGL ES 2.0.
 *
 * Implements:
 * - Ping-Pong Native FloatBuffer Double-Buffering (Buffer A & Buffer B) for zero-tearing lock-free rendering
 * - 24-float (96-byte) SIMD-aligned vertex structure with position, UV, color, photon lighting, and sharp edge isolation flags
 * - Zero-allocation quad and axis-aligned glyph generation
 */
class AsciiCharacterBuffer(maxQuads: Int = 12288) {

    companion object {
        const val FLOATS_PER_VERTEX = 24
        const val VERTICES_PER_QUAD = 6
        const val FLOATS_PER_QUAD = VERTICES_PER_QUAD * FLOATS_PER_VERTEX // 144 floats per quad
        const val BYTES_PER_FLOAT = 4
        const val VERTEX_STRIDE = FLOATS_PER_VERTEX * BYTES_PER_FLOAT // 96 bytes (16-byte aligned)

        const val POS_OFFSET = 0
        const val TEX_OFFSET = 3
        const val FG_OFFSET = 5
        const val BG_OFFSET = 9
        const val LIGHT_PARAMS_OFFSET = 13
        const val LIGHT_COLOR_OFFSET = 17
        const val EDGE_PARAMS_OFFSET = 20

        const val TILE_WIDTH = 68f
        const val TILE_HEIGHT = 34f
        const val WALL_HEIGHT = 32f
    }

    private val maxFloats = maxQuads * FLOATS_PER_QUAD

    // Double-Buffer A
    private val rawArrayA = FloatArray(maxFloats)
    private val floatBufferA: FloatBuffer = ByteBuffer.allocateDirect(maxFloats * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private var vertexCountA = 0

    // Double-Buffer B
    private val rawArrayB = FloatArray(maxFloats)
    private val floatBufferB: FloatBuffer = ByteBuffer.allocateDirect(maxFloats * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private var vertexCountB = 0

    // Buffer state indices: 0 = Buffer A, 1 = Buffer B
    @Volatile
    private var writeBufferIdx = 0
    @Volatile
    private var readBufferIdx = 0

    private var currentFloatIndex = 0

    val vertexCount: Int
        get() = if (readBufferIdx == 0) vertexCountA else vertexCountB

    val floatBuffer: FloatBuffer
        get() = if (readBufferIdx == 0) floatBufferA else floatBufferB

    /**
     * Begins writing geometry into the current back-buffer.
     */
    fun beginWrite() {
        currentFloatIndex = 0
    }

    /**
     * Completes writing into the back-buffer, updates native memory, and swaps front/back buffers.
     */
    fun finishWrite(): FloatBuffer {
        val currentArray = if (writeBufferIdx == 0) rawArrayA else rawArrayB
        val currentBuffer = if (writeBufferIdx == 0) floatBufferA else floatBufferB
        val writtenVertices = currentFloatIndex / FLOATS_PER_VERTEX

        currentBuffer.clear()
        currentBuffer.put(currentArray, 0, currentFloatIndex)
        currentBuffer.limit(currentFloatIndex)
        currentBuffer.position(0)

        if (writeBufferIdx == 0) {
            vertexCountA = writtenVertices
        } else {
            vertexCountB = writtenVertices
        }

        // Atomic swap
        readBufferIdx = writeBufferIdx
        writeBufferIdx = 1 - writeBufferIdx

        return currentBuffer
    }

    // Compatibility methods
    fun clear() = beginWrite()
    fun finish(): FloatBuffer = finishWrite()

    /**
     * Pushes a single vertex into the active write buffer.
     */
    fun pushVertex(
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        fgR: Float, fgG: Float, fgB: Float, fgA: Float,
        bgR: Float, bgG: Float, bgB: Float, bgA: Float,
        lightIntensity: Float, dither: Float, glow: Float, wave: Float,
        tintR: Float, tintG: Float, tintB: Float,
        edgeFlag: Float = 0.0f,
        edgeStrength: Float = 0.0f,
        gridX: Float = 0.0f,
        gridY: Float = 0.0f,
        rampIdx: Float = 0.0f
    ) {
        if (currentFloatIndex + FLOATS_PER_VERTEX > maxFloats) return

        val arr = if (writeBufferIdx == 0) rawArrayA else rawArrayB
        var idx = currentFloatIndex

        arr[idx++] = x
        arr[idx++] = y
        arr[idx++] = z

        arr[idx++] = u
        arr[idx++] = v

        arr[idx++] = fgR
        arr[idx++] = fgG
        arr[idx++] = fgB
        arr[idx++] = fgA

        arr[idx++] = bgR
        arr[idx++] = bgG
        arr[idx++] = bgB
        arr[idx++] = bgA

        arr[idx++] = lightIntensity
        arr[idx++] = dither
        arr[idx++] = glow
        arr[idx++] = wave

        arr[idx++] = tintR
        arr[idx++] = tintG
        arr[idx++] = tintB

        arr[idx++] = edgeFlag
        arr[idx++] = edgeStrength
        arr[idx++] = if (gridX != 0.0f || gridY != 0.0f) gridX else rampIdx
        arr[idx++] = gridY

        currentFloatIndex = idx
    }

    /**
     * Pushes a 2.5D Isometric Quad (composed of 2 triangles = 6 vertices).
     */
    fun pushQuad(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        z: Float,
        char: Char,
        fgR: Float, fgG: Float, fgB: Float, fgA: Float,
        bgR: Float, bgG: Float, bgB: Float, bgA: Float,
        lightIntensity: Float = 1.0f,
        dither: Float = 0.0f,
        glow: Float = 0.0f,
        wave: Float = 0.0f,
        tintR: Float = 1.0f,
        tintG: Float = 1.0f,
        tintB: Float = 1.0f,
        edgeFlag: Float = 0.0f,
        edgeStrength: Float = 0.0f,
        gridX: Float = 0.0f,
        gridY: Float = 0.0f,
        rampIdx: Float = 0.0f
    ) {
        if (currentFloatIndex + FLOATS_PER_QUAD > maxFloats) return

        val uv = FontAtlasGenerator.getGlyphUv(char)
        val uMin = uv[0]
        val vMin = uv[1]
        val uMax = uv[2]
        val vMax = uv[3]

        // Triangle 1: (0, 1, 2)
        pushVertex(x0, y0, z, uMin, vMin, fgR, fgG, fgB, fgA, bgR, bgG, bgB, bgA, lightIntensity, dither, glow, wave, tintR, tintG, tintB, edgeFlag, edgeStrength, gridX, gridY, rampIdx)
        pushVertex(x1, y1, z, uMax, vMin, fgR, fgG, fgB, fgA, bgR, bgG, bgB, bgA, lightIntensity, dither, glow, wave, tintR, tintG, tintB, edgeFlag, edgeStrength, gridX, gridY, rampIdx)
        pushVertex(x2, y2, z, uMax, vMax, fgR, fgG, fgB, fgA, bgR, bgG, bgB, bgA, lightIntensity, dither, glow, wave, tintR, tintG, tintB, edgeFlag, edgeStrength, gridX, gridY, rampIdx)

        // Triangle 2: (0, 2, 3)
        pushVertex(x0, y0, z, uMin, vMin, fgR, fgG, fgB, fgA, bgR, bgG, bgB, bgA, lightIntensity, dither, glow, wave, tintR, tintG, tintB, edgeFlag, edgeStrength, gridX, gridY, rampIdx)
        pushVertex(x2, y2, z, uMax, vMax, fgR, fgG, fgB, fgA, bgR, bgG, bgB, bgA, lightIntensity, dither, glow, wave, tintR, tintG, tintB, edgeFlag, edgeStrength, gridX, gridY, rampIdx)
        pushVertex(x3, y3, z, uMin, vMax, fgR, fgG, fgB, fgA, bgR, bgG, bgB, bgA, lightIntensity, dither, glow, wave, tintR, tintG, tintB, edgeFlag, edgeStrength, gridX, gridY, rampIdx)
    }

    /**
     * Pushes an Axis-Aligned Character Cell centered at (cx, cy).
     */
    fun pushCharCell(
        cx: Float,
        cy: Float,
        halfW: Float,
        halfH: Float,
        z: Float,
        char: Char,
        fgR: Float, fgG: Float, fgB: Float, fgA: Float = 1.0f,
        bgR: Float = 0f, bgG: Float = 0f, bgB: Float = 0f, bgA: Float = 0f,
        lightIntensity: Float = 1.0f,
        dither: Float = 0f,
        glow: Float = 0f,
        wave: Float = 0f,
        tintR: Float = 1.0f,
        tintG: Float = 1.0f,
        tintB: Float = 1.0f,
        edgeFlag: Float = 0.0f,
        edgeStrength: Float = 0.0f,
        gridX: Float = 0.0f,
        gridY: Float = 0.0f,
        rampIdx: Float = 0.0f
    ) {
        val x0 = cx - halfW
        val y0 = cy - halfH
        val x1 = cx + halfW
        val y1 = cy - halfH
        val x2 = cx + halfW
        val y2 = cy + halfH
        val x3 = cx - halfW
        val y3 = cy + halfH

        pushQuad(
            x0, y0, x1, y1, x2, y2, x3, y3, z,
            char,
            fgR, fgG, fgB, fgA,
            bgR, bgG, bgB, bgA,
            lightIntensity, dither, glow, wave,
            tintR, tintG, tintB,
            edgeFlag, edgeStrength, gridX, gridY, rampIdx
        )
    }

    /**
     * Pushes a string of text starting at (startX, startY).
     */
    fun pushText(
        startX: Float,
        startY: Float,
        text: String,
        charW: Float = 12f,
        charH: Float = 16f,
        z: Float = 1.0f,
        fgR: Float = 1f, fgG: Float = 1f, fgB: Float = 1f, fgA: Float = 1f,
        bgR: Float = 0f, bgG: Float = 0f, bgB: Float = 0f, bgA: Float = 0f
    ) {
        val halfW = charW * 0.5f
        val halfH = charH * 0.5f
        var curX = startX + halfW

        for (i in 0 until text.length) {
            val ch = text[i]
            if (ch != ' ') {
                pushCharCell(
                    cx = curX,
                    cy = startY + halfH,
                    halfW = halfW,
                    halfH = halfH,
                    z = z,
                    char = ch,
                    fgR = fgR, fgG = fgG, fgB = fgB, fgA = fgA,
                    bgR = bgR, bgG = bgG, bgB = bgB, bgA = bgA
                )
            }
            curX += charW
        }
    }

    /**
     * Converts 2.5D Isometric grid coordinate (gridX, gridY, elevation) to 2D screen coordinate.
     */
    fun gridToIso(
        gridX: Float,
        gridY: Float,
        elevation: Float,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float
    ): Pair<Float, Float> {
        val relX = gridX - camX
        val relY = gridY - camY

        val isoX = (relX - relY) * (TILE_WIDTH * 0.5f) * zoom
        val isoY = ((relX + relY) * (TILE_HEIGHT * 0.5f) - elevation * WALL_HEIGHT) * zoom

        return Pair(centerX + isoX, centerY + isoY)
    }
}
