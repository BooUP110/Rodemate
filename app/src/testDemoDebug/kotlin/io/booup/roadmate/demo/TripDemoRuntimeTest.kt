package io.booup.roadmate.demo

import io.booup.roadmate.testing.encryptedTestRepository
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import io.booup.roadmate.storage.FileTripRepository
import io.booup.roadmate.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class TripDemoRuntimeTest(private val id: TripDemoScenarioId) {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun scenarioRunsThroughAdapterContextCareTripAndActualDiskRepository() = runTest {
        var elapsed = 0L
        val clock = ContextClock { ObservationTime(1_000_000 + elapsed, 100_000 + elapsed) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        val engine = DeterministicCareEngine(clock)
        val root = temporary.newFolder()
        val repository = encryptedTestRepository(File(root, "demo"), RuntimeMode.DEMO)
        var serial = 0
        val trips = TripCoordinator(repository, RuntimeMode.DEMO, clock, TripIdSource { "demo-trip-${++serial}" },
            TripConfig(TripDemoScenarios.START_MILLIS, TripDemoScenarios.STOP_MILLIS))
        val controller = DemoTripController(adapter, trips, clock, backgroundScope)
        controller.select(id).join()
        store.foreground(); runCurrent()
        val states = mutableListOf<TripState>()
        for (second in 0L..10L) {
            elapsed = second * 1_000
            adapter.sampleCare(); runCurrent()
            val context = store.context.value
            trips.update(context, engine.evaluate(context))
            states += trips.uiState.value.runtime.state
        }
        if (id in setOf(TripDemoScenarioId.T7_DELETE_TRIP, TripDemoScenarioId.T8_CLEAR_HISTORY,
                TripDemoScenarioId.T10_CONSENT_REVOKED, TripDemoScenarioId.T11_MEMORY_WITHOUT_CONSENT)) {
            // File IO runs on Dispatchers.IO. This timeout uses a real dispatcher; lifecycle evidence has no sleeps.
            withContext(Dispatchers.Default) { withTimeout(10_000) {
                trips.uiState.first { state -> when (id) {
                    TripDemoScenarioId.T7_DELETE_TRIP, TripDemoScenarioId.T8_CLEAR_HISTORY -> state.postDrive == PostDriveState.DELETED
                    else -> state.operationError == StorageError.CONSENT_REQUIRED
                } }
            } }
        }
        val s = trips.uiState.value
        when (id) {
            TripDemoScenarioId.T1_NORMAL_TRIP -> {
                assertTrue("Observed states: $states / $s", states.containsAll(listOf(TripState.START_CANDIDATE, TripState.IN_PROGRESS, TripState.STOP_CANDIDATE, TripState.COMPLETED)))
                assertEquals(1, s.storage.trips.size); assertEquals(TripDataQuality.COMPLETE, s.storage.trips.single().quality)
            }
            TripDemoScenarioId.T2_TRAFFIC_STOP, TripDemoScenarioId.T5_RESUME_STOP -> {
                assertTrue(states.contains(TripState.STOP_CANDIDATE)); assertEquals(TripState.IN_PROGRESS, s.runtime.state)
                assertTrue(s.storage.trips.isEmpty())
            }
            TripDemoScenarioId.T3_UNKNOWN_MOTION -> {
                assertEquals(TripState.IDLE, s.runtime.state); assertNull(s.storage.active); assertTrue(s.storage.trips.isEmpty())
            }
            TripDemoScenarioId.T4_DISCONNECTION -> {
                assertNotNull(s.runtime.active); assertTrue(s.runtime.recoveryRequired); assertTrue(s.storage.trips.isEmpty())
            }
            TripDemoScenarioId.T6_POST_DRIVE_READY -> assertEquals(PostDriveState.READY, s.postDrive)
            TripDemoScenarioId.T7_DELETE_TRIP, TripDemoScenarioId.T8_CLEAR_HISTORY -> {
                assertTrue(s.storage.trips.isEmpty()); assertNull(s.storage.active); assertTrue(s.storage.memories.isEmpty())
                assertEquals(PostDriveState.DELETED, s.postDrive)
            }
            TripDemoScenarioId.T9_STORAGE_ISOLATION -> {
                val production = encryptedTestRepository(File(root, "production"), RuntimeMode.PRODUCTION)
                assertEquals(StorageResult.Success(Unit), production.load()); assertTrue(production.state.value.trips.isEmpty())
                assertEquals(1, s.storage.trips.size)
            }
            TripDemoScenarioId.T10_CONSENT_REVOKED -> {
                assertFalse(s.storage.consents[ConsentScope.EMOTIONAL_MEMORY]!!.granted)
                assertTrue(s.storage.memories.isEmpty()); assertEquals(1, s.storage.trips.size)
            }
            TripDemoScenarioId.T11_MEMORY_WITHOUT_CONSENT -> {
                assertTrue(s.storage.memories.isEmpty()); assertEquals(StorageError.CONSENT_REQUIRED, s.operationError)
            }
            TripDemoScenarioId.T12_PARTIAL_DATA -> {
                val r = s.storage.trips.single(); assertEquals(TripDataQuality.PARTIAL, r.quality)
                assertNull(r.startLocation); assertNull(r.endLocation); assertNull(r.routeSummary); assertNull(r.distanceMeters)
            }
        }
        val reopened = encryptedTestRepository(File(root, "demo"), RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), reopened.load())
        assertEquals(s.storage.trips, reopened.state.value.trips); assertEquals(s.storage.memories, reopened.state.value.memories)
        assertTrue(s.storage.trips.all { it.source == DataSource.SIMULATED })
        store.background(); runCurrent(); store.close()
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun scenarios() = TripDemoScenarioId.entries.map { arrayOf(it) }
    }
}
