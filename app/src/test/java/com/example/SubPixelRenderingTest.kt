package com.example

import com.example.engine.CellColorComputer
import com.example.engine.IntensityFieldGenerator
import com.example.engine.PrimitiveGenerator
import com.example.engine.SubPixelGlyphAtlas
import com.example.data.Enemy
import com.example.data.InteractiveObject
import com.example.data.InteractiveObjectType
import com.example.data.Player
import com.example.data.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubPixelRenderingTest {

    // ── SubPixelGlyphAtlas Tests ────────────────────────────────────

    @Test
    fun testAtlasHas64Glyphs() {
        assertEquals(64, SubPixelGlyphAtlas.ATLAS_SIZE)
        assertEquals(8, SubPixelGlyphAtlas.ATLAS_COLS)
        assertEquals(8, SubPixelGlyphAtlas.ATLAS_ROWS)
    }

    @Test
    fun testIntensitiesToPattern_allZeros() {
        val pattern = SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals(0, pattern)
    }

    @Test
    fun testIntensitiesToPattern_allOnes() {
        val pattern = SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f))
        assertEquals(63, pattern) // 0b111111
    }

    @Test
    fun testIntensitiesToPattern_singleBit() {
        assertEquals(1, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f)))
        assertEquals(2, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0f, 1f, 0f, 0f, 0f, 0f)))
        assertEquals(4, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0f, 0f, 1f, 0f, 0f, 0f)))
        assertEquals(8, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f)))
        assertEquals(16, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f)))
        assertEquals(32, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f)))
    }

    @Test
    fun testIntensitiesToPattern_thresholdBehavior() {
        // Below threshold -> 0
        assertEquals(0, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0.49f, 0f, 0f, 0f, 0f, 0f)))
        // Above threshold -> 1
        assertEquals(1, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0.51f, 0f, 0f, 0f, 0f, 0f)))
        // Exactly at threshold -> 0 (uses >)
        assertEquals(0, SubPixelGlyphAtlas.intensitiesToPattern(floatArrayOf(0.5f, 0f, 0f, 0f, 0f, 0f)))
    }

    @Test
    fun testGetGlyphUv_bounds() {
        for (pattern in 0 until 64) {
            val uv = SubPixelGlyphAtlas.getGlyphUv(pattern)
            assertEquals(4, uv.size)
            assertTrue("uMin >= 0", uv[0] >= 0f)
            assertTrue("vMin >= 0", uv[1] >= 0f)
            assertTrue("uMax <= 1", uv[2] <= 1f)
            assertTrue("vMax <= 1", uv[3] <= 1f)
            assertTrue("uMax > uMin", uv[2] > uv[0])
            assertTrue("vMax > vMin", uv[3] > uv[1])
        }
    }

    @Test
    fun testGetGlyphUv_clamping() {
        // Out-of-range indices should be clamped
        val uvNeg = SubPixelGlyphAtlas.getGlyphUv(-5)
        val uvOver = SubPixelGlyphAtlas.getGlyphUv(100)
        assertNotNull(uvNeg)
        assertNotNull(uvOver)
        assertEquals(4, uvNeg.size)
        assertEquals(4, uvOver.size)
    }

    @Test
    fun testGlyphUvsArray_matchesGetMethod() {
        for (i in 0 until 64) {
            val fromMethod = SubPixelGlyphAtlas.getGlyphUv(i)
            val fromArray = SubPixelGlyphAtlas.glyphUvs[i]
            assertEquals(fromMethod[0], fromArray[0], 1e-6f)
            assertEquals(fromMethod[1], fromArray[1], 1e-6f)
            assertEquals(fromMethod[2], fromArray[2], 1e-6f)
            assertEquals(fromMethod[3], fromArray[3], 1e-6f)
        }
    }

    @Test
    fun testSubpixelCenters_areNormalized() {
        for (center in SubPixelGlyphAtlas.SUBPIXEL_CENTERS) {
            assertEquals(2, center.size)
            assertTrue("cx in [0,1]", center[0] in 0f..1f)
            assertTrue("cy in [0,1]", center[1] in 0f..1f)
        }
    }

    // ── IntensityFieldGenerator SDF Tests ───────────────────────────

    @Test
    fun testSdfPoint_atCenter() {
        val d = IntensityFieldGenerator.sdfPoint(0.5f, 0.5f, 0.5f, 0.5f, 0.3f)
        assertEquals(-0.3f, d, 1e-5f)
    }

    @Test
    fun testSdfPoint_outside() {
        val d = IntensityFieldGenerator.sdfPoint(1.0f, 1.0f, 0.5f, 0.5f, 0.3f)
        assertTrue("distance should be positive outside", d > 0f)
    }

    @Test
    fun testSdfCircle_inside() {
        val d = IntensityFieldGenerator.sdfCircle(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        assertTrue("inside circle should be negative", d < 0f)
    }

    @Test
    fun testSdfCircle_outside() {
        val d = IntensityFieldGenerator.sdfCircle(2.0f, 2.0f, 0.5f, 0.5f, 0.5f)
        assertTrue("outside circle should be positive", d > 0f)
    }

    @Test
    fun testSdfLine_onLine() {
        val d = IntensityFieldGenerator.sdfLine(0.5f, 0.5f, 0f, 0.5f, 1f, 0.5f)
        assertEquals(0f, d, 0.01f)
    }

    @Test
    fun testSdfLine_awayFromLine() {
        val d = IntensityFieldGenerator.sdfLine(0.5f, 0f, 0f, 0.5f, 1f, 0.5f)
        assertTrue("distance from line should be ~0.5", d > 0.4f)
    }

    @Test
    fun testSdfRect_inside() {
        val d = IntensityFieldGenerator.sdfRect(0.5f, 0.5f, 0.2f, 0.2f, 0.8f, 0.8f)
        assertTrue("inside rect should be negative", d < 0f)
    }

    @Test
    fun testSdfRect_outside() {
        val d = IntensityFieldGenerator.sdfRect(0f, 0f, 0.2f, 0.2f, 0.8f, 0.8f)
        assertTrue("outside rect should be positive", d > 0f)
    }

    @Test
    fun testSdfSmoothUnion() {
        val d1 = 0.1f
        val d2 = -0.1f
        val union = IntensityFieldGenerator.sdfSmoothUnion(d1, d2, 0.05f)
        assertTrue("smooth union should be <= max(d1,d2)", union <= maxOf(d1, d2))
    }

    @Test
    fun testSdfSmoothSubtract() {
        val d1 = -0.2f
        val d2 = -0.1f
        val subtract = IntensityFieldGenerator.sdfSmoothSubtract(d1, d2, 0.05f)
        assertTrue("subtraction should increase distance", subtract > d1)
    }

    @Test
    fun testSdfToIntensity_fullInside() {
        val intensity = IntensityFieldGenerator.sdfToIntensity(-0.5f, 0.1f)
        assertEquals(1f, intensity, 0.05f)
    }

    @Test
    fun testSdfToIntensity_fullOutside() {
        val intensity = IntensityFieldGenerator.sdfToIntensity(0.5f, 0.1f)
        assertEquals(0f, intensity, 0.05f)
    }

    @Test
    fun testSdfToFilledIntensity_inside() {
        val intensity = IntensityFieldGenerator.sdfToFilledIntensity(-0.5f)
        assertEquals(1f, intensity, 0.05f)
    }

    @Test
    fun testSdfToFilledIntensity_outside() {
        val intensity = IntensityFieldGenerator.sdfToFilledIntensity(0.5f)
        assertEquals(0f, intensity, 0.05f)
    }

    @Test
    fun testSampleField_circle() {
        val field = IntensityFieldGenerator.sampleField(
            cellMinX = 0f, cellMinY = 0f, cellW = 1f, cellH = 1f,
            sdfFunc = { nx, ny ->
                IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.3f)
            }
        )
        assertEquals(6, field.intensities.size)
        // Center sub-pixels should have higher intensity than corners
        assertTrue("field should have some intensity", field.maxIntensity() > 0f)
    }

    @Test
    fun testIntensityField_toPatternIndex() {
        val field = IntensityFieldGenerator.IntensityField(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f))
        assertEquals(63, field.toPatternIndex())
    }

    @Test
    fun testIntensityField_empty() {
        val field = IntensityFieldGenerator.IntensityField.EMPTY
        assertEquals(0, field.toPatternIndex())
        assertEquals(0f, field.maxIntensity(), 1e-5f)
    }

    @Test
    fun testAccumulateLayers() {
        val layers = listOf(
            Triple<(Float, Float) -> Float, Float, Int>(
                { nx, ny -> IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.3f) },
                1f, 1
            )
        )
        val field = IntensityFieldGenerator.accumulateLayers(
            cellMinX = 0f, cellMinY = 0f, cellW = 1f, cellH = 1f,
            layers = layers
        )
        assertEquals(6, field.intensities.size)
        assertTrue("should have some intensity", field.maxIntensity() > 0f)
    }

    // ── PrimitiveGenerator Tests ────────────────────────────────────

    @Test
    fun testGeneratePrimitives_emptyGrid() {
        val result = PrimitiveGenerator.generatePrimitives(
            mapGrid = emptyList(),
            player = Player(),
            enemies = emptyList(),
            interactiveObjects = emptyMap()
        )
        assertTrue("empty grid should produce no primitives", result.isEmpty())
    }

    @Test
    fun testGeneratePrimitives_simpleGrid() {
        val grid = listOf(
            listOf(TileType.WALL, TileType.WALL, TileType.WALL),
            listOf(TileType.WALL, TileType.FLOOR, TileType.WALL),
            listOf(TileType.WALL, TileType.WALL, TileType.WALL)
        )
        val player = Player(x = 1.5f, y = 1.5f)
        val result = PrimitiveGenerator.generatePrimitives(
            mapGrid = grid,
            player = player,
            enemies = emptyList(),
            interactiveObjects = emptyMap()
        )
        assertTrue("should generate primitives for each cell", result.isNotEmpty())
        // Each cell should have at least one primitive
        for (cell in result) {
            assertTrue("cell (${cell.gridX},${cell.gridY}) should have primitives", cell.primitives.isNotEmpty())
        }
    }

    @Test
    fun testGeneratePrimitives_withEnemies() {
        val grid = listOf(
            listOf(TileType.WALL, TileType.WALL, TileType.WALL),
            listOf(TileType.WALL, TileType.FLOOR, TileType.WALL),
            listOf(TileType.WALL, TileType.WALL, TileType.WALL)
        )
        val enemy = Enemy(
            id = "test", name = "Rat", hp = 10, maxHp = 10,
            attack = 3, armor = 0, toxicityDamage = 0,
            asciiGlyph = 'r', expReward = 5, lootItemId = null,
            x = 1f, y = 1f
        )
        val result = PrimitiveGenerator.generatePrimitives(
            mapGrid = grid,
            player = Player(x = 1.5f, y = 1.5f),
            enemies = listOf(enemy),
            interactiveObjects = emptyMap()
        )
        assertTrue("should have cells", result.isNotEmpty())
        // Enemy should appear as a primitive in cell (1,1)
        val cell11 = result.first { it.gridX == 1 && it.gridY == 1 }
        assertTrue("enemy cell should have primitives", cell11.primitives.size >= 2)
    }

    @Test
    fun testGeneratePrimitives_withInteractiveObjects() {
        val grid = listOf(
            listOf(TileType.WALL, TileType.WALL),
            listOf(TileType.INTERACTIVE, TileType.FLOOR),
            listOf(TileType.WALL, TileType.WALL)
        )
        val obj = InteractiveObject(
            id = "test_terminal", type = InteractiveObjectType.TERMINAL,
            x = 0, y = 1
        )
        val result = PrimitiveGenerator.generatePrimitives(
            mapGrid = grid,
            player = Player(x = 0.5f, y = 0.5f),
            enemies = emptyList(),
            interactiveObjects = mapOf(Pair(0, 1) to obj)
        )
        assertTrue("should have cells", result.isNotEmpty())
    }

    @Test
    fun testPrimitiveColors_inRange() {
        val grid = listOf(
            listOf(TileType.WALL, TileType.WALL),
            listOf(TileType.FLOOR, TileType.TOXIC_POOL),
            listOf(TileType.WALL, TileType.WALL)
        )
        val result = PrimitiveGenerator.generatePrimitives(
            mapGrid = grid,
            player = Player(x = 0.5f, y = 0.5f),
            enemies = emptyList(),
            interactiveObjects = emptyMap()
        )
        for (cell in result) {
            for (prim in cell.primitives) {
                assertTrue("R in [0,1]", prim.colorR in 0f..1f)
                assertTrue("G in [0,1]", prim.colorG in 0f..1f)
                assertTrue("B in [0,1]", prim.colorB in 0f..1f)
            }
        }
    }

    // ── CellColorComputer Tests ─────────────────────────────────────

    @Test
    fun testTileBaseColor_allTiles() {
        for (type in TileType.values()) {
            val color = CellColorComputer.tileBaseColor(type)
            assertTrue("R in [0,1]", color.fgR in 0f..1f)
            assertTrue("G in [0,1]", color.fgG in 0f..1f)
            assertTrue("B in [0,1]", color.fgB in 0f..1f)
            assertTrue("lightIntensity > 0", color.lightIntensity > 0f)
        }
    }

    @Test
    fun testApplyLighting_noLights() {
        val base = CellColorComputer.tileBaseColor(TileType.FLOOR)
        val lit = CellColorComputer.applyLighting(base, 1f, 1f, emptyList(), ambientLight = 0.15f)
        // With no lights, should use ambient only
        assertTrue("should have some intensity from ambient", lit.lightIntensity > 0f)
        assertTrue("should have intensity from ambient", lit.lightIntensity <= 0.2f)
    }

    @Test
    fun testApplyLighting_withLights() {
        val base = CellColorComputer.tileBaseColor(TileType.FLOOR)
        val light = com.example.engine.LightSource(
            id = "test", gridX = 1f, gridY = 1f,
            colorR = 255, colorG = 255, colorB = 255,
            intensity = 1.5f, radius = 5f, type = com.example.engine.LightType.POINT_TORCH
        )
        val lit = CellColorComputer.applyLighting(base, 1f, 1f, listOf(light), ambientLight = 0.15f)
        assertTrue("should have higher intensity with light", lit.lightIntensity > 0.2f)
    }

    @Test
    fun testBlendPrimitiveColor() {
        val base = CellColorComputer.tileBaseColor(TileType.FLOOR)
        val blended = CellColorComputer.blendPrimitiveColor(base, 1f, 0f, 0f, 0.5f)
        assertTrue("blended R should be between base and prim", blended.fgR > base.fgR)
    }

    @Test
    fun testPlayerColor_pulsing() {
        val c1 = CellColorComputer.playerColor(0L)
        val c2 = CellColorComputer.playerColor(150L)
        assertNotNull(c1)
        assertNotNull(c2)
        // Colors should be similar but may differ due to pulsing
        assertTrue("player color should be greenish", c1.fgG > c1.fgR)
    }

    @Test
    fun testEnemyColor_byType() {
        val ratColor = CellColorComputer.enemyColor("Mutant Rat", 1.0f)
        val crawlerColor = CellColorComputer.enemyColor("Chem Crawler", 1.0f)
        val slimeColor = CellColorComputer.enemyColor("Toxic Slime", 1.0f)

        // Rat should be reddish
        assertTrue("rat R > G", ratColor.fgR > ratColor.fgG)
        // Slime should be greenish
        assertTrue("slime G > R", slimeColor.fgG > slimeColor.fgR)
    }

    @Test
    fun testEnemyColor_lowHp() {
        val fullHp = CellColorComputer.enemyColor("Test", 1.0f)
        val lowHp = CellColorComputer.enemyColor("Test", 0.1f)
        assertTrue("low HP should be dimmer", lowHp.lightIntensity < fullHp.lightIntensity)
        assertTrue("dying enemies should glow", lowHp.glow > fullHp.glow)
    }
}
