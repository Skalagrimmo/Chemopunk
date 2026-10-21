package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the EBE epistemics port, reproducing the canonical EBE 1.0 demo
 * scenario (Implements repo: Mara reports, Oles repeats, Iva observes
 * independently, the council publishes to Levko) plus a Chemopunk witness case.
 */
class EpistemicEngineTest {

    private val engine = EpistemicEngine()

    private fun playEchoScenario() {
        engine.observe("mara", "front:front_0:status", "burned", tick = 1)
        engine.transmit("mara", "oles", "front:front_0:status", tick = 2)
        engine.observe("iva", "front:front_0:status", "burned", tick = 3)
    }

    @Test
    fun observationCreatesSingleRootTentativeBelief() {
        val belief = engine.observe("mara", "front:front_0:status", "burned", tick = 1)
        assertEquals(setOf("mara:front:front_0:status"), belief.sourceRoots)
        assertEquals(1, belief.reporterCount)
        assertEquals(EpistemicState.TENTATIVE, EpistemicEngine.stateFor(belief.sourceRoots.size))
    }

    @Test
    fun echoTransmissionDoesNotManufactureCorroboration() {
        playEchoScenario()
        val oles = engine.belief("oles", "front:front_0:status") ?: return assertEquals("", "missing belief")
        assertEquals(1, oles.sourceRoots.size) // still only the original root
        assertEquals(2, oles.reporterCount)     // Mara + Oles reported
        assertEquals(EpistemicState.TENTATIVE, EpistemicEngine.stateFor(oles.sourceRoots.size))
    }

    @Test
    fun independentObservationDrivesCollectiveConsensus() {
        playEchoScenario()
        val council = listOf("mara", "oles", "iva")
        // Two independent roots (Mara, Iva) -> the collective reaches consensus,
        // exactly like the canonical EBE demo.
        assertEquals(EpistemicState.CONSENSUS, engine.collectiveState(council, "front:front_0:status"))
        // ...while individual hearsay beliefs stay tentative at a single root.
        assertEquals(EpistemicState.TENTATIVE, engine.beliefState("oles", "front:front_0:status"))
    }

    @Test
    fun collectivePublicationPreservesRootLineage() {
        playEchoScenario()
        val published = engine.publishCollective(
            listOf("mara", "oles", "iva"),
            "levko",
            "front:front_0:status",
            tick = 4
        ) ?: return assertEquals("", "missing published belief")
        assertEquals(setOf("mara:front:front_0:status", "iva:front:front_0:status"), published.sourceRoots)
        assertEquals(4, published.reporterCount)
        assertEquals(EpistemicState.CORROBORATED, EpistemicEngine.stateFor(published.sourceRoots.size))
    }

    @Test
    fun repeatedObservationBySameAgentAddsNoIndependentRoot() {
        engine.observe("mara", "front:front_0:status", "burned", tick = 1)
        val again = engine.observe("mara", "front:front_0:status", "burning", tick = 5)
        assertEquals(1, again.sourceRoots.size)
        assertEquals(1, again.reporterCount)
        assertEquals(5, again.lastTick)
    }

    @Test
    fun factionWitnessesReachConsensusOnPlayerActions() {
        // The player attacks a raider; two raiders see it independently.
        engine.observe("raider_a", "player:attacked:raiders", "true", tick = 10)
        engine.observe("raider_b", "player:attacked:raiders", "true", tick = 11)
        val raiders = listOf("raider_a", "raider_b")
        assertEquals(EpistemicState.CONSENSUS, engine.collectiveState(raiders, "player:attacked:raiders"))

        // A distant scientist knows nothing until both rumors arrive; a single
        // rumor stays tentative, the second one corroborates it.
        assertNull(engine.belief("scientist_x", "player:attacked:raiders"))
        engine.transmit("raider_a", "scientist_x", "player:attacked:raiders", tick = 12)
        assertEquals(EpistemicState.TENTATIVE, engine.beliefState("scientist_x", "player:attacked:raiders"))
        engine.transmit("raider_b", "scientist_x", "player:attacked:raiders", tick = 13)
        assertEquals(EpistemicState.CORROBORATED, engine.beliefState("scientist_x", "player:attacked:raiders"))
    }

    @Test
    fun transmittingAnUnknownFactReturnsNull() {
        assertNull(engine.transmit("ghost", "mara", "front:front_0:status", tick = 1))
        engine.observe("mara", "front:front_0:status", "burned", tick = 1)
        assertNull(engine.transmit("mara", "mara", "front:front_0:status", tick = 2))
    }
}
