package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CombatLogEntry
import com.example.data.LogCategory
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
import com.example.ui.theme.ToxicRed

/**
 * Interactive, Animated Tactical Narrative Stream.
 * Replaces plain text log dumps with categorized animated event nodes, icons,
 * telemetry metric badges, and interactive drill-down inspection.
 */
@Composable
fun InteractiveLogStream(
    logs: List<CombatLogEntry>,
    onOpenInventory: () -> Unit = {},
    onOpenStory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCategoryFilter by remember { mutableStateOf<LogCategory?>(null) }
    var inspectLogEntry by remember { mutableStateOf<CombatLogEntry?>(null) }
    var isExpanded by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) logs
        else logs.filter { it.category == selectedCategoryFilter }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(ImmersiveSurface, RoundedCornerShape(14.dp))
            .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(14.dp))
            .padding(8.dp)
            .testTag("interactive_tactical_log_stream")
    ) {
        // Stream Header & Category Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing live status indicator
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PhosphorGreen.copy(alpha = pulseAlpha))
                )
                Text(
                    text = "TACTICAL TELEMETRY STREAM",
                    color = ImmersiveTeal,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${filteredLogs.size} EVENTS",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("btn_toggle_log_expand")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Expand/Collapse",
                        tint = ImmersiveTeal,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Category Filter Bar
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                label = "ALL [${logs.size}]",
                badge = "⚡",
                isSelected = selectedCategoryFilter == null,
                onClick = { selectedCategoryFilter = null }
            )
            LogCategory.values().forEach { cat ->
                val count = logs.count { it.category == cat }
                FilterChip(
                    label = "${cat.label} [$count]",
                    badge = cat.badge,
                    isSelected = selectedCategoryFilter == cat,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Event Stream Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isExpanded) 140.dp else 68.dp)
                .background(ImmersiveBackground, RoundedCornerShape(8.dp))
                .border(0.8.dp, ImmersiveSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO TELEMETRY SIGNALS RECORDED",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                items(filteredLogs, key = { "${it.timestamp}_${it.message.hashCode()}" }) { log ->
                    InteractiveLogItem(
                        entry = log,
                        onInspect = { inspectLogEntry = log }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }

        // Inline Interactive Telemetry Drill-down Modal/Drawer
        inspectLogEntry?.let { entry ->
            Spacer(modifier = Modifier.height(6.dp))
            LogInspectionCard(
                entry = entry,
                onDismiss = { inspectLogEntry = null },
                onOpenInventory = onOpenInventory,
                onOpenStory = onOpenStory
            )
        }
    }
}

/**
 * Filter Chip with Cybernetic Styling.
 */
@Composable
private fun FilterChip(
    label: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) ImmersiveTeal.copy(alpha = 0.25f) else ImmersiveBackground
    val border = if (isSelected) ImmersiveTeal else ImmersiveSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("filter_chip_${label.substringBefore(" ")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = badge, fontSize = 9.sp)
            Text(
                text = label,
                color = if (isSelected) ImmersiveTeal else ImmersiveTextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 9.sp
            )
        }
    }
}

/**
 * Interactive Log Item Card with icon, dynamic impact badges, and click-to-inspect trigger.
 */
@Composable
fun InteractiveLogItem(
    entry: CombatLogEntry,
    onInspect: () -> Unit
) {
    val (icon, iconTintBase, bgTint) = getCategoryVisuals(entry.category, entry.isCritical)
    val accent = when {
        entry.isHeal -> PhosphorGreen
        entry.isMiss -> ImmersiveTextMuted
        entry.isCritical -> ToxicRed
        else -> iconTintBase
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -20 }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onInspect)
                .border(
                    0.8.dp,
                    accent.copy(alpha = if (entry.isCritical || entry.isHeal || entry.isMiss) 0.85f else 0.4f),
                    RoundedCornerShape(6.dp)
                )
                .testTag("log_item_${entry.category.name.lowercase()}"),
            color = when {
                entry.isCritical -> ToxicRed.copy(alpha = 0.12f)
                entry.isHeal -> PhosphorGreen.copy(alpha = 0.12f)
                entry.isMiss -> ImmersiveSurfaceVariant.copy(alpha = 0.5f)
                else -> bgTint
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Icon Avatar Badge
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(iconTint.copy(alpha = 0.2f))
                            .border(0.6.dp, iconTint, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = entry.category.label,
                            tint = iconTint,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Message text
                    Text(
                        text = entry.message,
                        color = accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (entry.isCritical || entry.isHeal || entry.isMiss) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Impact metric badge if detected (e.g. [-15 HP], [+4% TOX])
                if (entry.impactValue != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (entry.isCritical || entry.impactValue.contains("-") || entry.impactValue.contains("TOX"))
                                    ToxicRed.copy(alpha = 0.2f)
                                else PhosphorGreen.copy(alpha = 0.2f)
                            )
                            .border(
                                0.6.dp,
                                if (entry.isCritical || entry.impactValue.contains("-") || entry.impactValue.contains("TOX"))
                                    ToxicRed
                                else PhosphorGreen,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = entry.impactValue,
                            color = if (entry.isCritical || entry.impactValue.contains("-") || entry.impactValue.contains("TOX"))
                                ToxicRed
                            else PhosphorGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                } else if (entry.isCritical) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ToxicRed.copy(alpha = 0.25f))
                            .border(0.6.dp, ToxicRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "CRIT",
                            color = ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                } else if (entry.isMiss) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ImmersiveSurfaceVariant.copy(alpha = 0.6f))
                            .border(0.6.dp, ImmersiveTextMuted, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "MISS",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                } else if (entry.isHeal) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PhosphorGreen.copy(alpha = 0.2f))
                            .border(0.6.dp, PhosphorGreen, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HEAL",
                            color = PhosphorGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detailed Telemetry Breakdown Card for inspected event.
 */
@Composable
fun LogInspectionCard(
    entry: CombatLogEntry,
    onDismiss: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenStory: () -> Unit
) {
    val (icon, iconTint, _) = getCategoryVisuals(entry.category, entry.isCritical)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ImmersiveSurface)
            .border(1.2.dp, iconTint, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    Text(
                        text = "EVENT: [${entry.category.label}]",
                        color = iconTint,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTextMuted, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                color = ImmersiveText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEVERITY: ${if (entry.isCritical) "CRITICAL BIO-HAZARD / HOSTILE" else "ROUTINE TELEMETRY"}",
                    color = if (entry.isCritical) ToxicRed else ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Context quick action button
                when (entry.category) {
                    LogCategory.HAZARD, LogCategory.LOOT -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenInventory()
                                }
                                .background(ImmersiveTeal.copy(alpha = 0.2f))
                                .border(0.8.dp, ImmersiveTeal, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("OPEN INVENTORY", color = ImmersiveTeal, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    LogCategory.NARRATIVE -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenStory()
                                }
                                .background(ImmersiveAccentOrange.copy(alpha = 0.2f))
                                .border(0.8.dp, ImmersiveAccentOrange, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("VIEW SCRIPT", color = ImmersiveAccentOrange, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * Returns icon, tint color, and background tint for a log category.
 */
fun getCategoryVisuals(category: LogCategory, isCritical: Boolean): Triple<ImageVector, Color, Color> {
    return when (category) {
        LogCategory.COMBAT -> Triple(
            if (isCritical) Icons.Default.Bolt else Icons.Default.FlashOn,
            if (isCritical) ToxicRed else AcidYellow,
            Color(0xFF1E1014)
        )
        LogCategory.HAZARD -> Triple(
            Icons.Default.Dangerous,
            ToxicRed,
            Color(0xFF220C10)
        )
        LogCategory.NPC_AI -> Triple(
            Icons.Default.Visibility,
            AmberTerminal,
            Color(0xFF1A170C)
        )
        LogCategory.LOOT -> Triple(
            Icons.Default.Inventory,
            ImmersiveTeal,
            Color(0xFF0C191C)
        )
        LogCategory.NARRATIVE -> Triple(
            Icons.Default.Description,
            ImmersiveAccentOrange,
            Color(0xFF1F140D)
        )
        LogCategory.SYSTEM -> Triple(
            Icons.Default.NearMe,
            PhosphorGreen,
            Color(0xFF0E1A14)
        )
    }
}
