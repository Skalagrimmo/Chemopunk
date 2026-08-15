package com.example.data

import android.content.Context
import com.example.data.narrative.MarkdownNarrativeParser
import com.example.data.narrative.NarrativeScriptDocument
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
        val events: List<GameEvent> = emptyList(),
        val document: NarrativeScriptDocument? = null
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
            return parseString(markdownContent, assetFileName = fileName)
        }

        fun parseString(markdownText: String, assetFileName: String = "chemopank_world.md"): ParsedWorldData {
            val doc = MarkdownNarrativeParser.parseNarrativeDocument(markdownText, assetFileName = assetFileName)

            val eventsList = mutableListOf<GameEvent>()
            eventsList.add(GameEvent.ConfigLoaded(doc.config))
            doc.items.values.forEach { eventsList.add(GameEvent.ItemDefined(it)) }
            doc.enemies.forEach { eventsList.add(GameEvent.EnemyDefined(it)) }
            doc.storyNodes.values.forEach { eventsList.add(GameEvent.StoryNodeLoaded(it)) }

            // Ensure valid fallback map grid if empty
            val grid = if (doc.mapGrid.isNotEmpty()) {
                doc.mapGrid
            } else {
                val fallbackGrid = mutableListOf<MutableList<TileType>>()
                for (r in 0 until 10) {
                    val row = mutableListOf<TileType>()
                    for (c in 0 until 10) {
                        if (r == 0 || r == 9 || c == 0 || c == 9) row.add(TileType.WALL) else row.add(TileType.FLOOR)
                    }
                    fallbackGrid.add(row)
                }
                fallbackGrid
            }

            eventsList.add(GameEvent.MapGridParsed(grid))
            eventsList.add(GameEvent.ScriptLog("Markdown narrative script parsed: ${doc.storyNodes.size} story nodes, ${doc.items.size} items."))

            return ParsedWorldData(
                config = doc.config,
                items = doc.items,
                enemies = doc.enemies,
                mapGrid = grid,
                storyNodes = doc.storyNodes,
                rawMarkdownText = markdownText,
                events = eventsList,
                document = doc
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
- Damage: 22
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
