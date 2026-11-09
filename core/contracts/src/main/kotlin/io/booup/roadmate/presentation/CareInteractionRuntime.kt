package io.booup.roadmate.presentation

import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** Optional display-only demo receipt. No executor or vehicle gateway is registered here. */
fun interface SimulationFeedback { fun receipt(intent: ControlRequestIntent): ExecutionStatus }

/** Single owner on the application dispatcher. Requests bind to a runtime-owned event revision. */
class CareInteractionRuntime(
    private val clock: ContextClock,
    private val config: InteractionConfig = InteractionConfig(),
    private val resolver: CarePresentationResolver = CarePresentationResolver(),
    private val policy: DrivingPresentationPolicy = DrivingPresentationPolicy(),
    private val simulationFeedback: SimulationFeedback? = null,
) {
    private var context: VehicleContext? = null
    private var decision = CareDecision()
    private var profile = CompanionProfiles.dodam
    private var revision = 0L
    private var previousTime: Long? = null
    private val entries = linkedMapOf<String, InteractionRecord>()
    private val mutableUi = MutableStateFlow(CareUiState())
    val uiState = mutableUi.asStateFlow()
    private val mutableRecords = MutableStateFlow<Map<String, InteractionRecord>>(emptyMap())
    val records = mutableRecords.asStateFlow()
    var lastResolution: InteractionRecord? = null
        private set

    fun update(next: CareDecision, vehicleContext: VehicleContext) {
        val now = clock.now()
        val reset = context?.let { it.sessionId != vehicleContext.sessionId || it.mode != vehicleContext.mode } == true ||
            previousTime?.let { now.monotonicMillis < it } == true
        if (reset) entries.clear()
        previousTime = now.monotonicMillis
        context = vehicleContext
        decision = if (vehicleContext.foreground) next else CareDecision()
        val current = (listOfNotNull(decision.activeEvent) + decision.pendingEvents).associateBy { it.eventId }
        entries.keys.toList().filter { it !in current }.forEach { id ->
            lastResolution = transition(entries.getValue(id), InteractionStage.RESOLVED)
            entries.remove(id)
        }
        current.forEach { (id, event) ->
            val frozen = event.presentationSnapshot()
            val old = entries[id]
            entries[id] = if (old == null || !sameApprovalFacts(old.event, frozen)) {
                InteractionRecord(frozen, ++revision)
            } else old.copy(event = frozen)
        }
        val active = decision.activeEvent?.eventId
        entries.keys.toList().forEach { id ->
            var record = entries.getValue(id)
            if (id != active && record.stage in responding) {
                record = transition(record, InteractionStage.SUSPENDED).copy(suspendedFrom = record.stage)
            } else if (id == active) {
                if (record.stage == InteractionStage.SUSPENDED) record = transition(record,
                    record.suspendedFrom ?: InteractionStage.PRESENTED).copy(suspendedFrom = null)
                if (record.stage == InteractionStage.IDLE) record = transition(record, InteractionStage.PRESENTED).copy(
                    deadlineMonotonicMillis = if (record.event.priority == CarePriority.P1_SAFETY) null
                        else now.monotonicMillis + config.responseWindowMillis)
            }
            if (record.deadlineMonotonicMillis?.let { now.monotonicMillis >= it } == true &&
                record.stage in responding + InteractionStage.SUSPENDED) record = transition(record, InteractionStage.EXPIRED)
            entries[id] = record
        }
        publish(now)
    }

    fun displayed(eventId: String, expectedRevision: Long): Boolean {
        val record = active(eventId, expectedRevision) ?: return false
        if (record.stage != InteractionStage.PRESENTED) return false
        if (record.deadlineMonotonicMillis?.let { clock.now().monotonicMillis >= it } == true) {
            entries[eventId] = transition(record, InteractionStage.EXPIRED)
            publish(clock.now())
            return false
        }
        entries[eventId] = transition(record, InteractionStage.AWAITING_RESPONSE)
        publish(clock.now())
        return true
    }

    /** Caller refreshes CareDecision at consumption time before invoking this method. */
    fun respond(response: CareResponse): Boolean {
        val now = clock.now()
        val record = active(response.eventId, response.revision) ?: return false
        if (record.deadlineMonotonicMillis?.let { now.monotonicMillis >= it } == true) {
            entries[response.eventId] = transition(record, InteractionStage.EXPIRED)
            publish(now)
            return false
        }
        if (record.stage !in responding) return false
        val currentContext = context ?: return false
        val mode = policy.mode(currentContext, now)
        val presentation = resolver.resolve(record, profile, mode)
        // Never trust a UI-provided action, parameters, approval timestamp or source.
        if (presentation.actions.none { it.intent == response.intent && it.domainAction == response.action }) return false
        val next = when (response.intent) {
            UserIntent.ACCEPT -> {
                val approved = transition(record, InteractionStage.ACCEPTED)
                val intent = ControlRequestIntent(record.event.eventId, record.revision, response.action,
                    record.event.parameters, now, record.event.source)
                val feedback = if (currentContext.mode == RuntimeMode.DEMO && intent.source == DataSource.SIMULATED)
                    simulationFeedback?.receipt(intent) else null
                transition(approved, InteractionStage.PENDING_EXECUTION).copy(controlIntent = intent,
                    executionStatus = if (feedback == ExecutionStatus.SIMULATED) ExecutionStatus.SIMULATED else ExecutionStatus.NOT_EXECUTED)
            }
            UserIntent.REJECT -> transition(record, InteractionStage.REJECTED)
            UserIntent.ACKNOWLEDGE -> transition(record, InteractionStage.ACKNOWLEDGED)
            UserIntent.DISMISS, UserIntent.REQUEST_LATER -> transition(record, InteractionStage.DISMISSED)
            UserIntent.OPEN_DETAIL -> record.copy(detailRequested = true)
        }
        entries[response.eventId] = next
        publish(now)
        return true
    }

    fun selectCompanion(id: CompanionId): Boolean {
        val currentContext = context ?: return false
        if (policy.mode(currentContext, clock.now()) != DrivingPresentationMode.FULL_PARKED) return false
        if (CompanionProfiles.all.none { it.id == id }) return false
        profile = CompanionProfiles.find(id)
        publish(clock.now())
        return true
    }

    fun close() {
        entries.clear(); decision = CareDecision(); context = null
        mutableRecords.value = emptyMap(); mutableUi.value = CareUiState(companion = profile)
    }

    private fun active(eventId: String, expectedRevision: Long): InteractionRecord? {
        if (context?.foreground != true || decision.activeEvent?.eventId != eventId) return null
        return entries[eventId]?.takeIf { it.revision == expectedRevision }
    }
    private fun sameApprovalFacts(a: CareEvent, b: CareEvent): Boolean =
        a.copy(evidence = emptyList(), conditionDurationMillis = 0) == b.copy(evidence = emptyList(), conditionDurationMillis = 0) &&
            a.evidence.map { Triple(it.signal, it.value, it.source) } == b.evidence.map { Triple(it.signal, it.value, it.source) }

    private fun transition(record: InteractionRecord, stage: InteractionStage) = record.copy(stage = stage,
        transitions = Collections.unmodifiableList((record.transitions + stage).takeLast(12)))

    private fun publish(now: ObservationTime) {
        val mode = context?.let { policy.mode(it, now) } ?: DrivingPresentationMode.COMPACT_DRIVING
        val record = decision.activeEvent?.eventId?.let { entries[it] }
        val visible = record?.takeIf { it.event.priority == CarePriority.P1_SAFETY || it.stage in responding }
        val presentation = visible?.let { resolver.resolve(it, profile, mode) }
            ?.takeUnless { it.priority == CarePriority.P4_PARKED_REWARD && mode == DrivingPresentationMode.COMPACT_DRIVING }
        val notice = record?.let { notice(it) }
        mutableUi.value = CareUiState(presentation, notice, mode, mode == DrivingPresentationMode.FULL_PARKED,
            profile, record?.executionStatus ?: ExecutionStatus.NOT_REQUESTED)
        mutableRecords.value = Collections.unmodifiableMap(LinkedHashMap(entries))
    }
    private fun notice(record: InteractionRecord): String? = when (record.stage) {
        InteractionStage.PENDING_EXECUTION -> if (record.executionStatus == ExecutionStatus.SIMULATED) null // Demo-only UI owns its label.
            else if (record.controlIntent?.action == CareAction.REQUEST_NAVIGATION)
                "휴식 장소 요청을 받았어요. 실제 경로는 변경하지 않았어요."
            else "요청을 받았어요. 차량에서 직접 온도를 조절해 주세요. 앱은 변경하지 않았어요."
        InteractionStage.REJECTED -> if (profile.tone == CompanionTone.WARM) "좋아요. 이번 제안은 건너뛸게요." else "이번 제안은 닫았어요."
        InteractionStage.DISMISSED -> "안내를 닫았어요."
        InteractionStage.ACKNOWLEDGED -> if (record.event.priority == CarePriority.P1_SAFETY) null else "확인했어요."
        InteractionStage.EXPIRED -> "응답 시간이 지나 제안을 닫았어요."
        else -> null
    }
    companion object {
        private val responding = setOf(InteractionStage.PRESENTED, InteractionStage.AWAITING_RESPONSE)
    }
}
