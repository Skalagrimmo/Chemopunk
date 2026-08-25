package com.example.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.data.Choice
import com.example.data.StoryNode

/**
 * Room Entity representing a full Story Script or Markdown Narrative Document.
 * Stores top-level metadata, raw Markdown text, author, and chapter classification.
 */
@Entity(
    tableName = "story_scripts",
    indices = [
        Index(value = ["category"]),
        Index(value = ["isCustom"])
    ]
)
data class StoryScriptEntity(
    @PrimaryKey val scriptId: String,
    val title: String,
    val category: String = "CAMPAIGN", // CAMPAIGN, AUDIO_LOG, ARCHIVE, QUEST, MANUAL, SURVIVAL_LOG
    val description: String = "",
    val rawMarkdown: String = "",
    val initialNodeId: String = "start",
    val totalNodesCount: Int = 0,
    val author: String = "Vault Overseer",
    val isCustom: Boolean = false,
    val tags: String = "cyberpunk,wasteland,narrative",
    val sceneSummaryMarkdown: String = "",
    val lastParsedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Room Entity representing an individual Scene or Dialogue Node parsed from Markdown.
 * Holds scene text, markdown body, speaker metadata, environmental atmosphere, and checkpoint state.
 */
@Entity(
    tableName = "narrative_nodes",
    indices = [
        Index(value = ["scriptId"]),
        Index(value = ["scriptId", "nodeId"], unique = true),
        Index(value = ["speaker"]),
        Index(value = ["isCheckpoint"])
    ]
)
data class NarrativeNodeEntity(
    @PrimaryKey val compositeId: String, // "${scriptId}_${nodeId}"
    val nodeId: String,
    val scriptId: String,
    val title: String,
    val speaker: String? = null,
    val speakerMood: String = "NORMAL", // NORMAL, WARNING, AGGRESSIVE, WHISPER, GLITCH, RADIO, COLD
    val dialogueMarkdown: String = "", // Primary scene text / dialogue content
    val sceneDescriptionMarkdown: String = "", // Auxiliary environmental context or lore snippet
    val category: String = "STORY",
    val bgAtmosphere: String = "SECTOR_7_LAB", // TOXIC_LAB, BUNKER, TERMINAL, REACTOR, WASTELAND_STORM
    val soundEffectCue: String? = null, // AUDIO_SIREN, REACTOR_HUM, RADIO_STATIC, GIGER_CLICK
    val asciiArtScene: String? = null, // Optional ASCII art rendering header for the scene
    val weatherEffect: String = "CLEAR", // CLEAR, ACID_RAIN, RADIATION_FOG, SMOKE_HAZE
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
        fun fromDomainStoryNode(scriptId: String, node: StoryNode, orderIndex: Int = 0): NarrativeNodeEntity {
            return NarrativeNodeEntity(
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
 * Room Entity representing a Branching Narrative Choice / Dialogue Trigger.
 * Contains choice text, targets, action triggers, and conditional branching requirements.
 */
@Entity(
    tableName = "branching_choices",
    indices = [
        Index(value = ["scriptId"]),
        Index(value = ["scriptId", "nodeId"]),
        Index(value = ["actionTrigger"]),
        Index(value = ["requiredStoryFlag"]),
        Index(value = ["setStoryFlag"])
    ]
)
data class BranchingChoiceEntity(
    @PrimaryKey val compositeChoiceId: String, // "${scriptId}_${nodeId}_${choiceIndex}"
    val scriptId: String,
    val nodeId: String,
    val choiceIndex: Int,
    val label: String,
    val targetNodeId: String,
    
    // --- Conditional Branching Logic & Rules ---
    val branchingConditionType: String = "DIRECT", // DIRECT, STAT_CHECK, FLAG_CHECK, INVENTORY_CHECK, CHANCE_PROBABILITY, COMPOSITE
    val conditionExpression: String? = null, // e.g. "TOXICITY < 50 AND HAS_ITEM:medkit"
    val requiredItemId: String? = null,
    val requiredMinLevel: Int = 0,
    val requiredMaxToxicity: Int = 100,
    val requiredMinCredits: Int = 0,
    val requiredSkillName: String? = null, // e.g. "CHEMISTRY", "HACKING", "MEDICINE"
    val requiredSkillLevel: Int = 0,
    val requiredStoryFlag: String? = null,
    val successProbability: Float = 1.0f, // 0.0 to 1.0 for probability/dice checks
    val failureTargetNodeId: String? = null, // Alternative target node on conditional/roll failure
    val isHiddenIfUnavailable: Boolean = false,
    val customDisabledText: String? = null,

    // --- State Deltas & Gameplay Consequences ---
    val toxicityDelta: Int = 0,
    val hpDelta: Int = 0,
    val creditsDelta: Int = 0,
    val expReward: Int = 0,
    val rewardItemId: String? = null,

    // --- Story Progression Flags ---
    val setStoryFlag: String? = null,
    val clearStoryFlag: String? = null,

    // --- Choice Triggers & Action Hooks ---
    val actionTrigger: String? = null, // e.g. "ACTION_GAMEVIEW", "PURGE_VALVES", "COMBAT_ENCOUNTER", "HEAL_FIELD", "UNLOCK_DOOR", "SPAWN_NPC"
    val triggerPayload: String? = null // Optional JSON or param payload for the action trigger
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
        fun fromDomainChoice(scriptId: String, nodeId: String, choiceIndex: Int, choice: Choice): BranchingChoiceEntity {
            return BranchingChoiceEntity(
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
 * Room Entity tracking persistent narrative state, visited nodes, story flags, and player decisions.
 */
@Entity(tableName = "narrative_progress")
data class NarrativeProgressEntity(
    @PrimaryKey val profileId: Int = 1,
    val currentScriptId: String = "chemopank_world.md",
    val currentNodeId: String = "start",
    val visitedNodeIdsJson: String = "[\"start\"]", // JSON array of visited node IDs
    val storyFlagsJson: String = "{}", // JSON map of flagKey -> value
    val decisionHistoryJson: String = "[]", // JSON array of past decision summaries
    val lastInteractionTime: Long = System.currentTimeMillis()
)

/**
 * Relational model pairing a Narrative Node (Scene) with all of its associated Branching Choices.
 *
 * WARNING: Room @Relation cannot perform composite key joins.
 * The `choices` field is joined on `nodeId` alone, which is NOT unique across scripts.
 * This means `choices` may contain entries from OTHER scripts that share the same nodeId.
 * Always use [getScriptFilteredChoices] to get correctly filtered choices.
 * Direct access to `.choices` without filtering will produce incorrect results
 * when multiple scripts define nodes with overlapping nodeIds.
 */
data class NarrativeNodeWithChoices(
    @Embedded val node: NarrativeNodeEntity,
    @Relation(
        parentColumn = "nodeId",
        entityColumn = "nodeId"
    )
    val choices: List<BranchingChoiceEntity>
) {
    /**
     * Returns only the choices that belong to this node's script, filtering out
     * cross-script leaks caused by the single-column @Relation join.
     */
    fun getScriptFilteredChoices(): List<BranchingChoiceEntity> {
        return choices.filter { it.scriptId == node.scriptId }
    }
}

/**
 * Relational model pairing a Story Script with all its scenes (nodes) and branching choices.
 */
data class StoryScriptWithNodesAndChoices(
    @Embedded val script: StoryScriptEntity,
    @Relation(
        parentColumn = "scriptId",
        entityColumn = "scriptId"
    )
    val nodes: List<NarrativeNodeEntity>,
    @Relation(
        parentColumn = "scriptId",
        entityColumn = "scriptId"
    )
    val choices: List<BranchingChoiceEntity>
)
