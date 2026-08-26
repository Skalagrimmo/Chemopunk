package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Enemy
import com.example.data.Player
import com.example.data.TileType
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ToxicRed
import kotlinx.coroutines.delay

/**
 * Compact ASCII-style mini-map rendered from FOV-discovered tiles.
 * Shows walls, floors, hazards, interactables, live enemy blips and the player marker.
 */
@Composable
fun MinimapOverlay(
    mapGrid: List<List<TileType>>,
    discovered: Set<Pair<Int, Int>>,
    player: Player,
    enemies: List<Enemy>,
    modifier: Modifier = Modifier
) {
    if (mapGrid.isEmpty()) return
    val rows = mapGrid.size
    val cols = mapGrid.maxOf { it.size }.coerceAtLeast(1)

    Box(
        modifier = modifier
            .size(width = 112.dp, height = 84.dp)
            .background(ImmersiveBackground.copy(alpha = 0.72f))
            .border(1.dp, ImmersiveSurfaceVariant)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cw = size.width / cols
            val ch = size.height / rows
            for (r in mapGrid.indices) {
                val row = mapGrid[r]
                for (c in row.indices) {
                    if (Pair(c, r) !in discovered) continue
                    val color: Color = when (row[c]) {
                        TileType.WALL -> ImmersiveSurfaceVariant
                        TileType.FLOOR -> ImmersiveSurface
                        TileType.TOXIC_POOL -> ToxicRed.copy(alpha = 0.8f)
                        TileType.INTERACTIVE -> ImmersiveTeal
                        TileType.DOOR -> ImmersiveTeal.copy(alpha = 0.6f)
                        else -> ImmersiveSurface
                    }
                    drawRect(color, Offset(c * cw, r * ch), Size(cw, ch))
                }
            }
            enemies.filter { it.isAlive }.forEach { e ->
                val c = e.x.toInt().coerceIn(0, cols - 1)
                val r = e.y.toInt().coerceIn(0, rows - 1)
                if (Pair(c, r) in discovered) {
                    drawRect(ToxicRed, Offset(c * cw, r * ch), Size(cw, ch))
                }
            }
            val pc = player.x.toInt().coerceIn(0, cols - 1)
            val pr = player.y.toInt().coerceIn(0, rows - 1)
            drawRect(PhosphorGreen, Offset(pc * cw, pr * ch), Size(cw, ch))
        }
    }
}

/**
 * Cosmetic day/night clock derived from wall-clock minutes so it advances without
 * ViewModel changes. Exposes a phase tint that the caller can overlay on the viewport.
 */
@Composable
fun DayNightIndicator(
    modifier: Modifier = Modifier,
    onPhaseTint: (Color) -> Unit = {}
) {
    val minute = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            minute.value = (System.currentTimeMillis() / 60000L) % 1440
            delay(1000)
        }
    }
    val hour = ((minute.value / 60) % 24).toInt()
    val (phase, tint) = when (hour) {
        in 5..7 -> "DAWN" to Color(0xFF3A2A4D).copy(alpha = 0.18f)
        in 8..16 -> "DAY" to Color.Transparent
        in 17..19 -> "DUSK" to Color(0xFF5A2A1A).copy(alpha = 0.20f)
        else -> "NIGHT" to Color(0xFF0A1230).copy(alpha = 0.28f)
    }
    onPhaseTint(tint)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "$phase ${hour.toString().padStart(2, '0')}:00",
            color = ImmersiveTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
