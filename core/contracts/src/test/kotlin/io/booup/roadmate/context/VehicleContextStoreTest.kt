package io.booup.roadmate.context

import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleContextStoreTest {
    private class Source(
        override val source: DataSource = DataSource.SIMULATED,
        val failConnect: Boolean = false,
        val failDisconnect: Boolean = false,
    ) : VehicleAdapter {
        private val mutable = MutableStateFlow(VehicleFrame())
        override val frames = mutable.asStateFlow()
        fun send(frame: VehicleFrame) { mutable.value = frame.copy(sequence = mutable.value.sequence + 1) }
        fun force(frame: VehicleFrame) { mutable.value = frame }
        override suspend fun connect(): AdapterResult<Unit> {
            if (failConnect) error("Test source connection failure")
            send(frames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTING)))
            yield()
            send(frames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED)))
            return AdapterResult.Available(Unit)
        }
        override suspend fun disconnect() {
            if (failDisconnect) error("Test source cleanup failure")
            send(frames.value.copy(connection = ConnectionState()).invalidate(UnavailableReason.DISCONNECTED))
        }
    }
    private fun TestScope.clock() = ContextClock { ObservationTime(1_000_000 + testScheduler.currentTime, 1_000 + testScheduler.currentTime) }
    private fun frame(clock: ContextClock, source: DataSource = DataSource.SIMULATED): VehicleFrame {
        val time = clock.now()
        fun <T : Any> value(value: T): Reading<T> = Reading.Observed(value, DataMetadata(
            source, time.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED,
            PermissionState.GRANTED, DataQuality.VALID, time.monotonicMillis,
        ))
        return VehicleFrame(
            connection = ConnectionState(ConnectionStatus.CONNECTED),
            vehicle = VehicleSnapshot(value(LowBeamState.ON), value(22.0), value(60L), value(emptyList())),
            restrictions = value(DrivingRestrictions(UIMode.PARKED, 0, false, time.epochMillis)),
        )
    }

    @Test fun coldStartIsDisconnectedAndDoesNotPublishCurrentData() = runTest {
        val store = VehicleContextStore(Source(), RuntimeMode.DEMO, clock(), backgroundScope)
        assertEquals(ConnectionStatus.DISCONNECTED, store.context.value.connection.status)
        assertFalse(store.context.value.foreground)
        assertNull(store.context.value.lowBeam.currentValue)
        assertFalse(ContentGate().access(ContentArea.COLLECTION, store.context.value).allowed)
    }
    @Test fun connectionTransitionsFromDisconnectedThroughConnectingToConnected() = runTest {
        val source = Source()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock(), backgroundScope)
        val seen = mutableListOf<ConnectionStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.context.collect { seen += it.connection.status } }
        store.foreground()
        runCurrent()
        assertEquals(listOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTED), seen.distinct())
        assertNull(store.context.value.lowBeam.currentValue)
        store.close()
    }
    @Test fun connectionLostRemovesCurrentValueWithoutErasingItsSourceAndObservationTime() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent()
        source.send(frame(clock)); runCurrent()
        val timestamp = store.context.value.lowBeam.observedAtEpochMillis
        source.send(source.frames.value.copy(connection = ConnectionState())); runCurrent()
        assertNull(store.context.value.lowBeam.currentValue)
        assertEquals(Availability.DISCONNECTED, store.context.value.lowBeam.availability)
        assertEquals(DataSource.SIMULATED, store.context.value.lowBeam.source)
        assertEquals(timestamp, store.context.value.lowBeam.observedAtEpochMillis)
        store.close()
    }
    @Test fun unsupportedFeatureDoesNotDisconnectOtherFeatures() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent()
        val frame = frame(clock)
        source.send(frame.copy(vehicle = frame.vehicle.copy(targetTemperatureCelsius = Reading.Unavailable(UnavailableReason.UNSUPPORTED))))
        runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, store.context.value.connection.status)
        assertEquals(Availability.UNSUPPORTED, store.context.value.targetTemperatureCelsius.availability)
        assertEquals(LowBeamState.ON, store.context.value.lowBeam.currentValue)
        source.send(source.frames.value.copy(connection = ConnectionState())); runCurrent()
        assertEquals(Availability.UNSUPPORTED, store.context.value.targetTemperatureCelsius.availability)
        store.close()
    }
    @Test fun permissionDeniedIsSeparateFromTransportAndOtherCapabilities() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent()
        val frame = frame(clock)
        source.send(frame.copy(vehicle = frame.vehicle.copy(telltaleIdentifiers = Reading.Unavailable(UnavailableReason.PERMISSION_DENIED))))
        runCurrent()
        assertEquals(Availability.PERMISSION_DENIED, store.context.value.warnings.availability)
        assertEquals(ConnectionStatus.CONNECTED, store.context.value.connection.status)
        assertNotNull(store.context.value.lowBeam.currentValue)
        store.close()
    }
    @Test fun periodicAgeReevaluationRemovesCurrentValuesAndLocksStalePolicy() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); source.send(frame(clock)); runCurrent()
        val timestamp = store.context.value.lowBeam.observedAtEpochMillis
        advanceTimeBy(6_000); runCurrent()
        assertEquals(Freshness.STALE, store.context.value.lowBeam.freshness)
        assertNull(store.context.value.lowBeam.currentValue)
        assertEquals(timestamp, store.context.value.lowBeam.observedAtEpochMillis)
        assertFalse(ContentGate().access(ContentArea.SEASON_PASS, store.context.value).allowed)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(Freshness.EXPIRED, store.context.value.lowBeam.freshness)
        store.close()
    }
    @Test fun backgroundAndResumeDoNotRefreshOldObservationsAndNewReadRestoresState() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); source.send(frame(clock)); runCurrent()
        val original = source.frames.value
        store.background()
        assertFalse(store.context.value.foreground)
        store.foreground(); runCurrent()
        source.send(original); runCurrent()
        assertNull(store.context.value.lowBeam.currentValue)
        assertFalse(ContentGate().access(ContentArea.COLLECTION, store.context.value).allowed)
        advanceTimeBy(1)
        source.send(frame(clock)); runCurrent()
        assertEquals(LowBeamState.ON, store.context.value.lowBeam.currentValue)
        assertTrue(ContentGate().access(ContentArea.COLLECTION, store.context.value).allowed)
        store.close()
    }
    @Test fun failedConnectionCanRetryWithoutInventingFreshValues() = runTest {
        val source = Source()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock(), backgroundScope)
        store.foreground(); runCurrent()
        source.send(VehicleFrame(connection = ConnectionState(ConnectionStatus.FAILED, UnavailableReason.SDK_ERROR))); runCurrent()
        assertEquals(ConnectionStatus.FAILED, store.context.value.connection.status)
        store.background(); store.foreground(); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, store.context.value.connection.status)
        assertNull(store.context.value.lowBeam.currentValue)
        store.close()
    }
    @Test fun productionBindingRejectsDemoAdapterAtConstruction() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            VehicleContextStore(Source(), RuntimeMode.PRODUCTION, clock(), backgroundScope)
        }
    }
    @Test fun productionQuarantinesMixedSourceFrameEvenIfAdapterClaimsVehicleSdk() = runTest {
        val source = Source(DataSource.VEHICLE_SDK); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.PRODUCTION, clock, backgroundScope)
        store.foreground(); runCurrent(); source.send(frame(clock)); runCurrent()
        assertEquals(ConnectionStatus.FAILED, store.context.value.connection.status)
        assertEquals(UnavailableReason.SOURCE_MISMATCH, store.context.value.connection.reason)
        assertNull(store.context.value.lowBeam.currentValue)
        store.close()
    }
    @Test fun oldSequenceCannotOverwriteNewerVehicleContext() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); source.send(frame(clock)); runCurrent()
        source.force(VehicleFrame(sequence = 0)); runCurrent()
        assertEquals(LowBeamState.ON, store.context.value.lowBeam.currentValue)
        store.close()
    }
    @Test fun productionAlsoRejectsSimulatedUnavailableFeatureMetadata() = runTest {
        val source = Source(DataSource.VEHICLE_SDK)
        val store = VehicleContextStore(source, RuntimeMode.PRODUCTION, clock(), backgroundScope)
        store.foreground(); runCurrent()
        source.send(VehicleFrame(
            connection = ConnectionState(ConnectionStatus.CONNECTED),
            vehicle = emptyVehicleSnapshot().copy(lowBeam = Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED)),
        )); runCurrent()
        assertEquals(UnavailableReason.SOURCE_MISMATCH, store.context.value.connection.reason)
        store.close()
    }
    @Test fun closingCancelsSubscriptionAndFurtherSamplesCannotReopenContent() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); source.send(frame(clock)); runCurrent()
        store.close(); runCurrent()
        advanceTimeBy(1); source.send(frame(clock)); runCurrent(); store.foreground(); runCurrent()
        assertFalse(store.context.value.foreground)
        assertNull(store.context.value.lowBeam.currentValue)
    }
    @Test fun duplicateSequenceCannotSubstituteDifferentCurrentValues() = runTest {
        val source = Source(); val clock = clock()
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); source.send(frame(clock)); runCurrent()
        source.force(source.frames.value.copy(connection = ConnectionState(ConnectionStatus.FAILED)))
        runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, store.context.value.connection.status)
        assertEquals(LowBeamState.ON, store.context.value.lowBeam.currentValue)
        store.close()
    }
    @Test fun unexpectedSourceAndCleanupExceptionsBecomeExplicitFailureWithoutUncaughtJobs() = runTest {
        val source = Source(failConnect = true, failDisconnect = true)
        val store = VehicleContextStore(source, RuntimeMode.DEMO, clock(), backgroundScope)
        store.foreground(); runCurrent()
        assertEquals(ConnectionStatus.FAILED, store.context.value.connection.status)
        assertEquals(UnavailableReason.SOURCE_ERROR, store.context.value.connection.reason)
        assertEquals(Availability.UNKNOWN, store.context.value.lowBeam.availability)
        assertNull(store.context.value.lowBeam.currentValue)
        assertFalse(ContentGate().access(ContentArea.COLLECTION, store.context.value).allowed)
        store.background()
        store.close()
    }
}
