package io.booup.roadmate.model

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FreshnessBoundaryTest(private val age: Long, private val expected: Freshness) {
    @Test fun timestampAgeDeterminesFreshnessAndOnlyFreshValuesAreCurrent() {
        val observation = Reading.Observed(LowBeamState.OFF, metadata())
        val result = observation.evaluate(
            ObservationTime(50_000 + age, 10_000 + age), FreshnessPolicy(5_000, 15_000),
            ConnectionState(ConnectionStatus.CONNECTED), true,
        )
        assertEquals(expected, result.freshness)
        assertEquals(age, result.ageMillis)
        assertEquals(if (expected == Freshness.FRESH) LowBeamState.OFF else null, result.currentValue)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "age={0}ms expects {1}")
        fun cases() = listOf(
            arrayOf(0L, Freshness.FRESH), arrayOf(5_000L, Freshness.FRESH),
            arrayOf(5_001L, Freshness.STALE), arrayOf(15_000L, Freshness.STALE),
            arrayOf(15_001L, Freshness.EXPIRED),
        )
    }
}

internal fun metadata(
    capability: CapabilityState = CapabilityState.SUPPORTED,
    permission: PermissionState = PermissionState.GRANTED,
    quality: DataQuality = DataQuality.VALID,
) = DataMetadata(DataSource.SIMULATED, 50_000, Freshness.FRESH, capability, permission, quality, 10_000)

class ObservationPolicyTest {
    private val now = ObservationTime(50_000, 10_000)
    private val connected = ConnectionState(ConnectionStatus.CONNECTED)
    private val policy = FreshnessPolicy(5_000, 15_000)

    @Test fun unavailableCausesRemainDistinctWithoutInventingPlatformErrorCodes() {
        val expected = mapOf(
            UnavailableReason.NOT_REQUESTED to Availability.UNKNOWN,
            UnavailableReason.UNSUPPORTED to Availability.UNSUPPORTED,
            UnavailableReason.PERMISSION_DENIED to Availability.PERMISSION_DENIED,
            UnavailableReason.DISCONNECTED to Availability.DISCONNECTED,
            UnavailableReason.SDK_ERROR to Availability.ERROR,
            UnavailableReason.NO_VALUE to Availability.UNAVAILABLE,
            UnavailableReason.DEMO_DATA_MISSING to Availability.UNAVAILABLE,
        )
        expected.forEach { (reason, status) ->
            val result = Reading.Unavailable(reason).evaluate(now, policy, connected, true)
            assertEquals(reason.name, status, result.availability)
            assertNull(result.currentValue)
            assertEquals(Freshness.UNKNOWN, result.freshness)
        }
    }

    @Test fun connectedTransportDoesNotGrantCapabilityOrPermission() {
        val unknown = Reading.Observed(23.0, metadata(CapabilityState.UNKNOWN))
        val denied = Reading.Observed(23.0, metadata(permission = PermissionState.DENIED))
        assertEquals(Availability.UNKNOWN, unknown.evaluate(now, policy, connected, true).availability)
        assertNull(unknown.evaluate(now, policy, connected, true).currentValue)
        assertEquals(Availability.PERMISSION_DENIED, denied.evaluate(now, policy, connected, true).availability)
        assertNull(denied.evaluate(now, policy, connected, true).currentValue)
    }

    @Test fun invalidQualityDoesNotBecomeCurrentEvenWithFreshTimestamp() {
        val result = Reading.Observed(23.0, metadata(quality = DataQuality.INVALID)).evaluate(now, policy, connected, true)
        assertEquals(Availability.ERROR, result.availability)
        assertNull(result.currentValue)
    }

    @Test fun futureObservationIsUnknownAndUnusableRatherThanZeroAge() {
        val result = Reading.Observed(23.0, metadata()).evaluate(
            ObservationTime(49_000, 9_000), policy, connected, true,
        )
        assertNull(result.ageMillis)
        assertEquals(Freshness.UNKNOWN, result.freshness)
        assertNull(result.currentValue)
    }

    @Test fun monotonicAgeSurvivesWallClockRollback() {
        val result = Reading.Observed(23.0, metadata()).evaluate(
            ObservationTime(1_000, 16_000), policy, connected, true,
        )
        assertEquals(6_000L, result.ageMillis)
        assertEquals(Freshness.STALE, result.freshness)
    }

    @Test fun wallClockFallbackFailsClosedWhenItMovesBeforeObservation() {
        val result = Reading.Observed(23.0, metadata().copy(observedAtMonotonicMillis = null)).evaluate(
            ObservationTime(49_000, 16_000), policy, connected, true,
        )
        assertEquals(Freshness.UNKNOWN, result.freshness)
        assertNull(result.currentValue)
    }

    @Test fun sameAgeCanBeStaleForLightAndFreshForHvac() {
        val reading = Reading.Observed(23.0, metadata())
        val later = ObservationTime(56_000, 16_000)
        val policies = ContextFreshnessPolicies()
        assertEquals(Freshness.STALE, reading.evaluate(later, policies.light, connected, true).freshness)
        assertEquals(Freshness.FRESH, reading.evaluate(later, policies.hvac, connected, true).freshness)
    }

    @Test fun staleCachedFreshLabelCannotMakeAValueCurrent() {
        val result = Reading.Observed(23.0, metadata()).evaluate(ObservationTime(70_000, 30_000), policy, connected, true)
        assertEquals(Freshness.EXPIRED, result.freshness)
        assertNull(result.currentValue)
    }

    @Test fun freshSampleIsNotCurrentWhileDisconnectedOrBackgrounded() {
        val reading = Reading.Observed(23.0, metadata())
        assertNull(reading.evaluate(now, policy, ConnectionState(), true).currentValue)
        assertNull(reading.evaluate(now, policy, connected, false).currentValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun expiryBeforeFreshLimitIsRejected() { FreshnessPolicy(10, 9) }
}
