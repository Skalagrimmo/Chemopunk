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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.AmberTerminal
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.PhosphorGreen

@Composable
fun ControlDPad(
    onMoveNorth: () -> Unit,
    onMoveSouth: () -> Unit,
    onMoveWest: () -> Unit,
    onMoveEast: () -> Unit,
    onActionVats: () -> Unit,
    onQuickAttack: () -> Unit = onActionVats,
    onWaitTurn: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // D-Pad Directional Controls for Isometric Axonometric Movement
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    color = ImmersiveSurface,
                    shape = RoundedCornerShape(20.dp)
                )
                .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(20.dp))
                .padding(6.dp)
        ) {
            // North (dx:0, dy:-1)
            IconButton(
                onClick = onMoveNorth,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ImmersiveSurfaceVariant)
                    .testTag("btn_move_north")
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "North", tint = ImmersiveTeal)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // West (dx:-1, dy:0)
                IconButton(
                    onClick = onMoveWest,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_move_west")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "West", tint = ImmersiveTeal)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Center Action / Inspect / V.A.T.S.
                IconButton(
                    onClick = onActionVats,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ImmersiveTeal.copy(alpha = 0.2f))
                        .border(1.dp, ImmersiveTeal, CircleShape)
                        .testTag("btn_action_center")
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = "Inspect / VATS",
                        tint = ImmersiveTeal
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // East (dx:1, dy:0)
                IconButton(
                    onClick = onMoveEast,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ImmersiveSurfaceVariant)
                        .testTag("btn_move_east")
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "East", tint = ImmersiveTeal)
                }
            }

            // South (dx:0, dy:1)
            IconButton(
                onClick = onMoveSouth,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ImmersiveSurfaceVariant)
                    .testTag("btn_move_south")
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "South", tint = ImmersiveTeal)
            }
        }

        // Action Buttons: V.A.T.S. and WAIT TURN (Advance NPC State Machine)
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("btn_quick_action")
            ) {
                Text(
                    text = "V.A.T.S. [E]",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onWaitTurn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveSurfaceVariant,
                    contentColor = ImmersiveTeal
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("btn_wait_turn")
            ) {
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


