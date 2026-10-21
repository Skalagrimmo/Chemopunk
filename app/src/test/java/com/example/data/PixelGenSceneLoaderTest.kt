package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the PixelGen -> Chemopunk scene bridge, pinned to the baked
 * pixelgen/scene_smoliani_bolota.json asset (PixelGen 1.0, seed 4040,
 * Smoliani Bolota silt_marsh scene, 20x15).
 */
@RunWith(RobolectricTestRunner::class)
class PixelGenSceneLoaderTest {

    private fun loadBakedScene(): PixelGenSceneMap {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PixelGenSceneLoader.loadFromAssets(context, "pixelgen/scene_smoliani_bolota.json")
    }

    @Test
    fun parsesSceneIdentityAndDimensions() {
        val scene = loadBakedScene()
        assertEquals("Smoliani Bolota", scene.sceneName)
        assertEquals("silt_marsh", scene.kind)
        assertEquals(4040L, scene.seed)
        assertEquals(20, scene.grid.first().size)
        assertEquals(15, scene.grid.size)
    }

    @Test
    fun tileCountsMatchPixelGenSummary() {
        // PixelGen collision summary: 50 solid / 224 walk / 26 hazard.
        // After baking: 6 solid prop tiles become INTERACTIVE and 1 walk tile
        // becomes the EXTRACTION_LIFT, so FLOOR drops to 223.
        val counts = HashMap<TileType, Int>()
        loadBakedScene().grid.forEach { row ->
            row.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        assertEquals(44, counts.getOrDefault(TileType.WALL, 0))
        assertEquals(223, counts.getOrDefault(TileType.FLOOR, 0))
        assertEquals(26, counts.getOrDefault(TileType.TOXIC_POOL, 0))
        assertEquals(6, counts.getOrDefault(TileType.INTERACTIVE, 0))
        assertEquals(1, counts.getOrDefault(TileType.EXTRACTION_LIFT, 0))
    }

    @Test
    fun spawnAndEnemyPositionsAreBakedFromTheScene() {
        val scene = loadBakedScene()
        assertEquals(Pair(2.5f, 12.5f), scene.playerStart)
        assertEquals(listOf(Pair(10.5f, 10.5f)), scene.enemySpawns)
    }

    @Test
    fun exitIsTheFarthestWalkableTileFromSpawn() {
        val scene = loadBakedScene()
        assertEquals(Pair(19.5f, 1.5f), scene.exitPosition)
        assertEquals(TileType.EXTRACTION_LIFT, scene.grid[1][19])
    }

    @Test
    fun interactiveObjectsOverridePropTilesAndKeepPixelGenKinds() {
        val scene = loadBakedScene()
        assertEquals(6, scene.interactiveObjects.size)
        assertEquals(
            listOf(
                InteractiveObjectType.TERMINAL, // printing_press_altar (7, 2)
                InteractiveObjectType.BEACON,   // nanolith_shrine (12, 1)
                InteractiveObjectType.SWITCH,   // mask_cross (1, 2)
                InteractiveObjectType.SWITCH,   // brazier_lit (4, 4)
                InteractiveObjectType.TERMINAL, // warning_board (6, 9)
                InteractiveObjectType.LOCKER    // biogel_barrel_leaking (15, 13)
            ),
            scene.interactiveObjects.map { it.type }
        )
        assertEquals(TileType.INTERACTIVE, scene.grid[2][7])
        assertEquals(TileType.INTERACTIVE, scene.grid[1][12])
        assertEquals("pixelgen_printing_press_altar_7_2", scene.interactiveObjects.first().id)
    }

    @Test
    fun corruptScenesAreRejected() {
        // Row shorter than the declared width must be rejected.
        val badRow = "{" +
            "\"format\":\"chemopunk-pixelgen-scene/1\"," +
            "\"name\":\"x\",\"kind\":\"k\",\"seed\":1," +
            "\"width\":3,\"height\":1," +
            "\"collision\":[\"ss\"]," +
            "\"legend\":{\"s\":\"solid\"}," +
            "\"spawn\":{\"x\":0,\"y\":0}" +
            "}"
        try {
            PixelGenSceneLoader.load(badRow)
            fail("Expected rejection for a malformed collision row")
        } catch (e: RuntimeException) {
            assertTrue(e.message, e.message?.contains("length") == true)
        }

        // An unknown legend token must be rejected instead of guessed.
        val unknownToken = "{" +
            "\"format\":\"chemopunk-pixelgen-scene/1\"," +
            "\"name\":\"x\",\"kind\":\"k\",\"seed\":1," +
            "\"width\":1,\"height\":1," +
            "\"collision\":[\"q\"]," +
            "\"legend\":{\"q\":\"quantum foam\"}," +
            "\"spawn\":{\"x\":0,\"y\":0}" +
            "}"
        try {
            PixelGenSceneLoader.load(unknownToken)
            fail("Expected rejection for an unknown collision token")
        } catch (e: RuntimeException) {
            assertTrue(e.message, e.message?.contains("Unknown collision") == true)
        }

        // A foreign format string must be rejected.
        try {
            PixelGenSceneLoader.load("{}")
            fail("Expected rejection for a missing/unknown format")
        } catch (e: RuntimeException) {
            assertTrue(e.message, e.message?.contains("format") == true)
        }
    }
}
