package io.booup.roadmate.demo

import io.booup.roadmate.CompanionPreview
import io.booup.roadmate.JourneyPlace
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.progress.LocalSeasonCatalog
import io.booup.roadmate.trip.TripCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal class DemoJourney(private val adapter: DemoVehicleAdapter, private val trips: TripCoordinator) {
    private var destination: Pair<String, JourneyPlace>? = null

    fun evidence(trip: TripRecord): RegionEvidence? {
        val place = destination?.takeIf { it.first == trip.tripId }?.second ?: return null
        val end = trip.endedAtEpochMillis ?: return null
        return RegionEvidence(trip.tripId, place.id, place.name, RegionEvidenceSource.DEMO, end, true)
    }

    suspend fun visit(place: JourneyPlace, post: PostDriveCoordinator): Boolean {
        if (!post.controlsAllowed()) return false
        val state = trips.uiState.value
        if (state.storage.visits.any { it.regionId == place.id }) return true
        if (state.storage.consents[ConsentScope.TRIP_HISTORY]?.granted != true && !post.consent(ConsentScope.TRIP_HISTORY, true)) return false
        val previous = state.runtime.completed?.tripId
        adapter.startJourneyTrip()
        try {
            val trip = withTimeoutOrNull(22_000) {
                trips.uiState.first { snapshot ->
                    val completed = snapshot.runtime.completed
                    completed != null && completed.tripId != previous && snapshot.storage.trips.any { it.tripId == completed.tripId }
                }.runtime.completed
            } ?: return false
            destination = trip.tripId to place
            return post.open(trip.tripId) && trips.repository.state.value.visits.any { it.regionId == place.id }
        } finally {
            destination = null
            adapter.finishJourneyTrip()
        }
    }
}

internal fun journeySeason(preview: CompanionPreview): Season = LocalSeasonCatalog.first.copy(
    seasonId = "roadmate-collab-journey-1", title = preview.seasonTitle,
    levels = listOf(0L, 15L, 30L, 45L, 60L),
    config = ProgressConfig(tripXp = 5, postcardXp = 10, postDriveXp = 5),
    rewards = listOf(
        SeasonReward("journey-stamp", "여행의 시작 배지", 1, RewardTrack.FREE, CosmeticRewardType.BADGE),
        SeasonReward("journey-frame", "엽서 테두리", 2, RewardTrack.PREMIUM, CosmeticRewardType.THEME),
        SeasonReward("journey-first-discovery", "첫 발견 기념 배지", 2, RewardTrack.FREE, CosmeticRewardType.BADGE, "first-postcard"),
        SeasonReward("journey-palette", "파스텔 여행 테마", 3, RewardTrack.FREE, CosmeticRewardType.THEME),
        SeasonReward("journey-sunset", "노을 테마 미리보기", 4, RewardTrack.PREMIUM, CosmeticRewardType.THEME),
        SeasonReward("journey-companion", "${preview.profile.displayName} 동반자 팩", 5, RewardTrack.FREE,
            CosmeticRewardType.COMPANION_ITEM, pack = CompanionPack("journey-companion", preview.profile.id, "travel", "idle-wave")),
        SeasonReward("journey-animated", "움직이는 여행 엽서", 5, RewardTrack.PREMIUM, CosmeticRewardType.COSMETIC),
    ),
)
