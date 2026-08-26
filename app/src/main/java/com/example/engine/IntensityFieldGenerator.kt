package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Signed Distance Function (SDF) primitives and sub-pixel intensity field computation.
 *
 * Each cell has 6 sub-pixel samples (2x3 grid). The SDF field is sampled at each
 * sub-pixel center, then mapped to intensity values via smoothstep thresholds.
 * Produces a 6-element intensity array per cell that drives the sub-pixel glyph lookup.
 */
object IntensityFieldGenerator {

    // ── SDF Primitives ──────────────────────────────────────────────

    /**
     * SDF for a point with a soft radius.
     * Returns 0.0 at center, negative inside, positive outside.
     */
    fun sdfPoint(px: Float, py: Float, cx: Float, cy: Float, radius: Float): Float {
        val dx = px - cx
        val dy = py - cy
        return sqrt(dx * dx + dy * dy) - radius
    }

    /**
     * SDF for a line segment from (ax,ay) to (bx,by).
     */
    fun sdfLine(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-8f) return sdfPoint(px, py, ax, ay, 0f)

        var t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        t = t.coerceIn(0f, 1f)

        val closestX = ax + t * dx
        val closestY = ay + t * dy
        val ddx = px - closestX
        val ddy = py - closestY
        return sqrt(ddx * ddx + ddy * ddy)
    }

    /**
     * SDF for a filled circle.
     */
    fun sdfCircle(px: Float, py: Float, cx: Float, cy: Float, radius: Float): Float {
        return sdfPoint(px, py, cx, cy, radius)
    }

    /**
     * SDF for an axis-aligned rectangle defined by min/max corners.
     */
    fun sdfRect(px: Float, py: Float, minX: Float, minY: Float, maxX: Float, maxY: Float): Float {
        val dx = max(minX - px, 0f, px - maxX)
        val dy = max(minY - py, 0f, py - maxY)
        val outside = sqrt(dx * dx + dy * dy)
        val inside = min(maxX - px, px - minX).coerceAtLeast(0f)
        val dyInside = min(maxY - py, py - minY).coerceAtLeast(0f)
        val insideMin = min(inside, dyInside)
        return if (inside <= 0f || dyInside <= 0f) outside else -insideMin
    }

    /**
     * SDF for a filled convex polygon defined by vertices.
     * Uses the winding number approach for accurate inside/outside.
     */
    fun sdfConvexPoly(px: Float, py: Float, vertices: Array<FloatArray>): Float {
        if (vertices.size < 3) return Float.MAX_VALUE

        var maxDot = -Float.MAX_VALUE
        var edgeDist = Float.MAX_VALUE
        val n = vertices.size

        for (i in 0 until n) {
            val x0 = vertices[i][0]
            val y0 = vertices[i][1]
            val x1 = vertices[(i + 1) % n][0]
            val y1 = vertices[(i + 1) % n][1]

            // Edge normal (pointing outward for CCW winding)
            val ex = x1 - x0
            val ey = y1 - y0
            val nx = -ey
            val ny = ex
            val len = sqrt(nx * nx + ny * ny)
            val nnx = if (len > 1e-8f) nx / len else 0f
            val nny = if (len > 1e-8f) ny / len else 0f

            val dot = (px - x0) * nnx + (py - y0) * nny
            maxDot = maxOf(maxDot, dot)

            edgeDist = minOf(edgeDist, sdfLine(px, py, x0, y0, x1, y1))
        }

        return if (maxDot <= 0f) -edgeDist else maxDot
    }

    // ── SDF Combination Operations ──────────────────────────────────

    /**
     * Smooth union (blend) of two SDF values.
     * k controls blend sharpness (larger = sharper, smaller = smoother).
     */
    fun sdfSmoothUnion(d1: Float, d2: Float, k: Float = 0.1f): Float {
        val h = (0.5f + 0.5f * (d2 - d1) / k).coerceIn(0f, 1f)
        return d2 * (1f - h) + d1 * h - k * h * (1f - h)
    }

    /**
     * Smooth subtraction of d2 from d1.
     */
    fun sdfSmoothSubtract(d1: Float, d2: Float, k: Float = 0.1f): Float {
        val h = (0.5f - 0.5f * (d2 + d1) / k).coerceIn(0f, 1f)
        return d1 * (1f - h) - d2 * h + k * h * (1f - h)
    }

    /**
     * Smooth intersection of two SDF values.
     */
    fun sdfSmoothIntersect(d1: Float, d2: Float, k: Float = 0.1f): Float {
        val h = (0.5f - 0.5f * (d2 - d1) / k).coerceIn(0f, 1f)
        return d2 * h + d1 * (1f - h) + k * h * (1f - h)
    }

    // ── SDF to Intensity Mapping ────────────────────────────────────

    /**
     * Converts an SDF value to an intensity [0..1] with smooth anti-aliased edges.
     * @param sdf signed distance (negative = inside, 0 = edge, positive = outside)
     * @param radius half-thickness of the rendered stroke
     * @param edgeSoftness controls anti-aliasing width
     */
    fun sdfToIntensity(sdf: Float, radius: Float, edgeSoftness: Float = 0.02f): Float {
        val d = abs(sdf) - radius
        return (1f - smoothstep(-edgeSoftness, edgeSoftness, d)).coerceIn(0f, 1f)
    }

    /**
     * Converts an SDF value to a filled intensity [0..1].
     * Negative SDF = inside = full intensity.
     */
    fun sdfToFilledIntensity(sdf: Float, edgeSoftness: Float = 0.02f): Float {
        return (1f - smoothstep(-edgeSoftness, edgeSoftness, sdf)).coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ── Intensity Field Sampling ────────────────────────────────────

    /**
     * IntensityField holds 6 sub-pixel intensities for one cell.
     */
    data class IntensityField(val intensities: FloatArray) {
        companion object {
            val EMPTY = IntensityField(FloatArray(6) { 0f })
        }

        fun toPatternIndex(): Int = SubPixelGlyphAtlas.intensitiesToPattern(intensities)

        fun maxIntensity(): Float = intensities.maxOrNull() ?: 0f
    }

    /**
     * Samples an SDF function at all 6 sub-pixel centers of a cell,
     * then maps each sample to an intensity value.
     *
     * @param cellMinX left edge of cell in world space
     * @param cellMinY top edge of cell in world space
     * @param cellW width of cell in world space
     * @param cellH height of cell in world space
     * @param sdfFunc function(nx, ny) -> SDF value, where nx/ny are normalized [0..1] within cell
     * @param threshold the SDF value at which the glyph transitions (default 0 = boundary)
     * @param edgeSoftness anti-aliasing width
     */
    fun sampleField(
        cellMinX: Float,
        cellMinY: Float,
        cellW: Float,
        cellH: Float,
        threshold: Float = 0f,
        edgeSoftness: Float = 0.02f,
        sdfFunc: (Float, Float) -> Float
    ): IntensityField {
        val intensities = FloatArray(6)

        for (s in 0 until 6) {
            val cx = SubPixelGlyphAtlas.SUBPIXEL_CENTERS[s][0]
            val cy = SubPixelGlyphAtlas.SUBPIXEL_CENTERS[s][1]
            val sdf = sdfFunc(cx, cy)
            intensities[s] = sdfToFilledIntensity(sdf - threshold, edgeSoftness)
        }

        return IntensityField(intensities)
    }

    /**
     * Samples an SDF function at all 6 sub-pixel centers for a stroke (line/outline).
     */
    fun sampleStrokeField(
        cellMinX: Float,
        cellMinY: Float,
        cellW: Float,
        cellH: Float,
        strokeWidth: Float = 0.05f,
        edgeSoftness: Float = 0.02f,
        sdfFunc: (Float, Float) -> Float
    ): IntensityField {
        val intensities = FloatArray(6)

        for (s in 0 until 6) {
            val cx = SubPixelGlyphAtlas.SUBPIXEL_CENTERS[s][0]
            val cy = SubPixelGlyphAtlas.SUBPIXEL_CENTERS[s][1]
            val sdf = sdfFunc(cx, cy)
            intensities[s] = sdfToIntensity(sdf, strokeWidth, edgeSoftness)
        }

        return IntensityField(intensities)
    }

    /**
     * Samples at world-space sub-pixel positions (used for cross-cell SDF evaluation).
     * Returns intensity at a single sub-pixel in the cell at (gridX, gridY).
     */
    fun sampleWorldSubPixel(
        subPixelIndex: Int,
        gridX: Int,
        gridY: Int,
        tileSize: Float,
        worldSdfFunc: (Float, Float) -> Float
    ): Float {
        val worldX = (gridX + SubPixelGlyphAtlas.SUBPIXEL_CENTERS[subPixelIndex][0]) * tileSize
        val worldY = (gridY + SubPixelGlyphAtlas.SUBPIXEL_CENTERS[subPixelIndex][1]) * tileSize
        val sdf = worldSdfFunc(worldX, worldY)
        return sdfToFilledIntensity(sdf)
    }

    /**
     * Accumulates multiple SDF layers for a single cell.
     * Each primitive in the layer list contributes to the final intensity.
     *
     * @param layers list of (SDF function, weight, blendMode) triples
     *   blendMode: 0 = add, 1 = union, 2 = subtract
     */
    fun accumulateLayers(
        cellMinX: Float,
        cellMinY: Float,
        cellW: Float,
        cellH: Float,
        edgeSoftness: Float = 0.02f,
        layers: List<Triple<(Float, Float) -> Float, Float, Int>>
    ): IntensityField {
        val intensities = FloatArray(6)

        for (s in 0 until 6) {
            val cx = SubPixelGlyphAtlas.SUBPIXEL_CENTERS[s][0]
            val cy = SubPixelGlyphAtlas.SUBPIXEL_CENTERS[s][1]

            var combinedSdf = Float.MAX_VALUE
            for ((sdfFunc, weight, blendMode) in layers) {
                val sdf = sdfFunc(cx, cy) * weight
                combinedSdf = when (blendMode) {
                    0 -> combinedSdf + sdf          // additive (for stacked effects)
                    1 -> sdfSmoothUnion(combinedSdf, sdf)   // smooth union
                    2 -> sdfSmoothSubtract(combinedSdf, sdf) // smooth subtraction
                    else -> sdfSmoothUnion(combinedSdf, sdf)
                }
            }

            intensities[s] = sdfToFilledIntensity(combinedSdf, edgeSoftness)
        }

        return IntensityField(intensities)
    }
}
