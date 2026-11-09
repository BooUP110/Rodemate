package io.booup.roadmate.demo

import io.booup.roadmate.model.*
import io.booup.roadmate.trip.TripCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Explicit destructive TEST setup, demoDebug only. All fixture consent is marked SIMULATED. */
class DemoTripController(private val adapter: DemoVehicleAdapter, private val trips: TripCoordinator,
    private val clock: ContextClock, private val scope: CoroutineScope) {
    private var handled = false
    init {
        scope.launch { trips.uiState.collect { state ->
            val id = adapter.tripSelection.value
            val completed = state.runtime.completed
            if (!handled && id != null && completed != null && state.storage.trips.any { it.tripId == completed.tripId }) {
                handled = true
                when (id) {
                    TripDemoScenarioId.T7_DELETE_TRIP -> trips.delete(completed.tripId)
                    TripDemoScenarioId.T8_CLEAR_HISTORY -> { trips.manual(true); trips.clearHistory() }
                    TripDemoScenarioId.T10_CONSENT_REVOKED -> {
                        trips.saveMemory(memory(completed.tripId, "DEMO_MEMORY_BEFORE_REVOKE"))
                        trips.consent(ConsentScope.EMOTIONAL_MEMORY, false)
                        trips.saveMemory(memory(completed.tripId, "DEMO_MEMORY_BLOCKED"))
                    }
                    TripDemoScenarioId.T11_MEMORY_WITHOUT_CONSENT -> trips.saveMemory(memory(completed.tripId, "DEMO_MEMORY_NO_CONSENT"))
                    else -> Unit
                }
            }
        } }
    }
    fun select(id: TripDemoScenarioId) = scope.launch {
            handled = true
            trips.initialize()
            trips.clearHistory()
            trips.consent(ConsentScope.TRIP_HISTORY, true)
            trips.consent(ConsentScope.EMOTIONAL_MEMORY, id == TripDemoScenarioId.T10_CONSENT_REVOKED)
            handled = false
            adapter.selectTrip(id)
    }
    private fun memory(tripId: String, id: String) = PersistentEmotionalMemory(id, tripId,
        "명시적으로 주입한 시연 기억", clock.now().epochMillis, DataSource.SIMULATED, confirmedByUser = true)
}
