package io.booup.roadmate

import io.booup.roadmate.testing.encryptedTestRepository
import androidx.lifecycle.*
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import io.booup.roadmate.storage.FileTripRepository
import io.booup.roadmate.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private class Source : VehicleAdapter {
        override val source = DataSource.SIMULATED
        override val frames = MutableStateFlow(VehicleFrame())
        override suspend fun connect(): AdapterResult<Unit> = AdapterResult.Available(Unit)
        override suspend fun disconnect() = Unit
        fun sample(now: ObservationTime, motion: MotionState) {
            fun <T : Any> value(v: T): Reading<T> = Reading.Observed(v, DataMetadata(DataSource.SIMULATED,
                now.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, now.monotonicMillis))
            frames.value = VehicleFrame(sequence = frames.value.sequence + 1, connection = ConnectionState(ConnectionStatus.CONNECTED),
                motion = value(motion), restrictions = value(DrivingRestrictions(UIMode.PARKED, 0, false)))
        }
    }
    @Test fun actualViewModelPersistsCompletedTripAndBackgroundInvalidatesPostDrive() = runTest(dispatcher) {
        var elapsed = 0L
        val clock = ContextClock { ObservationTime(1_000_000 + elapsed, 100_000 + elapsed) }
        val dir = temporary.newFolder(); val source = Source()
        val trips = TripCoordinator(encryptedTestRepository(dir, RuntimeMode.DEMO), RuntimeMode.DEMO, clock,
            TripIdSource { "viewmodel-trip" }, TripConfig(10, 20))
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(RoadmateViewModel { scope ->
                RuntimeBinding(VehicleContextStore(source, RuntimeMode.DEMO, clock, scope),
                    careEngine = DeterministicCareEngine(clock), clock = clock, trips = trips)
            }))
        })[RoadmateViewModel::class.java]
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { trips.uiState.first { it.storage.loaded } } }
            trips.consent(ConsentScope.TRIP_HISTORY, true); model.foreground().join()
            for ((t, expected) in listOf(0L to TripState.START_CANDIDATE, 10L to TripState.IN_PROGRESS,
                    11L to TripState.STOP_CANDIDATE, 31L to TripState.COMPLETED)) {
                elapsed = t; source.sample(clock.now(), if (t < 11) MotionState.MOVING else MotionState.STATIONARY)
                withContext(Dispatchers.Default) { withTimeout(10_000) { trips.uiState.first { it.runtime.state == expected } } }
            }
            assertEquals(PostDriveState.READY, model.tripUiState.value.postDrive)
            assertEquals(1, model.tripUiState.value.storage.trips.size)
            val reopened = encryptedTestRepository(dir, RuntimeMode.DEMO); assertEquals(StorageResult.Success(Unit), reopened.load())
            assertEquals("viewmodel-trip", reopened.state.value.trips.single().tripId)
            model.background().join(); assertEquals(PostDriveState.NOT_READY, model.tripUiState.value.postDrive)
        } finally { clearAndAwaitViewModel(owner.viewModelStore, model) }
    }
}
