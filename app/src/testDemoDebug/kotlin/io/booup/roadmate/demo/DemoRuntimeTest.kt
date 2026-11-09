package io.booup.roadmate.demo

import io.booup.roadmate.RuntimeBindings
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class DemoScenarioTest(private val id: DemoScenarioId) {
    @Test fun selectedScenarioReachesContextWithoutCreatingCareDecisions() = runTest {
        val clock = ContextClock { ObservationTime(1_000_000 + testScheduler.currentTime, 1_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent()
        adapter.select(id); runCurrent()
        val context = store.context.value
        assertEquals(RuntimeMode.DEMO, context.mode)
        assertEquals(DataSource.SIMULATED, context.lowBeam.source)
        if (id != DemoScenarioId.F_DISCONNECTED) {
            assertEquals(ConnectionStatus.CONNECTED, context.connection.status)
            assertEquals(Freshness.FRESH, context.lowBeam.freshness)
        }
        when (id) {
            DemoScenarioId.A_NORMAL -> {
                assertEquals(LowBeamState.ON, context.lowBeam.currentValue)
                assertEquals(22.0, context.targetTemperatureCelsius.currentValue)
                assertEquals(emptyList<String>(), context.warnings.currentValue)
                assertEquals(0L, context.tripDrivingSeconds.currentValue)
                assertTrue(ContentGate().access(ContentArea.COLLECTION, context).allowed)
            }
            DemoScenarioId.B_NIGHT_LIGHT_OFF -> {
                assertEquals(LowBeamState.OFF, context.lowBeam.currentValue)
                assertEquals(MotionState.MOVING, context.motion.currentValue)
                assertEquals(emptyList<String>(), context.warnings.currentValue)
                assertFalse(ContentGate().access(ContentArea.COLLECTION, context).allowed)
            }
            DemoScenarioId.C_COLD_MORNING -> {
                assertEquals(3.0, context.outsideTemperatureCelsius.currentValue)
                assertEquals(19.0, context.targetTemperatureCelsius.currentValue)
                assertEquals(true, context.driverPresent.currentValue)
            }
            DemoScenarioId.D_WARNING -> assertEquals(listOf("DEMO_WARNING_UNMAPPED"), context.warnings.currentValue)
            DemoScenarioId.E_LONG_DRIVE -> {
                assertEquals(8_100L, context.tripDrivingSeconds.currentValue)
                assertEquals(120_000L, context.tripDistanceMeters.currentValue)
            }
            DemoScenarioId.F_DISCONNECTED -> {
                assertEquals(ConnectionStatus.DISCONNECTED, context.connection.status)
                assertEquals(Availability.DISCONNECTED, context.lowBeam.availability)
                assertNull(context.lowBeam.currentValue)
                assertFalse(ContentGate().access(ContentArea.SEASON_PASS, context).allowed)
            }
            DemoScenarioId.G_UNSUPPORTED -> {
                assertEquals(Availability.UNSUPPORTED, context.targetTemperatureCelsius.availability)
                assertEquals(LowBeamState.ON, context.lowBeam.currentValue)
            }
            DemoScenarioId.H_PERMISSION_UNAVAILABLE -> {
                assertEquals(Availability.PERMISSION_DENIED, context.warnings.availability)
                assertEquals(LowBeamState.ON, context.lowBeam.currentValue)
            }
        }
        assertEquals(Availability.UNKNOWN, context.locationLabel.availability)
        assertEquals(Availability.UNKNOWN, context.navigationLabel.availability)
        store.close()
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = DemoScenarioId.entries.map { arrayOf(it) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DemoRuntimeTest {
    @Test fun demoCompositionRootExposesDeveloperPanelAndOnlySimulatedRuntime() = runTest {
        val binding = RuntimeBindings.create(backgroundScope, ContextClock { ObservationTime(1_000, 1_000) })
        assertEquals(RuntimeMode.DEMO, binding.repository.context.value.mode)
        assertNotNull(binding.developerPanel)
        binding.repository.close()
    }
    @Test fun selectingDisconnectedThenNormalRestoresOnlyNewSimulatedValues() = runTest {
        val clock = ContextClock { ObservationTime(1_000 + testScheduler.currentTime, 1_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); adapter.select(DemoScenarioId.A_NORMAL); runCurrent()
        val first = store.context.value.lowBeam.observedAtEpochMillis!!
        adapter.select(DemoScenarioId.F_DISCONNECTED); runCurrent()
        assertNull(store.context.value.lowBeam.currentValue)
        advanceTimeBy(1)
        adapter.select(DemoScenarioId.A_NORMAL); runCurrent()
        assertEquals(LowBeamState.ON, store.context.value.lowBeam.currentValue)
        assertTrue(store.context.value.lowBeam.observedAtEpochMillis!! > first)
        assertEquals(DataSource.SIMULATED, store.context.value.lowBeam.source)
        store.close()
    }
    @Test fun repeatedScenarioSelectionIsAnExplicitNewReadButLifecycleResumeIsNot() = runTest {
        val clock = ContextClock { ObservationTime(1_000 + testScheduler.currentTime, 1_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); adapter.select(DemoScenarioId.A_NORMAL); runCurrent()
        val first = store.context.value.lowBeam.observedAtEpochMillis!!
        store.background(); advanceTimeBy(20_000); store.foreground(); runCurrent()
        assertNull(store.context.value.lowBeam.currentValue)
        assertFalse(ContentGate().access(ContentArea.COLLECTION, store.context.value).allowed)
        adapter.select(DemoScenarioId.A_NORMAL); runCurrent()
        assertEquals(LowBeamState.ON, store.context.value.lowBeam.currentValue)
        assertTrue(store.context.value.lowBeam.observedAtEpochMillis!! > first)
        store.close()
    }
    @Test fun simulatedWarningDataContainsNoManufacturerSeverityOrMeaning() {
        val scenario = DemoScenarios.all.single { it.id == DemoScenarioId.D_WARNING }
        assertTrue(scenario.warnings.all { it.startsWith("DEMO_") })
        val frame = DemoScenarios.frame(scenario, ObservationTime(1_000, 1_000), 1)
        frame.readings().filterIsInstance<Reading.Observed<*>>().forEach {
            assertEquals(DataSource.SIMULATED, it.metadata.source)
        }
    }
    @Test fun resumingDisconnectedScenarioDoesNotPretendTheTransportRecovered() = runTest {
        val clock = ContextClock { ObservationTime(1_000 + testScheduler.currentTime, 1_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        store.foreground(); runCurrent(); adapter.select(DemoScenarioId.F_DISCONNECTED); runCurrent()
        store.background(); advanceTimeBy(1); store.foreground(); runCurrent()
        assertEquals(ConnectionStatus.DISCONNECTED, store.context.value.connection.status)
        assertNull(store.context.value.lowBeam.currentValue)
        store.close()
    }
}
