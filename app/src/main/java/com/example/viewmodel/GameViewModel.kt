package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CombatLogEntry
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.Item
import com.example.data.ItemType
import com.example.data.MarkdownParser
import com.example.data.NpcState
import com.example.data.Player
import com.example.data.StoryNode
import com.example.data.TileType
import com.example.data.room.CharacterProfileEntity
import com.example.data.room.EquipSlot
import com.example.data.room.GameDatabase
import com.example.data.room.GearCombatStats
import com.example.data.room.InventoryItemEntity
import com.example.data.room.InventoryRepository
import com.example.data.room.ItemRarity
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

            _uiState.update {
                it.copy(
                    player = player,
                    currentStoryNode = startingNode,
                    currentStoryDocument = data.document,
                    currentStoryAssetFileName = "chemopank_world.md",
                    inventory = startingInventory,
                    activeEnemies = initializeNpcStateMachines(data.enemies, data.mapGrid),
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
        val logs = _uiState.value.combatLogs + CombatLogEntry("Turn skipped: Standing ground (Sensors active)...")
        _uiState.update { it.copy(combatLogs = logs) }
        spawnFloatingText("WAIT TURN", _uiState.value.player.x, _uiState.value.player.y, 0xFF94A3B8)
        updateAllNpcsAi()
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

                _uiState.update { state ->
                    state.copy(
                        player = state.player.copy(x = newX, y = newY, toxicity = tox),
                        discoveredTiles = newDiscovered,
                        combatLogs = logs
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

        _uiState.update {
            it.copy(
                player = player,
                activeEnemies = updatedEnemies,
                combatLogs = logs,
                isGameOver = isGameOver
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
            _uiState.update {
                it.copy(
                    activeModal = ActiveModal.COMBAT,
                    activeCombatEnemy = nearEnemy,
                    combatLogs = it.combatLogs + CombatLogEntry("Engaged hostile: ${nearEnemy.name}!")
                )
            }
        }
    }

    fun attackCombatEnemy() {
        val enemy = _uiState.value.activeCombatEnemy ?: return
        val player = _uiState.value.player
        val stats = _uiState.value.gearStats

        val weaponDmg = if (stats.totalWeaponDamage > 0) stats.totalWeaponDamage else (player.equippedWeapon?.damage ?: 10)
        val chipDmg = stats.activeChip?.damage ?: 0
        val critBonus = stats.activeChip?.criticalBonus ?: 0f

        val isCrit = Math.random() < (0.15 + critBonus)
        val baseDmg = (player.attackPower + weaponDmg + chipDmg - enemy.armor).coerceAtLeast(3)
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

            _uiState.update {
                it.copy(
                    activeCombatEnemy = null,
                    activeModal = ActiveModal.NONE,
                    inventory = inv,
                    combatLogs = logs,
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

            // Enemy Counter-Attack with Room Armor Defense Calculation
            val armorDef = if (stats.totalArmorDefense > 0) stats.totalArmorDefense else (player.equippedArmor?.defense ?: 0)
            val chipDef = stats.activeChip?.defense ?: 0
            val totalDef = player.defense + armorDef + chipDef

            val enemyDmg = (enemy.attack - totalDef).coerceAtLeast(1)
            val newPlayerHp = (player.hp - enemyDmg).coerceAtLeast(0)
            val newTox = (player.toxicity + enemy.toxicityDamage).coerceAtMost(100)

            logs.add(CombatLogEntry("${enemy.name} counter-attacked for $enemyDmg damage (Armor blocked ${totalDef} DMG)!"))

            // Armor durability degradation on impact
            viewModelScope.launch {
                inventoryRepository.degradeEquippedArmor(1)
            }

            if (newPlayerHp == 0 || newTox >= 100) {
                _uiState.update { it.copy(isGameOver = true) }
            } else {
                _uiState.update {
                    it.copy(
                        activeCombatEnemy = enemy,
                        player = player.copy(hp = newPlayerHp, toxicity = newTox),
                        combatLogs = logs
                    )
                }
            }
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
            val item = _uiState.value.roomInventory.firstOrNull { it.itemId == itemId }
            val creditsGained = inventoryRepository.scrapItem(itemId)
            if (item != null) {
                val logMsg = "Scrapped ${item.name} for +$creditsGained Credits."
                _uiState.update { it.copy(combatLogs = it.combatLogs + CombatLogEntry(logMsg)) }
                spawnFloatingText("+$creditsGained CR", _uiState.value.player.x, _uiState.value.player.y, 0xFFFFD700)
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

            val firstNode = doc.storyNodes.values.firstOrNull()
            _uiState.update {
                it.copy(
                    currentStoryDocument = doc,
                    currentStoryAssetFileName = assetFileName,
                    currentStoryNode = firstNode,
                    rawMarkdownContent = doc.rawText,
                    combatLogs = it.combatLogs + CombatLogEntry("Loaded Narrative Script: ${doc.title} ($assetFileName)")
                )
            }
        }
    }

    fun selectStoryNode(nodeId: String) {
        val currentDoc = _uiState.value.currentStoryDocument
        val targetNode = currentDoc?.storyNodes?.get(nodeId)
            ?: parsedData?.storyNodes?.get(nodeId)
        if (targetNode != null) {
            _uiState.update {
                it.copy(
                    currentStoryNode = targetNode,
                    currentViewMode = ViewMode.STORY_DIALOGUE
                )
            }
        }
    }

    fun selectStoryChoice(choiceTarget: String) {
        if (choiceTarget == "action_gameview") {
            _uiState.update { it.copy(currentViewMode = ViewMode.ISOMETRIC_WORLD) }
            return
        }

        // Check if choice target corresponds to an action trigger
        when (choiceTarget) {
            "action_purge" -> {
                val p = _uiState.value.player
                val newTox = (p.toxicity - 15).coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        player = p.copy(toxicity = newTox),
                        combatLogs = it.combatLogs + CombatLogEntry("Executed emergency purge protocol (-15% Toxicity).")
                    )
                }
                spawnFloatingText("-15% TOXICITY", p.x, p.y, 0xFF22C55E)
            }
            "action_heal" -> {
                val p = _uiState.value.player
                val newHp = (p.hp + 25).coerceAtMost(p.maxHp)
                _uiState.update {
                    it.copy(
                        player = p.copy(hp = newHp),
                        combatLogs = it.combatLogs + CombatLogEntry("Administered field medical stimulus (+25 HP).")
                    )
                }
                spawnFloatingText("+25 HP", p.x, p.y, 0xFF4FD1C5)
            }
        }

        val currentDoc = _uiState.value.currentStoryDocument
        val node = currentDoc?.storyNodes?.get(choiceTarget)
            ?: parsedData?.storyNodes?.get(choiceTarget)

        if (node != null) {
            // Apply choice side-effects if node has matching choice
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

    fun reloadFromMarkdownString(newMarkdown: String) {
        viewModelScope.launch {
            val fileName = _uiState.value.currentStoryAssetFileName
            val parsedWorld = MarkdownParser.parseString(newMarkdown, assetFileName = fileName)
            parsedData = parsedWorld

            val firstNode = parsedWorld.storyNodes.values.firstOrNull()

            _uiState.update {
                it.copy(
                    currentStoryDocument = parsedWorld.document,
                    currentStoryNode = firstNode ?: it.currentStoryNode,
                    rawMarkdownContent = newMarkdown,
                    mapGrid = if (parsedWorld.mapGrid.isNotEmpty()) parsedWorld.mapGrid else it.mapGrid,
                    combatLogs = it.combatLogs + CombatLogEntry("Reloaded live Markdown script (${parsedWorld.storyNodes.size} story nodes parsed).")
                )
            }
            spawnFloatingText("SCRIPT RELOADED", _uiState.value.player.x, _uiState.value.player.y, 0xFF4FD1C5)
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
