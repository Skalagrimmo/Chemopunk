package com.example.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.data.Choice
import com.example.data.StoryNode

/**
 * Room Entity representing a full Markdown story script document.
 */
@Entity(
    tableName = "story_scripts",
    indices = [
        Index(value = ["category"]),
        Index(value = ["isCustom"])
    ]
)
data class StoryScript(
    @PrimaryKey val scriptId: String,
    val title: String,
    val category: String = "CAMPAIGN",
    val description: String = "",
    val rawMarkdown: String = "",
    val initialNodeId: String = "start",
    val totalNodesCount: Int = 0,
    val author: String = "Vault Archivist",
    val isCustom: Boolean = false,
    val tags: String = "cyberpunk,wasteland,narrative",
    val sceneSummaryMarkdown: String = "",
    val lastParsedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Room Entity representing an individual scene/dialogue node within a Markdown story script.
 */
@Entity(
    tableName = "story_scene_nodes",
    indices = [
        Index(value = ["scriptId"]),
        Index(value = ["scriptId", "nodeId"], unique = true),
        Index(value = ["speaker"]),
        Index(value = ["isCheckpoint"])
    ]
)
data class StorySceneNode(
    @PrimaryKey val compositeId: String, // "${scriptId}_${nodeId}"
    val nodeId: String,
    val scriptId: String,
    val title: String,
    val speaker: String? = null,
    val speakerMood: String = "NORMAL",
    val dialogueMarkdown: String = "",
    val sceneDescriptionMarkdown: String = "",
    val category: String = "STORY",
    val bgAtmosphere: String = "SECTOR_7_LAB",
    val soundEffectCue: String? = null,
    val asciiArtScene: String? = null,
    val weatherEffect: String = "CLEAR",
    val requiredStoryFlag: String? = null,
    val isCheckpoint: Boolean = false,
    val orderIndex: Int = 0
) {
    fun toDomainStoryNode(choices: List<Choice> = emptyList()): StoryNode {
        return StoryNode(
            id = nodeId,
            title = title,
            content = dialogueMarkdown,
            speaker = speaker,
            category = category,
            mood = speakerMood,
            choices = choices,
            rawMarkdown = dialogueMarkdown,
            bgAtmosphere = bgAtmosphere,
            soundEffectCue = soundEffectCue,
            requiredStoryFlag = requiredStoryFlag,
            isCheckpoint = isCheckpoint
        )
    }

    companion object {
        fun fromDomainStoryNode(scriptId: String, node: StoryNode, orderIndex: Int = 0): StorySceneNode {
            return StorySceneNode(
                compositeId = "${scriptId}_${node.id}",
                nodeId = node.id,
                scriptId = scriptId,
                title = node.title,
                speaker = node.speaker,
                speakerMood = node.mood,
                dialogueMarkdown = node.content,
                sceneDescriptionMarkdown = "",
                category = node.category,
                bgAtmosphere = node.bgAtmosphere,
                soundEffectCue = node.soundEffectCue,
                asciiArtScene = null,
                weatherEffect = "CLEAR",
                requiredStoryFlag = node.requiredStoryFlag,
                isCheckpoint = node.isCheckpoint,
                orderIndex = orderIndex
            )
        }
    }
}

/**
 * Room Entity representing a branching choice, choice trigger, and conditional branching logic.
 */
@Entity(
    tableName = "story_branching_choices",
    indices = [
        Index(value = ["scriptId"]),
        Index(value = ["scriptId", "nodeId"]),
        Index(value = ["actionTrigger"]),
        Index(value = ["requiredStoryFlag"]),
        Index(value = ["setStoryFlag"])
    ]
)
data class StoryBranchingChoice(
    @PrimaryKey val compositeChoiceId: String, // "${scriptId}_${nodeId}_${choiceIndex}"
    val scriptId: String,
    val nodeId: String,
    val choiceIndex: Int,
    val label: String,
    val targetNodeId: String,

    // --- Conditional Branching Logic ---
    val branchingConditionType: String = "DIRECT", // DIRECT, STAT_CHECK, FLAG_CHECK, INVENTORY_CHECK, CHANCE_PROBABILITY
    val conditionExpression: String? = null,
    val requiredItemId: String? = null,
    val requiredMinLevel: Int = 0,
    val requiredMaxToxicity: Int = 100,
    val requiredMinCredits: Int = 0,
    val requiredSkillName: String? = null,
    val requiredSkillLevel: Int = 0,
    val requiredStoryFlag: String? = null,
    val successProbability: Float = 1.0f,
    val failureTargetNodeId: String? = null,
    val isHiddenIfUnavailable: Boolean = false,
    val customDisabledText: String? = null,

    // --- Gameplay Consequences & State Deltas ---
    val toxicityDelta: Int = 0,
    val hpDelta: Int = 0,
    val creditsDelta: Int = 0,
    val expReward: Int = 0,
    val rewardItemId: String? = null,

    // --- Story Progression Flags ---
    val setStoryFlag: String? = null,
    val clearStoryFlag: String? = null,

    // --- Action Triggers & System Events ---
    val actionTrigger: String? = null, // e.g., "PURGE_VALVES", "ACTION_GAMEVIEW", "COMBAT_ENCOUNTER", "UNLOCK_DOOR"
    val triggerPayload: String? = null
) {
    fun toDomainChoice(): Choice {
        return Choice(
            text = label,
            targetNodeId = targetNodeId,
            requiredItemId = requiredItemId,
            toxicityCost = toxicityDelta,
            hpReward = hpDelta,
            creditsReward = creditsDelta,
            actionTrigger = actionTrigger,
            requiredMinLevel = requiredMinLevel,
            requiredStoryFlag = requiredStoryFlag,
            setStoryFlag = setStoryFlag,
            rewardItemId = rewardItemId,
            failureTargetNodeId = failureTargetNodeId
        )
    }

    companion object {
        fun fromDomainChoice(scriptId: String, nodeId: String, choiceIndex: Int, choice: Choice): StoryBranchingChoice {
            return StoryBranchingChoice(
                compositeChoiceId = "${scriptId}_${nodeId}_${choiceIndex}",
                scriptId = scriptId,
                nodeId = nodeId,
                choiceIndex = choiceIndex,
                label = choice.text,
                targetNodeId = choice.targetNodeId,
                branchingConditionType = if (choice.requiredItemId != null) "INVENTORY_CHECK"
                    else if (choice.requiredStoryFlag != null) "FLAG_CHECK"
                    else if (choice.requiredMinLevel > 0) "STAT_CHECK"
                    else "DIRECT",
                requiredItemId = choice.requiredItemId,
                requiredMinLevel = choice.requiredMinLevel,
                requiredStoryFlag = choice.requiredStoryFlag,
                toxicityDelta = choice.toxicityCost,
                hpDelta = choice.hpReward,
                creditsDelta = choice.creditsReward,
                rewardItemId = choice.rewardItemId,
                setStoryFlag = choice.setStoryFlag,
                actionTrigger = choice.actionTrigger,
                failureTargetNodeId = choice.failureTargetNodeId
            )
        }
    }
}

/**
 * Room Entity tracking visited narrative nodes, current checkpoint, and active story flags.
 */
@Entity(tableName = "story_progress")
data class StoryProgress(
    @PrimaryKey val profileId: Int = 1,
    val currentScriptId: String = "chemopank_world.md",
    val currentNodeId: String = "start",
    val visitedNodeIdsJson: String = "[\"start\"]",
    val storyFlagsJson: String = "{}",
    val decisionHistoryJson: String = "[]",
    val lastInteractionTime: Long = System.currentTimeMillis()
)

/**
 * Relational pairing of a scene node and its child branching choices.
 *
 * WARNING: Room @Relation cannot perform composite key joins.
 * The `choices` field is joined on `nodeId` alone, which is NOT unique across scripts.
 * This means `choices` may contain entries from OTHER scripts that share the same nodeId.
 * Always use [getScriptFilteredChoices] to get correctly filtered choices.
 * Direct access to `.choices` without filtering will produce incorrect results
 * when multiple scripts define nodes with overlapping nodeIds.
 */
data class StoryNodeWithChoices(
    @Embedded val node: StorySceneNode,
    @Relation(
        parentColumn = "nodeId",
        entityColumn = "nodeId"
    )
    val choices: List<StoryBranchingChoice>
) {
    /**
     * Returns only the choices that belong to this node's script, filtering out
     * cross-script leaks caused by the single-column @Relation join.
     */
    fun getScriptFilteredChoices(): List<StoryBranchingChoice> {
        return choices.filter { it.scriptId == node.scriptId }
    }
}

/**
 * Relational model pairing a full story script with all its scene nodes and choices.
 */
data class StoryScriptWithGraph(
    @Embedded val script: StoryScript,
    @Relation(
        parentColumn = "scriptId",
        entityColumn = "scriptId"
    )
    val nodes: List<StorySceneNode>,
    @Relation(
        parentColumn = "scriptId",
        entityColumn = "scriptId"
    )
    val choices: List<StoryBranchingChoice>
)
