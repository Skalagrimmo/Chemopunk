package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Player
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.AmberTerminal
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TextGreen
import com.example.ui.theme.ToxicRed

@Composable
fun HudOverlay(
    player: Player,
    audioProfile: com.example.engine.AmbientAudioProfile,
    onOpenInventory: () -> Unit,
    onOpenStoryNotes: () -> Unit,
    onOpenMarkdownEditor: () -> Unit,
    onTogglePalette: () -> Unit,
    onToggleAudioMute: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ImmersiveSurface)
            .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(12.dp)
    ) {
        // App Header Bar with "ISO" Logo and Engine Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Glow Teal Logo Box
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ImmersiveTeal)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ISO",
                        color = ImmersiveBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column {
                    Text(
                        text = "FALLOUT ISO ASCII",
                        color = ImmersiveText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (audioProfile.dangerLevel > 0.4f) ToxicRed else Color(0xFF22C55E))
                        )
                        Text(
                            text = if (audioProfile.isMuted) "AUDIO: MUTED" else "DSP: ${audioProfile.audioStatusDescription}",
                            color = if (audioProfile.dangerLevel > 0.4f) ToxicRed else ImmersiveTextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Action Control Bar
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onToggleAudioMute,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_toggle_audio")
                ) {
                    Icon(
                        imageVector = if (audioProfile.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Audio",
                        tint = if (audioProfile.isMuted) ImmersiveTextMuted else ImmersiveTeal
                    )
                }
                IconButton(
                    onClick = onTogglePalette,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_toggle_palette")
                ) {
                    Icon(Icons.Default.ColorLens, contentDescription = "Palette", tint = ImmersiveTeal)
                }
                IconButton(
                    onClick = onOpenInventory,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_inventory")
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = "Inventory", tint = ImmersiveTeal)
                }
                IconButton(
                    onClick = onOpenStoryNotes,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_story")
                ) {
                    Icon(Icons.Default.Description, contentDescription = "Story", tint = ImmersiveAccentOrange)
                }
                IconButton(
                    onClick = onOpenMarkdownEditor,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_md_editor")
                ) {
                    Icon(Icons.Default.Code, contentDescription = "MD Editor", tint = AcidYellow)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // System Performance Hardware Metrics & Audio Soundscape Ticker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ImmersiveBackground)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("LIGHT", color = ImmersiveTeal, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text("${String.format("%.1f", audioProfile.lightingIntensity * 100)}%", color = ImmersiveText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("DANGER", color = if (audioProfile.dangerLevel > 0.4f) ToxicRed else ImmersiveTeal, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text("${(audioProfile.dangerLevel * 100).toInt()}%", color = if (audioProfile.dangerLevel > 0.4f) ToxicRed else ImmersiveText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("DSP-SYNTH", color = ImmersiveAccentOrange, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text(if (audioProfile.isMuted) "OFF" else "22kHz PCM", color = ImmersiveText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("DRAW-CL", color = ImmersiveTeal, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text("14/SCN", color = ImmersiveText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Health & Toxicity Gauges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // HP Bar
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "HP (${player.name})", color = ImmersiveTeal, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "${player.hp}/${player.maxHp}", color = ImmersiveText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { (player.hp.toFloat() / player.maxHp.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ImmersiveTeal,
                    trackColor = Color(0xFF0F3835)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Toxicity Bar
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "TOXICITY", color = ToxicRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "${player.toxicity}%", color = ToxicRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { (player.toxicity.toFloat() / player.maxToxicity.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ToxicRed,
                    trackColor = Color(0xFF330005)
                )
            }
        }
    }
}

