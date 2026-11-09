package io.booup.roadmate.demo

import io.booup.roadmate.model.*
import io.booup.roadmate.progress.LocalSeasonCatalog

enum class ProgressDemoId {
    S1_FIRST_TRIP, S2_DUPLICATE_TRIP, S3_DAILY_CAP, S4_FIRST_POSTCARD, S5_REFLECTION, S6_LEVEL_UP,
    S7_FREE_CLAIM, S8_DUPLICATE_CLAIM, S9_PREMIUM_LOCKED, S10_SIMULATED_PREMIUM,
    S11_FIRST_REGION, S12_REPEAT_REGION, S13_SAFE_MISSION, S14_RESTRICTED_UI, S15_DELETED_TRIP, S16_SEASON_TRANSITION,
}
object ProgressDemoScenarios {
    val catalog=RegionCatalog(setOf("demo_region_coast","demo_region_city","demo_region_mountain").map { RegionId(it) }.toSet(),RegionEvidenceSource.DEMO)
    fun seasons(id:ProgressDemoId,now:ObservationTime):List<Season> {
        val original=LocalSeasonCatalog.first
        val first=original.copy(seasonId="roadmate-demo-season-1",title="시뮬레이션 시즌 1",startsAtEpochMillis=(now.epochMillis-1000).coerceAtLeast(0),
            endsAtEpochMillis=now.epochMillis+if(id==ProgressDemoId.S16_SEASON_TRANSITION) 11_000 else 86_400_000,
            levels=listOf(0L,10,20,30,40),rewards=original.rewards.map { it.copy(rewardId="demo-${it.rewardId}") })
        return if(id==ProgressDemoId.S16_SEASON_TRANSITION) listOf(first,first.copy(seasonId="roadmate-demo-season-2",title="시뮬레이션 시즌 2",
            startsAtEpochMillis=first.endsAtEpochMillis,endsAtEpochMillis=first.endsAtEpochMillis+86_400_000)) else listOf(first)
    }
    fun region(id:ProgressDemoId?,trip:TripRecord):RegionEvidence?=if(id in setOf(ProgressDemoId.S4_FIRST_POSTCARD,ProgressDemoId.S11_FIRST_REGION,
        ProgressDemoId.S12_REPEAT_REGION,ProgressDemoId.S13_SAFE_MISSION)) RegionEvidence(trip.tripId,RegionId("demo_region_coast"),"시연 해안 지역",
            RegionEvidenceSource.DEMO,requireNotNull(trip.endedAtEpochMillis),true) else null
    fun frame(id:ProgressDemoId,elapsed:Long,now:ObservationTime,sequence:Long)=PostDriveDemoScenarios.frame(
        if(id==ProgressDemoId.S14_RESTRICTED_UI) PostDriveDemoId.P5_10_RESTRICTION else PostDriveDemoId.P5_1_BASIC_CARD,elapsed,now,sequence)
}
