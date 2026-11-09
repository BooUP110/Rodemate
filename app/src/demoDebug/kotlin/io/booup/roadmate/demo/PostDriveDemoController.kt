package io.booup.roadmate.demo

import io.booup.roadmate.contracts.CollectionRepository
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.trip.TripCoordinator
import kotlinx.coroutines.*

/** Explicit Demo setup and scripted UI operations; never registered in Production. */
class PostDriveDemoController(private val adapter: DemoVehicleAdapter, private val trips: TripCoordinator,
    private val post: PostDriveCoordinator, private val collection: CollectionRepository, private val scope: CoroutineScope) {
    private var handled = true
    var lastAction: Job? = null
        private set
    init { scope.launch { trips.uiState.collect { state ->
        val id = adapter.postDriveSelection.value
        val trip = state.runtime.completed
        if (!handled && id != null && trip != null && state.storage.trips.any { it.tripId == trip.tripId }) {
            handled = true
            lastAction = scope.launch { run(id, trip.tripId) }
        }
    } } }
    fun select(id: PostDriveDemoId) = scope.launch {
        handled = true; lastAction?.cancelAndJoin(); trips.initialize(); trips.clearHistory(); collection.clearCollection()
        trips.consent(ConsentScope.TRIP_HISTORY, true)
        trips.consent(ConsentScope.EMOTIONAL_MEMORY, id in setOf(PostDriveDemoId.P5_5_CONFIRMED_MEMORY,
            PostDriveDemoId.P5_12_IDEMPOTENCY, PostDriveDemoId.P5_13_DELETE))
        handled = false; adapter.selectPostDrive(id)
    }
    private suspend fun run(id: PostDriveDemoId, tripId: String) {
        post.refresh(); post.open(tripId)
        when (id) {
            PostDriveDemoId.P5_3_SKIP -> { post.skip(); post.complete() }
            PostDriveDemoId.P5_4_LOCAL_REFLECTION, PostDriveDemoId.P5_5_CONFIRMED_MEMORY,
            PostDriveDemoId.P5_6_NO_CONSENT -> {
                post.requestReflection(); post.reflect(ReflectionMood.GOOD, "사용자가 선택한 시연 회고", true)
                if (id != PostDriveDemoId.P5_4_LOCAL_REFLECTION) post.remember(true)
            }
            PostDriveDemoId.P5_8_REPEAT_REGION -> {
                post.skip(); post.complete(); trips.manual(true); trips.manual(false)
                post.open(requireNotNull(trips.uiState.value.runtime.completed).tripId)
            }
            PostDriveDemoId.P5_11_REVIEWED -> { post.skip(); post.complete(); post.open(tripId) }
            PostDriveDemoId.P5_12_IDEMPOTENCY -> {
                repeat(2) { post.open(tripId); post.reflect(ReflectionMood.GOOD, "중복 없는 시연 회고", true); post.remember(true) }
            }
            PostDriveDemoId.P5_13_DELETE -> {
                post.reflect(ReflectionMood.NEUTRAL, "삭제할 시연 회고", true); post.remember(true); post.delete(tripId)
            }
            else -> Unit
        }
    }
}
