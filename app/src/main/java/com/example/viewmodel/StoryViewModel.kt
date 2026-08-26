package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Choice
import com.example.data.Player
import com.example.data.StoryNode
import com.example.data.narrative.MarkdownNarrativeParser
import com.example.data.room.BranchingChoiceEntity
import com.example.data.room.GameDatabase
import com.example.data.room.InventoryRepository
import com.example.data.room.NarrativeNodeEntity
import com.example.data.room.NarrativeProgressEntity
import com.example.data.room.StoryNarrativeDao
import com.example.data.room.StoryScriptEntity
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

enum class SceneTransitionState {
    IDLE,
    ENTERING,
    ACTIVE,
    TRANSITIONING,
    REQUIREMENT_FAILED,
    TERMINAL_NODE
}

data class EvaluatedChoiceUiModel(
    val choice: BranchingChoiceEntity,
    val isEligible: Boolean,
    val isHidden: Boolean,
    val lockReason: String? = null,
    val probabilityPercent: Int = (choice.successProbability * 100).toInt(),
    val hasActionTrigger: Boolean = !choice.actionTrigger.isNullOrBlank(),
    val consequenceSummary: String = buildConsequenceSummary(choice)
)

private fun buildConsequenceSummary(choice: BranchingChoiceEntity): String {
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

sealed class StoryActionTriggerEvent {
    data class SwitchToGameView(val payload: String?) : StoryActionTriggerEvent()
    data class InitiateCombat(val enemyId: String, val payload: String?) : StoryActionTriggerEvent()
    data class TriggerEnvironmentalAction(val actionName: String, val payload: String?) : StoryActionTriggerEvent()
    data class ItemAcquired(val itemId: String, val count: Int = 1) : StoryActionTriggerEvent()
    data class PlaySoundCue(val cue: String) : StoryActionTriggerEvent()
    data class StoryCompleted(val scriptTitle: String) : StoryActionTriggerEvent()
    data class Notification(val message: String, val isWarning: Boolean = false) : StoryActionTriggerEvent()
}

data class StoryUiState(
    val isLoading: Boolean = true,
    val currentScript: StoryScriptEntity? = null,
    val currentNode: NarrativeNodeEntity? = null,
    val evaluatedChoices: List<EvaluatedChoiceUiModel> = emptyList(),
    val availableScripts: List<StoryScriptEntity> = emptyList(),
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
    val searchResults: List<NarrativeNodeEntity> = emptyList(),
    val playerStats: Player = Player(),
    val inventoryItemIds: Set<String> = emptySet(),
    val rawMarkdownPreview: String = "",
    val errorMessage: String? = null
)

class StoryViewModel(
    application: Application,
    private val storyDao: StoryNarrativeDao = GameDatabase.getDatabase(application).storyNarrativeDao(),
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

    fun initializeStoryDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingScripts = storyDao.getAllScripts().firstOrNull() ?: emptyList()
                if (existingScripts.isEmpty()) {
                    seedDefaultMarkdownScripts()
                }

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

    private fun observeScriptsList() {
        viewModelScope.launch {
            storyDao.getAllScripts().collectLatest { scripts ->
                _uiState.update { it.copy(availableScripts = scripts) }
            }
        }
    }

    private fun observeInventoryAndPlayer() {
        viewModelScope.launch {
            inventoryRepository.allInventoryItems.collectLatest { items ->
                val itemIds = items.map { it.itemId }.toSet()
                _uiState.update { it.copy(inventoryItemIds = itemIds) }
                reEvaluateCurrentChoices()
            }
        }
    }

    fun loadScript(scriptId: String, initialNodeId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            loadScriptSync(scriptId, initialNodeId)
        }
    }

    suspend fun loadScriptSync(scriptId: String, initialNodeId: String? = null) {
        _uiState.update { it.copy(isLoading = true, transitionState = SceneTransitionState.TRANSITIONING) }

        var script = storyDao.getScriptByIdDirect(scriptId)
        if (script == null) {
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

    fun navigateToScene(nodeId: String) {
        val currentScriptId = _uiState.value.currentScript?.scriptId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            navigateToSceneInternal(currentScriptId, nodeId, isBackNavigation = false)
        }
    }

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

        saveProgress(scriptId, nodeId, updatedVisited, storyFlags)
    }

    fun selectChoice(choice: BranchingChoiceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            selectChoiceSync(choice)
        }
    }

    suspend fun selectChoiceSync(choice: BranchingChoiceEntity) {
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

        val newPlayer = player.copy(
            hp = (player.hp + choice.hpDelta).coerceIn(0, player.maxHp),
            toxicity = (player.toxicity + choice.toxicityDelta).coerceIn(0, 100),
            credits = (player.credits + choice.creditsDelta).coerceAtLeast(0),
            exp = player.exp + choice.expReward
        )
        _uiState.update { it.copy(playerStats = newPlayer) }

        val updatedFlags = flags.toMutableMap()
        choice.setStoryFlag?.let { flag ->
            if (flag.isNotBlank()) updatedFlags[flag] = true
        }
        choice.clearStoryFlag?.let { flag ->
            if (flag.isNotBlank()) updatedFlags.remove(flag)
        }
        _uiState.update { it.copy(storyFlags = updatedFlags) }

        choice.rewardItemId?.let { rewardId ->
            if (rewardId.isNotBlank()) {
                _actionEvents.send(StoryActionTriggerEvent.ItemAcquired(rewardId, 1))
            }
        }

        choice.actionTrigger?.let { trigger ->
            if (trigger.isNotBlank()) {
                dispatchActionTrigger(trigger, choice.triggerPayload)
            }
        }

        val scriptId = choice.scriptId.ifBlank { currentState.currentScript?.scriptId ?: "chemopank_world.md" }
        navigateToSceneInternal(scriptId, targetNodeId, isBackNavigation = false)
    }

    private fun evaluateChoice(
        choice: BranchingChoiceEntity,
        player: Player,
        inventory: Set<String>,
        flags: Map<String, Boolean>
    ): EvaluatedChoiceUiModel {
        val missingRequirements = mutableListOf<String>()

        if (choice.requiredMinLevel > 0 && player.level < choice.requiredMinLevel) {
            missingRequirements.add("Requires Level ${choice.requiredMinLevel}")
        }

        if (player.toxicity > choice.requiredMaxToxicity) {
            missingRequirements.add("Toxicity too high (> ${choice.requiredMaxToxicity}%)")
        }

        if (choice.requiredMinCredits > 0 && player.credits < choice.requiredMinCredits) {
            missingRequirements.add("Requires ${choice.requiredMinCredits} Credits")
        }

        choice.requiredItemId?.let { requiredItem ->
            if (requiredItem.isNotBlank() && !inventory.contains(requiredItem)) {
                missingRequirements.add("Requires: $requiredItem")
            }
        }

        choice.requiredStoryFlag?.let { flag ->
            if (flag.isNotBlank() && flags[flag] != true) {
                missingRequirements.add("Prerequisite flag '$flag' not discovered")
            }
        }

        val isEligible = missingRequirements.isEmpty()
        val lockReason = if (!isEligible) missingRequirements.joinToString(" \u2022 ") else null

        return EvaluatedChoiceUiModel(
            choice = choice,
            isEligible = isEligible,
            isHidden = choice.isHiddenIfUnavailable && !isEligible,
            lockReason = lockReason
        )
    }

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

    fun jumpToCheckpoint() {
        val scriptId = _uiState.value.currentScript?.scriptId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val checkpointsFlow = storyDao.getCheckpointsForScript(scriptId)
            val checkpoints = checkpointsFlow.firstOrNull() ?: emptyList()
            val visited = _uiState.value.visitedNodeIds.toSet()
            val lastVisitedCheckpoint = checkpoints.lastOrNull { visited.contains(it.nodeId) }

            if (lastVisitedCheckpoint != null) {
                navigateToSceneInternal(scriptId, lastVisitedCheckpoint.nodeId, isBackNavigation = false)
            } else {
                val startNode = _uiState.value.currentScript?.initialNodeId ?: "start"
                navigateToSceneInternal(scriptId, startNode, isBackNavigation = false)
            }
        }
    }

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

    fun searchSceneContent(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val results = storyDao.searchSceneText(query).firstOrNull() ?: emptyList()
            _uiState.update { it.copy(searchResults = results) }
        }
    }

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

                storyDao.saveFullScript(scriptEntity, nodes, choices)
                loadScriptSync(scriptEntity.scriptId)
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

        storyDao.saveFullScript(scriptEntity, nodes, choices)
    }

    private suspend fun seedDefaultMarkdownScripts() = withContext(Dispatchers.IO) {
        val defaultFiles = listOf("chemopank_world.md", "chemopank_world_expanded.md")
        for (file in defaultFiles) {
            try {
                importScriptFromAsset(file)
            } catch (ignored: Exception) {
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
        val progress = NarrativeProgressEntity(
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
