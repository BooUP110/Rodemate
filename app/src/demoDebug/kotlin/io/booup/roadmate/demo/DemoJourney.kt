package io.booup.roadmate.demo

import io.booup.roadmate.CompanionPreview
import io.booup.roadmate.JOURNEY_SEASON_ID
import io.booup.roadmate.JourneyPlace
import io.booup.roadmate.journeyPassRewards
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

    suspend fun visit(place: JourneyPlace, post: PostDriveCoordinator, repeatVisit: Boolean = false): Boolean {
        if (!post.controlsAllowed()) return false
        val state = trips.uiState.value
        if (!repeatVisit && state.storage.visits.any { it.regionId == place.id }) return true
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

internal fun journeySeason(preview: CompanionPreview?): Season = LocalSeasonCatalog.first.copy(
    seasonId = JOURNEY_SEASON_ID, title = preview?.seasonTitle ?: "로드메이트 탐험 시즌",
    levels = listOf(0L) + journeyPassRewards.map { it.xp },
    config = ProgressConfig(tripXp = 5, postcardXp = 10, postDriveXp = 5),
    rewards = journeyPassRewards.mapIndexed { index, reward ->
        val level = index + 2 // The first season level starts at zero XP.
        val track = if (reward.premium) RewardTrack.PREMIUM else RewardTrack.FREE
        when (reward.xp) {
            5L -> SeasonReward("journey-frame", "엽서 테두리", level, track, CosmeticRewardType.THEME)
            15L -> SeasonReward("journey-sunset", "노을 테마 미리보기", level, track, CosmeticRewardType.THEME)
            20L -> SeasonReward("journey-stamp", "여행의 시작 배지", level, track, CosmeticRewardType.BADGE)
            25L -> SeasonReward("journey-animated", "움직이는 여행 엽서", level, track, CosmeticRewardType.COSMETIC)
            40L -> SeasonReward("journey-first-discovery", "첫 발견 기념 배지", level, track, CosmeticRewardType.BADGE, "first-postcard")
            55L -> SeasonReward("journey-animated-frame", "별빛 엽서 액자", level, track, CosmeticRewardType.THEME)
            60L -> SeasonReward("journey-companion", "완주 동반자 팩", level, track, CosmeticRewardType.COMPANION_ITEM,
                pack = CompanionPack("journey-companion", preview?.profile?.id ?: CompanionProfiles.somsom.id, "travel", "idle-wave"))
            else -> {
                val type = when (reward.xp) {
                    30L -> CosmeticRewardType.THEME
                    35L, 50L -> CosmeticRewardType.BADGE
                    else -> CosmeticRewardType.COSMETIC
                }
                SeasonReward("journey-reward-${reward.xp}", requireNotNull(reward.title), level, track, type)
            }
        }
    },
)
