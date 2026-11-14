package io.booup.roadmate

import androidx.lifecycle.*
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import io.booup.roadmate.pet.PetTravelCoordinator
import io.booup.roadmate.storage.FilePetTravelRepository
import io.booup.roadmate.testing.*
import io.booup.roadmate.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PetTravelViewModelTest {
    @get:Rule val temporary=TemporaryFolder()
    private val dispatcher=StandardTestDispatcher()
    @Before fun setUp() {Dispatchers.setMain(dispatcher)}
    @After fun tearDown() {Dispatchers.resetMain()}
    private class Source:VehicleAdapter {
        override val source=DataSource.SIMULATED
        override val frames=MutableStateFlow(VehicleFrame())
        override suspend fun connect():AdapterResult<Unit> = AdapterResult.Available(Unit)
        override suspend fun disconnect()=Unit
        fun sample(now:ObservationTime,motion:MotionState) {
            fun <T:Any> read(value:T):Reading<T> = Reading.Observed(value,DataMetadata(DataSource.SIMULATED,now.epochMillis,
                Freshness.FRESH,CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,now.monotonicMillis))
            frames.value=VehicleFrame(sequence=frames.value.sequence+1,connection=ConnectionState(ConnectionStatus.CONNECTED),
                motion=read(motion),restrictions=read(DrivingRestrictions(UIMode.PARKED,0,false)))
        }
    }
    @Test fun viewModelConnectsPetPlanActualTripCompletionPrivacyDeletionAndBackground()=runTest(dispatcher) {
        var elapsed=0L;var id=0
        val clock=ContextClock {ObservationTime(1_000_000+elapsed,100_000+elapsed)}
        val root=temporary.newFolder();val source=Source()
        val trips=TripCoordinator(encryptedTestRepository(File(root,"trip"),RuntimeMode.DEMO),RuntimeMode.DEMO,clock,
            TripIdSource {"vm-pet-trip"},TripConfig(10,20))
        val owner=object:ViewModelStoreOwner {override val viewModelStore=ViewModelStore()}
        val vm=ViewModelProvider(owner,object:ViewModelProvider.Factory {
            override fun <T:ViewModel> create(modelClass:Class<T>):T=requireNotNull(modelClass.cast(RoadmateViewModel {scope->
                val repository=VehicleContextStore(source,RuntimeMode.DEMO,clock,scope)
                RuntimeBinding(repository,clock=clock,trips=trips,careEngine=DeterministicCareEngine(clock),
                    petTravel=PetTravelCoordinator(FilePetTravelRepository(File(root,"pet"),RuntimeMode.DEMO,testEncryption(RuntimeMode.DEMO)),
                        RuntimeMode.DEMO,clock,{repository.context.value},{trips.uiState.value},{"vm-pet-${++id}"}))
            }))
        })[RoadmateViewModel::class.java]
        suspend fun awaitPet(predicate:(PetTravelUiState)->Boolean)=withContext(Dispatchers.Default) {
            withTimeout(15_000) {vm.petTravelUiState.first(predicate)}
        }
        try {
            withContext(Dispatchers.Default) {withTimeout(15_000) {trips.uiState.first {it.storage.loaded}}}
            vm.foreground().join();source.sample(clock.now(),MotionState.STATIONARY);awaitPet {it.contentAllowed}
            trips.consent(ConsentScope.TRIP_HISTORY,true)
            vm.petAction {assertTrue(it.saveProfile("VM pet",PetKind.DOG,true));assertTrue(it.beginOuting())}.join()
            for((time,state) in listOf(0L to TripState.START_CANDIDATE,10L to TripState.IN_PROGRESS,
                11L to TripState.STOP_CANDIDATE,31L to TripState.COMPLETED)) {
                elapsed=time;source.sample(clock.now(),if(time<11) MotionState.MOVING else MotionState.STATIONARY)
                withContext(Dispatchers.Default) {withTimeout(15_000) {trips.uiState.first {it.runtime.state==state}}}
            }
            awaitPet {it.completedTrip!=null}
            vm.petAction {assertTrue(it.finishOuting("fixed owner note","fixed next memo"))}.join()
            assertEquals(1,vm.petTravelUiState.value.cards.size)
            assertEquals("vm-pet-trip",vm.petTravelUiState.value.cards.single().first.tripId)
            val progress=trips.repository.state.value.progression
            trips.delete("vm-pet-trip");awaitPet {it.cards.isEmpty() && it.data.memos.isEmpty()}
            assertEquals(progress,trips.repository.state.value.progression)
            vm.background().join();awaitPet {!it.contentAllowed}
            assertNull(vm.petTravelUiState.value.data.profile)
        } finally {clearAndAwaitViewModel(owner.viewModelStore,vm)}
    }
}
