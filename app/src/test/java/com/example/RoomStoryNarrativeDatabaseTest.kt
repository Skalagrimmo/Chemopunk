package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Player
import com.example.data.room.BranchingChoiceEntity
import com.example.data.room.CharacterProfileDao
import com.example.data.room.CharacterProfileEntity
import com.example.data.room.GameDatabase
import com.example.data.room.InventoryDao
import com.example.data.room.InventoryItemEntity
import com.example.data.room.StoryNarrativeDao
import com.example.data.room.StoryNarrativeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomStoryNarrativeDatabaseTest {

    private lateinit var db: GameDatabase
    private lateinit var storyDao: StoryNarrativeDao
    private lateinit var inventoryDao: InventoryDao
    private lateinit var profileDao: CharacterProfileDao
    private lateinit var repository: StoryNarrativeRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GameDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storyDao = db.storyNarrativeDao()
        inventoryDao = db.inventoryDao()
        profileDao = db.characterProfileDao()
        repository = StoryNarrativeRepository(storyDao, inventoryDao, profileDao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testImportAndPersistMarkdownScriptWithNodesAndChoices() = runBlocking {
        val markdown = """
            # Vault 101 Chemical Incident
            
            ## STORY_NODES
            
            ### Node: start
            - Title: Contaminated Airlock
            - Speaker: OVERSEER KANE
            - Mood: WARNING
            - Atmosphere: TOXIC_AIRLOCK
            - SFX: SIREN_ALARM
            - Checkpoint: true
            - Content: The containment valves in Sector 7 have failed. Toxic vapor is venting into the corridors.
            - Choice: [Purge chemical valves | Tox: -15 | Action: PURGE_VALVES](@node_purged)
            - Choice: [Override security door | Req: keycard_red | CR: +50](@node_vault)
            
            ### Node: node_purged
            - Title: Pressure Stabilized
            - Speaker: AI PROTOCOL
            - Mood: NORMAL
            - Content: Toxicity levels reduced by 15%.
            - Choice: [Proceed deeper](@start)
            
            ### Node: node_vault
            - Title: Deep Vault
            - Speaker: AUTOMATED VAULT
            - Mood: GLITCH
            - Content: Vault unlocked. High tech salvage detected.
            - Choice: [Take salvage | Reward: plasma_core | SetFlag: VAULT_CLEARED](@start)
        """.trimIndent()

        repository.importMarkdownScript(
            markdownText = markdown,
            scriptId = "vault_incident.md",
            fallbackTitle = "Vault 101 Chemical Incident",
            category = "CAMPAIGN"
        )

        // Verify Script entity in Room
        val scripts = repository.allScripts.first()
        assertEquals(1, scripts.size)
        val script = scripts.first()
        assertEquals("vault_incident.md", script.scriptId)
        assertEquals("Vault 101 Chemical Incident", script.title)
        assertEquals(3, script.totalNodesCount)

        // Verify Nodes
        val nodes = storyDao.getNodesForScript("vault_incident.md").first()
        assertEquals(3, nodes.size)

        val startNode = storyDao.getNodeDirect("vault_incident.md", "start")
        assertNotNull(startNode)
        assertEquals("Contaminated Airlock", startNode?.title)
        assertEquals("OVERSEER KANE", startNode?.speaker)
        assertEquals("WARNING", startNode?.speakerMood)
        assertTrue(startNode?.isCheckpoint ?: false)
        assertEquals("TOXIC_AIRLOCK", startNode?.bgAtmosphere)

        // Verify Choices
        val choices = storyDao.getChoicesForNodeDirect("vault_incident.md", "start")
        assertEquals(2, choices.size)
        assertEquals("node_purged", choices[0].targetNodeId)
        assertEquals(-15, choices[0].toxicityDelta)
        assertEquals("PURGE_VALVES", choices[0].actionTrigger)
        assertEquals("keycard_red", choices[1].requiredItemId)
    }

    @Test
    fun testExecuteChoiceWithPrerequisitesAndConsequences() = runBlocking {
        // Player without keycard
        val player = Player(
            hp = 100,
            maxHp = 100,
            toxicity = 40,
            level = 2,
            credits = 20
        )

        val keycardChoice = BranchingChoiceEntity(
            compositeChoiceId = "test_choice_01",
            scriptId = "test_script.md",
            nodeId = "start",
            choiceIndex = 0,
            label = "Open Security Door",
            targetNodeId = "vault_room",
            requiredItemId = "keycard_red",
            creditsDelta = 100,
            setStoryFlag = "SECURITY_OPENED"
        )

        // 1. Should fail because keycard is missing
        val failedResult = repository.executeChoice(keycardChoice, player)
        assertFalse(failedResult.success)
        assertTrue(failedResult.message.contains("Requires item: keycard_red"))

        // 2. Insert keycard into Room inventory
        inventoryDao.insertItem(
            InventoryItemEntity(
                itemId = "keycard_red",
                name = "Red Keycard",
                type = "QUEST_ITEM",
                quantity = 1
            )
        )

        // 3. Re-execute choice -> should succeed now
        val successResult = repository.executeChoice(keycardChoice, player)
        assertTrue(successResult.success)
        assertEquals("vault_room", successResult.targetNodeId)
        assertEquals("true", successResult.newStoryFlags["SECURITY_OPENED"])

        // 4. Verify narrative progress updated in Room
        val progress = storyDao.getProgressDirect(1)
        assertNotNull(progress)
        assertEquals("vault_room", progress?.currentNodeId)
        assertTrue(progress?.visitedNodeIdsJson?.contains("vault_room") == true)
        assertTrue(progress?.storyFlagsJson?.contains("SECURITY_OPENED") == true)
    }

    @Test
    fun testSceneTextAndConditionalBranchingSchema() = runBlocking {
        val script = com.example.data.room.StoryScriptEntity(
            scriptId = "chem_reactor_crisis.md",
            title = "Reactor Core Crisis",
            category = "CAMPAIGN",
            description = "Emergency response script for sector reactor failure.",
            rawMarkdown = "# Reactor Core Crisis\n...",
            initialNodeId = "core_chamber",
            totalNodesCount = 2,
            tags = "reactor,crisis,wasteland",
            sceneSummaryMarkdown = "The cooling pipes are fractured."
        )

        val node1 = com.example.data.room.NarrativeNodeEntity(
            compositeId = "chem_reactor_crisis.md_core_chamber",
            nodeId = "core_chamber",
            scriptId = "chem_reactor_crisis.md",
            title = "Meltdown In Progress",
            speaker = "CHIEF ENGINEER VANCE",
            speakerMood = "WARNING",
            dialogueMarkdown = "Core temperature reaching critical mass! We need to bypass the coolant regulators immediately.",
            sceneDescriptionMarkdown = "Steam and green coolant fluid are spraying from severed conduits.",
            bgAtmosphere = "REACTOR",
            soundEffectCue = "REACTOR_HUM",
            asciiArtScene = "[ ! REACTOR ! ]",
            weatherEffect = "RADIATION_FOG",
            isCheckpoint = true,
            orderIndex = 0
        )

        val choiceDirect = BranchingChoiceEntity(
            compositeChoiceId = "chem_reactor_crisis.md_core_chamber_0",
            scriptId = "chem_reactor_crisis.md",
            nodeId = "core_chamber",
            choiceIndex = 0,
            label = "Purge coolant conduits",
            targetNodeId = "coolant_stabilized",
            branchingConditionType = "DIRECT",
            actionTrigger = "PURGE_VALVES",
            triggerPayload = "{\"pressure_psi\": 120}",
            toxicityDelta = -20,
            hpDelta = 0,
            creditsDelta = 50,
            setStoryFlag = "REACTOR_PURGED"
        )

        val choiceConditional = BranchingChoiceEntity(
            compositeChoiceId = "chem_reactor_crisis.md_core_chamber_1",
            scriptId = "chem_reactor_crisis.md",
            nodeId = "core_chamber",
            choiceIndex = 1,
            label = "Emergency Cybernetic Override",
            targetNodeId = "mainframe_override",
            branchingConditionType = "STAT_CHECK",
            requiredMinLevel = 3,
            requiredMaxToxicity = 50,
            requiredSkillName = "HACKING",
            requiredSkillLevel = 2,
            failureTargetNodeId = "override_failed",
            actionTrigger = "UNLOCK_DOOR",
            rewardItemId = "reactor_fuel_cell"
        )

        storyDao.saveFullScript(script, listOf(node1), listOf(choiceDirect, choiceConditional))

        // 1. Verify Scene Text & Full-text search
        val searchResults = storyDao.searchSceneText("critical mass").first()
        assertEquals(1, searchResults.size)
        assertEquals("Meltdown In Progress", searchResults.first().title)
        assertEquals("REACTOR", searchResults.first().bgAtmosphere)
        assertTrue(searchResults.first().isCheckpoint)

        // 2. Verify Choice Trigger Queries
        val triggerChoices = storyDao.getChoicesByActionTrigger("chem_reactor_crisis.md", "PURGE_VALVES").first()
        assertEquals(1, triggerChoices.size)
        assertEquals("PURGE_VALVES", triggerChoices.first().actionTrigger)
        assertEquals("{\"pressure_psi\": 120}", triggerChoices.first().triggerPayload)

        // 3. Verify Relational Query (Node with Choices)
        val nodeWithChoices = storyDao.getNodeWithChoices("chem_reactor_crisis.md", "core_chamber").first()
        assertNotNull(nodeWithChoices)
        assertEquals("core_chamber", nodeWithChoices?.node?.nodeId)
        assertEquals(2, nodeWithChoices?.getScriptFilteredChoices()?.size)

        // 4. Test Player Eligibility Query
        val eligibleChoicesLowLevel = storyDao.getEligibleChoicesForPlayer("chem_reactor_crisis.md", "core_chamber", playerLevel = 1, playerToxicity = 20).first()
        assertEquals(1, eligibleChoicesLowLevel.size)
        assertEquals("Purge coolant conduits", eligibleChoicesLowLevel.first().label)

        val eligibleChoicesHighLevel = storyDao.getEligibleChoicesForPlayer("chem_reactor_crisis.md", "core_chamber", playerLevel = 4, playerToxicity = 20).first()
        assertEquals(2, eligibleChoicesHighLevel.size)
    }
}
