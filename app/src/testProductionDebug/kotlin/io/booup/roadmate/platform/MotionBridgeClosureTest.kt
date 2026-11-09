package io.booup.roadmate.platform

import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.*
import io.booup.roadmate.model.*
import io.booup.roadmate.trip.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Port fakes test application mapping, not Android SDK/AVD implementation. */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionBridgeClosureTest {
    private class Motion : MotionAdapter {
        override val observations = MutableStateFlow(MotionObservation())
        override suspend fun connect() = AdapterResult.Available(Unit)
        override suspend fun disconnect() { observations.value = MotionObservation() }
    }
    private class Restrictions : DrivingRestrictionAdapter {
        override val restrictions = MutableStateFlow(DrivingRestrictions())
        override suspend fun connect() = AdapterResult.Available(Unit)
        override suspend fun disconnect() { restrictions.value = DrivingRestrictions() }
    }
    private class Session : VehicleReadSession {
        lateinit var observer: VehicleReadObserver
        override fun start(observer: VehicleReadObserver) { this.observer = observer; observer.capability("lowBeam", true, "READ") }
        override fun refresh() { }
        override fun close() { }
    }
    private fun <T : Any> reading(value: T, at: Long) = Reading.Observed(value,
        DataMetadata(DataSource.SYSTEM, at, Freshness.FRESH, CapabilityState.SUPPORTED,
            PermissionState.GRANTED, DataQuality.VALID, at))

    @Test fun motionNeverChangesUxAuthorityAndUxNeverInventsMotion() = runTest {
        var at = 1000L
        val clock = ContextClock { ObservationTime(at, at) }
        val motion = Motion(); val ux = Restrictions(); val session = Session()
        val adapter = PleosVehicleAdapter(clock, backgroundScope, { session }, restrictionAdapter = ux, motionAdapter = motion)
        val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, clock, backgroundScope)
        store.foreground(); runCurrent()
        ux.restrictions.value = normalizeRestriction(false, 0, at); runCurrent()
        assertNull(store.context.value.motion.currentValue)
        motion.observations.value = MotionObservation(motion = reading(MotionState.MOVING, at)); runCurrent()
        assertEquals(MotionState.MOVING, store.context.value.motion.currentValue)
        assertEquals(UIMode.PARKED, store.context.value.restrictions.currentValue?.mode)
        assertEquals(DataSource.SYSTEM, store.context.value.motion.source)
        assertEquals(PostDriveState.NOT_READY, PostDriveEligibility().evaluate(null, TripState.COMPLETED, store.context.value, clock.now()))
        at += 1000
        ux.restrictions.value = normalizeRestriction(true, 80, at)
        motion.observations.value = MotionObservation(motion = reading(MotionState.STATIONARY, at)); runCurrent()
        assertEquals(MotionState.STATIONARY, store.context.value.motion.currentValue)
        assertFalse(ContentGate().access(ContentArea.SEASON_PASS, store.context.value).allowed)
        store.close(); runCurrent()
    }

    @Test fun auxiliaryParkAndBrakeCannotCreateMotionOrCompleteTrip() = runTest {
        val clock = ContextClock { ObservationTime(1000, 1000) }; val motion = Motion()
        val adapter = PleosVehicleAdapter(clock, backgroundScope, { Session() }, motionAdapter = motion)
        val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, clock, backgroundScope)
        store.foreground(); runCurrent()
        motion.observations.value = MotionObservation(selectedGear = reading(SelectedGear.PARK, 1000), parkingBrakeOn = reading(true, 1000))
        runCurrent()
        assertNull(store.context.value.motion.currentValue)
        val status = TripTracker(RuntimeMode.PRODUCTION, TripIdSource { "unused" }).update(TripEvidenceMapper().map(store.context.value, clock.now()))
        assertEquals(TripState.IDLE, status.state); assertNull(status.completed)
        store.close(); runCurrent()
    }

    @Test fun disconnectedOrBackgroundSessionCannotReviveWithLateMotion() = runTest {
        val clock = ContextClock { ObservationTime(1000, 1000) }; val motion = Motion(); val session = Session()
        val adapter = PleosVehicleAdapter(clock, backgroundScope, { session }, motionAdapter = motion)
        val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, clock, backgroundScope)
        store.foreground(); runCurrent()
        motion.observations.value = MotionObservation(motion = reading(MotionState.MOVING, 1000)); runCurrent()
        session.observer.failure("session", IllegalStateException("gone"), UnavailableReason.DISCONNECTED); runCurrent()
        motion.observations.value = MotionObservation(motion = reading(MotionState.STATIONARY, 1001)); runCurrent()
        assertNull(store.context.value.motion.currentValue)
        store.background(); runCurrent()
        assertFalse(store.context.value.foreground); assertNull(store.context.value.motion.currentValue)
        store.close(); runCurrent()
    }

    @Test fun removedSeatProbeLeavesBothOccupancyAndDriverUnavailable() = runTest {
        val adapter = PleosVehicleAdapter(ContextClock { ObservationTime(1000, 1000) }, backgroundScope, { Session() })
        val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, ContextClock { ObservationTime(1000, 1000) }, backgroundScope)
        store.foreground(); runCurrent()
        assertNull(store.context.value.frontLeftSeatOccupied.currentValue)
        assertNull(store.context.value.driverPresent.currentValue)
        assertTrue(adapter.frames.value.vehicle.frontLeftSeatOccupied is Reading.Unavailable)
        store.close(); runCurrent()
    }
}
