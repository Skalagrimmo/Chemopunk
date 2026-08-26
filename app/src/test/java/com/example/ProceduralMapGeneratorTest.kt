package com.example

import com.example.data.ProceduralMapGenerator
import com.example.data.TileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralMapGeneratorTest {

    @Test
    fun testGridIsEnclosedByWalls() = runBlocking {
        val result = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 42L)
        val grid = result.grid

        assertEquals(18, grid.size)
        for (row in grid) {
            assertEquals(24, row.size)
        }

        for (c in 0 until 24) {
            assertEquals(TileType.WALL, grid[0][c])
            assertEquals(TileType.WALL, grid[17][c])
        }
        for (r in 0 until 18) {
            assertEquals(TileType.WALL, grid[r][0])
            assertEquals(TileType.WALL, grid[r][23])
        }
    }

    @Test
    fun testFloodFillConnectivity() = runBlocking {
        val result = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 99L)
        val grid = result.grid

        val sx = result.playerStart.first.toInt()
        val sy = result.playerStart.second.toInt()
        val ex = result.exitPosition.first.toInt()
        val ey = result.exitPosition.second.toInt()

        assertTrue(grid[sy][sx] != TileType.WALL)
        assertTrue(grid[ey][ex] != TileType.WALL)

        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(Pair(sx, sy))
        visited.add(Pair(sx, sy))

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            for ((dx, dy) in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                val nx = cx + dx
                val ny = cy + dy
                if (ny in grid.indices && nx in grid[0].indices &&
                    grid[ny][nx] != TileType.WALL && grid[ny][nx] != TileType.TOXIC_POOL &&
                    Pair(nx, ny) !in visited
                ) {
                    visited.add(Pair(nx, ny))
                    queue.addLast(Pair(nx, ny))
                }
            }
        }

        assertTrue(
            "Exit must be reachable from player start",
            visited.contains(Pair(ex, ey))
        )
    }

    @Test
    fun testSeedDeterminism() = runBlocking {
        val result1 = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 12345L)
        val result2 = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 12345L)

        assertEquals(result1.grid, result2.grid)
        assertEquals(result1.enemySpawns, result2.enemySpawns)
        assertEquals(result1.playerStart, result2.playerStart)
        assertEquals(result1.exitPosition, result2.exitPosition)
    }

    @Test
    fun testDifferentSeedsProduceDifferentMaps() = runBlocking {
        val result1 = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 100L)
        val result2 = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 200L)

        assertFalse("Different seeds should produce different grids", result1.grid == result2.grid)
    }

    @Test
    fun testEnemySpawnsOnWalkableTiles() = runBlocking {
        val result = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 42L, numEnemies = 6)

        for ((ex, ey) in result.enemySpawns) {
            val gx = ex.toInt()
            val gy = ey.toInt()
            assertTrue(
                "Enemy at ($gx, $gy) must be on a walkable tile, was ${result.grid[gy][gx]}",
                result.grid[gy][gx] != TileType.WALL
            )
        }
    }

    @Test
    fun testEnemyCountRespected() = runBlocking {
        val result = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 42L, numEnemies = 3)
        assertTrue("Should have at most 3 enemies", result.enemySpawns.size <= 3)
    }

    @Test
    fun testExtractionLiftExists() = runBlocking {
        val result = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 42L)
        val hasLift = result.grid.any { row -> row.any { it == TileType.EXTRACTION_LIFT } }
        assertTrue("Map must contain an EXTRACTION_LIFT tile", hasLift)
    }

    @Test
    fun testPlayerStartIsFloor() = runBlocking {
        val result = ProceduralMapGenerator.generate(width = 24, height = 18, seed = 42L)
        val sx = result.playerStart.first.toInt()
        val sy = result.playerStart.second.toInt()
        assertEquals("Player start must be FLOOR", TileType.FLOOR, result.grid[sy][sx])
    }
}
