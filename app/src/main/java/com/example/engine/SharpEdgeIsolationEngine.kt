package com.example.engine

import com.example.data.TileType

/**
 * Sharp Edge Isolation Engine for 2.5D Isometric ASCII Rendering.
 *
 * Capabilities:
 * - Detects topological and elevation discontinuities between adjacent isometric cells
 * - Computes directional boundary contours (North-East, North-West, South-East, South-West, Top, Vertical Facades)
 * - Assigns precise box-drawing and contour-isolating ASCII glyphs
 * - Emits edge isolation attributes for hardware edge-boost shader rendering
 */
object SharpEdgeIsolationEngine {

    data class EdgeSegment(
        val gridX: Float,
        val gridY: Float,
        val elevation: Float,
        val char: Char,
        val edgeType: EdgeType,
        val normalX: Float,
        val normalY: Float,
        val isolationStrength: Float = 1.0f
    )

    enum class EdgeType {
        WALL_TOP_NORTH_EAST,
        WALL_TOP_NORTH_WEST,
        WALL_TOP_SOUTH_EAST,
        WALL_TOP_SOUTH_WEST,
        WALL_VERTICAL_CORNER,
        WALL_FACADE_BOTTOM,
        ELEVATION_DROP,
        FLUID_RIM,
        TARGET_RETICLE
    }

    /**
     * Analyzes a 2D tile map and extracts sharp boundary edge segments.
     */
    fun extractTileEdges(
        mapGrid: List<List<TileType>>,
        gridX: Int,
        gridY: Int
    ): List<EdgeSegment> {
        val rows = mapGrid.size
        if (rows == 0) return emptyList()
        val cols = mapGrid[0].size

        val currentTile = mapGrid[gridY][gridX]
        val edges = mutableListOf<EdgeSegment>()

        // 1. Sharp Edge Isolation for Wall Structures
        if (currentTile == TileType.WALL) {
            val north = getTile(mapGrid, gridX, gridY - 1, rows, cols)
            val south = getTile(mapGrid, gridX, gridY + 1, rows, cols)
            val west  = getTile(mapGrid, gridX - 1, gridY, rows, cols)
            val east  = getTile(mapGrid, gridX + 1, gridY, rows, cols)

            val southEastIsFloor = (south != TileType.WALL)
            val southWestIsFloor = (east != TileType.WALL)
            val northEastIsFloor = (west != TileType.WALL)
            val northWestIsFloor = (north != TileType.WALL)

            // Front-facing vertical corner edge (facing the isometric camera)
            if (southEastIsFloor && southWestIsFloor) {
                edges.add(
                    EdgeSegment(
                        gridX = gridX.toFloat(),
                        gridY = gridY.toFloat(),
                        elevation = 0.5f,
                        char = '|',
                        edgeType = EdgeType.WALL_VERTICAL_CORNER,
                        normalX = 0.707f,
                        normalY = 0.707f,
                        isolationStrength = 1.2f
                    )
                )
            }

            // Top facade contour edges (elevated at 1.0)
            if (northWestIsFloor) {
                edges.add(
                    EdgeSegment(
                        gridX = gridX.toFloat(),
                        gridY = gridY.toFloat(),
                        elevation = 1.0f,
                        char = '/',
                        edgeType = EdgeType.WALL_TOP_NORTH_WEST,
                        normalX = -0.707f,
                        normalY = -0.707f,
                        isolationStrength = 1.0f
                    )
                )
            }

            if (northEastIsFloor) {
                edges.add(
                    EdgeSegment(
                        gridX = gridX.toFloat(),
                        gridY = gridY.toFloat(),
                        elevation = 1.0f,
                        char = '\\',
                        edgeType = EdgeType.WALL_TOP_NORTH_EAST,
                        normalX = -0.707f,
                        normalY = 0.707f,
                        isolationStrength = 1.0f
                    )
                )
            }

            if (southEastIsFloor) {
                edges.add(
                    EdgeSegment(
                        gridX = gridX.toFloat(),
                        gridY = gridY.toFloat(),
                        elevation = 1.0f,
                        char = '/',
                        edgeType = EdgeType.WALL_TOP_SOUTH_EAST,
                        normalX = 0.707f,
                        normalY = 0.707f,
                        isolationStrength = 1.15f
                    )
                )
            }

            if (southWestIsFloor) {
                edges.add(
                    EdgeSegment(
                        gridX = gridX.toFloat(),
                        gridY = gridY.toFloat(),
                        elevation = 1.0f,
                        char = '\\',
                        edgeType = EdgeType.WALL_TOP_SOUTH_WEST,
                        normalX = 0.707f,
                        normalY = -0.707f,
                        isolationStrength = 1.15f
                    )
                )
            }
        }

        // 2. Sharp Edge Isolation for Toxic Pools & Hazard Zones (Rim highlighting)
        if (currentTile == TileType.TOXIC_POOL) {
            val north = getTile(mapGrid, gridX, gridY - 1, rows, cols)
            val south = getTile(mapGrid, gridX, gridY + 1, rows, cols)
            val west  = getTile(mapGrid, gridX - 1, gridY, rows, cols)
            val east  = getTile(mapGrid, gridX + 1, gridY, rows, cols)

            if (north == TileType.FLOOR || south == TileType.FLOOR || west == TileType.FLOOR || east == TileType.FLOOR) {
                edges.add(
                    EdgeSegment(
                        gridX = gridX.toFloat(),
                        gridY = gridY.toFloat(),
                        elevation = 0.05f,
                        char = ':',
                        edgeType = EdgeType.FLUID_RIM,
                        normalX = 0.0f,
                        normalY = 1.0f,
                        isolationStrength = 0.8f
                    )
                )
            }
        }

        return edges
    }

    /**
     * Generates corner framing box-drawing glyphs for a selected grid coordinate.
     */
    fun getSelectionReticleEdges(
        gridX: Int,
        gridY: Int
    ): List<EdgeSegment> {
        return listOf(
            EdgeSegment(gridX.toFloat(), gridY.toFloat(), 0.05f, '[', EdgeType.TARGET_RETICLE, -1f, 0f, 1.5f),
            EdgeSegment(gridX.toFloat(), gridY.toFloat(), 0.05f, ']', EdgeType.TARGET_RETICLE, 1f, 0f, 1.5f)
        )
    }

    private fun getTile(
        mapGrid: List<List<TileType>>,
        x: Int,
        y: Int,
        rows: Int,
        cols: Int
    ): TileType {
        if (y !in 0 until rows || x !in 0 until cols) return TileType.WALL
        return mapGrid[y][x]
    }
}
