package io.booup.roadmate

import io.booup.roadmate.testing.encryptedTestRepository
import androidx.lifecycle.*
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.PostDriveCoordinator
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
class PostDriveViewModelTest {
    @get:Rule val temporary=TemporaryFolder()
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Source : VehicleAdapter {
        override val source=DataSource.SIMULATED
        override val frames=MutableStateFlow(VehicleFrame())
        override suspend fun connect(): AdapterResult<Unit> = AdapterResult.Available(Unit)
        override suspend fun disconnect() { frames.value=frames.value.invalidate(UnavailableReason.DISCONNECTED).copy(sequence=frames.value.sequence+1,connection=ConnectionState()) }
        fun sample(restricted: Boolean=false) {
            fun <T:Any> value(v:T):Reading<T> = Reading.Observed(v,DataMetadata(DataSource.SIMULATED,1_000_000,Freshness.FRESH,
                CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,100_000))
            frames.value=VehicleFrame(sequence=frames.value.sequence+1,connection=ConnectionState(ConnectionStatus.CONNECTED),
                motion=value(MotionState.STATIONARY),restrictions=value(DrivingRestrictions(if(restricted) UIMode.RESTRICTED else UIMode.PARKED,0,restricted)))
        }
    }
    private data class Environment(val model:RoadmateViewModel,val source:Source,val owner:ViewModelStore,val repository:FileTripRepository)
    private suspend fun create():Environment {
        val clock=ContextClock { ObservationTime(1_000_000,100_000) }; val source=Source()
        val repository=encryptedTestRepository(temporary.newFolder(),RuntimeMode.DEMO)
        repository.load(); repository.setConsent(ConsentScope.TRIP_HISTORY,true,1,DataSource.SIMULATED)
        repository.save(TripRecord("saved",TripState.COMPLETED,100,200,DataSource.SIMULATED,100,50,
            completionReason=TripCompletionReason.CONFIRMED_STATIONARY,quality=TripDataQuality.PARTIAL,createdAtEpochMillis=200),0)
        val owner=object:ViewModelStoreOwner { override val viewModelStore=ViewModelStore() }
        val model=ViewModelProvider(owner,object:ViewModelProvider.Factory {
            override fun <T:ViewModel> create(modelClass:Class<T>):T = requireNotNull(modelClass.cast(RoadmateViewModel { scope ->
                val context=VehicleContextStore(source,RuntimeMode.DEMO,clock,scope)
                val trips=TripCoordinator(repository,RuntimeMode.DEMO,clock,TripIdSource { "new" })
                RuntimeBinding(context,careEngine=DeterministicCareEngine(clock),clock=clock,trips=trips,
                    postDrive=PostDriveCoordinator(trips,repository,RuntimeMode.DEMO,clock,{context.context.value}))
            }))
        })[RoadmateViewModel::class.java]
        model.foreground().join(); source.sample()
        // UI policy can become allowed while asynchronous file load is still restoring
        // the completed runtime. Wait for both prerequisites, not host scheduling luck.
        withContext(Dispatchers.Default) { withTimeout(10_000) {
            model.tripUiState.first { it.storage.loaded && it.runtime.state == TripState.COMPLETED }
            model.postDriveUiState.first { it.contentAllowed }
        } }
        return Environment(model,source,owner.viewModelStore,repository)
    }
    @Test fun actualViewModelActionsReachCardReflectionConsentAndConfirmedDeletion()=runTest(dispatcher) {
        val e=create()
        try {
            e.model.postDriveAction { assertTrue(it.open("saved")); assertTrue(it.requestReflection()); assertTrue(it.reflect(ReflectionMood.GOOD,"user text",true)) }.join()
            assertEquals("user text",e.model.postDriveUiState.value.session.card!!.reflection!!.text)
            assertTrue(e.repository.state.value.memories.isEmpty())
            e.model.postDriveAction { it.consent(ConsentScope.EMOTIONAL_MEMORY,true); assertTrue(it.remember(true)); assertFalse(it.clearHistory(false)) }.join()
            assertEquals(1,e.repository.state.value.memories.size); assertEquals(1,e.repository.state.value.trips.size)
            e.model.postDriveAction { assertTrue(it.clearHistory(true)) }.join()
            assertTrue(e.repository.state.value.trips.isEmpty()); assertTrue(e.repository.state.value.postDrive.isEmpty()); assertNull(e.model.postDriveUiState.value.session.card)
        } finally { clearAndAwaitViewModel(e.owner, e.model) }
    }
    @Test fun restrictionChangeHidesOpenCardAndRejectsQueuedUserActionsThroughViewModel()=runTest(dispatcher) {
        val e=create()
        try {
            e.model.postDriveAction { assertTrue(it.open("saved")) }.join(); assertNotNull(e.model.postDriveUiState.value.session.card)
            e.source.sample(restricted=true); runCurrent()
            e.model.postDriveAction { assertFalse(it.reflect(ReflectionMood.GOOD,"blocked",true)); assertFalse(it.clearHistory(true)) }.join()
            assertFalse(e.model.postDriveUiState.value.contentAllowed); assertNull(e.model.postDriveUiState.value.session.card)
            assertEquals(1,e.repository.state.value.trips.size); assertNull(e.repository.state.value.postDrive["saved"]!!.reflection)
            e.model.background().join()
        } finally { clearAndAwaitViewModel(e.owner, e.model) }
    }
}
