package io.booup.roadmate.care

import io.booup.roadmate.model.*

/** One consumer-side validation boundary shared by all rules. Cached currentValue cannot revive. */
class RuleEligibility(private val context: VehicleContext, private val now: ObservationTime) {
    data class Input<T>(val value: T?, val status: RuleStatus?, val evidence: CareEvidence?, val validUntil: Long)

    fun <T : Any> read(name: String, input: EvaluatedReading<T>, policy: FreshnessPolicy): Input<T> {
        fun rejected(status: RuleStatus) = Input<T>(null, status, null, now.monotonicMillis)
        if (!context.foreground) return rejected(RuleStatus.INELIGIBLE_BACKGROUND)
        if (context.connection.status !in setOf(ConnectionStatus.CONNECTED, ConnectionStatus.DEGRADED))
            return rejected(RuleStatus.INELIGIBLE_DISCONNECTED)
        val actual = input.observation.evaluate(now, policy, context.connection, context.foreground)
        val sourceAllowed = when (context.mode) {
            RuntimeMode.DEMO -> actual.source == DataSource.SIMULATED
            RuntimeMode.PRODUCTION -> actual.source != DataSource.SIMULATED && actual.source != DataSource.UNKNOWN
        }
        if (!sourceAllowed && input.observation is Reading.Observed) return rejected(RuleStatus.INELIGIBLE_SOURCE)
        val status = when (actual.availability) {
            Availability.UNSUPPORTED -> RuleStatus.INELIGIBLE_UNSUPPORTED
            Availability.PERMISSION_DENIED -> RuleStatus.INELIGIBLE_PERMISSION
            Availability.DISCONNECTED -> RuleStatus.INELIGIBLE_DISCONNECTED
            Availability.ERROR -> RuleStatus.INELIGIBLE_INVALID
            Availability.UNKNOWN -> RuleStatus.INELIGIBLE_UNKNOWN
            Availability.UNAVAILABLE -> RuleStatus.INELIGIBLE_MISSING_DATA
            Availability.SUPPORTED -> when {
                actual.freshness == Freshness.STALE || actual.freshness == Freshness.EXPIRED -> RuleStatus.INELIGIBLE_STALE
                actual.currentValue == null -> RuleStatus.INELIGIBLE_UNKNOWN
                input.currentValue == null -> RuleStatus.INELIGIBLE_MISSING_DATA
                else -> null
            }
        }
        if (status != null) return rejected(status)
        val observation = actual.observation as Reading.Observed
        val age = requireNotNull(actual.ageMillis)
        return Input(actual.currentValue, null, CareEvidence(name, observation.value.toString(), actual.source,
            observation.metadata.observedAtEpochMillis, observation.metadata.observedAtMonotonicMillis, age),
            now.monotonicMillis + (policy.freshForMillis - age))
    }
}

data class RuleCandidate(
    val ruleId: RuleId,
    val subject: String,
    val kind: CareKind,
    val category: CareCategory,
    val priority: CarePriority,
    val key: String,
    val evidence: List<CareEvidence>,
    val validUntil: Long,
    val sustainMillis: Long = 0,
    val parameters: Map<String, String> = emptyMap(),
    val actions: Set<CareAction> = setOf(CareAction.ACKNOWLEDGE, CareAction.DISMISS),
) {
    val fingerprint: String get() = "${ruleId.name}:$subject"
}

data class RuleResult(val evaluation: RuleEvaluation, val candidates: List<RuleCandidate> = emptyList())

interface CareRule {
    val id: RuleId
    fun evaluate(context: VehicleContext, eligibility: RuleEligibility, config: CareConfig): RuleResult
}

internal fun rejected(id: RuleId, inputs: List<RuleEligibility.Input<*>>): RuleResult? =
    inputs.firstOrNull { it.status != null }?.let { RuleResult(RuleEvaluation(id, requireNotNull(it.status))) }

internal fun evidence(inputs: List<RuleEligibility.Input<*>>) = inputs.map { requireNotNull(it.evidence) }
internal fun validUntil(inputs: List<RuleEligibility.Input<*>>) = inputs.minOf { it.validUntil }
internal fun unmatched(id: RuleId, status: RuleStatus = RuleStatus.NOT_MATCHED) = RuleResult(RuleEvaluation(id, status))
internal fun matched(id: RuleId, candidates: List<RuleCandidate>) =
    RuleResult(RuleEvaluation(id, RuleStatus.MATCHED), candidates)
