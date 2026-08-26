package com.example.engine

import com.example.data.Enemy
import com.example.data.InteractiveObject
import com.example.data.InteractiveObjectType
import com.example.data.Player
import com.example.data.TileType

/**
 * Converts game state (tiles, player, enemies, objects) into SDF render primitives
 * that the IntensityFieldGenerator evaluates per sub-pixel cell.
 */
object PrimitiveGenerator {

    /**
     * A single render primitive: an SDF function operating in normalized [0..1] cell coordinates,
     * plus a color, weight, and layering blend mode.
     */
    data class RenderPrimitive(
        val sdf: (Float, Float) -> Float,
        val colorR: Float,
        val colorG: Float,
        val colorB: Float,
        val weight: Float = 1f,
        val blendMode: Int = 1,  // 0=add, 1=union, 2=subtract
        val isText: Boolean = false,
        val char: Char = ' ',
        val glow: Float = 0f,
        val priority: Int = 0  // higher = rendered on top
    )

    /**
     * A full cell's primitive list.
     */
    data class CellPrimitives(
        val gridX: Int,
        val gridY: Int,
        val primitives: List<RenderPrimitive>
    )

    /**
     * Generates render primitives for the entire visible map region.
     *
     * @param mapGrid 2D tile grid
     * @param player current player state
     * @param enemies active enemies
     * @param interactiveObjects map objects by (x,y) position
     * @param tileSize world-space size of one tile
     * @param viewMinX left column of visible area
     * @param viewMinY top row of visible area
     * @param viewMaxX right column (exclusive)
     * @param viewMaxY bottom row (exclusive)
     */
    fun generatePrimitives(
        mapGrid: List<List<TileType>>,
        player: Player,
        enemies: List<Enemy>,
        interactiveObjects: Map<Pair<Int, Int>, InteractiveObject>,
        tileSize: Float = 1f,
        viewMinX: Int = 0,
        viewMinY: Int = 0,
        viewMaxX: Int = if (mapGrid.isNotEmpty()) mapGrid[0].size else 0,
        viewMaxY: Int = mapGrid.size
    ): List<CellPrimitives> {
        if (mapGrid.isEmpty()) return emptyList()

        val result = mutableListOf<CellPrimitives>()

        for (row in viewMinY until viewMaxY) {
            if (row !in mapGrid.indices) continue
            for (col in viewMinX until viewMaxX) {
                if (col !in mapGrid[row].indices) continue

                val tileType = mapGrid[row][col]
                val primitives = mutableListOf<RenderPrimitive>()

                // ── Tile primitives ──
                when (tileType) {
                    TileType.WALL -> {
                        primitives.add(wallPrimitive(col, row))
                        // Wall edge highlights (subtle top bevel)
                        primitives.add(wallBevelPrimitive(col, row))
                    }
                    TileType.FLOOR -> {
                        primitives.add(floorPrimitive(col, row))
                        // Subtle floor grid lines
                        primitives.add(floorGridPrimitive(col, row))
                    }
                    TileType.TOXIC_POOL -> {
                        primitives.add(toxicPoolPrimitive(col, row))
                        // Toxic bubble highlights
                        primitives.add(toxicBubblePrimitive(col, row))
                    }
                    TileType.DOOR -> {
                        primitives.add(doorPrimitive(col, row))
                        // Door frame
                        primitives.add(doorFramePrimitive(col, row))
                    }
                    TileType.EXTRACTION_LIFT -> {
                        primitives.add(extractionLiftPrimitive(col, row))
                        // Beacon ring
                        primitives.add(extractionBeaconPrimitive(col, row))
                    }
                    TileType.INTERACTIVE -> {
                        primitives.add(floorPrimitive(col, row))
                    }
                }

                // ── Interactive objects ──
                val obj = interactiveObjects[Pair(col, row)]
                if (obj != null) {
                    primitives.add(interactiveObjectPrimitive(obj))
                }

                result.add(CellPrimitives(col, row, primitives))
            }
        }

        // ── Player (always on top) ──
        val px = player.x.toInt()
        val py = player.y.toInt()
        if (px in viewMinX until viewMaxX && py in viewMinY until viewMaxY) {
            val existing = result.indexOfFirst { it.gridX == px && it.gridY == py }
            val playerPrims = mutableListOf(
                playerBodyPrimitive(player),
                playerIndicatorPrimitive(player)
            )
            if (existing >= 0) {
                val cell = result[existing]
                result[existing] = CellPrimitives(px, py, cell.primitives + playerPrims)
            } else {
                result.add(CellPrimitives(px, py, playerPrims))
            }
        }

        // ── Enemies ──
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val ex = enemy.x.toInt()
            val ey = enemy.y.toInt()
            if (ex in viewMinX until viewMaxX && ey in viewMinY until viewMaxY) {
                val existing = result.indexOfFirst { it.gridX == ex && it.gridY == ey }
                val enemyPrims = listOf(enemyBodyPrimitive(enemy))
                if (existing >= 0) {
                    val cell = result[existing]
                    result[existing] = CellPrimitives(ex, ey, cell.primitives + enemyPrims)
                } else {
                    result.add(CellPrimitives(ex, ey, enemyPrims))
                }
            }
        }

        return result
    }

    // ── Tile Primitives ─────────────────────────────────────────────

    private fun wallPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Full cell fill
                IntensityFieldGenerator.sdfRect(nx, ny, 0f, 0f, 1f, 1f)
            },
            colorR = 0.45f, colorG = 0.48f, colorB = 0.55f,
            priority = 1
        )
    }

    private fun wallBevelPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Top edge highlight
                IntensityFieldGenerator.sdfRect(nx, ny, 0f, 0f, 1f, 0.15f)
            },
            colorR = 0.55f, colorG = 0.58f, colorB = 0.65f,
            weight = 0.4f,
            priority = 2
        )
    }

    private fun floorPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                IntensityFieldGenerator.sdfRect(nx, ny, 0f, 0f, 1f, 1f)
            },
            colorR = 0.15f, colorG = 0.17f, colorB = 0.2f,
            priority = 0
        )
    }

    private fun floorGridPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                val hLine = IntensityFieldGenerator.sdfLine(nx, ny, 0f, 0.02f, 1f, 0.02f)
                val vLine = IntensityFieldGenerator.sdfLine(nx, ny, 0.02f, 0f, 0.02f, 1f)
                IntensityFieldGenerator.sdfSmoothUnion(hLine, vLine, 0.01f)
            },
            colorR = 0.2f, colorG = 0.22f, colorB = 0.25f,
            weight = 0.15f,
            blendMode = 1,
            priority = 1
        )
    }

    private fun toxicPoolPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Pool with slight inset
                IntensityFieldGenerator.sdfRect(nx, ny, 0.05f, 0.05f, 0.95f, 0.95f)
            },
            colorR = 0.1f, colorG = 0.7f, colorB = 0.4f,
            glow = 0.3f,
            priority = 1
        )
    }

    private fun toxicBubblePrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Small bubble accents (deterministic by position)
                val offsetX = (col * 0.37f) % 0.4f
                val offsetY = (row * 0.53f) % 0.4f
                val d1 = IntensityFieldGenerator.sdfCircle(nx, ny, 0.3f + offsetX, 0.4f + offsetY, 0.08f)
                val d2 = IntensityFieldGenerator.sdfCircle(nx, ny, 0.6f - offsetX, 0.6f - offsetY, 0.05f)
                IntensityFieldGenerator.sdfSmoothUnion(d1, d2, 0.03f)
            },
            colorR = 0.2f, colorG = 0.9f, colorB = 0.5f,
            weight = 0.6f,
            glow = 0.5f,
            priority = 2
        )
    }

    private fun doorPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Door slab (centered)
                IntensityFieldGenerator.sdfRect(nx, ny, 0.2f, 0.05f, 0.8f, 0.95f)
            },
            colorR = 0.6f, colorG = 0.5f, colorB = 0.3f,
            priority = 1
        )
    }

    private fun doorFramePrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Door frame edges
                val left = IntensityFieldGenerator.sdfRect(nx, ny, 0.1f, 0f, 0.2f, 1f)
                val right = IntensityFieldGenerator.sdfRect(nx, ny, 0.8f, 0f, 0.9f, 1f)
                IntensityFieldGenerator.sdfSmoothUnion(left, right, 0.01f)
            },
            colorR = 0.7f, colorG = 0.6f, colorB = 0.4f,
            weight = 0.5f,
            priority = 2
        )
    }

    private fun extractionLiftPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Circular lift platform
                IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.42f)
            },
            colorR = 0.8f, colorG = 0.7f, colorB = 0.2f,
            glow = 0.4f,
            priority = 1
        )
    }

    private fun extractionBeaconPrimitive(col: Int, row: Int): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Pulsing beacon ring (outer)
                IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.48f) *
                    IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.35f).let { d ->
                        if (d > 0) 1f else 0f
                    }
            },
            colorR = 1f, colorG = 0.9f, colorB = 0.1f,
            weight = 0.4f,
            glow = 0.8f,
            priority = 2
        )
    }

    // ── Entity Primitives ───────────────────────────────────────────

    private fun playerBodyPrimitive(player: Player): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Diamond / circle player shape
                IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.3f)
            },
            colorR = 0.2f, colorG = 1f, colorB = 0.5f,
            glow = 0.3f,
            priority = 10
        )
    }

    private fun playerIndicatorPrimitive(player: Player): RenderPrimitive {
        return RenderPrimitive(
            sdf = { nx, ny ->
                // Small direction indicator dot
                val angle = Math.toRadians(player.angleDegrees.toDouble()).toFloat()
                val dotX = 0.5f + kotlin.math.cos(angle) * 0.35f
                val dotY = 0.5f - kotlin.math.sin(angle) * 0.35f
                IntensityFieldGenerator.sdfCircle(nx, ny, dotX, dotY, 0.06f)
            },
            colorR = 0.5f, colorG = 1f, colorB = 0.7f,
            glow = 0.6f,
            priority = 11
        )
    }

    private fun enemyBodyPrimitive(enemy: Enemy): RenderPrimitive {
        val colorR: Float
        val colorG: Float
        val colorB: Float
        when {
            enemy.name.contains("Rat", ignoreCase = true) -> {
                colorR = 0.7f; colorG = 0.3f; colorB = 0.2f
            }
            enemy.name.contains("Crawler", ignoreCase = true) -> {
                colorR = 0.5f; colorG = 0.2f; colorB = 0.6f
            }
            enemy.name.contains("Slime", ignoreCase = true) || enemy.name.contains("Blob", ignoreCase = true) -> {
                colorR = 0.2f; colorG = 0.8f; colorB = 0.3f
            }
            else -> {
                colorR = 0.9f; colorG = 0.2f; colorB = 0.2f
            }
        }
        return RenderPrimitive(
            sdf = { nx, ny ->
                IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.28f)
            },
            colorR = colorR, colorG = colorG, colorB = colorB,
            glow = 0.2f,
            priority = 8
        )
    }

    private fun interactiveObjectPrimitive(obj: InteractiveObject): RenderPrimitive {
        return when (obj.type) {
            InteractiveObjectType.TERMINAL -> RenderPrimitive(
                sdf = { nx, ny -> IntensityFieldGenerator.sdfRect(nx, ny, 0.2f, 0.2f, 0.8f, 0.8f) },
                colorR = 0.3f, colorG = 0.5f, colorB = 1f,
                glow = 0.3f, priority = 5
            )
            InteractiveObjectType.LOCKER -> RenderPrimitive(
                sdf = { nx, ny -> IntensityFieldGenerator.sdfRect(nx, ny, 0.15f, 0.1f, 0.85f, 0.9f) },
                colorR = 0.6f, colorG = 0.6f, colorB = 0.6f,
                priority = 5
            )
            InteractiveObjectType.SWITCH -> RenderPrimitive(
                sdf = { nx, ny -> IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.2f) },
                colorR = 0.9f, colorG = 0.7f, colorB = 0.1f,
                glow = 0.4f, priority = 5
            )
            InteractiveObjectType.BEACON -> RenderPrimitive(
                sdf = { nx, ny -> IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.3f) },
                colorR = 0.2f, colorG = 0.9f, colorB = 1f,
                glow = 0.8f, priority = 5
            )
            InteractiveObjectType.MERCHANT -> RenderPrimitive(
                sdf = { nx, ny -> IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.3f) },
                colorR = 1f, colorG = 0.85f, colorB = 0.2f,
                glow = 0.3f, priority = 5
            )
            InteractiveObjectType.ZONE_EXIT -> RenderPrimitive(
                sdf = { nx, ny -> IntensityFieldGenerator.sdfCircle(nx, ny, 0.5f, 0.5f, 0.35f) },
                colorR = 0.8f, colorG = 0.3f, colorB = 1f,
                glow = 0.5f, priority = 5
            )
        }
    }
}
