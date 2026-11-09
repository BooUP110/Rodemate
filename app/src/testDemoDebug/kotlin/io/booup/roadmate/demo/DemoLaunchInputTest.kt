package io.booup.roadmate.demo

import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoLaunchInputTest {
    @Test fun unknownPolicyBlocksUiSelectorButExplicitHostInputCanSeedAFreshSimulation() = runTest {
        val clock = ContextClock { ObservationTime(1_000_000 + testScheduler.currentTime, 100_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        val input = DemoLaunchInput(adapter, store, clock, backgroundScope)
        assertFalse(input.fromUi("I4_CHARACTER_SWITCH"))
        input.fromHost("I4_CHARACTER_SWITCH"); runCurrent()
        assertNull(adapter.interactionSelection.value)
        store.foreground(); runCurrent(); advanceTimeBy(2); runCurrent()
        assertEquals(InteractionDemoScenarioId.I4_CHARACTER_SWITCH, adapter.interactionSelection.value)
        assertEquals(UIMode.PARKED, store.context.value.restrictions.currentValue?.mode)
        assertTrue(input.fromUi("I1_P1_LIGHT")); runCurrent()
        assertFalse(input.fromUi("I4_CHARACTER_SWITCH"))
        store.close()
    }
    @Test fun invalidHostCommandsDoNotCreateAnyScenarioOrData() = runTest {
        val clock = ContextClock { ObservationTime(1_000, 1_000) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        val input = DemoLaunchInput(adapter, store, clock, backgroundScope)
        input.fromHost("UNKNOWN_COMMAND"); runCurrent()
        assertNull(adapter.interactionSelection.value)
        assertNull(adapter.careSelection.value)
        assertNull(adapter.selection.value)
        store.close()
    }
}
