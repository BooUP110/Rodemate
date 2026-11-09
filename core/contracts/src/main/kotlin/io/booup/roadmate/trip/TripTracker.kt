package io.booup.roadmate.trip

import io.booup.roadmate.care.RuleEligibility
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy

fun interface TripIdSource { fun nextId(): String }

class TripEvidenceMapper(private val policies: ContextFreshnessPolicies = ContextFreshnessPolicies()) {
    fun map(context: VehicleContext, now: ObservationTime): TripEvidence {
        val gate = RuleEligibility(context, now)
        val motion = gate.read("tripMotion", context.motion, policies.occupancy)
        val distance = gate.read("tripDistanceCounter", context.tripDistanceMeters, policies.trip)
        val place = gate.read("tripLocationLabel", context.locationLabel, policies.external)
        val end = gate.read("normalizedTripEnd", context.tripEnd, policies.occupancy)
        return TripEvidence(now, motion.value?.takeUnless { it == MotionState.UNKNOWN },
            motion.evidence?.source ?: DataSource.UNKNOWN, motion.evidence?.let { motion.validUntil }, context.sessionId,
            distance.value?.takeIf { it >= 0 }, place.value?.takeIf { it.isNotBlank() }?.let {
                TripLocation(it, requireNotNull(place.evidence).source)
            }, end.value == TripEndSignal.CONFIRMED_END && end.evidence?.observedAtEpochMillis == now.epochMillis)
    }
}

/** All temporal decisions use explicit evidence/clock input. IDs are an injected dependency. */
class TripTracker(private val mode: RuntimeMode, private val ids: TripIdSource, private val config: TripConfig = TripConfig()) {
    var status = TripRuntimeStatus()
        private set
    private var previous: TripEvidence? = null
    private var candidate: ObservationTime? = null
    private var candidateLocation: TripLocation? = null
    private var candidateCare = emptyList<TripCareOccurrence>()
    private var stopAt: ObservationTime? = null
    private var counterBaseline: Long? = null
    private var lastCounter: Long? = null

    fun restore(snapshot: ActiveTripSnapshot) {
        reset()
        status = TripRuntimeStatus(snapshot.state, snapshot.copy(hasGap = true, distanceMeters = null), recoveryRequired = true)
    }
    fun restoreCompleted(trip: TripRecord) {
        require(trip.state == TripState.COMPLETED)
        reset()
        status = TripRuntimeStatus(TripState.COMPLETED, completed = trip)
    }
    fun reset() {
        status = TripRuntimeStatus(); previous = null; candidate = null; stopAt = null
        candidateLocation = null; candidateCare = emptyList(); counterBaseline = null; lastCounter = null
    }
    fun update(input: TripEvidence, decision: CareDecision = CareDecision()): TripRuntimeStatus {
        val old = previous
        val sourceOk = if (mode == RuntimeMode.DEMO) input.source == DataSource.SIMULATED
            else input.source != DataSource.SIMULATED && input.source != DataSource.UNKNOWN
        val valid = input.motion != null && input.motion != MotionState.UNKNOWN && sourceOk && input.validUntilMonotonicMillis?.let { input.now.monotonicMillis <= it } == true
        val gap = old != null && (old.contextSessionId != input.contextSessionId ||
            input.now.monotonicMillis < old.now.monotonicMillis || input.now.epochMillis < old.now.epochMillis ||
            old.validUntilMonotonicMillis?.let { input.now.monotonicMillis > it } != false)
        if (!valid || gap || (status.active?.let { input.now.epochMillis < it.lastEvidenceAtEpochMillis } == true)) {
            candidate = null; stopAt = null; candidateCare = emptyList(); counterBaseline = null; lastCounter = null
            status = status.active?.let { TripRuntimeStatus(TripState.IN_PROGRESS,
                it.copy(state = TripState.IN_PROGRESS, hasGap = true, distanceMeters = null), recoveryRequired = true) }
                ?: status.takeIf { it.state == TripState.COMPLETED } ?: TripRuntimeStatus()
            previous = null
            if (!valid || status.active?.let { input.now.epochMillis < it.lastEvidenceAtEpochMillis } == true) return status
        }
        val moving = input.motion == MotionState.MOVING
        if (status.active == null) {
            if (!moving) { previous = input; candidate = null; if (status.state != TripState.COMPLETED) status = TripRuntimeStatus(); return status }
            if (candidate == null) {
                candidate = input.now; candidateLocation = input.location; counterBaseline = input.distanceCounterMeters
                candidateCare = emptyList()
            }
            candidateCare = occurrences(candidateCare, decision, "candidate", requireNotNull(candidate).epochMillis, input.now.epochMillis)
            if (input.distanceCounterMeters == null || previous?.distanceCounterMeters?.let { input.distanceCounterMeters?.let { value -> value < it } } == true) counterBaseline = null
            val elapsed = input.now.monotonicMillis - requireNotNull(candidate).monotonicMillis
            if (elapsed < config.startSustainMillis) {
                status = TripRuntimeStatus(TripState.START_CANDIDATE); previous = input; return status
            }
            val id = ids.nextId()
            val active = ActiveTripSnapshot(id, TripState.IN_PROGRESS, requireNotNull(candidate).epochMillis,
                input.now.epochMillis, elapsed, elapsed, delta(input.distanceCounterMeters), candidateLocation,
                if (mode == RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.DERIVED, false,
                candidateCare.map { it.copy(tripId = id) })
            status = TripRuntimeStatus(TripState.IN_PROGRESS, active)
            candidate = null; lastCounter = input.distanceCounterMeters; previous = input
            return status
        }
        var active = requireNotNull(status.active)
        val contiguous = previous?.let { input.now.monotonicMillis >= it.now.monotonicMillis &&
            input.now.monotonicMillis <= (it.validUntilMonotonicMillis ?: -1) } == true
        val elapsed = if (contiguous) input.now.monotonicMillis - requireNotNull(previous).now.monotonicMillis else 0
        if (input.distanceCounterMeters == null || lastCounter == null || input.distanceCounterMeters?.let { it < requireNotNull(lastCounter) } == true) counterBaseline = null
        active = active.copy(lastEvidenceAtEpochMillis = input.now.epochMillis,
            accumulatedDurationMillis = active.accumulatedDurationMillis + elapsed,
            confirmedDrivingMillis = active.confirmedDrivingMillis + if (moving && previous?.motion == MotionState.MOVING) elapsed else 0,
            distanceMeters = delta(input.distanceCounterMeters),
            careEvents = occurrences(active.careEvents, decision, active.tripId, active.startedAtEpochMillis, input.now.epochMillis))
        if (moving) {
            stopAt = null
            status = TripRuntimeStatus(TripState.IN_PROGRESS, active.copy(state = TripState.IN_PROGRESS))
        } else {
            if (stopAt == null) stopAt = input.now
            val stopDuration = input.now.monotonicMillis - requireNotNull(stopAt).monotonicMillis
            if (input.explicitEnd || stopDuration >= config.stopSustainMillis) {
                status = complete(active, input.now, input.location,
                    if (input.explicitEnd) TripCompletionReason.EXPLICIT_END_EVIDENCE else TripCompletionReason.CONFIRMED_STATIONARY)
                stopAt = null; counterBaseline = null; lastCounter = null
            } else status = TripRuntimeStatus(TripState.STOP_CANDIDATE, active.copy(state = TripState.STOP_CANDIDATE))
        }
        previous = input; lastCounter = input.distanceCounterMeters
        return status
    }
    fun manualStart(now: ObservationTime): Boolean {
        if (mode != RuntimeMode.DEMO || status.active != null) return false
        reset()
        status = TripRuntimeStatus(TripState.IN_PROGRESS, ActiveTripSnapshot(ids.nextId(), TripState.IN_PROGRESS,
            now.epochMillis, now.epochMillis, 0, 0, null, null, DataSource.SIMULATED, true))
        return true
    }
    fun manualStop(now: ObservationTime): Boolean {
        if (mode != RuntimeMode.DEMO || status.active == null || now.epochMillis < status.active!!.lastEvidenceAtEpochMillis) return false
        status = complete(status.active!!, now, null, TripCompletionReason.MANUAL_DEMO)
        previous = null; stopAt = null
        return true
    }
    private fun complete(active: ActiveTripSnapshot, now: ObservationTime, end: TripLocation?, reason: TripCompletionReason): TripRuntimeStatus {
        val route = active.startLocation?.let { start -> end?.let { RouteSummary(start, it, active.distanceMeters) } }
        val quality = if (active.accumulatedDurationMillis == 0L) TripDataQuality.MINIMAL
            else if (!active.hasGap && route != null && active.distanceMeters != null) TripDataQuality.COMPLETE else TripDataQuality.PARTIAL
        return TripRuntimeStatus(TripState.COMPLETED, completed = TripRecord(active.tripId, TripState.COMPLETED,
            active.startedAtEpochMillis, now.epochMillis, active.source, active.accumulatedDurationMillis,
            active.confirmedDrivingMillis, active.distanceMeters, active.startLocation, end, route, active.careEvents,
            reason, quality, now.epochMillis))
    }
    private fun delta(counter: Long?): Long? = counterBaseline?.let { base -> counter?.takeIf { it >= base }?.minus(base) }
    private fun occurrences(previous: List<TripCareOccurrence>, decision: CareDecision, id: String, start: Long, now: Long): List<TripCareOccurrence> =
        (previous + decision.emittedEvents.filter { it.createdAtEpochMillis in start..now &&
            (if (mode == RuntimeMode.DEMO) it.source == DataSource.SIMULATED else it.source != DataSource.SIMULATED && it.source != DataSource.UNKNOWN)
        }.map { TripCareOccurrence(id, it.eventId, it.ruleId, it.priority, it.createdAtEpochMillis, it.source) }).distinctBy { it.eventId }
}

class PostDriveEligibility {
    fun evaluate(trip: TripRecord?, runtime: TripState, context: VehicleContext, now: ObservationTime, deleted: Boolean = false): PostDriveState {
        if (deleted) return PostDriveState.DELETED
        if (runtime != TripState.COMPLETED || trip?.state != TripState.COMPLETED) return PostDriveState.NOT_READY
        val motion = RuleEligibility(context, now).read("postDriveMotion", context.motion, ContextFreshnessPolicies().occupancy).value
        if (motion !in setOf(MotionState.STATIONARY, MotionState.PARKED) ||
            DrivingPresentationPolicy().mode(context, now) != DrivingPresentationMode.FULL_PARKED) return PostDriveState.NOT_READY
        return if (trip.reviewed) PostDriveState.ALREADY_REVIEWED else PostDriveState.READY
    }
}
