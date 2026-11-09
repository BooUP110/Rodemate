package io.booup.roadmate

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class UiCopyTest {
    @Test fun unknownAndUnsupportedDoNotClaimHealthyOrOff() {
        val unknown = UiCopy.unavailable(Availability.UNKNOWN)
        val unsupported = UiCopy.unavailable(Availability.UNSUPPORTED)
        assertNotEquals(unknown, unsupported)
        listOf(unknown, unsupported).forEach { assertFalse(it.contains("정상") || it.contains("꺼짐")) }
    }
    @Test fun permissionAndConnectionFailureRemainDifferentUserProblems() {
        assertNotEquals(UiCopy.unavailable(Availability.PERMISSION_DENIED), UiCopy.unavailable(Availability.ERROR))
        assertNotEquals(UiCopy.connection(ConnectionStatus.FAILED), UiCopy.connection(ConnectionStatus.CONNECTED))
    }
    @Test fun degradedConnectionDoesNotSayAllDataConnected() {
        assertTrue(UiCopy.connection(ConnectionStatus.DEGRADED).contains("일부"))
        assertNotEquals(UiCopy.connection(ConnectionStatus.CONNECTED), UiCopy.connection(ConnectionStatus.DEGRADED))
    }
    @Test fun tripStatesAreDistinctAndNeverExposeEnumNames() {
        assertEquals(TripState.entries.size, TripState.entries.map { UiCopy.trip(it) }.toSet().size)
        TripState.entries.forEach { assertFalse(UiCopy.trip(it).contains(it.name)) }
    }
    @Test fun observationGapDoesNotClaimDriveCompleted() {
        assertEquals(UiCopy.trip(TripState.IN_PROGRESS, true), UiCopy.trip(TripState.COMPLETED, true))
        assertFalse(UiCopy.trip(TripState.COMPLETED, true).contains("마쳤"))
    }
    @Test fun durationUsesSuppliedTripTimeWithoutContinuousDrivingClaim() {
        assertEquals("2분 36초", UiCopy.duration(156089))
        assertFalse(UiCopy.duration(156089).contains("연속"))
        assertEquals("0초", UiCopy.duration(-1))
    }
    @Test fun cardDateIsExplicitUtcAndStableAcrossDeviceLocale() {
        assertEquals("1970.01.01  00:00", UiCopy.date(0))
    }
    @Test fun celsiusDisplayDoesNotDependOnSystemDecimalSeparator() {
        val original = Locale.getDefault()
        try { Locale.setDefault(Locale.GERMANY); assertEquals("24.5°C", UiCopy.temperature(24.5)) }
        finally { Locale.setDefault(original) }
    }
}
