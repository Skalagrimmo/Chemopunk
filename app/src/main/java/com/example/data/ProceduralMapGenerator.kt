package com.example.data

import kotlin.random.Random

data class ProceduralMapResult(
    val grid: List<List<TileType>>,
    val enemySpawns: List<Pair<Float, Float>>,
    val playerStart: Pair<Float, Float>,
    val exitPosition: Pair<Float, Float>,
    val seed: Long
)

object ProceduralMapGenerator {

    fun generate(
        width: Int = 24,
        height: Int = 18,
        seed: Long = System.currentTimeMillis(),
        roomMinSize: Int = 4,
        roomMaxSize: Int = 8,
        numEnemies: Int = 4,
        numToxicPools: Int = 3,
        numDoors: Int = 2
    ): ProceduralMapResult {
        val rng = Random(seed)
        val grid = Array(height) { Array(width) { TileType.WALL } }

        val rooms = generateRooms(width, height, roomMinSize, roomMaxSize, rng)
        carveRooms(grid, rooms)
        connectRooms(grid, rooms, rng)
        placeToxicPools(grid, rooms, numToxicPools, rng)
        placeDoors(grid, rooms, numDoors, rng)

        val allFloorTiles = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (grid[r][c] == TileType.FLOOR) allFloorTiles.add(Pair(c, r))
            }
        }

        if (allFloorTiles.isEmpty()) {
            for (r in 1 until height - 1) {
                for (c in 1 until width - 1) {
                    grid[r][c] = TileType.FLOOR
                    allFloorTiles.add(Pair(c, r))
                }
            }
        }

        val shuffledFloors = allFloorTiles.toMutableList().also { it.shuffle(rng) }

        val firstRoom = rooms.first()
        val startTile = Pair(firstRoom.cx, firstRoom.cy)
        grid[startTile.second][startTile.first] = TileType.FLOOR

        val lastRoom = rooms.last()
        val exitTile = findFloorNear(lastRoom.cx, lastRoom.cy, grid, width, height)
        grid[exitTile.second][exitTile.first] = TileType.EXTRACTION_LIFT

        val enemySpawnPositions = mutableListOf<Pair<Float, Float>>()
        val usedPositions = mutableSetOf(startTile, exitTile)
        var enemyCount = 0
        for (tile in shuffledFloors) {
            if (enemyCount >= numEnemies) break
            if (tile == startTile || tile == exitTile) continue
            if (usedPositions.any { kotlin.math.abs(it.first - tile.first) + kotlin.math.abs(it.second - tile.second) < 3 }) continue
            usedPositions.add(tile)
            enemySpawnPositions.add(Pair(tile.first + 0.5f, tile.second + 0.5f))
            enemyCount++
        }

        return ProceduralMapResult(
            grid = grid.map { row -> row.toList() },
            enemySpawns = enemySpawnPositions,
            playerStart = Pair(startTile.first + 0.5f, startTile.second + 0.5f),
            exitPosition = Pair(exitTile.first + 0.5f, exitTile.second + 0.5f),
            seed = seed
        )
    }

    private data class Room(
        val x: Int, val y: Int,
        val w: Int, val h: Int,
        val cx: Int get() = x + w / 2
        val cy: Int get() = y + h / 2
    )

    private fun generateRooms(
        mapWidth: Int, mapHeight: Int,
        minSize: Int, maxSize: Int,
        rng: Random
    ): List<Room> {
        val rooms = mutableListOf<Room>()
        val maxAttempts = 200

        for (attempt in 0 until maxAttempts) {
            val w = rng.nextInt(minSize, maxSize + 1)
            val h = rng.nextInt(minSize, maxSize + 1)
            val x = rng.nextInt(1, mapWidth - w - 1)
            val y = rng.nextInt(1, mapHeight - h - 1)

            val newRoom = Room(x, y, w, h)
            val overlaps = rooms.any { r ->
                x < r.x + r.w + 2 && x + w + 2 > r.x &&
                    y < r.y + r.h + 2 && y + h + 2 > r.y
            }

            if (!overlaps) {
                rooms.add(newRoom)
                if (rooms.size >= 5) break
            }
        }

        if (rooms.isEmpty()) {
            rooms.add(Room(2, 2, 5, 4))
            rooms.add(Room(mapWidth - 8, mapHeight - 7, 5, 4))
        }

        return rooms
    }

    private fun carveRooms(grid: Array<Array<TileType>>, rooms: List<Room>) {
        for (room in rooms) {
            for (r in room.y until room.y + room.h) {
                for (c in room.x until room.x + room.w) {
                    if (r in grid.indices && c in grid[0].indices) {
                        grid[r][c] = TileType.FLOOR
                    }
                }
            }
        }
    }

    private fun connectRooms(grid: Array<Array<TileType>>, rooms: List<Room>, rng: Random) {
        for (i in 0 until rooms.size - 1) {
            val a = rooms[i]
            val b = rooms[i + 1]

            if (rng.nextBoolean()) {
                carveHorizontalCorridor(grid, a.cx, b.cx, a.cy)
                carveVerticalCorridor(grid, a.cy, b.cy, b.cx)
            } else {
                carveVerticalCorridor(grid, a.cy, b.cy, a.cx)
                carveHorizontalCorridor(grid, a.cx, b.cx, b.cy)
            }
        }
    }

    private fun carveHorizontalCorridor(grid: Array<Array<TileType>>, x1: Int, x2: Int, y: Int) {
        val start = minOf(x1, x2)
        val end = maxOf(x1, x2)
        for (x in start..end) {
            if (y in grid.indices && x in grid[0].indices) {
                grid[y][x] = TileType.FLOOR
            }
        }
    }

    private fun carveVerticalCorridor(grid: Array<Array<TileType>>, y1: Int, y2: Int, x: Int) {
        val start = minOf(y1, y2)
        val end = maxOf(y1, y2)
        for (y in start..end) {
            if (y in grid.indices && x in grid[0].indices) {
                grid[y][x] = TileType.FLOOR
            }
        }
    }

    private fun placeToxicPools(
        grid: Array<Array<TileType>>,
        rooms: List<Room>,
        count: Int,
        rng: Random
    ) {
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (room in rooms.drop(1)) {
            for (r in room.y + 1 until room.y + room.h - 1) {
                for (c in room.x + 1 until room.x + room.w - 1) {
                    if (grid[r][c] == TileType.FLOOR) {
                        candidates.add(Pair(c, r))
                    }
                }
            }
        }
        candidates.shuffle(rng)
        for (i in 0 until minOf(count, candidates.size)) {
            val (cx, cy) = candidates[i]
            grid[cy][cx] = TileType.TOXIC_POOL
        }
    }

    private fun placeDoors(
        grid: Array<Array<TileType>>,
        rooms: List<Room>,
        count: Int,
        rng: Random
    ) {
        var placed = 0
        for (i in 0 until rooms.size - 1 && placed < count) {
            val a = rooms[i]
            val b = rooms[i + 1]
            val midX = (a.cx + b.cx) / 2
            val midY = (a.cy + b.cy) / 2
            if (midY in grid.indices && midX in grid[0].indices && grid[midY][midX] == TileType.FLOOR) {
                grid[midY][midX] = TileType.DOOR
                placed++
            }
        }
    }

    private fun findFloorNear(x: Int, y: Int, grid: Array<Array<TileType>>, width: Int, height: Int): Pair<Int, Int> {
        for (radius in 0..maxOf(width, height)) {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = x + dx
                    val ny = y + dy
                    if (ny in grid.indices && nx in grid[0].indices && grid[ny][nx] == TileType.FLOOR) {
                        return Pair(nx, ny)
                    }
                }
            }
        }
        return Pair(x.coerceIn(1, width - 2), y.coerceIn(1, height - 2))
    }
}
