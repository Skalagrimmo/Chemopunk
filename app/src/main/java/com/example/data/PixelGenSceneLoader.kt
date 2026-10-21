package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Baked PixelGen scene loaded into the Chemopunk map model.
 *
 * Assets under `assets/pixelgen/` use the `chemopunk-pixelgen-scene/1` format
 * produced from a PixelGen 1.0 scene export by `tools/pixelgen_bake.py`.
 */
data class PixelGenSceneMap(
    val sceneName: String,
    val kind: String,
    val seed: Long,
    val grid: List<List<TileType>>,
    val enemySpawns: List<Pair<Float, Float>>,
    val playerStart: Pair<Float, Float>,
    val exitPosition: Pair<Float, Float>,
    val interactiveObjects: List<InteractiveObject>
)

/**
 * Bridge between PixelGen 1.0 scene exports and the Chemopunk map model.
 *
 * Collision projection: PixelGen `solid` -> WALL, `walk` -> FLOOR,
 * `hazard` -> TOXIC_POOL. The EXTRACTION_LIFT exit is chosen deterministically
 * as the walkable tile with the greatest Manhattan distance from the spawn,
 * so a given baked scene always produces the same map (the same seed -> the
 * same world determinism contract PixelGen itself guarantees).
 */
object PixelGenSceneLoader {

    private const val EXPECTED_FORMAT = "chemopunk-pixelgen-scene/1"

    /** Loads a baked scene from Android assets, e.g. "pixelgen/scene_smoliani_bolota.json". */
    fun loadFromAssets(context: Context, fileName: String): PixelGenSceneMap =
        load(context.assets.open(fileName).bufferedReader().use { it.readText() })

    /** Parses and validates a baked PixelGen scene; rejects corrupt input. */
    fun load(json: String): PixelGenSceneMap {
        val root = try {
            JSONObject(json)
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("PixelGen scene is not valid JSON", e)
        }
        val format = root.optString("format")
        require(format == EXPECTED_FORMAT) {
            "Unsupported PixelGen scene format: " + format + " (expected " + EXPECTED_FORMAT + ")"
        }

        val width = root.getInt("width")
        val height = root.getInt("height")
        require(width > 0 && height > 0) { "PixelGen scene has invalid dimensions " + width + "x" + height }

        val charToTile = HashMap<Char, TileType>()
        val legend = root.getJSONObject("legend")
        for (key in legend.keys()) {
            val token = legend.getString(key)
            val tile = when (token) {
                "solid" -> TileType.WALL
                "walk" -> TileType.FLOOR
                "hazard" -> TileType.TOXIC_POOL
                else -> throw IllegalStateException("Unknown collision token: " + token)
            }
            charToTile[key[0]] = tile
        }

        val rowsJson = root.getJSONArray("collision")
        require(rowsJson.length() == height) {
            "Scene declares height " + height + " but has " + rowsJson.length() + " collision rows"
        }
        val grid = Array(height) { arrayOfNulls<TileType>(width) }
        for (y in 0 until height) {
            val row = rowsJson.getString(y)
            require(row.length == width) {
                "Collision row " + y + " has length " + row.length + ", expected " + width
            }
            for (x in 0 until width) {
                grid[y][x] = charToTile[row[x]]
                    ?: throw IllegalStateException("Unknown collision char at column " + x + " of row " + y)
            }
        }

        val spawn = root.getJSONObject("spawn")
        val spawnX = spawn.getInt("x")
        val spawnY = spawn.getInt("y")
        require(spawnX in 0 until width && spawnY in 0 until height) {
            "Spawn (" + spawnX + ", " + spawnY + ") is out of scene bounds"
        }
        var playerX = spawnX
        var playerY = spawnY
        if (grid[playerY][playerX] != TileType.FLOOR) {
            // Defensive fallback: pick the nearest walkable tile deterministically.
            var bestDist = Int.MAX_VALUE
            outer@ for (y in 0 until height) {
                for (x in 0 until width) {
                    if (grid[y][x] == TileType.FLOOR) {
                        val d = Math.abs(x - spawnX) + Math.abs(y - spawnY)
                        if (d < bestDist) {
                            bestDist = d
                            playerX = x
                            playerY = y
                        }
                        if (bestDist == 0) break@outer
                    }
                }
            }
            check(bestDist != Int.MAX_VALUE) { "Scene has no walkable tile for the player spawn" }
        }

        // Deterministic exit: walkable tile farthest from the spawn, scan order (y, x).
        var exitX = -1
        var exitY = -1
        var bestExitDist = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (grid[y][x] != TileType.FLOOR) continue
                val d = Math.abs(x - playerX) + Math.abs(y - playerY)
                if (d > bestExitDist) {
                    bestExitDist = d
                    exitX = x
                    exitY = y
                }
            }
        }
        check(exitX >= 0) { "Scene has no walkable tile for the extraction lift" }
        grid[exitY][exitX] = TileType.EXTRACTION_LIFT

        val interactive = mutableListOf<InteractiveObject>()
        val objectsJson = root.optJSONArray("interactive_objects") ?: JSONArray()
        for (i in 0 until objectsJson.length()) {
            val obj = objectsJson.getJSONObject(i)
            val x = obj.getInt("x")
            val y = obj.getInt("y")
            require(x in 0 until width && y in 0 until height) {
                "Interactive object " + obj.optString("kind") + " at (" + x + ", " + y + ") is out of bounds"
            }
            val type = try {
                InteractiveObjectType.valueOf(obj.getString("type"))
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Unknown interactive object type: " + obj.getString("type"), e)
            }
            // Never override the spawn or the extraction lift with a prop tile.
            if ((x == playerX && y == playerY) || (x == exitX && y == exitY)) continue
            grid[y][x] = TileType.INTERACTIVE
            interactive.add(
                InteractiveObject(
                    id = "pixelgen_" + obj.optString("kind", "object") + "_" + x + "_" + y,
                    type = type,
                    x = x,
                    y = y,
                    description = obj.optString("note", "")
                )
            )
        }

        val enemySpawns = mutableListOf<Pair<Float, Float>>()
        root.optJSONObject("enemy_spawn")?.let { enemy ->
            if (enemy.has("x") && enemy.has("y")) {
                enemySpawns.add(Pair(enemy.getInt("x") + 0.5f, enemy.getInt("y") + 0.5f))
            }
        }
        root.optJSONArray("enemy_spawns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val enemy = arr.getJSONObject(i)
                enemySpawns.add(Pair(enemy.getInt("x") + 0.5f, enemy.getInt("y") + 0.5f))
            }
        }

        return PixelGenSceneMap(
            sceneName = root.getString("name"),
            kind = root.getString("kind"),
            seed = root.getLong("seed"),
            grid = grid.map { row -> row.map { it!! } },
            enemySpawns = enemySpawns,
            playerStart = Pair(playerX + 0.5f, playerY + 0.5f),
            exitPosition = Pair(exitX + 0.5f, exitY + 0.5f),
            interactiveObjects = interactive
        )
    }
}
