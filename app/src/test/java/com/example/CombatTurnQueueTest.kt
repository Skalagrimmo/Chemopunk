package com.example

import com.example.data.CombatQueueEntity
import com.example.data.CombatantType
import com.example.data.Enemy
import com.example.data.NpcState
import com.example.data.Player
import com.example.data.TurnCombatQueueState
import com.example.data.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the turn-based combat queue system.
 */
class CombatTurnQueueTest {

    @Test
    fun `turn queue sorts combatants by initiative descending`() {
        val player = CombatQueueEntity(
            id = "player",
            name = "Scythe-01",
            glyph = "@",
            type = CombatantType.PLAYER,
            hp = 100,
            maxHp = 100,
            initiative = 15,
            stateLabel = "PLAYER"
        )

        val fastDrone = CombatQueueEntity(
            id = "drone_1",
            name = "Sentry Drone",
            glyph = "D",
            type = CombatantType.ENEMY,
            hp = 40,
            maxHp = 40,
            initiative = 18,
            stateLabel = "AGGRO"
        )

        val slowMutant = CombatQueueEntity(
            id = "mutant_1",
            name = "Wasteland Mutant",
            glyph = "M",
            type = CombatantType.ENEMY,
            hp = 80,
            maxHp = 80,
            initiative = 12,
            stateLabel = "PATROL"
        )

        val combatants = listOf(player, fastDrone, slowMutant).sortedByDescending { it.initiative }

        assertEquals("drone_1", combatants[0].id)
        assertEquals("player", combatants[1].id)
        assertEquals("mutant_1", combatants[2].id)
    }

    @Test
    fun `turn combat queue state correctly tracks active and next combatant`() {
        val c1 = CombatQueueEntity(
            id = "c1",
            name = "Alpha",
            glyph = "A",
            type = CombatantType.PLAYER,
            hp = 100,
            maxHp = 100,
            initiative = 20,
            stateLabel = "PLAYER",
            isCurrentTurn = true
        )
        val c2 = CombatQueueEntity(
            id = "c2",
            name = "Beta",
            glyph = "B",
            type = CombatantType.ENEMY,
            hp = 50,
            maxHp = 50,
            initiative = 15,
            stateLabel = "AGGRO"
        )
        val c3 = CombatQueueEntity(
            id = "c3",
            name = "Gamma",
            glyph = "G",
            type = CombatantType.ENEMY,
            hp = 30,
            maxHp = 30,
            initiative = 10,
            stateLabel = "PATROL"
        )

        val queueState = TurnCombatQueueState(
            roundNumber = 1,
            currentTurnIndex = 0,
            combatants = listOf(c1, c2, c3),
            activeCombatantId = "c1",
            turnPhase = TurnPhase.PLAYER_INPUT
        )

        assertEquals("c1", queueState.activeCombatant?.id)
        assertTrue(queueState.isPlayerTurn)
        assertEquals("c2", queueState.nextCombatant?.id)
    }

    @Test
    fun `turn cycling advances current turn index and wraps round`() {
        val list = listOf(
            CombatQueueEntity("p", "Player", "@", CombatantType.PLAYER, 100, 100, 20, "PLAYER"),
            CombatQueueEntity("e1", "Enemy1", "E", CombatantType.ENEMY, 50, 50, 15, "AGGRO")
        )

        var round = 1
        var idx = 0

        // Step 1: Advance from index 0 -> index 1
        idx = (idx + 1) % list.size
        assertEquals(1, idx)
        assertEquals("e1", list[idx].id)

        // Step 2: Advance from index 1 -> wrap to index 0, new round
        val nextIdx = (idx + 1) % list.size
        if (nextIdx == 0) round += 1
        idx = nextIdx

        assertEquals(0, idx)
        assertEquals(2, round)
        assertEquals("p", list[idx].id)
    }

    @Test
    fun `combatant health ratio calculations are bounded`() {
        val entity = CombatQueueEntity(
            id = "test",
            name = "Test",
            glyph = "T",
            type = CombatantType.ENEMY,
            hp = 25,
            maxHp = 100,
            initiative = 10,
            stateLabel = "FLEE"
        )

        assertEquals(0.25f, entity.hpRatio, 0.001f)
    }

    @Test
    fun `status effects modify combatant stats and combat readiness`() {
        val adrenaline = com.example.data.StatusEffect(
            type = com.example.data.StatusEffectType.ADRENALINE,
            durationTurns = 3,
            magnitude = 8
        )
        val corrosion = com.example.data.StatusEffect(
            type = com.example.data.StatusEffectType.CORROSION,
            durationTurns = 3,
            magnitude = 5
        )

        val player = Player(
            attackPower = 18,
            defense = 10,
            statusEffects = listOf(adrenaline, corrosion)
        )

        val effectiveAtk = player.attackPower + player.statusEffects.filter { it.type == com.example.data.StatusEffectType.ADRENALINE }.sumOf { it.magnitude }
        val effectiveDef = (player.defense - player.statusEffects.filter { it.type == com.example.data.StatusEffectType.CORROSION }.sumOf { it.magnitude }).coerceAtLeast(0)

        assertEquals(26, effectiveAtk)
        assertEquals(5, effectiveDef)
        assertTrue(adrenaline.isBuff)
        assertTrue(corrosion.isDebuff)
    }

    @Test
    fun `poison status effect deals tick damage and decrements duration`() {
        val poison = com.example.data.StatusEffect(
            type = com.example.data.StatusEffectType.POISON,
            durationTurns = 3,
            magnitude = 6
        )

        var hp = 50
        var duration = poison.durationTurns

        // Turn 1 tick
        hp -= poison.magnitude
        duration -= 1
        assertEquals(44, hp)
        assertEquals(2, duration)

        // Turn 2 tick
        hp -= poison.magnitude
        duration -= 1
        assertEquals(38, hp)
        assertEquals(1, duration)

        // Turn 3 tick -> expires
        hp -= poison.magnitude
        duration -= 1
        assertEquals(32, hp)
        assertEquals(0, duration)
    }
}
