package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CombatLogEntry
import com.example.data.CombatQueueEntity
import com.example.data.CombatantType
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.Item
import com.example.data.ItemType
import com.example.data.MarkdownParser
import com.example.data.NpcState
import com.example.data.Player
import com.example.data.StatusEffect
import com.example.data.StatusEffectType
import com.example.data.StoryNode
import com.example.data.TileType
import com.example.data.TurnCombatQueueState
import com.example.data.TurnPhase
import com.example.data.room.BranchingChoiceEntity
import com.example.data.room.CharacterProfileEntity
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
    QUEST_LOG
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
        profileDao = database.characterProfileDao()
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

    private fun observeRoomDatabase() {
        viewModelScope.launch {
            inventoryRepository.allInventoryItems.collect { items ->
                val domainItems = items.map { it.toDomainItem() }
                _uiState.update {
                    it.copy(
                        roomInventory = items,
                        inventory = domainItems
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
                    _uiState.update {
                        it.copy(
                            characterProfile = profile,
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

    fun loadWorldFromMarkdown() {
        viewModelScope.launch {
            val data = parser.parseWorld(MarkdownParser.parseFromAssets(getApplication()).rawMarkdownText)
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

            val initialEnemies = initializeNpcStateMachines(data.enemies, data.mapGrid)
            val initialQueue = buildTurnCombatQueue(player, initialEnemies, round = 1, currentActiveId = "player")

            _uiState.update {
                it.copy(
                    player = player,
                    currentStoryNode = startingNode,
                    currentStoryDocument = data.document,
                    currentStoryAssetFileName = "chemopank_world.md",
                    inventory = startingInventory,
                    activeEnemies = initialEnemies,
                    turnQueueState = initialQueue,
                    mapGrid = data.mapGrid,
                    lightSources = initialLights,
                    rawMarkdownContent = data.rawMarkdownText,
                    discoveredTiles = initialDiscovered,
                    combatLogs = initialLogs
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
                // Engage in combat
                _uiState.update {
                    it.copy(
                        activeModal = ActiveModal.COMBAT,
                        activeCombatEnemy = enemy,
                        combatLogs = it.combatLogs + CombatLogEntry("Engaged hostile: ${enemy.name}!")
                    )
                }
            }
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
                enemy.copy(statusEffects = existing).also {
                    enemy.statusEffects = existing
                }
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

            enemy.hp = enemyHp
            enemy.statusEffects = remainingEffects
            if (enemyHp == 0) enemy.isAlive = false

            val updatedEnemies = state.activeEnemies.map { if (it.id == enemy.id) enemy else it }
            val refreshedQueue = buildTurnCombatQueue(state.player, updatedEnemies)

            _uiState.update {
                it.copy(
                    activeEnemies = updatedEnemies,
                    combatLogs = logs,
                    turnQueueState = refreshedQueue
                )
            }
            return canAct && enemy.isAlive
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
        val state = _uiState.value
        val currentQueue = state.turnQueueState
        if (currentQueue.combatants.isEmpty()) {
            val refreshed = buildTurnCombatQueue(state.player, state.activeEnemies)
            _uiState.update { it.copy(turnQueueState = refreshed) }
            return
        }

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

        // Process Status Effects at Turn Start
        val canAct = processCombatantTurnStatusEffects(nextCombatant.id)

        if (!canAct) {
            // Turn skipped due to stun
            advanceTurnQueue()
            return
        }

        if (!nextCombatant.isPlayer) {
            executeNpcCombatantTurn(nextCombatant.id)
        } else {
            val logs = _uiState.value.combatLogs + CombatLogEntry("⚡ Round $nextRound: Scythe-01 ready. (Your Turn)")
            _uiState.update { it.copy(combatLogs = logs) }
        }
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

                _uiState.update { state ->
                    state.copy(
                        player = updatedPlayer,
                        discoveredTiles = newDiscovered,
                        combatLogs = logs,
                        turnQueueState = refreshedQueue
                    )
                }

                // Advance NPC State Machine Turn after player movement
                updateAllNpcsAi()

                // Check Enemy Encounter
                checkEnemyProximity(newX, newY)

                // Check Extraction
                if (tile == TileType.EXTRACTION_LIFT) {
                    _uiState.update { it.copy(isVictory = true) }
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
                val totalDef = (armorDef + chipDef) / 2
                val netDmg = (turnResult.damageDealtToPlayer - totalDef).coerceAtLeast(1)

                val newHp = (player.hp - netDmg).coerceAtLeast(0)
                val newTox = (player.toxicity + turnResult.toxicityDealtToPlayer).coerceAtMost(100)
                player = player.copy(hp = newHp, toxicity = newTox)
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
        val enemies = _uiState.value.activeEnemies
        val nearEnemy = enemies.firstOrNull { enemy ->
            enemy.isAlive && Math.hypot((enemy.x - px).toDouble(), (enemy.y - py).toDouble()) < 0.8
        }

        if (nearEnemy != null) {
            val currentRound = _uiState.value.turnQueueState.roundNumber
            val queue = buildTurnCombatQueue(_uiState.value.player, enemies, round = currentRound, currentActiveId = "player")
            _uiState.update {
                it.copy(
                    activeModal = ActiveModal.COMBAT,
                    activeCombatEnemy = nearEnemy,
                    turnQueueState = queue.copy(isCombatActive = true),
                    combatLogs = it.combatLogs + CombatLogEntry("Engaged hostile: ${nearEnemy.name}!")
                )
            }
        }
    }

    fun attackCombatEnemy() {
        val enemy = _uiState.value.activeCombatEnemy ?: return
        val player = _uiState.value.player
        val stats = _uiState.value.gearStats

        val adrenalineBonus = player.statusEffects.filter { it.type == StatusEffectType.ADRENALINE }.sumOf { it.magnitude }
        val weaponDmg = if (stats.totalWeaponDamage > 0) stats.totalWeaponDamage else (player.equippedWeapon?.damage ?: 10)
        val chipDmg = stats.activeChip?.damage ?: 0
        val critBonus = stats.activeChip?.criticalBonus ?: 0f

        val isCrit = Math.random() < (0.15 + critBonus)
        val enemyCorrosion = enemy.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
        val effectiveEnemyArmor = (enemy.armor - enemyCorrosion).coerceAtLeast(0)

        val baseDmg = (player.attackPower + adrenalineBonus + weaponDmg + chipDmg - effectiveEnemyArmor).coerceAtLeast(3)
        val totalDmg = if (isCrit) (baseDmg * 1.5f).toInt() else baseDmg

        val newEnemyHp = (enemy.hp - totalDmg).coerceAtLeast(0)
        val logs = _uiState.value.combatLogs.toMutableList()
        val hitMsg = if (isCrit) "CRITICAL STRIKE on ${enemy.name} for $totalDmg damage!" else "Attacked ${enemy.name} for $totalDmg damage!"
        logs.add(CombatLogEntry(hitMsg, isCritical = isCrit))
        spawnFloatingText(if (isCrit) "-$totalDmg CRIT!" else "-$totalDmg", enemy.x, enemy.y, 0xFFFF4150)

        // Weapon durability degradation on strike
        viewModelScope.launch {
            inventoryRepository.degradeEquippedWeapon(1)
        }

        // Status effect chance on strike
        val enemyEffects = enemy.statusEffects.toMutableList()
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
        enemy.statusEffects = enemyEffects

        if (newEnemyHp == 0) {
            enemy.isAlive = false
            logs.add(CombatLogEntry("Defeated ${enemy.name}! +${enemy.expReward} EXP!"))
            spawnFloatingText("+${enemy.expReward} EXP", enemy.x, enemy.y, 0xFF4FD1C5)

            var inv = _uiState.value.inventory.toMutableList()
            if (enemy.lootItemId != null) {
                val lootItem = parsedData?.items?.get(enemy.lootItemId)
                if (lootItem != null) {
                    inv.add(lootItem)
                    logs.add(CombatLogEntry("Looted item: ${lootItem.name}! (Saved to Room DB)"))
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

            val updatedEnemies = _uiState.value.activeEnemies.map { if (it.id == enemy.id) enemy else it }
            val refreshedQueue = buildTurnCombatQueue(player, updatedEnemies)

            _uiState.update {
                it.copy(
                    activeCombatEnemy = null,
                    activeModal = ActiveModal.NONE,
                    inventory = inv,
                    combatLogs = logs,
                    activeEnemies = updatedEnemies,
                    turnQueueState = refreshedQueue,
                    player = player.copy(exp = player.exp + enemy.expReward, credits = player.credits + 20)
                )
            }
        } else {
            enemy.hp = newEnemyHp

            // Check if NPC enters FLEE or AGGRESSIVE state
            val hpRatio = newEnemyHp.toFloat() / enemy.maxHp.toFloat()
            if (hpRatio <= enemy.fleeThreshold) {
                enemy.state = NpcState.FLEE
                logs.add(CombatLogEntry("⚠️ [MORALE BROKEN] ${enemy.name} panicked (HP: $newEnemyHp/${enemy.maxHp})! Fleeing!"))
                spawnFloatingText("💨 FLEEING!", enemy.x, enemy.y, 0xFFFFD700)
            } else {
                enemy.state = NpcState.AGGRESSIVE
                logs.add(CombatLogEntry("🚨 ${enemy.name} is enraged (AGGRESSIVE)!"))
            }

            // Enemy Counter-Attack with Room Armor Defense & Status Corrosion Calculation
            val playerCorrosion = player.statusEffects.filter { it.type == StatusEffectType.CORROSION }.sumOf { it.magnitude }
            val armorDef = if (stats.totalArmorDefense > 0) stats.totalArmorDefense else (player.equippedArmor?.defense ?: 0)
            val chipDef = stats.activeChip?.defense ?: 0
            val totalDef = (player.defense + armorDef + chipDef - playerCorrosion).coerceAtLeast(0)

            val enemyDmg = (enemy.attack - totalDef).coerceAtLeast(1)
            val newPlayerHp = (player.hp - enemyDmg).coerceAtLeast(0)
            val newTox = (player.toxicity + enemy.toxicityDamage).coerceAtMost(100)

            logs.add(CombatLogEntry("${enemy.name} counter-attacked for $enemyDmg damage (Armor blocked ${totalDef} DMG)!"))

            // Armor durability degradation on impact
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

            val updatedEnemies = _uiState.value.activeEnemies.map { if (it.id == enemy.id) enemy else it }
            val refreshedQueue = buildTurnCombatQueue(updatedPlayer, updatedEnemies)

            if (newPlayerHp == 0 || newTox >= 100) {
                _uiState.update { it.copy(isGameOver = true) }
            } else {
                _uiState.update {
                    it.copy(
                        activeCombatEnemy = enemy,
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
                logs.add(CombatLogEntry("💉 Injected Combat Adrenaline: +8 ATK for 3 turns!"))
            }
            StatusEffectType.REGENERATION -> {
                applyStatusEffectToPlayer(StatusEffect(type = StatusEffectType.REGENERATION, durationTurns = 4, magnitude = 7))
                logs.add(CombatLogEntry("💖 Activated Nano-Regeneration: +7 HP/turn for 4 turns."))
            }
            StatusEffectType.STUN -> {
                if (enemy != null) {
                    applyStatusEffectToEnemy(enemy.id, StatusEffect(type = StatusEffectType.STUN, durationTurns = 1, magnitude = 1))
                    logs.add(CombatLogEntry("⚡ Deployed Shock Grenade: ${enemy.name} Stunned for 1 turn!"))
                }
            }
            StatusEffectType.CORROSION -> {
                if (enemy != null) {
                    applyStatusEffectToEnemy(enemy.id, StatusEffect(type = StatusEffectType.CORROSION, durationTurns = 3, magnitude = 6))
                    logs.add(CombatLogEntry("🧪 Launched Acid Chem-Flask: ${enemy.name} armor corroded (-6 DEF)!"))
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
        advanceTurnQueue()
    }

    fun useCombatStimpack() {
        val player = _uiState.value.player
        val newHp = (player.hp + 35).coerceAtMost(player.maxHp)
        val newTox = (player.toxicity - 15).coerceAtLeast(0)
        val logs = _uiState.value.combatLogs + CombatLogEntry("Quick Stimpack applied: +35 HP, -15% Toxicity purged.")
        spawnFloatingText("+35 HP", player.x, player.y, 0xFF4FD1C5)
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
        advanceTurnQueue()
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
