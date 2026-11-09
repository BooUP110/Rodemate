package io.booup.roadmate

import androidx.lifecycle.*
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CarePresentationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Source : VehicleAdapter {
        override val source = DataSource.SIMULATED
        override val frames = MutableStateFlow(VehicleFrame())
        override suspend fun connect(): AdapterResult<Unit> = AdapterResult.Available(Unit)
        override suspend fun disconnect() { frames.value = frames.value.invalidate(UnavailableReason.DISCONNECTED).copy(
            sequence = frames.value.sequence + 1, connection = ConnectionState()) }
        fun sample(time: ObservationTime, light: Boolean = false) {
            fun <T : Any> value(value: T): Reading<T> = Reading.Observed(value, DataMetadata(DataSource.SIMULATED,
                time.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, time.monotonicMillis))
            frames.value = VehicleFrame(frames.value.sequence + 1, ConnectionState(ConnectionStatus.CONNECTED),
                VehicleSnapshot(value(if (light) LowBeamState.OFF else LowBeamState.ON), value(19.0), value(0L), value(emptyList()),
                    driverPresent = value(true), outsideTemperatureCelsius = value(3.0)),
                restrictions = value(DrivingRestrictions(UIMode.PARKED, 0, false)), motion = value(MotionState.MOVING),
                care = CareInputs(lighting = value(LightingCondition.NIGHT), preference = value(PreferredTemperature(23.0, true))))
        }
    }
    private fun TestScope.create(): Triple<RoadmateViewModel, Source, ViewModelStore> {
        val clock = ContextClock { ObservationTime(1_000_000 + testScheduler.currentTime, 100_000 + testScheduler.currentTime) }
        val source = Source()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(RoadmateViewModel { scope ->
                RuntimeBinding(VehicleContextStore(source, RuntimeMode.DEMO, clock, scope),
                    careEngine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0)), clock = clock)
            }))
        })[RoadmateViewModel::class.java]
        model.foreground(); runCurrent(); source.sample(clock.now()); runCurrent()
        return Triple(model, source, owner.viewModelStore)
    }
    private fun response(model: RoadmateViewModel): CareResponse {
        val p = requireNotNull(model.uiState.value.presentation)
        return CareResponse(p.eventId, p.revision, UserIntent.ACCEPT, CareAction.REQUEST_HVAC_CHANGE)
    }
    @Test fun actualStoreEngineAndResolverReachUiState() = runTest(dispatcher) {
        val (model, _, owner) = create()
        assertEquals(CarePriority.P2_SUGGESTION, model.uiState.value.presentation?.priority)
        assertTrue(model.uiState.value.presentation!!.body.contains("3℃"))
        assertTrue(model.uiState.value.presentation!!.body.contains("23℃"))
        assertEquals(model.careDecision.value.activeEvent, model.uiState.value.presentation?.sourceEvent)
        owner.clear()
    }
    @Test fun selectingCompanionCannotMutateCareDecisionOrInteractionRevision() = runTest(dispatcher) {
        val (model, _, owner) = create(); val before = model.careDecision.value; val p = model.uiState.value.presentation!!
        model.selectCompanion(CompanionProfiles.somsom.id).join()
        assertEquals(before, model.careDecision.value)
        assertEquals(p.revision, model.uiState.value.presentation?.revision)
        assertNotEquals(p.body, model.uiState.value.presentation?.body)
        owner.clear()
    }
    @Test fun acceptCreatesNotExecutedRequestAndNoVehicleWrite() = runTest(dispatcher) {
        val (model, source, owner) = create(); val request = response(model); val original = source.frames.value
        model.respond(request).join()
        assertEquals(original, source.frames.value)
        val record = model.interactionRecords.value.getValue(request.eventId)
        assertEquals(ExecutionStatus.NOT_EXECUTED, record.executionStatus)
        assertEquals("23.0", record.controlIntent?.parameters?.get("preferredCelsius"))
        owner.clear()
    }
    @Test fun acceptRechecksFreshnessBetweenStoreTicks() = runTest(dispatcher) {
        val (model, _, owner) = create(); val request = response(model)
        advanceTimeBy(5_001); runCurrent()
        // Store/UI tick at5000 still allowed this value. The action at5001 must not approve it.
        model.respond(request).join()
        assertTrue(model.interactionRecords.value.isEmpty())
        assertNull(model.uiState.value.presentation)
        owner.clear()
    }
    @Test fun backgroundCancelsPresentationAndRejectsOldInteraction() = runTest(dispatcher) {
        val (model, _, owner) = create(); val request = response(model)
        model.background().join(); model.respond(request).join()
        assertNull(model.uiState.value.presentation)
        assertTrue(model.interactionRecords.value.isEmpty())
        owner.clear()
    }
    @Test fun continuouslyFreshSamplesDoNotPreventInteractionTimeout() = runTest(dispatcher) {
        val (model, source, owner) = create(); val id = model.uiState.value.presentation!!.eventId
        repeat(30) {
            advanceTimeBy(1_000); runCurrent()
            source.sample(ObservationTime(1_000_000 + testScheduler.currentTime, 100_000 + testScheduler.currentTime)); runCurrent()
        }
        assertEquals(InteractionStage.EXPIRED, model.interactionRecords.value.getValue(id).stage)
        assertNull(model.uiState.value.presentation)
        owner.clear()
    }
    @Test fun p1ImmediatelyPreemptsAnExistingP2InViewModel() = runTest(dispatcher) {
        val (model, source, owner) = create(); val old = response(model)
        source.sample(ObservationTime(1_000_000, 100_000), light = true); runCurrent()
        assertEquals(CarePriority.P1_SAFETY, model.uiState.value.presentation?.priority)
        assertEquals(InteractionStage.SUSPENDED, model.interactionRecords.value.getValue(old.eventId).stage)
        model.respond(old).join()
        assertEquals(CarePriority.P1_SAFETY, model.uiState.value.presentation?.priority)
        assertTrue(model.interactionRecords.value.values.all { it.controlIntent == null })
        owner.clear()
    }
}
