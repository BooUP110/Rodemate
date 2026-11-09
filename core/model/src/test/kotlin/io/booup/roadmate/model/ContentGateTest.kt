package io.booup.roadmate.model

import org.junit.Assert.*
import org.junit.Test

class ContentGateTest {
    private val gate = ContentGate()
    private fun context(
        policy: DrivingRestrictions? = null,
        age: Long = 0,
        foreground: Boolean = true,
        connected: Boolean = true,
    ): VehicleContext {
        val connection = ConnectionState(if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED)
        val now = ObservationTime(50_000 + age, 10_000 + age)
        val freshness = FreshnessPolicy(5_000, 10_000)
        val missing = Reading.Unavailable(UnavailableReason.NO_VALUE).evaluate(now, freshness, connection, foreground)
        val restriction = policy?.let { Reading.Observed(it, metadata()) }
            ?: Reading.Unavailable(UnavailableReason.NOT_REQUESTED)
        return VehicleContext(
            RuntimeMode.DEMO, foreground, connection, missing, missing, missing, missing,
            missing, missing, missing, restriction.evaluate(now, freshness, connection, foreground),
            missing, missing, missing,
        )
    }
    private fun unrestricted() = DrivingRestrictions(UIMode.PARKED, 0, false, 50_000)

    @Test fun unknownPolicyLocksEveryParkedContentArea() {
        ContentArea.entries.drop(3).forEach { assertFalse(it.name, gate.access(it, context()).allowed) }
    }
    @Test fun drivingRestrictionBlocksCollectionAndSeasonAndAllParkedContent() {
        val restricted = context(DrivingRestrictions(UIMode.RESTRICTED, requiresDistractionOptimization = true))
        ContentArea.entries.drop(3).forEach { assertFalse(it.name, gate.access(it, restricted).allowed) }
    }
    @Test fun freshExplicitUnrestrictedPolicyAllowsEveryParkedContentArea() {
        ContentArea.entries.drop(3).forEach { assertTrue(it.name, gate.access(it, context(unrestricted())).allowed) }
    }
    @Test fun staleAndExpiredUnrestrictedPolicyCannotKeepContentOpen() {
        for (age in listOf(5_001L, 10_001L)) {
            assertFalse(gate.access(ContentArea.COLLECTION, context(unrestricted(), age)).allowed)
        }
    }
    @Test fun missingRawFlagsCannotBeTreatedAsUnrestricted() {
        assertFalse(gate.access(ContentArea.SEASON_PASS, context(DrivingRestrictions(UIMode.PARKED))).allowed)
    }
    @Test fun unknownModeWithZeroFlagsStillFailsClosed() {
        assertFalse(gate.access(ContentArea.COLLECTION, context(DrivingRestrictions(UIMode.UNKNOWN_RESTRICTED, 0, false))).allowed)
    }
    @Test fun backgroundOrDisconnectedStateCannotOpenParkedContent() {
        assertFalse(gate.access(ContentArea.POSTCARDS, context(unrestricted(), foreground = false)).allowed)
        assertFalse(gate.access(ContentArea.POSTCARDS, context(unrestricted(), connected = false)).allowed)
    }
    @Test fun diagnosticStatusIsVisibleWithUnknownPolicyButHiddenInBackground() {
        assertEquals(GateReason.STATUS_ONLY, gate.access(ContentArea.CARE_STATUS, context()).reason)
        assertFalse(gate.access(ContentArea.CARE_STATUS, context(foreground = false)).allowed)
    }
    @Test fun restrictiveRawFlagsOrDoRequirementOverrideParkedLabel() {
        assertFalse(gate.access(ContentArea.COLLECTION, context(unrestricted().copy(activeRestrictions = 1))).allowed)
        assertFalse(gate.access(ContentArea.COLLECTION, context(unrestricted().copy(requiresDistractionOptimization = true))).allowed)
    }
}
