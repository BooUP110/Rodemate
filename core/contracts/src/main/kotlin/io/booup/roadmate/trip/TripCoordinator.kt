package io.booup.roadmate.trip

import io.booup.roadmate.contracts.TripRepository
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Repository boundary only: UI never sees a file/codec/DAO. Failed writes remain visible failures. */
class TripCoordinator(
    val repository: TripRepository, private val mode: RuntimeMode, private val clock: ContextClock,
    ids: TripIdSource, config: TripConfig = TripConfig(),
    private val persistence: TripPersistenceConfig = TripPersistenceConfig(),
) {
    private val tracker = TripTracker(mode, ids, config)
    private val mapper = TripEvidenceMapper()
    private val mutex = Mutex()
    private var generation = -1L
    private var context: VehicleContext? = null
    private var lastCheckpoint: Long? = null
    private var deleted = false
    private var error: StorageError? = null
    private val mutableUi = MutableStateFlow(TripUiState())
    val uiState = mutableUi.asStateFlow()

    /** Re-evaluate freshness at event consumption, including a queued Demo UI click. */
    fun userControlsAllowed(): Boolean = context?.let {
        DrivingPresentationPolicy().mode(it, clock.now()) == DrivingPresentationMode.FULL_PARKED
    } == true

    suspend fun initialize() = mutex.withLock {
        capture(repository.load())
        synchronizeGeneration()
        publish()
    }
    suspend fun update(context: VehicleContext, decision: CareDecision) = mutex.withLock {
        this.context = context
        if (!repository.state.value.loaded || repository.state.value.error != null) {
            publish(); return@withLock
        }
        synchronizeGeneration()
        tracker.update(mapper.map(context, clock.now()), decision)
        if (tracker.status.state == TripState.START_CANDIDATE || tracker.status.active != null) deleted = false
        persist(force = !context.foreground)
        publish()
    }
    suspend fun consent(scope: ConsentScope, granted: Boolean) = mutex.withLock {
        capture(repository.setConsent(scope, granted, clock.now().epochMillis,
            if (mode == RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.USER_INPUT))
        synchronizeGeneration(); publish()
    }
    suspend fun delete(tripId: String) = mutex.withLock {
        capture(repository.delete(tripId)); deleted = error == null
        synchronizeGeneration(); publish()
    }
    suspend fun clearHistory() = mutex.withLock {
        capture(repository.clearHistory()); deleted = error == null
        synchronizeGeneration(); publish()
    }
    suspend fun saveMemory(memory: PersistentEmotionalMemory): StorageResult<Unit> = mutex.withLock {
        val result = repository.saveMemory(memory); capture(result); publish(); result
    }
    suspend fun manual(start: Boolean): Boolean = mutex.withLock {
        if (mode != RuntimeMode.DEMO) return@withLock false
        synchronizeGeneration()
        val accepted = if (start) tracker.manualStart(clock.now()) else tracker.manualStop(clock.now())
        if (accepted) { deleted = false; persist(force = true); publish() }
        accepted
    }
    private fun synchronizeGeneration() {
        val current = repository.state.value
        if (current.loaded && generation != current.captureGeneration) {
            tracker.reset()
            current.active?.let { tracker.restore(it) }
                ?: current.trips.lastOrNull()?.let { tracker.restoreCompleted(it) }
            generation = current.captureGeneration
            lastCheckpoint = null
        }
    }
    private suspend fun persist(force: Boolean = false) {
        val storage = repository.state.value
        if (storage.consents[ConsentScope.TRIP_HISTORY]?.granted != true) return // Session-only tracking; no implicit grant.
        tracker.status.completed?.let { trip ->
            if (storage.trips.none { it.tripId == trip.tripId }) capture(repository.save(trip, generation))
            return
        }
        tracker.status.active?.let { active ->
            val elapsed = lastCheckpoint?.let { clock.now().monotonicMillis - it }
            if (force || elapsed == null || elapsed < 0 || elapsed >= persistence.checkpointIntervalMillis ||
                storage.active?.state != active.state || storage.active?.careEvents != active.careEvents) {
                capture(repository.checkpoint(active, generation))
                if (error == null) lastCheckpoint = clock.now().monotonicMillis
            }
        }
    }
    private fun capture(result: StorageResult<Unit>) { error = (result as? StorageResult.Failure)?.error }
    private fun publish() {
        val runtime = tracker.status
        val state = repository.state.value
        val trip = runtime.completed ?: state.trips.lastOrNull()
        val postDrive = context?.let {
            PostDriveEligibility().evaluate(trip, runtime.state, it, clock.now(), deleted)
        } ?: if (deleted) PostDriveState.DELETED else PostDriveState.NOT_READY
        mutableUi.value = TripUiState(runtime, state, postDrive, error ?: state.error)
    }
}
