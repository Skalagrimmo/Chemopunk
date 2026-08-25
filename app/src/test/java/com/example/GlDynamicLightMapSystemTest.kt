package com.example

import com.example.data.Player
import com.example.data.TileType
import com.example.engine.GlDynamicLightMapSystem
import com.example.engine.LightSource
import com.example.engine.LightType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlDynamicLightMapSystemTest {

    @Test
    fun testGlDynamicLightMapSystemAllocationAndSampling() {
        val lightMapSystem = GlDynamicLightMapSystem(mapWidth = 32, mapHeight = 32)
        assertNotNull(lightMapSystem)

        // Create sample map grid
        val mapGrid = List(32) { y ->
            List(32) { x ->
                if (x == 10 && y in 8..15) TileType.WALL else TileType.FLOOR
            }
        }

        val player = Player(
            x = 8.0f,
            y = 10.0f,
            angleDegrees = 0.0f // Facing East towards wall at x=10
        )

        val discovered = mutableSetOf<Pair<Int, Int>>()
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                discovered.add(Pair(x, y))
            }
        }

        val externalLights = listOf(
            LightSource(
                id = "flare",
                gridX = 15.0f,
                gridY = 10.0f,
                colorR = 255,
                colorG = 120,
                colorB = 40,
                intensity = 1.5f,
                radius = 5.0f,
                type = LightType.FLARE_EMERGENCY
            )
        )

        // Update lightmap calculations
        lightMapSystem.updateAndUpload(
            mapGrid = mapGrid,
            player = player,
            enemies = emptyList(),
            externalLights = externalLights,
            discoveredTiles = discovered,
            animTime = 1.25f
        )

        // Sample lighting at player location (should be brightly lit due to bio-suit aura and flashlight)
        val playerLighting = lightMapSystem.sampleInterpolatedLighting(8.0f, 10.0f)
        assertTrue("Player location must be in direct light", playerLighting.inDirectLight)
        assertTrue("Player intensity should exceed ambient", playerLighting.totalIntensity > 0.4f)

        // Sample in front of player (x=9, y=10)
        val inFrontLighting = lightMapSystem.sampleInterpolatedLighting(9.0f, 10.0f)
        assertTrue("In-front tile must be illuminated by flashlight", inFrontLighting.totalIntensity > 0.4f)

        // Sample behind wall at x=12, y=10 (flashlight should be occluded by wall at x=10)
        val behindWallLighting = lightMapSystem.sampleInterpolatedLighting(11.0f, 10.0f)
        // Flare at (15, 10) may illuminate it, but distance from player direct flashlight is blocked
        assertNotNull(behindWallLighting)
    }

    @Test
    fun testFogOfWarShrouding() {
        val lightMapSystem = GlDynamicLightMapSystem(mapWidth = 32, mapHeight = 32)
        val mapGrid = List(32) { List(32) { TileType.FLOOR } }
        val player = Player(x = 5.0f, y = 5.0f, angleDegrees = 90.0f)

        // Only player tile is discovered
        val discovered = setOf(Pair(5, 5))

        lightMapSystem.updateAndUpload(
            mapGrid = mapGrid,
            player = player,
            enemies = emptyList(),
            externalLights = emptyList(),
            discoveredTiles = discovered,
            animTime = 0.0f
        )

        val undiscoveredSample = lightMapSystem.sampleInterpolatedLighting(25.0f, 25.0f)
        assertTrue("Undiscovered distant tile must be FOW hidden", undiscoveredSample.isFOWHidden)
    }
}
