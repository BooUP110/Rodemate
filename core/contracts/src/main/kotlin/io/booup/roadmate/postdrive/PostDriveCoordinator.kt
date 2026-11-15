package io.booup.roadmate.postdrive

import io.booup.roadmate.contracts.CollectionRepository
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import io.booup.roadmate.trip.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface RegionEvidenceProvider { fun evidence(trip: TripRecord): RegionEvidence? }

/** Uses trip eligibility; never decides when trips start/end. Every action rechecks current policy. */
class PostDriveCoordinator(private val trips: TripCoordinator, private val collection: CollectionRepository,
    private val mode: RuntimeMode, private val clock: ContextClock, private val context: () -> VehicleContext,
    private val regions: RegionEvidenceProvider = RegionEvidenceProvider { null },
    private val projector: MemoryCardProjector = MemoryCardProjector()) {
    private val mutex = Mutex()
    private var session = PostDriveSession()
    private var selected: String? = null
    private var selectedGeneration = -1L
    private var draft: TripReflection? = null
    private var error: StorageError? = null
    private val mutableUi = MutableStateFlow(PostDriveUiState())
    val uiState = mutableUi.asStateFlow()
    private val store get() = collection.state.value
    fun controlsAllowed(): Boolean {
        val c = context()
        val motion = TripEvidenceMapper().map(c, clock.now()).motion
        return c.mode == mode && motion in setOf(MotionState.PARKED, MotionState.STATIONARY) &&
            trips.uiState.value.runtime.state in setOf(TripState.IDLE, TripState.COMPLETED) &&
            DrivingPresentationPolicy().mode(c, clock.now()) == DrivingPresentationMode.FULL_PARKED && store.loaded && store.error == null
    }
    private fun target(id: String?) = store.trips.singleOrNull { it.tripId == id }
        ?: trips.uiState.value.runtime.completed?.takeIf { it.tripId == id && selectedGeneration == store.captureGeneration }
    private fun eligibility(t: TripRecord) = PostDriveEligibility().evaluate(t, trips.uiState.value.runtime.state, context(), clock.now())
    private fun eligible(t: TripRecord) = controlsAllowed() && eligibility(t) in setOf(PostDriveState.READY, PostDriveState.ALREADY_REVIEWED)
    suspend fun refresh() = mutex.withLock { publish() }
    suspend fun open(tripId: String): Boolean = mutex.withLock {
        selectedGeneration = store.captureGeneration
        val t = target(tripId)
        if (t == null || !eligible(t)) { publish(); return@withLock false }
        selected = tripId; draft = null; error = null
        val region = regions.evidence(t)
        val card = projector.project(t, store.postDrive[tripId]?.reflection) ?: return@withLock false
        val postcardEligibility = PostcardPolicy().evaluate(t, eligibility(t), region, store.visits, mode)
        var reveal = false
        if (!t.reviewed && store.trips.any { it.tripId == tripId }) {
            when (val result = collection.present(tripId, region, card.calendar, selectedGeneration)) {
                is StorageResult.Success -> reveal = result.value
                is StorageResult.Failure -> error = result.error
            }
        }
        session = PostDriveSession(tripId, if (t.reviewed) PostDriveStage.COMPLETED else PostDriveStage.CARD_PRESENTED,
            card, store.postcards.singleOrNull { it.sourceTripId == tripId }
                ?: store.postcards.singleOrNull { it.key.regionId == region?.regionId }, reveal, postcardEligibility, error)
        publish(); true
    }
    suspend fun requestReflection(expectedTripId: String? = selected): Boolean = mutex.withLock {
        val t = target(selected)
        if (t == null || t.reviewed || expectedTripId != selected || !eligible(t) || session.card == null) { publish(); return@withLock false }
        session = session.copy(stage = PostDriveStage.REFLECTION_PENDING); publish(); true
    }
    suspend fun reflect(mood: ReflectionMood?, text: String?, saveLocally: Boolean, expectedTripId: String? = selected): Boolean = mutex.withLock {
        val t = target(selected)
        if (t == null || t.reviewed || expectedTripId != selected || !eligible(t) || session.card == null || (mood == null && text.isNullOrBlank()) || (text?.length ?: 0) > 4096) {
            publish(); return@withLock false
        }
        val r = TripReflection(t.tripId, mood, text?.takeIf { it.isNotBlank() }, clock.now().epochMillis,
            if (mode == RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.USER_INPUT,
            if (saveLocally) ReflectionPersistence.TRIP_LOCAL else ReflectionPersistence.SESSION_ONLY)
        if (saveLocally && !capture(collection.reflect(r, selectedGeneration))) { publish(); return@withLock false }
        draft = r; session = session.copy(stage = PostDriveStage.REFLECTION_COMPLETED,
            card = projector.project(t, r), error = null); publish(); true
    }
    suspend fun skip(expectedTripId: String? = selected): Boolean = mutex.withLock {
        val t = target(selected)
        if (t == null || t.reviewed || expectedTripId != selected || !eligible(t) || session.card == null) { publish(); return@withLock false }
        if (store.trips.any { it.tripId == t.tripId } && !capture(collection.skipReflection(t.tripId, selectedGeneration))) { publish(); return@withLock false }
        draft = null; session = session.copy(stage = PostDriveStage.SKIPPED, card = projector.project(t)); publish(); true
    }
    suspend fun complete(expectedTripId: String? = selected): Boolean = mutex.withLock {
        val t = target(selected)
        if (t == null || expectedTripId != selected || !eligible(t) || session.card == null) { publish(); return@withLock false }
        if (store.trips.any { it.tripId == t.tripId } && !capture(collection.completeReview(t.tripId, selectedGeneration))) { publish(); return@withLock false }
        session = session.copy(stage = PostDriveStage.COMPLETED, reveal = false); publish(); true
    }
    suspend fun remember(confirmed: Boolean, expectedTripId: String? = selected, expectedReflection: TripReflection? = null): Boolean = mutex.withLock {
        val t = target(selected)
        if (t == null || expectedTripId != selected || !eligible(t) || session.card == null ||
            (expectedReflection != null && store.postDrive[t.tripId]?.reflection != expectedReflection)) { publish(); return@withLock false }
        // No implicit conversion of a SESSION_ONLY draft, and no retry queue on failure.
        if (draft?.persistence == ReflectionPersistence.SESSION_ONLY) { error = StorageError.CONSENT_REQUIRED; publish(); return@withLock false }
        val ok = capture(collection.rememberReflection(t.tripId, confirmed, selectedGeneration)); publish(); ok
    }
    suspend fun consent(scope: ConsentScope, granted: Boolean): Boolean = mutex.withLock {
        if (!controlsAllowed() || scope !in setOf(ConsentScope.TRIP_HISTORY, ConsentScope.EMOTIONAL_MEMORY)) { publish(); return@withLock false }
        trips.consent(scope, granted); error = trips.uiState.value.operationError; publish(); error == null
    }
    suspend fun delete(tripId: String): Boolean = mutex.withLock {
        if (!controlsAllowed()) { publish(); return@withLock false }
        trips.delete(tripId); error = trips.uiState.value.operationError; publish(); error == null
    }
    suspend fun clearHistory(confirmed: Boolean): Boolean = mutex.withLock {
        if (!confirmed || !controlsAllowed()) { publish(); return@withLock false }
        trips.clearHistory(); error = trips.uiState.value.operationError; publish(); error == null
    }
    private fun capture(result: StorageResult<Unit>): Boolean { error = (result as? StorageResult.Failure)?.error; return error == null }
    private fun publish() {
        val allowed = controlsAllowed()
        val t = target(selected)
        if (!allowed || (selected != null && (t == null || selectedGeneration != store.captureGeneration || !eligible(t)))) {
            session = PostDriveSession(); selected = null; draft = null
        } else if (t != null && session.card != null) {
            session = session.copy(card = projector.project(t, draft ?: store.postDrive[t.tripId]?.reflection),
                postcard = session.postcard?.let { old -> store.postcards.singleOrNull { it.key == old.key } }, error = error)
        } else if (session.card == null && allowed) {
            val ready = trips.uiState.value.runtime.completed?.let { eligibility(it) == PostDriveState.READY } == true
            session = PostDriveSession(stage = if (ready) PostDriveStage.READY else PostDriveStage.NOT_AVAILABLE)
        }
        mutableUi.value = PostDriveUiState(allowed, session, if (allowed) store.trips else emptyList(),
            if (allowed) store.postcards else emptyList(), store.consents[ConsentScope.TRIP_HISTORY]?.granted == true,
            store.consents[ConsentScope.EMOTIONAL_MEMORY]?.granted == true, error ?: store.error)
    }
}
