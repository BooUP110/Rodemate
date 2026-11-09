package io.booup.roadmate.demo

import io.booup.roadmate.contracts.CollectionRepository
import io.booup.roadmate.contracts.TripRepository
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.progress.ProgressCoordinator
import io.booup.roadmate.trip.TripCoordinator
import kotlinx.coroutines.*

/** Explicit simulation facts only, no raw XP/level editor. Demo source set only. */
class ProgressDemoController(private val adapter:DemoVehicleAdapter,private val trips:TripCoordinator,
    private val tripStore:TripRepository,private val collection:CollectionRepository,private val post:PostDriveCoordinator,
    private val progress:ProgressCoordinator,private val clock:ContextClock,private val scope:CoroutineScope) {
    private var handled=true
    var lastAction:Job?=null; private set
    init { scope.launch { trips.uiState.collect { state ->
        val id=adapter.progressSelection.value; val trip=state.runtime.completed
        if(!handled && id!=null && trip!=null && state.storage.trips.any { it.tripId==trip.tripId }) {
            handled=true; lastAction=scope.launch { run(id,trip) }
        }
    } } }
    fun select(id:ProgressDemoId)=scope.launch {
        handled=true; lastAction?.cancelAndJoin(); trips.initialize(); trips.clearHistory(); collection.clearCollection()
        progress.repository.resetSimulatedSeasons(ProgressDemoScenarios.seasons(id,clock.now()))
        trips.consent(ConsentScope.TRIP_HISTORY,true); handled=false; adapter.selectProgress(id)
    }
    private suspend fun run(id:ProgressDemoId,trip:TripRecord) {
        post.refresh(); post.open(trip.tripId); progress.refresh()
        val season=progress.repository.state.value.progression.seasons.first()
        val free=season.rewards.first { it.track==RewardTrack.FREE }
        val premium=season.rewards.first { it.track==RewardTrack.PREMIUM }
        when(id) {
            ProgressDemoId.S2_DUPLICATE_TRIP -> repeat(2) { progress.refresh() }
            ProgressDemoId.S3_DAILY_CAP -> { repeat(3) { index -> extraTrip(trip,"demo-cap-trip-$index") }; progress.refresh() }
            ProgressDemoId.S5_REFLECTION -> { post.requestReflection(); post.reflect(ReflectionMood.GOOD,"시연 정리",true); post.complete(); progress.refresh() }
            ProgressDemoId.S7_FREE_CLAIM,ProgressDemoId.S16_SEASON_TRANSITION -> progress.claim(season.seasonId,free.rewardId)
            ProgressDemoId.S8_DUPLICATE_CLAIM -> repeat(2) { progress.claim(season.seasonId,free.rewardId) }
            ProgressDemoId.S9_PREMIUM_LOCKED -> progress.claim(season.seasonId,premium.rewardId)
            ProgressDemoId.S10_SIMULATED_PREMIUM -> { progress.repository.setSimulatedPremium(season.seasonId,true); progress.refresh(); progress.claim(season.seasonId,premium.rewardId) }
            ProgressDemoId.S12_REPEAT_REGION -> { extraTrip(trip,"demo-repeat-region-trip"); trips.initialize(); post.open("demo-repeat-region-trip"); progress.refresh() }
            ProgressDemoId.S15_DELETED_TRIP -> { trips.delete(trip.tripId); progress.refresh() }
            else -> Unit
        }
    }
    private suspend fun extraTrip(original:TripRecord,id:String) {
        // Explicit duplicate SIMULATED completed-fact fixture to exercise caps; never presented as a real extra drive.
        tripStore.save(original.copy(tripId=id,careEvents=original.careEvents.map { it.copy(tripId=id) }),tripStore.state.value.captureGeneration)
    }
}
