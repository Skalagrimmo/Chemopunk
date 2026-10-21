package com.example.engine

/**
 * Epistemic state derived from the number of independent observation roots.
 *
 * An individual belief becomes CORROBORATED at 2 independent roots and
 * CONSENSUS at 3; a collective aggregate reaches CONSENSUS already at 2
 * independent roots (mirroring the canonical EBE 1.0 demo where the watch
 * council reaches consensus on two independent observations).
 */
enum class EpistemicState {
    TENTATIVE,
    CORROBORATED,
    CONSENSUS
}

/**
 * What an agent currently holds about a fact key.
 *
 * `sourceRoots` are independent observation roots (one per agent that
 * directly observed the fact), while `reporterCount` counts every identity
 * that reported it, hearsay included. Echo loops inflate reporterCount but
 * never sourceRoots.
 */
data class EpistemicBelief(
    val key: String,
    val value: String,
    val sourceRoots: Set<String>,
    val reporterCount: Int,
    val lastTick: Int
)

/**
 * Chemopunk port of the EBE 1.0 local-cognition core (Implements repo,
 * ebe-v1.0.0-stable): observation -> belief -> knowledge with source lineage
 * and echo protection.
 *
 * EBE invariants preserved here:
 *  - only a direct observation creates an independent root;
 *  - transmitting a belief copies its roots and never invents new ones;
 *  - re-observing the same fact by the same agent adds no new root;
 *  - `message copies != independent evidence`.
 *
 * Deliberately not ported yet (EBE non-goals for this first slice):
 * memory decay, institutional archives/routes/delay, collectives as
 * persistent entities, publication policy and censorship. The Kotlin
 * surface below is the subset the NPC reputation system needs first.
 */
class EpistemicEngine {

    private val beliefs = HashMap<String, MutableMap<String, EpistemicBelief>>()

    /**
     * A direct observation. The agent becomes an independent root for
     * `key` (re-observation by the same agent refreshes the value/tick but
     * adds no root and no reporter).
     */
    fun observe(agentId: String, key: String, value: String, tick: Int): EpistemicBelief {
        val rootId = agentId + ":" + key
        val byKey = beliefs.getOrPut(agentId) { HashMap() }
        val existing = byKey[key]
        val updated = if (existing == null) {
            EpistemicBelief(key, value, setOf(rootId), 1, tick)
        } else {
            existing.copy(value = value, lastTick = tick)
        }
        byKey[key] = updated
        return updated
    }

    /**
     * Hearsay: `toId` receives the belief held by `fromId`. Roots are copied
     * (never manufactured), the reporter count grows. Receiving hearsay never
     * dilutes an existing larger root set. Returns null when `fromId` holds
     * nothing for the key or when agents coincide.
     */
    fun transmit(fromId: String, toId: String, key: String, tick: Int): EpistemicBelief? {
        if (fromId == toId) return null
        val source = beliefs[fromId]?.get(key) ?: return null
        val byKey = beliefs.getOrPut(toId) { HashMap() }
        val existing = byKey[key]
        val updated = if (existing == null) {
            source.copy(reporterCount = source.reporterCount + 1, lastTick = tick)
        } else {
            existing.copy(
                value = source.value,
                sourceRoots = existing.sourceRoots + source.sourceRoots,
                reporterCount = maxOf(existing.reporterCount, source.reporterCount) + 1,
                lastTick = tick
            )
        }
        byKey[key] = updated
        return updated
    }

    /**
     * Collective publication (EBE demo: the watch council publishes to Levko).
     * The receiver gets a belief carrying every independent root known to any
     * member, with the original lineage preserved.
     */
    fun publishCollective(
        memberIds: Collection<String>,
        toId: String,
        key: String,
        tick: Int
    ): EpistemicBelief? {
        if (toId in memberIds) return null
        val memberBeliefs = memberIds.mapNotNull { belief(it, key) }
        if (memberBeliefs.isEmpty()) return null
        val roots = memberBeliefs.flatMap { it.sourceRoots }.toSet()
        val value = memberBeliefs.first().value
        val reporterCount = memberBeliefs.size + 1
        val byKey = beliefs.getOrPut(toId) { HashMap() }
        val existing = byKey[key]
        val updated = if (existing == null) {
            EpistemicBelief(key, value, roots, reporterCount, tick)
        } else {
            existing.copy(
                value = value,
                sourceRoots = existing.sourceRoots + roots,
                reporterCount = maxOf(existing.reporterCount, reporterCount),
                lastTick = tick
            )
        }
        byKey[key] = updated
        return updated
    }

    fun belief(agentId: String, key: String): EpistemicBelief? = beliefs[agentId]?.get(key)

    fun beliefState(agentId: String, key: String): EpistemicState? =
        belief(agentId, key)?.let { stateFor(it.sourceRoots.size) }

    /**
     * Aggregate epistemic state over a group: CONSENSUS once the group has
     * 2 or more independent roots for the key, TENTATIVE at one,
     * null when nobody knows anything.
     */
    fun collectiveState(agentIds: Collection<String>, key: String): EpistemicState? {
        val roots = agentIds.flatMap { belief(it, key)?.sourceRoots ?: emptySet() }.toSet()
        return when {
            roots.isEmpty() -> null
            roots.size == 1 -> EpistemicState.TENTATIVE
            else -> EpistemicState.CONSENSUS
        }
    }

    companion object {
        /** State of an individual belief by independent root count. */
        fun stateFor(rootCount: Int): EpistemicState = when {
            rootCount >= 3 -> EpistemicState.CONSENSUS
            rootCount == 2 -> EpistemicState.CORROBORATED
            else -> EpistemicState.TENTATIVE
        }
    }
}
