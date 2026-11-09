package io.booup.roadmate.demo

import io.booup.roadmate.model.*

enum class TripDemoScenarioId {
    T1_NORMAL_TRIP, T2_TRAFFIC_STOP, T3_UNKNOWN_MOTION, T4_DISCONNECTION,
    T5_RESUME_STOP, T6_POST_DRIVE_READY, T7_DELETE_TRIP, T8_CLEAR_HISTORY,
    T9_STORAGE_ISOLATION, T10_CONSENT_REVOKED, T11_MEMORY_WITHOUT_CONSENT, T12_PARTIAL_DATA,
}
object TripDemoScenarios {
    const val START_MILLIS = 3_000L
    const val STOP_MILLIS = 5_000L
    // Includes transport startup time: the Store correctly rejects observations from before reconnect.
    const val MOVING_MILLIS = START_MILLIS + 2_000L
    const val SHORT_STOP_MILLIS = 2_000L
    fun frame(id: TripDemoScenarioId, elapsed: Long, now: ObservationTime, sequence: Long): VehicleFrame {
        fun <T : Any> value(value: T): Reading<T> = Reading.Observed(value, DataMetadata(DataSource.SIMULATED,
            now.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, now.monotonicMillis))
        val disconnected = id == TripDemoScenarioId.T4_DISCONNECTION && elapsed in (MOVING_MILLIS + 1_000) until (MOVING_MILLIS + 3_000)
        val motion = when {
            id == TripDemoScenarioId.T3_UNKNOWN_MOTION -> MotionState.UNKNOWN
            id == TripDemoScenarioId.T4_DISCONNECTION && elapsed >= MOVING_MILLIS + 3_000 -> MotionState.UNKNOWN
            id in setOf(TripDemoScenarioId.T2_TRAFFIC_STOP, TripDemoScenarioId.T5_RESUME_STOP) ->
                if (elapsed in MOVING_MILLIS until (MOVING_MILLIS + SHORT_STOP_MILLIS)) MotionState.STATIONARY else MotionState.MOVING
            elapsed < MOVING_MILLIS -> MotionState.MOVING
            else -> MotionState.STATIONARY
        }
        val partial = id == TripDemoScenarioId.T12_PARTIAL_DATA
        val original = DemoScenarios.frame(DemoScenarios.all.first(), now, sequence)
        val result = original.copy(motion = value(motion), vehicle = original.vehicle.copy(
            currentTripDistanceMeters = if (partial) Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED)
                else value((elapsed.coerceAtMost(MOVING_MILLIS) / 1_000) * 100)),
            locationLabel = if (partial) Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED)
                else value(if (elapsed < MOVING_MILLIS) "DEMO_TRIP_START" else "DEMO_TRIP_END"))
        return if (disconnected) result.invalidate(UnavailableReason.DISCONNECTED).copy(connection = ConnectionState()) else result
    }
}
