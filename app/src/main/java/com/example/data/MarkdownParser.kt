package com.example.data

import android.content.Context
import java.io.InputStream

sealed class GameEvent {
    data class ConfigLoaded(val config: GameConfig) : GameEvent()
    data class ItemDefined(val item: Item) : GameEvent()
    data class EnemyDefined(val enemy: Enemy) : GameEvent()
    data class MapGridParsed(val mapGrid: List<List<TileType>>) : GameEvent()
    data class StoryNodeLoaded(val node: StoryNode) : GameEvent()
    data class ScriptLog(val message: String, val isCritical: Boolean = false) : GameEvent()
}

class MarkdownParser(private val context: Context? = null) {

    data class ParsedWorldData(
        val config: GameConfig,
        val items: Map<String, Item>,
        val enemies: List<Enemy>,
        val mapGrid: List<List<TileType>>,
        val storyNodes: Map<String, StoryNode>,
        val rawMarkdownText: String,
        val events: List<GameEvent> = emptyList()
    )

    fun parseScriptFromAssets(fileName: String = "chemopank_world.md"): List<GameEvent> {
        val ctx = context ?: throw IllegalStateException("Context is required to open assets.")
        val content = try {
            ctx.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            getFallbackMarkdown()
        }
        return parseScriptToEvents(content)
    }

    fun parseScriptToEvents(markdownText: String): List<GameEvent> {
        return parseString(markdownText).events
    }

    fun parseWorld(markdownText: String): ParsedWorldData {
        return parseString(markdownText)
    }

    companion object {

        fun parseFromAssets(context: Context, fileName: String = "chemopank_world.md"): ParsedWorldData {
            val markdownContent = try {
                val inputStream: InputStream = context.assets.open(fileName)
                inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                getFallbackMarkdown()
            }
            return parseString(markdownContent)
        }

        fun parseString(markdownText: String): ParsedWorldData {
            val eventsList = mutableListOf<GameEvent>()
            val lines = markdownText.lines()

            var config = GameConfig()
            val items = mutableMapOf<String, Item>()
            val enemies = mutableListOf<Enemy>()
            val mapLines = mutableListOf<String>()
            val storyNodes = mutableMapOf<String, StoryNode>()

            var currentSection = ""
            var currentEntityHeader = ""
            val currentEntityProps = mutableMapOf<String, String>()

            var currentStoryId = ""
            var currentStoryTitle = ""
            var currentStoryContent = StringBuilder()
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
                    eventsList.add(GameEvent.ItemDefined(item))
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
                    eventsList.add(GameEvent.EnemyDefined(enemy))
                }
                currentEntityHeader = ""
                currentEntityProps.clear()
            }

            fun finalizeStoryNode() {
                if (currentStoryId.isNotBlank()) {
                    val node = StoryNode(
                        id = currentStoryId,
                        title = currentStoryTitle,
                        content = currentStoryContent.toString().trim(),
                        choices = currentStoryChoices.toList()
                    )
                    storyNodes[currentStoryId] = node
                    eventsList.add(GameEvent.StoryNodeLoaded(node))
                }
                currentStoryId = ""
                currentStoryTitle = ""
                currentStoryContent = StringBuilder()
                currentStoryChoices.clear()
            }

            var inCodeBlock = false

            for (line in lines) {
                val trimmed = line.trim()

                if (trimmed.startsWith("## GAME_CONFIG")) {
                    currentSection = "CONFIG"
                    continue
                } else if (trimmed.startsWith("## ITEM_DATABASE")) {
                    currentSection = "ITEMS"
                    continue
                } else if (trimmed.startsWith("## ENEMY_DATABASE")) {
                    currentSection = "ENEMIES"
                    continue
                } else if (trimmed.startsWith("## MAP_LAYOUT")) {
                    currentSection = "MAP"
                    continue
                } else if (trimmed.startsWith("## STORY_NODES")) {
                    currentSection = "STORY"
                    continue
                }

                when (currentSection) {
                    "CONFIG" -> {
                        if (trimmed.startsWith("- ")) {
                            val parts = trimmed.substring(2).split(":", limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
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
                        if (trimmed.startsWith("### Node:")) {
                            finalizeStoryNode()
                            currentStoryId = trimmed.substring("### Node:".length).trim()
                        } else if (currentStoryId.isNotBlank()) {
                            if (trimmed.startsWith("- Title:")) {
                                currentStoryTitle = trimmed.substring("- Title:".length).trim()
                            } else if (trimmed.startsWith("- Choice:")) {
                                val choiceMatch = Regex("""\[(.*?)\]\(@(.*?)\)""").find(trimmed)
                                if (choiceMatch != null) {
                                    val text = choiceMatch.groupValues[1]
                                    val target = choiceMatch.groupValues[2]
                                    currentStoryChoices.add(Choice(text, target))
                                }
                            } else if (trimmed.startsWith("- Content:")) {
                                currentStoryContent.append(trimmed.substring("- Content:".length).trim()).append("\n")
                            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                                currentStoryContent.append(trimmed).append("\n")
                            }
                        }
                    }
                }
            }

            finalizeItem()
            finalizeEnemy()
            finalizeStoryNode()

            eventsList.add(GameEvent.ConfigLoaded(config))

            // Build Map Grid from mapLines
            val mapGrid = mutableListOf<MutableList<TileType>>()
            if (mapLines.isEmpty()) {
                for (r in 0 until 10) {
                    val row = mutableListOf<TileType>()
                    for (c in 0 until 10) {
                        if (r == 0 || r == 9 || c == 0 || c == 9) row.add(TileType.WALL) else row.add(TileType.FLOOR)
                    }
                    mapGrid.add(row)
                }
            } else {
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

            eventsList.add(GameEvent.MapGridParsed(mapGrid))
            eventsList.add(GameEvent.ScriptLog("Script parsed successfully: ${eventsList.size} game events generated."))

            return ParsedWorldData(
                config = config,
                items = items,
                enemies = enemies,
                mapGrid = mapGrid,
                storyNodes = storyNodes,
                rawMarkdownText = markdownText,
                events = eventsList
            )
        }

        private fun getFallbackMarkdown(): String {
            return """
# CHEMOPUNK RPG
## GAME_CONFIG
- initial_floor: 1
- max_toxicity: 100
- starting_hp: 100
- starting_credits: 50
- starting_weapon: plasma_scalpel
- font_style: CRT_GREEN

## ITEM_DATABASE
### Item: anti_toxin
- Name: Anti-Toxin Serum
- Type: CONSUMABLE
- Value: 25
- Effect: Reduce Toxicity by 40, Heal 15 HP
- Description: Neutralizes chemical mutagens.

### Item: plasma_scalpel
- Name: Plasma Scalpel
- Type: WEAPON
- Damage: 18
- Description: High-energy blade for cutting armor.

## ENEMY_DATABASE
### Enemy: acid_slug
- Name: Mutated Acid Slug
- HP: 35
- Attack: 8
- Armor: 2
- ToxicityDamage: 5
- ASCII_Glyph: S
- ExpReward: 15
- Loot: anti_toxin

## MAP_LAYOUT: Floor 1
```
##########
#P..#...S#
#...#....#
###.####.#
#...#...E#
##########
```

## STORY_NODES
### Node: start
- Title: Sector 7 Lab
- Content: You wake up amidst flickering terminal screens and green chemical pools.
- Choice: [Proceed into dungeon](@action_gameview)
""".trimIndent()
        }
    }
}
