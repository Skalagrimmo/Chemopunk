package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Choice
import com.example.data.Enemy
import com.example.data.Item
import com.example.data.StoryNode
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
import com.example.ui.theme.TerminalCardBackground
import com.example.ui.theme.TextGreen
import com.example.ui.theme.ToxicRed

@Composable
fun StoryModal(
    node: StoryNode,
    onChoiceSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.95f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.5.dp, ImmersiveTeal, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "terminal://story_node/${node.id}",
                        color = ImmersiveTeal,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = onClose, modifier = Modifier.testTag("btn_close_story")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTeal)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = node.title,
                    color = ImmersiveText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = node.content,
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "DECISION OPTIONS:",
                    color = ImmersiveAccentOrange,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(node.choices) { choice ->
                        Button(
                            onClick = { onChoiceSelected(choice.targetNodeId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, ImmersiveTeal, RoundedCornerShape(12.dp))
                                .testTag("btn_choice_${choice.targetNodeId}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ImmersiveTeal.copy(alpha = 0.15f),
                                contentColor = ImmersiveTeal
                            )
                        ) {
                            Text(
                                text = "> ${choice.text}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryModal(
    roomInventory: List<com.example.data.room.InventoryItemEntity>,
    gearStats: com.example.data.room.GearCombatStats,
    playerCredits: Int,
    maxCarryWeight: Float,
    onEquipItem: (String) -> Unit,
    onUnequipItem: (String) -> Unit,
    onUseItem: (String) -> Unit,
    onRepairItem: (String) -> Unit,
    onScrapItem: (String) -> Unit,
    onCraftSampleItem: (name: String, type: String, dmg: Int, def: Int, heal: Int, tox: Int) -> Unit,
    onClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showCraftDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(roomInventory, selectedCategory) {
        when (selectedCategory) {
            "WEAPON" -> roomInventory.filter { it.type == "WEAPON" || it.equipSlot == "WEAPON" }
            "ARMOR" -> roomInventory.filter { it.type == "ARMOR" || it.equipSlot == "ARMOR" }
            "MEDICAL" -> roomInventory.filter { it.type == "CONSUMABLE" }
            "CYBERWARE" -> roomInventory.filter { it.type == "NEURAL_CHIP" || it.equipSlot == "NEURAL_CHIP" || it.type == "KEY_ITEM" }
            else -> roomInventory
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.96f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.92f)
                .border(1.5.dp, ImmersiveTeal, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CYBERNETIC RIG & ROOM INVENTORY",
                            color = ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "ROOM DB PERSISTED • SECURE LOCAL STORAGE",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("btn_close_inventory")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTeal)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Equipment Paperdoll Overview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "EQUIPPED HARDWARE SLOTS",
                            color = ImmersiveAccentOrange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Weapon Slot
                            EquipmentSlotCard(
                                title = "WEAPON",
                                item = gearStats.activeWeapon,
                                modifier = Modifier.weight(1f),
                                onUnequip = { gearStats.activeWeapon?.let { onUnequipItem(it.itemId) } }
                            )

                            // Armor Slot
                            EquipmentSlotCard(
                                title = "EXOSUIT",
                                item = gearStats.activeArmor,
                                modifier = Modifier.weight(1f),
                                onUnequip = { gearStats.activeArmor?.let { onUnequipItem(it.itemId) } }
                            )

                            // Cyber-Chip Slot
                            EquipmentSlotCard(
                                title = "CYBER-CHIP",
                                item = gearStats.activeChip,
                                modifier = Modifier.weight(1f),
                                onUnequip = { gearStats.activeChip?.let { onUnequipItem(it.itemId) } }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats & Weight summary bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DMG: +${gearStats.totalWeaponDamage + (gearStats.activeChip?.damage ?: 0)} | DEF: +${gearStats.totalArmorDefense + (gearStats.activeChip?.defense ?: 0)}",
                                color = AcidYellow,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "WEIGHT: ${String.format("%.1f", gearStats.totalWeightKg)} / ${maxCarryWeight} kg",
                                color = if (gearStats.totalWeightKg > maxCarryWeight) ToxicRed else PhosphorGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "CREDITS: $playerCredits CR",
                                color = ImmersiveAccentOrange,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Tabs & Craft Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "WEAPON", "ARMOR", "MEDICAL", "CYBERWARE").forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Button(
                                onClick = { selectedCategory = cat },
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("tab_inv_$cat"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) ImmersiveTeal else ImmersiveSurfaceVariant,
                                    contentColor = if (isSelected) ImmersiveBackground else ImmersiveTextMuted
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showCraftDialog = true },
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_craft_gear"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImmersiveAccentOrange,
                            contentColor = ImmersiveBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "+ CRAFT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of Room Items
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO ITEMS FOUND IN THIS CATEGORY",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredItems, key = { it.itemId }) { item ->
                            RoomItemCard(
                                item = item,
                                onEquip = { onEquipItem(item.itemId) },
                                onUnequip = { onUnequipItem(item.itemId) },
                                onUse = { onUseItem(item.itemId) },
                                onRepair = { onRepairItem(item.itemId) },
                                onScrap = { onScrapItem(item.itemId) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCraftDialog) {
        CraftItemDialog(
            onCraft = { name, type, dmg, def, heal, tox ->
                onCraftSampleItem(name, type, dmg, def, heal, tox)
                showCraftDialog = false
            },
            onDismiss = { showCraftDialog = false }
        )
    }
}

@Composable
private fun EquipmentSlotCard(
    title: String,
    item: com.example.data.room.InventoryItemEntity?,
    modifier: Modifier = Modifier,
    onUnequip: () -> Unit
) {
    Card(
        modifier = modifier
            .border(
                1.dp,
                if (item != null) ImmersiveTeal.copy(alpha = 0.6f) else ImmersiveSurfaceVariant,
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (item != null) ImmersiveTeal.copy(alpha = 0.1f) else ImmersiveSurfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = ImmersiveAccentOrange,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item?.name ?: "[EMPTY]",
                color = if (item != null) ImmersiveText else ImmersiveTextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                maxLines = 1
            )
            if (item != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onUnequip,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ToxicRed.copy(alpha = 0.8f),
                        contentColor = ImmersiveText
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .height(20.dp)
                        .testTag("btn_unequip_slot_$title"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("UNEQUIP", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun RoomItemCard(
    item: com.example.data.room.InventoryItemEntity,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
    onUse: () -> Unit,
    onRepair: () -> Unit,
    onScrap: () -> Unit
) {
    val rarityColor = when (item.rarity) {
        "LEGENDARY" -> androidx.compose.ui.graphics.Color(0xFFFFD700)
        "EPIC" -> androidx.compose.ui.graphics.Color(0xFFA855F7)
        "RARE" -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
        "UNCOMMON" -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        else -> androidx.compose.ui.graphics.Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                1.dp,
                if (item.isEquipped) ImmersiveTeal else ImmersiveSurfaceVariant,
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        color = ImmersiveText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (item.quantity > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "x${item.quantity}",
                            color = ImmersiveAccentOrange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Rarity Badge
                    Box(
                        modifier = Modifier
                            .background(rarityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(0.8.dp, rarityColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.rarity,
                            color = rarityColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }

                    if (item.isEquipped) {
                        Box(
                            modifier = Modifier
                                .background(ImmersiveTeal.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(0.8.dp, ImmersiveTeal, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EQUIPPED",
                                color = ImmersiveTeal,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Description
            Text(
                text = item.description,
                color = ImmersiveTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata Row (Stats, Weight, Durability, Resale)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stats preview
                val statsText = buildString {
                    if (item.damage > 0) append("+${item.damage} DMG  ")
                    if (item.defense > 0) append("+${item.defense} DEF  ")
                    if (item.healHp > 0) append("+${item.healHp} HP  ")
                    if (item.reduceToxicity > 0) append("-${item.reduceToxicity}% TOX  ")
                    if (item.criticalBonus > 0f) append("+${(item.criticalBonus * 100).toInt()}% CRIT")
                }

                Text(
                    text = statsText.ifEmpty { item.category },
                    color = PhosphorGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )

                Text(
                    text = "${item.weightKg}kg | DUR: ${item.durability}% | ${item.creditValue} CR",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.type == "CONSUMABLE") {
                    Button(
                        onClick = onUse,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImmersiveTeal,
                            contentColor = ImmersiveBackground
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .testTag("btn_use_${item.itemId}"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("INJECT / CONSUME", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                } else if (item.type == "WEAPON" || item.type == "ARMOR" || item.type == "NEURAL_CHIP") {
                    if (item.isEquipped) {
                        Button(
                            onClick = onUnequip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ToxicRed.copy(alpha = 0.8f),
                                contentColor = ImmersiveText
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .testTag("btn_unequip_${item.itemId}"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("UNEQUIP", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onEquip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ImmersiveTeal,
                                contentColor = ImmersiveBackground
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .testTag("btn_equip_${item.itemId}"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("EQUIP TO RIG", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (item.durability < item.maxDurability) {
                        Button(
                            onClick = onRepair,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AcidYellow,
                                contentColor = ImmersiveBackground
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("btn_repair_${item.itemId}"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("REPAIR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Scrap item button
                Button(
                    onClick = onScrap,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveSurfaceVariant,
                        contentColor = ImmersiveTextMuted
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("btn_scrap_${item.itemId}"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text("SCRAP", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun CraftItemDialog(
    onCraft: (name: String, type: String, dmg: Int, def: Int, heal: Int, tox: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var itemName by remember { mutableStateOf("Overcharged Plasma Rail") }
    var itemType by remember { mutableStateOf("WEAPON") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.92f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ImmersiveAccentOrange, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FIELD FABRICATOR (ROOM DB)",
                    color = ImmersiveAccentOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name", color = ImmersiveTextMuted, fontSize = 10.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_craft_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ImmersiveText,
                        unfocusedTextColor = ImmersiveTextMuted,
                        focusedBorderColor = ImmersiveTeal,
                        unfocusedBorderColor = ImmersiveSurfaceVariant
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WEAPON", "ARMOR", "CONSUMABLE", "NEURAL_CHIP").forEach { type ->
                        val isSelected = itemType == type
                        Button(
                            onClick = { itemType = type },
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("btn_craft_type_$type"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) ImmersiveAccentOrange else ImmersiveSurfaceVariant,
                                contentColor = if (isSelected) ImmersiveBackground else ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(type, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImmersiveSurfaceVariant,
                            contentColor = ImmersiveText
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            when (itemType) {
                                "WEAPON" -> onCraft(itemName, "WEAPON", 32, 0, 0, 0)
                                "ARMOR" -> onCraft(itemName, "ARMOR", 0, 18, 0, 0)
                                "CONSUMABLE" -> onCraft(itemName, "CONSUMABLE", 0, 0, 50, 25)
                                "NEURAL_CHIP" -> onCraft(itemName, "NEURAL_CHIP", 12, 8, 0, 0)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_confirm_craft"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImmersiveAccentOrange,
                            contentColor = ImmersiveBackground
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("FABRICATE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CombatModal(
    enemy: Enemy,
    playerHp: Int,
    playerMaxHp: Int,
    playerToxicity: Int,
    onAttack: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.95f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, ToxicRed, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "!!! COMBAT ENCOUNTER DETECTED !!!",
                    color = ToxicRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "[ ${enemy.asciiGlyph} ]",
                    color = ToxicRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                )

                Text(
                    text = enemy.name,
                    color = ImmersiveText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ENEMY HP: ${enemy.hp} / ${enemy.maxHp}",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val stateColor = when (enemy.state) {
                    com.example.data.NpcState.AGGRESSIVE -> ToxicRed
                    com.example.data.NpcState.FLEE -> AcidYellow
                    com.example.data.NpcState.PATROL -> ImmersiveTeal
                }

                val stateText = when (enemy.state) {
                    com.example.data.NpcState.AGGRESSIVE -> "⚔ AI STATE: AGGRESSIVE (PURSUE / ATTACK)"
                    com.example.data.NpcState.FLEE -> "💨 AI STATE: FLEEING (PANICKED MORALE)"
                    com.example.data.NpcState.PATROL -> "👁 AI STATE: PATROL (SURVEILLANCE ROUTE)"
                }

                Box(
                    modifier = Modifier
                        .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, stateColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stateText,
                        color = stateColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onAttack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ToxicRed,
                            contentColor = ImmersiveText
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_combat_attack")
                    ) {
                        Text(
                            text = "STRIKE WITH WEAPON",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownEditorModal(
    initialMarkdownText: String,
    onSaveAndParse: (String) -> Unit,
    onClose: () -> Unit
) {
    var textState by remember { mutableStateOf(initialMarkdownText) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.95f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, ImmersiveTeal, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE MARKDOWN SCRIPT EDITOR (assets/chemopank_world.md)",
                        color = ImmersiveTeal,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = onClose, modifier = Modifier.testTag("btn_close_editor")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTeal)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("input_markdown_editor"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ImmersiveText,
                        unfocusedTextColor = ImmersiveTextMuted,
                        focusedBorderColor = ImmersiveTeal,
                        unfocusedBorderColor = ImmersiveSurfaceVariant,
                        focusedContainerColor = ImmersiveBackground
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSaveAndParse(textState)
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveTeal,
                        contentColor = ImmersiveBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_reload_markdown")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APPLY & RELOAD MARKDOWN SCRIPT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

