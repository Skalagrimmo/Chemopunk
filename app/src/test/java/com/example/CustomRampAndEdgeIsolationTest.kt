package com.example

import com.example.data.TileType
import com.example.engine.AsciiCharacterBuffer
import com.example.engine.CustomRampEngine
import com.example.engine.SharpEdgeIsolationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Custom Ramps, Sharp Edge Isolation, and Double-Buffered ASCII Vertex rendering pipelines.
 */
class CustomRampAndEdgeIsolationTest {

    @Test
    fun testCustomRampEvaluation() {
        val standardRamp = CustomRampEngine.RampType.PHOSPHOR_GREEN
        assertEquals(8, standardRamp.quantizationSteps.toInt())

        // Test glyph lookup across luminance range
        val darkChar = CustomRampEngine.mapLuminanceToGlyph(0.0f, standardRamp)
        val brightChar = CustomRampEngine.mapLuminanceToGlyph(1.0f, standardRamp)

        assertEquals(' ', darkChar)
        assertEquals('█', brightChar)

        // Test all ramp types from indices
        for (i in 0..5) {
            val ramp = CustomRampEngine.RampType.fromIndex(i)
            assertNotNull(ramp)
            val (r, g, b) = CustomRampEngine.evaluateRampColor(0.8f, 1f, 1f, 1f, ramp)
            assertTrue(r in 0f..1.5f)
            assertTrue(g in 0f..1.5f)
            assertTrue(b in 0f..1.5f)
        }
    }

    @Test
    fun testSharpEdgeIsolationExtraction() {
        val map = listOf(
            listOf(TileType.FLOOR, TileType.WALL, TileType.FLOOR),
            listOf(TileType.FLOOR, TileType.WALL, TileType.FLOOR),
            listOf(TileType.FLOOR, TileType.FLOOR, TileType.FLOOR)
        )

        // Wall tile at (1, 0) should have adjacent step down to floor at (0, 0) and (2, 0)
        val edges = SharpEdgeIsolationEngine.extractTileEdges(map, 1, 0)
        assertTrue("Wall tile should produce boundary edge contours", edges.isNotEmpty())
        assertNotNull("Should detect boundary edge", edges.firstOrNull())

        // Reticle edges for selection
        val reticle = SharpEdgeIsolationEngine.getSelectionReticleEdges(1, 1)
        assertEquals("Selection reticle must have 2 bracket segments", 2, reticle.size)
    }

    @Test
    fun testAsciiCharacterBufferDoubleBuffering() {
        val buffer = AsciiCharacterBuffer(maxQuads = 128)

        // Initial state
        assertEquals(0, buffer.vertexCount)

        // Begin write pass on back buffer
        buffer.beginWrite()
        buffer.pushCharCell(
            cx = 100f,
            cy = 100f,
            halfW = 10f,
            halfH = 10f,
            z = 0.5f,
            char = '@',
            fgR = 1f, fgG = 1f, fgB = 1f, fgA = 1f,
            edgeFlag = 1.0f,
            edgeStrength = 0.85f
        )

        // Vertex count before finishing write should reflect current frame count once swapped
        buffer.finishWrite()

        // After atomic swap, vertex count should be 6 vertices (1 quad = 2 triangles = 6 vertices)
        assertEquals(6, buffer.vertexCount)
        assertNotNull(buffer.floatBuffer)

        // Push another frame with 2 quads
        buffer.beginWrite()
        buffer.pushCharCell(cx = 50f, cy = 50f, halfW = 8f, halfH = 8f, z = 0f, char = '#', fgR = 1f, fgG = 1f, fgB = 1f)
        buffer.pushCharCell(cx = 70f, cy = 70f, halfW = 8f, halfH = 8f, z = 0f, char = '#', fgR = 1f, fgG = 1f, fgB = 1f)
        buffer.finishWrite()

        assertEquals(12, buffer.vertexCount)
    }
}
