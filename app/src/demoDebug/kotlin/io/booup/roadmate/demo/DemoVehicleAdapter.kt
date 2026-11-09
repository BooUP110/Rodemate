package io.booup.roadmate.demo

import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.*

class DemoVehicleAdapter(private val clock: ContextClock) : VehicleAdapter {
    override val source = DataSource.SIMULATED
    private val mutableFrames = MutableStateFlow(VehicleFrame())
    private val mutableSelection = MutableStateFlow<DemoScenarioId?>(null)
    override val frames = mutableFrames.asStateFlow()
    val selection = mutableSelection.asStateFlow()
    private val mutableCareSelection = MutableStateFlow<CareDemoScenarioId?>(null)
    val careSelection = mutableCareSelection.asStateFlow()
    private var samplingJob: Job? = null
    private val mutableInteractionSelection = MutableStateFlow<InteractionDemoScenarioId?>(null)
    val interactionSelection = mutableInteractionSelection.asStateFlow()
    private var interactionStartedAt = 0L
    private val mutableTripSelection = MutableStateFlow<TripDemoScenarioId?>(null)
    val tripSelection = mutableTripSelection.asStateFlow()
    private var tripStartedAt = 0L
    private val mutablePostDriveSelection = MutableStateFlow<PostDriveDemoId?>(null)
    val postDriveSelection = mutablePostDriveSelection.asStateFlow()
    private var postDriveStartedAt = 0L
    private val mutableProgressSelection=MutableStateFlow<ProgressDemoId?>(null)
    val progressSelection=mutableProgressSelection.asStateFlow()
    private var progressStartedAt=0L

    /** A selection is an explicit NEW demo observation, including when replaying the same case. */
    fun select(id: DemoScenarioId) {
        mutableProgressSelection.value=null
        mutablePostDriveSelection.value = null
        mutableTripSelection.value = null
        val scenario = DemoScenarios.all.single { it.id == id }
        mutableCareSelection.value = null
        mutableInteractionSelection.value = null
        mutableSelection.value = id
        mutableFrames.value = DemoScenarios.frame(scenario, clock.now(), mutableFrames.value.sequence + 1)
    }

    fun selectCare(id: CareDemoScenarioId) {
        mutableProgressSelection.value=null
        mutablePostDriveSelection.value = null
        mutableTripSelection.value = null
        mutableInteractionSelection.value = null
        mutableSelection.value = null
        mutableCareSelection.value = id
        sampleCare()
    }

    fun selectInteraction(id: InteractionDemoScenarioId) {
        mutableProgressSelection.value=null
        mutablePostDriveSelection.value = null
        mutableTripSelection.value = null
        mutableSelection.value = null
        mutableCareSelection.value = null
        mutableInteractionSelection.value = id
        interactionStartedAt = clock.now().monotonicMillis
        sampleCare()
    }

    fun selectTrip(id: TripDemoScenarioId) {
        mutableProgressSelection.value=null
        mutablePostDriveSelection.value = null
        mutableSelection.value = null; mutableCareSelection.value = null; mutableInteractionSelection.value = null
        mutableTripSelection.value = id; tripStartedAt = clock.now().monotonicMillis
        sampleCare()
    }

    fun selectPostDrive(id: PostDriveDemoId) {
        mutableProgressSelection.value=null
        mutableSelection.value = null; mutableCareSelection.value = null; mutableInteractionSelection.value = null; mutableTripSelection.value = null
        mutablePostDriveSelection.value = id; postDriveStartedAt = clock.now().monotonicMillis; sampleCare()
    }
    fun selectProgress(id:ProgressDemoId) {
        mutableSelection.value=null; mutableCareSelection.value=null; mutableInteractionSelection.value=null; mutableTripSelection.value=null; mutablePostDriveSelection.value=null
        mutableProgressSelection.value=id; progressStartedAt=clock.now().monotonicMillis; sampleCare()
    }
    /** A real new SIMULATED sample, not a re-stamp of received vehicle data. */
    fun sampleCare() {
        mutableProgressSelection.value?.let { id ->
            val now=clock.now(); mutableFrames.value=ProgressDemoScenarios.frame(id,(now.monotonicMillis-progressStartedAt).coerceAtLeast(0),now,mutableFrames.value.sequence+1); return
        }
        mutablePostDriveSelection.value?.let { id ->
            val now = clock.now()
            mutableFrames.value = PostDriveDemoScenarios.frame(id, (now.monotonicMillis - postDriveStartedAt).coerceAtLeast(0), now, mutableFrames.value.sequence + 1)
            return
        }
        mutableTripSelection.value?.let { id ->
            val now = clock.now()
            mutableFrames.value = TripDemoScenarios.frame(id, (now.monotonicMillis - tripStartedAt).coerceAtLeast(0), now, mutableFrames.value.sequence + 1)
            return
        }
        mutableInteractionSelection.value?.let { id ->
            val now = clock.now()
            mutableFrames.value = InteractionDemoScenarios.frame(id,
                (now.monotonicMillis - interactionStartedAt).coerceAtLeast(0), now, mutableFrames.value.sequence + 1)
            return
        }
        mutableCareSelection.value?.let { id ->
            mutableFrames.value = CareDemoScenarios.frame(id, clock.now(), mutableFrames.value.sequence + 1)
        }
    }

    override suspend fun connect(): AdapterResult<Unit> {
        // Child of the Store's foreground session: never samples after background/close.
        samplingJob?.cancelAndJoin()
        samplingJob = CoroutineScope(currentCoroutineContext()).launch {
            while (isActive) { delay(1_000); sampleCare() }
        }
        if (mutableSelection.value == DemoScenarioId.F_DISCONNECTED ||
            mutableCareSelection.value == CareDemoScenarioId.C9_DISCONNECTED) {
            mutableFrames.value = mutableFrames.value.invalidate(UnavailableReason.DISCONNECTED).copy(
                sequence = mutableFrames.value.sequence + 1, connection = ConnectionState(ConnectionStatus.DISCONNECTED))
            return AdapterResult.Unavailable(UnavailableReason.DISCONNECTED)
        }
        mutableFrames.value = mutableFrames.value.copy(
            sequence = mutableFrames.value.sequence + 1,
            connection = ConnectionState(ConnectionStatus.CONNECTING),
        )
        yield()
        // Connection restoration never re-stamps/recreates observations.
        mutableFrames.value = mutableFrames.value.copy(
            sequence = mutableFrames.value.sequence + 1,
            connection = ConnectionState(ConnectionStatus.CONNECTED),
        )
        return AdapterResult.Available(Unit) // Simulation transport only, never a Pleos connection.
    }

    override suspend fun disconnect() {
        samplingJob?.cancelAndJoin()
        samplingJob = null
        mutableFrames.value = mutableFrames.value.invalidate(UnavailableReason.DISCONNECTED).copy(
            sequence = mutableFrames.value.sequence + 1,
            connection = ConnectionState(ConnectionStatus.DISCONNECTED),
        )
    }
}
