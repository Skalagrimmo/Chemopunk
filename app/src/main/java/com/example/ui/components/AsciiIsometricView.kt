package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.Player
import com.example.data.TileType
import com.example.engine.AsciiIsometricEngine
import com.example.engine.LightSource
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal

@Composable
fun AsciiIsometricView(
    mapGrid: List<List<TileType>>,
    player: Player,
    enemies: List<Enemy>,
    lightSources: List<LightSource> = emptyList(),
    selectedTile: Pair<Int, Int>?,
    discoveredTiles: Set<Pair<Int, Int>>,
    floatingTexts: List<FloatingText>,
    paletteIndex: Int,
    screenShakeIntensity: Float = 0f,
    screenShakeStartTime: Long = 0L,
    onTileTapped: (Int, Int) -> Unit,
    onLongPress: (Float, Float, Int, Int) -> Unit = { _, _, _, _ -> },
    onDropFlare: () -> Unit = {},
    onRecenterCamera: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val engine = remember { AsciiIsometricEngine() }

    var animTime by remember { mutableFloatStateOf(0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomLevel by remember { mutableFloatStateOf(1.05f) }

    // 60 FPS animation ticker for pulsating pools, particle steam, dynamic photon flicker, and CRT scanlines
    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime ->
                animTime = (frameTime - startTime) / 1_000_000_000f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(ImmersiveBackground)
            .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ascii_isometric_canvas")
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                        zoomLevel = (zoomLevel * zoom).coerceIn(0.6f, 2.2f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val centerX = size.width * 0.5f + panOffsetX
                            val centerY = size.height * 0.44f + panOffsetY
                            val gridCoords = engine.screenToGrid(
                                screenX = offset.x,
                                screenY = offset.y,
                                centerX = centerX,
                                centerY = centerY,
                                camX = player.x,
                                camY = player.y,
                                zoom = zoomLevel
                            )
                            onTileTapped(gridCoords.first, gridCoords.second)
                        },
                        onLongPress = { offset ->
                            val centerX = size.width * 0.5f + panOffsetX
                            val centerY = size.height * 0.44f + panOffsetY
                            val gridCoords = engine.screenToGrid(
                                screenX = offset.x,
                                screenY = offset.y,
                                centerX = centerX,
                                centerY = centerY,
                                camX = player.x,
                                camY = player.y,
                                zoom = zoomLevel
                            )
                            onLongPress(offset.x, offset.y, gridCoords.first, gridCoords.second)
                        }
                    )
                }
        ) {
            // Draw Isometric World via AsciiIsometricEngine with Dynamic Lighting
            engine.renderWorld(
                drawScope = this,
                mapGrid = mapGrid,
                player = player,
                enemies = enemies,
                lightSources = lightSources,
                selectedTile = selectedTile,
                discoveredTiles = discoveredTiles,
                floatingTexts = floatingTexts,
                paletteIndex = paletteIndex,
                animTime = animTime,
                zoom = zoomLevel,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                shakeIntensity = screenShakeIntensity,
                shakeStartTime = screenShakeStartTime
            )
        }

        // Camera, Flare Deployment & Zoom Floating Quick Controls in top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drop Chemical Emergency Flare button
                IconButton(
                    onClick = onDropFlare,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveAccentOrange.copy(alpha = 0.25f))
                        .border(1.dp, ImmersiveAccentOrange, CircleShape)
                        .testTag("btn_deploy_flare")
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = "Drop Flare Light",
                        tint = ImmersiveAccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Clear Selection Button (if tile selected)
                if (selectedTile != null) {
                    IconButton(
                        onClick = onClearSelection,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AcidYellow.copy(alpha = 0.2f))
                            .border(1.dp, AcidYellow, CircleShape)
                            .testTag("btn_clear_target")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Deselect", tint = AcidYellow, modifier = Modifier.size(16.dp))
                    }
                }

                // Zoom In
                IconButton(
                    onClick = { zoomLevel = (zoomLevel + 0.15f).coerceAtMost(2.2f) },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurface)
                        .border(1.dp, ImmersiveSurfaceVariant, CircleShape)
                        .testTag("btn_zoom_in")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = ImmersiveTeal, modifier = Modifier.size(16.dp))
                }

                // Zoom Out
                IconButton(
                    onClick = { zoomLevel = (zoomLevel - 0.15f).coerceAtLeast(0.6f) },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurface)
                        .border(1.dp, ImmersiveSurfaceVariant, CircleShape)
                        .testTag("btn_zoom_out")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = ImmersiveTeal, modifier = Modifier.size(16.dp))
                }

                // Recenter Camera to Player
                IconButton(
                    onClick = {
                        panOffsetX = 0f
                        panOffsetY = 0f
                        onRecenterCamera()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurface)
                        .border(1.dp, ImmersiveTeal.copy(alpha = 0.5f), CircleShape)
                        .testTag("btn_recenter_cam")
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = "Recenter", tint = ImmersiveTeal, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Tactical Watermark Badge
        Text(
            text = "DYNAMIC LIGHT ENGINE // MULTI-SOURCE PHOTON ASCII",
            color = ImmersiveTeal.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
    }
}
