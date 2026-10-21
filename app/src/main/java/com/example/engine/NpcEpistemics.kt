package com.example.engine

import kotlin.math.max
import kotlin.math.round

/**
 * Kotlin port of the EBE 1.0 local-cognition core (observation -> memory ->
 * belief -> knowledge), adapted for Chemopunk NPC minds.
 *
 * Semantics follow the Lua original (Implements repo, ebe-v1.0.0-stable,
 * ebe/cognition/{observation,memory,belief,knowledge}.lua):
 * - base confidence per evidence kind: direct_local 0.96, transmitted 0.72,
 *   rumor 0.62, inferred 0.52;
 * - memory decays per hour (factor 0.985) and evicts its weakest entries
 *   beyond capacity (128 by default);
 * - beliefs distinguish reporter_count (immediate reporting identities) from
 *   source_count (independent observation roots), so echoing a message never
 *   manufactures corroboration;
 * - knowledge requires direct evidence (confidence >= 0.55) or corroboration
 *   by >= 2 independent roots (confidence >= 0.62).
 */

enum class NpcEvidenceKind(val baseConfidence: Float) {
    DIRECT_LOCAL(0.96f),
    TRANSMITTED(0.72f),
    RUMOR(0.62f),
    INFERRED(0.52f)
}

data class NpcClaim(
    val key: String,
    val value: String,
    val prior: String? = null,
    val confidence: Float = 1.0f
)

data class NpcObservation(
    val id: String,
    val observerId: String,
    val evidence: NpcEvidenceKind,
    val claims: List<NpcClaim>,
    val confidence: Float = evidence.baseConfidence,
    val subjectType: String = "subject",
    val subjectId: String = "unknown",
    val observedAt: Long = 0L,
    /** For non-direct evidence: the agent this report came from. */
    val sourceAgentId: String? = null,
    /** Independent observation roots this evidence descends from (source lineage). */
    val originObservationIds: List<String> = emptyList()
) {
    /** Per-claim confidence folded into the observation confidence (EBE Observation.claims). */
    fun effectiveClaims(): List<NpcClaim> = claims.map {
        it.copy(confidence = clamp01(it.confidence * confidence))
    }

    companion object {
        /**
         * Relays an observation from one agent to another (EBE-style transmission):
         * the relay becomes TRANSMITTED evidence, its confidence drops to the
         * transmitted base, and the origin roots are preserved so relaying a
         * message never creates a new independent source.
         */
        fun relay(
            original: NpcObservation,
            newId: String,
            relayedBy: String,
            receivedBy: String,
            observedAt: Long = 0L
        ): NpcObservation = NpcObservation(
            id = newId,
            observerId = receivedBy,
            evidence = NpcEvidenceKind.TRANSMITTED,
            claims = original.claims,
            confidence = NpcEvidenceKind.TRANSMITTED.baseConfidence,
            subjectType = original.subjectType,
            subjectId = original.subjectId,
            observedAt = observedAt,
            sourceAgentId = relayedBy,
            originObservationIds = original.originObservationIds.ifEmpty { listOf(original.id) }
        )
    }
}

internal fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

private fun round6(v: Float): Float = round(v * 1_000_000f) / 1_000_000f

/**
 * Bounded NPC memory (EBE Memory): fixed capacity, per-hour confidence decay,
 * eviction of the weakest entry once capacity is exceeded, dedupe by id.
 */
class NpcMemory(
    private val capacity: Int = 128,
    private val decayPerHour: Double = 0.985,
    private val minConfidence: Float = 0.08f
) {
    data class Entry(
        val id: String,
        val observation: NpcObservation,
        var confidence: Float,
        val encodedAt: Long
    )

    private val order = mutableListOf<String>()
    private val entries = HashMap<String, Entry>()

    fun size(): Int = entries.size

    fun has(id: String): Boolean = entries.containsKey(id)

    fun add(observation: NpcObservation, tick: Long = 0L): Boolean {
        if (entries.containsKey(observation.id)) return false
        entries[observation.id] = Entry(
            id = observation.id,
            observation = observation,
            confidence = clamp01(observation.confidence),
            encodedAt = tick
        )
        order.add(observation.id)
        trim()
        return true
    }

    private fun trim() {
        while (order.size > capacity) {
            var weakestIndex = 0
            var weakestScore = Float.MAX_VALUE
            for (i in order.indices) {
                val entry = entries[order[i]] ?: continue
                val score = entry.confidence + i * 1e-9f
                if (score < weakestScore) {
                    weakestScore = score
                    weakestIndex = i
                }
            }
            entries.remove(order.removeAt(weakestIndex))
        }
    }

    /** Advances time by [hours]: confidences decay, faded entries are forgotten. */
    fun advance(hours: Int) {
        val h = max(0, hours)
        val factor = Math.pow(decayPerHour, h.toDouble()).toFloat()
        val kept = mutableListOf<String>()
        for (id in order) {
            val entry = entries[id] ?: continue
            entry.confidence = clamp01(entry.confidence * factor)
            if (entry.confidence >= minConfidence) kept.add(id) else entries.remove(id)
        }
        order.clear()
        order.addAll(kept)
    }

    fun all(): List<Entry> = order.mapNotNull { entries[it] }
}

data class NpcBelief(
    val key: String,
    val value: String,
    val confidence: Float,
    val agreement: Float,
    val support: Float,
    val evidenceCount: Int,
    /** Independent observation roots (EBE source_count): echo never inflates this. */
    val sourceCount: Int,
    /** Immediate reporting identities (EBE reporter_count). */
    val reporterCount: Int,
    val directSources: Int,
    val originObservationIds: List<String>
)

data class NpcKnowledge(
    val key: String,
    val value: String,
    val confidence: Float,
    val basis: String,
    val sourceCount: Int
)

/**
 * Rebuilds beliefs/knowledge from a memory, mirroring EBE Belief.rebuild and
 * Knowledge.rebuild. Pure and deterministic: no randomness, sorted keys.
 */
object NpcEpistemics {

    const val DEFAULT_SOURCE_TRUST = 0.72f
    const val DIRECT_KNOWLEDGE_THRESHOLD = 0.55f
    const val CORROBORATED_KNOWLEDGE_THRESHOLD = 0.62f
    const val BASIS_DIRECT = "direct_evidence"
    const val BASIS_CORROBORATED = "corroborated_reports"

    private class Option(
        val value: String,
        var score: Float,
        val sources: MutableList<String>,
        val sourceAgents: MutableSet<String>,
        val roots: MutableSet<String>,
        var directSources: Int
    )

    fun trustFor(evidence: NpcEvidenceKind, sourceAgentId: String?, trust: Map<String, Float>): Float {
        if (evidence == NpcEvidenceKind.DIRECT_LOCAL) return 1.0f
        val source = sourceAgentId ?: return DEFAULT_SOURCE_TRUST
        val t = trust[source] ?: return DEFAULT_SOURCE_TRUST
        return clamp01(t)
    }

    fun rebuildBeliefs(memory: NpcMemory, trust: Map<String, Float> = emptyMap()): List<NpcBelief> {
        val buckets = HashMap<String, HashMap<String, Option>>()

        for (entry in memory.all()) {
            val obs = entry.observation
            val t = trustFor(obs.evidence, obs.sourceAgentId, trust)
            for (claim in obs.effectiveClaims()) {
                val bucket = buckets.getOrPut(claim.key) { HashMap() }
                val option = bucket.getOrPut(claim.value) {
                    Option(claim.value, 0f, mutableListOf(), LinkedHashSet(), sortedSetOf(), 0)
                }
                option.score += clamp01(entry.confidence) * t
                option.sources.add(obs.id)
                val sourceIdentity = obs.sourceAgentId
                    ?: if (obs.evidence == NpcEvidenceKind.DIRECT_LOCAL) obs.observerId else null
                if (sourceIdentity != null) option.sourceAgents.add(sourceIdentity)
                val roots = obs.originObservationIds.ifEmpty {
                    if (obs.evidence != NpcEvidenceKind.DIRECT_LOCAL && obs.sourceAgentId != null) {
                        // Legacy observations without explicit lineage: repeated
                        // reports from the same sender count as one source.
                        listOf("legacy-source:" + obs.sourceAgentId)
                    } else {
                        listOf(obs.id)
                    }
                }
                option.roots.addAll(roots)
                if (obs.evidence == NpcEvidenceKind.DIRECT_LOCAL) option.directSources++
            }
        }

        val beliefs = mutableListOf<NpcBelief>()
        for (key in buckets.keys.sorted()) {
            val bucket = buckets.getValue(key)
            var total = 0f
            var bestKey: String? = null
            var bestScore = -1f
            for ((valueKey, option) in bucket) {
                total += option.score
                if (option.score > bestScore ||
                    (option.score == bestScore && valueKey < (bestKey ?: valueKey))) {
                    bestKey = valueKey
                    bestScore = option.score
                }
            }
            val best = bucket.getValue(bestKey!!)
            val agreement = if (total > 0f) bestScore / total else 0f
            val support = clamp01(bestScore)
            beliefs.add(
                NpcBelief(
                    key = key,
                    value = best.value,
                    confidence = round6(clamp01(agreement * support)),
                    agreement = round6(agreement),
                    support = round6(support),
                    evidenceCount = best.sources.size,
                    sourceCount = best.roots.size,
                    reporterCount = best.sourceAgents.size,
                    directSources = best.directSources,
                    originObservationIds = best.roots.toList().sorted()
                )
            )
        }
        return beliefs
    }

    fun rebuildKnowledge(
        beliefs: List<NpcBelief>,
        directThreshold: Float = DIRECT_KNOWLEDGE_THRESHOLD,
        corroboratedThreshold: Float = CORROBORATED_KNOWLEDGE_THRESHOLD
    ): List<NpcKnowledge> = beliefs.mapNotNull { belief ->
        val direct = belief.directSources > 0
        val corroborated = belief.sourceCount >= 2
        when {
            direct && belief.confidence >= directThreshold -> NpcKnowledge(
                key = belief.key,
                value = belief.value,
                confidence = belief.confidence,
                basis = BASIS_DIRECT,
                sourceCount = belief.sourceCount
            )
            corroborated && belief.confidence >= corroboratedThreshold -> NpcKnowledge(
                key = belief.key,
                value = belief.value,
                confidence = belief.confidence,
                basis = BASIS_CORROBORATED,
                sourceCount = belief.sourceCount
            )
            else -> null
        }
    }.sortedBy { it.key }
}
