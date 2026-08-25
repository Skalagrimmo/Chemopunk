package com.example

import com.example.data.TileType
import com.example.engine.LightMapBuffer
import com.example.engine.LightSource
import com.example.engine.LightType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LightMapBufferTest {

    @Test
    fun testLightMapBufferBrightnessAdjustedByDistance() {
        val lightMap = LightMapBuffer(width = 16, height = 16)

        val grid = List(16) { List(16) { TileType.FLOOR } }
        val lightSource = LightSource(
            id = "central_beacon",
            gridX = 8.5f,
            gridY = 8.0f,
            colorR = 255,
            colorG = 200,
            colorB = 50,
            intensity = 1.5f,
            radius = 6.0f,
            type = LightType.EXTRACTION_BEACON,
            flickerIntensity = 0f
        )

        val discovered = (0..15).flatMap { y -> (0..15).map { x -> Pair(x, y) } }.toSet()

        lightMap.computeLightMap(
            mapGrid = grid,
            lightSources = listOf(lightSource),
            discoveredTiles = discovered,
            animTime = 0f,
            enableShadows = false
        )

        // Close to light source (distance ~ 0.5)
        val closeBrightness = lightMap.getGlyphBrightness(gridX = 8.0f, gridY = 8.5f)
        // Mid-distance from light source (distance ~ 3.0)
        val midBrightness = lightMap.getGlyphBrightness(gridX = 8.0f, gridY = 11.0f)
        // Far away from light source (distance ~ 7.0 > radius)
        val farBrightness = lightMap.getGlyphBrightness(gridX = 8.0f, gridY = 15.0f)

        assertTrue("Brightness should be highest near light center ($closeBrightness > $midBrightness)", closeBrightness > midBrightness)
        assertTrue("Brightness should fall off with distance ($midBrightness > $farBrightness)", midBrightness > farBrightness)
        assertEquals("Distance calculation should accurately track nearest light", 0.5f, lightMap.getDistanceToNearestLight(8.0f, 8.5f), 0.1f)
    }

    @Test
    fun testGlyphColorAdjustmentFromLightMap() {
        val lightMap = LightMapBuffer(width = 10, height = 10)
        val grid = List(10) { List(10) { TileType.FLOOR } }
        val cyanLight = LightSource(
            id = "cyan_pool",
            gridX = 3.0f,
            gridY = 3.0f,
            colorR = 0,
            colorG = 255,
            colorB = 255,
            intensity = 1.4f,
            radius = 5.0f,
            type = LightType.BIO_LUMINESCENT,
            flickerIntensity = 0f
        )

        val discovered = setOf(Pair(3, 3), Pair(3, 4))
        lightMap.computeLightMap(
            mapGrid = grid,
            lightSources = listOf(cyanLight),
            discoveredTiles = discovered,
            animTime = 0f,
            enableShadows = false
        )

        val litGlyph = lightMap.applyLightToGlyph(
            baseChar = '@',
            baseR = 100,
            baseG = 100,
            baseB = 100,
            gridX = 3.5f,
            gridY = 3.5f,
            palette = 0
        )

        val red = android.graphics.Color.red(litGlyph.colorArgb)
        val green = android.graphics.Color.green(litGlyph.colorArgb)
        val blue = android.graphics.Color.blue(litGlyph.colorArgb)

        assertTrue("Cyan light should tint green and blue channels above red", green > red && blue > red)
        assertTrue("Alpha should be strong when illuminated", litGlyph.alpha > 0.6f)
    }

    @Test
    fun testDensityGlyphSelectionBasedOnLightIntensity() {
        val lightMap = LightMapBuffer(width = 10, height = 10)
        val grid = List(10) { List(10) { TileType.FLOOR } }
        val flare = LightSource(
            id = "flare",
            gridX = 2.0f,
            gridY = 2.0f,
            colorR = 255,
            colorG = 50,
            colorB = 50,
            intensity = 1.8f,
            radius = 4.0f,
            type = LightType.FLARE_EMERGENCY,
            flickerIntensity = 0f
        )

        val discovered = (0..9).flatMap { y -> (0..9).map { x -> Pair(x, y) } }.toSet()
        lightMap.computeLightMap(
            mapGrid = grid,
            lightSources = listOf(flare),
            discoveredTiles = discovered,
            animTime = 0f,
            enableShadows = false
        )

        val denseGlyph = lightMap.selectDensityGlyph(gridX = 2.2f, gridY = 2.2f, densityRamp = " .:-=+*#%@")
        val sparseGlyph = lightMap.selectDensityGlyph(gridX = 5.0f, gridY = 5.0f, densityRamp = " .:-=+*#%@")

        val denseIndex = " .:-=+*#%@".indexOf(denseGlyph)
        val sparseIndex = " .:-=+*#%@".indexOf(sparseGlyph)

        assertTrue("Closer point should select a denser glyph ($denseIndex >= $sparseIndex)", denseIndex >= sparseIndex)
    }
}
