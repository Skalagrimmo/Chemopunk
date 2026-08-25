package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.PhosphorGreen

/**
 * Smartphone-Optimized Dual Thumb Control Deck.
 * Left thumb: 8-way directional tactile navigation matrix.
 * Right thumb: High-priority ergonomic action buttons (Action, Inventory, Wait).
 */
@Composable
fun ControlDPad(
    onMoveNorth: () -> Unit,
    onMoveSouth: () -> Unit,
    onMoveWest: () -> Unit,
    onMoveEast: () -> Unit,
    onMoveNorthWest: () -> Unit = {},
    onMoveNorthEast: () -> Unit = {},
    onMoveSouthWest: () -> Unit = {},
    onMoveSouthEast: () -> Unit = {},
    onAction: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    onWaitTurn: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ImmersiveSurface)
            .border(
                1.dp,
                ImmersiveSurfaceVariant,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Thumb Zone: 8-Way Tactile D-Pad Matrix
        Box(
            modifier = Modifier
                .background(
                    color = ImmersiveBackground,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.2.dp, ImmersiveSurfaceVariant, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: North-West (↖), North (↑), North-East (↗)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DPadCell(
                        icon = Icons.Default.ArrowUpward,
                        rotation = -45f,
                        contentDescription = "North-West",
                        testTag = "btn_move_north_west",
                        tint = AcidYellow,
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        isDiagonal = true,
                        onClick = onMoveNorthWest
                    )

                    DPadCell(
                        icon = Icons.Default.ArrowUpward,
                        rotation = 0f,
                        contentDescription = "North",
                        testTag = "btn_move_north",
                        tint = ImmersiveTeal,
                        shape = RoundedCornerShape(4.dp),
                        isDiagonal = false,
                        onClick = onMoveNorth
                    )

                    DPadCell(
                        icon = Icons.Default.ArrowUpward,
                        rotation = 45f,
                        contentDescription = "North-East",
                        testTag = "btn_move_north_east",
                        tint = AcidYellow,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        isDiagonal = true,
                        onClick = onMoveNorthEast
                    )
                }

                // Row 2: West (←), Center Action (⚡), East (→)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DPadCell(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        rotation = 0f,
                        contentDescription = "West",
                        testTag = "btn_move_west",
                        tint = ImmersiveTeal,
                        shape = RoundedCornerShape(4.dp),
                        isDiagonal = false,
                        onClick = onMoveWest
                    )

                    // Center Primary Action Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ImmersiveTeal.copy(alpha = 0.25f))
                            .border(1.5.dp, ImmersiveTeal, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = ImmersiveTeal),
                                onClick = onAction
                            )
                            .testTag("btn_action_center"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Action",
                            tint = PhosphorGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DPadCell(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        rotation = 0f,
                        contentDescription = "East",
                        testTag = "btn_move_east",
                        tint = ImmersiveTeal,
                        shape = RoundedCornerShape(4.dp),
                        isDiagonal = false,
                        onClick = onMoveEast
                    )
                }

                // Row 3: South-West (↙), South (↓), South-East (↘)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DPadCell(
                        icon = Icons.Default.ArrowDownward,
                        rotation = 45f,
                        contentDescription = "South-West",
                        testTag = "btn_move_south_west",
                        tint = AcidYellow,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 4.dp),
                        isDiagonal = true,
                        onClick = onMoveSouthWest
                    )

                    DPadCell(
                        icon = Icons.Default.ArrowDownward,
                        rotation = 0f,
                        contentDescription = "South",
                        testTag = "btn_move_south",
                        tint = ImmersiveTeal,
                        shape = RoundedCornerShape(4.dp),
                        isDiagonal = false,
                        onClick = onMoveSouth
                    )

                    DPadCell(
                        icon = Icons.Default.ArrowDownward,
                        rotation = -45f,
                        contentDescription = "South-East",
                        testTag = "btn_move_south_east",
                        tint = AcidYellow,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 10.dp),
                        isDiagonal = true,
                        onClick = onMoveSouthEast
                    )
                }
            }
        }

        // Right Thumb Zone: Oversized Smartphone Action Triggers
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Primary Action Button
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveTeal,
                    contentColor = ImmersiveBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(42.dp)
                    .fillMaxWidth()
                    .border(1.dp, PhosphorGreen, RoundedCornerShape(12.dp))
                    .testTag("btn_action")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ImmersiveBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ACTION",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }

            // 2. Secondary Row: Inventory & Wait Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Inventory
                Button(
                    onClick = onOpenInventory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveSurfaceVariant,
                        contentColor = ImmersiveTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_dpad_inventory")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "INV",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Wait Turn
                Button(
                    onClick = onWaitTurn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveSurfaceVariant,
                        contentColor = AcidYellow
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_wait_turn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "WAIT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact D-Pad tactile directional cell button.
 */
@Composable
private fun DPadCell(
    icon: ImageVector,
    rotation: Float,
    contentDescription: String,
    testTag: String,
    tint: Color,
    shape: androidx.compose.ui.graphics.Shape,
    isDiagonal: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(if (isDiagonal) ImmersiveSurfaceVariant.copy(alpha = 0.5f) else ImmersiveSurfaceVariant)
            .border(
                0.8.dp,
                if (isDiagonal) tint.copy(alpha = 0.35f) else ImmersiveTeal.copy(alpha = 0.5f),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = tint),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(if (isDiagonal) 15.dp else 18.dp)
                .rotate(rotation)
        )
    }
}
