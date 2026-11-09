package io.booup.roadmate.demo

import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.RegionEvidenceProvider

enum class PostDriveDemoId {
    P5_1_BASIC_CARD, P5_2_PARTIAL, P5_3_SKIP, P5_4_LOCAL_REFLECTION, P5_5_CONFIRMED_MEMORY,
    P5_6_NO_CONSENT, P5_7_FIRST_REGION, P5_8_REPEAT_REGION, P5_9_UNKNOWN_REGION,
    P5_10_RESTRICTION, P5_11_REVIEWED, P5_12_IDEMPOTENCY, P5_13_DELETE, P5_14_REGION_ISOLATION,
}
object PostDriveDemoScenarios {
    fun provider(adapter: DemoVehicleAdapter) = RegionEvidenceProvider { trip -> region(adapter.postDriveSelection.value, trip) }
    fun region(id: PostDriveDemoId?, trip: TripRecord): RegionEvidence? = if (id in setOf(PostDriveDemoId.P5_3_SKIP,
        PostDriveDemoId.P5_7_FIRST_REGION, PostDriveDemoId.P5_8_REPEAT_REGION, PostDriveDemoId.P5_11_REVIEWED,
        PostDriveDemoId.P5_12_IDEMPOTENCY, PostDriveDemoId.P5_13_DELETE, PostDriveDemoId.P5_14_REGION_ISOLATION)) {
        RegionEvidence(trip.tripId, RegionId("demo_region_coast"), "시연 해안 지역", RegionEvidenceSource.DEMO,
            requireNotNull(trip.endedAtEpochMillis), true)
    } else null
    fun frame(id: PostDriveDemoId, elapsed: Long, now: ObservationTime, sequence: Long): VehicleFrame {
        val f = TripDemoScenarios.frame(if (id == PostDriveDemoId.P5_2_PARTIAL) TripDemoScenarioId.T12_PARTIAL_DATA
            else TripDemoScenarioId.T1_NORMAL_TRIP, elapsed, now, sequence)
        if (id != PostDriveDemoId.P5_10_RESTRICTION || elapsed < 11_000) return f
        return f.copy(restrictions = Reading.Observed(DrivingRestrictions(UIMode.RESTRICTED, null, true),
            DataMetadata(DataSource.SIMULATED, now.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED,
                PermissionState.GRANTED, DataQuality.VALID, now.monotonicMillis)))
    }
}
