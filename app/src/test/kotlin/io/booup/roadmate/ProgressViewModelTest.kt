package io.booup.roadmate

import androidx.lifecycle.*
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.progress.*
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
class ProgressViewModelTest {
    @get:Rule val temporary=TemporaryFolder()
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Source:VehicleAdapter {
        override val source=DataSource.SIMULATED
        override val frames=MutableStateFlow(VehicleFrame())
        override suspend fun connect():AdapterResult<Unit> = AdapterResult.Available(Unit)
        override suspend fun disconnect() { frames.value=frames.value.invalidate(UnavailableReason.DISCONNECTED).copy(sequence=frames.value.sequence+1,connection=ConnectionState()) }
        fun sample(restricted:Boolean=false) {
            fun <T:Any> value(v:T):Reading<T> = Reading.Observed(v,DataMetadata(DataSource.SIMULATED,1_000_000,Freshness.FRESH,
                CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,100_000))
            frames.value=VehicleFrame(sequence=frames.value.sequence+1,connection=ConnectionState(ConnectionStatus.CONNECTED),
                vehicle=VehicleSnapshot(value(LowBeamState.ON),value(19.0),value(0L),value(emptyList()),driverPresent=value(true)),
                restrictions=value(DrivingRestrictions(if(restricted) UIMode.RESTRICTED else UIMode.PARKED,0,restricted)),motion=value(MotionState.STATIONARY),
                care=CareInputs(safetySignals=value(listOf(SafetySignal("test-immediate",SafetyClassification.IMMEDIATE_ATTENTION)))))
        }
    }
    private data class Environment(val model:RoadmateViewModel,val source:Source,val owner:ViewModelStore,val repository:FileTripRepository)
    private suspend fun create():Environment {
        val clock=ContextClock { ObservationTime(1_000_000,100_000) }; val source=Source()
        val repository=FileTripRepository(temporary.newFolder(),RuntimeMode.DEMO)
        repository.load(); repository.setConsent(ConsentScope.TRIP_HISTORY,true,1,DataSource.SIMULATED)
        repository.save(TripRecord("saved",TripState.COMPLETED,100,200,DataSource.SIMULATED,100,50,
            completionReason=TripCompletionReason.CONFIRMED_STATIONARY,quality=TripDataQuality.PARTIAL,createdAtEpochMillis=200),0)
        val owner=object:ViewModelStoreOwner { override val viewModelStore=ViewModelStore() }
        val model=ViewModelProvider(owner,object:ViewModelProvider.Factory {
            override fun <T:ViewModel> create(modelClass:Class<T>):T=requireNotNull(modelClass.cast(RoadmateViewModel { scope ->
                val store=VehicleContextStore(source,RuntimeMode.DEMO,clock,scope)
                val trips=TripCoordinator(repository,RuntimeMode.DEMO,clock,TripIdSource { "new" })
                val post=PostDriveCoordinator(trips,repository,RuntimeMode.DEMO,clock,{store.context.value})
                RuntimeBinding(store,careEngine=DeterministicCareEngine(clock),clock=clock,trips=trips,postDrive=post,
                    progress=ProgressCoordinator(repository,RuntimeMode.DEMO,clock,{post.controlsAllowed()},
                        listOf(LocalSeasonCatalog.first.copy(seasonId="test-season",startsAtEpochMillis=0,endsAtEpochMillis=Long.MAX_VALUE))))
            }))
        })[RoadmateViewModel::class.java]
        model.foreground().join(); source.sample()
        withContext(Dispatchers.Default) { withTimeout(10_000) { model.progressUiState.first { it.contentAllowed && it.active?.progress?.totalXp==20L } } }
        return Environment(model,source,owner.viewModelStore,repository)
    }
    @Test fun actualViewModelAwardsAndClaimsWithoutChangingSafetyJudgmentOrP1Wording()=runTest(dispatcher) {
        val e=create()
        try {
            val before=e.model.uiState.value.presentation!!
            assertEquals(CarePriority.P1_SAFETY,before.priority)
            e.model.progressAction { assertTrue(it.claim("test-season","original-roadmate-badge")) }.join()
            assertEquals(1,e.repository.state.value.progression.claims.size)
            val after=e.model.uiState.value.presentation!!
            assertEquals(before.body,after.body); assertEquals(before.priority,after.priority); assertEquals(before.facts,after.facts)
            assertEquals(20L,e.model.progressUiState.value.active!!.progress.totalXp)
        } finally { clearAndAwaitViewModel(e.owner, e.model) }
    }
    @Test fun restrictedSeasonActionsAreBlockedWhileSafetyCareRemainsVisible()=runTest(dispatcher) {
        val e=create()
        try {
            val before=e.model.uiState.value.presentation!!.body
            e.source.sample(true); runCurrent()
            e.model.progressAction { assertFalse(it.claim("test-season","original-roadmate-badge")) }.join()
            assertFalse(e.model.progressUiState.value.contentAllowed); assertEquals(before,e.model.uiState.value.presentation!!.body)
            assertTrue(e.repository.state.value.progression.claims.isEmpty()); e.model.background().join()
        } finally { clearAndAwaitViewModel(e.owner, e.model) }
    }
}
