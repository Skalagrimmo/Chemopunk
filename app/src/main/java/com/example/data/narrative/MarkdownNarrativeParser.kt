package com.example.data.narrative

import android.content.Context
import com.example.data.Choice
import com.example.data.Enemy
import com.example.data.GameConfig
import com.example.data.Item
import com.example.data.ItemType
import com.example.data.StoryNode
import com.example.data.TileType
import java.io.InputStream

/**
 * Inline elements parsed from raw Markdown text.
 */
sealed class InlineElement {
    data class Text(val content: String) : InlineElement()
    data class Bold(val content: String) : InlineElement()
    data class Italic(val content: String) : InlineElement()
    data class BoldItalic(val content: String) : InlineElement()
    data class InlineCode(val code: String) : InlineElement()
    data class Strikethrough(val content: String) : InlineElement()
    data class Highlight(val content: String) : InlineElement()
    data class Link(val label: String, val target: String, val isStoryLink: Boolean = false) : InlineElement()
}

/**
 * Block-level Markdown nodes for AST representation.
 */
sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String, val inlines: List<InlineElement>) : MarkdownBlock()
    data class Paragraph(val inlines: List<InlineElement>) : MarkdownBlock()
    data class Blockquote(val speaker: String?, val inlines: List<InlineElement>, val isHazard: Boolean = false) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletList(val items: List<List<InlineElement>>) : MarkdownBlock()
    data class NumberedList(val items: List<Pair<Int, List<InlineElement>>>) : MarkdownBlock()
    data class MarkdownTable(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class ChoiceAction(val choice: Choice) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

/**
 * Story Asset metadata descriptor.
 */
data class StoryAssetDescriptor(
    val fileName: String,
    val title: String,
    val category: String,
    val description: String,
    val iconName: String = "ic_story"
)

/**
 * Comprehensive parsed narrative script document.
 */
data class NarrativeScriptDocument(
    val title: String,
    val assetFileName: String,
    val config: GameConfig = GameConfig(),
    val items: Map<String, Item> = emptyMap(),
    val enemies: List<Enemy> = emptyList(),
    val mapGrid: List<List<TileType>> = emptyList(),
    val storyNodes: Map<String, StoryNode> = emptyMap(),
    val standaloneBlocks: List<MarkdownBlock> = emptyList(),
    val rawText: String = ""
)

/**
 * High-performance, AST-based Markdown Parser for Chemopunk / Retro RPG Narrative Scripts.
 */
object MarkdownNarrativeParser {

    /**
     * Pre-registered narrative assets in the application assets bundle.
     */
    val AVAILABLE_STORY_ASSETS = listOf(
        StoryAssetDescriptor(
            fileName = "chemopank_world.md",
            title = "Sector 7: Chemical Wasteland",
            category = "CAMPAIGN",
            description = "Main campaign narrative, map definitions, enemy database, and interactive story nodes."
        ),
        StoryAssetDescriptor(
            fileName = "audio_logs.md",
            title = "Survivor Audio Transmissions",
            category = "AUDIO_LOGS",
            description = "Recovered voice logs from Dr. Vance, Engineer Miller, and Vault 13 security teams."
        ),
        StoryAssetDescriptor(
            fileName = "terminal_archives.md",
            title = "Vault Mainframe Archives",
            category = "ARCHIVES",
            description = "Classified research reports, facility incident records, and auto-defense logs."
        ),
        StoryAssetDescriptor(
            fileName = "survival_manual.md",
            title = "Wasteland Field Guide",
            category = "MANUAL",
            description = "Tactical survival protocols, toxicity management, and plasma weaponry specifications."
        )
    )

    /**
     * Parses inline Markdown formatting (Bold, Italic, Code, Strikethrough, Links, Highlight).
     */
    fun parseInline(text: String, variableResolver: (String) -> String = { it }): List<InlineElement> {
        val resolved = interpolateVariables(text, variableResolver)
        val elements = mutableListOf<InlineElement>()

        // Tokenizer regex matching links, bold-italic, bold, italic, code, strikethrough, highlight
        val tokenRegex = Regex(
            """(\[.*?\]\(.*?\))|(\*\*\*.*?\*\*\*)|(\*\*.*?\*\*)|(__.*?__)|(\*.*?\*)|(_.*?_)|(`.*?`)|(~~.*?~~)|(==.*?==)"""
        )

        var lastIndex = 0
        val matches = tokenRegex.findAll(resolved)

        for (match in matches) {
            val range = match.range
            if (range.first > lastIndex) {
                elements.add(InlineElement.Text(resolved.substring(lastIndex, range.first)))
            }

            val token = match.value
            when {
                // Link: [Label](Target)
                token.startsWith("[") && token.endsWith(")") -> {
                    val linkMatch = Regex("""\[(.*?)\]\((.*?)\)""").find(token)
                    if (linkMatch != null) {
                        val label = linkMatch.groupValues[1]
                        val target = linkMatch.groupValues[2]
                        val isStory = target.startsWith("@") || target.startsWith("node_")
                        elements.add(InlineElement.Link(label, target, isStoryLink = isStory))
                    } else {
                        elements.add(InlineElement.Text(token))
                    }
                }
                // Bold-Italic: ***text***
                token.startsWith("***") && token.endsWith("***") && token.length >= 6 -> {
                    elements.add(InlineElement.BoldItalic(token.substring(3, token.length - 3)))
                }
                // Bold: **text** or __text__
                (token.startsWith("**") && token.endsWith("**") && token.length >= 4) ||
                (token.startsWith("__") && token.endsWith("__") && token.length >= 4) -> {
                    elements.add(InlineElement.Bold(token.substring(2, token.length - 2)))
                }
                // Italic: *text* or _text_
                (token.startsWith("*") && token.endsWith("*") && token.length >= 2) ||
                (token.startsWith("_") && token.endsWith("_") && token.length >= 2) -> {
                    elements.add(InlineElement.Italic(token.substring(1, token.length - 1)))
                }
                // Inline Code: `code`
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    elements.add(InlineElement.InlineCode(token.substring(1, token.length - 1)))
                }
                // Strikethrough: ~~text~~
                token.startsWith("~~") && token.endsWith("~~") && token.length >= 4 -> {
                    elements.add(InlineElement.Strikethrough(token.substring(2, token.length - 2)))
                }
                // Highlight: ==text==
                token.startsWith("==") && token.endsWith("==") && token.length >= 4 -> {
                    elements.add(InlineElement.Highlight(token.substring(2, token.length - 2)))
                }
                else -> {
                    elements.add(InlineElement.Text(token))
                }
            }
            lastIndex = range.last + 1
        }

        if (lastIndex < resolved.length) {
            elements.add(InlineElement.Text(resolved.substring(lastIndex)))
        }

        return if (elements.isEmpty()) listOf(InlineElement.Text(resolved)) else elements
    }

    /**
     * Interpolates game state variables in text e.g. {PLAYER_NAME}, {HP}, {TOXICITY}.
     */
    private fun interpolateVariables(text: String, variableResolver: (String) -> String): String {
        val varRegex = Regex("""\{([A-Za-z0-9_]+)\}""")
        return varRegex.replace(text) { match ->
            val varName = match.groupValues[1]
            variableResolver(varName)
        }
    }

    /**
     * Parses raw Markdown into a sequence of structured block elements.
     */
    fun parseBlocks(
        markdown: String,
        variableResolver: (String) -> String = { it }
    ): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 1. Horizontal Rules (---, ***, ===)
            if (trimmed == "---" || trimmed == "***" || trimmed == "===" || trimmed == "- - -") {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // 2. Code Block (```lang ... ```)
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.append(lines[i]).append("\n")
                    i++
                }
                if (i < lines.size) i++ // skip closing ```
                blocks.add(MarkdownBlock.CodeBlock(lang, codeBuilder.toString().trimEnd()))
                continue
            }

            // 3. Headers (# .. #####)
            if (trimmed.startsWith("#")) {
                val headerMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
                if (headerMatch != null) {
                    val level = headerMatch.groupValues[1].length
                    val headerText = headerMatch.groupValues[2]
                    blocks.add(
                        MarkdownBlock.Header(
                            level = level,
                            text = headerText,
                            inlines = parseInline(headerText, variableResolver)
                        )
                    )
                    i++
                    continue
                }
            }

            // 4. Blockquotes (> ...) with optional speaker tag (> [DR. VANCE]: ...)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                var isHazard = false
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    val qLine = lines[i].trim().removePrefix(">").trim()
                    if (qLine.contains("WARNING", ignoreCase = true) || qLine.contains("DANGER", ignoreCase = true) || qLine.contains("HAZARD", ignoreCase = true)) {
                        isHazard = true
                    }
                    quoteLines.add(qLine)
                    i++
                }
                val fullQuote = quoteLines.joinToString(" ")
                val speakerMatch = Regex("""^\[(.*?)\]:\s*(.*)$""").find(fullQuote)
                val speaker = speakerMatch?.groupValues?.get(1)
                val quoteContent = speakerMatch?.groupValues?.get(2) ?: fullQuote

                blocks.add(
                    MarkdownBlock.Blockquote(
                        speaker = speaker,
                        inlines = parseInline(quoteContent, variableResolver),
                        isHazard = isHazard
                    )
                )
                continue
            }

            // 5. Choice Action Button (- Choice: [Label](@target))
            if (trimmed.startsWith("- Choice:") || trimmed.startsWith("* Choice:")) {
                val choiceStr = trimmed.substringAfter("Choice:").trim()
                val choice = parseChoiceString(choiceStr)
                if (choice != null) {
                    blocks.add(MarkdownBlock.ChoiceAction(choice))
                }
                i++
                continue
            }

            // 6. Markdown Tables (| Header 1 | Header 2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    val headers = tableLines[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    val rows = mutableListOf<List<String>>()
                    for (rowIdx in 1 until tableLines.size) {
                        val rowLine = tableLines[rowIdx]
                        if (rowLine.contains("---")) continue // separator row
                        val cells = rowLine.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        if (cells.isNotEmpty()) rows.add(cells)
                    }
                    blocks.add(MarkdownBlock.MarkdownTable(headers, rows))
                    continue
                }
            }

            // 7. Bullet Lists (- item or * item)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                val listItems = mutableListOf<List<InlineElement>>()
                while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ") || lines[i].trim().startsWith("+ "))) {
                    val itemText = lines[i].trim().substring(2).trim()
                    if (!itemText.startsWith("Choice:")) {
                        listItems.add(parseInline(itemText, variableResolver))
                    }
                    i++
                }
                if (listItems.isNotEmpty()) {
                    blocks.add(MarkdownBlock.BulletList(listItems))
                }
                continue
            }

            // 8. Numbered Lists (1. item)
            val numMatch = Regex("""^(\d+)\.\s+(.*)$""").find(trimmed)
            if (numMatch != null) {
                val listItems = mutableListOf<Pair<Int, List<InlineElement>>>()
                while (i < lines.size) {
                    val curMatch = Regex("""^(\d+)\.\s+(.*)$""").find(lines[i].trim())
                    if (curMatch != null) {
                        val num = curMatch.groupValues[1].toIntOrNull() ?: 1
                        val text = curMatch.groupValues[2]
                        listItems.add(Pair(num, parseInline(text, variableResolver)))
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.NumberedList(listItems))
                continue
            }

            // 9. Standard Paragraph (gather consecutive text lines)
            val paragraphBuilder = StringBuilder()
            while (i < lines.size && lines[i].trim().isNotEmpty() &&
                !lines[i].trim().startsWith("#") &&
                !lines[i].trim().startsWith(">") &&
                !lines[i].trim().startsWith("```") &&
                !lines[i].trim().startsWith("---") &&
                !lines[i].trim().startsWith("- ") &&
                !lines[i].trim().startsWith("* ") &&
                !Regex("""^(\d+)\.\s+""").containsMatchIn(lines[i].trim())
            ) {
                paragraphBuilder.append(lines[i].trim()).append(" ")
                i++
            }

            val pText = paragraphBuilder.toString().trim()
            if (pText.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(parseInline(pText, variableResolver)))
            }
        }

        return blocks
    }

    /**
     * Parses enriched choice strings such as:
     * `[Purge nearest chemical valve (-10 Toxicity)](@node_purge)`
     * or `[Unlock Security Door | Req: keycard_alpha](@node_security)`
     */
    fun parseChoiceString(choiceStr: String): Choice? {
        val match = Regex("""\[(.*?)\]\(@?(.*?)\)""").find(choiceStr) ?: return null
        val fullLabel = match.groupValues[1]
        val target = match.groupValues[2].removePrefix("@")

        var text = fullLabel
        var reqItem: String? = null
        var toxCost = 0
        var hpReward = 0
        var crReward = 0
        var action: String? = null

        // Parse pipe metadata if present: [Text | Req: item_id | Tox: -10 | CR: +25]
        if (fullLabel.contains("|")) {
            val parts = fullLabel.split("|").map { it.trim() }
            text = parts[0]
            for (p in parts.drop(1)) {
                val sub = p.lowercase()
                when {
                    sub.startsWith("req:") -> reqItem = p.substringAfter(":").trim()
                    sub.startsWith("tox:") -> {
                        toxCost = p.substringAfter(":").trim().replace("+", "").toIntOrNull() ?: 0
                    }
                    sub.startsWith("hp:") -> {
                        hpReward = p.substringAfter(":").trim().replace("+", "").toIntOrNull() ?: 0
                    }
                    sub.startsWith("cr:") -> {
                        crReward = p.substringAfter(":").trim().replace("+", "").toIntOrNull() ?: 0
                    }
                    sub.startsWith("action:") -> {
                        action = p.substringAfter(":").trim()
                    }
                }
            }
        } else {
            // Auto-detect toxicity or HP modifiers in parentheses e.g. (-10 Toxicity)
            val toxMatch = Regex("""\(([+-]?\d+)\s*(?:Toxicity|Tox|RADS)\)""", RegexOption.IGNORE_CASE).find(fullLabel)
            if (toxMatch != null) {
                toxCost = toxMatch.groupValues[1].toIntOrNull() ?: 0
            }
            val hpMatch = Regex("""\(([+-]?\d+)\s*HP\)""", RegexOption.IGNORE_CASE).find(fullLabel)
            if (hpMatch != null) {
                hpReward = hpMatch.groupValues[1].toIntOrNull() ?: 0
            }
        }

        return Choice(
            text = text,
            targetNodeId = target,
            requiredItemId = reqItem,
            toxicityCost = toxCost,
            hpReward = hpReward,
            creditsReward = crReward,
            actionTrigger = action
        )
    }

    /**
     * Parses an entire narrative document containing metadata, story nodes, entities, and map.
     */
    fun parseNarrativeDocument(
        markdownText: String,
        assetFileName: String = "script.md",
        variableResolver: (String) -> String = { it }
    ): NarrativeScriptDocument {
        val lines = markdownText.lines()
        var title = "Chemopunk Narrative Script"

        var config = GameConfig()
        val items = mutableMapOf<String, Item>()
        val enemies = mutableListOf<Enemy>()
        val mapLines = mutableListOf<String>()
        val storyNodes = mutableMapOf<String, StoryNode>()
        val standaloneBlocks = mutableListOf<MarkdownBlock>()

        var currentSection = "ROOT"
        var currentEntityHeader = ""
        val currentEntityProps = mutableMapOf<String, String>()

        var currentStoryId = ""
        var currentStoryTitle = ""
        var currentStorySpeaker: String? = null
        var currentStoryCategory = "STORY"
        var currentStoryMood = "NORMAL"
        val currentStoryContentLines = mutableListOf<String>()
        val currentStoryChoices = mutableListOf<Choice>()

        fun finalizeItem() {
            if (currentEntityHeader.isNotBlank()) {
                val id = currentEntityHeader.lowercase().replace(" ", "_")
                val name = currentEntityProps["name"] ?: currentEntityHeader
                val typeStr = currentEntityProps["type"] ?: "CONSUMABLE"
                val type = try { ItemType.valueOf(typeStr.uppercase()) } catch (_: Exception) { ItemType.CONSUMABLE }
                val valInt = currentEntityProps["value"]?.toIntOrNull() ?: 0
                val desc = currentEntityProps["description"] ?: ""

                val item = when (type) {
                    ItemType.CONSUMABLE -> {
                        val effect = currentEntityProps["effect"] ?: ""
                        var heal = 0
                        var reduceTox = 0
                        if (effect.contains("Heal", ignoreCase = true)) {
                            val match = Regex("""Heal\s+(\d+)""", RegexOption.IGNORE_CASE).find(effect)
                            heal = match?.groupValues?.get(1)?.toIntOrNull() ?: 20
                        }
                        if (effect.contains("Toxicity", ignoreCase = true)) {
                            val match = Regex("""Reduce Toxicity by\s+(\d+)""", RegexOption.IGNORE_CASE).find(effect)
                            reduceTox = match?.groupValues?.get(1)?.toIntOrNull() ?: 30
                        }
                        Item(id, name, type, valInt, healHp = heal, reduceToxicity = reduceTox, description = desc)
                    }
                    ItemType.WEAPON -> {
                        val dmg = currentEntityProps["damage"]?.toIntOrNull() ?: 15
                        Item(id, name, type, valInt, damage = dmg, description = desc)
                    }
                    ItemType.ARMOR -> {
                        val def = currentEntityProps["defense"]?.toIntOrNull() ?: 5
                        Item(id, name, type, valInt, defense = def, description = desc)
                    }
                    else -> Item(id, name, type, valInt, description = desc)
                }
                items[id] = item
            }
            currentEntityHeader = ""
            currentEntityProps.clear()
        }

        fun finalizeEnemy() {
            if (currentEntityHeader.isNotBlank()) {
                val id = currentEntityHeader.lowercase().replace(" ", "_")
                val name = currentEntityProps["name"] ?: currentEntityHeader
                val hp = currentEntityProps["hp"]?.toIntOrNull() ?: 40
                val atk = currentEntityProps["attack"]?.toIntOrNull() ?: 10
                val arm = currentEntityProps["armor"]?.toIntOrNull() ?: 2
                val toxDmg = currentEntityProps["toxicitydamage"]?.toIntOrNull() ?: 5
                val glyph = currentEntityProps["ascii_glyph"]?.getOrNull(0) ?: 'E'
                val exp = currentEntityProps["expreward"]?.toIntOrNull() ?: 20
                val loot = currentEntityProps["loot"]

                val enemy = Enemy(
                    id = id,
                    name = name,
                    hp = hp,
                    maxHp = hp,
                    attack = atk,
                    armor = arm,
                    toxicityDamage = toxDmg,
                    asciiGlyph = glyph,
                    expReward = exp,
                    lootItemId = loot,
                    x = 3.5f,
                    y = 3.5f
                )
                enemies.add(enemy)
            }
            currentEntityHeader = ""
            currentEntityProps.clear()
        }

        fun finalizeStoryNode() {
            if (currentStoryId.isNotBlank()) {
                val rawContent = currentStoryContentLines.joinToString("\n").trim()
                val node = StoryNode(
                    id = currentStoryId,
                    title = currentStoryTitle.ifEmpty { "Log #${currentStoryId}" },
                    content = rawContent,
                    speaker = currentStorySpeaker,
                    category = currentStoryCategory,
                    mood = currentStoryMood,
                    choices = currentStoryChoices.toList(),
                    rawMarkdown = rawContent
                )
                storyNodes[currentStoryId] = node
            }
            currentStoryId = ""
            currentStoryTitle = ""
            currentStorySpeaker = null
            currentStoryCategory = "STORY"
            currentStoryMood = "NORMAL"
            currentStoryContentLines.clear()
            currentStoryChoices.clear()
        }

        var inCodeBlock = false

        for (line in lines) {
            val trimmed = line.trim()

            // Root Document Title (# TITLE)
            if (trimmed.startsWith("# ") && currentSection == "ROOT") {
                title = trimmed.removePrefix("# ").trim()
                continue
            }

            if (trimmed.startsWith("## GAME_CONFIG")) {
                finalizeItem()
                finalizeEnemy()
                finalizeStoryNode()
                currentSection = "CONFIG"
                continue
            } else if (trimmed.startsWith("## ITEM_DATABASE")) {
                finalizeItem()
                finalizeEnemy()
                finalizeStoryNode()
                currentSection = "ITEMS"
                continue
            } else if (trimmed.startsWith("## ENEMY_DATABASE")) {
                finalizeItem()
                finalizeEnemy()
                finalizeStoryNode()
                currentSection = "ENEMIES"
                continue
            } else if (trimmed.startsWith("## MAP_LAYOUT")) {
                finalizeItem()
                finalizeEnemy()
                finalizeStoryNode()
                currentSection = "MAP"
                continue
            } else if (trimmed.startsWith("## STORY_NODES") || trimmed.startsWith("## AUDIO_LOGS") || trimmed.startsWith("## ARCHIVE_ENTRIES") || trimmed.startsWith("## CHAPTERS")) {
                finalizeItem()
                finalizeEnemy()
                finalizeStoryNode()
                currentSection = "STORY"
                continue
            }

            when (currentSection) {
                "CONFIG" -> {
                    if (trimmed.startsWith("- ")) {
                        val parts = trimmed.substring(2).split(":", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim().lowercase()
                            val value = parts[1].trim()
                            when (key) {
                                "initial_floor" -> config = config.copy(initialFloor = value.toIntOrNull() ?: 1)
                                "max_toxicity" -> config = config.copy(maxToxicity = value.toIntOrNull() ?: 100)
                                "starting_hp" -> config = config.copy(startingHp = value.toIntOrNull() ?: 100)
                                "starting_credits" -> config = config.copy(startingCredits = value.toIntOrNull() ?: 50)
                                "starting_weapon" -> config = config.copy(startingWeapon = value)
                                "font_style" -> config = config.copy(fontStyle = value)
                            }
                        }
                    }
                }

                "ITEMS" -> {
                    if (trimmed.startsWith("### Item:")) {
                        finalizeItem()
                        currentEntityHeader = trimmed.substring("### Item:".length).trim()
                    } else if (trimmed.startsWith("- ") && currentEntityHeader.isNotBlank()) {
                        val parts = trimmed.substring(2).split(":", limit = 2)
                        if (parts.size == 2) {
                            currentEntityProps[parts[0].trim().lowercase()] = parts[1].trim()
                        }
                    }
                }

                "ENEMIES" -> {
                    if (trimmed.startsWith("### Enemy:")) {
                        finalizeEnemy()
                        currentEntityHeader = trimmed.substring("### Enemy:".length).trim()
                    } else if (trimmed.startsWith("- ") && currentEntityHeader.isNotBlank()) {
                        val parts = trimmed.substring(2).split(":", limit = 2)
                        if (parts.size == 2) {
                            currentEntityProps[parts[0].trim().lowercase()] = parts[1].trim()
                        }
                    }
                }

                "MAP" -> {
                    if (trimmed.startsWith("```")) {
                        inCodeBlock = !inCodeBlock
                        continue
                    }
                    if (inCodeBlock && trimmed.isNotEmpty()) {
                        mapLines.add(trimmed)
                    }
                }

                "STORY" -> {
                    if (trimmed.startsWith("### Node:") || trimmed.startsWith("### Log:") || trimmed.startsWith("### Entry:")) {
                        finalizeStoryNode()
                        val prefix = if (trimmed.startsWith("### Node:")) "### Node:" else if (trimmed.startsWith("### Log:")) "### Log:" else "### Entry:"
                        currentStoryId = trimmed.substring(prefix.length).trim()
                        currentStoryCategory = if (prefix.contains("Log")) "AUDIO_LOG" else if (prefix.contains("Entry")) "ARCHIVE" else "STORY"
                    } else if (currentStoryId.isNotBlank()) {
                        when {
                            trimmed.startsWith("- Title:") -> currentStoryTitle = trimmed.substring("- Title:".length).trim()
                            trimmed.startsWith("- Speaker:") -> currentStorySpeaker = trimmed.substring("- Speaker:".length).trim()
                            trimmed.startsWith("- Mood:") -> currentStoryMood = trimmed.substring("- Mood:".length).trim().uppercase()
                            trimmed.startsWith("- Category:") -> currentStoryCategory = trimmed.substring("- Category:".length).trim().uppercase()
                            trimmed.startsWith("- Choice:") -> {
                                val choice = parseChoiceString(trimmed.substring("- Choice:".length).trim())
                                if (choice != null) currentStoryChoices.add(choice)
                            }
                            trimmed.startsWith("- Content:") -> {
                                currentStoryContentLines.add(trimmed.substring("- Content:".length).trim())
                            }
                            trimmed.isNotEmpty() && !trimmed.startsWith("---") -> {
                                currentStoryContentLines.add(trimmed)
                            }
                        }
                    }
                }

                else -> {
                    // Document-level standalone blocks
                    if (trimmed.isNotEmpty()) {
                        // Will parse with parseBlocks below
                    }
                }
            }
        }

        finalizeItem()
        finalizeEnemy()
        finalizeStoryNode()

        // Map layout parsing
        val mapGrid = mutableListOf<MutableList<TileType>>()
        if (mapLines.isNotEmpty()) {
            var enemyIndex = 0
            mapLines.forEachIndexed { r, line ->
                val row = mutableListOf<TileType>()
                line.forEachIndexed { c, char ->
                    when (char) {
                        '#' -> row.add(TileType.WALL)
                        'S', 'D', 'M' -> {
                            row.add(TileType.FLOOR)
                            if (enemyIndex < enemies.size) {
                                enemies[enemyIndex].x = c + 0.5f
                                enemies[enemyIndex].y = r + 0.5f
                                enemyIndex++
                            }
                        }
                        '~' -> row.add(TileType.TOXIC_POOL)
                        'E' -> row.add(TileType.EXTRACTION_LIFT)
                        else -> row.add(TileType.FLOOR)
                    }
                }
                mapGrid.add(row)
            }
        }

        val allBlocks = parseBlocks(markdownText, variableResolver)

        return NarrativeScriptDocument(
            title = title,
            assetFileName = assetFileName,
            config = config,
            items = items,
            enemies = enemies,
            mapGrid = mapGrid,
            storyNodes = storyNodes,
            standaloneBlocks = allBlocks,
            rawText = markdownText
        )
    }

    /**
     * Loads and parses a narrative script directly from the app's raw assets.
     */
    fun loadFromAssets(
        context: Context,
        fileName: String,
        variableResolver: (String) -> String = { it }
    ): NarrativeScriptDocument {
        val content = try {
            val stream: InputStream = context.assets.open(fileName)
            stream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            getFallbackAssetContent(fileName)
        }
        return parseNarrativeDocument(content, assetFileName = fileName, variableResolver = variableResolver)
    }

    private fun getFallbackAssetContent(fileName: String): String {
        return """
# CHEMOPUNK RPG // SECTOR 7
## GAME_CONFIG
- initial_floor: 1
- max_toxicity: 100
- starting_hp: 100
- starting_credits: 50
- starting_weapon: plasma_scalpel

## STORY_NODES
### Node: start
- Title: Waking in Sector 7
- Speaker: BIO-MONITOR v2.4
- Mood: WARNING
- Content: You awaken on cold steel gratings. Green toxic vapor pools along the lower decks.
- Choice: [Inspect nearby terminal](@node_terminal)
- Choice: [Advance into dungeon](@action_gameview)
        """.trimIndent()
    }
}
