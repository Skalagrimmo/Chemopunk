package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Player
import com.example.data.room.GameDatabase
import com.example.data.room.StoryBranchingChoice
import com.example.data.room.StoryDao
import com.example.data.room.StoryDatabase
import com.example.data.room.StorySceneNode
import com.example.data.room.StoryScript
import com.example.viewmodel.SceneTransitionState
import com.example.viewmodel.StoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying StoryViewModel processing of Markdown story scripts from Room,
 * state transitions, scene navigation, and conditional choice logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryViewModelTest {

    private lateinit var context: Application
    private lateinit var storyDb: StoryDatabase
    private lateinit var gameDb: GameDatabase
    private lateinit var storyDao: StoryDao
    private lateinit var viewModel: StoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        storyDb = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDb = Room.inMemoryDatabaseBuilder(context, GameDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storyDao = storyDb.storyDao()
        viewModel = StoryViewModel(context, storyDao, gameDb, autoInitDatabase = false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        storyDb.close()
        gameDb.close()
    }

    @Test
    fun testScriptProcessingAndSceneNavigation() = runBlocking {
        val script = StoryScript(
            scriptId = "chem_bunker.md",
            title = "Sub-Level Bunker",
            category = "CAMPAIGN",
            initialNodeId = "room_1",
            totalNodesCount = 2
        )

        val node1 = StorySceneNode(
            compositeId = "chem_bunker.md_room_1",
            nodeId = "room_1",
            scriptId = "chem_bunker.md",
            title = "Decontamination Chamber",
            speaker = "COMMANDER RYDER",
            speakerMood = "WARNING",
            dialogueMarkdown = "The pressure seal is failing.",
            isCheckpoint = true,
            orderIndex = 0
        )

        val node2 = StorySceneNode(
            compositeId = "chem_bunker.md_room_2",
            nodeId = "room_2",
            scriptId = "chem_bunker.md",
            title = "Main Reactor Core",
            speaker = "AI NEXUS",
            speakerMood = "COLD",
            dialogueMarkdown = "Core temperature normal.",
            isCheckpoint = false,
            orderIndex = 1
        )

        val choiceToRoom2 = StoryBranchingChoice(
            compositeChoiceId = "chem_bunker.md_room_1_0",
            scriptId = "chem_bunker.md",
            nodeId = "room_1",
            choiceIndex = 0,
            label = "Proceed to Reactor Core",
            targetNodeId = "room_2",
            toxicityDelta = -10,
            hpDelta = 20,
            creditsDelta = 50,
            setStoryFlag = "DECON_PASSED"
        )

        storyDao.saveFullScript(script, listOf(node1, node2), listOf(choiceToRoom2))

        // Load script into ViewModel
        viewModel.loadScriptSync("chem_bunker.md", "room_1")

        val state1 = viewModel.uiState.value
        assertEquals("Sub-Level Bunker", state1.currentScript?.title)
        assertEquals("room_1", state1.currentNode?.nodeId)
        assertEquals("COMMANDER RYDER", state1.activeSpeaker)
        assertTrue(state1.isCheckpoint)
        assertEquals(1, state1.evaluatedChoices.size)
        assertTrue(state1.evaluatedChoices.first().isEligible)

        // Execute choice transition to Room 2
        viewModel.selectChoiceSync(choiceToRoom2)

        val state2 = viewModel.uiState.value
        assertEquals("room_2", state2.currentNode?.nodeId)
        assertEquals("AI NEXUS", state2.activeSpeaker)
        assertTrue(state2.storyFlags["DECON_PASSED"] == true)
        assertTrue(state2.visitedNodeIds.contains("room_2"))
        assertTrue(state2.isTerminalNode)

        // Test Breadcrumb back navigation
        val navigatedBack = viewModel.navigateBackSync()
        assertTrue(navigatedBack)

        val stateBack = viewModel.uiState.value
        assertEquals("room_1", stateBack.currentNode?.nodeId)
    }

    @Test
    fun testConditionalChoiceEvaluationAndStatPrerequisites() = runBlocking {
        val script = StoryScript(
            scriptId = "chem_security.md",
            title = "Security Vault",
            category = "CAMPAIGN",
            initialNodeId = "vault_door"
        )

        val node = StorySceneNode(
            compositeId = "chem_security.md_vault_door",
            nodeId = "vault_door",
            scriptId = "chem_security.md",
            title = "Blast Door",
            dialogueMarkdown = "A locked biometric console blocks the passage.",
            orderIndex = 0
        )

        // Choice requires Level 4 and item 'passcard'
        val highLevelChoice = StoryBranchingChoice(
            compositeChoiceId = "chem_security.md_vault_door_0",
            scriptId = "chem_security.md",
            nodeId = "vault_door",
            choiceIndex = 0,
            label = "Hack Terminal (Level 4)",
            targetNodeId = "vault_opened",
            requiredMinLevel = 4,
            requiredItemId = "passcard"
        )

        storyDao.saveFullScript(script, listOf(node), listOf(highLevelChoice))

        // Set low-level player
        viewModel.updatePlayerState(Player(level = 1, toxicity = 10))
        viewModel.loadScriptSync("chem_security.md", "vault_door")

        var state = viewModel.uiState.value
        assertEquals(1, state.evaluatedChoices.size)
        val choiceEvalLow = state.evaluatedChoices.first()
        assertFalse("Choice must not be eligible for level 1 player without passcard", choiceEvalLow.isEligible)
        assertNotNull(choiceEvalLow.lockReason)
        assertTrue(choiceEvalLow.lockReason?.contains("Level 4") == true)

        // Level up player
        viewModel.updatePlayerState(Player(level = 5, toxicity = 10))

        // Even with level 5, still missing 'passcard'
        viewModel.loadScriptSync("chem_security.md", "vault_door")
        val choiceEvalStillLocked = viewModel.uiState.value.evaluatedChoices.first()
        assertFalse(choiceEvalStillLocked.isEligible)
        assertTrue(choiceEvalStillLocked.lockReason?.contains("passcard") == true)
    }
}
