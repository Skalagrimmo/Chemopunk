package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Enemy
import com.example.data.Player
import com.example.data.StatusEffectType
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Descriptor for a single radial action petal.
 */
data class RadialAction(
    val id: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badge: String? = null,
    val onExecute: () -> Unit
)

/**
 * Touch-friendly Radial Context Menu appearing on Long-Press.
 *
 * Provides instant access to combat strikes, status chem-stim injections,
 * emergency flares, med-gel cleanses, and tactical overwatch without UI clutter.
 */
@Composable
fun RadialQuickActionMenu(
    visible: Boolean,
    touchPosition: Offset,
    targetTile: Pair<Int, Int>?,
    targetedEnemy: Enemy?,
    player: Player,
    onDismiss: () -> Unit,
    onAttack: () -> Unit,
    onDeployFlare: () -> Unit,
    onUseAdrenaline: () -> Unit,
    onUseNanoRegen: () -> Unit,
    onUseShockGrenade: () -> Unit,
    onUseAcidFlask: () -> Unit,
    onUseNeurotoxin: () -> Unit,
    onWaitTurn: () -> Unit,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val density = LocalDensity.current
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val animScale = remember { Animatable(0.2f) }
    val animAlpha = remember { Animatable(0.0f) }

    // Build context-aware radial action list
    val actions = remember(targetedEnemy, player.hp, player.toxicity) {
        val list = mutableListOf<RadialAction>()

        // 1. Primary Combat Action (North - 12 o'clock)
        list.add(
            RadialAction(
                id = "strike",
                label = if (targetedEnemy != null) "STRIKE" else "ATTACK",
                subtitle = if (targetedEnemy != null) "${targetedEnemy.name} (Atk: ${player.attackPower})" else "Target Primary Enemy",
                icon = Icons.Default.Bolt,
                accentColor = ToxicRed,
                badge = if (targetedEnemy != null) "ENGAGED" else "READY",
                onExecute = onAttack
            )
        )

        // 2. Nano-Regen / Med-Gel (North-East - 1:30)
        list.add(
            RadialAction(
                id = "nano_regen",
                label = "MED-GEL",
                subtitle = "Nano-Regen (+7 HP/turn & Cleanse)",
                icon = Icons.Default.MedicalServices,
                accentColor = PhosphorGreen,
                badge = "${player.hp}/${player.maxHp} HP",
                onExecute = onUseNanoRegen
            )
        )

        // 3. Combat Adrenaline (East - 3 o'clock)
        list.add(
            RadialAction(
                id = "adrenaline",
                label = "ADRENALINE",
                subtitle = "Combat Stim (+8 ATK boost)",
                icon = Icons.Default.ElectricBolt,
                accentColor = AcidYellow,
                badge = "+8 ATK",
                onExecute = onUseAdrenaline
            )
        )

        // 4. Acid Flask / Armor Corrosion (South-East - 4:30)
        list.add(
            RadialAction(
                id = "acid_flask",
                label = "ACID FLASK",
                subtitle = "Corrode Enemy Armor (-6 DEF)",
                icon = Icons.Default.Science,
                accentColor = ImmersiveAccentOrange,
                badge = "-6 DEF",
                onExecute = onUseAcidFlask
            )
        )

        // 5. Emergency Flare (South - 6 o'clock)
        list.add(
            RadialAction(
                id = "flare",
                label = "FLARE",
                subtitle = "Deploy Phosphor Light Flare",
                icon = Icons.Default.LocalFireDepartment,
                accentColor = ImmersiveAccentOrange,
                badge = "LIGHT",
                onExecute = onDeployFlare
            )
        )

        // 6. Neurotoxin Dart (South-West - 7:30)
        list.add(
            RadialAction(
                id = "neurotoxin",
                label = "BIO-DART",
                subtitle = "Neurotoxin (6 Poison DMG/turn)",
                icon = Icons.Default.Warning,
                accentColor = PhosphorGreen,
                badge = "POISON",
                onExecute = onUseNeurotoxin
            )
        )

        // 7. EMP Shock Grenade (West - 9 o'clock)
        list.add(
            RadialAction(
                id = "emp_shock",
                label = "EMP SHOCK",
                subtitle = "Stun Target (Skip 1 Turn)",
                icon = Icons.Default.FlashOn,
                accentColor = ImmersiveTeal,
                badge = "STUN",
                onExecute = onUseShockGrenade
            )
        )

        // 8. Overwatch / Wait Turn (North-West - 10:30)
        list.add(
            RadialAction(
                id = "wait_turn",
                label = "STAND GROUND",
                subtitle = "Hold position & scan sensors",
                icon = Icons.Default.Shield,
                accentColor = AmberTerminal,
                badge = "WAIT",
                onExecute = onWaitTurn
            )
        )

        list
    }

    LaunchedEffect(visible) {
        if (visible) {
            animScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            animAlpha.animateTo(1.0f, tween(180))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("radial_menu_root")
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Clamp radial center so the entire wheel (radius ~120dp) stays inside screen bounds
        val wheelRadiusPx = with(density) { 110.dp.toPx() }
        val marginPx = with(density) { 60.dp.toPx() }

        val clampedCenterX = touchPosition.x.coerceIn(wheelRadiusPx + marginPx * 0.5f, screenWidthPx - wheelRadiusPx - marginPx * 0.5f)
        val clampedCenterY = touchPosition.y.coerceIn(wheelRadiusPx + marginPx * 0.5f, screenHeightPx - wheelRadiusPx - marginPx * 0.5f)

        // 1. Full-screen touch dismissal backdrop scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f * animAlpha.value))
                .pointerInput(Unit) {
                    detectTapGestures {
                        onDismiss()
                    }
                }
        )

        // 2. Drag & Touch gesture dispatcher for instant swipe-to-select
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(actions) {
                    detectDragGestures(
                        onDragEnd = {
                            if (selectedIndex in actions.indices) {
                                val action = actions[selectedIndex]
                                action.onExecute()
                                onDismiss()
                            }
                        },
                        onDragCancel = {
                            selectedIndex = -1
                        },
                        onDrag = { change, _ ->
                            val currentPos = change.position
                            val dx = currentPos.x - clampedCenterX
                            val dy = currentPos.y - clampedCenterY
                            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                            if (dist in with(density) { 35.dp.toPx() }..with(density) { 160.dp.toPx() }) {
                                var angle = atan2(dy.toDouble(), dx.toDouble()) + (PI / 2.0)
                                if (angle < 0) angle += 2.0 * PI
                                val sectorAngle = (2.0 * PI) / actions.size
                                val hoveredIdx = ((angle + (sectorAngle * 0.5)) / sectorAngle).toInt() % actions.size
                                selectedIndex = hoveredIdx
                            } else if (dist < with(density) { 30.dp.toPx() }) {
                                selectedIndex = -1
                            }
                        }
                    )
                }
        )

        // 3. Radial Wheel Dial Layout
        val centerOffsetDp = with(density) {
            IntOffset(
                x = (clampedCenterX - 140.dp.toPx()).roundToInt(),
                y = (clampedCenterY - 140.dp.toPx()).roundToInt()
            )
        }

        Box(
            modifier = Modifier
                .offset { centerOffsetDp }
                .size(280.dp)
                .scale(animScale.value)
                .alpha(animAlpha.value)
        ) {
            // Background Circular Reticle & Energy Arcs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width * 0.40f

                // Outer guide circle
                drawCircle(
                    color = ImmersiveTeal.copy(alpha = 0.25f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )

                // Inner core ring
                drawCircle(
                    color = ImmersiveSurfaceVariant.copy(alpha = 0.45f),
                    radius = size.width * 0.18f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // Radial connecting spokes
                val count = actions.size
                for (i in 0 until count) {
                    val angle = (2 * PI * i / count) - (PI / 2.0)
                    val spokeStartX = center.x + (cos(angle) * (size.width * 0.20f)).toFloat()
                    val spokeStartY = center.y + (sin(angle) * (size.width * 0.20f)).toFloat()
                    val spokeEndX = center.x + (cos(angle) * (radius - 16f)).toFloat()
                    val spokeEndY = center.y + (sin(angle) * (radius - 16f)).toFloat()

                    val spokeColor = if (i == selectedIndex) {
                        actions[i].accentColor.copy(alpha = 0.8f)
                    } else {
                        ImmersiveTeal.copy(alpha = 0.15f)
                    }

                    drawLine(
                        color = spokeColor,
                        start = Offset(spokeStartX, spokeStartY),
                        end = Offset(spokeEndX, spokeEndY),
                        strokeWidth = if (i == selectedIndex) 3f else 1.5f
                    )
                }
            }

            // Radial Action Petals
            val actionCount = actions.size
            val radialDistanceDp = 100.dp

            actions.forEachIndexed { index, action ->
                val angle = (2 * PI * index / actionCount) - (PI / 2.0)
                val isHovered = (index == selectedIndex)
                val petalScale by animateFloatAsState(
                    targetValue = if (isHovered) 1.22f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "petal_scale_$index"
                )

                val petalOffsetX = (cos(angle) * with(density) { radialDistanceDp.toPx() }).toFloat()
                val petalOffsetY = (sin(angle) * with(density) { radialDistanceDp.toPx() }).toFloat()

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            IntOffset(
                                x = petalOffsetX.roundToInt(),
                                y = petalOffsetY.roundToInt()
                            )
                        }
                        .scale(petalScale)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    if (isHovered) action.accentColor.copy(alpha = 0.45f) else ImmersiveSurface.copy(alpha = 0.92f),
                                    if (isHovered) action.accentColor.copy(alpha = 0.15f) else ImmersiveBackground.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .border(
                            width = if (isHovered) 2.5.dp else 1.2.dp,
                            color = if (isHovered) action.accentColor else action.accentColor.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            action.onExecute()
                            onDismiss()
                        }
                        .testTag("radial_action_${action.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = if (isHovered) Color.White else action.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Central Core Hub (Context & Telemetry Display)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(ImmersiveBackground.copy(alpha = 0.96f))
                    .border(1.5.dp, ImmersiveTeal.copy(alpha = 0.7f), CircleShape)
                    .clickable { onDismiss() }
                    .testTag("radial_hub_center"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    if (selectedIndex in actions.indices) {
                        val active = actions[selectedIndex]
                        Text(
                            text = active.label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = active.accentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        if (active.badge != null) {
                            Text(
                                text = active.badge,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                color = ImmersiveText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = if (targetedEnemy != null) targetedEnemy.name.take(7).uppercase() else "TACTICAL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (targetedEnemy != null) ToxicRed else ImmersiveTeal,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        if (targetTile != null) {
                            Text(
                                text = "[X:${targetTile.first}, Y:${targetTile.second}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = ImmersiveTextMuted
                            )
                        } else {
                            Text(
                                text = "WHEEL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = ImmersiveTextMuted
                            )
                        }
                    }
                }
            }
        }

        // 4. Bottom Contextual Action Card / Detail Banner
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            val currentHovered = actions.getOrNull(selectedIndex)

            Surface(
                color = ImmersiveSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    currentHovered?.accentColor ?: ImmersiveSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background((currentHovered?.accentColor ?: ImmersiveTeal).copy(alpha = 0.2f))
                                .border(1.dp, (currentHovered?.accentColor ?: ImmersiveTeal).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentHovered?.icon ?: Icons.Default.Bolt,
                                contentDescription = null,
                                tint = currentHovered?.accentColor ?: ImmersiveTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = currentHovered?.label ?: "SELECT TACTICAL ACTION",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = currentHovered?.accentColor ?: ImmersiveText
                            )
                            Text(
                                text = currentHovered?.subtitle ?: "Tap or drag thumb over any radial petal to execute.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ImmersiveTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Quick dismiss icon
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Menu",
                            tint = ImmersiveTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
