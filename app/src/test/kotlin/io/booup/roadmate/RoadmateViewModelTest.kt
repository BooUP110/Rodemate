package io.booup.roadmate

import androidx.lifecycle.*
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.*
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RoadmateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Repository : VehicleContextRepository {
        private val source = object : VehicleAdapter {
            override val source = DataSource.UNKNOWN
            override val frames = MutableStateFlow(VehicleFrame())
            override suspend fun connect() = AdapterResult.Unavailable(UnavailableReason.NOT_VERIFIED)
            override suspend fun disconnect() { frames.value = VehicleFrame() }
        }
        override val context = MutableStateFlow(VehicleContextStore(
            source, RuntimeMode.PRODUCTION, ContextClock { ObservationTime(1_000, 1_000) }, CoroutineScope(SupervisorJob()),
        ).context.value)
        var started = 0
        var stopped = 0
        var closed = false
        override suspend fun foreground() { started += 1 }
        override suspend fun background() { stopped += 1 }
        override fun close() { closed = true }
    }
    private fun create(repository: Repository): Pair<RoadmateViewModel, ViewModelStore> {
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                requireNotNull(modelClass.cast(RoadmateViewModel { RuntimeBinding(repository) }))
        })[RoadmateViewModel::class.java]
        return model to store
    }
    @Test fun firstCreationKeepsUnknownPolicyAndDoesNotStartBackgroundCollection() = runTest(dispatcher) {
        val repository = Repository(); val (model, owner) = create(repository)
        assertEquals(0, repository.started)
        assertSame(repository.context, model.state)
        assertNull(model.state.value.restrictions.currentValue)
        assertFalse(ContentGate().access(ContentArea.COLLECTION, model.state.value).allowed)
        owner.clear()
    }
    @Test fun foregroundAndBackgroundAreDelegatedInLifecycleOrder() = runTest(dispatcher) {
        val repository = Repository(); val (model, owner) = create(repository)
        model.foreground().join(); model.background().join(); model.foreground().join()
        assertEquals(2, repository.started)
        assertEquals(1, repository.stopped)
        owner.clear()
    }
    @Test fun repositoryUpdatesReachApplicationStateWithoutRestampingObservations() = runTest(dispatcher) {
        val repository = Repository(); val (model, owner) = create(repository)
        val initial = model.state.value
        repository.context.value = initial.copy(connection = ConnectionState(ConnectionStatus.DEGRADED))
        assertEquals(ConnectionStatus.DEGRADED, model.state.value.connection.status)
        assertSame(initial.lowBeam, model.state.value.lowBeam)
        owner.clear()
    }
    @Test fun clearingViewModelClosesRuntimeAndCancelsLaterLifecycleRequests() = runTest(dispatcher) {
        val repository = Repository(); val (model, owner) = create(repository)
        owner.clear()
        model.foreground(); runCurrent()
        assertTrue(repository.closed)
        assertEquals(0, repository.started)
    }
}
