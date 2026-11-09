package io.booup.roadmate.care

import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CareContextIntegrationTest {
    private class Source(override val source: DataSource = DataSource.SIMULATED) : VehicleAdapter {
        override val frames = MutableStateFlow(VehicleFrame())
        fun send(value: VehicleFrame) { frames.value = value.copy(sequence = frames.value.sequence + 1) }
        override suspend fun connect(): AdapterResult<Unit> = AdapterResult.Available(Unit)
        override suspend fun disconnect() { send(frames.value.invalidate(UnavailableReason.DISCONNECTED).copy(connection = ConnectionState())) }
    }
    @Test fun normalizedInputsUseStoreFreshnessAndBackgroundInvalidation() = runTest {
        val clock = TestCareClock(); val adapter = Source()
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); adapter.send(frame()); runCurrent()
        assertEquals(LightingCondition.NIGHT, store.context.value.care.lighting.currentValue)
        assertEquals(8_100L, store.context.value.care.continuousDriving.currentValue?.seconds)
        store.background()
        assertNull(store.context.value.care.preference.currentValue)
        assertNull(store.context.value.care.continuousDriving.currentValue)
        store.close()
    }
    @Test fun reconnectDoesNotReviveOldSafetyOrPreferenceObservations() = runTest {
        val clock = TestCareClock(); val adapter = Source()
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); val original = frame(); adapter.send(original); runCurrent()
        val firstSession = store.context.value.sessionId
        adapter.send(original.copy(connection = ConnectionState())); runCurrent()
        clock.elapsed = 1; adapter.send(original); runCurrent()
        assertTrue(store.context.value.sessionId > firstSession)
        assertNull(store.context.value.care.preference.currentValue)
        assertNull(store.context.value.care.safetySignals.currentValue)
        clock.elapsed = 2; adapter.send(frame(2)); runCurrent()
        assertNotNull(store.context.value.care.preference.currentValue)
        store.close()
    }
    @Test fun productionRejectsSimulationInNewNormalizedSlot() = runTest {
        val adapter = Source(DataSource.VEHICLE_SDK)
        val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, TestCareClock(), backgroundScope)
        store.foreground(); runCurrent()
        adapter.send(VehicleFrame(connection = ConnectionState(ConnectionStatus.CONNECTED),
            care = CareInputs(lighting = observed(LightingCondition.NIGHT))))
        runCurrent()
        assertEquals(UnavailableReason.SOURCE_MISMATCH, store.context.value.connection.reason)
        assertNull(store.context.value.care.lighting.currentValue)
        store.close()
    }
    @Test fun newNormalizedSlotCannotSmuggleSimulatedUnavailableIntoProduction() = runTest {
        val adapter = Source(DataSource.VEHICLE_SDK)
        val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, TestCareClock(), backgroundScope)
        store.foreground(); runCurrent()
        adapter.send(VehicleFrame(connection = ConnectionState(ConnectionStatus.CONNECTED),
            care = CareInputs(fogRisk = Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED))))
        runCurrent()
        assertEquals(UnavailableReason.SOURCE_MISMATCH, store.context.value.connection.reason)
        store.close()
    }
    @Test fun normalizedSignalProvenanceIsPreservedWhenDisconnected() = runTest {
        val adapter = Source(); val clock = TestCareClock()
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); adapter.send(frame()); runCurrent()
        adapter.send(adapter.frames.value.copy(connection = ConnectionState())); runCurrent()
        val signal = store.context.value.care.continuousDriving
        assertNull(signal.currentValue)
        assertEquals(DataSource.SIMULATED, signal.source)
        assertEquals(time().epochMillis, signal.observedAtEpochMillis)
        assertEquals(Availability.DISCONNECTED, signal.availability)
        store.close()
    }
}
