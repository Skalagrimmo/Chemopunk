package com.example.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for Markdown story scripts, scene text nodes,
 * choice triggers, and conditional branching logic.
 */
@Dao
interface StoryDao {

    // ==========================================
    // 1. STORY SCRIPTS (MARKDOWN DOCUMENTS)
    // ==========================================

    @Query("SELECT * FROM story_scripts ORDER BY isCustom DESC, title ASC")
    fun getAllScripts(): Flow<List<StoryScript>>

    @Query("SELECT * FROM story_scripts WHERE category = :category ORDER BY title ASC")
    fun getScriptsByCategory(category: String): Flow<List<StoryScript>>

    @Query("SELECT * FROM story_scripts WHERE scriptId = :scriptId LIMIT 1")
    fun getScriptById(scriptId: String): Flow<StoryScript?>

    @Query("SELECT * FROM story_scripts WHERE scriptId = :scriptId LIMIT 1")
    suspend fun getScriptByIdDirect(scriptId: String): StoryScript?

    @Query("SELECT * FROM story_scripts WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchScripts(query: String): Flow<List<StoryScript>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: StoryScript)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScripts(scripts: List<StoryScript>)

    @Query("DELETE FROM story_scripts WHERE scriptId = :scriptId")
    suspend fun deleteScript(scriptId: String)

    @Query("DELETE FROM story_scripts")
    suspend fun deleteAllScripts()

    // ==========================================
    // 2. SCENE NODES (SCENE TEXT & ATMOSPHERE)
    // ==========================================

    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId ORDER BY orderIndex ASC")
    fun getNodesForScript(scriptId: String): Flow<List<StorySceneNode>>

    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId AND nodeId = :nodeId LIMIT 1")
    fun getNode(scriptId: String, nodeId: String): Flow<StorySceneNode?>

    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId AND nodeId = :nodeId LIMIT 1")
    suspend fun getNodeDirect(scriptId: String, nodeId: String): StorySceneNode?

    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId AND isCheckpoint = 1 ORDER BY orderIndex ASC")
    fun getCheckpoints(scriptId: String): Flow<List<StorySceneNode>>

    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId AND speaker = :speaker ORDER BY orderIndex ASC")
    fun getNodesBySpeaker(scriptId: String, speaker: String): Flow<List<StorySceneNode>>

    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId AND bgAtmosphere = :atmosphere ORDER BY orderIndex ASC")
    fun getNodesByAtmosphere(scriptId: String, atmosphere: String): Flow<List<StorySceneNode>>

    @Query("SELECT * FROM story_scene_nodes WHERE dialogueMarkdown LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR sceneDescriptionMarkdown LIKE '%' || :query || '%'")
    fun searchSceneContent(query: String): Flow<List<StorySceneNode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: StorySceneNode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<StorySceneNode>)

    @Query("DELETE FROM story_scene_nodes WHERE scriptId = :scriptId AND nodeId = :nodeId")
    suspend fun deleteNode(scriptId: String, nodeId: String)

    @Query("DELETE FROM story_scene_nodes WHERE scriptId = :scriptId")
    suspend fun deleteNodesForScript(scriptId: String)

    // ==========================================
    // 3. BRANCHING CHOICES & TRIGGERS
    // ==========================================

    @Query("SELECT * FROM story_branching_choices WHERE scriptId = :scriptId AND nodeId = :nodeId ORDER BY choiceIndex ASC")
    fun getChoicesForNode(scriptId: String, nodeId: String): Flow<List<StoryBranchingChoice>>

    @Query("SELECT * FROM story_branching_choices WHERE scriptId = :scriptId AND nodeId = :nodeId ORDER BY choiceIndex ASC")
    suspend fun getChoicesForNodeDirect(scriptId: String, nodeId: String): List<StoryBranchingChoice>

    @Query("SELECT * FROM story_branching_choices WHERE scriptId = :scriptId ORDER BY nodeId ASC, choiceIndex ASC")
    fun getAllChoicesForScript(scriptId: String): Flow<List<StoryBranchingChoice>>

    @Query("SELECT * FROM story_branching_choices WHERE scriptId = :scriptId AND actionTrigger = :actionTrigger")
    fun getChoicesByActionTrigger(scriptId: String, actionTrigger: String): Flow<List<StoryBranchingChoice>>

    @Query("SELECT * FROM story_branching_choices WHERE scriptId = :scriptId AND actionTrigger IS NOT NULL AND actionTrigger != ''")
    fun getActionTriggerChoices(scriptId: String): Flow<List<StoryBranchingChoice>>

    @Query("SELECT * FROM story_branching_choices WHERE scriptId = :scriptId AND (requiredItemId IS NOT NULL OR requiredMinLevel > 0 OR requiredStoryFlag IS NOT NULL)")
    fun getConditionalChoices(scriptId: String): Flow<List<StoryBranchingChoice>>

    @Query("""
        SELECT * FROM story_branching_choices 
        WHERE scriptId = :scriptId 
          AND nodeId = :nodeId 
          AND requiredMinLevel <= :playerLevel 
          AND requiredMaxToxicity >= :playerToxicity
        ORDER BY choiceIndex ASC
    """)
    fun getEligibleChoices(
        scriptId: String,
        nodeId: String,
        playerLevel: Int,
        playerToxicity: Int
    ): Flow<List<StoryBranchingChoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChoice(choice: StoryBranchingChoice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChoices(choices: List<StoryBranchingChoice>)

    @Query("DELETE FROM story_branching_choices WHERE scriptId = :scriptId AND nodeId = :nodeId")
    suspend fun deleteChoicesForNode(scriptId: String, nodeId: String)

    @Query("DELETE FROM story_branching_choices WHERE scriptId = :scriptId")
    suspend fun deleteChoicesForScript(scriptId: String)

    // ==========================================
    // 4. RELATIONAL GRAPH QUERIES (@Transaction)
    // ==========================================

    @Transaction
    @Query("SELECT * FROM story_scene_nodes WHERE scriptId = :scriptId AND nodeId = :nodeId LIMIT 1")
    fun getNodeWithChoices(scriptId: String, nodeId: String): Flow<StoryNodeWithChoices?>

    @Transaction
    @Query("SELECT * FROM story_scripts WHERE scriptId = :scriptId LIMIT 1")
    fun getScriptWithGraph(scriptId: String): Flow<StoryScriptWithGraph?>

    // ==========================================
    // 5. NARRATIVE PROGRESS & FLAGS
    // ==========================================

    @Query("SELECT * FROM story_progress WHERE profileId = :profileId LIMIT 1")
    fun getProgress(profileId: Int = 1): Flow<StoryProgress?>

    @Query("SELECT * FROM story_progress WHERE profileId = :profileId LIMIT 1")
    suspend fun getProgressDirect(profileId: Int = 1): StoryProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: StoryProgress)

    @Query("UPDATE story_progress SET currentNodeId = :nodeId, lastInteractionTime = :timestamp WHERE profileId = :profileId")
    suspend fun updateCurrentNode(profileId: Int = 1, nodeId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM story_progress WHERE profileId = :profileId")
    suspend fun resetProgress(profileId: Int = 1)

    // ==========================================
    // 6. ATOMIC SCRIPT IMPORT & TRANSACTIONS
    // ==========================================

    @Transaction
    suspend fun saveFullScript(
        script: StoryScript,
        nodes: List<StorySceneNode>,
        choices: List<StoryBranchingChoice>
    ) {
        insertScript(script)
        deleteNodesForScript(script.scriptId)
        insertNodes(nodes)
        deleteChoicesForScript(script.scriptId)
        insertChoices(choices)
    }

    @Transaction
    suspend fun deleteFullScript(scriptId: String) {
        deleteChoicesForScript(scriptId)
        deleteNodesForScript(scriptId)
        deleteScript(scriptId)
    }
}
