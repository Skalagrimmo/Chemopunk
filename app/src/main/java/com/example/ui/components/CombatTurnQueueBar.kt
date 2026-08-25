package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CombatQueueEntity
import com.example.data.CombatantType
import com.example.data.TurnCombatQueueState
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
 * Tactical Turn-Based Combat Queue Bar.
 * Visualizes the initiative turn order, active combatant, HP status, and upcoming entities.
 */
@Composable
fun CombatTurnQueueBar(
    queueState: TurnCombatQueueState,
    onSelectCombatant: (CombatQueueEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (queueState.combatants.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "queue_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .border(
                1.dp,
                if (queueState.isCombatActive) ToxicRed.copy(alpha = 0.6f) else ImmersiveTeal.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .testTag("combat_turn_queue_bar"),
        colors = CardDefaults.cardColors(containerColor = ImmersiveSurface.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Header Row: Round Badge, Queue Status & Active Combatant Callout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Round and Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (queueState.isCombatActive) ToxicRed.copy(alpha = 0.2f) else ImmersiveTeal.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                0.8.dp,
                                if (queueState.isCombatActive) ToxicRed else ImmersiveTeal,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (queueState.isCombatActive) "⚔ RND ${queueState.roundNumber}" else "⚡ TACTICAL RND ${queueState.roundNumber}",
                            color = if (queueState.isCombatActive) ToxicRed else ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }

                    Text(
                        text = "TURN QUEUE",
                        color = ImmersiveTextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                // Active Combatant Name Chip
                val active = queueState.activeCombatant
                if (active != null) {
                    val activeColor = if (active.isPlayer) ImmersiveTeal else ToxicRed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag("active_combatant_chip")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .scale(pulseScale)
                                .background(activeColor, CircleShape)
                        )
                        Text(
                            text = if (active.isPlayer) "ACTIVE: YOU [ACT]" else "ACTIVE: ${active.name.take(14).uppercase()}",
                            color = activeColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontal Initiative Turn Timeline Carousel
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                queueState.combatants.forEachIndexed { index, combatant ->
                    val isCurrent = combatant.isCurrentTurn || (queueState.activeCombatant?.id == combatant.id)
                    val isNext = queueState.nextCombatant?.id == combatant.id && !isCurrent

                    CombatantQueueCard(
                        combatant = combatant,
                        isCurrentTurn = isCurrent,
                        isNextInQueue = isNext,
                        orderIndex = index + 1,
                        pulseScale = if (isCurrent) pulseScale else 1.0f,
                        onClick = { onSelectCombatant(combatant) }
                    )

                    // Right Arrow Divider between cards
                    if (index < queueState.combatants.size - 1) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next turn",
                            tint = if (isCurrent) ImmersiveTeal else ImmersiveTextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Combatant Card in the Initiative Queue.
 */
@Composable
private fun CombatantQueueCard(
    combatant: CombatQueueEntity,
    isCurrentTurn: Boolean,
    isNextInQueue: Boolean,
    orderIndex: Int,
    pulseScale: Float,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrentTurn && combatant.isPlayer -> ImmersiveTeal
            isCurrentTurn -> ToxicRed
            isNextInQueue -> AcidYellow.copy(alpha = 0.8f)
            combatant.isPlayer -> ImmersiveTeal.copy(alpha = 0.3f)
            else -> ImmersiveSurfaceVariant
        },
        label = "border_color"
    )

    val bgColor = when {
        isCurrentTurn && combatant.isPlayer -> ImmersiveTeal.copy(alpha = 0.18f)
        isCurrentTurn -> ToxicRed.copy(alpha = 0.18f)
        isNextInQueue -> AcidYellow.copy(alpha = 0.08f)
        else -> ImmersiveBackground.copy(alpha = 0.8f)
    }

    Box(
        modifier = Modifier
            .width(96.dp)
            .scale(if (isCurrentTurn) pulseScale.coerceIn(1.0f, 1.03f) else 1.0f)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(if (isCurrentTurn) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(5.dp)
            .testTag("queue_card_${combatant.id}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Row: Initiative Order Badge & Turn Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$orderIndex",
                    color = if (isCurrentTurn) ImmersiveText else ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )

                if (isCurrentTurn) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (combatant.isPlayer) ImmersiveTeal else ToxicRed,
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "TURN",
                            color = ImmersiveBackground,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.5.sp
                        )
                    }
                } else if (isNextInQueue) {
                    Text(
                        text = "NEXT",
                        color = AcidYellow,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.5.sp
                    )
                } else {
                    Text(
                        text = "SPD ${combatant.initiative}",
                        color = ImmersiveTextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Glyph & Name Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "[${combatant.glyph}]",
                    color = if (combatant.isPlayer) PhosphorGreen else ToxicRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = combatant.name.take(7),
                    color = if (isCurrentTurn) ImmersiveText else ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // HP Mini-Bar
            val hpColor = when {
                combatant.hpRatio > 0.6f -> PhosphorGreen
                combatant.hpRatio > 0.3f -> AcidYellow
                else -> ToxicRed
            }

            LinearProgressIndicator(
                progress = { combatant.hpRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = hpColor,
                trackColor = ImmersiveSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Action Points Pips & HP Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${combatant.hp}HP",
                    color = hpColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp
                )

                // AP Pips
                Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    repeat(combatant.maxActionPoints) { i ->
                        val filled = i < combatant.actionPoints
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .background(
                                    if (filled) (if (combatant.isPlayer) ImmersiveTeal else ToxicRed) else ImmersiveSurfaceVariant,
                                    CircleShape
                                )
                        )
                    }
                }
            }

            // Status Effects Badges Row
            if (combatant.statusEffects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    combatant.statusEffects.take(3).forEach { effect ->
                        val effectColor = Color(effect.type.colorHex)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .background(effectColor.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                .border(0.5.dp, effectColor, RoundedCornerShape(2.dp))
                                .padding(horizontal = 2.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = "${effect.iconGlyph}${effect.durationTurns}",
                                color = effectColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 6.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
