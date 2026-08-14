package com.example.engine

import com.example.data.Enemy
import com.example.data.NpcState
import com.example.data.Player
import com.example.data.TileType
import kotlin.math.hypot

/**
 * Result data produced when an NPC executes a state machine step.
 */
data class NpcTurnResult(
    val updatedNpc: Enemy,
    val damageDealtToPlayer: Int = 0,
    val toxicityDealtToPlayer: Int = 0,
    val stateChanged: Boolean = false,
    val stateChangeLog: String? = null,
    val combatLog: String? = null,
    val floatingText: String? = null,
    val floatingTextColor: Long = 0xFFFF4150
)

/**
 * Core AI State Machine Engine for wasteland NPCs / Enemies.
 *
 * Behaviors:
 * 1. PATROL: Cycles through assigned patrol waypoints or paces territory when player is distant.
 * 2. AGGRESSIVE: Transitions when player enters detection radius. Pursues player and engages in melee.
 * 3. FLEE: Transitions when NPC health drops below flee threshold (e.g. <= 30% max HP). Panics and retreats to cover.
 */
class NpcStateMachineEngine {

    companion object {
        const val DEFAULT_DETECTION_RADIUS = 4.5f
        const val FLEE_HEALTH_THRESHOLD = 0.30f
        const val MELEE_ENGAGEMENT_DISTANCE = 1.25f
    }

    /**
     * Evaluates state machine transitions and performs movement/combat actions for a single NPC.
     */
    fun processNpcTurn(
        npc: Enemy,
        player: Player,
        mapGrid: List<List<TileType>>,
        allNpcs: List<Enemy>
    ): NpcTurnResult {
        if (!npc.isAlive || mapGrid.isEmpty()) {
            return NpcTurnResult(updatedNpc = npc)
        }

        val prevState = npc.state
        var nextState = prevState
        var stateChanged = false
        var stateChangeMsg: String? = null
        var combatMsg: String? = null
        var floatingTxt: String? = null
        var floatingColor: Long = 0xFFFF4150
        var damageDealt = 0
        var toxDealt = 0

        val distToPlayer = hypot(npc.x - player.x, npc.y - player.y)
        val hpRatio = if (npc.maxHp > 0) npc.hp.toFloat() / npc.maxHp.toFloat() else 0f

        // --- 1. STATE TRANSITION LOGIC ---
        if (hpRatio <= npc.fleeThreshold && npc.hp > 0) {
            // Low HP Trigger: Morale Broken -> FLEE
            nextState = NpcState.FLEE
            if (prevState != NpcState.FLEE) {
                stateChanged = true
                stateChangeMsg = "⚠️ [MORALE BROKEN] ${npc.name} is severely wounded (${npc.hp}/${npc.maxHp} HP) and FLEEING in panic!"
                floatingTxt = "💨 FLEEING!"
                floatingColor = 0xFFFFD700 // Yellow panic
            }
        } else if (distToPlayer <= npc.detectionRadius) {
            // Player in Detection Radius -> AGGRESSIVE
            nextState = NpcState.AGGRESSIVE
            if (prevState != NpcState.AGGRESSIVE) {
                stateChanged = true
                val distRounded = (distToPlayer * 10).toInt() / 10f
                stateChangeMsg = "🚨 [ALERT] ${npc.name} spotted target at ${distRounded}m! Switched to AGGRESSIVE!"
                floatingTxt = "⚔ AGGRO!"
                floatingColor = 0xFFFF3B5C // Red aggro
            }
        } else if (distToPlayer > npc.detectionRadius * 1.35f) {
            // Player escaped search perimeter -> PATROL
            nextState = NpcState.PATROL
            if (prevState == NpcState.AGGRESSIVE) {
                stateChanged = true
                stateChangeMsg = "👁 [STAND DOWN] ${npc.name} lost target line-of-sight. Resuming PATROL route."
                floatingTxt = "👁 PATROL"
                floatingColor = 0xFF4FD1C5 // Cyan patrol
            }
        }

        val updatedNpc = npc.copy(
            state = nextState,
            turnsInCurrentState = if (prevState == nextState) npc.turnsInCurrentState + 1 else 1
        )

        // --- 2. STATE ACTION EXECUTION ---
        when (nextState) {
            NpcState.FLEE -> {
                // Fleeing: Move to adjacent walkable tile that MAXIMIZES distance to player
                val bestTile = findBestMove(
                    currentX = updatedNpc.x.toInt(),
                    currentY = updatedNpc.y.toInt(),
                    targetX = player.x.toInt(),
                    targetY = player.y.toInt(),
                    mapGrid = mapGrid,
                    allNpcs = allNpcs,
                    currentNpcId = updatedNpc.id,
                    maximizeDistance = true
                )

                if (bestTile != null) {
                    updatedNpc.x = bestTile.first + 0.5f
                    updatedNpc.y = bestTile.second + 0.5f
                }
            }

            NpcState.AGGRESSIVE -> {
                if (distToPlayer <= MELEE_ENGAGEMENT_DISTANCE) {
                    // In Melee Engagement Range: Strike player!
                    val rawDmg = updatedNpc.attack - (player.defense / 2)
                    damageDealt = rawDmg.coerceAtLeast(3)
                    toxDealt = updatedNpc.toxicityDamage

                    combatMsg = "⚔ [STRIKE] ${updatedNpc.name} attacked you for $damageDealt DMG! (+${toxDealt}% Rads)"
                    floatingTxt = "-$damageDealt HP"
                    floatingColor = 0xFFFF4150
                } else {
                    // Advance: Move to adjacent walkable tile that MINIMIZES distance to player
                    val bestTile = findBestMove(
                        currentX = updatedNpc.x.toInt(),
                        currentY = updatedNpc.y.toInt(),
                        targetX = player.x.toInt(),
                        targetY = player.y.toInt(),
                        mapGrid = mapGrid,
                        allNpcs = allNpcs,
                        currentNpcId = updatedNpc.id,
                        maximizeDistance = false
                    )

                    if (bestTile != null) {
                        updatedNpc.x = bestTile.first + 0.5f
                        updatedNpc.y = bestTile.second + 0.5f
                    }
                }
            }

            NpcState.PATROL -> {
                // Patrol: Cycle through waypoints or patrol area
                val waypoints = updatedNpc.patrolWaypoints
                if (waypoints.isNotEmpty()) {
                    val targetWp = waypoints[updatedNpc.currentWaypointIdx.coerceIn(waypoints.indices)]
                    val distToWp = hypot(updatedNpc.x - (targetWp.first + 0.5f), updatedNpc.y - (targetWp.second + 0.5f))

                    if (distToWp < 0.6f) {
                        // Reached waypoint -> advance to next
                        updatedNpc.currentWaypointIdx = (updatedNpc.currentWaypointIdx + 1) % waypoints.size
                    } else {
                        // Step towards current waypoint
                        val bestTile = findBestMove(
                            currentX = updatedNpc.x.toInt(),
                            currentY = updatedNpc.y.toInt(),
                            targetX = targetWp.first,
                            targetY = targetWp.second,
                            mapGrid = mapGrid,
                            allNpcs = allNpcs,
                            currentNpcId = updatedNpc.id,
                            maximizeDistance = false
                        )

                        if (bestTile != null) {
                            updatedNpc.x = bestTile.first + 0.5f
                            updatedNpc.y = bestTile.second + 0.5f
                        }
                    }
                }
            }
        }

        return NpcTurnResult(
            updatedNpc = updatedNpc,
            damageDealtToPlayer = damageDealt,
            toxicityDealtToPlayer = toxDealt,
            stateChanged = stateChanged,
            stateChangeLog = stateChangeMsg,
            combatLog = combatMsg,
            floatingText = floatingTxt,
            floatingTextColor = floatingColor
        )
    }

    /**
     * Generates a circular or corridor patrol route around a spawn origin.
     */
    fun generatePatrolWaypoints(
        originX: Int,
        originY: Int,
        mapGrid: List<List<TileType>>,
        radius: Int = 2
    ): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        points.add(Pair(originX, originY))

        // Check East, South, West, North offsets for walkable tiles
        val candidateOffsets = listOf(
            Pair(radius, 0),
            Pair(radius, radius),
            Pair(0, radius),
            Pair(-radius, 0),
            Pair(0, -radius)
        )

        for (offset in candidateOffsets) {
            val cx = originX + offset.first
            val cy = originY + offset.second
            if (isTileWalkable(cx, cy, mapGrid)) {
                points.add(Pair(cx, cy))
            }
        }

        return if (points.size >= 2) points else listOf(Pair(originX, originY))
    }

    /**
     * Finds the best adjacent walkable tile to move to.
     */
    private fun findBestMove(
        currentX: Int,
        currentY: Int,
        targetX: Int,
        targetY: Int,
        mapGrid: List<List<TileType>>,
        allNpcs: List<Enemy>,
        currentNpcId: String,
        maximizeDistance: Boolean
    ): Pair<Int, Int>? {
        val neighbors = listOf(
            Pair(currentX + 1, currentY),
            Pair(currentX - 1, currentY),
            Pair(currentX, currentY + 1),
            Pair(currentX, currentY - 1)
        )

        var bestTile: Pair<Int, Int>? = null
        var bestScore = if (maximizeDistance) -1f else 999999f

        for (n in neighbors) {
            val (nx, ny) = n
            if (!isTileWalkable(nx, ny, mapGrid)) continue

            // Don't step on other living NPCs
            val isOccupied = allNpcs.any { other ->
                other.isAlive && other.id != currentNpcId && other.x.toInt() == nx && other.y.toInt() == ny
            }
            if (isOccupied) continue

            val dist = hypot((nx - targetX).toFloat(), (ny - targetY).toFloat())

            if (maximizeDistance) {
                if (dist > bestScore) {
                    bestScore = dist
                    bestTile = n
                }
            } else {
                if (dist < bestScore) {
                    bestScore = dist
                    bestTile = n
                }
            }
        }

        return bestTile
    }

    private fun isTileWalkable(x: Int, y: Int, mapGrid: List<List<TileType>>): Boolean {
        if (mapGrid.isEmpty()) return false
        if (y !in mapGrid.indices || x !in mapGrid[y].indices) return false
        val tile = mapGrid[y][x]
        return tile != TileType.WALL
    }
}
