package com.example

import com.example.engine.DynamicLightingEngine
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
class DynamicLightingEngineTest {

    private val lightingEngine = DynamicLightingEngine()

    @Test
    fun testPointLightIlluminatesNearbyTile() {
        val torch = LightSource(
            id = "test_torch",
            gridX = 5.0f,
            gridY = 5.0f,
            colorR = 255,
            colorG = 180,
            colorB = 40,
            intensity = 1.5f,
            radius = 4.0f,
            type = LightType.POINT_TORCH,
            flickerIntensity = 0f
        )

        val discovered = setOf(Pair(5, 5), Pair(5, 6))

        // Tile right next to torch (distance 1.0)
        val lighting = lightingEngine.calculateLighting(
            gridX = 5.5f,
            gridY = 5.5f,
            lightSources = listOf(torch),
            discoveredTiles = discovered,
            animTime = 0f
        )

        assertTrue("Tile close to point torch should be illuminated", lighting.inDirectLight)
        assertFalse("Tile close to point torch should not be hidden in FOW", lighting.isFOWHidden)
        assertTrue("Intensity should be strong near light source", lighting.totalIntensity > 0.8f)
    }

    @Test
    fun testFogOfWarMemoryVsHiddenDarkness() {
        val discovered = setOf(Pair(2, 2))

        // Tile that is discovered but has no active light sources nearby
        val memoryLighting = lightingEngine.calculateLighting(
            gridX = 2.5f,
            gridY = 2.5f,
            lightSources = emptyList(),
            discoveredTiles = discovered,
            animTime = 0f
        )

        assertFalse(memoryLighting.inDirectLight)
        assertFalse("Discovered tile should be visible as memory in shadow", memoryLighting.isFOWHidden)
        assertEquals(0.22f, memoryLighting.totalIntensity, 0.01f)

        // Tile that has never been visited / discovered
        val darkLighting = lightingEngine.calculateLighting(
            gridX = 15.5f,
            gridY = 15.5f,
            lightSources = emptyList(),
            discoveredTiles = discovered,
            animTime = 0f
        )

        assertFalse(darkLighting.inDirectLight)
        assertTrue("Undiscovered tile without light should be completely hidden in FOW", darkLighting.isFOWHidden)
    }

    @Test
    fun testColorBlendingWithLighting() {
        val torchLighting = com.example.engine.TileLighting(
            totalIntensity = 1.2f,
            colorR = 255,
            colorG = 120,
            colorB = 20,
            inDirectLight = true,
            isFOWHidden = false
        )

        val blendedColor = lightingEngine.blendColorWithLighting(
            baseR = 100,
            baseG = 100,
            baseB = 100,
            lighting = torchLighting,
            palette = 0 // Multi-color
        )

        val redChannel = android.graphics.Color.red(blendedColor)
        val blueChannel = android.graphics.Color.blue(blendedColor)

        assertTrue("Red channel should be boosted by orange-red torchlight", redChannel > blueChannel)
    }
}
