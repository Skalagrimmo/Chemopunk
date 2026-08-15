package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AsciiIsometricView
import com.example.ui.components.CombatModal
import com.example.ui.components.ControlDPad
import com.example.ui.components.HudOverlay
import com.example.ui.components.InteractiveLogStream
import com.example.ui.components.InventoryModal
import com.example.ui.components.MarkdownEditorModal
import com.example.ui.components.StoryDialogueScreen
import com.example.ui.components.StoryModal
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.ToxicRed
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.ViewMode

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("game_screen_root"),
        containerColor = ImmersiveBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top HUD Overlay (Tactical Terminal Header)
                HudOverlay(
                    player = uiState.player,
                    audioProfile = uiState.audioProfile,
                    onOpenInventory = { viewModel.openModal(ActiveModal.INVENTORY) },
                    onOpenStoryNotes = { viewModel.setViewMode(ViewMode.STORY_DIALOGUE) },
                    onOpenMarkdownEditor = { viewModel.setViewMode(ViewMode.MARKDOWN_EDITOR) },
                    onTogglePalette = { viewModel.togglePalette() },
                    onToggleAudioMute = { viewModel.toggleAudioMute() }
                )

                // Dedicated Fallout 1 & 2 Style 2.5D Isometric ASCII Renderer Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp)
                        .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(16.dp))
                        .background(ImmersiveBackground, RoundedCornerShape(16.dp))
                ) {
                    AsciiIsometricView(
                        mapGrid = uiState.mapGrid,
                        player = uiState.player,
                        enemies = uiState.activeEnemies,
                        lightSources = uiState.lightSources,
                        selectedTile = uiState.selectedTile,
                        discoveredTiles = uiState.discoveredTiles,
                        floatingTexts = uiState.floatingTexts,
                        paletteIndex = uiState.colorPaletteIndex,
                        onTileTapped = { gx, gy -> viewModel.handleTileTap(gx, gy) },
                        onDropFlare = { viewModel.deployEmergencyFlare() },
                        onRecenterCamera = {},
                        onClearSelection = { viewModel.clearSelectedTile() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Interactive Tactical Telemetry Log Stream (Categorized, Animated, Icon-driven)
                InteractiveLogStream(
                    logs = uiState.combatLogs,
                    onOpenInventory = { viewModel.openModal(ActiveModal.INVENTORY) },
                    onOpenStory = { viewModel.setViewMode(ViewMode.STORY_DIALOGUE) },
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                // Enhanced 8-Way Directional D-Pad with Isometric Grid Movement & V.A.T.S.
                ControlDPad(
                    onMoveNorth = { viewModel.movePlayerIsometric(0, -1) },
                    onMoveSouth = { viewModel.movePlayerIsometric(0, 1) },
                    onMoveWest = { viewModel.movePlayerIsometric(-1, 0) },
                    onMoveEast = { viewModel.movePlayerIsometric(1, 0) },
                    onMoveNorthWest = { viewModel.movePlayerIsometric(-1, -1) },
                    onMoveNorthEast = { viewModel.movePlayerIsometric(1, -1) },
                    onMoveSouthWest = { viewModel.movePlayerIsometric(-1, 1) },
                    onMoveSouthEast = { viewModel.movePlayerIsometric(1, 1) },
                    onActionVats = {
                        val selected = uiState.selectedTile
                        if (selected != null) {
                            viewModel.handleTileTap(selected.first, selected.second)
                        } else {
                            viewModel.handleTileTap(uiState.player.x.toInt(), uiState.player.y.toInt())
                        }
                    },
                    onQuickAttack = {
                        val selected = uiState.selectedTile
                        if (selected != null) {
                            viewModel.handleTileTap(selected.first, selected.second)
                        } else {
                            // Find closest enemy within range
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
                    onWaitTurn = { viewModel.waitTurn() }
                )
            }

            // Story / Dialogue Modal with Rich AST Markdown Narrative Renderer
            if (uiState.currentViewMode == ViewMode.STORY_DIALOGUE) {
                val currentDoc = uiState.currentStoryDocument
                    ?: com.example.data.narrative.MarkdownNarrativeParser.parseNarrativeDocument(
                        markdownText = uiState.rawMarkdownContent,
                        assetFileName = uiState.currentStoryAssetFileName
                    )

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

            // Active Modals
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
                            onAttack = { viewModel.attackCombatEnemy() },
                            onClose = { viewModel.closeModal() }
                        )
                    }
                }
                else -> {}
            }

            // Game Over / Victory Modal
            if (uiState.isGameOver || uiState.isVictory) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ImmersiveBackground.copy(alpha = 0.95f))
                        .padding(24.dp),
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
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (uiState.isVictory) "SECTOR 7 ESCAPED!" else "SIGNAL LOST - TOXICITY FATALITY",
                                color = if (uiState.isVictory) ImmersiveTeal else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (uiState.isVictory)
                                    "You successfully reached the Extraction Lift and purged local mutagens!"
                                else
                                    "Your bio-suit ruptured in the chemical wasteland. Reboot vital functions to retry.",
                                color = ImmersiveTextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.restartGame() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ImmersiveTeal,
                                    contentColor = ImmersiveBackground
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_restart_game")
                            ) {
                                Text(
                                    text = "REBOOT SYSTEM (RESTART)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
