package com.example.data

enum class TileType {
    WALL,
    FLOOR,
    TOXIC_POOL,
    DOOR,
    EXTRACTION_LIFT,
    INTERACTIVE
}

/**
 * Static world props the player can interact with by tapping an adjacent (or occupied) tile.
 * Rendered as the INTERACTIVE tile type and trigger events in the ViewModel.
 */
enum class InteractiveObjectType(val glyph: Char, val label: String) {
    TERMINAL('⌨', "Data Terminal"),
    LOCKER('▣', "Supply Locker"),
    SWITCH('⊞', "Power Switch"),
    BEACON('☉', "Rescue Beacon"),
    MERCHANT('₿', "Black Market Vendor"),
    ZONE_EXIT('⏏', "Zone Transit Gate")
}

/**
 * World factions tracked by the reputation system. Standing ranges roughly -100 (hostile)
 * to +100 (allied) and influences vendor pricing.
 */
enum class Faction(val id: String, val label: String) {
    RAIDERS("raiders", "Raiders"),
    SCIENTISTS("scientists", "Scientists"),
    MUTANTS("mutants", "Mutants");

    companion object {
        fun fromId(id: String): Faction? = values().firstOrNull { it.id == id }
    }
}

/**
 * A distinct explorable region. Each zone can load its own markdown map asset,
 * apply an encounter difficulty multiplier, and tint the dynamic lighting.
 */
data class Zone(
    val id: String,
    val name: String,
    val assetFileName: String,
    val encounterMultiplier: Float = 1.0f,
    val lightTint: Long = 0xFFFFFFFF
)

data class InteractiveObject(
    val id: String,
    val type: InteractiveObjectType,
    val x: Int,
    val y: Int,
    var isUsed: Boolean = false,
    val description: String = "",
    // For SWITCH: the light source id it toggles (or null to simply brighten the area)
    val linkedLightId: String? = null
)

/** Side-effect triggered when a dialogue option is selected. */
enum class DialogueAction { NONE, OPEN_TRADE, CLOSE }

data class DialogueOption(
    val label: String,
    val nextNodeId: String? = null,
    val action: DialogueAction = DialogueAction.NONE
)

data class DialogueNode(
    val id: String,
    val speaker: String,
    val text: String,
    val options: List<DialogueOption> = emptyList()
)

data class DialogueTree(
    val npcId: String,
    val startNodeId: String,
    val nodes: Map<String, DialogueNode>
) {
    fun node(id: String): DialogueNode? = nodes[id]
}

/**
 * A recruitable ally. Companions grant a passive combat bonus (attack) and are tracked
 * in GameUiState. Full follower AI/inventory is a future expansion.
 */
data class Companion(
    val id: String,
    val name: String,
    val hp: Int = 60,
    val maxHp: Int = 60,
    val attack: Int = 8,
    val quip: String = "On your six."
)

/**
 * Quest journal entries derived from narrative progress & world milestones.
 */
enum class QuestStatus { ACTIVE, COMPLETED, FAILED }

data class QuestObjective(
    val id: String,
    val description: String,
    val isCompleted: Boolean = false
)

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val objectives: List<QuestObjective> = emptyList()
) {
    val progressText: String
        get() {
            if (objectives.isEmpty()) return if (status == QuestStatus.COMPLETED) "DONE" else "ACTIVE"
            val done = objectives.count { it.isCompleted }
            return "$done/${objectives.size}"
        }
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
    var equippedArmor: Item? = null,
    var statusEffects: List<StatusEffect> = emptyList()
)

enum class StatusEffectType(
    val defaultName: String,
    val glyph: String,
    val defaultMagnitude: Int,
    val colorHex: Long
) {
    POISON("Poison Sludge", "☣", 6, 0xFF10B981),
    RADIATION("Radiation Sickness", "☢", 4, 0xFFFFB020),
    STUN("Electromagnetic Stun", "⚡", 1, 0xFFF59E0B),
    CORROSION("Acid Corrosion", "🧪", 5, 0xFFF97316),
    ADRENALINE("Adrenaline Rush", "💉", 8, 0xFF38BDF8),
    REGENERATION("Nano-Regen", "💖", 7, 0xFF4ADE80)
}

data class StatusEffect(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: StatusEffectType,
    val name: String = type.defaultName,
    val durationTurns: Int = 3,
    val magnitude: Int = type.defaultMagnitude,
    val description: String = "",
    val iconGlyph: String = type.glyph
) {
    val isDebuff: Boolean
        get() = type in listOf(StatusEffectType.POISON, StatusEffectType.RADIATION, StatusEffectType.STUN, StatusEffectType.CORROSION)

    val isBuff: Boolean
        get() = !isDebuff
}

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
    var turnsInCurrentState: Int = 0,
    var statusEffects: List<StatusEffect> = emptyList()
)

data class Choice(
    val text: String,
    val targetNodeId: String,
    val requiredItemId: String? = null,
    val toxicityCost: Int = 0,
    val hpReward: Int = 0,
    val creditsReward: Int = 0,
    val actionTrigger: String? = null,
    val requiredMinLevel: Int = 0,
    val requiredStoryFlag: String? = null,
    val setStoryFlag: String? = null,
    val rewardItemId: String? = null,
    val failureTargetNodeId: String? = null
)

data class StoryNode(
    val id: String,
    val title: String,
    val content: String,
    val speaker: String? = null,
    val category: String = "STORY",
    val mood: String = "NORMAL",
    val choices: List<Choice> = emptyList(),
    val rawMarkdown: String = "",
    val bgAtmosphere: String = "SECTOR_7_LAB",
    val soundEffectCue: String? = null,
    val requiredStoryFlag: String? = null,
    val isCheckpoint: Boolean = false
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

enum class LogCategory(val label: String, val badge: String) {
    COMBAT("COMBAT", "⚔"),
    HAZARD("HAZARD", "☣"),
    NPC_AI("AI/NPC", "🎯"),
    LOOT("INVENTORY", "📦"),
    NARRATIVE("STORY", "📜"),
    SYSTEM("SYSTEM", "⚡")
}

data class CombatLogEntry(
    val message: String,
    val isCritical: Boolean = false,
    val isMiss: Boolean = false,
    val isHeal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory = categorizeMessage(message, isCritical, isMiss, isHeal),
    val impactValue: String? = extractImpactValue(message),
    val isInteractive: Boolean = true
) {
    companion object {
        fun categorizeMessage(msg: String, critical: Boolean, miss: Boolean = false, heal: Boolean = false): LogCategory {
            val lower = msg.lowercase()
            return when {
                heal -> LogCategory.LOOT
                miss -> LogCategory.SYSTEM
                lower.contains("strike") || lower.contains("hit") || lower.contains("crit") ||
                lower.contains("damage") || lower.contains("attack") || lower.contains("defeated") ||
                lower.contains("engaged") || lower.contains("counter-attack") || lower.contains("blocked") -> LogCategory.COMBAT

                lower.contains("toxic") || lower.contains("sludge") || lower.contains("mutagen") ||
                lower.contains("purge") || lower.contains("radiation") || lower.contains("hazard") ||
                lower.contains("fatality") || lower.contains("bio-suit") -> LogCategory.HAZARD

                lower.contains("patrol") || lower.contains("aggro") || lower.contains("flee") ||
                lower.contains("spotted") || lower.contains("alert") || lower.contains("v.a.t.s.") ||
                lower.contains("sensors") || lower.contains("target") || lower.contains("hostile") -> LogCategory.NPC_AI

                lower.contains("item") || lower.contains("equip") || lower.contains("scrapped") ||
                lower.contains("repair") || lower.contains("craft") || lower.contains("loot") ||
                lower.contains("credits") || lower.contains("inventory") || lower.contains("durability") -> LogCategory.LOOT

                lower.contains("script") || lower.contains("narrative") || lower.contains("audio log") ||
                lower.contains("terminal") || lower.contains("document") || lower.contains("archive") ||
                lower.contains("lore") || lower.contains("node") -> LogCategory.NARRATIVE

                else -> if (critical) LogCategory.COMBAT else LogCategory.SYSTEM
            }
        }

        fun extractImpactValue(msg: String): String? {
            val regex = Regex("([+-]?\\d+\\s*(?:HP|DMG|TOX|%|EXP|Credits))", RegexOption.IGNORE_CASE)
            return regex.find(msg)?.value?.trim()
        }
    }
}

/**
 * Entity type in the Turn-Based Combat Queue.
 */
enum class CombatantType {
    PLAYER,
    ENEMY
}

/**
 * Represents an active combatant participant in the tactical turn queue.
 */
data class CombatQueueEntity(
    val id: String,
    val name: String,
    val glyph: String,
    val type: CombatantType,
    val hp: Int,
    val maxHp: Int,
    val initiative: Int, // Speed / Initiative value determining turn order
    val stateLabel: String, // "PLAYER", "AGGRO", "PATROL", "FLEE"
    val isCurrentTurn: Boolean = false,
    val isAlive: Boolean = true,
    val actionPoints: Int = 2,
    val maxActionPoints: Int = 2,
    val attackPower: Int = 10,
    val defense: Int = 2,
    val gridX: Float = 0f,
    val gridY: Float = 0f,
    val statusEffects: List<StatusEffect> = emptyList()
) {
    val isPlayer: Boolean get() = type == CombatantType.PLAYER
    val hpRatio: Float get() = if (maxHp > 0) (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * State container for the Turn-Based Combat Queue System.
 */
data class TurnCombatQueueState(
    val roundNumber: Int = 1,
    val currentTurnIndex: Int = 0,
    val combatants: List<CombatQueueEntity> = emptyList(),
    val isCombatActive: Boolean = false,
    val activeCombatantId: String? = null,
    val turnPhase: TurnPhase = TurnPhase.PLAYER_INPUT
) {
    val activeCombatant: CombatQueueEntity?
        get() = combatants.firstOrNull { it.id == activeCombatantId }
            ?: combatants.getOrNull(currentTurnIndex)
            ?: combatants.firstOrNull()

    val isPlayerTurn: Boolean
        get() = activeCombatant?.isPlayer == true

    val nextCombatant: CombatQueueEntity?
        get() {
            if (combatants.isEmpty()) return null
            val nextIdx = (currentTurnIndex + 1) % combatants.size
            return combatants.getOrNull(nextIdx)
        }
}

enum class TurnPhase {
    PLAYER_INPUT,
    NPC_ACTION,
    ROUND_TRANSITION
}

/**
 * Character skill categories. Points are allocated on level-up and passively
 * enhance related mechanics (combat, healing, crafting, lockpicking).
 */
enum class SkillType(
    val label: String,
    val description: String
) {
    LOCKPICKING("Lockpicking", "Opens sealed lockers & terminals without forced entry."),
    SCIENCE("Science", "Improves chem synthesis potency and success rates."),
    MELEE("Melee", "Increases unarmed / blade attack power."),
    GUNS("Guns", "Increases ranged weapon damage output."),
    MEDICINE("Medicine", "Boosts stim and healing effectiveness.")
}

/**
 * A passive perk earned on level-up. Stored in the Room `perks` table once acquired.
 */
data class Perk(
    val perkId: String,
    val name: String,
    val description: String,
    val tier: Int = 1
) {
    companion object {
        val POOL: List<Perk> = listOf(
            Perk("tough_skin", "Tough Skin", "+10% damage resistance vs all incoming attacks.", 1),
            Perk("chem_affinity", "Chem Affinity", "Consumable chems & stimpacks last 50% longer.", 1),
            Perk("quick_reflexes", "Quick Reflexes", "+1 Action Point on your combat turn.", 1),
            Perk("scavenger", "Scavenger", "+25% credits & loot from defeated enemies.", 1),
            Perk("weapon_mastery", "Weapon Mastery", "+15% ranged weapon damage.", 2),
            Perk("blade_dancer", "Blade Dancer", "+15% melee damage and +5% crit chance.", 2),
            Perk("field_medic", "Field Medic", "Stimpacks restore +20 HP.", 2),
            Perk("iron_lungs", "Iron Lungs", "Toxicity threshold increased by 25.", 2),
            Perk("silent_takedown", "Silent Takedown", "First strike each combat deals +50% damage.", 3),
            Perk("nanosurge", "Nanosurge", "Regenerate 5 HP at the start of each round.", 3)
        )
    }
}


