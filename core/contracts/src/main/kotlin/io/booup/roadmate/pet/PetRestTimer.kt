package io.booup.roadmate.pet

import io.booup.roadmate.model.*
import io.booup.roadmate.trip.TripEvidenceMapper

/** Foreground, evidence-bounded session clock. Never declares a stopped car to be a pet rest. */
internal class PetRestTimer {
    var value: PetRestSession? = null
        private set
    private var previous: TripEvidence? = null

    fun adopt(session: PetRestSession?, context: VehicleContext, now: ObservationTime, recovery: Boolean = false) {
        value = session?.let { if (recovery && !it.paused) it.copy(paused = true) else it }
        previous = if (recovery) null else evidence(context, now)
    }

    fun tick(context: VehicleContext, now: ObservationTime) {
        val current = value ?: return
        val next = evidence(context, now)
        if (!current.paused && !current.resting) {
            val old = previous
            val gap = old?.let { now.monotonicMillis - it.now.monotonicMillis }
            val continuous = next != null && (old == null ||
                (gap != null && gap in 0..5_000 && now.epochMillis >= old.now.epochMillis &&
                    old.contextSessionId == next.contextSessionId &&
                    now.monotonicMillis <= (old.validUntilMonotonicMillis ?: -1)))
            value = if (!continuous) current.copy(paused = true)
                else if (gap == null) current
                else current.copy(
                    elapsedTotalMillis = (current.elapsedTotalMillis + gap).coerceAtMost(MAX_ELAPSED),
                    elapsedSinceRestMillis = (current.elapsedSinceRestMillis + gap).coerceAtMost(MAX_ELAPSED),
                    paused = current.elapsedTotalMillis + gap >= MAX_ELAPSED,
                )
        }
        previous = next
    }

    private fun evidence(context: VehicleContext, now: ObservationTime): TripEvidence? {
        val result = TripEvidenceMapper().map(context, now)
        return result.takeIf { it.motion != null && context.foreground &&
            it.validUntilMonotonicMillis?.let { until -> now.monotonicMillis <= until } == true }
    }

    companion object { const val MAX_ELAPSED = 7 * 24 * 60 * 60_000L }
}
