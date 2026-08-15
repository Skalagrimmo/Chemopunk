package com.example

import com.example.data.TileType
import com.example.engine.DynamicLightingEngine
import com.example.engine.LightSource
import com.example.engine.LightType
import com.example.engine.SurfaceNormals
import com.example.engine.Vector3
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

    @Test
    fun test3DRaymarchedShadowOcclusionBehindWall() {
        // Map with a solid wall in the middle: Light at (2, 2) -> Wall at (3, 2) -> Target behind wall at (5, 2)
        val grid = listOf(
            listOf(TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR),
            listOf(TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR),
            listOf(TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL,  TileType.FLOOR, TileType.FLOOR),
            listOf(TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR)
        )

        val light = LightSource(
            id = "test_light",
            gridX = 1.5f,
            gridY = 2.5f,
            elevation = 0.5f,
            colorR = 255,
            colorG = 255,
            colorB = 255,
            intensity = 1.5f,
            radius = 8.0f,
            castsShadows = true
        )

        val unoccludedLighting = lightingEngine.calculate3DLighting(
            gridX = 2.5f,
            gridY = 2.5f,
            elevation = 0f,
            normal = SurfaceNormals.FLOOR,
            mapGrid = grid,
            lightSources = listOf(light),
            discoveredTiles = setOf(Pair(2, 2), Pair(5, 2)),
            animTime = 0f,
            enableShadows = true
        )

        val occludedLighting = lightingEngine.calculate3DLighting(
            gridX = 5.5f,
            gridY = 2.5f,
            elevation = 0f,
            normal = SurfaceNormals.FLOOR,
            mapGrid = grid,
            lightSources = listOf(light),
            discoveredTiles = setOf(Pair(2, 2), Pair(5, 2)),
            animTime = 0f,
            enableShadows = true
        )

        assertTrue("Occluded tile behind wall should have lower shadow factor", occludedLighting.shadowFactor < unoccludedLighting.shadowFactor)
        assertTrue("Occluded tile should have significantly reduced intensity due to shadow casting", occludedLighting.totalIntensity < unoccludedLighting.totalIntensity)
    }

    @Test
    fun test3DLambertianSurfaceNormals() {
        val light = LightSource(
            id = "overhead_light",
            gridX = 5.0f,
            gridY = 5.0f,
            elevation = 2.0f, // High overhead light
            colorR = 255,
            colorG = 255,
            colorB = 255,
            intensity = 1.5f,
            radius = 6.0f
        )

        val topFaceLighting = lightingEngine.calculate3DLighting(
            gridX = 5.0f,
            gridY = 5.0f,
            elevation = 1.0f,
            normal = SurfaceNormals.TOP,
            mapGrid = null,
            lightSources = listOf(light),
            discoveredTiles = setOf(Pair(5, 5)),
            animTime = 0f,
            enableShadows = false
        )

        assertTrue("Top horizontal face directly facing overhead light should receive maximum illumination", topFaceLighting.totalIntensity > 1.0f)
    }

    @Test
    fun testProjectedGroundShadowCalculation() {
        val light = LightSource(
            id = "side_torch",
            gridX = 2.0f,
            gridY = 2.0f,
            elevation = 0.5f,
            colorR = 255,
            colorG = 180,
            colorB = 50,
            intensity = 1.2f,
            radius = 5.0f
        )

        val shadows = lightingEngine.calculateProjectedShadows(
            objectX = 4.0f,
            objectY = 2.0f,
            objectHeight = 1.0f,
            lightSources = listOf(light)
        )

        assertEquals("Should calculate 1 projected shadow from the single light source", 1, shadows.size)
        val shadow = shadows[0]
        assertTrue("Shadow direction X should point away from light (dx > 0)", shadow.shadowDirX > 0.9f)
        assertTrue("Shadow length should be positive", shadow.length > 0.5f)
        assertTrue("Shadow opacity should be greater than zero", shadow.opacity > 0.2f)
    }
}
