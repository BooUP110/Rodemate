package io.booup.roadmate

import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionBindingTest {
    @Test fun productionHasNoDeveloperPanelOrAutomaticDemoFallback() = runTest {
        val binding = RuntimeBindings.create(backgroundScope, ContextClock { ObservationTime(1_000, 1_000) })
        binding.repository.foreground(); runCurrent()
        val state = binding.repository.context.value
        assertEquals(RuntimeMode.PRODUCTION, state.mode)
        assertNull(binding.developerPanel)
        assertEquals(ConnectionStatus.DISCONNECTED, state.connection.status)
        assertEquals(UnavailableReason.NOT_VERIFIED, state.connection.reason)
        assertNull(state.lowBeam.currentValue)
        assertNotEquals(DataSource.SIMULATED, state.lowBeam.source)
        assertFalse(ContentGate().access(ContentArea.COLLECTION, state).allowed)
        binding.repository.close()
    }
    @Test fun unconfiguredAdapterReturnsExplicitUnverifiedFailureAndNeverConnected() = runTest {
        val adapter = UnconfiguredVehicleAdapter()
        assertEquals(AdapterResult.Unavailable(UnavailableReason.NOT_VERIFIED), adapter.connect())
        assertEquals(ConnectionStatus.DISCONNECTED, adapter.frames.value.connection.status)
        assertEquals(DataSource.UNKNOWN, adapter.source)
    }
    @Test fun resumeOfProductionStillRequiresAnActualFutureSdkImplementation() = runTest {
        val binding = RuntimeBindings.create(backgroundScope, ContextClock { ObservationTime(1_000 + testScheduler.currentTime, 1_000 + testScheduler.currentTime) })
        binding.repository.foreground(); runCurrent()
        binding.repository.background(); advanceTimeBy(60_000)
        binding.repository.foreground(); runCurrent()
        assertNull(binding.repository.context.value.lowBeam.currentValue)
        assertFalse(ContentGate().access(ContentArea.SEASON_PASS, binding.repository.context.value).allowed)
        binding.repository.close()
    }
}
