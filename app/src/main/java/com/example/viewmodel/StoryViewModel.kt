package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Choice
import com.example.data.Player
import com.example.data.StoryNode
import com.example.data.narrative.MarkdownNarrativeParser
import com.example.data.narrative.NarrativeScriptDocument
import com.example.data.room.BranchingChoiceEntity
import com.example.data.room.GameDatabase
import com.example.data.room.InventoryRepository
import com.example.data.room.NarrativeNodeEntity
import com.example.data.room.StoryBranchingChoice
import com.example.data.room.StoryDao
import com.example.data.room.StoryDatabase
import com.example.data.room.StoryNodeWithChoices
import com.example.data.room.StoryProgress
import com.example.data.room.StorySceneNode
import com.example.data.room.StoryScript
import com.example.data.room.StoryScriptEntity
import com.example.data.room.StoryScriptWithGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import kotlin.random.Random

/**
 * State of visual and logistical scene transition.
 */
enum class SceneTransitionState {
    IDLE,
    ENTERING,
    ACTIVE,
    TRANSITIONING,
    REQUIREMENT_FAILED,
    TERMINAL_NODE
}

/**
 * Evaluated model for a branching choice ready for UI rendering.
 */
data class EvaluatedChoiceUiModel(
    val choice: StoryBranchingChoice,
    val isEligible: Boolean,
    val isHidden: Boolean,
    val lockReason: String? = null,
    val probabilityPercent: Int = (choice.successProbability * 100).toInt(),
    val hasActionTrigger: Boolean = !choice.actionTrigger.isNullOrBlank(),
    val consequenceSummary: String = buildConsequenceSummary(choice)
)

private fun buildConsequenceSummary(choice: StoryBranchingChoice): String {
    val deltas = mutableListOf<String>()
    if (choice.hpDelta > 0) deltas.add("+${choice.hpDelta} HP")
    else if (choice.hpDelta < 0) deltas.add("${choice.hpDelta} HP")

    if (choice.toxicityDelta > 0) deltas.add("+${choice.toxicityDelta}% TOX")
    else if (choice.toxicityDelta < 0) deltas.add("${choice.toxicityDelta}% TOX")

    if (choice.creditsDelta > 0) deltas.add("+${choice.creditsDelta} CR")
    else if (choice.creditsDelta < 0) deltas.add("${choice.creditsDelta} CR")

    if (choice.expReward > 0) deltas.add("+${choice.expReward} EXP")
    if (!choice.rewardItemId.isNullOrBlank()) deltas.add("Item: ${choice.rewardItemId}")
    if (!choice.setStoryFlag.isNullOrBlank()) deltas.add("Flag: ${choice.setStoryFlag}")

    return deltas.joinToString(" | ")
}

/**
 * Event dispatched when an interactive or system action trigger occurs.
 */
sealed class StoryActionTriggerEvent {
    data class SwitchToGameView(val payload: String?) : StoryActionTriggerEvent()
    data class InitiateCombat(val enemyId: String, val payload: String?) : StoryActionTriggerEvent()
    data class TriggerEnvironmentalAction(val actionName: String, val payload: String?) : StoryActionTriggerEvent()
    data class ItemAcquired(val itemId: String, val count: Int = 1) : StoryActionTriggerEvent()
    data class PlaySoundCue(val cue: String) : StoryActionTriggerEvent()
    data class StoryCompleted(val scriptTitle: String) : StoryActionTriggerEvent()
    data class Notification(val message: String, val isWarning: Boolean = false) : StoryActionTriggerEvent()
}

/**
 * Full UI State representation for the Markdown Story Narrative engine.
 */
data class StoryUiState(
    val isLoading: Boolean = true,
    val currentScript: StoryScript? = null,
    val currentNode: StorySceneNode? = null,
    val evaluatedChoices: List<EvaluatedChoiceUiModel> = emptyList(),
    val availableScripts: List<StoryScript> = emptyList(),
    val visitedNodeIds: List<String> = emptyList(),
    val navigationBreadcrumbs: List<String> = emptyList(),
    val storyFlags: Map<String, Boolean> = emptyMap(),
    val transitionState: SceneTransitionState = SceneTransitionState.IDLE,
    val activeSpeaker: String? = null,
    val speakerMood: String = "NORMAL",
    val sceneAtmosphere: String = "SECTOR_7_LAB",
    val soundEffectCue: String? = null,
    val isCheckpoint: Boolean = false,
    val isTerminalNode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<StorySceneNode> = emptyList(),
    val playerStats: Player = Player(),
    val inventoryItemIds: Set<String> = emptySet(),
    val rawMarkdownPreview: String = "",
    val errorMessage: String? = null
)

/**
 * ViewModel that processes Markdown story scripts from the Room database,
 * manages persistent progress, and drives scene navigation and conditional choice logic.
 */
class StoryViewModel(
    application: Application,
    private val storyDao: StoryDao = StoryDatabase.getDatabase(application).storyDao(),
    private val gameDatabase: GameDatabase = GameDatabase.getDatabase(application),
    private val autoInitDatabase: Boolean = true
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    private val _actionEvents = Channel<StoryActionTriggerEvent>(Channel.BUFFERED)
    val actionEvents = _actionEvents.receiveAsFlow()

    private val inventoryRepository = InventoryRepository(
        inventoryDao = gameDatabase.inventoryDao(),
        profileDao = gameDatabase.characterProfileDao(),
        perkDao = gameDatabase.perkDao(),
        shopDao = gameDatabase.shopDao(),
        factionRepDao = gameDatabase.factionRepDao()
    )

    init {
        if (autoInitDatabase) {
            initializeStoryDatabase()
        }
        observeInventoryAndPlayer()
        observeScriptsList()
    }

    /**
     * Seeds and loads the initial Markdown narrative scripts into the Room database.
     */
    fun initializeStoryDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure default scripts are imported if DB is empty
                val existingScripts = storyDao.getAllScripts().firstOrNull() ?: emptyList()
                if (existingScripts.isEmpty()) {
                    seedDefaultMarkdownScripts()
                }

                // Load saved progress or initial script
                val progress = storyDao.getProgressDirect(1)
                val targetScriptId = progress?.currentScriptId ?: "chemopank_world.md"
                val targetNodeId = progress?.currentNodeId ?: "start"

                val flags = parseFlagsJson(progress?.storyFlagsJson)
                val visited = parseVisitedJson(progress?.visitedNodeIdsJson)

                _uiState.update {
                    it.copy(
                        storyFlags = flags,
                        visitedNodeIds = visited,
                        navigationBreadcrumbs = visited
                    )
                }

                loadScriptSync(targetScriptId, targetNodeId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error initializing narrative engine: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Observes real-time script documents from Room.
     */
    private fun observeScriptsList() {
        viewModelScope.launch {
            storyDao.getAllScripts().collectLatest { scripts ->
                _uiState.update { it.copy(availableScripts = scripts) }
            }
        }
    }

    /**
     * Observes player inventory from Room to evaluate dynamic item requirements.
     */
    private fun observeInventoryAndPlayer() {
        viewModelScope.launch {
            inventoryRepository.allInventoryItems.collectLatest { items ->
                val itemIds = items.map { it.itemId }.toSet()
                _uiState.update { it.copy(inventoryItemIds = itemIds) }
                // Re-evaluate choices when inventory changes
                reEvaluateCurrentChoices()
            }
        }
    }

    /**
     * Loads a specific story script and navigates to the given starting scene node.
     */
    fun loadScript(scriptId: String, initialNodeId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            loadScriptSync(scriptId, initialNodeId)
        }
    }

    /**
     * Synchronous suspend helper for loading a story script.
     */
    suspend fun loadScriptSync(scriptId: String, initialNodeId: String? = null) {
        _uiState.update { it.copy(isLoading = true, transitionState = SceneTransitionState.TRANSITIONING) }

        var script = storyDao.getScriptByIdDirect(scriptId)
        if (script == null) {
            // Try to import from assets if missing
            importScriptFromAsset(scriptId)
            script = storyDao.getScriptByIdDirect(scriptId)
        }

        if (script != null) {
            val startNodeId = initialNodeId ?: script.initialNodeId
            _uiState.update {
                it.copy(
                    currentScript = script,
                    rawMarkdownPreview = script.rawMarkdown
                )
            }
            navigateToSceneInternal(script.scriptId, startNodeId, isBackNavigation = false)
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Script '$scriptId' not found."
                )
            }
        }
    }

    /**
     * Navigates to a specific scene node within the active script.
     */
    fun navigateToScene(nodeId: String) {
        val currentScriptId = _uiState.value.currentScript?.scriptId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            navigateToSceneInternal(currentScriptId, nodeId, isBackNavigation = false)
        }
    }

    /**
     * Internal implementation of scene navigation and state transitions.
     */
    private suspend fun navigateToSceneInternal(
        scriptId: String,
        nodeId: String,
        isBackNavigation: Boolean
    ) {
        _uiState.update { it.copy(transitionState = SceneTransitionState.TRANSITIONING) }

        val node = storyDao.getNodeDirect(scriptId, nodeId)
        if (node == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    transitionState = SceneTransitionState.IDLE,
                    errorMessage = "Scene node '$nodeId' not found in script '$scriptId'."
                )
            }
            return
        }

        val rawChoices = storyDao.getChoicesForNodeDirect(scriptId, nodeId)
        val player = _uiState.value.playerStats
        val inventory = _uiState.value.inventoryItemIds
        val storyFlags = _uiState.value.storyFlags

        val evaluated = rawChoices.map { choice ->
            evaluateChoice(choice, player, inventory, storyFlags)
        }

        val newBreadcrumbs = if (isBackNavigation) {
            _uiState.value.navigationBreadcrumbs.dropLast(1)
        } else {
            _uiState.value.navigationBreadcrumbs + nodeId
        }

        val updatedVisited = if (!_uiState.value.visitedNodeIds.contains(nodeId)) {
            _uiState.value.visitedNodeIds + nodeId
        } else {
            _uiState.value.visitedNodeIds
        }

        val isTerminal = rawChoices.isEmpty()

        // Sound effect cue
        node.soundEffectCue?.let { cue ->
            _actionEvents.send(StoryActionTriggerEvent.PlaySoundCue(cue))
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                currentNode = node,
                evaluatedChoices = evaluated,
                visitedNodeIds = updatedVisited,
                navigationBreadcrumbs = newBreadcrumbs,
                activeSpeaker = node.speaker,
                speakerMood = node.speakerMood,
                sceneAtmosphere = node.bgAtmosphere,
                soundEffectCue = node.soundEffectCue,
                isCheckpoint = node.isCheckpoint,
                isTerminalNode = isTerminal,
                transitionState = if (isTerminal) SceneTransitionState.TERMINAL_NODE else SceneTransitionState.ACTIVE,
                errorMessage = null
            )
        }

        // Persist progress to Room
        saveProgress(scriptId, nodeId, updatedVisited, storyFlags)
    }

    /**
     * Executes a player-selected branching choice, applying state deltas and trigger logic.
     */
    fun selectChoice(choice: StoryBranchingChoice) {
        viewModelScope.launch(Dispatchers.IO) {
            selectChoiceSync(choice)
        }
    }

    /**
     * Synchronous suspend helper for selecting a choice.
     */
    suspend fun selectChoiceSync(choice: StoryBranchingChoice) {
        val currentState = _uiState.value
        val player = currentState.playerStats
        val inventory = currentState.inventoryItemIds
        val flags = currentState.storyFlags

        val evaluation = evaluateChoice(choice, player, inventory, flags)
        if (!evaluation.isEligible) {
            _uiState.update { it.copy(transitionState = SceneTransitionState.REQUIREMENT_FAILED) }
            _actionEvents.send(
                StoryActionTriggerEvent.Notification(
                    evaluation.lockReason ?: "Prerequisites not met.",
                    isWarning = true
                )
            )
            return
        }

        // 1. Evaluate Chance / Probability Checks
        val isSuccess = if (choice.successProbability < 1.0f) {
            Random.nextFloat() <= choice.successProbability
        } else {
            true
        }

        val targetNodeId = if (isSuccess) {
            choice.targetNodeId
        } else {
            choice.failureTargetNodeId ?: choice.targetNodeId
        }

        // 2. Apply Player State Consequences (HP, Toxicity, Credits, EXP)
        val newPlayer = player.copy(
            hp = (player.hp + choice.hpDelta).coerceIn(0, player.maxHp),
            toxicity = (player.toxicity + choice.toxicityDelta).coerceIn(0, 100),
            credits = (player.credits + choice.creditsDelta).coerceAtLeast(0),
            exp = player.exp + choice.expReward
        )
        _uiState.update { it.copy(playerStats = newPlayer) }

        // 3. Update Story Flags
        val updatedFlags = flags.toMutableMap()
        choice.setStoryFlag?.let { flag ->
            if (flag.isNotBlank()) updatedFlags[flag] = true
        }
        choice.clearStoryFlag?.let { flag ->
            if (flag.isNotBlank()) updatedFlags.remove(flag)
        }
        _uiState.update { it.copy(storyFlags = updatedFlags) }

        // 4. Handle Item Rewards
        choice.rewardItemId?.let { rewardId ->
            if (rewardId.isNotBlank()) {
                _actionEvents.send(StoryActionTriggerEvent.ItemAcquired(rewardId, 1))
            }
        }

        // 5. Fire Choice Action Triggers & System Events
        choice.actionTrigger?.let { trigger ->
            if (trigger.isNotBlank()) {
                dispatchActionTrigger(trigger, choice.triggerPayload)
            }
        }

        // 6. Navigate to Target Scene Node
        val scriptId = choice.scriptId.ifBlank { currentState.currentScript?.scriptId ?: "chemopank_world.md" }
        navigateToSceneInternal(scriptId, targetNodeId, isBackNavigation = false)
    }

    /**
     * Evaluates a single choice against player stats, inventory items, and narrative flags.
     */
    private fun evaluateChoice(
        choice: StoryBranchingChoice,
        player: Player,
        inventory: Set<String>,
        flags: Map<String, Boolean>
    ): EvaluatedChoiceUiModel {
        val missingRequirements = mutableListOf<String>()

        // 1. Level Check
        if (choice.requiredMinLevel > 0 && player.level < choice.requiredMinLevel) {
            missingRequirements.add("Requires Level ${choice.requiredMinLevel}")
        }

        // 2. Max Toxicity Check
        if (player.toxicity > choice.requiredMaxToxicity) {
            missingRequirements.add("Toxicity too high (> ${choice.requiredMaxToxicity}%)")
        }

        // 3. Min Credits Check
        if (choice.requiredMinCredits > 0 && player.credits < choice.requiredMinCredits) {
            missingRequirements.add("Requires ${choice.requiredMinCredits} Credits")
        }

        // 4. Inventory Item Check
        choice.requiredItemId?.let { requiredItem ->
            if (requiredItem.isNotBlank() && !inventory.contains(requiredItem)) {
                missingRequirements.add("Requires: $requiredItem")
            }
        }

        // 5. Story Flag Check
        choice.requiredStoryFlag?.let { flag ->
            if (flag.isNotBlank() && flags[flag] != true) {
                missingRequirements.add("Prerequisite flag '$flag' not discovered")
            }
        }

        val isEligible = missingRequirements.isEmpty()
        val lockReason = if (!isEligible) missingRequirements.joinToString(" • ") else null

        return EvaluatedChoiceUiModel(
            choice = choice,
            isEligible = isEligible,
            isHidden = choice.isHiddenIfUnavailable && !isEligible,
            lockReason = lockReason
        )
    }

    /**
     * Dispatches system action triggers (e.g. gameplay switch, combat start, environmental hazards).
     */
    private suspend fun dispatchActionTrigger(actionTrigger: String, payload: String?) {
        when {
            actionTrigger.equals("ACTION_GAMEVIEW", ignoreCase = true) -> {
                _actionEvents.send(StoryActionTriggerEvent.SwitchToGameView(payload))
            }
            actionTrigger.startsWith("COMBAT", ignoreCase = true) -> {
                val enemyId = payload ?: "mutant_scavenger"
                _actionEvents.send(StoryActionTriggerEvent.InitiateCombat(enemyId, payload))
            }
            actionTrigger.startsWith("AUDIO", ignoreCase = true) -> {
                _actionEvents.send(StoryActionTriggerEvent.PlaySoundCue(actionTrigger))
            }
            else -> {
                _actionEvents.send(StoryActionTriggerEvent.TriggerEnvironmentalAction(actionTrigger, payload))
            }
        }
    }

    /**
     * Navigates back one step in the scene breadcrumbs.
     */
    fun navigateBack(): Boolean {
        val breadcrumbs = _uiState.value.navigationBreadcrumbs
        if (breadcrumbs.size <= 1) return false

        val previousNodeId = breadcrumbs[breadcrumbs.size - 2]
        val scriptId = _uiState.value.currentScript?.scriptId ?: return false

        viewModelScope.launch(Dispatchers.IO) {
            navigateToSceneInternal(scriptId, previousNodeId, isBackNavigation = true)
        }
        return true
    }

    suspend fun navigateBackSync(): Boolean {
        val breadcrumbs = _uiState.value.navigationBreadcrumbs
        if (breadcrumbs.size <= 1) return false

        val previousNodeId = breadcrumbs[breadcrumbs.size - 2]
        val scriptId = _uiState.value.currentScript?.scriptId ?: return false

        navigateToSceneInternal(scriptId, previousNodeId, isBackNavigation = true)
        return true
    }

    /**
     * Jumps directly to the last visited checkpoint node.
     */
    fun jumpToCheckpoint() {
        val scriptId = _uiState.value.currentScript?.scriptId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val checkpoints = storyDao.getCheckpoints(scriptId).firstOrNull() ?: emptyList()
            val visited = _uiState.value.visitedNodeIds.toSet()
            val lastVisitedCheckpoint = checkpoints.lastOrNull { visited.contains(it.nodeId) }

            if (lastVisitedCheckpoint != null) {
                navigateToSceneInternal(scriptId, lastVisitedCheckpoint.nodeId, isBackNavigation = false)
            } else {
                // Fallback to start
                val startNode = _uiState.value.currentScript?.initialNodeId ?: "start"
                navigateToSceneInternal(scriptId, startNode, isBackNavigation = false)
            }
        }
    }

    /**
     * Restarts the current story script from the beginning.
     */
    fun restartScript() {
        val script = _uiState.value.currentScript ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    visitedNodeIds = listOf(script.initialNodeId),
                    navigationBreadcrumbs = listOf(script.initialNodeId)
                )
            }
            navigateToSceneInternal(script.scriptId, script.initialNodeId, isBackNavigation = false)
        }
    }

    /**
     * Performs full-text search across dialogue markdown and scene descriptions.
     */
    fun searchSceneContent(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val results = storyDao.searchSceneContent(query).firstOrNull() ?: emptyList()
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    /**
     * Parses and imports a custom raw Markdown script into the Room database.
     */
    fun importCustomMarkdownScript(
        scriptId: String,
        title: String,
        rawMarkdown: String,
        category: String = "CUSTOM"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val parsedDoc = MarkdownNarrativeParser.parseNarrativeDocument(
                    markdownText = rawMarkdown,
                    assetFileName = scriptId
                )
                val (scriptEntity, nodes, choices) = parsedDoc.toRoomEntities(
                    category = category,
                    isCustom = true
                )

                // Convert to StoryEntity structures
                val storyScript = StoryScript(
                    scriptId = scriptEntity.scriptId,
                    title = scriptEntity.title,
                    category = scriptEntity.category,
                    description = scriptEntity.description,
                    rawMarkdown = scriptEntity.rawMarkdown,
                    initialNodeId = scriptEntity.initialNodeId,
                    totalNodesCount = scriptEntity.totalNodesCount,
                    isCustom = true,
                    tags = scriptEntity.tags,
                    sceneSummaryMarkdown = scriptEntity.sceneSummaryMarkdown
                )

                val storyNodes = nodes.map { n: NarrativeNodeEntity ->
                    StorySceneNode(
                        compositeId = n.compositeId,
                        nodeId = n.nodeId,
                        scriptId = n.scriptId,
                        title = n.title,
                        speaker = n.speaker,
                        speakerMood = n.speakerMood,
                        dialogueMarkdown = n.dialogueMarkdown,
                        sceneDescriptionMarkdown = n.sceneDescriptionMarkdown,
                        category = n.category,
                        bgAtmosphere = n.bgAtmosphere,
                        soundEffectCue = n.soundEffectCue,
                        asciiArtScene = n.asciiArtScene,
                        weatherEffect = n.weatherEffect,
                        requiredStoryFlag = n.requiredStoryFlag,
                        isCheckpoint = n.isCheckpoint,
                        orderIndex = n.orderIndex
                    )
                }

                val storyChoices = choices.map { c: BranchingChoiceEntity ->
                    StoryBranchingChoice(
                        compositeChoiceId = c.compositeChoiceId,
                        scriptId = c.scriptId,
                        nodeId = c.nodeId,
                        choiceIndex = c.choiceIndex,
                        label = c.label,
                        targetNodeId = c.targetNodeId,
                        branchingConditionType = c.branchingConditionType,
                        conditionExpression = c.conditionExpression,
                        requiredItemId = c.requiredItemId,
                        requiredMinLevel = c.requiredMinLevel,
                        requiredMaxToxicity = c.requiredMaxToxicity,
                        requiredMinCredits = c.requiredMinCredits,
                        requiredSkillName = c.requiredSkillName,
                        requiredSkillLevel = c.requiredSkillLevel,
                        requiredStoryFlag = c.requiredStoryFlag,
                        successProbability = c.successProbability,
                        failureTargetNodeId = c.failureTargetNodeId,
                        toxicityDelta = c.toxicityDelta,
                        hpDelta = c.hpDelta,
                        creditsDelta = c.creditsDelta,
                        expReward = c.expReward,
                        rewardItemId = c.rewardItemId,
                        setStoryFlag = c.setStoryFlag,
                        actionTrigger = c.actionTrigger,
                        triggerPayload = c.triggerPayload
                    )
                }

                storyDao.saveFullScript(storyScript, storyNodes, storyChoices)
                loadScriptSync(storyScript.scriptId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to parse Markdown script: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Helper to import default bundled assets into Room.
     */
    private suspend fun importScriptFromAsset(fileName: String) = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val rawText = try {
            val stream: InputStream = app.assets.open(fileName)
            stream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "# Default Script\n\n## [start]\n### Welcome\n> Vault Terminal\nSystem initialized.\n\n* [Proceed](@room_1)"
        }

        val parsedDoc = MarkdownNarrativeParser.parseNarrativeDocument(
            markdownText = rawText,
            assetFileName = fileName
        )
        val (scriptEntity, nodes, choices) = parsedDoc.toRoomEntities()

        val storyScript = StoryScript(
            scriptId = scriptEntity.scriptId,
            title = scriptEntity.title,
            category = scriptEntity.category,
            description = scriptEntity.description,
            rawMarkdown = scriptEntity.rawMarkdown,
            initialNodeId = scriptEntity.initialNodeId,
            totalNodesCount = scriptEntity.totalNodesCount,
            author = scriptEntity.author,
            isCustom = false,
            tags = scriptEntity.tags,
            sceneSummaryMarkdown = scriptEntity.sceneSummaryMarkdown
        )

        val storyNodes = nodes.map { n: NarrativeNodeEntity ->
            StorySceneNode(
                compositeId = n.compositeId,
                nodeId = n.nodeId,
                scriptId = n.scriptId,
                title = n.title,
                speaker = n.speaker,
                speakerMood = n.speakerMood,
                dialogueMarkdown = n.dialogueMarkdown,
                sceneDescriptionMarkdown = n.sceneDescriptionMarkdown,
                category = n.category,
                bgAtmosphere = n.bgAtmosphere,
                soundEffectCue = n.soundEffectCue,
                asciiArtScene = n.asciiArtScene,
                weatherEffect = n.weatherEffect,
                requiredStoryFlag = n.requiredStoryFlag,
                isCheckpoint = n.isCheckpoint,
                orderIndex = n.orderIndex
            )
        }

        val storyChoices = choices.map { c: BranchingChoiceEntity ->
            StoryBranchingChoice(
                compositeChoiceId = c.compositeChoiceId,
                scriptId = c.scriptId,
                nodeId = c.nodeId,
                choiceIndex = c.choiceIndex,
                label = c.label,
                targetNodeId = c.targetNodeId,
                branchingConditionType = c.branchingConditionType,
                conditionExpression = c.conditionExpression,
                requiredItemId = c.requiredItemId,
                requiredMinLevel = c.requiredMinLevel,
                requiredMaxToxicity = c.requiredMaxToxicity,
                requiredMinCredits = c.requiredMinCredits,
                requiredSkillName = c.requiredSkillName,
                requiredSkillLevel = c.requiredSkillLevel,
                requiredStoryFlag = c.requiredStoryFlag,
                successProbability = c.successProbability,
                failureTargetNodeId = c.failureTargetNodeId,
                toxicityDelta = c.toxicityDelta,
                hpDelta = c.hpDelta,
                creditsDelta = c.creditsDelta,
                expReward = c.expReward,
                rewardItemId = c.rewardItemId,
                setStoryFlag = c.setStoryFlag,
                actionTrigger = c.actionTrigger,
                triggerPayload = c.triggerPayload
            )
        }

        storyDao.saveFullScript(storyScript, storyNodes, storyChoices)
    }

    private suspend fun seedDefaultMarkdownScripts() = withContext(Dispatchers.IO) {
        val defaultFiles = listOf("chemopank_world.md", "chemopank_world_expanded.md")
        for (file in defaultFiles) {
            try {
                importScriptFromAsset(file)
            } catch (ignored: Exception) {
                // Ignore missing file during early tests
            }
        }
    }

    private fun reEvaluateCurrentChoices() {
        val currentNode = _uiState.value.currentNode ?: return
        val scriptId = _uiState.value.currentScript?.scriptId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rawChoices = storyDao.getChoicesForNodeDirect(scriptId, currentNode.nodeId)
            val player = _uiState.value.playerStats
            val inventory = _uiState.value.inventoryItemIds
            val flags = _uiState.value.storyFlags

            val evaluated = rawChoices.map { evaluateChoice(it, player, inventory, flags) }
            _uiState.update { it.copy(evaluatedChoices = evaluated) }
        }
    }

    /**
     * Updates player statistics dynamically (e.g. from combat or external game state).
     */
    fun updatePlayerState(player: Player) {
        _uiState.update { it.copy(playerStats = player) }
        reEvaluateCurrentChoices()
    }

    private suspend fun saveProgress(
        scriptId: String,
        nodeId: String,
        visited: List<String>,
        flags: Map<String, Boolean>
    ) {
        val progress = StoryProgress(
            profileId = 1,
            currentScriptId = scriptId,
            currentNodeId = nodeId,
            visitedNodeIdsJson = JSONArray(visited).toString(),
            storyFlagsJson = JSONObject(flags as Map<*, *>).toString(),
            lastInteractionTime = System.currentTimeMillis()
        )
        storyDao.insertOrUpdateProgress(progress)
    }

    private fun parseVisitedJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return listOf("start")
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            listOf("start")
        }
    }

    private fun parseFlagsJson(json: String?): Map<String, Boolean> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Boolean>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optBoolean(key, true)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
