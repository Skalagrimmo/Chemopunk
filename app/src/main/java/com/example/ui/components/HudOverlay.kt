package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Player
import com.example.engine.AmbientAudioProfile
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ToxicRed

/**
 * Smartphone-Optimized Cyberpunk HUD Header.
 * Rebuilt for crystal-clear readability, comfortable spacing,
 * and high-contrast tactile feedback on handheld displays.
 */
@Composable
fun HudOverlay(
    player: Player,
    audioProfile: AmbientAudioProfile,
    onOpenInventory: () -> Unit,
    onOpenStoryNotes: () -> Unit,
    onOpenMarkdownEditor: () -> Unit,
    onOpenQuests: () -> Unit = {},
    onTogglePalette: () -> Unit = {},
    onToggleAudioMute: () -> Unit = {},
    isEncumbered: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ImmersiveSurface)
            .border(
                1.dp,
                ImmersiveSurfaceVariant,
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top Header Row: System Identity + Quick Control Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Player Identity Badge & Credits
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ImmersiveTeal)
                        .border(1.dp, PhosphorGreen, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ISO",
                        color = ImmersiveBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = player.name,
                            color = ImmersiveText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AcidYellow.copy(alpha = 0.2f))
                                .border(0.8.dp, AcidYellow, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "L${player.level}",
                                color = AcidYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "◈ ${player.credits} CR",
                            color = PhosphorGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "•",
                            color = ImmersiveTextMuted,
                            fontSize = 8.sp
                        )
                        Text(
                            text = if (audioProfile.isMuted) "MUTED" else "DSP ON",
                            color = if (audioProfile.dangerLevel > 0.4f) ToxicRed else ImmersiveTextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (isEncumbered) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "⚠ OVER-ENCUMBERED",
                            color = ToxicRed,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right: Tactile Quick Action Buttons for Smartphone
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleAudioMute,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .border(0.8.dp, ImmersiveTeal.copy(alpha = 0.3f), CircleShape)
                        .testTag("btn_toggle_audio")
                ) {
                    Icon(
                        imageVector = if (audioProfile.isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Toggle Audio",
                        tint = if (audioProfile.isMuted) ImmersiveTextMuted else ImmersiveTeal,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePalette,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .border(0.8.dp, ImmersiveTeal.copy(alpha = 0.3f), CircleShape)
                        .testTag("btn_toggle_palette")
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = "Palette",
                        tint = ImmersiveTeal,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenQuests,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .border(0.8.dp, ImmersiveAccentOrange.copy(alpha = 0.5f), CircleShape)
                        .testTag("btn_quests")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Quests",
                        tint = ImmersiveAccentOrange,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenInventory,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .border(0.8.dp, ImmersiveTeal.copy(alpha = 0.5f), CircleShape)
                        .testTag("btn_inventory")
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Inventory",
                        tint = ImmersiveTeal,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenStoryNotes,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .border(0.8.dp, ImmersiveAccentOrange.copy(alpha = 0.5f), CircleShape)
                        .testTag("btn_story")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Story",
                        tint = ImmersiveAccentOrange,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenMarkdownEditor,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .border(0.8.dp, AcidYellow.copy(alpha = 0.5f), CircleShape)
                        .testTag("btn_md_editor")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "MD Editor",
                        tint = AcidYellow,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dual Smartphone Status Gauges (HP & Toxicity)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // HP Gauge
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ImmersiveBackground)
                    .border(0.8.dp, ImmersiveTeal.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = ImmersiveTeal,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "HEALTH",
                            color = ImmersiveTeal,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${player.hp}/${player.maxHp}",
                        color = ImmersiveText,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (player.hp.toFloat() / player.maxHp.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ImmersiveTeal,
                    trackColor = Color(0xFF0C2B28)
                )
            }

            // Toxicity / Radiation Gauge
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ImmersiveBackground)
                    .border(0.8.dp, ToxicRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ToxicRed,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "TOXICITY",
                            color = ToxicRed,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${player.toxicity}%",
                        color = ToxicRed,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
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

        Spacer(modifier = Modifier.height(6.dp))

        // Slim EXP Progression Bar for Mobile
        val expNeeded = if (player.level > 0) player.level * 100 else 100
        val expCurrent = player.exp % expNeeded
        val expProgress = (expCurrent.toFloat() / expNeeded.toFloat()).coerceIn(0f, 1f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ImmersiveBackground)
                .border(0.8.dp, AcidYellow.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "EXP",
                color = AcidYellow,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { expProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AcidYellow,
                trackColor = Color(0xFF262305)
            )
            Text(
                text = "$expCurrent / $expNeeded",
                color = ImmersiveTextMuted,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
