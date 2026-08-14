package com.example.engine

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.NpcState
import com.example.data.Player
import com.example.data.TileType
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Advanced 2.5D Isometric ASCII Renderer featuring real-time Dynamic Multi-Source Lighting & Fog of War.
 *
 * Capabilities:
 * - Dynamic Lighting Engine integration with multiple point lights (Torches, Flares, Toxic Glows, Extraction Beacons)
 * - Directional player flashlight with real-time cone calculations and ambient radius
 * - True photon color tinting and illumination falloff per individual ASCII glyph
 * - Organic flicker and pulse physics for flames and chemical reactions
 * - Fog of War with unexplored darkness, explored shadow memory, and illuminated line-of-sight
 * - Tactical V.A.T.S. targeting overlay with localized hit chances
 * - Shimmering animated toxic pool ASCII currents and rising particle steam
 * - Integrated CRT phosphor scanline emulation and rolling cathode refresh sweep
 */
class AsciiIsometricEngine {

    companion object {
        const val TILE_WIDTH = 68f
        const val TILE_HEIGHT = 34f
        const val WALL_HEIGHT = 32f
    }

    private val lightingEngine = DynamicLightingEngine()

    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val smallPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val scanPaint = Paint().apply {
        color = android.graphics.Color.argb(18, 0, 0, 0)
        strokeWidth = 1.2f
    }

    private val beamPaint = Paint().apply {
        color = android.graphics.Color.argb(12, 79, 209, 197)
        strokeWidth = 14f
    }

    private val engagementPath = Path()
    private val activeLightsBuffer = mutableListOf<LightSource>()

    /**
     * Converts grid (x, y) coordinates to screen-space isometric (screenX, screenY).
     */
    fun gridToIso(
        gridX: Float,
        gridY: Float,
        elevation: Float = 0f,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float = 1.0f
    ): Offset {
        val relX = gridX - camX
        val relY = gridY - camY

        val isoX = (relX - relY) * (TILE_WIDTH * 0.5f) * zoom
        val isoY = ((relX + relY) * (TILE_HEIGHT * 0.5f) - elevation * WALL_HEIGHT) * zoom

        return Offset(centerX + isoX, centerY + isoY)
    }

    /**
     * Converts screen-space touch coordinates (screenX, screenY) back to grid coordinates.
     */
    fun screenToGrid(
        screenX: Float,
        screenY: Float,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float = 1.0f
    ): Pair<Int, Int> {
        val relScreenX = (screenX - centerX) / (zoom * (TILE_WIDTH * 0.5f))
        val relScreenY = (screenY - centerY) / (zoom * (TILE_HEIGHT * 0.5f))

        val gridX = (relScreenY + relScreenX) * 0.5f + camX
        val gridY = (relScreenY - relScreenX) * 0.5f + camY

        val cellX = if (gridX >= 0) gridX.toInt() else gridX.toInt() - 1
        val cellY = if (gridY >= 0) gridY.toInt() else gridY.toInt() - 1

        return Pair(cellX, cellY)
    }

    /**
     * Main Render Pass for the Isometric ASCII Wasteland with Dynamic Lighting Engine.
     */
    fun renderWorld(
        drawScope: DrawScope,
        mapGrid: List<List<TileType>>,
        player: Player,
        enemies: List<Enemy>,
        lightSources: List<LightSource>,
        selectedTile: Pair<Int, Int>?,
        discoveredTiles: Set<Pair<Int, Int>>,
        floatingTexts: List<FloatingText>,
        paletteIndex: Int, // 0: Cyberpunk Multi-Color, 1: Phosphor Green CRT, 2: Amber Terminal, 3: Neon Cyan
        animTime: Float,
        zoom: Float = 1.0f,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        onTargetBodyPart: ((Enemy, String) -> Unit)? = null
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val centerX = width * 0.5f + panOffsetX
        val centerY = height * 0.44f + panOffsetY

        val canvas = drawScope.drawContext.canvas.nativeCanvas

        // Background Dark Wasteland Void
        val bgArgb = when (paletteIndex) {
            1 -> android.graphics.Color.rgb(3, 10, 4) // Dark Phosphor Green
            2 -> android.graphics.Color.rgb(12, 8, 2)  // Dark Amber
            3 -> android.graphics.Color.rgb(3, 8, 16)  // Dark Cyan
            else -> android.graphics.Color.rgb(8, 10, 14) // Dark Cyberpunk
        }
        canvas.drawColor(bgArgb)

        if (mapGrid.isEmpty()) return

        val rows = mapGrid.size
        val cols = mapGrid[0].size

        // Build Complete Dynamic Light Sources List (Player Torch + Point Lights + Environmental Hazards)
        activeLightsBuffer.clear()

        // 1. Player Directional Flashlight / Torch Light Source
        activeLightsBuffer.add(
            LightSource(
                id = "player_torch",
                gridX = player.x,
                gridY = player.y,
                elevation = 0.5f,
                colorR = 210,
                colorG = 240,
                colorB = 255,
                intensity = 1.35f,
                radius = 8.5f,
                type = LightType.PLAYER_FLASHLIGHT,
                isDirectional = true,
                directionAngleDeg = player.angleDegrees,
                coneAngleDeg = 75f,
                flickerFrequency = 7.0f,
                flickerIntensity = 0.06f
            )
        )

        // 2. Custom User / Map Point Lights (Flares, Wall Torches, Beacons)
        activeLightsBuffer.addAll(lightSources)

        // 3. Environmental Light Sources from Tiles (Toxic Pools, Extraction Elevator Lift)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (mapGrid[r][c]) {
                    TileType.TOXIC_POOL -> {
                        activeLightsBuffer.add(
                            LightSource(
                                id = "toxic_${c}_${r}",
                                gridX = c + 0.5f,
                                gridY = r + 0.5f,
                                colorR = 40,
                                colorG = 255,
                                colorB = 90,
                                intensity = 1.05f,
                                radius = 3.2f,
                                type = LightType.BIO_LUMINESCENT,
                                flickerFrequency = 5.0f,
                                flickerIntensity = 0.18f,
                                pulseSpeed = 4.5f
                            )
                        )
                    }
                    TileType.EXTRACTION_LIFT -> {
                        activeLightsBuffer.add(
                            LightSource(
                                id = "lift_${c}_${r}",
                                gridX = c + 0.5f,
                                gridY = r + 0.5f,
                                colorR = 255,
                                colorG = 220,
                                colorB = 60,
                                intensity = 1.4f,
                                radius = 4.8f,
                                type = LightType.EXTRACTION_BEACON,
                                pulseSpeed = 6.0f
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        // Draw Pathfinding / Movement Line if a tile is selected
        if (selectedTile != null) {
            val (stX, stY) = selectedTile
            if (stY in 0 until rows && stX in 0 until cols) {
                renderPathPreview(
                    canvas = canvas,
                    playerX = player.x,
                    playerY = player.y,
                    targetX = stX.toFloat(),
                    targetY = stY.toFloat(),
                    centerX = centerX,
                    centerY = centerY,
                    zoom = zoom,
                    palette = paletteIndex,
                    animTime = animTime
                )
            }
        }

        // Draw Range Indicator (Tactical Weapon Engagement Circle)
        renderEngagementRadius(
            canvas = canvas,
            playerX = player.x,
            playerY = player.y,
            radiusTiles = 3.5f,
            centerX = centerX,
            centerY = centerY,
            zoom = zoom,
            palette = paletteIndex,
            animTime = animTime
        )

        // Draw Order: Back-to-Front diagonal depth sorting (depth = r + c)
        val maxDepth = rows + cols
        for (depth in 0..maxDepth) {
            for (r in 0 until rows) {
                val c = depth - r
                if (c in 0 until cols) {
                    val tile = mapGrid[r][c]

                    // Calculate real-time dynamic multi-source lighting for this cell
                    val lighting = lightingEngine.calculateLighting(
                        gridX = c + 0.5f,
                        gridY = r + 0.5f,
                        lightSources = activeLightsBuffer,
                        discoveredTiles = discoveredTiles,
                        animTime = animTime
                    )

                    // Fog of War: If hidden and undiscovered, render minimal grid marker
                    if (lighting.isFOWHidden) {
                        val pos = gridToIso(c.toFloat(), r.toFloat(), 0f, centerX, centerY, player.x, player.y, zoom)
                        if (pos.x in -50f..width + 50f && pos.y in -50f..height + 50f) {
                            drawScope.drawCircle(
                                color = Color(0xFF1E293B).copy(alpha = 0.25f),
                                radius = 1.0f * zoom,
                                center = pos
                            )
                        }
                        continue
                    }

                    // Render Isometric Tile with dynamic photon illumination
                    renderIsoTile(
                        canvas = canvas,
                        tile = tile,
                        gridX = c,
                        gridY = r,
                        centerX = centerX,
                        centerY = centerY,
                        camX = player.x,
                        camY = player.y,
                        zoom = zoom,
                        lighting = lighting,
                        palette = paletteIndex,
                        animTime = animTime,
                        isSelected = (selectedTile?.first == c && selectedTile.second == r)
                    )

                    // Render Enemies standing on this tile
                    enemies.filter { it.isAlive && it.x.toInt() == c && it.y.toInt() == r }.forEach { enemy ->
                        val enemyLighting = lightingEngine.calculateLighting(
                            gridX = enemy.x,
                            gridY = enemy.y,
                            lightSources = activeLightsBuffer,
                            discoveredTiles = discoveredTiles,
                            animTime = animTime
                        )
                        if (!enemyLighting.isFOWHidden) {
                            renderEnemySprite(
                                canvas = canvas,
                                enemy = enemy,
                                centerX = centerX,
                                centerY = centerY,
                                camX = player.x,
                                camY = player.y,
                                zoom = zoom,
                                lighting = enemyLighting,
                                palette = paletteIndex,
                                animTime = animTime,
                                isSelected = (selectedTile?.first == c && selectedTile.second == r)
                            )
                        }
                    }

                    // Render Player Character if on this tile
                    if (player.x.toInt() == c && player.y.toInt() == r) {
                        renderPlayerSprite(
                            canvas = canvas,
                            player = player,
                            centerX = centerX,
                            centerY = centerY,
                            zoom = zoom,
                            lighting = lighting,
                            palette = paletteIndex,
                            animTime = animTime
                        )
                    }
                }
            }
        }

        // Render Point Light Flares and Particle Embers (Torch / Flare Glyphs)
        renderActiveLightEmitters(
            canvas = canvas,
            lightSources = activeLightsBuffer,
            centerX = centerX,
            centerY = centerY,
            playerX = player.x,
            playerY = player.y,
            zoom = zoom,
            animTime = animTime,
            palette = paletteIndex
        )

        // Render Rising Toxic Steam / Fallout Particles
        renderAtmosphericParticles(
            canvas = canvas,
            mapGrid = mapGrid,
            centerX = centerX,
            centerY = centerY,
            playerX = player.x,
            playerY = player.y,
            zoom = zoom,
            animTime = animTime,
            palette = paletteIndex
        )

        // Render Floating ASCII Combat Popups & Damage Numbers
        val curTime = System.currentTimeMillis()
        floatingTexts.forEach { ft ->
            val age = curTime - ft.spawnTime
            if (age < ft.durationMs) {
                val progress = age.toFloat() / ft.durationMs.toFloat()
                val elevationOffset = progress * 1.8f
                val pos = gridToIso(ft.x, ft.y, elevationOffset, centerX, centerY, player.x, player.y, zoom)

                val alpha = ((1.0f - progress) * 255).toInt().coerceIn(0, 255)
                smallPaint.textSize = (13f + progress * 4f) * zoom
                smallPaint.color = (ft.colorHex.toInt() and 0x00FFFFFF) or (alpha shl 24)
                smallPaint.isFakeBoldText = true

                canvas.drawText(ft.text, pos.x, pos.y - 34f * zoom, smallPaint)
            }
        }

        // Render V.A.T.S. Tactical Target Breakdown (if enemy selected)
        if (selectedTile != null) {
            val (stX, stY) = selectedTile
            val targetEnemy = enemies.firstOrNull { it.isAlive && it.x.toInt() == stX && it.y.toInt() == stY }
            if (targetEnemy != null) {
                renderVatsTargetOverlay(
                    canvas = canvas,
                    enemy = targetEnemy,
                    player = player,
                    centerX = centerX,
                    centerY = centerY,
                    zoom = zoom,
                    palette = paletteIndex,
                    animTime = animTime
                )
            }
        }

        // Tactical Fallout HUD Status & CRT Scanline Post-Process Pass
        renderTacticalHudAndCrt(
            canvas = canvas,
            width = width,
            height = height,
            selectedTile = selectedTile,
            mapGrid = mapGrid,
            enemies = enemies,
            player = player,
            activeLightCount = activeLightsBuffer.size,
            palette = paletteIndex,
            animTime = animTime,
            zoom = zoom
        )
    }

    private fun renderIsoTile(
        canvas: android.graphics.Canvas,
        tile: TileType,
        gridX: Int,
        gridY: Int,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float,
        lighting: TileLighting,
        palette: Int,
        animTime: Float,
        isSelected: Boolean
    ) {
        val basePos = gridToIso(gridX.toFloat(), gridY.toFloat(), 0f, centerX, centerY, camX, camY, zoom)

        when (tile) {
            TileType.WALL -> {
                // 2.5D Multi-Layered Extruded Isometric Wall with Dynamic Multi-Source Illumination
                val topPos = gridToIso(gridX.toFloat(), gridY.toFloat(), 1.0f, centerX, centerY, camX, camY, zoom)

                val wallBaseColor = lightingEngine.blendColorWithLighting(baseR = 85, baseG = 135, baseB = 140, lighting = lighting, palette = palette)
                val wallMidColor = lightingEngine.blendColorWithLighting(baseR = 120, baseG = 180, baseB = 175, lighting = lighting, palette = palette)
                val wallTopColor = lightingEngine.blendColorWithLighting(baseR = 175, baseG = 235, baseB = 230, lighting = lighting, palette = palette)
                val conduitColor = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 170, baseB = 45, lighting = lighting, palette = palette)

                textPaint.textSize = 10.5f * zoom
                textPaint.isFakeBoldText = true

                // Wall Base / Pillars
                textPaint.color = wallBaseColor
                canvas.drawText("[|###|]", basePos.x, basePos.y - 4f * zoom, textPaint)

                // Wall Conduit / Power Cable Mid-Section
                textPaint.color = wallMidColor
                canvas.drawText("|#==#|", basePos.x, basePos.y - 12f * zoom, textPaint)

                // High-voltage warning sign on alternate walls
                if ((gridX + gridY) % 3 == 0) {
                    smallPaint.textSize = 8f * zoom
                    smallPaint.color = conduitColor
                    canvas.drawText("[!]", basePos.x, basePos.y - 19f * zoom, smallPaint)
                }

                // Wall Top Roof Diamond
                textPaint.color = wallTopColor
                canvas.drawText("/#####\\", topPos.x, topPos.y - 3f * zoom, textPaint)
                canvas.drawText("\\#####/", topPos.x, topPos.y + 6f * zoom, textPaint)
            }

            TileType.TOXIC_POOL -> {
                // Shimmering Chemopunk Acid Pool with animated fluid currents
                val waveIdx = ((animTime * 5.0f + gridX * 2.0f + gridY).toInt() % 4)
                val waveGlyphs = listOf("≈ ~ ≈", "% ≈ %", "~ % ~", "≈ % ≈")
                val toxicGlyph = waveGlyphs[waveIdx]

                val pulse = (sin(animTime * 6f + gridX + gridY) * 0.18f + 0.82f)
                val dynamicToxicLighting = lighting.copy(totalIntensity = lighting.totalIntensity * pulse)

                val toxicColor = lightingEngine.blendColorWithLighting(baseR = 30, baseG = 255, baseB = 70, lighting = dynamicToxicLighting, palette = palette)
                val rimColor = lightingEngine.blendColorWithLighting(baseR = 55, baseG = 175, baseB = 85, lighting = lighting, palette = palette)

                textPaint.textSize = 10f * zoom
                textPaint.isFakeBoldText = true

                // Slime Rim
                textPaint.color = rimColor
                canvas.drawText("/  ~  \\", basePos.x, basePos.y - 6f * zoom, textPaint)

                // Boiling Core
                textPaint.color = toxicColor
                canvas.drawText(toxicGlyph, basePos.x, basePos.y + 2f * zoom, textPaint)

                // Lower Border
                textPaint.color = rimColor
                canvas.drawText("\\  ~  /", basePos.x, basePos.y + 9f * zoom, textPaint)
            }

            TileType.EXTRACTION_LIFT -> {
                // Vault 13 / Sector 7 Reinforced Extraction Elevator Lift
                val liftPulse = (sin(animTime * 5.5f) * 0.25f + 0.75f)
                val beaconColor = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 220, baseB = 0, lighting = lighting.copy(totalIntensity = lighting.totalIntensity * liftPulse), palette = palette)
                val frameColor = lightingEngine.blendColorWithLighting(baseR = 79, baseG = 209, baseB = 197, lighting = lighting, palette = palette)

                textPaint.textSize = 9.5f * zoom
                textPaint.isFakeBoldText = true

                textPaint.color = beaconColor
                canvas.drawText("/[ E ]\\", basePos.x, basePos.y - 7f * zoom, textPaint)
                canvas.drawText(">>LIFT<<", basePos.x, basePos.y + 1f * zoom, textPaint)

                textPaint.color = frameColor
                canvas.drawText("\\[===]/", basePos.x, basePos.y + 8f * zoom, textPaint)
            }

            TileType.DOOR -> {
                // Bulkhead Airlock Door
                val doorColor = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 175, baseB = 40, lighting = lighting, palette = palette)
                textPaint.textSize = 10f * zoom
                textPaint.isFakeBoldText = true
                textPaint.color = doorColor

                canvas.drawText("[|DOOR|]", basePos.x, basePos.y - 4f * zoom, textPaint)
                canvas.drawText("<===>", basePos.x, basePos.y + 5f * zoom, textPaint)
            }

            TileType.FLOOR -> {
                // Chemopunk Industrial Steel Grate Diamond
                val floorGrit = (gridX * 7 + gridY * 13) % 4
                val floorColor = lightingEngine.blendColorWithLighting(baseR = 65, baseG = 85, baseB = 95, lighting = lighting, palette = palette)

                textPaint.textSize = 9f * zoom
                textPaint.isFakeBoldText = false
                textPaint.color = floorColor

                when (floorGrit) {
                    0 -> {
                        canvas.drawText("/ · \\", basePos.x, basePos.y - 5f * zoom, textPaint)
                        canvas.drawText("· : ·", basePos.x, basePos.y + 2f * zoom, textPaint)
                        canvas.drawText("\\ · /", basePos.x, basePos.y + 8f * zoom, textPaint)
                    }
                    1 -> {
                        canvas.drawText("/ + \\", basePos.x, basePos.y - 5f * zoom, textPaint)
                        canvas.drawText("+ # +", basePos.x, basePos.y + 2f * zoom, textPaint)
                        canvas.drawText("\\ + /", basePos.x, basePos.y + 8f * zoom, textPaint)
                    }
                    2 -> {
                        canvas.drawText("/ - \\", basePos.x, basePos.y - 5f * zoom, textPaint)
                        canvas.drawText("- · -", basePos.x, basePos.y + 2f * zoom, textPaint)
                        canvas.drawText("\\ - /", basePos.x, basePos.y + 8f * zoom, textPaint)
                    }
                    else -> {
                        canvas.drawText("/ · \\", basePos.x, basePos.y - 5f * zoom, textPaint)
                        canvas.drawText("· * ·", basePos.x, basePos.y + 2f * zoom, textPaint)
                        canvas.drawText("\\ · /", basePos.x, basePos.y + 8f * zoom, textPaint)
                    }
                }
            }
        }

        // Selection Reticle on Targeted Tile
        if (isSelected) {
            val reticleColor = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 220, baseB = 0, lighting = lighting.copy(totalIntensity = 1.3f), palette = palette)
            textPaint.textSize = 14f * zoom
            textPaint.isFakeBoldText = true
            textPaint.color = reticleColor

            val bounce = sin(animTime * 8f) * 2f * zoom
            canvas.drawText("⌜     ⌝", basePos.x, basePos.y - 14f * zoom - bounce, textPaint)
            canvas.drawText("⌞     ⌟", basePos.x, basePos.y + 16f * zoom + bounce, textPaint)
        }
    }

    private fun renderPlayerSprite(
        canvas: android.graphics.Canvas,
        player: Player,
        centerX: Float,
        centerY: Float,
        zoom: Float,
        lighting: TileLighting,
        palette: Int,
        animTime: Float
    ) {
        val walkBob = sin(animTime * 4f) * 0.05f
        val elevation = 0.42f + walkBob
        val pos = gridToIso(player.x, player.y, elevation, centerX, centerY, player.x, player.y, zoom)

        val playerGlow = lightingEngine.blendColorWithLighting(baseR = 79, baseG = 209, baseB = 197, lighting = lighting.copy(totalIntensity = 1.1f), palette = palette)
        val armorColor = lightingEngine.blendColorWithLighting(baseR = 240, baseG = 250, baseB = 255, lighting = lighting.copy(totalIntensity = 1.1f), palette = palette)

        textPaint.textSize = 12f * zoom
        textPaint.isFakeBoldText = true

        // Player Direction Badge
        val dirArrow = when {
            player.angleDegrees in 45f..135f -> "EAST ▶"
            player.angleDegrees in 135f..225f -> "SOUTH ▼"
            player.angleDegrees in 225f..315f -> "WEST ◀"
            else -> "NORTH ▲"
        }

        smallPaint.textSize = 7.5f * zoom
        smallPaint.isFakeBoldText = true
        smallPaint.color = playerGlow
        canvas.drawText("< $dirArrow >", pos.x, pos.y - 34f * zoom, smallPaint)

        // Player Name & Level
        smallPaint.textSize = 8.5f * zoom
        smallPaint.color = armorColor
        canvas.drawText("${player.name} [LV.${player.level}]", pos.x, pos.y - 24f * zoom, smallPaint)

        // Player ASCII Body
        textPaint.color = armorColor
        canvas.drawText("[@]", pos.x, pos.y - 12f * zoom, textPaint)

        textPaint.textSize = 10f * zoom
        textPaint.color = playerGlow
        canvas.drawText("/|\\-=", pos.x + 3f * zoom, pos.y - 2f * zoom, textPaint)
        canvas.drawText("/ \\", pos.x, pos.y + 7f * zoom, textPaint)

        // Player Mini HP Bar: [■■■■□]
        val hpFrac = (player.hp.toFloat() / player.maxHp.toFloat()).coerceIn(0f, 1f)
        val pips = (hpFrac * 6).toInt()
        val hpBar = "■".repeat(pips) + "□".repeat(6 - pips)
        smallPaint.textSize = 8f * zoom
        smallPaint.color = playerGlow
        canvas.drawText("[$hpBar ${player.hp}HP]", pos.x, pos.y + 17f * zoom, smallPaint)
    }

    private fun renderEnemySprite(
        canvas: android.graphics.Canvas,
        enemy: Enemy,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float,
        lighting: TileLighting,
        palette: Int,
        animTime: Float,
        isSelected: Boolean
    ) {
        // State-driven vertical hover and panic jitter
        val hover = when (enemy.state) {
            NpcState.FLEE -> sin(animTime * 14f + enemy.x) * 0.15f
            NpcState.AGGRESSIVE -> sin(animTime * 8f + enemy.x) * 0.11f
            NpcState.PATROL -> sin(animTime * 4.5f + enemy.x) * 0.08f
        }
        val pos = gridToIso(enemy.x, enemy.y, 0.42f + hover, centerX, centerY, camX, camY, zoom)

        // Color coding depending on NPC state machine mode
        val baseR: Int
        val baseG: Int
        val baseB: Int
        when (enemy.state) {
            NpcState.FLEE -> {
                baseR = 255
                baseG = 215
                baseB = 50
            }
            NpcState.AGGRESSIVE -> {
                baseR = 255
                baseG = 50
                baseB = 75
            }
            NpcState.PATROL -> {
                baseR = 79
                baseG = 209
                baseB = 197
            }
        }

        val enemyColor = lightingEngine.blendColorWithLighting(baseR = baseR, baseG = baseG, baseB = baseB, lighting = lighting, palette = palette)
        val hpColor = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 160, baseB = 60, lighting = lighting, palette = palette)

        textPaint.textSize = 12f * zoom
        textPaint.isFakeBoldText = true
        textPaint.color = enemyColor

        // Hostile ASCII Character Sprite
        val glyphStr = "[${enemy.asciiGlyph}]"
        canvas.drawText(glyphStr, pos.x, pos.y - 8f * zoom, textPaint)

        textPaint.textSize = 9.5f * zoom
        canvas.drawText("▲-▲", pos.x, pos.y - 18f * zoom, textPaint)
        canvas.drawText("d b", pos.x, pos.y + 2f * zoom, textPaint)

        // State Machine Badge above NPC
        smallPaint.textSize = 8.5f * zoom
        smallPaint.isFakeBoldText = true
        when (enemy.state) {
            NpcState.AGGRESSIVE -> {
                val flash = ((animTime * 6f).toInt() % 2 == 0)
                smallPaint.color = if (flash) android.graphics.Color.rgb(255, 45, 65) else android.graphics.Color.rgb(255, 180, 40)
                canvas.drawText("⚔ [AGGRO!]", pos.x, pos.y - 36f * zoom, smallPaint)
            }
            NpcState.FLEE -> {
                smallPaint.color = android.graphics.Color.rgb(255, 220, 60)
                canvas.drawText("💨 [FLEEING]", pos.x, pos.y - 36f * zoom, smallPaint)
            }
            NpcState.PATROL -> {
                smallPaint.color = lightingEngine.blendColorWithLighting(79, 209, 197, lighting, palette)
                val wpText = if (enemy.patrolWaypoints.isNotEmpty()) " (W${enemy.currentWaypointIdx + 1})" else ""
                canvas.drawText("👁 [PATROL$wpText]", pos.x, pos.y - 36f * zoom, smallPaint)
            }
        }

        // Enemy Name & Mini HP Bar
        smallPaint.textSize = 8.5f * zoom
        smallPaint.isFakeBoldText = true
        smallPaint.color = enemyColor
        canvas.drawText(enemy.name, pos.x, pos.y - 26f * zoom, smallPaint)

        val hpFrac = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
        val pips = (hpFrac * 5).toInt()
        val hpBar = "■".repeat(pips) + "□".repeat(5 - pips)
        smallPaint.color = hpColor
        canvas.drawText("[$hpBar ${enemy.hp}HP]", pos.x, pos.y + 13f * zoom, smallPaint)

        if (isSelected) {
            smallPaint.color = android.graphics.Color.YELLOW
            canvas.drawText(">> V.A.T.S. TARGET LOCKED <<", pos.x, pos.y - 46f * zoom, smallPaint)
        }
    }

    private fun renderActiveLightEmitters(
        canvas: android.graphics.Canvas,
        lightSources: List<LightSource>,
        centerX: Float,
        centerY: Float,
        playerX: Float,
        playerY: Float,
        zoom: Float,
        animTime: Float,
        palette: Int
    ) {
        lightSources.filter { it.type == LightType.POINT_TORCH || it.type == LightType.FLARE_EMERGENCY }.forEach { light ->
            val flicker = sin(animTime * 8f + light.gridX) * 1.5f * zoom
            val pos = gridToIso(light.gridX, light.gridY, 0.4f, centerX, centerY, playerX, playerY, zoom)

            smallPaint.textSize = 10f * zoom
            smallPaint.isFakeBoldText = true

            if (light.type == LightType.FLARE_EMERGENCY) {
                // Chemical Emergency Flare
                smallPaint.color = android.graphics.Color.rgb(255, 60, 100)
                canvas.drawText("*FLARE*", pos.x, pos.y - 8f * zoom - flicker, smallPaint)
                canvas.drawText(" ( ! ) ", pos.x, pos.y + 2f * zoom, smallPaint)
            } else {
                // Point Torch
                smallPaint.color = android.graphics.Color.rgb(255, 180, 50)
                canvas.drawText("♨ TORCH", pos.x, pos.y - 8f * zoom - flicker, smallPaint)
                canvas.drawText("  |  ", pos.x, pos.y + 2f * zoom, smallPaint)
            }
        }
    }

    private fun renderPathPreview(
        canvas: android.graphics.Canvas,
        playerX: Float,
        playerY: Float,
        targetX: Float,
        targetY: Float,
        centerX: Float,
        centerY: Float,
        zoom: Float,
        palette: Int,
        animTime: Float
    ) {
        val dist = hypot(targetX - playerX, targetY - playerY)
        if (dist < 0.2f) return

        val steps = (dist * 3.5f).toInt().coerceIn(2, 20)
        val apCost = (dist * 1.2f).toInt().coerceAtLeast(1)

        val dotColor = lightingEngine.blendColorWithLighting(baseR = 79, baseG = 209, baseB = 197, lighting = TileLighting(1.0f, 79, 209, 197, true, false), palette = palette)
        smallPaint.color = dotColor
        smallPaint.textSize = 9f * zoom
        smallPaint.isFakeBoldText = true

        for (i in 1..steps) {
            val t = i.toFloat() / steps.toFloat()
            val gx = playerX + (targetX - playerX) * t
            val gy = playerY + (targetY - playerY) * t
            val pos = gridToIso(gx, gy, 0.1f, centerX, centerY, playerX, playerY, zoom)

            val char = if (i == steps) "▶" else "·"
            canvas.drawText(char, pos.x, pos.y, smallPaint)
        }

        // Target Destination AP Cost Label
        val endPos = gridToIso(targetX, targetY, 0.5f, centerX, centerY, playerX, playerY, zoom)
        smallPaint.textSize = 8.5f * zoom
        smallPaint.color = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 220, baseB = 40, lighting = TileLighting(1.0f, 255, 220, 40, true, false), palette = palette)
        canvas.drawText("AP -$apCost [${String.format("%.1f", dist)}m]", endPos.x, endPos.y - 20f * zoom, smallPaint)
    }

    private fun renderEngagementRadius(
        canvas: android.graphics.Canvas,
        playerX: Float,
        playerY: Float,
        radiusTiles: Float,
        centerX: Float,
        centerY: Float,
        zoom: Float,
        palette: Int,
        animTime: Float
    ) {
        val segments = 16
        val color = lightingEngine.blendColorWithLighting(baseR = 79, baseG = 209, baseB = 197, lighting = TileLighting(0.45f, 79, 209, 197, true, false), palette = palette)
        linePaint.color = color
        linePaint.strokeWidth = 1.2f * zoom

        engagementPath.reset()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2.0 * Math.PI
            val rx = playerX + radiusTiles * cos(theta).toFloat()
            val ry = playerY + radiusTiles * sin(theta).toFloat()
            val pos = gridToIso(rx, ry, 0f, centerX, centerY, playerX, playerY, zoom)

            if (i == 0) engagementPath.moveTo(pos.x, pos.y) else engagementPath.lineTo(pos.x, pos.y)
        }
        canvas.drawPath(engagementPath, linePaint)
    }

    private fun renderAtmosphericParticles(
        canvas: android.graphics.Canvas,
        mapGrid: List<List<TileType>>,
        centerX: Float,
        centerY: Float,
        playerX: Float,
        playerY: Float,
        zoom: Float,
        animTime: Float,
        palette: Int
    ) {
        val toxicColor = lightingEngine.blendColorWithLighting(baseR = 40, baseG = 255, baseB = 80, lighting = TileLighting(0.85f, 40, 255, 80, true, false), palette = palette)
        smallPaint.color = toxicColor
        smallPaint.textSize = 9f * zoom
        smallPaint.isFakeBoldText = true

        val rows = mapGrid.size
        val cols = if (rows > 0) mapGrid[0].size else 0

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (mapGrid[r][c] == TileType.TOXIC_POOL) {
                    val pTime = animTime * 2.0f + (c * 1.5f) + (r * 2.2f)
                    val particleProgress = (pTime % 1.0f)
                    val particleYOffset = particleProgress * 1.2f
                    val particleXJitter = sin(pTime * 5.0f) * 0.15f

                    val pos = gridToIso(c + 0.5f + particleXJitter, r + 0.5f, particleYOffset, centerX, centerY, playerX, playerY, zoom)
                    val glyph = if (particleProgress > 0.6f) "°" else if (particleProgress > 0.3f) "^" else "~"

                    canvas.drawText(glyph, pos.x, pos.y, smallPaint)
                }
            }
        }
    }

    private fun renderVatsTargetOverlay(
        canvas: android.graphics.Canvas,
        enemy: Enemy,
        player: Player,
        centerX: Float,
        centerY: Float,
        zoom: Float,
        palette: Int,
        animTime: Float
    ) {
        val pos = gridToIso(enemy.x, enemy.y, 0.9f, centerX, centerY, player.x, player.y, zoom)
        val dist = hypot(player.x - enemy.x, player.y - enemy.y)

        val headChance = (85 - (dist * 8)).toInt().coerceIn(15, 95)
        val torsoChance = (95 - (dist * 5)).toInt().coerceIn(35, 95)
        val armsChance = (80 - (dist * 7)).toInt().coerceIn(20, 90)
        val legsChance = (88 - (dist * 6)).toInt().coerceIn(25, 95)

        val vatsColor = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 215, baseB = 0, lighting = TileLighting(1f, 255, 215, 0, true, false), palette = palette)
        val cardBgColor = android.graphics.Color.argb(220, 8, 14, 18)

        smallPaint.textSize = 8.5f * zoom
        smallPaint.isFakeBoldText = true

        val boxLeft = pos.x + 36f * zoom
        val boxTop = pos.y - 45f * zoom
        val boxWidth = 118f * zoom
        val boxHeight = 78f * zoom

        bgPaint.color = cardBgColor
        canvas.drawRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, bgPaint)

        linePaint.color = vatsColor
        linePaint.strokeWidth = 1f * zoom
        canvas.drawRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, linePaint)

        smallPaint.color = vatsColor
        smallPaint.textAlign = Paint.Align.LEFT

        val stateBadge = when (enemy.state) {
            NpcState.AGGRESSIVE -> "⚔ AGGRO"
            NpcState.FLEE -> "💨 FLEE"
            NpcState.PATROL -> "👁 PATROL"
        }

        canvas.drawText("V.A.T.S. TARGET // $stateBadge", boxLeft + 6f * zoom, boxTop + 12f * zoom, smallPaint)
        canvas.drawText("• HEAD:  $headChance% [2x]", boxLeft + 6f * zoom, boxTop + 24f * zoom, smallPaint)
        canvas.drawText("• TORSO: $torsoChance%", boxLeft + 6f * zoom, boxTop + 35f * zoom, smallPaint)
        canvas.drawText("• ARMS:  $armsChance%", boxLeft + 6f * zoom, boxTop + 46f * zoom, smallPaint)
        canvas.drawText("• LEGS:  $legsChance%", boxLeft + 6f * zoom, boxTop + 57f * zoom, smallPaint)
        canvas.drawText("• SENS: RAD ${enemy.detectionRadius.toInt()}m", boxLeft + 6f * zoom, boxTop + 69f * zoom, smallPaint)

        smallPaint.textAlign = Paint.Align.CENTER
    }

    private fun renderTacticalHudAndCrt(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        selectedTile: Pair<Int, Int>?,
        mapGrid: List<List<TileType>>,
        enemies: List<Enemy>,
        player: Player,
        activeLightCount: Int,
        palette: Int,
        animTime: Float,
        zoom: Float
    ) {
        val overlayColor = lightingEngine.blendColorWithLighting(baseR = 79, baseG = 209, baseB = 197, lighting = TileLighting(1f, 79, 209, 197, true, false), palette = palette)
        val mutedColor = lightingEngine.blendColorWithLighting(baseR = 140, baseG = 150, baseB = 160, lighting = TileLighting(0.7f, 140, 150, 160, true, false), palette = palette)

        smallPaint.textSize = 10f
        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.isFakeBoldText = true

        // Top-Left Header Coordinates
        smallPaint.color = overlayColor
        canvas.drawText("FALLOUT DYNAMIC LIGHT ENGINE // SECTOR 07", 16f, 22f, smallPaint)

        smallPaint.color = mutedColor
        val px = player.x.toInt()
        val py = player.y.toInt()
        val toxRatio = player.toxicity
        val zoomInt = (zoom * 10).toInt()
        canvas.drawText("POS: [X:$px, Y:$py] | LIGHTS: $activeLightCount | RADS: $toxRatio% | ZOOM: ${zoomInt / 10}.${zoomInt % 10}x", 16f, 36f, smallPaint)

        // Top-Right Selected Tile / Target Info
        if (selectedTile != null) {
            val (sx, sy) = selectedTile
            val targetEnemy = enemies.firstOrNull { it.isAlive && it.x.toInt() == sx && it.y.toInt() == sy }

            smallPaint.textAlign = Paint.Align.RIGHT
            smallPaint.color = lightingEngine.blendColorWithLighting(baseR = 255, baseG = 210, baseB = 50, lighting = TileLighting(1f, 255, 210, 50, true, false), palette = palette)

            if (targetEnemy != null) {
                val dist = hypot(player.x - targetEnemy.x, player.y - targetEnemy.y)
                val hitChance = (95 - (dist * 7)).toInt().coerceIn(20, 95)
                val distInt = (dist * 10).toInt()
                canvas.drawText("HOSTILE: ${targetEnemy.name} [${targetEnemy.state.name}]", width - 16f, 22f, smallPaint)
                canvas.drawText("HP: ${targetEnemy.hp}/${targetEnemy.maxHp} | HIT: $hitChance% [DIST: ${distInt / 10}.${distInt % 10}m]", width - 16f, 36f, smallPaint)
            } else if (sy in mapGrid.indices && sx in mapGrid[sy].indices) {
                val tile = mapGrid[sy][sx]
                canvas.drawText("TILE: $tile [X:$sx, Y:$sy]", width - 16f, 22f, smallPaint)
                canvas.drawText("TAP TO MOVE / INSPECT", width - 16f, 36f, smallPaint)
            }
        }

        // Bottom CRT Scanline Simulation on Canvas
        val scanlineSpacing = 5f
        scanPaint.strokeWidth = 1.2f
        var scanY = 0f
        while (scanY < height) {
            canvas.drawLine(0f, scanY, width, scanY, scanPaint)
            scanY += scanlineSpacing
        }

        // Rolling Cathode Bar (Subtle horizontal scan beam)
        val beamY = ((animTime * 85f) % height)
        beamPaint.strokeWidth = 14f
        canvas.drawLine(0f, beamY, width, beamY, beamPaint)
    }
}
