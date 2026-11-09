package io.booup.roadmate.platform

import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosVehicleAdapterTest {
    private class Session : VehicleReadSession {
        lateinit var observer: VehicleReadObserver
        var closed = false
        var refreshes = 0
        override fun start(observer: VehicleReadObserver) { this.observer = observer }
        override fun refresh() { refreshes++ }
        override fun close() { closed = true }
    }
    @Test fun initializationAloneDoesNotClaimConnectedOrInventVehicleValues() = runTest {
        val session = Session()
        val adapter = PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect()
        assertEquals(ConnectionStatus.CONNECTING,adapter.frames.value.connection.status)
        assertTrue(adapter.frames.value.vehicle.lowBeam is Reading.Unavailable)
        assertEquals(DataSource.VEHICLE_SDK,adapter.source)
        adapter.disconnect()
    }
    @Test fun actualResponsesReachExistingStoreWithSourceClockAndFreshness() = runTest {
        var elapsed=1000L
        val clock=ContextClock { ObservationTime(elapsed,elapsed) }
        val session=Session(); val adapter=PleosVehicleAdapter(clock,backgroundScope,{session})
        val store=VehicleContextStore(adapter,RuntimeMode.PRODUCTION,clock,backgroundScope)
        store.foreground(); runCurrent()
        session.observer.capability("lowBeam",true,"READ")
        session.observer.lowBeam(LowBeamState.ON,"ON","CALLBACK"); runCurrent()
        assertEquals(LowBeamState.ON,store.context.value.lowBeam.currentValue)
        assertEquals(1000L,store.context.value.lowBeam.observedAtEpochMillis)
        assertEquals(DataSource.VEHICLE_SDK,store.context.value.lowBeam.source)
        assertEquals(Availability.SUPPORTED,store.context.value.lowBeam.availability)
        elapsed=7001; advanceTimeBy(1000); runCurrent()
        assertNull(store.context.value.lowBeam.currentValue)
        assertEquals(Freshness.STALE,store.context.value.lowBeam.freshness)
        store.close(); runCurrent()
    }
    @Test fun refreshRequestsDoNotRestampCachedObservations() = runTest {
        var elapsed=1000L
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(elapsed,elapsed) },backgroundScope,{session})
        adapter.connect(); session.observer.lowBeam(LowBeamState.OFF,"OFF","GET")
        elapsed=9000; advanceTimeBy(8000); runCurrent()
        assertTrue(session.refreshes>0)
        assertEquals(1000L,(adapter.frames.value.vehicle.lowBeam as Reading.Observed).metadata.observedAtEpochMillis)
        adapter.disconnect()
    }
    @Test fun releasedSessionCallbacksCannotReviveDisconnectedData() = runTest {
        val sessions=mutableListOf<Session>()
        val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{Session().also { sessions+=it }})
        adapter.connect(); val old=sessions.single()
        old.observer.lowBeam(LowBeamState.ON,"ON","GET"); adapter.disconnect()
        val sequence=adapter.frames.value.sequence
        old.observer.lowBeam(LowBeamState.OFF,"OFF","CALLBACK")
        assertTrue(old.closed); assertEquals(sequence,adapter.frames.value.sequence)
        assertTrue(adapter.frames.value.vehicle.lowBeam is Reading.Unavailable)
        adapter.connect(); old.observer.capability("lowBeam",true,"READ")
        assertEquals(ConnectionStatus.CONNECTING,adapter.frames.value.connection.status)
        sessions.last().observer.lowBeam(LowBeamState.OFF,"OFF","GET")
        assertEquals(LowBeamState.OFF,(adapter.frames.value.vehicle.lowBeam as Reading.Observed).value)
        adapter.disconnect()
    }
    @Test fun unsupportedIsNotFalseAndDoesNotInvalidateOtherSupportedProperties() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.lowBeam(LowBeamState.ON,"ON","GET")
        session.observer.capability("odometer",false,"NONE")
        assertEquals(UnavailableReason.UNSUPPORTED,(adapter.frames.value.vehicle.totalOdometerKilometers as Reading.Unavailable).reason)
        assertEquals(LowBeamState.ON,(adapter.frames.value.vehicle.lowBeam as Reading.Observed).value)
        adapter.disconnect()
    }
    @Test fun cumulativeOdometerCannotBecomeTripDistanceOrContinuousDriving() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.odometer(123.0,"123","GET")
        assertEquals(123.0,(adapter.frames.value.vehicle.totalOdometerKilometers as Reading.Observed).value,0.0)
        assertTrue(adapter.frames.value.vehicle.currentTripDistanceMeters is Reading.Unavailable)
        assertTrue(adapter.frames.value.care.continuousDriving is Reading.Unavailable)
        session.observer.odometer(-1.0,"-1","GET")
        assertEquals(UnavailableReason.INVALID_DATA,(adapter.frames.value.vehicle.totalOdometerKilometers as Reading.Unavailable).reason)
        adapter.disconnect()
    }
    @Test fun unknownLightValueIsNotMappedToOff() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.lowBeam(null,"DAYTIME_RUNNING","GET")
        assertEquals(UnavailableReason.NO_VALUE,(adapter.frames.value.vehicle.lowBeam as Reading.Unavailable).reason)
        adapter.disconnect()
    }
    @Test fun warningDoesNotInventManufacturerSeverityOrActions() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.washerWarning(true,"true","GET")
        val signal=(adapter.frames.value.care.safetySignals as Reading.Observed).value.single()
        assertEquals(SafetyClassification.UNCLASSIFIED,signal.classification)
        assertEquals("LOW_WASHER_FLUID",signal.subject)
        session.observer.washerWarning(null,"null","GET")
        assertTrue(adapter.frames.value.care.safetySignals is Reading.Unavailable)
        adapter.disconnect()
    }
    @Test fun permissionFailureDoesNotPublishFalseNormalData() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.lowBeam(LowBeamState.ON,"ON","GET")
        session.observer.failure("lowBeam",SecurityException("denied"))
        assertEquals(UnavailableReason.PERMISSION_DENIED,(adapter.frames.value.vehicle.lowBeam as Reading.Unavailable).reason)
        assertEquals(ConnectionStatus.DEGRADED,adapter.frames.value.connection.status)
        adapter.disconnect()
    }
    @Test fun serviceFailureInvalidatesAllPreviouslyObservedValues() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.lowBeam(LowBeamState.ON,"ON","GET"); session.observer.odometer(123.0,"123","GET")
        session.observer.failure("odometer",Exception("service"),UnavailableReason.DISCONNECTED)
        assertEquals(ConnectionStatus.FAILED,adapter.frames.value.connection.status)
        assertTrue(adapter.frames.value.vehicle.lowBeam is Reading.Unavailable)
        assertTrue(adapter.frames.value.vehicle.totalOdometerKilometers is Reading.Unavailable)
        val sequence = adapter.frames.value.sequence
        session.observer.lowBeam(LowBeamState.OFF,"OFF","CALLBACK")
        session.observer.capability("lowBeam",true,"READ")
        assertEquals(sequence,adapter.frames.value.sequence)
        adapter.disconnect()
    }

    @Test fun ambiguousSeatSemanticsAreNeverTrustedOrPromotedToDriverPresence() = runTest {
        val clock=ContextClock { ObservationTime(1000,1000) }
        val session=Session(); val adapter=PleosVehicleAdapter(clock,backgroundScope,{session})
        val store=VehicleContextStore(adapter,RuntimeMode.PRODUCTION,clock,backgroundScope)
        store.foreground(); runCurrent()
        for (raw in listOf(true,false)) {
            session.observer.frontLeftSeatOccupied(raw,raw.toString(),"CALLBACK"); runCurrent()
            assertEquals(DataQuality.UNKNOWN,(adapter.frames.value.vehicle.frontLeftSeatOccupied as Reading.Observed).metadata.quality)
            assertNull(store.context.value.frontLeftSeatOccupied.currentValue)
            assertNull(store.context.value.driverPresent.currentValue)
        }
        store.close(); runCurrent()
    }

    @Test fun areaSpecificHvacTargetDoesNotInventOutsideTemperatureOrUserPreference() = runTest {
        val session=Session(); val adapter=PleosVehicleAdapter(ContextClock { ObservationTime(1000,1000) },backgroundScope,{session})
        adapter.connect(); session.observer.frontLeftTargetTemperature(24.5,"24.5","GET")
        assertEquals(24.5,(adapter.frames.value.vehicle.frontLeftTargetTemperatureCelsius as Reading.Observed).value,0.0)
        assertTrue(adapter.frames.value.vehicle.targetTemperatureCelsius is Reading.Unavailable)
        assertTrue(adapter.frames.value.vehicle.outsideTemperatureCelsius is Reading.Unavailable)
        session.observer.frontLeftTargetTemperature(Double.NaN,"NaN","GET")
        assertEquals(UnavailableReason.INVALID_DATA,(adapter.frames.value.vehicle.frontLeftTargetTemperatureCelsius as Reading.Unavailable).reason)
        adapter.disconnect()
    }
}
