package com.example.data.room

import android.content.Context
import com.example.data.Player
import com.example.data.narrative.MarkdownNarrativeParser
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Result of evaluating a branching choice prerequisite.
 */
data class BranchEvaluation(
    val isAvailable: Boolean,
    val reason: String? = null,
    val hasItem: Boolean = true,
    val meetsLevel: Boolean = true,
    val meetsToxicity: Boolean = true,
    val meetsFlag: Boolean = true
)

/**
 * Result of executing a branching choice in the story engine.
 */
data class BranchExecutionResult(
    val success: Boolean,
    val targetNodeId: String,
    val message: String,
    val hpDelta: Int = 0,
    val toxicityDelta: Int = 0,
    val creditsDelta: Int = 0,
    val rewardItemId: String? = null,
    val newStoryFlags: Map<String, String> = emptyMap(),
    val actionTrigger: String? = null
)

/**
 * Repository mediating access between Room database narrative tables,
 * the Markdown script parser, and game state execution.
 */
class StoryNarrativeRepository(
    private val storyDao: StoryNarrativeDao,
    private val inventoryDao: InventoryDao? = null,
    private val profileDao: CharacterProfileDao? = null
) {

    val allScripts: Flow<List<StoryScriptEntity>> = storyDao.getAllScripts()
    val narrativeProgress: Flow<NarrativeProgressEntity?> = storyDao.getProgress()

    fun getNodesForScript(scriptId: String): Flow<List<NarrativeNodeEntity>> {
        return storyDao.getNodesForScript(scriptId)
    }

    fun getNode(scriptId: String, nodeId: String): Flow<NarrativeNodeEntity?> {
        return storyDao.getNode(scriptId, nodeId)
    }

    suspend fun getNodeDirect(scriptId: String, nodeId: String): NarrativeNodeEntity? {
        return storyDao.getNodeDirect(scriptId, nodeId)
    }

    fun getChoicesForNode(scriptId: String, nodeId: String): Flow<List<BranchingChoiceEntity>> {
        return storyDao.getChoicesForNode(scriptId, nodeId)
    }

    suspend fun getChoicesForNodeDirect(scriptId: String, nodeId: String): List<BranchingChoiceEntity> {
        return storyDao.getChoicesForNodeDirect(scriptId, nodeId)
    }

    fun getNodeWithChoices(scriptId: String, nodeId: String): Flow<NarrativeNodeWithChoices?> {
        return storyDao.getNodeWithChoices(scriptId, nodeId)
    }

    fun getScriptWithNodesAndChoices(scriptId: String): Flow<StoryScriptWithNodesAndChoices?> {
        return storyDao.getScriptWithNodesAndChoices(scriptId)
    }

    fun getChoicesByActionTrigger(scriptId: String, actionTrigger: String): Flow<List<BranchingChoiceEntity>> {
        return storyDao.getChoicesByActionTrigger(scriptId, actionTrigger)
    }

    fun searchSceneText(query: String): Flow<List<NarrativeNodeEntity>> {
        return storyDao.searchSceneText(query)
    }

    fun searchStoryScripts(query: String): Flow<List<StoryScriptEntity>> {
        return storyDao.searchStoryScripts(query)
    }

    fun getCheckpoints(scriptId: String): Flow<List<NarrativeNodeEntity>> {
        return storyDao.getCheckpointsForScript(scriptId)
    }

    fun getEligibleChoicesForPlayer(scriptId: String, nodeId: String, player: Player): Flow<List<BranchingChoiceEntity>> {
        return storyDao.getEligibleChoicesForPlayer(scriptId, nodeId, player.level, player.toxicity)
    }

    /**
     * Parses and imports a raw Markdown narrative script directly into the Room database.
     */
    suspend fun importMarkdownScript(
        markdownText: String,
        scriptId: String,
        fallbackTitle: String = "Narrative Log",
        category: String = "CAMPAIGN",
        description: String = "",
        isCustom: Boolean = false
    ) {
        val doc = MarkdownNarrativeParser.parseNarrativeDocument(markdownText, assetFileName = scriptId)

        val scriptEntity = StoryScriptEntity(
            scriptId = scriptId,
            title = if (doc.title != "Chemopunk Narrative Script") doc.title else fallbackTitle,
            category = category,
            description = description.ifEmpty { "Script containing ${doc.storyNodes.size} dialogue & branching nodes." },
            rawMarkdown = markdownText,
            initialNodeId = doc.storyNodes.keys.firstOrNull() ?: "start",
            totalNodesCount = doc.storyNodes.size,
            isCustom = isCustom,
            lastParsedTimestamp = System.currentTimeMillis()
        )

        val nodeEntities = mutableListOf<NarrativeNodeEntity>()
        val choiceEntities = mutableListOf<BranchingChoiceEntity>()

        doc.storyNodes.values.forEachIndexed { index, node ->
            val compId = "${scriptId}_${node.id}"
            nodeEntities.add(
                NarrativeNodeEntity(
                    compositeId = compId,
                    nodeId = node.id,
                    scriptId = scriptId,
                    title = node.title,
                    speaker = node.speaker,
                    speakerMood = node.mood,
                    dialogueMarkdown = node.content,
                    category = node.category,
                    bgAtmosphere = node.bgAtmosphere.ifEmpty {
                        when (node.category) {
                            "AUDIO_LOG" -> "RADIO_TRANSMISSION"
                            "ARCHIVE" -> "MAINFRAME_TERMINAL"
                            else -> "SECTOR_7_LAB"
                        }
                    },
                    soundEffectCue = node.soundEffectCue,
                    requiredStoryFlag = node.requiredStoryFlag,
                    isCheckpoint = node.isCheckpoint,
                    orderIndex = index
                )
            )

            node.choices.forEachIndexed { choiceIdx, choice ->
                val choiceCompId = "${scriptId}_${node.id}_$choiceIdx"
                choiceEntities.add(
                    BranchingChoiceEntity(
                        compositeChoiceId = choiceCompId,
                        scriptId = scriptId,
                        nodeId = node.id,
                        choiceIndex = choiceIdx,
                        label = choice.text,
                        targetNodeId = choice.targetNodeId,
                        requiredItemId = choice.requiredItemId,
                        toxicityDelta = choice.toxicityCost,
                        hpDelta = choice.hpReward,
                        creditsDelta = choice.creditsReward,
                        actionTrigger = choice.actionTrigger
                    )
                )
            }
        }

        // Atomically save script, nodes, and choices in Room
        storyDao.saveFullScript(scriptEntity, nodeEntities, choiceEntities)
    }

    /**
     * Evaluates whether a player meets all prerequisites to select a branching choice.
     */
    suspend fun evaluateChoicePrerequisites(
        choice: BranchingChoiceEntity,
        player: Player,
        storyFlags: Map<String, String> = emptyMap()
    ): BranchEvaluation {
        var hasItem = true
        var meetsLevel = true
        var meetsTox = true
        var meetsFlag = true
        val failureReasons = mutableListOf<String>()

        // 1. Required Item check
        if (!choice.requiredItemId.isNullOrBlank()) {
            val itemInDb = inventoryDao?.findItemDirect(choice.requiredItemId)
            if (itemInDb == null || itemInDb.quantity <= 0) {
                hasItem = false
                failureReasons.add("Requires item: ${choice.requiredItemId}")
            }
        }

        // 2. Minimum Level check
        if (choice.requiredMinLevel > 0 && player.level < choice.requiredMinLevel) {
            meetsLevel = false
            failureReasons.add("Requires Level ${choice.requiredMinLevel}")
        }

        // 3. Maximum Toxicity check (e.g. choice requires low radiation)
        if (player.toxicity > choice.requiredMaxToxicity) {
            meetsTox = false
            failureReasons.add("Toxicity too high (Max: ${choice.requiredMaxToxicity}%)")
        }

        // 4. Required Story Flag check
        if (!choice.requiredStoryFlag.isNullOrBlank()) {
            val flagVal = storyFlags[choice.requiredStoryFlag]
            if (flagVal == null || flagVal == "false" || flagVal == "0") {
                meetsFlag = false
                failureReasons.add("Locked by prerequisite story event: ${choice.requiredStoryFlag}")
            }
        }

        val available = hasItem && meetsLevel && meetsTox && meetsFlag
        return BranchEvaluation(
            isAvailable = available,
            reason = if (available) null else failureReasons.joinToString(", "),
            hasItem = hasItem,
            meetsLevel = meetsLevel,
            meetsToxicity = meetsTox,
            meetsFlag = meetsFlag
        )
    }

    /**
     * Executes a branching choice: validates requirements, applies consequences,
     * updates Room narrative progress, and transitions to target node.
     */
    suspend fun executeChoice(
        choice: BranchingChoiceEntity,
        player: Player,
        profileId: Int = 1
    ): BranchExecutionResult {
        val currentProgress = storyDao.getProgressDirect(profileId) ?: NarrativeProgressEntity(profileId = profileId)
        val flags = parseJsonMap(currentProgress.storyFlagsJson).toMutableMap()

        val evaluation = evaluateChoicePrerequisites(choice, player, flags)
        if (!evaluation.isAvailable) {
            val fallbackTarget = choice.failureTargetNodeId ?: choice.nodeId
            return BranchExecutionResult(
                success = false,
                targetNodeId = fallbackTarget,
                message = "Branch prerequisite failed: ${evaluation.reason}"
            )
        }

        // Set any defined story flag
        if (!choice.setStoryFlag.isNullOrBlank()) {
            flags[choice.setStoryFlag] = "true"
        }

        // Update visited nodes in Room
        val visitedList = parseJsonList(currentProgress.visitedNodeIdsJson).toMutableList()
        if (!visitedList.contains(choice.targetNodeId)) {
            visitedList.add(choice.targetNodeId)
        }

        // Record decision log
        val decisions = parseJsonList(currentProgress.decisionHistoryJson).toMutableList()
        decisions.add("[${choice.nodeId} -> ${choice.targetNodeId}] ${choice.label}")

        val updatedProgress = currentProgress.copy(
            currentScriptId = choice.scriptId,
            currentNodeId = choice.targetNodeId,
            visitedNodeIdsJson = JSONArray(visitedList).toString(),
            storyFlagsJson = JSONObject(flags as Map<*, *>).toString(),
            decisionHistoryJson = JSONArray(decisions).toString(),
            lastInteractionTime = System.currentTimeMillis()
        )
        storyDao.insertOrUpdateProgress(updatedProgress)

        // Give reward item if specified
        if (!choice.rewardItemId.isNullOrBlank() && inventoryDao != null) {
            val existing = inventoryDao.findItemDirect(choice.rewardItemId)
            if (existing != null) {
                inventoryDao.insertItem(existing.copy(quantity = existing.quantity + 1))
            } else {
                inventoryDao.insertItem(
                    InventoryItemEntity(
                        itemId = choice.rewardItemId,
                        name = choice.rewardItemId.replace("_", " ").capitalizeWords(),
                        type = "CONSUMABLE",
                        description = "Acquired from narrative choice in Sector 7."
                    )
                )
            }
        }

        return BranchExecutionResult(
            success = true,
            targetNodeId = choice.targetNodeId,
            message = "Transitioned to node '${choice.targetNodeId}'.",
            hpDelta = choice.hpDelta,
            toxicityDelta = choice.toxicityDelta,
            creditsDelta = choice.creditsDelta,
            rewardItemId = choice.rewardItemId,
            newStoryFlags = flags,
            actionTrigger = choice.actionTrigger
        )
    }

    /**
     * Initializes and synchronizes default story markdown assets from the app bundle into Room.
     */
    suspend fun syncDefaultAssetsFromContext(context: Context) {
        MarkdownNarrativeParser.AVAILABLE_STORY_ASSETS.forEach { asset ->
            val existing = storyDao.getScriptByIdDirect(asset.fileName)
            if (existing == null) {
                try {
                    val raw = context.assets.open(asset.fileName).bufferedReader().use { it.readText() }
                    importMarkdownScript(
                        markdownText = raw,
                        scriptId = asset.fileName,
                        fallbackTitle = asset.title,
                        category = asset.category,
                        description = asset.description,
                        isCustom = false
                    )
                } catch (e: Exception) {
                    // Fallback to sample script if asset file cannot be opened
                    val fallback = """
                        # ${asset.title}
                        ## STORY_NODES
                        ### Node: start
                        - Title: ${asset.title}
                        - Speaker: TERMINAL LOG
                        - Mood: NORMAL
                        - Content: Archival record ${asset.fileName} initialized.
                        - Choice: [Return to main terminal](@start)
                    """.trimIndent()
                    importMarkdownScript(
                        markdownText = fallback,
                        scriptId = asset.fileName,
                        fallbackTitle = asset.title,
                        category = asset.category,
                        description = asset.description,
                        isCustom = false
                    )
                }
            }
        }
    }

    /**
     * Resets or sets the narrative progress to a specific node.
     */
    suspend fun setCurrentStoryNode(scriptId: String, nodeId: String, profileId: Int = 1) {
        val currentProgress = storyDao.getProgressDirect(profileId) ?: NarrativeProgressEntity(profileId = profileId)
        val visitedList = parseJsonList(currentProgress.visitedNodeIdsJson).toMutableList()
        if (!visitedList.contains(nodeId)) {
            visitedList.add(nodeId)
        }

        val updated = currentProgress.copy(
            currentScriptId = scriptId,
            currentNodeId = nodeId,
            visitedNodeIdsJson = JSONArray(visitedList).toString(),
            lastInteractionTime = System.currentTimeMillis()
        )
        storyDao.insertOrUpdateProgress(updated)
    }

    private fun parseJsonList(jsonStr: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseJsonMap(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
        } catch (_: Exception) {}
        return map
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
