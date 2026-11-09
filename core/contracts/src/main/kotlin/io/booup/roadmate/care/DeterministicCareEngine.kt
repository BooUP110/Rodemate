package io.booup.roadmate.care

import io.booup.roadmate.contracts.CareRuleEngine
import io.booup.roadmate.model.*

object CareArbitrator {
    private fun priority(value: CarePriority) = when (value) {
        CarePriority.P1_SAFETY -> 0; CarePriority.P2_SUGGESTION -> 1
        CarePriority.P3_COMPANION -> 2; CarePriority.P4_PARKED_REWARD -> 3
    }
    private fun category(value: CareCategory) = when (value) {
        CareCategory.VEHICLE_WARNING -> 0; CareCategory.LIGHTING -> 1; CareCategory.HVAC -> 2
        CareCategory.REST -> 3; CareCategory.COMPANION -> 4; CareCategory.POST_DRIVE -> 5
    }
    val comparator: Comparator<CareEvent> = compareBy<CareEvent> { priority(it.priority) }
        .thenBy { category(it.category) }.thenBy { it.createdAtEpochMillis }
        .thenBy { it.ruleId.name }.thenBy { it.subject }.thenBy { it.eventId }

    fun order(events: Collection<CareEvent>): List<CareEvent> = events.sortedWith(comparator)
}

data class CareEpisode(
    val ordinal: Long,
    val startedAt: ObservationTime,
    val validUntil: Long,
    val lastNotifiedAt: Long? = null,
    val notificationCount: Int = 0,
    val event: CareEvent? = null,
)

data class CareRuntimeState(
    val sessionId: Long? = null,
    val mode: RuntimeMode? = null,
    val previousTime: ObservationTime? = null,
    val nextOrdinal: Long = 0,
    val episodes: Map<String, CareEpisode> = emptyMap(),
)
data class CareTransition(val state: CareRuntimeState, val decision: CareDecision)

/** Pure reducer: identical context + state + time + config produces identical complete output. */
class CareReducer(private val config: CareConfig = CareConfig()) {
    private val rules: List<CareRule> = listOf(LightRule(), HvacRule(), RestRule(), WarningRule())

    fun evaluate(context: VehicleContext, state: CareRuntimeState, now: ObservationTime): CareTransition {
        val discontinuity = state.sessionId != context.sessionId || state.mode != context.mode ||
            (state.previousTime?.monotonicMillis?.let { now.monotonicMillis < it } == true)
        val previous = if (discontinuity) emptyMap() else state.episodes
        var ordinal = state.nextOrdinal
        val episodes = linkedMapOf<String, CareEpisode>()
        val events = mutableListOf<CareEvent>()
        val emitted = mutableListOf<CareEvent>()
        val evaluations = mutableListOf<RuleEvaluation>()
        val eligibility = RuleEligibility(context, now)
        rules.sortedBy { it.id.name }.forEach { rule ->
            val result = rule.evaluate(context, eligibility, config)
            if (result.candidates.isEmpty()) evaluations += result.evaluation
            result.candidates.sortedBy { it.fingerprint }.forEach { candidate ->
                val old = previous[candidate.fingerprint]?.takeIf { now.monotonicMillis <= it.validUntil }
                val episode = old ?: CareEpisode(++ordinal, now, candidate.validUntil)
                val duration = now.monotonicMillis - episode.startedAt.monotonicMillis
                var next = episode.copy(validUntil = candidate.validUntil)
                var suppression = Suppression.NONE
                var nextAt: Long? = null
                if (duration >= candidate.sustainMillis) {
                    val p1 = candidate.priority == CarePriority.P1_SAFETY
                    val interval = if (p1) config.p1RepeatIntervalMillis else config.cooldowns.getValue(candidate.ruleId)
                    nextAt = episode.lastNotifiedAt?.plus(interval)
                    suppression = when {
                        p1 && episode.notificationCount >= config.p1MaxNotificationsPerEpisode -> Suppression.REPEAT_LIMIT
                        episode.lastNotifiedAt == now.monotonicMillis -> Suppression.DUPLICATE
                        nextAt != null && now.monotonicMillis < nextAt -> Suppression.COOLDOWN
                        else -> Suppression.NONE
                    }
                    if (suppression == Suppression.NONE) {
                        val event = CareEvent(
                            "${context.sessionId}:${candidate.fingerprint}:${episode.startedAt.monotonicMillis}:${episode.ordinal}:${episode.notificationCount}",
                            candidate.kind, candidate.priority,
                            if (context.mode == RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.DERIVED,
                            now.epochMillis, candidate.ruleId, candidate.category, candidate.evidence,
                            candidate.key, candidate.parameters, candidate.actions, candidate.subject, duration,
                        )
                        next = next.copy(lastNotifiedAt = now.monotonicMillis,
                            notificationCount = episode.notificationCount + 1, event = event)
                        emitted += event
                        nextAt = now.monotonicMillis + interval
                    }
                    // Keep a current-condition event during cooldown and while preempted; refresh proof.
                    next.event?.copy(evidence = candidate.evidence, conditionDurationMillis = duration,
                        parameters = candidate.parameters)?.let {
                        next = next.copy(event = it)
                        events += it
                    }
                }
                if (suppression == Suppression.REPEAT_LIMIT) nextAt = null
                evaluations += RuleEvaluation(candidate.ruleId,
                    if (duration >= candidate.sustainMillis) RuleStatus.MATCHED else RuleStatus.WAITING_DURATION,
                    candidate.subject, candidate.evidence, duration, suppression, nextAt)
                episodes[candidate.fingerprint] = next
            }
        }
        val ordered = CareArbitrator.order(events)
        return CareTransition(CareRuntimeState(context.sessionId, context.mode, now, ordinal, episodes),
            CareDecision(ordered.firstOrNull(), ordered.drop(1), CareArbitrator.order(emitted), evaluations))
    }
}

/** Single-owner runtime facade. Clock is injected; no SDK, presentation, purchase or action executor. */
class DeterministicCareEngine(private val clock: ContextClock, config: CareConfig = CareConfig()) : CareRuleEngine {
    private val reducer = CareReducer(config)
    private var state = CareRuntimeState()
    override fun evaluate(context: VehicleContext): CareDecision {
        val transition = reducer.evaluate(context, state, clock.now())
        state = transition.state
        return transition.decision
    }
    override fun reset() { state = CareRuntimeState() }
}
