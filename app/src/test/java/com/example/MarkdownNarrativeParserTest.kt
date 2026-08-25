package com.example

import com.example.data.narrative.InlineElement
import com.example.data.narrative.MarkdownBlock
import com.example.data.narrative.MarkdownNarrativeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Kotlin-based Markdown parser's ability to ingest
 * narrative scripts, parse inline and block-level ASTs, and generate dialogue choices
 * and narrative events.
 */
class MarkdownNarrativeParserTest {

    @Test
    fun testParseInlineElementsWithStylingAndLinks() {
        val raw = "Welcome to **Sector 7**. Check `terminal_01` for *urgent* updates. [Purge Valve](@node_purge) or ==quarantine== immediately."
        val inlines = MarkdownNarrativeParser.parseInline(raw)

        assertTrue("Should produce multiple inline tokens", inlines.size >= 7)

        val hasBold = inlines.any { it is InlineElement.Bold && it.content == "Sector 7" }
        val hasCode = inlines.any { it is InlineElement.InlineCode && it.code == "terminal_01" }
        val hasItalic = inlines.any { it is InlineElement.Italic && it.content == "urgent" }
        val hasLink = inlines.any { it is InlineElement.Link && it.label == "Purge Valve" && it.target == "@node_purge" && it.isStoryLink }
        val hasHighlight = inlines.any { it is InlineElement.Highlight && it.content == "quarantine" }

        assertTrue("Bold parsed correctly", hasBold)
        assertTrue("Code parsed correctly", hasCode)
        assertTrue("Italic parsed correctly", hasItalic)
        assertTrue("Story link parsed correctly", hasLink)
        assertTrue("Highlight parsed correctly", hasHighlight)
    }

    @Test
    fun testVariableInterpolation() {
        val raw = "Operative {PLAYER_NAME}, your current HP is {HP} and Toxicity is {TOXICITY}%."
        val inlines = MarkdownNarrativeParser.parseInline(raw) { varName ->
            when (varName) {
                "PLAYER_NAME" -> "Vance-9"
                "HP" -> "85"
                "TOXICITY" -> "14"
                else -> varName
            }
        }

        val fullText = inlines.joinToString("") {
            when (it) {
                is InlineElement.Text -> it.content
                else -> ""
            }
        }

        assertTrue("Interpolated player name", fullText.contains("Vance-9"))
        assertTrue("Interpolated HP", fullText.contains("85"))
        assertTrue("Interpolated Toxicity", fullText.contains("14%"))
    }

    @Test
    fun testParseComplexChoiceMetadata() {
        val choiceStr = "[Override Blast Door | Req: keycard_alpha | ReqFlag: SEC_CLEARANCE | Tox: -10 | HP: +15 | CR: +50 | SetFlag: VAULT_OPENED | Action: UNLOCK_DOOR | Fail: node_breach](@node_reactor)"
        val choice = MarkdownNarrativeParser.parseChoiceString(choiceStr)

        assertNotNull("Choice should be parsed", choice)
        assertEquals("Override Blast Door", choice?.text)
        assertEquals("node_reactor", choice?.targetNodeId)
        assertEquals("keycard_alpha", choice?.requiredItemId)
        assertEquals("SEC_CLEARANCE", choice?.requiredStoryFlag)
        assertEquals(-10, choice?.toxicityCost)
        assertEquals(15, choice?.hpReward)
        assertEquals(50, choice?.creditsReward)
        assertEquals("VAULT_OPENED", choice?.setStoryFlag)
        assertEquals("UNLOCK_DOOR", choice?.actionTrigger)
        assertEquals("node_breach", choice?.failureTargetNodeId)
    }

    @Test
    fun testParseBlockElements() {
        val markdown = """
# SECTOR 7 ARCHIVES
## INCIDENT REPORT

> [DR. VANCE (HAZARD)]: Atmospheric seal compromised in Sub-Level 3!

Here is the operational checklist:
- Check bio-respirator seal
- Inject anti-toxin if toxicity > 50%
- Choice: [Activate emergency vent](@node_vent)

```terminal
SYS_OVERRIDE --FORCE --VALVE 04
STATUS: OK
```

| Component | Status |
| Reactor | Stable |
| Scrubbers | Offline |
        """.trimIndent()

        val blocks = MarkdownNarrativeParser.parseBlocks(markdown)

        assertTrue("Header 1 parsed", blocks.any { it is MarkdownBlock.Header && it.level == 1 && it.text == "SECTOR 7 ARCHIVES" })
        assertTrue("Header 2 parsed", blocks.any { it is MarkdownBlock.Header && it.level == 2 && it.text == "INCIDENT REPORT" })
        assertTrue("Blockquote parsed with speaker", blocks.any { it is MarkdownBlock.Blockquote && it.speaker == "DR. VANCE (HAZARD)" })
        assertTrue("Codeblock parsed", blocks.any { it is MarkdownBlock.CodeBlock && it.language == "terminal" })
        assertTrue("Choice action block parsed", blocks.any { it is MarkdownBlock.ChoiceAction && it.choice.targetNodeId == "node_vent" })
        assertTrue("Markdown table parsed", blocks.any { it is MarkdownBlock.MarkdownTable && it.headers.contains("Component") })
    }

    @Test
    fun testParseFullNarrativeDocumentAndEntities() {
        val script = """
# CHEMOPUNK SCRIPT TEST
## GAME_CONFIG
- initial_floor: 2
- max_toxicity: 100
- starting_hp: 120
- starting_credits: 75

## ITEM_DATABASE
### Item: stim_pack
- Name: Military Stim Pack
- Type: CONSUMABLE
- Value: 30
- Effect: Heal 40 HP, Reduce Toxicity by 20
- Description: High-potency restorative cocktail.

## ENEMY_DATABASE
### Enemy: cyber_stalker
- Name: Cyber Stalker
- HP: 60
- Attack: 18
- Armor: 4
- ToxicityDamage: 12
- ASCII_Glyph: K
- ExpReward: 45
- Loot: stim_pack

## STORY_NODES
### Node: start
- Title: Decontamination Deck
- Speaker: BIOS-AI
- Mood: WARNING
- Atmosphere: SECTOR_7_LAB
- SFX: alarm_pulse
- Checkpoint: true
- Content:
> [BIOS-AI (WARNING)]: Decontamination protocol engaged. Air scrubbers offline.

The corridor ahead is drenched in virulent chemical runoff.
- Choice: [Deploy Bio-Shield | Req: stim_pack | Tox: -20](@node_shield)
- Choice: [Enter the hazardous sector](@action_gameview)

### Node: node_shield
- Title: Bio-Shield Activated
- Speaker: SUIT OS
- Mood: NORMAL
- Content: Bio-shield deployed. Radiation levels stabilized.
- Choice: [Advance into dungeon](@action_gameview)
        """.trimIndent()

        val doc = MarkdownNarrativeParser.parseNarrativeDocument(script, "test_script.md")

        assertEquals("CHEMOPUNK SCRIPT TEST", doc.title)
        assertEquals(2, doc.config.initialFloor)
        assertEquals(120, doc.config.startingHp)
        assertEquals(75, doc.config.startingCredits)

        // Item check
        assertTrue("Contains stim_pack", doc.items.containsKey("stim_pack"))
        val stim = doc.items["stim_pack"]
        assertEquals(40, stim?.healHp)
        assertEquals(20, stim?.reduceToxicity)

        // Enemy check
        assertEquals(1, doc.enemies.size)
        val enemy = doc.enemies[0]
        assertEquals("cyber_stalker", enemy.id)
        assertEquals(60, enemy.hp)
        assertEquals('K', enemy.asciiGlyph)

        // Story nodes check
        assertEquals(2, doc.storyNodes.size)
        val startNode = doc.storyNodes["start"]
        assertNotNull(startNode)
        assertEquals("Decontamination Deck", startNode?.title)
        assertEquals("BIOS-AI", startNode?.speaker)
        assertEquals("WARNING", startNode?.mood)
        assertEquals("alarm_pulse", startNode?.soundEffectCue)
        assertTrue(startNode?.isCheckpoint == true)
        assertEquals(2, startNode?.choices?.size)

        // Conversion to Room entities
        val (scriptEntity, nodeEntities, choiceEntities) = doc.toRoomEntities()
        assertEquals("test_script.md", scriptEntity.scriptId)
        assertEquals(2, nodeEntities.size)
        assertEquals(3, choiceEntities.size) // 2 in start, 1 in node_shield
    }
}
