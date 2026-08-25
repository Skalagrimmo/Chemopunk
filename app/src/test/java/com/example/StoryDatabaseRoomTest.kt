package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.room.StoryBranchingChoice
import com.example.data.room.StoryDao
import com.example.data.room.StoryDatabase
import com.example.data.room.StoryProgress
import com.example.data.room.StorySceneNode
import com.example.data.room.StoryScript
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying StoryDatabase, StoryDao, and StoryEntity Room implementations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryDatabaseRoomTest {

    private lateinit var db: StoryDatabase
    private lateinit var storyDao: StoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storyDao = db.storyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testStoreAndRetrieveMarkdownStoryScript() = runBlocking {
        val script = StoryScript(
            scriptId = "chemopank_main.md",
            title = "Chemopank: Sector 7 Infiltration",
            category = "CAMPAIGN",
            description = "Main campaign markdown story script",
            rawMarkdown = "# Chapter 1: The Breach\nWelcome to Sector 7...",
            initialNodeId = "vault_airlock",
            totalNodesCount = 3,
            author = "Wasteland Chronicler",
            tags = "cyberpunk,infiltration,mutagens"
        )

        storyDao.insertScript(script)

        val retrieved = storyDao.getScriptById("chemopank_main.md").first()
        assertNotNull(retrieved)
        assertEquals("Chemopank: Sector 7 Infiltration", retrieved?.title)
        assertEquals("CAMPAIGN", retrieved?.category)
        assertEquals("vault_airlock", retrieved?.initialNodeId)
    }

    @Test
    fun testSceneTextAndBranchingChoiceTriggers() = runBlocking {
        val script = StoryScript(
            scriptId = "bunker_archive.md",
            title = "Forbidden Bunker Log",
            category = "ARCHIVE"
        )

        val sceneNode = StorySceneNode(
            compositeId = "bunker_archive.md_vault_airlock",
            nodeId = "vault_airlock",
            scriptId = "bunker_archive.md",
            title = "Sub-Level Airlock",
            speaker = "AI SYSTEM NEXUS",
            speakerMood = "WARNING",
            dialogueMarkdown = "Decontamination protocol failed. Toxic mist detected in corridor 4B.",
            sceneDescriptionMarkdown = "Hazard lights strobe yellow across the reinforced blast door.",
            bgAtmosphere = "TOXIC_LAB",
            soundEffectCue = "AUDIO_SIREN",
            isCheckpoint = true,
            orderIndex = 0
        )

        val choice1 = StoryBranchingChoice(
            compositeChoiceId = "bunker_archive.md_vault_airlock_0",
            scriptId = "bunker_archive.md",
            nodeId = "vault_airlock",
            choiceIndex = 0,
            label = "Deploy chemical neutralizing flare",
            targetNodeId = "corridor_cleared",
            branchingConditionType = "INVENTORY_CHECK",
            requiredItemId = "chem_flare",
            actionTrigger = "PURGE_VALVES",
            toxicityDelta = -15,
            setStoryFlag = "AIRLOCK_PURGED"
        )

        val choice2 = StoryBranchingChoice(
            compositeChoiceId = "bunker_archive.md_vault_airlock_1",
            scriptId = "bunker_archive.md",
            nodeId = "vault_airlock",
            choiceIndex = 1,
            label = "Force open security hatch (Level 2+)",
            targetNodeId = "vent_shaft",
            branchingConditionType = "STAT_CHECK",
            requiredMinLevel = 2,
            requiredMaxToxicity = 60,
            actionTrigger = "ACTION_GAMEVIEW",
            failureTargetNodeId = "hatch_alarm_triggered",
            creditsDelta = 25
        )

        storyDao.saveFullScript(script, listOf(sceneNode), listOf(choice1, choice2))

        // 1. Search scene text
        val searchResults = storyDao.searchSceneContent("Decontamination protocol").first()
        assertEquals(1, searchResults.size)
        assertEquals("AI SYSTEM NEXUS", searchResults.first().speaker)
        assertTrue(searchResults.first().isCheckpoint)

        // 2. Action trigger queries
        val purgeChoices = storyDao.getChoicesByActionTrigger("bunker_archive.md", "PURGE_VALVES").first()
        assertEquals(1, purgeChoices.size)
        assertEquals("AIRLOCK_PURGED", purgeChoices.first().setStoryFlag)

        // 3. Relational Node with Choices query
        val nodeWithChoices = storyDao.getNodeWithChoices("bunker_archive.md", "vault_airlock").first()
        assertNotNull(nodeWithChoices)
        assertEquals(2, nodeWithChoices?.getScriptFilteredChoices()?.size)

        // 4. Eligible choices filtering
        val eligibleChoicesLvl1 = storyDao.getEligibleChoices("bunker_archive.md", "vault_airlock", playerLevel = 1, playerToxicity = 20).first()
        assertEquals(1, eligibleChoicesLvl1.size)

        val eligibleChoicesLvl3 = storyDao.getEligibleChoices("bunker_archive.md", "vault_airlock", playerLevel = 3, playerToxicity = 20).first()
        assertEquals(2, eligibleChoicesLvl3.size)
    }

    @Test
    fun testNarrativeProgressTracking() = runBlocking {
        val progress = StoryProgress(
            profileId = 1,
            currentScriptId = "chemopank_main.md",
            currentNodeId = "vault_airlock",
            visitedNodeIdsJson = "[\"start\",\"vault_airlock\"]",
            storyFlagsJson = "{\"PURGED\":true}"
        )

        storyDao.insertOrUpdateProgress(progress)

        val retrieved = storyDao.getProgress(1).first()
        assertNotNull(retrieved)
        assertEquals("chemopank_main.md", retrieved?.currentScriptId)
        assertEquals("vault_airlock", retrieved?.currentNodeId)

        storyDao.updateCurrentNode(1, "corridor_cleared")
        val updated = storyDao.getProgressDirect(1)
        assertEquals("corridor_cleared", updated?.currentNodeId)
    }
}
