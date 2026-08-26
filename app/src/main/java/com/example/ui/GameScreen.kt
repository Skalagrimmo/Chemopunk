package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Enemy
import com.example.data.StatusEffectType
import com.example.ui.components.AsciiGlIsometricView
import com.example.ui.components.AsciiIsometricView
import com.example.ui.components.CombatModal
import com.example.ui.components.ControlDPad
import com.example.ui.components.HudOverlay
import com.example.ui.components.InventoryModal
import com.example.ui.components.MarkdownEditorModal
import com.example.ui.components.QuestLogModal
import com.example.ui.components.SkillsModal
import com.example.ui.components.PerkSelectModal
import com.example.ui.components.TradeModal
import com.example.ui.components.SynthesisModal
import com.example.ui.components.RadialQuickActionMenu
import com.example.ui.components.StoryDialogueScreen
import com.example.ui.components.StoryModal
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.AmberTerminal
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ToxicRed
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.ViewMode

/**
 * Smartphone-First Game Screen Layout.
 * Layer architecture:
 * 1. Top Cyberpunk HUD Header with status and quick tools.
 * 2. Full-Screen 2.5D OpenGL ASCII Viewport with integrated zoom/flare controls.
 * 3. Bottom Ergonomic Thumb Navigation Deck & Action Triggers.
 * 4. Modal Overlays with backdrop scrims and safe padding.
 */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var useOpenGlRenderer by remember { mutableStateOf(true) }

    var isRadialMenuOpen by remember { mutableStateOf(false) }
    var radialTouchOffset by remember { mutableStateOf(Offset(200f, 300f)) }
    var radialTargetTile by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var radialTargetEnemy by remember { mutableStateOf<Enemy?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("game_screen_root"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = ImmersiveBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Top HUD Overlay (Compact Smartphone Header)
                HudOverlay(
                    player = uiState.player,
                    audioProfile = uiState.audioProfile,
                    isEncumbered = uiState.isEncumbered,
                    onOpenInventory = { viewModel.openModal(ActiveModal.INVENTORY) },
                    onOpenQuests = { viewModel.openModal(ActiveModal.QUEST_LOG) },
                    onOpenSkills = { viewModel.openSkillsModal() },
                    onOpenSynthesis = { viewModel.openSynthesisModal() },
                    zoneName = uiState.currentZoneId.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it },
                    onOpenStoryNotes = { viewModel.setViewMode(ViewMode.STORY_DIALOGUE) },
                    onOpenMarkdownEditor = { viewModel.setViewMode(ViewMode.MARKDOWN_EDITOR) },
                    onTogglePalette = { viewModel.togglePalette() },
                    onToggleAudioMute = { viewModel.toggleAudioMute() }
                )

                // 2. Full-Height 2.5D Isometric ASCII Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(16.dp))
                        .background(ImmersiveBackground)
                ) {
                    if (useOpenGlRenderer) {
                        AsciiGlIsometricView(
                            mapGrid = uiState.mapGrid,
                            player = uiState.player,
                            enemies = uiState.activeEnemies,
                            lightSources = uiState.lightSources,
                            selectedTile = uiState.selectedTile,
                            discoveredTiles = uiState.discoveredTiles,
                            floatingTexts = uiState.floatingTexts,
                            paletteIndex = uiState.colorPaletteIndex,
                            screenShakeIntensity = uiState.screenShakeIntensity,
                            screenShakeStartTime = uiState.screenShakeStartTime,
                            onTileTapped = { gx, gy -> viewModel.handleTileTap(gx, gy) },
                            onLongPress = { sx, sy, gx, gy ->
                                radialTouchOffset = Offset(sx, sy)
                                radialTargetTile = Pair(gx, gy)
                                radialTargetEnemy = uiState.activeEnemies.firstOrNull { it.isAlive && it.x.toInt() == gx && it.y.toInt() == gy }
                                    ?: uiState.activeEnemies.filter { it.isAlive }.minByOrNull { e ->
                                        val dx = e.x - uiState.player.x
                                        val dy = e.y - uiState.player.y
                                        dx * dx + dy * dy
                                    }
                                isRadialMenuOpen = true
                            },
                            onDropFlare = { viewModel.deployEmergencyFlare() },
                            onRecenterCamera = {},
                            onClearSelection = { viewModel.clearSelectedTile() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsciiIsometricView(
                            mapGrid = uiState.mapGrid,
                            player = uiState.player,
                            enemies = uiState.activeEnemies,
                            lightSources = uiState.lightSources,
                            selectedTile = uiState.selectedTile,
                            discoveredTiles = uiState.discoveredTiles,
                            floatingTexts = uiState.floatingTexts,
                            paletteIndex = uiState.colorPaletteIndex,
                            screenShakeIntensity = uiState.screenShakeIntensity,
                            screenShakeStartTime = uiState.screenShakeStartTime,
                            onTileTapped = { gx, gy -> viewModel.handleTileTap(gx, gy) },
                            onLongPress = { sx, sy, gx, gy ->
                                radialTouchOffset = Offset(sx, sy)
                                radialTargetTile = Pair(gx, gy)
                                radialTargetEnemy = uiState.activeEnemies.firstOrNull { it.isAlive && it.x.toInt() == gx && it.y.toInt() == gy }
                                    ?: uiState.activeEnemies.filter { it.isAlive }.minByOrNull { e ->
                                        val dx = e.x - uiState.player.x
                                        val dy = e.y - uiState.player.y
                                        dx * dx + dy * dy
                                    }
                                isRadialMenuOpen = true
                            },
                            onDropFlare = { viewModel.deployEmergencyFlare() },
                            onRecenterCamera = {},
                            onClearSelection = { viewModel.clearSelectedTile() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 3. Ergonomic Smartphone Bottom Thumb Deck (8-Way D-Pad + Primary Actions)
                ControlDPad(
                    onMoveNorth = { viewModel.movePlayerIsometric(0, -1) },
                    onMoveSouth = { viewModel.movePlayerIsometric(0, 1) },
                    onMoveWest = { viewModel.movePlayerIsometric(-1, 0) },
                    onMoveEast = { viewModel.movePlayerIsometric(1, 0) },
                    onMoveNorthWest = { viewModel.movePlayerIsometric(-1, -1) },
                    onMoveNorthEast = { viewModel.movePlayerIsometric(1, -1) },
                    onMoveSouthWest = { viewModel.movePlayerIsometric(-1, 1) },
                    onMoveSouthEast = { viewModel.movePlayerIsometric(1, 1) },
                    onAction = {
                        val selected = uiState.selectedTile
                        if (selected != null) {
                            viewModel.handleTileTap(selected.first, selected.second)
                        } else {
                            val targetEnemy = uiState.activeEnemies.minByOrNull { e ->
                                val dx = e.x - uiState.player.x
                                val dy = e.y - uiState.player.y
                                dx * dx + dy * dy
                            }
                            if (targetEnemy != null) {
                                viewModel.handleTileTap(targetEnemy.x.toInt(), targetEnemy.y.toInt())
                            } else {
                                viewModel.handleTileTap(uiState.player.x.toInt(), uiState.player.y.toInt())
                            }
                        }
                    },
                    onOpenInventory = { viewModel.openModal(ActiveModal.INVENTORY) },
                    onWaitTurn = { viewModel.waitTurn() }
                )
            }

            // Radial Context Quick-Action Menu (Long-Press Gesture Overlay)
            RadialQuickActionMenu(
                visible = isRadialMenuOpen,
                touchPosition = radialTouchOffset,
                targetTile = radialTargetTile,
                targetedEnemy = radialTargetEnemy ?: uiState.activeCombatEnemy,
                player = uiState.player,
                onDismiss = { isRadialMenuOpen = false },
                onAttack = {
                    val enemy = radialTargetEnemy ?: uiState.activeCombatEnemy
                    if (enemy != null) {
                        viewModel.handleTileTap(enemy.x.toInt(), enemy.y.toInt())
                        viewModel.attackCombatEnemy()
                    } else {
                        viewModel.attackCombatEnemy()
                    }
                },
                onDeployFlare = { viewModel.deployEmergencyFlare() },
                onUseAdrenaline = { viewModel.useCombatChemEffect(StatusEffectType.ADRENALINE) },
                onUseNanoRegen = { viewModel.useCombatChemEffect(StatusEffectType.REGENERATION) },
                onUseShockGrenade = { viewModel.useCombatChemEffect(StatusEffectType.STUN) },
                onUseAcidFlask = { viewModel.useCombatChemEffect(StatusEffectType.CORROSION) },
                onUseNeurotoxin = { viewModel.useCombatChemEffect(StatusEffectType.POISON) },
                onWaitTurn = { viewModel.waitTurn() },
                onOpenInventory = { viewModel.openModal(ActiveModal.INVENTORY) }
            )

            // Story / Dialogue Screen (AST Markdown Renderer)
            if (uiState.currentViewMode == ViewMode.STORY_DIALOGUE) {
                val rawMarkdown = uiState.rawMarkdownContent
                val assetFile = uiState.currentStoryAssetFileName
                val parsedDocument = remember(uiState.currentStoryNode, rawMarkdown, assetFile) {
                    com.example.data.narrative.MarkdownNarrativeParser.parseNarrativeDocument(
                        markdownText = rawMarkdown,
                        assetFileName = assetFile
                    )
                }
                val currentDoc = uiState.currentStoryDocument ?: parsedDocument

                StoryDialogueScreen(
                    document = currentDoc,
                    currentNode = uiState.currentStoryNode,
                    availableAssets = uiState.availableStoryAssets,
                    currentAssetFileName = uiState.currentStoryAssetFileName,
                    onSelectAsset = { assetFileName -> viewModel.loadStoryScript(assetFileName) },
                    onSelectNode = { nodeId -> viewModel.selectStoryNode(nodeId) },
                    onChoiceSelected = { choice -> viewModel.selectStoryChoice(choice) },
                    onOpenEditor = { viewModel.setViewMode(ViewMode.MARKDOWN_EDITOR) },
                    onClose = { viewModel.setViewMode(ViewMode.ISOMETRIC_WORLD) },
                    playerInventoryItemIds = uiState.roomInventory.map { it.itemId }.toSet()
                )
            } else if (uiState.currentViewMode == ViewMode.MARKDOWN_EDITOR) {
                MarkdownEditorModal(
                    initialMarkdownText = uiState.rawMarkdownContent,
                    onSaveAndParse = { newMarkdown -> viewModel.reloadFromMarkdownString(newMarkdown) },
                    onClose = { viewModel.setViewMode(ViewMode.STORY_DIALOGUE) }
                )
            }

            // Active Modals (Optimized for Mobile)
            when (uiState.activeModal) {
                ActiveModal.INVENTORY -> {
                    InventoryModal(
                        roomInventory = uiState.roomInventory,
                        gearStats = uiState.gearStats,
                        playerCredits = uiState.characterProfile?.credits ?: uiState.player.credits,
                        maxCarryWeight = uiState.characterProfile?.maxCarryWeightKg ?: 45.0f,
                        onEquipItem = { itemId -> viewModel.equipInventoryItem(itemId) },
                        onUnequipItem = { itemId -> viewModel.unequipInventoryItem(itemId) },
                        onUseItem = { itemId -> viewModel.useOrConsumeItem(itemId) },
                        onRepairItem = { itemId -> viewModel.repairInventoryItem(itemId) },
                        onScrapItem = { itemId -> viewModel.scrapInventoryItem(itemId) },
                        onCraftRecipe = { recipe -> viewModel.craftWastelandRecipe(recipe) },
                        onUpgradeItem = { itemId -> viewModel.upgradeInventoryItem(itemId) },
                        onCraftSampleItem = { name, type, dmg, def, heal, tox ->
                            viewModel.craftOrAddCustomItem(name, type, dmg, def, heal, tox)
                        },
                        onClose = { viewModel.closeModal() }
                    )
                }
                ActiveModal.COMBAT -> {
                    uiState.activeCombatEnemy?.let { enemy ->
                        CombatModal(
                            enemy = enemy,
                            playerHp = uiState.player.hp,
                            playerMaxHp = uiState.player.maxHp,
                            playerToxicity = uiState.player.toxicity,
                            playerStatusEffects = uiState.player.statusEffects,
                            queueState = uiState.turnQueueState,
                            availableTargets = uiState.activeEnemies.filter { it.isAlive },
                            onSelectTarget = { targetId -> viewModel.selectCombatTarget(targetId) },
                            onAttack = { viewModel.attackCombatEnemy() },
                            onDefend = { viewModel.defendCombatTurn() },
                            onUseStimpack = { viewModel.useCombatStimpack() },
                            onUseChemEffect = { effectType -> viewModel.useCombatChemEffect(effectType) },
                            onFlee = { viewModel.fleeCombat() },
                            onClose = { viewModel.closeModal() }
                        )
                    }
                }
                ActiveModal.QUEST_LOG -> {
                    QuestLogModal(
                        quests = uiState.quests,
                        onClose = { viewModel.closeModal() }
                    )
                }
                ActiveModal.SKILLS -> {
                    SkillsModal(
                        playerLevel = uiState.player.level,
                        currentExp = uiState.player.exp,
                        expForLevel = uiState.player.level * 100,
                        unspentSkillPoints = uiState.unspentSkillPoints,
                        skills = uiState.skills,
                        acquiredPerks = uiState.acquiredPerks,
                        onAllocateSkill = { skill -> viewModel.allocateSkillPoint(skill) },
                        onClose = { viewModel.closeModal() }
                    )
                }
                ActiveModal.PERK_SELECT -> {
                    PerkSelectModal(
                        choices = uiState.pendingPerkChoices,
                        onSelect = { perk -> viewModel.confirmPerkChoice(perk) },
                        onSkip = { viewModel.cancelPerkChoices() }
                    )
                }
                ActiveModal.TRADE -> {
                    TradeModal(
                        shopItems = uiState.shopItems,
                        ownedItems = uiState.roomInventory,
                        playerCredits = uiState.characterProfile?.credits ?: uiState.player.credits,
                        onBuy = { itemId -> viewModel.buyShopItem(itemId) },
                        onSell = { itemId -> viewModel.sellInventoryItem(itemId) },
                        onClose = { viewModel.closeModal() }
                    )
                }
                ActiveModal.SYNTHESIS -> {
                    val chemCount = uiState.roomInventory.filter { it.itemId == "mat_chem_reagent" }.sumOf { it.quantity }
                    val bioCount = uiState.roomInventory.filter { it.itemId == "mat_biogel_vial" }.sumOf { it.quantity }
                    SynthesisModal(
                        chemReagentCount = chemCount,
                        bioGelCount = bioCount,
                        onSynthesize = { chem, bio -> viewModel.synthesizeChem(chem, bio) },
                        onClose = { viewModel.closeModal() }
                    )
                }
                else -> {}
            }

            // Game Over / Victory Modal
            if (uiState.isGameOver || uiState.isVictory) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ImmersiveBackground.copy(alpha = 0.95f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                2.dp,
                                if (uiState.isVictory) ImmersiveTeal else ToxicRed,
                                RoundedCornerShape(20.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (uiState.isVictory) "SECTOR 7 PURGED & ESCAPED!" else "SIGNAL LOST - BIO-SUIT RUPTURE",
                                color = if (uiState.isVictory) ImmersiveTeal else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = if (uiState.isVictory)
                                    "You reached the extraction lift and successfully purged local mutagens."
                                else
                                    "Your bio-suit collapsed under mutagenic pressure. Reboot system to retry.",
                                color = ImmersiveTextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { viewModel.restartGame() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ImmersiveTeal,
                                    contentColor = ImmersiveBackground
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_restart_game")
                            ) {
                                Text(
                                    text = "REBOOT SYSTEM (RESTART)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
