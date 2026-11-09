package io.booup.roadmate.model

import org.junit.Assert.*
import org.junit.Test

class BoundaryModelsTest {
    @Test fun coldStartDoesNotAssertParkingOrInventPlatformFlags() {
        val restrictions = DrivingRestrictions()
        assertEquals(UIMode.UNKNOWN_RESTRICTED, restrictions.mode)
        assertNull(restrictions.activeRestrictions)
        assertNull(restrictions.requiresDistractionOptimization)
        assertNull(restrictions.observedAtEpochMillis)
    }

    @Test fun unavailableLightCannotMasqueradeAsObservedOff() {
        val reading: Reading<LowBeamState> = Reading.Unavailable(UnavailableReason.NOT_CONNECTED)
        assertFalse(reading is Reading.Observed)
    }

    @Test fun observedDataRetainsSimulatedSourceAndStaleQuality() {
        val metadata = DataMetadata(
            DataSource.SIMULATED, 10, Freshness.STALE,
            CapabilityState.UNKNOWN, PermissionState.UNKNOWN, DataQuality.INVALID,
        )
        val reading = Reading.Observed(LowBeamState.OFF, metadata)
        assertEquals(DataSource.SIMULATED, reading.metadata.source)
        assertEquals(Freshness.STALE, reading.metadata.freshness)
        assertEquals(DataQuality.INVALID, reading.metadata.quality)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingProposalCannotBeAnApproval() {
        UserApproval("", 10, 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveApprovalWindowIsRejected() {
        UserApproval("proposal", 20, 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeObservationTimestampIsRejected() {
        DataMetadata(
            DataSource.VEHICLE_SDK, -1, Freshness.UNKNOWN,
            CapabilityState.UNKNOWN, PermissionState.UNKNOWN, DataQuality.UNKNOWN,
        )
    }
}
