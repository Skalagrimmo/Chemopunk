package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CombatLogEntry
import com.example.data.CombatQueueEntity
import com.example.data.CombatantType
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.InteractiveObject
import com.example.data.InteractiveObjectType
import com.example.data.Item
import com.example.data.ItemType
import com.example.data.MarkdownParser
import com.example.data.NpcState
import com.example.data.Player
import com.example.data.Perk
import com.example.data.Quest
import com.example.data.QuestObjective
import com.example.data.QuestStatus
import com.example.data.StatusEffect
import com.example.data.StatusEffectType
import com.example.data.SkillType
import com.example.data.StoryNode
import com.example.data.TileType
import com.example.data.TurnCombatQueueState
import com.example.data.TurnPhase
import com.example.data.room.BranchingChoiceEntity
import com.example.data.room.CharacterProfileEntity
import com.example.data.room.CraftingMaterials
import com.example.data.room.EquipSlot
import com.example.data.room.GameDatabase
import com.example.data.room.GearCombatStats
import com.example.data.room.InventoryItemEntity
import com.example.data.room.InventoryRepository
import com.example.data.room.ItemRarity
import com.example.data.room.NarrativeNodeEntity
import com.example.data.room.NarrativeProgressEntity
import com.example.data.room.StoryNarrativeRepository
import com.example.data.room.StoryScriptEntity
import com.example.engine.AmbientAudioProfile
import com.example.engine.DynamicLightingEngine
import com.example.engine.LightSource
import com.example.engine.LightType
import com.example.engine.NpcStateMachineEngine
import com.example.engine.ProceduralAudioManager
import com.example.data.narrative.MarkdownNarrativeParser
import com.example.data.narrative.NarrativeScriptDocument
import com.example.data.narrative.StoryAssetDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

data class GameUiState(
    val player: Player = Player(),
    val currentStoryNode: StoryNode? = null,
    val currentStoryDocument: NarrativeScriptDocument? = null,
    val currentStoryAssetFileName: String = "chemopank_world.md",
    val availableStoryAssets: List<StoryAssetDescriptor> = MarkdownNarrativeParser.AVAILABLE_STORY_ASSETS,
    val roomScripts: List<StoryScriptEntity> = emptyList(),
    val roomNarrativeNodes: List<NarrativeNodeEntity> = emptyList(),
    val roomNarrativeChoices: List<BranchingChoiceEntity> = emptyList(),
    val narrativeProgress: NarrativeProgressEntity? = null,
    val inventory: List<Item> = emptyList(),
    val roomInventory: List<InventoryItemEntity> = emptyList(),
    val gearStats: GearCombatStats = GearCombatStats(),
    val characterProfile: CharacterProfileEntity? = null,
    val activeEnemies: List<Enemy> = emptyList(),
    val mapGrid: List<List<TileType>> = emptyList(),
    val lightSources: List<LightSource> = emptyList(),
    val combatLogs: List<CombatLogEntry> = emptyList(),
    val interactiveObjects: Map<Pair<Int, Int>, InteractiveObject> = emptyMap(),
    val quests: List<Quest> = emptyList(),
    val currentZoneId: String = "sector7",
    val killCount: Int = 0,
    val itemsCollected: Int = 0,
    val isEncumbered: Boolean = false,
    val skills: Map<com.example.data.SkillType, Int> = emptyMap(),
    val unspentSkillPoints: Int = 0,
    val unspentPerkPoints: Int = 0,
    val acquiredPerks: List<com.example.data.Perk> = emptyList(),
    val pendingPerkChoices: List<com.example.data.Perk> = emptyList(),
    val shopItems: List<com.example.data.room.NpcShopEntity> = emptyList(),
    val factionReps: Map<String, Int> = emptyMap(), // faction id -> standing (-100..100)
    val dialogueTree: com.example.data.DialogueTree? = null,
    val dialogueNodeId: String = "",
    val companions: List<com.example.data.Companion> = emptyList()
    val screenShakeIntensity: Float = 0f,
    val screenShakeStartTime: Long = 0L,
    val currentViewMode: ViewMode = ViewMode.ISOMETRIC_WORLD,
    val activeModal: ActiveModal = ActiveModal.NONE,
    val colorPaletteIndex: Int = 0, // 0: Cyberpunk Multi-Color, 1: Green CRT, 2: Amber, 3: Cyan
    val rawMarkdownContent: String = "",
    val activeCombatEnemy: Enemy? = null,
    val turnQueueState: TurnCombatQueueState = TurnCombatQueueState(),
    val selectedTile: Pair<Int, Int>? = null,
    val discoveredTiles: Set<Pair<Int, Int>> = emptySet(),
    val floatingTexts: List<FloatingText> = emptyList(),
    val audioProfile: AmbientAudioProfile = AmbientAudioProfile(),
    val isGameOver: Boolean = false,
    val isVictory: Boolean = false
)

enum class ViewMode {
    ISOMETRIC_WORLD,
    STORY_DIALOGUE,
    MARKDOWN_EDITOR
}

enum class ActiveModal {
    NONE,
    INVENTORY,
    COMBAT,
    QUEST_LOG,
    SKILLS,
    PERK_SELECT,
    TRADE,
    SYNTHESIS,
    DIALOGUE
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val parser = MarkdownParser(getApplication())
    private var parsedData: MarkdownParser.ParsedWorldData? = null
    private val npcStateMachineEngine = NpcStateMachineEngine()
    private val lightingEngine = DynamicLightingEngine()
    val proceduralAudioManager = ProceduralAudioManager(viewModelScope)

    private val database = GameDatabase.getDatabase(application)
    val inventoryRepository = InventoryRepository(
        inventoryDao = database.inventoryDao(),
        profileDao = database.characterProfileDao(),
        perkDao = database.perkDao(),
        shopDao = database.shopDao(),
        factionRepDao = database.factionRepDao()
    )
    val storyNarrativeRepository = StoryNarrativeRepository(
        storyDao = database.storyNarrativeDao(),
        inventoryDao = database.inventoryDao(),
        profileDao = database.characterProfileDao()
    )

    init {
        observeRoomDatabase()
        observeProceduralAudio()
        loadWorldFromMarkdown()
    }

    private fun observeProceduralAudio() {
        viewModelScope.launch {
            proceduralAudioManager.audioProfile.collect { profile ->
                _uiState.update { it.copy(audioProfile = profile) }
            }
        }
    }

    private fun initializeNpcStateMachines(rawEnemies: List<Enemy>, mapGrid: List<List<TileType>>): List<Enemy> {
        return rawEnemies.map { enemy ->
            val detRad = when {
                enemy.id.contains("mutant", ignoreCase = true) -> 5.5f
                enemy.id.contains("drone", ignoreCase = true) -> 4.5f
                else -> 3.5f
            }
            val waypoints = npcStateMachineEngine.generatePatrolWaypoints(enemy.x.toInt(), enemy.y.toInt(), mapGrid)
            enemy.copy(
                state = NpcState.PATROL,
                patrolOriginX = enemy.x,
                patrolOriginY = enemy.y,
                patrolWaypoints = waypoints,
                currentWaypointIdx = 0,
                detectionRadius = detRad,
                fleeThreshold = 0.30f
            )
        }
    }

    /**
     * Scatters interactive world props (terminals, lockers, switches, beacons) onto FLOOR tiles,
     * converting them to the INTERACTIVE tile type so they render and can be triggered on tap.
     */
    private fun injectInteractiveObjects(mapGrid: List<List<TileType>>): Pair<List<List<TileType>>, Map<Pair<Int, Int>, InteractiveObject>> {
        if (mapGrid.isEmpty()) return Pair(mapGrid, emptyMap())
        val floorTiles = mutableListOf<Pair<Int, Int>>()
        for (r in mapGrid.indices) {
            for (c in mapGrid[r].indices) {
                if (mapGrid[r][c] == TileType.FLOOR) floorTiles.add(Pair(c, r))
            }
        }
        if (floorTiles.size < 4) return Pair(mapGrid, emptyMap())

        val picks = listOf(3, 8, 13, 18).mapNotNull { floorTiles.getOrNull(it) }
        val types = listOf(
            InteractiveObjectType.TERMINAL,
            InteractiveObjectType.LOCKER,
            InteractiveObjectType.SWITCH,
            InteractiveObjectType.BEACON
        )

        val mutable = mapGrid.map { it.toMutableList() }
        val result = mutableMapOf<Pair<Int, Int>, InteractiveObject>()
        picks.forEachIndexed { idx, (x, y) ->
            val type = types.getOrElse(idx) { InteractiveObjectType.TERMINAL }
            mutable[y][x] = TileType.INTERACTIVE
            result[Pair(x, y)] = InteractiveObject(
                id = "io_${type.name.lowercase()}_${x}_$y",
                type = type,
                x = x,
                y = y,
                description = when (type) {
                    InteractiveObjectType.TERMINAL -> "Encrypted Sector 7 terminal. May contain salvage codes or intel."
                    InteractiveObjectType.LOCKER -> "Sealed supply locker. Might contain useful gear."
                    InteractiveObjectType.SWITCH -> "Power relay switch. Activating it floods the area with light."
                    InteractiveObjectType.BEACON -> "Emergency rescue beacon. Signals extraction forces."
                }
            )
        }
        if (floorTiles.size >= 5) {
            val mPos = floorTiles[floorTiles.size / 2]
            if (mPos !in result.keys) {
                mutable[mPos.second][mPos.first] = TileType.INTERACTIVE
                result[mPos] = InteractiveObject(
                    id = "io_merchant_${mPos.first}_${mPos.second}",
                    type = InteractiveObjectType.MERCHANT,
                    x = mPos.first,
                    y = mPos.second,
                    description = "Roving black-market vendor. Buy gear, sell salvage."
                )
            }
        }
        if (floorTiles.size >= 6) {
            val zPos = floorTiles[floorTiles.size - 1]
            if (zPos !in result.keys) {
                mutable[zPos.second][zPos.first] = TileType.INTERACTIVE
                result[zPos] = InteractiveObject(
                    id = "io_zoneexit_${zPos.first}_${zPos.second}",
                    type = InteractiveObjectType.ZONE_EXIT,
                    x = zPos.first,
                    y = zPos.second,
                    description = "Zone transit gate. Step through to reach the next sector."
                )
            }
        }
        return Pair(mutable.map { it.toList() }, result)
    }

    private fun buildInitialQuests(): List<Quest> {
        return listOf(
            Quest(
                id = "escape_sector_7",
                title = "Escape Sector 7",
                description = "Reach the Extraction Lift and purge the sector of mutagens.",
                objectives = listOf(
                    QuestObjective(id = "reach_lift", description = "Reach the Extraction Lift"),
                    QuestObjective(id = "survive", description = "Survive with bio-suit intact")
                )
            ),
            Quest(
                id = "purge_hostiles",
                title = "Purge the Mutagen Horde",
                description = "Eliminate hostile organisms roaming the facility.",
                objectives = listOf(QuestObjective(id = "kills", description = "Defeat 3 hostiles"))
            ),
            Quest(
                id = "salvage_run",
                title = "Salvage Run",
                description = "Recover useful equipment from the wasteland.",
                objectives = listOf(QuestObjective(id = "collect", description = "Collect 5 pieces of salvage"))
            )
        )
    }

    private fun computeEncumbered(roomInventory: List<InventoryItemEntity>, maxCarryWeight: Float): Boolean {
        val gearWeight = roomInventory
            .filter { !it.itemId.startsWith("mat_") }
            .sumOf { (it.weightKg * it.quantity).toDouble() }
            .toFloat()
        return gearWeight > maxCarryWeight
    }

    // region Skills & Perk System

    private fun hasPerk(perkId: String): Boolean =
        _uiState.value.acquiredPerks.any { it.perkId == perkId }

    /** exp (leftover within current level) required to advance from `level` to `level+1`. */
    private fun expForLevel(level: Int): Int = level * 100

    /** Award experience and trigger any pending level-ups. Returns a level-up log message (empty if none). */
    fun grantExp(amount: Int): String {
        val current = _uiState.value.player
        _uiState.update { it.copy(player = it.player.copy(exp = current.exp + amount)) }
        viewModelScope.launch { inventoryRepository.addExperience(amount) }
        return checkLevelUp()
    }

    private fun checkLevelUp(): String {
        var exp = _uiState.value.player.exp
        var level = _uiState.value.player.level
        var skillPoints = 0
        var perkPoints = 0
        while (exp >= expForLevel(level)) {
            exp -= expForLevel(level)
            level += 1
            skillPoints += 1
            if (level % 2 == 0) perkPoints += 1
        }
        if (level <= _uiState.value.player.level) {
            _uiState.update { it.copy(player = it.player.copy(exp = exp)) }
            return ""
        }
        viewModelScope.launch { inventoryRepository.grantLevelRewards(skillPoints, perkPoints) }
        _uiState.update { it.copy(player = it.player.copy(level = level, exp = exp)) }
        if (perkPoints > 0) rollPerkChoices()
        return "LEVEL UP! Reached level $level. +$skillPoints skill point(s), +$perkPoints perk point(s)."
    }

    private fun rollPerkChoices() {
        val owned = _uiState.value.acquiredPerks.map { it.perkId }.toSet()
        val available = Perk.POOL.filter { it.perkId !in owned }
        val choices = if (available.size <= 3) available else available.shuffled().take(3)
        _uiState.update { it.copy(pendingPerkChoices = choices, activeModal = ActiveModal.PERK_SELECT) }
    }

    fun confirmPerkChoice(perk: Perk) {
        viewModelScope.launch { inventoryRepository.acquirePerk(perk) }
        _uiState.update {
            val bumpedPlayer = if (perk.perkId == "iron_lungs") {
                it.player.copy(maxToxicity = it.player.maxToxicity + 25)
            } else {
                it.player
            }
            it.copy(
                pendingPerkChoices = emptyList(),
                activeModal = ActiveModal.NONE,
                acquiredPerks = it.acquiredPerks + perk,
                player = bumpedPlayer
            )
        }
    }

    fun cancelPerkChoices() {
        _uiState.update { it.copy(pendingPerkChoices = emptyList(), activeModal = ActiveModal.NONE) }
    }

    fun allocateSkillPoint(skill: SkillType) {
        viewModelScope.launch { inventoryRepository.allocateSkillPoint(skill) }
    }

    fun openSkillsModal() = openModal(ActiveModal.SKILLS)

    fun buyShopItem(itemId: String) {
        val creditsBefore = _uiState.value.player.credits
        viewModelScope.launch {
            val boughtId = inventoryRepository.buyShopItem(itemId)
            if (boughtId != null) {
                val profile = inventoryRepository.characterProfile.firstOrNull()
                val newCredits = profile?.credits ?: creditsBefore
                _uiState.update {
                    it.copy(
                        player = it.player.copy(credits = newCredits),
                        combatLogs = it.combatLogs + CombatLogEntry("Purchased item from vendor.", isHeal = true)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(combatLogs = it.combatLogs + CombatLogEntry("Transaction failed — insufficient credits or out of stock."))
                }
            }
        }
    }

    fun sellInventoryItem(itemId: String) {
        viewModelScope.launch {
            val success = inventoryRepository.sellInventoryItem(itemId)
            if (success) {
                val profile = inventoryRepository.characterProfile.firstOrNull()
                val newCredits = profile?.credits ?: _uiState.value.player.credits
                _uiState.update {
                    it.copy(
                        player = it.player.copy(credits = newCredits),
                        combatLogs = it.combatLogs + CombatLogEntry("Sold item to vendor for credits.", isHeal = true)
                    )
                }
            }
        }
    }

    fun openSynthesisModal() = openModal(ActiveModal.SYNTHESIS)

    // region NPC Dialogue

    /** Build and open a branching dialogue tree for the given NPC id. */
    fun startDialogue(npcId: String) {
        val tree = buildDialogueTree(npcId)
        _uiState.update {
            it.copy(
                dialogueTree = tree,
                dialogueNodeId = tree.startNodeId,
                activeModal = ActiveModal.DIALOGUE
            )
        }
    }

    private fun buildDialogueTree(npcId: String): com.example.data.DialogueTree {
        return when (npcId) {
            "merchant_trader" -> com.example.data.DialogueTree(
                npcId = npcId,
                startNodeId = "greet",
                nodes = mapOf(
                    "greet" to com.example.data.DialogueNode(
                        id = "greet",
                        speaker = "ROVING TRADER",
                        text = "Fresh stims, real credits-only. The mutants pay in teeth, but I don't take teeth.",
                        options = listOf(
                            com.example.data.DialogueOption("Browse goods", action = com.example.data.DialogueAction.OPEN_TRADE),
                            com.example.data.DialogueOption("Talk about the sector", nextNodeId = "lore"),
                            com.example.data.DialogueOption("Leave", action = com.example.data.DialogueAction.CLOSE)
                        )
                    ),
                    "lore" to com.example.data.DialogueNode(
                        id = "lore",
                        speaker = "ROVING TRADER",
                        text = "Sector 7's toxins are rising. The Scientists pay well for clean samples — the Raiders pay better for the opposite.",
                        options = listOf(
                            com.example.data.DialogueOption("Back", nextNodeId = "greet"),
                            com.example.data.DialogueOption("Leave", action = com.example.data.DialogueAction.CLOSE)
                        )
                    )
                )
            )
            else -> com.example.data.DialogueTree(
                npcId = npcId,
                startNodeId = "root",
                nodes = mapOf(
                    "root" to com.example.data.DialogueNode(
                        id = "root",
                        speaker = "STRANGER",
                        text = "..." ,
                        options = listOf(com.example.data.DialogueOption("Leave", action = com.example.data.DialogueAction.CLOSE))
                    )
                )
            )
        }
    }

    fun selectDialogueOption(option: com.example.data.DialogueOption) {
        when (option.action) {
            com.example.data.DialogueAction.OPEN_TRADE -> {
                _uiState.update { it.copy(activeModal = ActiveModal.TRADE) }
            }
            com.example.data.DialogueAction.CLOSE -> closeModal()
            else -> {
                val next = option.nextNodeId
                if (next != null) {
                    _uiState.update { it.copy(dialogueNodeId = next) }
                } else {
                    closeModal()
                }
            }
        }
    }

    // endregion

    fun synthesizeChem(chemReagents: Int, bioGel: Int) {
        if (chemReagents <= 0) return
        viewModelScope.launch {
            val haveChem = inventoryRepository.getMaterialQuantity(CraftingMaterials.CHEM_REAGENT)
            val haveBio = inventoryRepository.getMaterialQuantity(CraftingMaterials.BIOGEL_VIAL)
            if (haveChem < chemReagents || haveBio < bioGel) {
                _uiState.update {
                    it.copy(combatLogs = it.combatLogs + CombatLogEntry("⚗ Synthesis failed — insufficient reagents (have $haveChem chem / $haveBio biogel)."))
                }
                return@launch
            }
            inventoryRepository.addMaterialQuantity(CraftingMaterials.CHEM_REAGENT, -chemReagents)
            inventoryRepository.addMaterialQuantity(CraftingMaterials.BIOGEL_VIAL, -bioGel)
            val science = _uiState.value.skills[SkillType.SCIENCE] ?: 0
            val balance = if (bioGel > 0) (chemReagents.toFloat() / (chemReagents + bioGel)) else 1f
            val potency = (35 + science * 4 + (balance * 25).toInt()).coerceAtLeast(10)
            val isPurge = bioGel >= chemReagents
            val name = if (isPurge) "Toxic Purge Chem" else "Battle Stim Chem"
            val item = Item(
                id = "synth_chem_${System.currentTimeMillis()}",
                name = name,
                type = ItemType.CONSUMABLE,
                healHp = potency,
                reduceToxicity = if (isPurge) potency else (potency / 2)
            )
            inventoryRepository.addItemFromDomain(item, rarity = ItemRarity.UNCOMMON, weightKg = 0.3f)
            _uiState.update {
                it.copy(
                    combatLogs = it.combatLogs + CombatLogEntry(
                        "⚗ Synthesized $name (potency $potency HP) from $chemReagents reagent(s) + $bioGel biogel.",
                        isHeal = true
                    )
                )
            }
            closeModal()
        }
    }

    // endregion


    private fun updateQuestsProgress(state: GameUiState): List<Quest> {
        return state.quests.map { quest ->
            when (quest.id) {
                "escape_sector_7" -> {
                    val reached = state.isVictory
                    val survived = state.player.hp > 0 && state.player.toxicity < 100
                    val objectives = quest.objectives.map { obj ->
                        when (obj.id) {
                            "reach_lift" -> obj.copy(isCompleted = reached)
                            "survive" -> obj.copy(isCompleted = survived)
                            else -> obj
                        }
                    }
                    quest.copy(
                        objectives = objectives,
                        status = if (objectives.all { it.isCompleted }) QuestStatus.COMPLETED else QuestStatus.ACTIVE
                    )
                }
                "purge_hostiles" -> {
                    val completed = state.killCount >= 3
                    val objectives = quest.objectives.map { if (it.id == "kills") it.copy(isCompleted = completed) else it }
                    quest.copy(objectives = objectives, status = if (completed) QuestStatus.COMPLETED else QuestStatus.ACTIVE)
                }
                "salvage_run" -> {
                    val completed = state.itemsCollected >= 5
                    val objectives = quest.objectives.map { if (it.id == "collect") it.copy(isCompleted = completed) else it }
                    quest.copy(objectives = objectives, status = if (completed) QuestStatus.COMPLETED else QuestStatus.ACTIVE)
                }
                else -> quest
            }
        }
    }

    private fun triggerScreenShake(intensity: Float) {
        _uiState.update {
            it.copy(screenShakeIntensity = intensity, screenShakeStartTime = System.currentTimeMillis())
        }
    }

    /**
     * Triggers an interactive object's effect when the player taps (or stands next to) it.
     */
    private fun interactWithObject(obj: InteractiveObject) {
        val state = _uiState.value
        val grid = state.mapGrid
        if (grid.isEmpty() || obj.y !in grid.indices || obj.x !in grid[obj.y].indices) return
        if (obj.isUsed) return

        // Locked objects require the Lockpicking skill
        if (obj.locked && (_uiState.value.skills[SkillType.LOCKPICKING] ?: 0) <= 0) {
            val logs = state.combatLogs.toMutableList()
            logs.add(CombatLogEntry("🔒 ${obj.type.label} is locked. Requires the Lockpicking skill to bypass."))
            _uiState.update { it.copy(combatLogs = logs) }
            return
        }

        val logs = state.combatLogs.toMutableList()
        var player = state.player
        val px = player.x
        val py = player.y
        val distance = Math.hypot((obj.x - px).toDouble(), (obj.y - py).toDouble())

        // Require adjacency or standing on the tile
        if (distance > 1.5) {
            logs.add(CombatLogEntry("Too far from ${obj.type.label}. Move adjacent to interact."))
            _uiState.update { it.copy(combatLogs = logs) }
            return
        }

        when (obj.type) {
            InteractiveObjectType.TERMINAL -> {
                val credits = 15
                val levelUpMsg = grantExp(5)
                if (levelUpMsg.isNotBlank()) logs.add(CombatLogEntry(levelUpMsg, isHeal = true))
                player = _uiState.value.player.copy(credits = _uiState.value.player.credits + credits)
                logs.add(CombatLogEntry("⌨ Terminal accessed: decrypted Sector 7 logs. +$credits CR, +5 EXP.", isHeal = true))
                spawnFloatingText("+${credits} CR", obj.x.toFloat(), obj.y.toFloat(), 0xFFFFD700)
            }
            InteractiveObjectType.LOCKER -> {
                val consumables = parsedData?.items?.values?.filter { it.type == ItemType.CONSUMABLE }
                val loot = if (!consumables.isNullOrEmpty()) consumables.random() else Item("anti_toxin", "Anti-Toxin", ItemType.CONSUMABLE, healHp = 15, reduceToxicity = 40)
                viewModelScope.launch {
                    inventoryRepository.addItemFromDomain(domainItem = loot, rarity = ItemRarity.UNCOMMON, weightKg = 0.3f)
                }
                logs.add(CombatLogEntry("▣ Supply locker opened: recovered ${loot.name}!", isHeal = true))
                spawnFloatingText("LOOT: ${loot.name}", obj.x.toFloat(), obj.y.toFloat(), 0xFF22C55E)
            }
            InteractiveObjectType.SWITCH -> {
                val newLight = LightSource(
                    id = "switch_light_${obj.x}_${obj.y}",
                    gridX = obj.x + 0.5f,
                    gridY = obj.y + 0.5f,
                    colorR = 150, colorG = 220, colorB = 255,
                    intensity = 1.5f, radius = 7.0f,
                    type = LightType.POINT_TORCH, flickerFrequency = 4.0f, flickerIntensity = 0.12f
                )
                val revealed = computeFov(obj.x, obj.y, radius = 5) + state.discoveredTiles
                logs.add(CombatLogEntry("⊞ Power relay engaged! Area illuminated."))
                _uiState.update {
                    it.copy(
                        lightSources = it.lightSources + newLight,
                        discoveredTiles = revealed
                    )
                }
                spawnFloatingText("POWER ON", obj.x.toFloat(), obj.y.toFloat(), 0xFF96E0FF)
            }
            InteractiveObjectType.BEACON -> {
                val revealed = computeFov(obj.x, obj.y, radius = 8) + state.discoveredTiles
                logs.add(CombatLogEntry("☉ Rescue beacon activated! Extraction forces have been signaled."))
                val companions = state.companions
                val updatedCompanions = if (companions.none { it.id == "vex" }) {
                    logs.add(CombatLogEntry("⚑ Vex, a discharged extraction trooper, joins your squad.", isHeal = true))
                    companions + com.example.data.Companion(
                        id = "vex",
                        name = "Vex",
                        hp = 60,
                        maxHp = 60,
                        attack = 8,
                        quip = "On your six."
                    )
                } else {
                    logs.add(CombatLogEntry("⚑ Vex is already with your squad."))
                    companions
                }
                _uiState.update { it.copy(discoveredTiles = revealed, companions = updatedCompanions, combatLogs = logs) }
                spawnFloatingText("BEACON ONLINE", obj.x.toFloat(), obj.y.toFloat(), 0xFF4FD1C5)
            }
            InteractiveObjectType.MERCHANT -> {
                startDialogue("merchant_trader")
                return
            }
            InteractiveObjectType.ZONE_EXIT -> {
                val cur = _uiState.value.currentZoneId
                val idx = ZONE_ORDER.indexOf(cur).coerceAtLeast(0)
                val next = ZONE_ORDER[(idx + 1) % ZONE_ORDER.size]
                logs.add(CombatLogEntry("⏏ Stepped through the transit gate toward the next sector..."))
                _uiState.update { it.copy(combatLogs = logs) }
                travelToZone(next)
                return
            }
        }

        // Consume the object: revert tile to FLOOR and drop it from the active map
        val newGrid = grid.mapIndexed { r, row ->
            if (r == obj.y) row.toMutableList().also { it[obj.x] = TileType.FLOOR } else row
        }
        val newObjects = state.interactiveObjects - Pair(obj.x, obj.y)

        var itemsCollected = state.itemsCollected
        if (obj.type == InteractiveObjectType.LOCKER) itemsCollected += 1

        val refreshedQuests = updateQuestsProgress(state.copy(itemsCollected = itemsCollected, player = player))

        _uiState.update {
            it.copy(
                player = player,
                mapGrid = newGrid,
                interactiveObjects = newObjects,
                combatLogs = logs,
                itemsCollected = itemsCollected,
                quests = refreshedQuests
            )
        }
    }

    private fun observeRoomDatabase() {
        viewModelScope.launch {
            inventoryRepository.allInventoryItems.collect { items ->
                val domainItems = items.map { it.toDomainItem() }
                _uiState.update {
                    it.copy(
                        roomInventory = items,
                        inventory = domainItems,
                        isEncumbered = computeEncumbered(items, it.characterProfile?.maxCarryWeightKg ?: 45.0f)
                    )
                }
            }
        }

        viewModelScope.launch {
            inventoryRepository.gearCombatStats.collect { stats ->
                val activeWeaponItem = stats.activeWeapon?.toDomainItem()
                val activeArmorItem = stats.activeArmor?.toDomainItem()

                _uiState.update {
                    it.copy(
                        gearStats = stats,
                        player = it.player.copy(
                            equippedWeapon = activeWeaponItem,
                            equippedArmor = activeArmorItem
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            inventoryRepository.characterProfile.collect { profile ->
                if (profile != null) {
                    val skillMap = mapOf(
                        com.example.data.SkillType.LOCKPICKING to profile.skillLockpicking,
                        com.example.data.SkillType.SCIENCE to profile.skillScience,
                        com.example.data.SkillType.MELEE to profile.skillMelee,
                        com.example.data.SkillType.GUNS to profile.skillGuns,
                        com.example.data.SkillType.MEDICINE to profile.skillMedicine
                    )
                    _uiState.update {
                        it.copy(
                            characterProfile = profile,
                            skills = skillMap,
                            unspentSkillPoints = profile.unspentSkillPoints,
                            unspentPerkPoints = profile.unspentPerkPoints,
                            player = it.player.copy(
                                credits = profile.credits,
                                exp = profile.exp,
                                level = profile.level
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            inventoryRepository.acquiredPerks.collect { perks ->
                _uiState.update { it.copy(acquiredPerks = perks.map { p -> p.toPerk() }) }
            }
        }

        viewModelScope.launch {
            inventoryRepository.shopItems.collect { items ->
                _uiState.update { it.copy(shopItems = items) }
            }
        }

        viewModelScope.launch {
            inventoryRepository.factionReps.collect { reps ->
                _uiState.update { it.copy(factionReps = reps) }
            }
        }

        viewModelScope.launch {
            storyNarrativeRepository.allScripts.collect { scripts ->
                _uiState.update { it.copy(roomScripts = scripts) }
            }
        }

        viewModelScope.launch {
            storyNarrativeRepository.narrativeProgress.collect { progress ->
                _uiState.update { it.copy(narrativeProgress = progress) }
            }
        }
    }

    private val ZONES = mapOf(
        "sector7" to Zone("sector7", "Sector 7 — Chemical Wasteland", "chemopank_world.md", 1.0f),
        "sewers" to Zone("sewers", "Sector 7 — Toxic Sewers", "chemopank_world.md", 1.4f),
        "surface" to Zone("surface", "Surface — Ruined City", "chemopank_world.md", 1.8f)
    )
    private val ZONE_ORDER = listOf("sector7", "sewers", "surface")

    /** Transition to another zone, scaling remaining hostiles & dimming lights to match difficulty. */
    fun travelToZone(zoneId: String) {
        val zone = ZONES[zoneId] ?: return
        val mult = zone.encounterMultiplier
        val scaled = _uiState.value.activeEnemies.map { e ->
            if (e.isAlive) e.copy(
                hp = (e.hp * mult).toInt().coerceAtLeast(1),
                maxHp = (e.maxHp * mult).toInt().coerceAtLeast(1),
                attack = (e.attack * mult).toInt().coerceAtLeast(1)
            ) else e
        }
        val tintedLights = _uiState.value.lightSources.map { l ->
            l.copy(intensity = (l.intensity / kotlin.math.sqrt(mult.toDouble())).toFloat())
        }
        _uiState.update {
            it.copy(
                currentZoneId = zoneId,
                activeEnemies = scaled,
                lightSources = tintedLights,
                combatLogs = it.combatLogs + CombatLogEntry("⏏ Zone transit engaged: ${zone.name}. Threat level adjusted.")
            )
        }
    }

    fun loadWorldFromMarkdown(
        zoneId: String = "sector7",
        assetFileName: String = "chemopank_world.md",
        encounterMultiplier: Float = 1.0f
    ) {
        viewModelScope.launch {
            val data = parser.parseWorld(MarkdownParser.parseFromAssets(getApplication(), assetFileName).rawMarkdownText)
            parsedData = data

            val startingNode = data.storyNodes["start"] ?: data.storyNodes.values.firstOrNull()

            val startingWeapon = data.items[data.config.startingWeapon]
                ?: data.items.values.firstOrNull { it.type == ItemType.WEAPON }

            val startingInventory = mutableListOf<Item>()
            data.items.values.filter { it.type == ItemType.CONSUMABLE }.forEach { startingInventory.add(it) }
            startingWeapon?.let { startingInventory.add(it) }

            // Seed Room database if empty
            inventoryRepository.seedInitialInventoryIfEmpty(
                items = data.items.values.toList(),
                startingWeaponId = data.config.startingWeapon,
                startingCredits = data.config.startingCredits
            )
            inventoryRepository.seedShopIfEmpty()
            inventoryRepository.seedFactionRepsIfEmpty()

            // Synchronize and ingest Markdown narrative scripts into Room database
            storyNarrativeRepository.syncDefaultAssetsFromContext(getApplication())

            val player = Player(
                hp = data.config.startingHp,
                maxHp = data.config.startingHp,
                toxicity = 0,
                maxToxicity = data.config.maxToxicity,
                credits = data.config.startingCredits,
                equippedWeapon = startingWeapon,
                x = 1.5f,
                y = 1.5f
            )

            val initialLogs = listOf(
                CombatLogEntry("Initialized Sector 7 - Chemical Wasteland."),
                CombatLogEntry("Room Database inventory engine loaded & synchronized."),
                CombatLogEntry("Dynamic Lighting Engine active (Point lights, Flares, Torches, FOV).")
            )

            val initialDiscovered = computeFov(1, 1)

            // Seed initial map point light sources (Wall torches & Emergency beacons)
            val initialLights = mutableListOf<LightSource>(
                LightSource(
                    id = "torch_junction_1",
                    gridX = 3.5f,
                    gridY = 3.5f,
                    colorR = 255,
                    colorG = 175,
                    colorB = 40,
                    intensity = 1.25f,
                    radius = 4.2f,
                    type = LightType.POINT_TORCH,
                    flickerFrequency = 6.5f,
                    flickerIntensity = 0.2f
                ),
                LightSource(
                    id = "torch_corridor_2",
                    gridX = 8.5f,
                    gridY = 4.5f,
                    colorR = 255,
                    colorG = 160,
                    colorB = 30,
                    intensity = 1.2f,
                    radius = 4.0f,
                    type = LightType.POINT_TORCH,
                    flickerFrequency = 5.5f,
                    flickerIntensity = 0.18f
                ),
                LightSource(
                    id = "torch_vault_3",
                    gridX = 12.5f,
                    gridY = 8.5f,
                    colorR = 255,
                    colorG = 185,
                    colorB = 60,
                    intensity = 1.35f,
                    radius = 4.5f,
                    type = LightType.POINT_TORCH,
                    flickerFrequency = 7.0f,
                    flickerIntensity = 0.22f
                )
            )

            val scaledEnemies = data.enemies.map { e ->
                e.copy(
                    hp = (e.hp * encounterMultiplier).toInt().coerceAtLeast(1),
                    maxHp = (e.maxHp * encounterMultiplier).toInt().coerceAtLeast(1),
                    attack = (e.attack * encounterMultiplier).toInt().coerceAtLeast(1)
                )
            }
            val initialEnemies = initializeNpcStateMachines(scaledEnemies, data.mapGrid)
            val initialQueue = buildTurnCombatQueue(player, initialEnemies, round = 1, currentActiveId = "player")

            val (gridWithObjects, interactiveObjectsMap) = injectInteractiveObjects(data.mapGrid)
            val initialQuests = buildInitialQuests()
            val isEnc = computeEncumbered(
                roomInventory = _uiState.value.roomInventory,
                maxCarryWeight = data.config.startingCredits.let { 45.0f }
            )

            _uiState.update {
                it.copy(
                    player = player,
                    currentStoryNode = startingNode,
                    currentStoryDocument = data.document,
                    currentStoryAssetFileName = assetFileName,
                    currentZoneId = zoneId,
                    inventory = startingInventory,
                    activeEnemies = initialEnemies,
                    turnQueueState = initialQueue,
                    mapGrid = gridWithObjects,
                    lightSources = initialLights,
                    rawMarkdownContent = data.rawMarkdownContent,
                    discoveredTiles = initialDiscovered,
                    combatLogs = initialLogs,
                    interactiveObjects = interactiveObjectsMap,
                    quests = initialQuests,
                    isEncumbered = isEnc
                )
            }
            refreshAudioAtmosphere()
        }
    }

    /**
     * Calculates the real-time lighting intensity and hostile danger proximity at the player's tile,
     * updating the procedural synthesizer accordingly.
     */
    fun refreshAudioAtmosphere() {
        val state = _uiState.value
        val px = state.player.x
        val py = state.player.y

        // 1. Calculate lighting at player's position
        val tileLighting = lightingEngine.calculateLighting(
            gridX = px,
            gridY = py,
            lightSources = state.lightSources,
            discoveredTiles = state.discoveredTiles,
            animTime = (System.currentTimeMillis() % 100000L) / 1000f
        )

        // 2. Calculate danger proximity from living hostiles
        var maxDanger = 0.0f
        for (enemy in state.activeEnemies) {
            if (!enemy.isAlive) continue
            val dist = Math.hypot((enemy.x - px).toDouble(), (enemy.y - py).toDouble()).toFloat()
            val enemyDanger = when (enemy.state) {
                NpcState.AGGRESSIVE -> {
                    // Aggressive enemies create intense tension that scales sharply as they close in
                    (1.0f - (dist / 6.0f)).coerceIn(0.25f, 1.0f)
                }
                NpcState.PATROL -> {
                    // Patrolling enemies create low ambient tension when nearby
                    if (dist < 4.0f) (1.0f - (dist / 4.0f)) * 0.4f else 0.0f
                }
                NpcState.FLEE -> {
                    // Panicked fleeing enemies create jittery low-mid tension
                    if (dist < 3.0f) 0.3f else 0.0f
                }
            }
            if (enemyDanger > maxDanger) {
                maxDanger = enemyDanger
            }
        }

        // If currently in combat modal, lock danger to high
        if (state.activeModal == ActiveModal.COMBAT) {
            maxDanger = maxOf(maxDanger, 0.85f)
        }

        proceduralAudioManager.updateAtmosphere(
            lightingIntensity = tileLighting.totalIntensity,
            dangerLevel = maxDanger,
            toxicity = state.player.toxicity
        )
    }

    fun toggleAudioMute() {
        proceduralAudioManager.toggleMute()
    }

    private fun computeFov(px: Int, py: Int, radius: Int = 6): Set<Pair<Int, Int>> {
        val discovered = mutableSetOf<Pair<Int, Int>>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) {
                    discovered.add(Pair(px + dx, py + dy))
                }
            }
        }
        return discovered
    }

    fun spawnFloatingText(text: String, x: Float, y: Float, colorHex: Long = 0xFF4FD1C5) {
        val ft = FloatingText(
            id = System.nanoTime(),
            text = text,
            x = x,
            y = y,
            colorHex = colorHex
        )
        _uiState.update {
            val now = System.currentTimeMillis()
            val filtered = it.floatingTexts.filter { item -> now - item.spawnTime < item.durationMs }
            it.copy(floatingTexts = filtered + ft)
        }
    }

    fun movePlayerIsometric(dx: Int, dy: Int) {
        val p = _uiState.value.player
        val targetX = p.x + dx
        val targetY = p.y + dy

        // Calculate angle based on dx/dy (8-way directions)
        val angle = when {
            dx == 0 && dy < 0 -> 0f     // North
            dx > 0 && dy < 0 -> 45f    // North-East
            dx > 0 && dy == 0 -> 90f   // East
            dx > 0 && dy > 0 -> 135f   // South-East
            dx == 0 && dy > 0 -> 180f  // South
            dx < 0 && dy > 0 -> 225f   // South-West
            dx < 0 && dy == 0 -> 270f  // West
            dx < 0 && dy < 0 -> 315f   // North-West
            else -> p.angleDegrees
        }

        _uiState.update { it.copy(player = it.player.copy(angleDegrees = angle)) }
        checkAndApplyPosition(targetX, targetY)
    }

    fun handleTileTap(gridX: Int, gridY: Int) {
        val grid = _uiState.value.mapGrid
        if (grid.isEmpty() || gridY !in grid.indices || gridX !in grid[gridY].indices) return

        val p = _uiState.value.player
        val px = p.x.toInt()
        val py = p.y.toInt()

        // Check if tapping an enemy
        val enemy = _uiState.value.activeEnemies.firstOrNull { it.isAlive && it.x.toInt() == gridX && it.y.toInt() == gridY }
        if (enemy != null) {
            _uiState.update {
                it.copy(
                    selectedTile = Pair(gridX, gridY),
                    combatLogs = it.combatLogs + CombatLogEntry("V.A.T.S. Locked: ${enemy.name} at [X:$gridX, Y:$gridY]")
                )
            }

            val dist = Math.hypot((enemy.x - p.x).toDouble(), (enemy.y - p.y).toDouble())
            if (dist < 1.5) {
                engageHostiles(enemy)
            }
            return
        }

        // Check if tapping an interactive object (terminal / locker / switch / beacon)
        val interactive = _uiState.value.interactiveObjects[Pair(gridX, gridY)]
        if (interactive != null) {
            _uiState.update { it.copy(selectedTile = Pair(gridX, gridY)) }
            interactWithObject(interactive)
            return
        }

        // Check if tapping an adjacent tile: Walk there directly
        val dx = gridX - px
        val dy = gridY - py
        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && !(dx == 0 && dy == 0)) {
            movePlayerIsometric(dx, dy)
            _uiState.update { it.copy(selectedTile = Pair(gridX, gridY)) }
            return
        }

        // Otherwise, select the tile for inspection / targeting
        val tile = grid[gridY][gridX]
        _uiState.update {
            it.copy(
                selectedTile = Pair(gridX, gridY),
                combatLogs = it.combatLogs + CombatLogEntry("Inspecting: $tile at [X:$gridX, Y:$gridY]")
            )
        }
    }

    fun movePlayerForward() {
        val p = _uiState.value.player
        val rad = Math.toRadians(p.angleDegrees.toDouble())
        val step = 0.3f
        val newX = p.x + (sin(rad) * step).toFloat()
        val newY = p.y + (cos(rad) * step).toFloat()

        checkAndApplyPosition(newX, newY)
    }

    fun movePlayerBackward() {
        val p = _uiState.value.player
        val rad = Math.toRadians(p.angleDegrees.toDouble())
        val step = 0.3f
        val newX = p.x - (sin(rad) * step).toFloat()
        val newY = p.y - (cos(rad) * step).toFloat()

        checkAndApplyPosition(newX, newY)
    }

    fun rotatePlayer(deltaDegrees: Float) {
        _uiState.update { state ->
            val newAngle = (state.player.angleDegrees + deltaDegrees) % 360f
            state.copy(player = state.player.copy(angleDegrees = newAngle))
        }
    }

    fun waitTurn() {
        val player = _uiState.value.player
        val logs = _uiState.value.combatLogs + CombatLogEntry("Turn skipped: Standing ground (Sensors active)...")
        _uiState.update { it.copy(combatLogs = logs) }
        spawnFloatingText("WAIT TURN", player.x, player.y, 0xFF94A3B8)
        advanceTurnQueue()
    }

    /**
     * Applies a status effect to the player, refreshing duration if already present.
     */
    fun applyStatusEffectToPlayer(effect: StatusEffect) {
        val player = _uiState.value.player
        val existing = player.statusEffects.toMutableList()
        val idx = existing.indexOfFirst { it.type == effect.type }
        if (idx >= 0) {
            existing[idx] = existing[idx].copy(
                durationTurns = Math.max(existing[idx].durationTurns, effect.durationTurns),
                magnitude = Math.max(existing[idx].magnitude, effect.magnitude)
            )
        } else {
            existing.add(effect)
        }
        val updatedPlayer = player.copy(statusEffects = existing)
        val refreshedQueue = buildTurnCombatQueue(updatedPlayer, _uiState.value.activeEnemies)
        val logMsg = if (effect.isDebuff) "⚠️ Afflicted with ${effect.name} (${effect.durationTurns} turns)!" else "✨ Gained ${effect.name} (${effect.durationTurns} turns)!"
        spawnFloatingText("${effect.iconGlyph} ${effect.name.take(10)}", updatedPlayer.x, updatedPlayer.y, effect.type.colorHex)
        _uiState.update {
            it.copy(
                player = updatedPlayer,
                turnQueueState = refreshedQueue,
                combatLogs = it.combatLogs + CombatLogEntry(logMsg, isCritical = effect.isDebuff)
            )
        }
    }

    /**
     * Applies a status effect to an enemy.
     */
    fun applyStatusEffectToEnemy(enemyId: String, effect: StatusEffect) {
        val enemies = _uiState.value.activeEnemies.map { enemy ->
            if (enemy.id == enemyId) {
                val existing = enemy.statusEffects.toMutableList()
                val idx = existing.indexOfFirst { it.type == effect.type }
                if (idx >= 0) {
                    existing[idx] = existing[idx].copy(
                        durationTurns = Math.max(existing[idx].durationTurns, effect.durationTurns),
                        magnitude = Math.max(existing[idx].magnitude, effect.magnitude)
                    )
                } else {
                    existing.add(effect)
                }
                enemy.copy(statusEffects = existing)
            } else enemy
        }

        val target = enemies.firstOrNull { it.id == enemyId }
        target?.let {
            spawnFloatingText("${effect.iconGlyph} ${effect.name.take(8)}", it.x, it.y, effect.type.colorHex)
        }
        val refreshedQueue = buildTurnCombatQueue(_uiState.value.player, enemies)
        val logMsg = "${target?.name ?: "Target"} afflicted with ${effect.name}!"
        _uiState.update {
            it.copy(
                activeEnemies = enemies,
                turnQueueState = refreshedQueue,
                combatLogs = it.combatLogs + CombatLogEntry(logMsg)
            )
        }
    }

    /**
     * Cleanses all negative debuffs from the player.
     */
    fun cleansePlayerStatusEffects() {
        val player = _uiState.value.player
        val purged = player.statusEffects.filter { it.isBuff }
        val updatedPlayer = player.copy(statusEffects = purged)
        val refreshedQueue = buildTurnCombatQueue(updatedPlayer, _uiState.value.activeEnemies)
        spawnFloatingText("PURGED DEBUFFS", updatedPlayer.x, updatedPlayer.y, 0xFF4FD1C5)
        _uiState.update {
            it.copy(
                player = updatedPlayer,
                turnQueueState = refreshedQueue,
                combatLogs = it.combatLogs + CombatLogEntry("🧪 Med-Gel applied: Cleansed Poison, Radiation, and Corrosion debuffs.")
            )
        }
    }

    /**
     * Processes turn-based status effects for a given combatant.
     * Returns true if the entity can act, false if stunned.
     */
    private fun processCombatantTurnStatusEffects(combatantId: String): Boolean {
        val state = _uiState.value
        val logs = state.combatLogs.toMutableList()

        if (combatantId == "player") {
            var player = state.player
            if (player.statusEffects.isEmpty()) return true

            var canAct = true
            val remainingEffects = mutableListOf<StatusEffect>()

            for (effect in player.statusEffects) {
                when (effect.type) {
                    StatusEffectType.POISON -> {
                        val newHp = (player.hp - effect.magnitude).coerceAtLeast(0)
                        player = player.copy(hp = newHp)
                        logs.add(CombatLogEntry("☣ Poison Sludge dealt ${effect.magnitude} damage! (HP: $newHp)", isCritical = true))
                        spawnFloatingText("-${effect.magnitude} POISON", player.x, player.y, 0xFF10B981)
                    }
                    StatusEffectType.RADIATION -> {
                        val newTox = (player.toxicity + effect.magnitude).coerceAtMost(100)
                        val newHp = (player.hp - (effect.magnitude / 2).coerceAtLeast(1)).coerceAtLeast(0)
                        player = player.copy(toxicity = newTox, hp = newHp)
                        logs.add(CombatLogEntry("☢ Radiation Sickness: +${effect.magnitude}% Toxicity, -${effect.magnitude / 2} HP!", isCritical = true))
                        spawnFloatingText("+${effect.magnitude}% RADS", player.x, player.y, 0xFFFFB020)
                    }
                    StatusEffectType.STUN -> {
                        canAct = false
                        logs.add(CombatLogEntry("⚡ EMP Stun active! Turn action skipped.", isCritical = true))
                        spawnFloatingText("⚡ STUNNED", player.x, player.y, 0xFFF59E0B)
                    }
                    StatusEffectType.REGENERATION -> {
                        val newHp = (player.hp + effect.magnitude).coerceAtMost(player.maxHp)
                        player = player.copy(hp = newHp)
                        logs.add(CombatLogEntry("💖 Nano-Regen restored ${effect.magnitude} HP (HP: $newHp)"))
                        spawnFloatingText("+${effect.magnitude} REGEN", player.x, player.y, 0xFF4ADE80)
                    }
                    StatusEffectType.ADRENALINE -> {
                        logs.add(CombatLogEntry("💉 Adrenaline Surge active: +${effect.magnitude} ATK"))
                    }
                    StatusEffectType.CORROSION -> {
                        logs.add(CombatLogEntry("🧪 Acid Corrosion active: -${effect.magnitude} Armor DEF"))
                    }
                }

                val nextDuration = effect.durationTurns - 1
                if (nextDuration > 0) {
                    remainingEffects.add(effect.copy(durationTurns = nextDuration))
                } else {
                    logs.add(CombatLogEntry("✨ ${effect.name} effect expired."))
                }
            }

            val isGameOver = player.hp <= 0 || player.toxicity >= 100
            val updatedPlayer = player.copy(statusEffects = remainingEffects)
            val refreshedQueue = buildTurnCombatQueue(updatedPlayer, state.activeEnemies)

            _uiState.update {
                it.copy(
                    player = updatedPlayer,
                    combatLogs = logs,
                    isGameOver = isGameOver,
                    turnQueueState = refreshedQueue
                )
            }
            return canAct
        } else {
            // Processing for Enemy
            val enemy = state.activeEnemies.firstOrNull { it.id == combatantId && it.isAlive } ?: return true
            if (enemy.statusEffects.isEmpty()) return true

            var canAct = true
            val remainingEffects = mutableListOf<StatusEffect>()
            var enemyHp = enemy.hp

            for (effect in enemy.statusEffects) {
                when (effect.type) {
                    StatusEffectType.POISON -> {
                        enemyHp = (enemyHp - effect.magnitude).coerceAtLeast(0)
                        logs.add(CombatLogEntry("☣ ${enemy.name} suffers ${effect.magnitude} Poison damage! (HP: $enemyHp)"))
                        spawnFloatingText("-${effect.magnitude} POISON", enemy.x, enemy.y, 0xFF10B981)
                    }
                    StatusEffectType.RADIATION -> {
                        enemyHp = (enemyHp - effect.magnitude).coerceAtLeast(0)
                        logs.add(CombatLogEntry("☢ Radiation decay burns ${enemy.name} for ${effect.magnitude} DMG!"))
                        spawnFloatingText("-${effect.magnitude} RADS", enemy.x, enemy.y, 0xFFFFB020)
                    }
                    StatusEffectType.STUN -> {
                        canAct = false
                        logs.add(CombatLogEntry("⚡ ${enemy.name} is Stunned! Turn skipped."))
                        spawnFloatingText("⚡ STUNNED", enemy.x, enemy.y, 0xFFF59E0B)
                    }
                    StatusEffectType.REGENERATION -> {
                        enemyHp = (enemyHp + effect.magnitude).coerceAtMost(enemy.maxHp)
                        logs.add(CombatLogEntry("💖 ${enemy.name} regenerated ${effect.magnitude} HP."))
                    }
                    else -> {}
                }

                val nextDuration = effect.durationTurns - 1
                if (nextDuration > 0) {
                    remainingEffects.add(effect.copy(durationTurns = nextDuration))
                } else {
                    logs.add(CombatLogEntry("✨ ${effect.name} on ${enemy.name} expired."))
                }
            }

            val updatedEnemy = enemy.copy(
                hp = enemyHp,
                statusEffects = remainingEffects,
                isAlive = enemyHp > 0
            )

            val updatedEnemies = state.activeEnemies.map { if (it.id == enemy.id) updatedEnemy else it }
            val refreshedQueue = buildTurnCombatQueue(state.player, updatedEnemies)

            _uiState.update {
                it.copy(
                    activeEnemies = updatedEnemies,
                    combatLogs = logs,
                    turnQueueState = refreshedQueue
                )
            }
            return canAct && updatedEnemy.isAlive
        }
    }

    /**
     * Builds and sorts the turn queue according to combatant initiative values,
     * taking active status effects into calculation.
     */
    fun buildTurnCombatQueue(
        player: Player,
        enemies: List<Enemy>,
        round: Int = _uiState.value.turnQueueState.roundNumber.coerceAtLeast(1),
        currentActiveId: String? = null
    ): TurnCombatQueueState {
        val stats = _uiState.value.gearStats
        val chipBonus = stats.activeChip?.damage ?: 0

        // Calculate Player Status Modifiers
        val playerAdrenaline = player.statusEffects.filter { it.type == StatusEffectType.ADRENALINE }.sumOf { it.magnitude }
        val playerCorrosion = player.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
        val playerStunPenalty = if (player.statusEffects.any { it.type == StatusEffectType.STUN }) 10 else 0

        val playerInitiative = (15 + chipBonus + (player.level * 2) - playerStunPenalty).coerceAtLeast(1)
        val playerEffectiveAtk = player.attackPower + playerAdrenaline
        val playerEffectiveDef = (player.defense + stats.totalArmorDefense - playerCorrosion).coerceAtLeast(0)

        val playerCombatant = CombatQueueEntity(
            id = "player",
            name = player.name.ifBlank { "Scythe-01" },
            glyph = "@",
            type = CombatantType.PLAYER,
            hp = player.hp,
            maxHp = player.maxHp,
            initiative = playerInitiative,
            stateLabel = "PLAYER",
            isCurrentTurn = false,
            isAlive = player.hp > 0,
            actionPoints = 2,
            maxActionPoints = 2,
            attackPower = playerEffectiveAtk,
            defense = playerEffectiveDef,
            gridX = player.x,
            gridY = player.y,
            statusEffects = player.statusEffects
        )

        val enemyCombatants = enemies
            .filter { it.isAlive }
            .map { enemy ->
                val baseSpeed = when {
                    enemy.id.contains("drone", ignoreCase = true) -> 18
                    enemy.id.contains("boss", ignoreCase = true) -> 14
                    enemy.id.contains("mutant", ignoreCase = true) -> 10 + (enemy.attack / 2)
                    else -> 12
                }

                val enemyCorrosion = enemy.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
                val enemyStunPenalty = if (enemy.statusEffects.any { it.type == StatusEffectType.STUN }) 10 else 0
                val effectiveSpeed = (baseSpeed - enemyStunPenalty).coerceAtLeast(1)
                val effectiveDefense = (enemy.armor - enemyCorrosion).coerceAtLeast(0)

                CombatQueueEntity(
                    id = enemy.id,
                    name = enemy.name,
                    glyph = enemy.asciiGlyph.toString(),
                    type = CombatantType.ENEMY,
                    hp = enemy.hp,
                    maxHp = enemy.maxHp,
                    initiative = effectiveSpeed,
                    stateLabel = enemy.state.label,
                    isCurrentTurn = false,
                    isAlive = enemy.isAlive,
                    actionPoints = 1,
                    maxActionPoints = 1,
                    attackPower = enemy.attack,
                    defense = effectiveDefense,
                    gridX = enemy.x,
                    gridY = enemy.y,
                    statusEffects = enemy.statusEffects
                )
            }

        val allSorted = (listOf(playerCombatant) + enemyCombatants).sortedByDescending { it.initiative }
        val activeId = currentActiveId ?: allSorted.firstOrNull()?.id ?: "player"
        val activeIdx = allSorted.indexOfFirst { it.id == activeId }.coerceAtLeast(0)

        val finalizedList = allSorted.map { it.copy(isCurrentTurn = it.id == activeId) }
        val isEngaged = enemies.any {
            it.isAlive && (it.state == NpcState.AGGRESSIVE || Math.hypot((it.x - player.x).toDouble(), (it.y - player.y).toDouble()) <= 5.5)
        }

        return TurnCombatQueueState(
            roundNumber = round,
            currentTurnIndex = activeIdx,
            combatants = finalizedList,
            isCombatActive = isEngaged || _uiState.value.activeModal == ActiveModal.COMBAT,
            activeCombatantId = activeId,
            turnPhase = if (activeId == "player") TurnPhase.PLAYER_INPUT else TurnPhase.NPC_ACTION
        )
    }

    /**
     * Cycles through turn queue in initiative order, triggering AI for NPCs or waiting for player input.
     */
    fun advanceTurnQueue() {
        var state = _uiState.value
        var currentQueue = state.turnQueueState
        if (currentQueue.combatants.isEmpty()) {
            val refreshed = buildTurnCombatQueue(state.player, state.activeEnemies)
            _uiState.update { it.copy(turnQueueState = refreshed) }
            return
        }

        var processedCount = 0
        while (processedCount < currentQueue.combatants.size) {
            val nextIndex = (currentQueue.currentTurnIndex + 1) % currentQueue.combatants.size
            val nextRound = if (nextIndex == 0) currentQueue.roundNumber + 1 else currentQueue.roundNumber
            val nextCombatant = currentQueue.combatants.getOrNull(nextIndex) ?: return

            val updatedCombatants = currentQueue.combatants.map {
                it.copy(isCurrentTurn = it.id == nextCombatant.id)
            }

            val nextQueueState = currentQueue.copy(
                roundNumber = nextRound,
                currentTurnIndex = nextIndex,
                combatants = updatedCombatants,
                activeCombatantId = nextCombatant.id,
                turnPhase = if (nextCombatant.isPlayer) TurnPhase.PLAYER_INPUT else TurnPhase.NPC_ACTION
            )

            _uiState.update { it.copy(turnQueueState = nextQueueState) }

            val canAct = processCombatantTurnStatusEffects(nextCombatant.id)
            state = _uiState.value
            currentQueue = state.turnQueueState

            if (!canAct) {
                processedCount++
                continue
            }

            if (!nextCombatant.isPlayer) {
                executeNpcCombatantTurn(nextCombatant.id)
                return
            } else {
                val logs = state.combatLogs + CombatLogEntry("⚡ Round $nextRound: Scythe-01 ready. (Your Turn)")
                _uiState.update { it.copy(combatLogs = logs) }
                return
            }
        }
        val refreshed = buildTurnCombatQueue(state.player, state.activeEnemies, currentQueue.roundNumber + 1)
        _uiState.update { it.copy(turnQueueState = refreshed) }
    }

    private fun executeNpcCombatantTurn(enemyId: String) {
        val state = _uiState.value
        val enemy = state.activeEnemies.firstOrNull { it.id == enemyId && it.isAlive }
        if (enemy == null) {
            val refreshed = buildTurnCombatQueue(state.player, state.activeEnemies, state.turnQueueState.roundNumber)
            _uiState.update { it.copy(turnQueueState = refreshed) }
            return
        }

        val turnResult = npcStateMachineEngine.processNpcTurn(
            npc = enemy,
            player = state.player,
            mapGrid = state.mapGrid,
            allNpcs = state.activeEnemies
        )

        val updatedEnemies = state.activeEnemies.map {
            if (it.id == enemyId) turnResult.updatedNpc else it
        }

        val logs = state.combatLogs.toMutableList()
        turnResult.stateChangeLog?.let { logs.add(CombatLogEntry(it)) }
        turnResult.combatLog?.let { logs.add(CombatLogEntry(it, isCritical = true)) }
        turnResult.floatingText?.let { txt ->
            spawnFloatingText(txt, turnResult.updatedNpc.x, turnResult.updatedNpc.y, turnResult.floatingTextColor)
        }

        var player = state.player
        if (turnResult.damageDealtToPlayer > 0) {
            val stats = state.gearStats
            val corrosionDefLoss = player.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
            val armorDef = if (stats.totalArmorDefense > 0) stats.totalArmorDefense else (player.equippedArmor?.defense ?: 0)
            val chipDef = stats.activeChip?.defense ?: 0
            val totalDef = ((armorDef + chipDef) / 2 - corrosionDefLoss).coerceAtLeast(0)
            val netDmg = (turnResult.damageDealtToPlayer - totalDef).coerceAtLeast(1)

            val newHp = (player.hp - netDmg).coerceAtLeast(0)
            val newTox = (player.toxicity + turnResult.toxicityDealtToPlayer).coerceAtMost(100)
            player = player.copy(hp = newHp, toxicity = newTox)

            // 20% Chance for mutant/toxic enemies to inflict poison on player
            if (Math.random() < 0.25 && (enemy.toxicityDamage > 0 || enemy.id.contains("mutant", ignoreCase = true))) {
                val poisonEffect = StatusEffect(type = StatusEffectType.POISON, durationTurns = 3, magnitude = 5)
                val existing = player.statusEffects.toMutableList()
                if (existing.none { it.type == StatusEffectType.POISON }) {
                    existing.add(poisonEffect)
                    player = player.copy(statusEffects = existing)
                    logs.add(CombatLogEntry("☣ Struck by toxic venom! Afflicted with Poison!", isCritical = true))
                    spawnFloatingText("☣ POISONED", player.x, player.y, 0xFF10B981)
                }
            }
        }

        val isGameOver = player.hp <= 0 || player.toxicity >= 100
        val refreshedQueue = buildTurnCombatQueue(player, updatedEnemies, state.turnQueueState.roundNumber, currentActiveId = enemyId)

        _uiState.update {
            it.copy(
                player = player,
                activeEnemies = updatedEnemies,
                combatLogs = logs,
                isGameOver = isGameOver,
                turnQueueState = refreshedQueue
            )
        }
        refreshAudioAtmosphere()
    }

    private fun checkAndApplyPosition(newX: Float, newY: Float) {
        val grid = _uiState.value.mapGrid
        if (grid.isEmpty()) return

        val tileC = newX.toInt()
        val tileR = newY.toInt()

        if (tileR in grid.indices && tileC in grid[tileR].indices) {
            val tile = grid[tileR][tileC]
            if (tile != TileType.WALL) {
                // Weight-based inventory: over-encumbered players are slowed by labored movement
                if (_uiState.value.isEncumbered && Math.random() < 0.35f) {
                    val encLogs = _uiState.value.combatLogs + CombatLogEntry("OVER-ENCUMBERED: movement labored (drop gear to move freely)!", isCritical = true)
                    spawnFloatingText("ENCUMBERED", newX, newY, 0xFFFF4150)
                    _uiState.update { it.copy(combatLogs = encLogs) }
                    return
                }

                var tox = _uiState.value.player.toxicity
                val logs = _uiState.value.combatLogs.toMutableList()

                val newDiscovered = _uiState.value.discoveredTiles + computeFov(tileC, tileR)

                if (tile == TileType.TOXIC_POOL) {
                    tox = (tox + 4).coerceAtMost(100)
                    logs.add(CombatLogEntry("Stepped in Toxic Sludge! Toxicity: $tox%"))
                    spawnFloatingText("+4% TOX", newX, newY, 0xFFFF4150)
                }

                val updatedPlayer = _uiState.value.player.copy(x = newX, y = newY, toxicity = tox)
                val refreshedQueue = buildTurnCombatQueue(updatedPlayer, _uiState.value.activeEnemies)
                val refreshedQuests = updateQuestsProgress(_uiState.value.copy(player = updatedPlayer))

                _uiState.update { state ->
                    state.copy(
                        player = updatedPlayer,
                        discoveredTiles = newDiscovered,
                        combatLogs = logs,
                        turnQueueState = refreshedQueue,
                        quests = refreshedQuests
                    )
                }

                // Advance NPC State Machine Turn after player movement
                updateAllNpcsAi()

                // Check Enemy Encounter
                checkEnemyProximity(newX, newY)

                // Check Extraction
                if (tile == TileType.EXTRACTION_LIFT) {
                    val finalQuests = updateQuestsProgress(_uiState.value.copy(isVictory = true))
                    _uiState.update { it.copy(isVictory = true, quests = finalQuests) }
                }
            }
        }
    }

    fun updateAllNpcsAi() {
        val state = _uiState.value
        if (state.isGameOver || state.isVictory || state.mapGrid.isEmpty()) return

        val currentEnemies = state.activeEnemies
        var player = state.player
        val updatedEnemies = mutableListOf<Enemy>()
        val logs = state.combatLogs.toMutableList()
        val stats = state.gearStats

        for (enemy in currentEnemies) {
            if (!enemy.isAlive) {
                updatedEnemies.add(enemy)
                continue
            }

            val turnResult = npcStateMachineEngine.processNpcTurn(
                npc = enemy,
                player = player,
                mapGrid = state.mapGrid,
                allNpcs = currentEnemies
            )

            updatedEnemies.add(turnResult.updatedNpc)

            turnResult.stateChangeLog?.let { msg ->
                logs.add(CombatLogEntry(msg))
            }

            turnResult.combatLog?.let { msg ->
                logs.add(CombatLogEntry(msg, isCritical = true))
            }

            turnResult.floatingText?.let { txt ->
                spawnFloatingText(txt, turnResult.updatedNpc.x, turnResult.updatedNpc.y, turnResult.floatingTextColor)
            }

            if (turnResult.damageDealtToPlayer > 0) {
                val armorDef = if (stats.totalArmorDefense > 0) stats.totalArmorDefense else (player.equippedArmor?.defense ?: 0)
                val chipDef = stats.activeChip?.defense ?: 0
                val corrosionDefLoss = player.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
                val totalDef = ((armorDef + chipDef) / 2 - corrosionDefLoss).coerceAtLeast(0)
            val netDmg = ((turnResult.damageDealtToPlayer - totalDef) * if (hasPerk("tough_skin")) 0.9f else 1f).toInt().coerceAtLeast(1)

                val newHp = (player.hp - netDmg).coerceAtLeast(0)
                val newTox = (player.toxicity + turnResult.toxicityDealtToPlayer).coerceAtMost(100)
                player = player.copy(hp = newHp, toxicity = newTox)
                triggerScreenShake(12f)
            }
        }

        val isGameOver = player.hp <= 0 || player.toxicity >= 100
        val refreshedQueue = buildTurnCombatQueue(player, updatedEnemies, state.turnQueueState.roundNumber)

        _uiState.update {
            it.copy(
                player = player,
                activeEnemies = updatedEnemies,
                combatLogs = logs,
                isGameOver = isGameOver,
                turnQueueState = refreshedQueue
            )
        }
        refreshAudioAtmosphere()
    }

    private fun checkEnemyProximity(px: Float, py: Float) {
        if (_uiState.value.activeModal == ActiveModal.COMBAT) return
        val enemies = _uiState.value.activeEnemies
        val nearEnemy = enemies.filter { enemy ->
            enemy.isAlive && Math.hypot((enemy.x - px).toDouble(), (enemy.y - py).toDouble()) < 4.0
        }.minByOrNull { enemy -> Math.hypot((enemy.x - px).toDouble(), (enemy.y - py).toDouble()) }

        if (nearEnemy != null) {
            engageHostiles(nearEnemy)
        }
    }

    fun attackCombatEnemy() {
        val enemy = _uiState.value.activeCombatEnemy ?: return
        val player = _uiState.value.player
        val stats = _uiState.value.gearStats
        val skills = _uiState.value.skills

        val gunsBonus = skills[SkillType.GUNS] ?: 0
        val meleeBonus = skills[SkillType.MELEE] ?: 0
        val weaponMasteryMult = if (hasPerk("weapon_mastery")) 1.15f else 1f
        val bladeDancerMeleeMult = if (hasPerk("blade_dancer")) 1.15f else 1f
        val perkCrit = if (hasPerk("blade_dancer")) 0.05f else 0f

        val adrenalineBonus = player.statusEffects.filter { it.type == StatusEffectType.ADRENALINE }.sumOf { it.magnitude }
        val weaponDmg = (((if (stats.totalWeaponDamage > 0) stats.totalWeaponDamage else (player.equippedWeapon?.damage ?: 10)) + gunsBonus * 2) * weaponMasteryMult).toInt()
        val chipDmg = stats.activeChip?.damage ?: 0
        val critBonus = (stats.activeChip?.criticalBonus ?: 0f) + perkCrit

        val equippedWeapon = stats.activeWeapon
        val weaponBroken = equippedWeapon?.isBroken == true
        val logs = _uiState.value.combatLogs.toMutableList()

        // Broken weapon has a chance to jam, wasting the attack entirely
        if (weaponBroken && Math.random() < 0.5f) {
            logs.add(CombatLogEntry("⚠️ WEAPON JAMMED! ${equippedWeapon?.name ?: "Weapon"} is broken and failed to fire.", isCritical = true))
            spawnFloatingText("JAMMED!", enemy.x, enemy.y, 0xFFFF4150)
            triggerScreenShake(6f)
            _uiState.update {
                it.copy(
                    combatLogs = logs,
                    turnQueueState = buildTurnCombatQueue(player, it.activeEnemies)
                )
            }
            return
        }

        val isCrit = Math.random() < (0.15 + critBonus)
        val isMiss = !weaponBroken && Math.random() < 0.10
        val enemyCorrosion = enemy.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
        val effectiveEnemyArmor = (enemy.armor - enemyCorrosion).coerceAtLeast(0)

        val companionBonus = _uiState.value.companions.sumOf { it.attack }
        val baseDmg = (player.attackPower + adrenalineBonus + (meleeBonus * 2 * bladeDancerMeleeMult).toInt() + weaponDmg + chipDmg + companionBonus - effectiveEnemyArmor).coerceAtLeast(3)
        // Broken weapons deal reduced damage (in addition to the jam chance above)
        val brokenMult = if (weaponBroken) 0.5f else 1f
        val totalDmg = if (isMiss) 0 else ((if (isCrit) (baseDmg * 1.5f) else baseDmg.toFloat()) * brokenMult).toInt()

        val newEnemyHp = (enemy.hp - totalDmg).coerceAtLeast(0)
        val hitMsg = when {
            isMiss -> "Attack against ${enemy.name} MISSED!"
            isCrit -> "CRITICAL STRIKE on ${enemy.name} for $totalDmg damage!"
            else -> "Attacked ${enemy.name} for $totalDmg damage!"
        }
        logs.add(CombatLogEntry(hitMsg, isCritical = isCrit, isMiss = isMiss))
        if (isMiss) {
            spawnFloatingText("MISS", enemy.x, enemy.y, 0xFF94A3B8)
        } else {
            spawnFloatingText(if (isCrit) "-$totalDmg CRIT!" else "-$totalDmg", enemy.x, enemy.y, 0xFFFF4150)
            triggerScreenShake(if (isCrit) 14f else 8f)
        }

        // Weapon durability degradation on a successful strike
        if (!isMiss) {
            viewModelScope.launch {
                inventoryRepository.degradeEquippedWeapon(1)
            }
        }

        // Status effect chance on strike (only on a successful hit)
        val enemyEffects = enemy.statusEffects.toMutableList()
        if (!isMiss) {
            if (isCrit && Math.random() < 0.6) {
                enemyEffects.add(StatusEffect(type = StatusEffectType.STUN, durationTurns = 1, magnitude = 1))
                logs.add(CombatLogEntry("⚡ Critical Shock stunned ${enemy.name}!", isCritical = true))
                spawnFloatingText("⚡ STUNNED", enemy.x, enemy.y, 0xFFF59E0B)
            } else if (Math.random() < 0.35) {
                val roll = Math.random()
                if (roll < 0.5) {
                    enemyEffects.add(StatusEffect(type = StatusEffectType.CORROSION, durationTurns = 3, magnitude = 5))
                    logs.add(CombatLogEntry("🧪 Acid Splash corroded ${enemy.name}'s armor (-5 DEF)!"))
                    spawnFloatingText("🧪 CORROSION", enemy.x, enemy.y, 0xFFF97316)
                } else {
                    enemyEffects.add(StatusEffect(type = StatusEffectType.POISON, durationTurns = 3, magnitude = 6))
                    logs.add(CombatLogEntry("☣ Bio-Dart poisoned ${enemy.name} (6 DMG/turn)!"))
                    spawnFloatingText("☣ POISON", enemy.x, enemy.y, 0xFF10B981)
                }
            }
        }

        if (newEnemyHp == 0) {
            val updatedEnemy = enemy.copy(
                hp = newEnemyHp,
                statusEffects = enemyEffects,
                isAlive = newEnemyHp > 0,
                state = enemy.state
            )
            val updatedEnemies = _uiState.value.activeEnemies.map { if (it.id == enemy.id) updatedEnemy else it }
            val remainingEnemies = _uiState.value.activeEnemies.filter { it.id != enemy.id && it.isAlive }
            val refreshedQueue = buildTurnCombatQueue(player, remainingEnemies)

            logs.add(CombatLogEntry("Defeated ${enemy.name}! +${enemy.expReward} EXP!"))
            spawnFloatingText("+${enemy.expReward} EXP", enemy.x, enemy.y, 0xFF4FD1C5)

            var inv = _uiState.value.inventory.toMutableList()
            var lootObtained = false
            if (enemy.lootItemId != null) {
                val lootItem = parsedData?.items?.get(enemy.lootItemId)
                if (lootItem != null) {
                    inv.add(lootItem)
                    lootObtained = true
                    logs.add(CombatLogEntry("Looted item: ${lootItem.name}! (Saved to Room DB)", isHeal = true))
                    spawnFloatingText("LOOT: ${lootItem.name}", enemy.x, enemy.y, 0xFFFFD700)

                    viewModelScope.launch {
                        inventoryRepository.addItemFromDomain(
                            domainItem = lootItem,
                            rarity = ItemRarity.RARE,
                            weightKg = 1.5f
                        )
                    }
                }
            }

            val newKillCount = _uiState.value.killCount + 1
            val newItemsCollected = _uiState.value.itemsCollected + if (lootObtained) 1 else 0
            val levelUpMsg = grantExp(enemy.expReward)
            if (levelUpMsg.isNotBlank()) logs.add(CombatLogEntry(levelUpMsg, isHeal = true))
            val postPlayer = _uiState.value.player
            val creditsReward = if (hasPerk("scavenger")) 25 else 20
            val updatedPlayer = postPlayer.copy(credits = postPlayer.credits + creditsReward)
            val refreshedQuests = updateQuestsProgress(
                _uiState.value.copy(killCount = newKillCount, itemsCollected = newItemsCollected, player = updatedPlayer)
            )

            val combatEnded = remainingEnemies.isEmpty()
            val nextTarget = remainingEnemies.firstOrNull()
            val nextModal = when {
                it.pendingPerkChoices.isNotEmpty() -> ActiveModal.PERK_SELECT
                combatEnded -> ActiveModal.NONE
                else -> ActiveModal.COMBAT
            }

            _uiState.update {
                it.copy(
                    activeCombatEnemy = nextTarget,
                    activeModal = nextModal,
                    inventory = inv,
                    combatLogs = logs,
                    activeEnemies = updatedEnemies,
                    turnQueueState = refreshedQueue,
                    killCount = newKillCount,
                    itemsCollected = newItemsCollected,
                    quests = refreshedQuests,
                    player = updatedPlayer
                )
            }
        } else {
            val hpRatio = newEnemyHp.toFloat() / enemy.maxHp.toFloat()
            val updatedEnemy = enemy.copy(
                hp = newEnemyHp,
                statusEffects = enemyEffects,
                isAlive = newEnemyHp > 0,
                state = if (hpRatio <= enemy.fleeThreshold) NpcState.FLEE else NpcState.AGGRESSIVE
            )

            if (hpRatio <= enemy.fleeThreshold) {
                logs.add(CombatLogEntry("⚠️ [MORALE BROKEN] ${enemy.name} panicked (HP: $newEnemyHp/${enemy.maxHp})! Fleeing!"))
                spawnFloatingText("💨 FLEEING!", enemy.x, enemy.y, 0xFFFFD700)
            } else {
                logs.add(CombatLogEntry("🚨 ${enemy.name} is enraged (AGGRESSIVE)!"))
            }

            // Enemy Counter-Attack with Room Armor Defense & Status Corrosion Calculation
            val playerCorrosion = player.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
            val armorDef = if (stats.totalArmorDefense > 0) stats.totalArmorDefense else (player.equippedArmor?.defense ?: 0)
            val chipDef = stats.activeChip?.defense ?: 0
            val totalDef = (player.defense + armorDef + chipDef - playerCorrosion).coerceAtLeast(0)

            val enemyDmg = ((enemy.attack - totalDef) * if (hasPerk("tough_skin")) 0.9f else 1f).toInt().coerceAtLeast(1)
            val newPlayerHp = (player.hp - enemyDmg).coerceAtLeast(0)
            val newTox = (player.toxicity + enemy.toxicityDamage).coerceAtMost(100)

            logs.add(CombatLogEntry("${enemy.name} counter-attacked for $enemyDmg damage (Armor blocked ${totalDef} DMG)!"))

            // Armor durability degradation on impact
            triggerScreenShake(10f)
            viewModelScope.launch {
                inventoryRepository.degradeEquippedArmor(1)
            }

            // Enemy status application chance
            var updatedPlayer = player.copy(hp = newPlayerHp, toxicity = newTox)
            if (Math.random() < 0.3) {
                if (enemy.toxicityDamage > 0) {
                    val radEffect = StatusEffect(type = StatusEffectType.RADIATION, durationTurns = 3, magnitude = 4)
                    val pEffects = updatedPlayer.statusEffects.toMutableList()
                    pEffects.add(radEffect)
                    updatedPlayer = updatedPlayer.copy(statusEffects = pEffects)
                    logs.add(CombatLogEntry("☢ Irradiated by enemy toxic cloud! (+4% RADS/turn)", isCritical = true))
                    spawnFloatingText("☢ IRRADIATED", updatedPlayer.x, updatedPlayer.y, 0xFFFFB020)
                }
            }

            val updatedEnemies = _uiState.value.activeEnemies.map { if (it.id == enemy.id) updatedEnemy else it }
            val refreshedQueue = buildTurnCombatQueue(updatedPlayer, updatedEnemies)

            if (newPlayerHp == 0 || newTox >= 100) {
                _uiState.update { it.copy(isGameOver = true) }
            } else {
                _uiState.update {
                    it.copy(
                        activeCombatEnemy = updatedEnemy,
                        activeEnemies = updatedEnemies,
                        player = updatedPlayer,
                        combatLogs = logs,
                        turnQueueState = refreshedQueue
                    )
                }
            }
        }
    }

    fun useCombatChemEffect(type: StatusEffectType) {
        val player = _uiState.value.player
        val enemy = _uiState.value.activeCombatEnemy
        val logs = _uiState.value.combatLogs.toMutableList()

        when (type) {
            StatusEffectType.ADRENALINE -> {
                applyStatusEffectToPlayer(StatusEffect(type = StatusEffectType.ADRENALINE, durationTurns = 3, magnitude = 8))
            }
            StatusEffectType.REGENERATION -> {
                applyStatusEffectToPlayer(StatusEffect(type = StatusEffectType.REGENERATION, durationTurns = 4, magnitude = 7))
            }
            StatusEffectType.STUN -> {
                if (enemy != null) {
                    applyStatusEffectToEnemy(enemy.id, StatusEffect(type = StatusEffectType.STUN, durationTurns = 1, magnitude = 1))
                }
            }
            StatusEffectType.CORROSION -> {
                if (enemy != null) {
                    applyStatusEffectToEnemy(enemy.id, StatusEffect(type = StatusEffectType.CORROSION, durationTurns = 3, magnitude = 6))
                }
            }
            StatusEffectType.POISON -> {
                if (enemy != null) {
                    applyStatusEffectToEnemy(enemy.id, StatusEffect(type = StatusEffectType.POISON, durationTurns = 4, magnitude = 6))
                    logs.add(CombatLogEntry("☣ Fired Neurotoxin Dart: ${enemy.name} Poisoned (6 DMG/turn)!"))
                }
            }
            StatusEffectType.RADIATION -> {
                cleansePlayerStatusEffects()
                return
            }
        }
        _uiState.update { it.copy(combatLogs = logs) }
        advanceTurnQueue()
    }

    fun defendCombatTurn() {
        val player = _uiState.value.player
        val logs = _uiState.value.combatLogs + CombatLogEntry("Defensive Stance: Reinforced shielding (+50% DEF this turn).")
        spawnFloatingText("DEFENSE UP +50%", player.x, player.y, 0xFF4FD1C5)
        _uiState.update { it.copy(combatLogs = logs) }
        applyStatusEffectToPlayer(StatusEffect(type = StatusEffectType.ADRENALINE, durationTurns = 1, magnitude = 5))
        advanceTurnQueue()
    }

    fun useCombatStimpack() {
        val player = _uiState.value.player
        val medicineBonus = _uiState.value.skills[SkillType.MEDICINE] ?: 0
        val stimAmount = 35 + medicineBonus * 3 + if (hasPerk("field_medic")) 20 else 0
        val newHp = (player.hp + stimAmount).coerceAtMost(player.maxHp)
        val newTox = (player.toxicity - 15).coerceAtLeast(0)
        val logs = _uiState.value.combatLogs + CombatLogEntry("Quick Stimpack applied: +$stimAmount HP, -15% Toxicity purged.", isHeal = true)
        spawnFloatingText("+$stimAmount HP", player.x, player.y, 0xFF4FD1C5)
        spawnFloatingText("-15% TOX", player.x, player.y, 0xFF38BDF8)
        _uiState.update {
            it.copy(
                player = player.copy(hp = newHp, toxicity = newTox),
                combatLogs = logs
            )
        }
        advanceTurnQueue()
    }

    fun fleeCombat() {
        val logs = _uiState.value.combatLogs + CombatLogEntry("Tactical retreat: Disengaged hostile combat encounter.")
        spawnFloatingText("DISENGAGED", _uiState.value.player.x, _uiState.value.player.y, 0xFFFFD700)
        _uiState.update {
            it.copy(
                activeModal = ActiveModal.NONE,
                activeCombatEnemy = null,
                combatLogs = logs
            )
        }
    }

    /** Switch the focused combat target to another living enemy in the active encounter. */
    fun selectCombatTarget(enemyId: String) {
        val enemy = _uiState.value.activeEnemies.firstOrNull { it.id == enemyId && it.isAlive } ?: return
        _uiState.update { it.copy(activeCombatEnemy = enemy) }
    }

    /** Engage every nearby hostile at once, focusing `target` (falls back to the nearest). */
    private fun engageHostiles(target: Enemy) {
        val player = _uiState.value.player
        val all = _uiState.value.activeEnemies.filter { it.isAlive }
        val near = all.filter { Math.hypot((it.x - player.x).toDouble(), (it.y - player.y).toDouble()) < 4.0 }
        val engaged = if (near.isNotEmpty()) near else all
        val finalTarget = if (engaged.any { it.id == target.id }) {
            target
        } else {
            engaged.minByOrNull { Math.hypot((it.x - player.x).toDouble(), (it.y - player.y).toDouble()) } ?: target
        }
        val queue = buildTurnCombatQueue(
            player,
            engaged,
            round = _uiState.value.turnQueueState.roundNumber,
            currentActiveId = "player"
        )
        _uiState.update {
            it.copy(
                activeModal = ActiveModal.COMBAT,
                activeCombatEnemy = finalTarget,
                turnQueueState = queue.copy(isCombatActive = true),
                combatLogs = it.combatLogs + CombatLogEntry("Engaged ${engaged.size} hostile(s): ${finalTarget.name}!")
            )
        }
    }

    fun equipInventoryItem(itemId: String) {
        viewModelScope.launch {
            val item = _uiState.value.roomInventory.firstOrNull { it.itemId == itemId }
            val success = inventoryRepository.equipItem(itemId)
            if (success && item != null) {
                val logMsg = "Equipped ${item.name} into ${item.equipSlot} slot."
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText("EQUIPPED: ${item.name}", _uiState.value.player.x, _uiState.value.player.y, 0xFF4FD1C5)
            }
        }
    }

    fun unequipInventoryItem(itemId: String) {
        viewModelScope.launch {
            val item = _uiState.value.roomInventory.firstOrNull { it.itemId == itemId }
            inventoryRepository.unequipItem(itemId)
            if (item != null) {
                val logMsg = "Unequipped ${item.name}."
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText("UNEQUIPPED", _uiState.value.player.x, _uiState.value.player.y, 0xFF94A3B8)
            }
        }
    }

    fun useOrConsumeItem(itemId: String) {
        viewModelScope.launch {
            val item = inventoryRepository.consumeItem(itemId) ?: return@launch
            val player = _uiState.value.player
            val newHp = (player.hp + item.healHp).coerceAtMost(player.maxHp)
            val newTox = (player.toxicity - item.reduceToxicity).coerceAtLeast(0)

            val logMsg = "Administered ${item.name} (+${item.healHp} HP, -${item.reduceToxicity}% Toxicity)"
            _uiState.update {
                it.copy(
                    player = player.copy(hp = newHp, toxicity = newTox),
                    combatLogs = it.combatLogs + CombatLogEntry(logMsg)
                )
            }

            if (item.healHp > 0) spawnFloatingText("+${item.healHp} HP", player.x, player.y, 0xFF4FD1C5)
            if (item.reduceToxicity > 0) spawnFloatingText("-${item.reduceToxicity}% TOX", player.x, player.y, 0xFF22C55E)
        }
    }

    fun repairInventoryItem(itemId: String) {
        viewModelScope.launch {
            val result = inventoryRepository.repairItem(itemId)
            val item = _uiState.value.roomInventory.firstOrNull { it.itemId == itemId }
            if (result.first && item != null) {
                val logMsg = "Repaired ${item.name} to 100% durability (-${result.second} Credits)."
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText("REPAIRED 100%", _uiState.value.player.x, _uiState.value.player.y, 0xFF22C55E)
            } else {
                val logMsg = "Cannot repair ${item?.name ?: "item"}: Insufficient credits (${result.second} CR needed)."
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText("INSUFFICIENT CREDITS", _uiState.value.player.x, _uiState.value.player.y, 0xFFFF4150)
            }
        }
    }

    fun scrapInventoryItem(itemId: String) {
        viewModelScope.launch {
            val result = inventoryRepository.scrapItem(itemId)
            if (result.itemName.isNotEmpty()) {
                val scrapLog = buildString {
                    append("Scrapped ${result.itemName}: +${result.creditsGained} CR")
                    if (result.scrapMetalGained > 0) append(", +${result.scrapMetalGained} Scrap Metal")
                    if (result.chemReagentsGained > 0) append(", +${result.chemReagentsGained} Chem Reagents")
                }
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(scrapLog)) }
                spawnFloatingText("+${result.scrapMetalGained} SCRAP / +${result.creditsGained} CR", _uiState.value.player.x, _uiState.value.player.y, 0xFFFFD700)
            }
        }
    }

    fun craftWastelandRecipe(recipe: com.example.data.room.CraftingRecipe) {
        viewModelScope.launch {
            val result = inventoryRepository.craftRecipe(recipe)
            result.onSuccess { item ->
                val logMsg = "⚙️ FABRICATED: ${item.name} [${item.rarity}] via Scrap & Chemical synthesis!"
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg, isCritical = true)) }
                spawnFloatingText("CRAFTED: ${item.name}", _uiState.value.player.x, _uiState.value.player.y, 0xFF4FD1C5)
            }.onFailure { err ->
                val logMsg = "⚠️ CRAFT FAILED: ${err.message}"
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText(err.message ?: "CRAFT FAILED", _uiState.value.player.x, _uiState.value.player.y, 0xFFFF4150)
            }
        }
    }

    fun upgradeInventoryItem(itemId: String) {
        viewModelScope.launch {
            val result = inventoryRepository.upgradeEquipment(itemId)
            result.onSuccess { upgraded ->
                val logMsg = "⚡ OVERCLOCKED & UPGRADED: ${upgraded.name} (Tech Lvl ${upgraded.techLevel})!"
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg, isCritical = true)) }
                spawnFloatingText("UPGRADED: ${upgraded.name}", _uiState.value.player.x, _uiState.value.player.y, 0xFFFFD700)
            }.onFailure { err ->
                val logMsg = "⚠️ UPGRADE FAILED: ${err.message}"
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText(err.message ?: "UPGRADE FAILED", _uiState.value.player.x, _uiState.value.player.y, 0xFFFF4150)
            }
        }
    }

    fun craftOrAddCustomItem(name: String, type: String, damage: Int, defense: Int, healHp: Int, reduceTox: Int, rarity: ItemRarity = ItemRarity.RARE) {
        viewModelScope.launch {
            val cleanId = "crafted_${System.currentTimeMillis()}"
            val slot = when (type) {
                ItemType.WEAPON.name -> EquipSlot.WEAPON.name
                ItemType.ARMOR.name -> EquipSlot.ARMOR.name
                "NEURAL_CHIP" -> EquipSlot.NEURAL_CHIP.name
                else -> EquipSlot.NONE.name
            }
            val entity = InventoryItemEntity(
                itemId = cleanId,
                name = name,
                type = type,
                rarity = rarity.name,
                category = when (type) {
                    ItemType.WEAPON.name -> "Custom Plasma Weapon"
                    ItemType.ARMOR.name -> "Reinforced Hazmat Gear"
                    "NEURAL_CHIP" -> "Neural Cyber-Mod"
                    else -> "Synthesized Chem"
                },
                damage = damage,
                defense = defense,
                healHp = healHp,
                reduceToxicity = reduceTox,
                durability = 100,
                maxDurability = 100,
                weightKg = if (type == ItemType.WEAPON.name) 2.0f else 1.0f,
                creditValue = 40,
                isEquipped = false,
                equipSlot = slot,
                description = "Wasteland field-crafted $name with Room persistence."
            )
            inventoryRepository.insertOrUpdateItem(entity)
            _uiState.update {
                it.copy(combatLogs = it.combatLogs + CombatLogEntry("Crafted new item: $name!"))
            }
            spawnFloatingText("CRAFTED: $name", _uiState.value.player.x, _uiState.value.player.y, 0xFF4FD1C5)
        }
    }

    fun useItem(item: Item) {
        val inv = _uiState.value.inventory.toMutableList()
        val player = _uiState.value.player
        val logs = _uiState.value.combatLogs.toMutableList()

        if (item.type == ItemType.CONSUMABLE) {
            useOrConsumeItem(item.id)
        } else if (item.type == ItemType.WEAPON || item.type == ItemType.ARMOR) {
            equipInventoryItem(item.id)
        }
    }

    fun loadStoryScript(assetFileName: String) {
        viewModelScope.launch {
            val doc = MarkdownNarrativeParser.loadFromAssets(
                context = getApplication(),
                fileName = assetFileName,
                variableResolver = { varName ->
                    val p = _uiState.value.player
                    when (varName.uppercase()) {
                        "PLAYER_NAME" -> p.name
                        "HP" -> p.hp.toString()
                        "MAX_HP" -> p.maxHp.toString()
                        "TOXICITY" -> p.toxicity.toString()
                        "CREDITS" -> p.credits.toString()
                        else -> varName
                    }
                }
            )

            // Persist script into Room database
            storyNarrativeRepository.importMarkdownScript(
                markdownText = doc.rawText,
                scriptId = assetFileName,
                fallbackTitle = doc.title,
                category = "CAMPAIGN"
            )

            val firstNode = doc.storyNodes.values.firstOrNull()
            if (firstNode != null) {
                storyNarrativeRepository.setCurrentStoryNode(assetFileName, firstNode.id)
            }

            _uiState.update {
                it.copy(
                    currentStoryDocument = doc,
                    currentStoryAssetFileName = assetFileName,
                    currentStoryNode = firstNode,
                    rawMarkdownContent = doc.rawText,
                    combatLogs = it.combatLogs + CombatLogEntry("Loaded Narrative Script from Room: ${doc.title} ($assetFileName)")
                )
            }
        }
    }

    fun selectStoryNode(nodeId: String) {
        viewModelScope.launch {
            val scriptId = _uiState.value.currentStoryAssetFileName
            storyNarrativeRepository.setCurrentStoryNode(scriptId, nodeId)

            val currentDoc = _uiState.value.currentStoryDocument
            val targetNode = currentDoc?.storyNodes?.get(nodeId)
                ?: parsedData?.storyNodes?.get(nodeId)
                ?: storyNarrativeRepository.getNodeDirect(scriptId, nodeId)?.toDomainStoryNode()

            if (targetNode != null) {
                _uiState.update {
                    it.copy(
                        currentStoryNode = targetNode,
                        currentViewMode = ViewMode.STORY_DIALOGUE
                    )
                }
            }
        }
    }

    fun selectStoryChoice(choiceTarget: String) {
        if (choiceTarget == "action_gameview") {
            _uiState.update { it.copy(currentViewMode = ViewMode.ISOMETRIC_WORLD) }
            return
        }

        viewModelScope.launch {
            val currentNode = _uiState.value.currentStoryNode
            val matchedChoice = currentNode?.choices?.firstOrNull { it.targetNodeId == choiceTarget }
            val scriptId = _uiState.value.currentStoryAssetFileName

            if (matchedChoice != null) {
                val choiceEntity = BranchingChoiceEntity.fromDomainChoice(
                    scriptId = scriptId,
                    nodeId = currentNode.id,
                    choiceIndex = 0,
                    choice = matchedChoice
                )

                val result = storyNarrativeRepository.executeChoice(
                    choice = choiceEntity,
                    player = _uiState.value.player
                )

                var p = _uiState.value.player
                val logs = _uiState.value.combatLogs.toMutableList()

                // Apply HP delta
                if (result.hpDelta != 0) {
                    val newHp = (p.hp + result.hpDelta).coerceIn(0, p.maxHp)
                    p = p.copy(hp = newHp)
                    val text = if (result.hpDelta > 0) "+${result.hpDelta} HP" else "${result.hpDelta} HP"
                    spawnFloatingText(text, p.x, p.y, if (result.hpDelta > 0) 0xFF4FD1C5 else 0xFFFF4150)
                }

                // Apply Toxicity delta
                if (result.toxicityDelta != 0) {
                    val newTox = (p.toxicity + result.toxicityDelta).coerceIn(0, 100)
                    p = p.copy(toxicity = newTox)
                    val text = if (result.toxicityDelta > 0) "+${result.toxicityDelta}% TOX" else "${result.toxicityDelta}% TOX"
                    spawnFloatingText(text, p.x, p.y, if (result.toxicityDelta < 0) 0xFF22C55E else 0xFFFF4150)
                }

                // Apply Credits delta
                if (result.creditsDelta != 0) {
                    val newCredits = (p.credits + result.creditsDelta).coerceAtLeast(0)
                    p = p.copy(credits = newCredits)
                    spawnFloatingText("+${result.creditsDelta} CR", p.x, p.y, 0xFFFFB020)
                }

                logs.add(CombatLogEntry(result.message))

                // Handle action triggers
                when (result.actionTrigger ?: matchedChoice.actionTrigger ?: choiceTarget) {
                    "action_purge", "PURGE_VALVES" -> {
                        val newTox = (p.toxicity - 15).coerceAtLeast(0)
                        p = p.copy(toxicity = newTox)
                        logs.add(CombatLogEntry("Executed emergency purge protocol (-15% Toxicity)."))
                        spawnFloatingText("-15% TOXICITY", p.x, p.y, 0xFF22C55E)
                    }
                    "action_heal", "HEAL_FIELD" -> {
                        val newHp = (p.hp + 25).coerceAtMost(p.maxHp)
                        p = p.copy(hp = newHp)
                        logs.add(CombatLogEntry("Administered field medical stimulus (+25 HP)."))
                        spawnFloatingText("+25 HP", p.x, p.y, 0xFF4FD1C5)
                    }
                }

                val currentDoc = _uiState.value.currentStoryDocument
                val nextNode = currentDoc?.storyNodes?.get(result.targetNodeId)
                    ?: parsedData?.storyNodes?.get(result.targetNodeId)
                    ?: storyNarrativeRepository.getNodeDirect(scriptId, result.targetNodeId)?.toDomainStoryNode()

                _uiState.update { state ->
                    state.copy(
                        player = p,
                        combatLogs = logs,
                        currentStoryNode = nextNode ?: state.currentStoryNode,
                        currentViewMode = if (nextNode != null) ViewMode.STORY_DIALOGUE else ViewMode.ISOMETRIC_WORLD
                    )
                }
            } else {
                // Fallback standard choice transition
                val currentDoc = _uiState.value.currentStoryDocument
                val node = currentDoc?.storyNodes?.get(choiceTarget)
                    ?: parsedData?.storyNodes?.get(choiceTarget)
                    ?: storyNarrativeRepository.getNodeDirect(scriptId, choiceTarget)?.toDomainStoryNode()

                if (node != null) {
                    storyNarrativeRepository.setCurrentStoryNode(scriptId, choiceTarget)
                    _uiState.update {
                        it.copy(
                            currentStoryNode = node,
                            currentViewMode = ViewMode.STORY_DIALOGUE
                        )
                    }
                } else {
                    _uiState.update { it.copy(currentViewMode = ViewMode.ISOMETRIC_WORLD) }
                }
            }
        }
    }

    fun reloadFromMarkdownString(newMarkdown: String) {
        viewModelScope.launch {
            val fileName = _uiState.value.currentStoryAssetFileName
            val parsedWorld = MarkdownParser.parseString(newMarkdown, assetFileName = fileName)
            parsedData = parsedWorld

            // Ingest updated markdown into Room database
            storyNarrativeRepository.importMarkdownScript(
                markdownText = newMarkdown,
                scriptId = fileName,
                fallbackTitle = parsedWorld.document?.title ?: "Custom Script",
                category = "CAMPAIGN",
                isCustom = true
            )

            val firstNode = parsedWorld.storyNodes.values.firstOrNull()

            _uiState.update {
                it.copy(
                    currentStoryDocument = parsedWorld.document,
                    currentStoryNode = firstNode ?: it.currentStoryNode,
                    rawMarkdownContent = newMarkdown,
                    mapGrid = if (parsedWorld.mapGrid.isNotEmpty()) parsedWorld.mapGrid else it.mapGrid,
                    combatLogs = it.combatLogs + CombatLogEntry("Saved & parsed Markdown script into Room (${parsedWorld.storyNodes.size} story nodes).")
                )
            }
            spawnFloatingText("SCRIPT PERSISTED TO ROOM", _uiState.value.player.x, _uiState.value.player.y, 0xFF4FD1C5)
        }
    }

    fun deployEmergencyFlare() {
        val p = _uiState.value.player
        val flareId = "flare_${System.currentTimeMillis()}"
        val flareLight = LightSource(
            id = flareId,
            gridX = p.x,
            gridY = p.y,
            colorR = 255,
            colorG = 50,
            colorB = 80,
            intensity = 1.6f,
            radius = 6.0f,
            type = LightType.FLARE_EMERGENCY,
            flickerFrequency = 9.0f,
            flickerIntensity = 0.25f,
            pulseSpeed = 6.0f
        )
        // Reveal area around flare
        val revealed = computeFov(p.x.toInt(), p.y.toInt(), radius = 5)

        _uiState.update {
            it.copy(
                lightSources = it.lightSources + flareLight,
                discoveredTiles = it.discoveredTiles + revealed,
                combatLogs = it.combatLogs + CombatLogEntry("Deployed chemical emergency flare at [X:${p.x.toInt()}, Y:${p.y.toInt()}]!")
            )
        }
        spawnFloatingText("FLARE DEPLOYED", p.x, p.y, 0xFFFF3B5C)
    }

    fun placeWallTorch(gridX: Int, gridY: Int) {
        val torchId = "torch_${gridX}_${gridY}_${System.currentTimeMillis()}"
        val torchLight = LightSource(
            id = torchId,
            gridX = gridX + 0.5f,
            gridY = gridY + 0.5f,
            colorR = 255,
            colorG = 175,
            colorB = 40,
            intensity = 1.3f,
            radius = 4.5f,
            type = LightType.POINT_TORCH,
            flickerFrequency = 6.0f,
            flickerIntensity = 0.18f
        )
        val revealed = computeFov(gridX, gridY, radius = 4)

        _uiState.update {
            it.copy(
                lightSources = it.lightSources + torchLight,
                discoveredTiles = it.discoveredTiles + revealed,
                combatLogs = it.combatLogs + CombatLogEntry("Mounted phosphor torch at [X:$gridX, Y:$gridY]!")
            )
        }
        spawnFloatingText("TORCH MOUNTED", gridX + 0.5f, gridY + 0.5f, 0xFFFFB020)
    }

    fun clearSelectedTile() {
        _uiState.update { it.copy(selectedTile = null) }
    }

    fun togglePalette() {
        val nextIdx = (_uiState.value.colorPaletteIndex + 1) % 7
        val modeName = when (nextIdx) {
            1 -> "ANSI-256 Colorizer (8-bit Indexed)"
            2 -> "ANSI-16 Colorizer (4-bit CGA/EGA)"
            3 -> "P1 Phosphor Green (CRT Gamma)"
            4 -> "P20 Amber Industrial Terminal"
            5 -> "Synthwave Neon Cyan (16-bit)"
            6 -> "Matrix Digital Rain (High-Contrast)"
            else -> "TrueColor HDR (24-bit RGB Filmic)"
        }
        _uiState.update {
            it.copy(
                colorPaletteIndex = nextIdx,
                combatLogs = it.combatLogs + CombatLogEntry("Switched Colorizer: $modeName")
            )
        }
        spawnFloatingText(modeName, _uiState.value.player.x, _uiState.value.player.y, 0xFF4FD1C5)
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(currentViewMode = mode) }
    }

    fun openModal(modal: ActiveModal) {
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun closeModal() {
        _uiState.update { it.copy(activeModal = ActiveModal.NONE) }
    }

    fun restartGame() {
        loadWorldFromMarkdown()
        _uiState.update { it.copy(isGameOver = false, isVictory = false, activeModal = ActiveModal.NONE) }
    }

    override fun onCleared() {
        super.onCleared()
        proceduralAudioManager.release()
    }
}
