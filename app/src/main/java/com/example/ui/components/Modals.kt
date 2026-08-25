package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.Quest
import com.example.data.QuestStatus
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
    onCraftRecipe: (com.example.data.room.CraftingRecipe) -> Unit,
    onUpgradeItem: (String) -> Unit,
    onCraftSampleItem: (name: String, type: String, dmg: Int, def: Int, heal: Int, tox: Int) -> Unit,
    onClose: () -> Unit
) {
    var activeSubView by remember { mutableStateOf("GEAR") } // GEAR, CRAFT, UPGRADE
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedCraftCategory by remember { mutableStateOf<com.example.data.room.CraftingCategory?>(null) }
    var showCustomCraftDialog by remember { mutableStateOf(false) }

    // Material quantities extracted from Room DB
    val scrapCount = remember(roomInventory) {
        roomInventory.firstOrNull { it.itemId == com.example.data.room.CraftingMaterials.SCRAP_METAL }?.quantity ?: 0
    }
    val chemCount = remember(roomInventory) {
        roomInventory.firstOrNull { it.itemId == com.example.data.room.CraftingMaterials.CHEM_REAGENT }?.quantity ?: 0
    }
    val biogelCount = remember(roomInventory) {
        roomInventory.firstOrNull { it.itemId == com.example.data.room.CraftingMaterials.BIOGEL_VIAL }?.quantity ?: 0
    }
    val circuitCount = remember(roomInventory) {
        roomInventory.firstOrNull { it.itemId == com.example.data.room.CraftingMaterials.MICRO_CIRCUIT }?.quantity ?: 0
    }

    val filteredItems = remember(roomInventory, selectedCategory) {
        when (selectedCategory) {
            "WEAPON" -> roomInventory.filter { it.type == "WEAPON" || it.equipSlot == "WEAPON" }
            "ARMOR" -> roomInventory.filter { it.type == "ARMOR" || it.equipSlot == "ARMOR" }
            "MEDICAL" -> roomInventory.filter { it.type == "CONSUMABLE" }
            "MATERIALS" -> roomInventory.filter { it.type == "MATERIAL" || it.itemId.startsWith("mat_") }
            "CYBERWARE" -> roomInventory.filter { it.type == "NEURAL_CHIP" || it.equipSlot == "NEURAL_CHIP" || it.type == "KEY_ITEM" }
            else -> roomInventory
        }
    }

    val filteredRecipes = remember(selectedCraftCategory) {
        if (selectedCraftCategory == null) {
            com.example.data.room.WastelandRecipes.ALL_RECIPES
        } else {
            com.example.data.room.WastelandRecipes.ALL_RECIPES.filter { it.category == selectedCraftCategory }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.96f))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.94f)
                .border(1.5.dp, ImmersiveTeal, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CYBERNETIC RIG & FIELD WORKBENCH",
                            color = ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "ROOM DB PERSISTED • CHEMICAL & SCRAP FABRICATION",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("btn_close_inventory")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTeal)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Sub-Mode Selector (GEAR, CRAFT, UPGRADE)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        Triple("GEAR", "🎒 RIG & GEAR", "tab_mode_gear"),
                        Triple("CRAFT", "⚙️ FABRICATOR", "tab_mode_craft"),
                        Triple("UPGRADE", "⚡ OVERCLOCK BAY", "tab_mode_upgrade")
                    )

                    tabs.forEach { (mode, label, tag) ->
                        val isSelected = activeSubView == mode
                        Button(
                            onClick = { activeSubView = mode },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag(tag),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) ImmersiveAccentOrange else ImmersiveSurfaceVariant,
                                contentColor = if (isSelected) ImmersiveBackground else ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Persistent Resource & Materials Counter Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔩 Scrap: $scrapCount",
                            color = PhosphorGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "🧪 Chems: $chemCount",
                            color = AcidYellow,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "🧬 Bio-Gel: $biogelCount",
                            color = ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "💽 Circuits: $circuitCount",
                            color = androidx.compose.ui.graphics.Color(0xFFA855F7),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "🪙 $playerCredits CR",
                            color = ImmersiveAccentOrange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Active View Body
                when (activeSubView) {
                    "GEAR" -> {
                        GearInventoryView(
                            filteredItems = filteredItems,
                            gearStats = gearStats,
                            maxCarryWeight = maxCarryWeight,
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            onEquipItem = onEquipItem,
                            onUnequipItem = onUnequipItem,
                            onUseItem = onUseItem,
                            onRepairItem = onRepairItem,
                            onScrapItem = onScrapItem,
                            onOpenCraftTab = { activeSubView = "CRAFT" }
                        )
                    }
                    "CRAFT" -> {
                        CraftingWorkbenchView(
                            recipes = filteredRecipes,
                            scrapCount = scrapCount,
                            chemCount = chemCount,
                            biogelCount = biogelCount,
                            circuitCount = circuitCount,
                            selectedCategory = selectedCraftCategory,
                            onSelectCategory = { selectedCraftCategory = it },
                            onCraftRecipe = onCraftRecipe,
                            onOpenCustomCraft = { showCustomCraftDialog = true }
                        )
                    }
                    "UPGRADE" -> {
                        EquipmentUpgradeBayView(
                            roomInventory = roomInventory,
                            scrapCount = scrapCount,
                            chemCount = chemCount,
                            playerCredits = playerCredits,
                            onUpgradeItem = onUpgradeItem
                        )
                    }
                }
            }
        }
    }

    if (showCustomCraftDialog) {
        CraftItemDialog(
            scrapAvailable = scrapCount,
            chemAvailable = chemCount,
            onCraft = { name, type, dmg, def, heal, tox ->
                onCraftSampleItem(name, type, dmg, def, heal, tox)
                showCustomCraftDialog = false
            },
            onDismiss = { showCustomCraftDialog = false }
        )
    }
}

@Composable
private fun GearInventoryView(
    filteredItems: List<com.example.data.room.InventoryItemEntity>,
    gearStats: com.example.data.room.GearCombatStats,
    maxCarryWeight: Float,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onEquipItem: (String) -> Unit,
    onUnequipItem: (String) -> Unit,
    onUseItem: (String) -> Unit,
    onRepairItem: (String) -> Unit,
    onScrapItem: (String) -> Unit,
    onOpenCraftTab: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Equipment Paperdoll Overview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "EQUIPPED HARDWARE SLOTS",
                    color = ImmersiveAccentOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EquipmentSlotCard(
                        title = "WEAPON",
                        item = gearStats.activeWeapon,
                        modifier = Modifier.weight(1f),
                        onUnequip = { gearStats.activeWeapon?.let { onUnequipItem(it.itemId) } }
                    )
                    EquipmentSlotCard(
                        title = "EXOSUIT",
                        item = gearStats.activeArmor,
                        modifier = Modifier.weight(1f),
                        onUnequip = { gearStats.activeArmor?.let { onUnequipItem(it.itemId) } }
                    )
                    EquipmentSlotCard(
                        title = "CYBER-CHIP",
                        item = gearStats.activeChip,
                        modifier = Modifier.weight(1f),
                        onUnequip = { gearStats.activeChip?.let { onUnequipItem(it.itemId) } }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                        fontSize = 10.sp
                    )
                    Text(
                        text = "WEIGHT: ${String.format("%.1f", gearStats.totalWeightKg)} / ${maxCarryWeight} kg",
                        color = if (gearStats.totalWeightKg > maxCarryWeight) ToxicRed else PhosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Category Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("ALL", "WEAPON", "ARMOR", "MEDICAL", "MATERIALS", "CYBERWARE").forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Button(
                        onClick = { onSelectCategory(cat) },
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("tab_inv_$cat"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ImmersiveTeal else ImmersiveSurfaceVariant,
                            contentColor = if (isSelected) ImmersiveBackground else ImmersiveTextMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = cat,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Button(
                onClick = onOpenCraftTab,
                modifier = Modifier
                    .height(28.dp)
                    .testTag("btn_craft_gear"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveAccentOrange,
                    contentColor = ImmersiveBackground
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "⚙️ CRAFT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Items List
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
                    fontSize = 11.sp
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

@Composable
private fun CraftingWorkbenchView(
    recipes: List<com.example.data.room.CraftingRecipe>,
    scrapCount: Int,
    chemCount: Int,
    biogelCount: Int,
    circuitCount: Int,
    selectedCategory: com.example.data.room.CraftingCategory?,
    onSelectCategory: (com.example.data.room.CraftingCategory?) -> Unit,
    onCraftRecipe: (com.example.data.room.CraftingRecipe) -> Unit,
    onOpenCustomCraft: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Recipe Category Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val allSelected = selectedCategory == null
                Button(
                    onClick = { onSelectCategory(null) },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("tab_craft_all"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allSelected) ImmersiveAccentOrange else ImmersiveSurfaceVariant,
                        contentColor = if (allSelected) ImmersiveBackground else ImmersiveTextMuted
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("ALL", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                com.example.data.room.CraftingCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Button(
                        onClick = { onSelectCategory(cat) },
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("tab_craft_${cat.name}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ImmersiveAccentOrange else ImmersiveSurfaceVariant,
                            contentColor = if (isSelected) ImmersiveBackground else ImmersiveTextMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(cat.label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Button(
                onClick = onOpenCustomCraft,
                modifier = Modifier
                    .height(28.dp)
                    .testTag("btn_custom_craft_dialog"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveTeal,
                    contentColor = ImmersiveBackground
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("+ CUSTOM", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Recipe Cards List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(recipes, key = { it.recipeId }) { recipe ->
                CraftingRecipeCard(
                    recipe = recipe,
                    scrapAvailable = scrapCount,
                    chemAvailable = chemCount,
                    biogelAvailable = biogelCount,
                    circuitAvailable = circuitCount,
                    onCraft = { onCraftRecipe(recipe) }
                )
            }
        }
    }
}

@Composable
private fun CraftingRecipeCard(
    recipe: com.example.data.room.CraftingRecipe,
    scrapAvailable: Int,
    chemAvailable: Int,
    biogelAvailable: Int,
    circuitAvailable: Int,
    onCraft: () -> Unit
) {
    val canAfford = scrapAvailable >= recipe.scrapMetalCost &&
            chemAvailable >= recipe.chemReagentCost &&
            biogelAvailable >= recipe.biogelCost &&
            circuitAvailable >= recipe.microCircuitCost

    val rarityColor = when (recipe.resultRarity) {
        com.example.data.room.ItemRarity.LEGENDARY -> androidx.compose.ui.graphics.Color(0xFFFFD700)
        com.example.data.room.ItemRarity.EPIC -> androidx.compose.ui.graphics.Color(0xFFA855F7)
        com.example.data.room.ItemRarity.RARE -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
        com.example.data.room.ItemRarity.UNCOMMON -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        else -> androidx.compose.ui.graphics.Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (canAfford) ImmersiveAccentOrange.copy(alpha = 0.8f) else ImmersiveSurfaceVariant,
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Recipe Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.name,
                    color = ImmersiveText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )

                Box(
                    modifier = Modifier
                        .background(rarityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(0.8.dp, rarityColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = recipe.resultRarity.name,
                        color = rarityColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = recipe.resultDescription,
                color = ImmersiveTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Outcome Stats Preview
            val statsPreview = buildString {
                if (recipe.damage > 0) append("+${recipe.damage} DMG  ")
                if (recipe.defense > 0) append("+${recipe.defense} DEF  ")
                if (recipe.healHp > 0) append("+${recipe.healHp} HP  ")
                if (recipe.reduceToxicity > 0) append("-${recipe.reduceToxicity}% TOX  ")
                if (recipe.criticalBonus > 0f) append("+${(recipe.criticalBonus * 100).toInt()}% CRIT  ")
                append("${recipe.weightKg}kg")
            }

            Text(
                text = statsPreview,
                color = PhosphorGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Materials Cost Checklist & Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recipe.scrapMetalCost > 0) {
                        val hasScrap = scrapAvailable >= recipe.scrapMetalCost
                        Text(
                            text = "🔩 $scrapAvailable/${recipe.scrapMetalCost}",
                            color = if (hasScrap) PhosphorGreen else ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }
                    if (recipe.chemReagentCost > 0) {
                        val hasChem = chemAvailable >= recipe.chemReagentCost
                        Text(
                            text = "🧪 $chemAvailable/${recipe.chemReagentCost}",
                            color = if (hasChem) PhosphorGreen else ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }
                    if (recipe.biogelCost > 0) {
                        val hasBio = biogelAvailable >= recipe.biogelCost
                        Text(
                            text = "🧬 $biogelAvailable/${recipe.biogelCost}",
                            color = if (hasBio) PhosphorGreen else ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }
                    if (recipe.microCircuitCost > 0) {
                        val hasCirc = circuitAvailable >= recipe.microCircuitCost
                        Text(
                            text = "💽 $circuitAvailable/${recipe.microCircuitCost}",
                            color = if (hasCirc) PhosphorGreen else ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }
                }

                Button(
                    onClick = onCraft,
                    enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveAccentOrange,
                        contentColor = ImmersiveBackground,
                        disabledContainerColor = ImmersiveSurfaceVariant,
                        disabledContentColor = ImmersiveTextMuted
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("btn_craft_${recipe.recipeId}"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (canAfford) "FABRICATE" else "LOCKED",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EquipmentUpgradeBayView(
    roomInventory: List<com.example.data.room.InventoryItemEntity>,
    scrapCount: Int,
    chemCount: Int,
    playerCredits: Int,
    onUpgradeItem: (String) -> Unit
) {
    val upgradableItems = remember(roomInventory) {
        roomInventory.filter { it.type == "WEAPON" || it.type == "ARMOR" || it.type == "NEURAL_CHIP" }
    }

    var selectedItemId by remember { mutableStateOf(upgradableItems.firstOrNull()?.itemId) }
    val selectedItem = upgradableItems.firstOrNull { it.itemId == selectedItemId } ?: upgradableItems.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "SELECT EQUIPMENT TO OVERCLOCK / UPGRADE",
            color = ImmersiveAccentOrange,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (upgradableItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO UPGRADABLE WEAPONS OR ARMOR FOUND IN INVENTORY",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            // Horizontal selector of upgradable items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                upgradableItems.take(4).forEach { item ->
                    val isSelected = (selectedItem?.itemId == item.itemId)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (isSelected) ImmersiveTeal else ImmersiveSurfaceVariant,
                                RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) ImmersiveTeal.copy(alpha = 0.15f) else ImmersiveBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        onClick = { selectedItemId = item.itemId }
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = item.name,
                                color = if (isSelected) ImmersiveText else ImmersiveTextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Lvl ${item.techLevel}",
                                color = ImmersiveAccentOrange,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected Item Upgrade Blueprint
            selectedItem?.let { item ->
                val cost = com.example.data.room.UpgradeEngine.calculateUpgrade(item)
                val canUpgrade = scrapCount >= cost.scrapCost &&
                        chemCount >= cost.chemCost &&
                        playerCredits >= cost.creditCost

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, ImmersiveTeal, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                color = ImmersiveText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "TECH LEVEL: ${item.techLevel} ➔ ${cost.nextTechLevel}",
                                color = ImmersiveAccentOrange,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Current vs Upgraded Comparison
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CURRENT SPEC", color = ImmersiveTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                if (item.damage > 0) Text("Damage: ${item.damage}", color = PhosphorGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (item.defense > 0) Text("Defense: ${item.defense}", color = PhosphorGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Max Durability: ${item.maxDurability}", color = ImmersiveTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("OVERCLOCKED SPEC", color = ImmersiveAccentOrange, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                if (cost.nextDamage > 0) Text("Damage: ${cost.nextDamage} (+6)", color = AcidYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (cost.nextDefense > 0) Text("Defense: ${cost.nextDefense} (+5)", color = AcidYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Max Durability: ${cost.nextMaxDurability} (+15)", color = AcidYellow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Upgrade Resource Cost Checklist
                        Text("REQUIRED REAGENTS & COMPONENTS:", color = ImmersiveTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔩 Scrap: $scrapCount/${cost.scrapCost}",
                                color = if (scrapCount >= cost.scrapCost) PhosphorGreen else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = "🧪 Chems: $chemCount/${cost.chemCost}",
                                color = if (chemCount >= cost.chemCost) PhosphorGreen else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = "🪙 $playerCredits/${cost.creditCost} CR",
                                color = if (playerCredits >= cost.creditCost) PhosphorGreen else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { onUpgradeItem(item.itemId) },
                            enabled = canUpgrade,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ImmersiveTeal,
                                contentColor = ImmersiveBackground,
                                disabledContainerColor = ImmersiveSurfaceVariant,
                                disabledContentColor = ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_upgrade_selected_item")
                        ) {
                            Text(
                                text = if (canUpgrade) "⚡ OVERCLOCK & UPGRADE EQUIPMENT" else "INSUFFICIENT MATERIALS",
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
    scrapAvailable: Int,
    chemAvailable: Int,
    onCraft: (name: String, type: String, dmg: Int, def: Int, heal: Int, tox: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var itemName by remember { mutableStateOf("Overcharged Plasma Rail") }
    var itemType by remember { mutableStateOf("WEAPON") }

    val scrapCost = when (itemType) {
        "WEAPON" -> 3
        "ARMOR" -> 4
        "NEURAL_CHIP" -> 2
        else -> 1
    }
    val chemCost = when (itemType) {
        "CONSUMABLE" -> 3
        "NEURAL_CHIP" -> 2
        "WEAPON" -> 2
        else -> 1
    }

    val canAfford = scrapAvailable >= scrapCost && chemAvailable >= chemCost

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.92f))
            .padding(20.dp),
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
                    text = "CUSTOM CHEMICAL & SCRAP SYNTHESIS",
                    color = ImmersiveAccentOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Combines chemical reactants with alloy scrap for custom field fabrication",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp
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

                Spacer(modifier = Modifier.height(10.dp))

                // Material Cost requirements
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔩 Scrap Needed: $scrapAvailable/$scrapCost",
                        color = if (scrapAvailable >= scrapCost) PhosphorGreen else ToxicRed,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                    Text(
                        text = "🧪 Chems Needed: $chemAvailable/$chemCost",
                        color = if (chemAvailable >= chemCost) PhosphorGreen else ToxicRed,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImmersiveAccentOrange,
                            contentColor = ImmersiveBackground,
                            disabledContainerColor = ImmersiveSurfaceVariant,
                            disabledContentColor = ImmersiveTextMuted
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
    playerStatusEffects: List<com.example.data.StatusEffect> = emptyList(),
    queueState: com.example.data.TurnCombatQueueState = com.example.data.TurnCombatQueueState(),
    onAttack: () -> Unit,
    onDefend: () -> Unit = {},
    onUseStimpack: () -> Unit = {},
    onUseChemEffect: (com.example.data.StatusEffectType) -> Unit = {},
    onFlee: () -> Unit = {},
    onClose: () -> Unit
) {
    val isPlayerTurn = queueState.isPlayerTurn || queueState.combatants.isEmpty()
    val activeCombatant = queueState.activeCombatant
    var showChemOptions by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.95f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .border(2.dp, if (isPlayerTurn) ImmersiveTeal else ToxicRed, RoundedCornerShape(20.dp))
                .testTag("combat_modal_root"),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Encounter Title and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚔ TACTICAL COMBAT ENGAGEMENT",
                            color = if (isPlayerTurn) ImmersiveTeal else ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("btn_close_combat")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Combat",
                            tint = ImmersiveTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Active Combatant Turn Phase Banner
                val bannerBg = if (isPlayerTurn) ImmersiveTeal.copy(alpha = 0.15f) else ToxicRed.copy(alpha = 0.15f)
                val bannerBorder = if (isPlayerTurn) ImmersiveTeal else ToxicRed
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerBg, RoundedCornerShape(8.dp))
                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("active_turn_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPlayerTurn) "▶ CURRENT TURN: SCYTHE-01 (YOUR ACTION)" else "⚠️ ENEMY TURN: ${activeCombatant?.name ?: enemy.name}",
                            color = if (isPlayerTurn) ImmersiveTeal else ToxicRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )

                        Text(
                            text = if (isPlayerTurn) "2 AP READY" else "EXECUTING AI",
                            color = if (isPlayerTurn) PhosphorGreen else AcidYellow,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Target Enemy Visual Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "[ ${enemy.asciiGlyph} ]",
                                color = ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )

                            Column {
                                Text(
                                    text = enemy.name,
                                    color = ImmersiveText,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )

                                Text(
                                    text = "HP: ${enemy.hp} / ${enemy.maxHp}  •  ATK: ${enemy.attack}  •  ARMOR: ${enemy.armor}",
                                    color = ImmersiveTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Enemy HP Progress Bar
                        val hpProgress = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { hpProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                            color = if (hpProgress > 0.3f) ToxicRed else AcidYellow,
                            trackColor = ImmersiveSurfaceVariant
                        )

                        // Enemy Status Effects Display
                        if (enemy.statusEffects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AFFLICTIONS:",
                                    color = ImmersiveTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                enemy.statusEffects.forEach { effect ->
                                    val color = androidx.compose.ui.graphics.Color(effect.type.colorHex)
                                    Box(
                                        modifier = Modifier
                                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                            .border(0.5.dp, color, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${effect.iconGlyph} ${effect.name} (${effect.durationTurns}t)",
                                            color = color,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val stateColor = when (enemy.state) {
                            com.example.data.NpcState.AGGRESSIVE -> ToxicRed
                            com.example.data.NpcState.FLEE -> AcidYellow
                            com.example.data.NpcState.PATROL -> ImmersiveTeal
                        }

                        val stateText = when (enemy.state) {
                            com.example.data.NpcState.AGGRESSIVE -> "⚔ AI STATE: AGGRESSIVE (PURSUE / STRIKE)"
                            com.example.data.NpcState.FLEE -> "💨 AI STATE: FLEEING (MORALE BROKEN)"
                            com.example.data.NpcState.PATROL -> "👁 AI STATE: PATROL (SURVEILLANCE)"
                        }

                        Text(
                            text = stateText,
                            color = stateColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }
                }

                // Player Vital & Status Summary
                if (playerStatusEffects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ImmersiveSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE BUFFS/DEBUFFS:",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        playerStatusEffects.forEach { effect ->
                            val color = androidx.compose.ui.graphics.Color(effect.type.colorHex)
                            Box(
                                modifier = Modifier
                                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                    .border(0.5.dp, color, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${effect.iconGlyph} ${effect.name} (${effect.durationTurns}t)",
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tactical Action Controls Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Strike & Defend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onAttack,
                            enabled = isPlayerTurn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ToxicRed,
                                contentColor = ImmersiveText,
                                disabledContainerColor = ImmersiveSurfaceVariant,
                                disabledContentColor = ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_combat_attack")
                        ) {
                            Text(
                                text = "⚔ STRIKE WEAPON",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        Button(
                            onClick = onDefend,
                            enabled = isPlayerTurn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ImmersiveSurfaceVariant,
                                contentColor = ImmersiveTeal,
                                disabledContainerColor = ImmersiveSurfaceVariant,
                                disabledContentColor = ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .border(1.dp, ImmersiveTeal.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .testTag("btn_combat_defend")
                        ) {
                            Text(
                                text = "🛡 DEFEND / WAIT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Row 2: Stimpack, Tactical Chem Menu & Disengage/Flee
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onUseStimpack,
                            enabled = isPlayerTurn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ImmersiveSurfaceVariant,
                                contentColor = PhosphorGreen,
                                disabledContainerColor = ImmersiveSurfaceVariant,
                                disabledContentColor = ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .testTag("btn_combat_stimpack")
                        ) {
                            Text(
                                text = "🧪 STIM (+35HP)",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }

                        Button(
                            onClick = { showChemOptions = !showChemOptions },
                            enabled = isPlayerTurn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showChemOptions) ImmersiveAccentOrange else ImmersiveSurfaceVariant,
                                contentColor = if (showChemOptions) ImmersiveBackground else ImmersiveAccentOrange,
                                disabledContainerColor = ImmersiveSurfaceVariant,
                                disabledContentColor = ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .border(1.dp, ImmersiveAccentOrange.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .testTag("btn_combat_chem_menu")
                        ) {
                            Text(
                                text = if (showChemOptions) "▲ HIDE CHEMS" else "⚡ CHEMS / STATUS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }

                        Button(
                            onClick = onFlee,
                            enabled = isPlayerTurn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ImmersiveSurfaceVariant,
                                contentColor = AcidYellow,
                                disabledContainerColor = ImmersiveSurfaceVariant,
                                disabledContentColor = ImmersiveTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(38.dp)
                                .border(1.dp, AcidYellow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .testTag("btn_combat_flee")
                        ) {
                            Text(
                                text = "💨 FLEE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // Tactical Status / Chem Action Expandable Row
                    if (showChemOptions && isPlayerTurn) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { onUseChemEffect(com.example.data.StatusEffectType.ADRENALINE); showChemOptions = false },
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0284C7), contentColor = ImmersiveText),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("btn_chem_adrenaline"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("💉 ADRENALINE", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onUseChemEffect(com.example.data.StatusEffectType.STUN); showChemOptions = false },
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFD97706), contentColor = ImmersiveText),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("btn_chem_stun"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("⚡ EMP STUN", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onUseChemEffect(com.example.data.StatusEffectType.CORROSION); showChemOptions = false },
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEA580C), contentColor = ImmersiveText),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("btn_chem_corrosion"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("🧪 ACID FLASK", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onUseChemEffect(com.example.data.StatusEffectType.RADIATION); showChemOptions = false },
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF059669), contentColor = ImmersiveText),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("btn_chem_purge"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("✨ PURGE TOX", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
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

/**
 * Quest Journal modal — built from narrative progress + world milestones.
 */
@Composable
fun QuestLogModal(
    quests: List<Quest>,
    onClose: () -> Unit
) {
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
                .fillMaxHeight(0.9f)
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
                        text = "▣ MISSION JOURNAL",
                        color = ImmersiveTeal,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = onClose, modifier = Modifier.testTag("btn_close_quests")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTeal)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (quests.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO ACTIVE CONTRACTS",
                            color = ImmersiveTextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quests, key = { it.id }) { quest ->
                            val accent = when (quest.status) {
                                QuestStatus.COMPLETED -> PhosphorGreen
                                QuestStatus.FAILED -> ToxicRed
                                else -> ImmersiveAccentOrange
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = ImmersiveBackground),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = quest.title,
                                            color = ImmersiveText,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(accent.copy(alpha = 0.2f))
                                                .border(0.6.dp, accent, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = quest.status.name,
                                                color = accent,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.5.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = quest.description,
                                        color = ImmersiveTextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    quest.objectives.forEach { obj ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (obj.isCompleted) "✔" else "○",
                                                color = if (obj.isCompleted) PhosphorGreen else ImmersiveTextMuted,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = obj.description,
                                                color = if (obj.isCompleted) PhosphorGreen else ImmersiveText,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }

                                    if (quest.objectives.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "PROGRESS: ${quest.progressText}",
                                            color = accent,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

