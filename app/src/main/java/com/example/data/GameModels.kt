package com.example.data

enum class TileType {
    WALL,
    FLOOR,
    TOXIC_POOL,
    DOOR,
    EXTRACTION_LIFT
}

enum class IsoDirection(val label: String, val dx: Int, val dy: Int, val arrow: String) {
    NORTH("NORTH", 0, -1, "▲"),
    SOUTH("SOUTH", 0, 1, "▼"),
    WEST("WEST", -1, 0, "◀"),
    EAST("EAST", 1, 0, "▶"),
    NORTH_WEST("NW", -1, -1, "◤"),
    NORTH_EAST("NE", 1, -1, "◥"),
    SOUTH_WEST("SW", -1, 1, "◣"),
    SOUTH_EAST("SE", 1, 1, "◢")
}

data class FloatingText(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val colorHex: Long,
    val spawnTime: Long = System.currentTimeMillis(),
    val durationMs: Long = 1800L
)

data class MapCell(
    val x: Int,
    val y: Int,
    val type: TileType,
    val hasLoot: Boolean = false,
    val lootItemId: String? = null
)

data class Player(
    var name: String = "Scythe-01",
    var hp: Int = 100,
    var maxHp: Int = 100,
    var toxicity: Int = 0, // 0 - 100%
    var maxToxicity: Int = 100,
    var attackPower: Int = 18,
    var defense: Int = 4,
    var credits: Int = 50,
    var exp: Int = 0,
    var level: Int = 1,
    var x: Float = 1.5f,
    var y: Float = 1.5f,
    var angleDegrees: Float = 0f,
    var equippedWeapon: Item? = null,
    var equippedArmor: Item? = null
)

enum class ItemType {
    CONSUMABLE,
    WEAPON,
    ARMOR,
    KEY_ITEM
}

data class Item(
    val id: String,
    val name: String,
    val type: ItemType,
    val value: Int = 0,
    val healHp: Int = 0,
    val reduceToxicity: Int = 0,
    val damage: Int = 0,
    val defense: Int = 0,
    val description: String = ""
)

enum class NpcState(val label: String, val badge: String) {
    PATROL("PATROL", "👁 PATROL"),
    AGGRESSIVE("AGGRESSIVE", "⚔ AGGRO"),
    FLEE("FLEEING", "💨 FLEE")
}

data class Enemy(
    val id: String,
    val name: String,
    var hp: Int,
    val maxHp: Int,
    val attack: Int,
    val armor: Int,
    val toxicityDamage: Int,
    val asciiGlyph: Char,
    val expReward: Int,
    val lootItemId: String?,
    var x: Float,
    var y: Float,
    var isAlive: Boolean = true,
    var state: NpcState = NpcState.PATROL,
    var patrolOriginX: Float = x,
    var patrolOriginY: Float = y,
    var patrolWaypoints: List<Pair<Int, Int>> = emptyList(),
    var currentWaypointIdx: Int = 0,
    var detectionRadius: Float = 4.0f,
    var fleeThreshold: Float = 0.30f,
    var turnsInCurrentState: Int = 0
)

data class Choice(
    val text: String,
    val targetNodeId: String
)

data class StoryNode(
    val id: String,
    val title: String,
    val content: String,
    val choices: List<Choice> = emptyList()
)

data class GameConfig(
    val initialFloor: Int = 1,
    val maxToxicity: Int = 100,
    val startingHp: Int = 100,
    val startingCredits: Int = 50,
    val startingWeapon: String = "plasma_scalpel",
    val fontStyle: String = "CRT_GREEN"
)

enum class ColorPalette {
    PHOSPHOR_GREEN,
    AMBER_TERMINAL,
    CYAN_CYBER,
    MONOCHROME_HIGH_CONTRAST
}

data class CombatLogEntry(
    val message: String,
    val isCritical: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
