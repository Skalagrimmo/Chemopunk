package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.Player
import com.example.data.TileType
import com.example.engine.LightSource
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal

/**
 * Jetpack Compose wrapper for OpenGL ES 2.0 SurfaceView 2.5D Isometric ASCII Renderer.
 */
@Composable
fun AsciiGlIsometricView(
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
    var surfaceViewRef by remember { mutableStateOf<AsciiGlSurfaceView?>(null) }
    var scanlineIntensity by remember { mutableFloatStateOf(0.55f) }
    var ditherStrength by remember { mutableFloatStateOf(1.0f) }
    var currentZoom by remember { mutableFloatStateOf(AsciiGlSurfaceView.DEFAULT_ZOOM) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(ImmersiveBackground)
            .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(16.dp))
    ) {
        // Embedded OpenGL ES 2.0 SurfaceView
        AndroidView(
            factory = { ctx ->
                AsciiGlSurfaceView(ctx).apply {
                    this.onTileTapped = onTileTapped
                    this.onLongPressed = onLongPress
                    this.onZoomChanged = { z -> currentZoom = z }
                    surfaceViewRef = this
                }
            },
            update = { glView ->
                glView.onTileTapped = onTileTapped
                glView.onLongPressed = onLongPress
                glView.onZoomChanged = { z -> currentZoom = z }
                glView.updateRenderState(
                    mapGrid = mapGrid,
                    player = player,
                    enemies = enemies,
                    lightSources = lightSources,
                    selectedTile = selectedTile,
                    discoveredTiles = discoveredTiles,
                    floatingTexts = floatingTexts,
                    paletteIndex = paletteIndex,
                    scanlineIntensity = scanlineIntensity,
                    ditherStrength = ditherStrength,
                    screenShakeIntensity = screenShakeIntensity,
                    screenShakeStartTime = screenShakeStartTime
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("ascii_opengl_surface_view")
        )

        // Camera, Flare & Shader Post-Process Controls in Top-Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drop Flare button
                IconButton(
                    onClick = onDropFlare,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveAccentOrange.copy(alpha = 0.25f))
                        .border(1.dp, ImmersiveAccentOrange, CircleShape)
                        .testTag("btn_deploy_flare_gl")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Drop Flare",
                        tint = ImmersiveAccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // CRT Shader Toggle
                IconButton(
                    onClick = {
                        scanlineIntensity = if (scanlineIntensity > 0.1f) 0.0f else 0.55f
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (scanlineIntensity > 0.1f) ImmersiveTeal.copy(alpha = 0.25f)
                            else ImmersiveSurface.copy(alpha = 0.6f)
                        )
                        .border(
                            1.dp,
                            if (scanlineIntensity > 0.1f) ImmersiveTeal else ImmersiveSurfaceVariant,
                            CircleShape
                        )
                        .testTag("btn_toggle_crt_gl")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Toggle CRT Scanlines",
                        tint = if (scanlineIntensity > 0.1f) ImmersiveTeal else ImmersiveSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Zoom Out Button
                IconButton(
                    onClick = {
                        surfaceViewRef?.zoomOut(0.20f)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurface.copy(alpha = 0.85f))
                        .border(1.dp, ImmersiveSurfaceVariant, CircleShape)
                        .testTag("btn_zoom_out_gl")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = AcidYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Zoom Level Badge (when magnified or zoomed out)
                if (kotlin.math.abs(currentZoom - AsciiGlSurfaceView.DEFAULT_ZOOM) > 0.1f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ImmersiveSurface.copy(alpha = 0.90f))
                            .border(1.dp, AcidYellow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "%.1fx".format(currentZoom),
                            color = AcidYellow,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Zoom In Button
                IconButton(
                    onClick = {
                        surfaceViewRef?.zoomIn(0.20f)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurface.copy(alpha = 0.85f))
                        .border(1.dp, ImmersiveSurfaceVariant, CircleShape)
                        .testTag("btn_zoom_in_gl")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = AcidYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Re-center Camera Button
                IconButton(
                    onClick = {
                        surfaceViewRef?.recenterCamera()
                        onRecenterCamera()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurface.copy(alpha = 0.85f))
                        .border(1.dp, ImmersiveSurfaceVariant, CircleShape)
                        .testTag("btn_recenter_camera_gl")
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Re-center Camera",
                        tint = AcidYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Active Target Selection Banner in Top-Left
        if (selectedTile != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ImmersiveSurface.copy(alpha = 0.88f))
                    .border(1.dp, AcidYellow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TARGET: (${selectedTile.first}, ${selectedTile.second})",
                        color = AcidYellow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = onClearSelection,
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Target",
                            tint = AcidYellow,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
