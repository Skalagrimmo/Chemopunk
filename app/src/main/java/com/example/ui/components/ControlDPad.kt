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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ui.theme.AmberTerminal
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ToxicRed

/**
 * Enhanced 8-Way Tactile Tactical D-Pad.
 * Fits within the original compact footprint without enlarging the controller.
 * Provides 8-directional isometric navigation: N, NE, E, SE, S, SW, W, NW + Center V.A.T.S.
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
    onActionVats: () -> Unit,
    onQuickAttack: () -> Unit = onActionVats,
    onWaitTurn: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 8-Way Directional Controller Matrix (Compact 3x3 Grid)
        Box(
            modifier = Modifier
                .background(
                    color = ImmersiveSurface,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.2.dp, ImmersiveSurfaceVariant, RoundedCornerShape(16.dp))
                .padding(5.dp)
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

                // Row 2: West (←), Center V.A.T.S. / Inspect (🎯), East (→)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DPadCell(
                        icon = Icons.Default.ArrowBack,
                        rotation = 0f,
                        contentDescription = "West",
                        testTag = "btn_move_west",
                        tint = ImmersiveTeal,
                        shape = RoundedCornerShape(4.dp),
                        isDiagonal = false,
                        onClick = onMoveWest
                    )

                    // Center Tactical Targeting / Inspect Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ImmersiveTeal.copy(alpha = 0.22f))
                            .border(1.2.dp, ImmersiveTeal, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = ImmersiveTeal),
                                onClick = onActionVats
                            )
                            .testTag("btn_action_center"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Inspect / VATS",
                            tint = ImmersiveTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DPadCell(
                        icon = Icons.Default.ArrowForward,
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

        // Action Tactical Bar: V.A.T.S. [E] & WAIT TURN [Z]
        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onQuickAttack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveTeal,
                    contentColor = ImmersiveBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .fillMaxWidth(0.95f)
                    .testTag("btn_quick_action")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "V.A.T.S. [E]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Button(
                onClick = onWaitTurn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveSurfaceVariant,
                    contentColor = ImmersiveTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .fillMaxWidth(0.95f)
                    .testTag("btn_wait_turn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "WAIT [Z]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
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
            .size(36.dp)
            .clip(shape)
            .background(if (isDiagonal) ImmersiveSurfaceVariant.copy(alpha = 0.6f) else ImmersiveSurfaceVariant)
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
