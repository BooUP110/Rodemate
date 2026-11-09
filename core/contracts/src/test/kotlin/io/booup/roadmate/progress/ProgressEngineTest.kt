package io.booup.roadmate.progress

import io.booup.roadmate.model.*
import io.booup.roadmate.trip.record
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

internal fun season(id:String="season",start:Long=0,end:Long=Long.MAX_VALUE,config:ProgressConfig=ProgressConfig())=Season(id,"Test season",start,end,
    listOf(0L,10,20,30,40),listOf(SeasonReward("free","Original badge",1,RewardTrack.FREE,CosmeticRewardType.BADGE),
        SeasonReward("last","Original theme",5,RewardTrack.FREE,CosmeticRewardType.THEME),
        SeasonReward("premium","Original premium theme",1,RewardTrack.PREMIUM,CosmeticRewardType.COSMETIC)),
    listOf(Mission("review","Review including skip",ProgressEventType.POST_DRIVE_SESSION_COMPLETED,1),
        Mission("postcard","First postcard",ProgressEventType.POSTCARD_ACQUIRED,1,MissionVisibility.HIDDEN_UNTIL_COMPLETED)),config)
internal fun event(id:String="trip",type:ProgressEventType=ProgressEventType.TRIP_COMPLETED,at:Long=200)=
    ProgressEvent(progressKey("${type.name}:$id"),type,id,id,at,DataSource.SIMULATED,TripDataQuality.PARTIAL)
internal fun now(epoch:Long=1000)=ObservationTime(epoch,epoch)
internal fun ProgressData.add(engine:SafeProgressEngine,s:Season,e:ProgressEvent,at:ObservationTime=now()):ProgressData =
    engine.award(this,s,e,at,RuntimeMode.DEMO)?.let { copy(ledger=ledger+it) } ?: this

class ProgressEngineTest {
    private val engine=SafeProgressEngine()
    @Test fun validCompletedTripGetsFixedXpOnce() {
        val s=season(); val d=ProgressData(seasons=listOf(s)).add(engine,s,event())
        assertEquals(20L,d.ledger.single().awardedXp); assertEquals(d,d.add(engine,s,event()))
    }
    @Test fun identicalInputsProduceIdenticalResults() {
        val s=season(); assertEquals(engine.award(ProgressData(),s,event(),now(),RuntimeMode.DEMO),engine.award(ProgressData(),s,event(),now(),RuntimeMode.DEMO))
    }
    @Test fun warningAndCareTypesAreIneligibleEvenWhenForgedAsProgress() {
        val forbidden=ProgressEventType.entries.toSet()-ProgressEligibilityPolicy.safeEvents
        assertEquals(4,forbidden.size)
        forbidden.forEach { assertNull(engine.award(ProgressData(),season(),event(type=it),now(),RuntimeMode.DEMO)) }
    }
    @Test fun productionRejectsSimulatedAndUnknownEventSources() {
        listOf(DataSource.SIMULATED,DataSource.UNKNOWN).forEach { source -> assertNull(engine.award(ProgressData(),season(),event().copy(source=source),now(),RuntimeMode.PRODUCTION)) }
    }
    @Test fun productionAcceptsVerifiedNormalizedDomainSource() {
        assertEquals(20L,engine.award(ProgressData(),season(),event().copy(source=DataSource.DERIVED),now(),RuntimeMode.PRODUCTION)!!.awardedXp)
    }
    @Test fun unconfirmedMinimalAndInvalidTripQualityCannotFarmXp() {
        listOf(TripDataQuality.MINIMAL,TripDataQuality.INVALID).forEach { assertNull(engine.award(ProgressData(),season(),event().copy(quality=it),now(),RuntimeMode.DEMO)) }
    }
    @Test fun factoryNeverReadsWarningsOrMakesCareProgress() {
        val t=record(); val events=ProgressEventFactory().events(TripStorageState(trips=listOf(t)))
        assertEquals(listOf(ProgressEventType.TRIP_COMPLETED),events.map { it.type })
        assertTrue(t.careEvents.isNotEmpty())
    }
    @Test fun distanceAndDrivingDurationDoNotChangeTripXp() {
        val short=record("short").copy(distanceMeters=1,durationMillis=1,confirmedDrivingMillis=1)
        val long=record("long").copy(distanceMeters=1_000_000,durationMillis=1_000_000,confirmedDrivingMillis=1_000_000)
        val xp=listOf(short,long).map { t -> engine.award(ProgressData(),season(),ProgressEventFactory().events(TripStorageState(trips=listOf(t))).single(),now(),RuntimeMode.DEMO)!!.awardedXp }
        assertEquals(listOf(20L,20L),xp)
    }
    @Test fun dailyCapLimitsRepeatTripsWithoutNegativeOrFailureXp() {
        val s=season(config=ProgressConfig(dailyCap=45)); var d=ProgressData()
        repeat(4) { d=d.add(engine,s,event("trip-$it")) }
        assertEquals(listOf(20L,20,5,0),d.ledger.map { it.awardedXp }); assertEquals(45L,d.ledger.sumOf { it.awardedXp })
        assertEquals(XpReason.DAILY_CAP,d.ledger.last().reason)
    }
    @Test fun tripCapBoundsMultipleEventsButDoesNotDropTheirMissionFacts() {
        val s=season(config=ProgressConfig(tripCap=22)); var d=ProgressData()
        d=d.add(engine,s,event()); d=d.add(engine,s,event(type=ProgressEventType.POSTCARD_ACQUIRED)); d=d.add(engine,s,event(type=ProgressEventType.POST_DRIVE_SESSION_COMPLETED))
        assertEquals(22L,d.ledger.sumOf { it.awardedXp }); assertEquals(3,d.ledger.size)
        assertTrue(engine.resolve(s,d,now()).missions.single { it.mission.missionId=="review" }.completed)
    }
    @Test fun reflectionItselfGetsZeroAndCannotBeRequiredForBasicMission() {
        val s=season(); val a=ProgressData().add(engine,s,event()).add(engine,s,event(type=ProgressEventType.POST_DRIVE_SESSION_COMPLETED))
        val b=a.add(engine,s,event(type=ProgressEventType.REFLECTION_COMPLETED))
        assertEquals(a.ledger.sumOf { it.awardedXp },b.ledger.sumOf { it.awardedXp })
        assertEquals(engine.resolve(s,a,now()).missions,engine.resolve(s,b,now()).missions)
    }
    @Test fun zeroCapsRecordZeroXpWithoutNegativeValues() {
        val s=season(config=ProgressConfig(dailyCap=0,tripCap=0)); val d=ProgressData().add(engine,s,event())
        assertEquals(0L,d.ledger.single().awardedXp)
    }
    @Test fun midnightResetsDailyCapInExplicitTimezone() {
        val before=Instant.parse("2026-11-07T23:59:59Z").toEpochMilli(); val after=before+1000
        val s=season(config=ProgressConfig(dailyCap=20)); var d=ProgressData()
        d=d.add(engine,s,event("first",at=before),now(before)); d=d.add(engine,s,event("second",at=before),now(before))
        d=d.add(engine,s,event("next-day",at=after),now(after))
        assertEquals(listOf(20L,0,20),d.ledger.map { it.awardedXp }); assertEquals("2026-11-08",d.ledger.last().dayKey)
    }
    @Test fun seasonTimezoneChangesBoundaryWithoutUsingSystemDefault() {
        val at=Instant.parse("2026-11-07T15:00:00Z").toEpochMilli()
        val s=season(config=ProgressConfig(zoneId="Asia/Seoul"))
        assertEquals("2026-11-08",engine.award(ProgressData(),s,event(at=at),now(at),RuntimeMode.DEMO)!!.dayKey)
    }
    @Test fun expiredFutureAndOutOfWindowEventsCannotAwardOrAdvanceMissions() {
        val s=season(start=100,end=1000)
        assertNull(engine.award(ProgressData(),s,event(),now(1000),RuntimeMode.DEMO))
        assertNull(engine.award(ProgressData(),s,event(at=99),now(),RuntimeMode.DEMO))
        assertNull(engine.award(ProgressData(),s,event(at=1001),now(500),RuntimeMode.DEMO))
    }
    @Test fun clockRollbackCannotResetCap() {
        val s=season(); val d=ProgressData().add(engine,s,event(),now(2000))
        assertNull(engine.award(d,s,event("rollback"),now(1000),RuntimeMode.DEMO))
    }
    @Test fun clearCutoffRejectsOldFactsRatherThanReawardingAfterReset() {
        assertNull(engine.award(ProgressData(resetAtEpochMillis=500),season(),event(),now(),RuntimeMode.DEMO))
    }
    @Test fun levelBelowExactAboveAndMaxThresholdAreDerivedFromLedger() {
        for((xp,expected) in listOf(9L to 1,10L to 2,11L to 2,40L to 5,100L to 5)) {
            val s=season(); val l=engine.award(ProgressData(),s,event(),now(),RuntimeMode.DEMO)!!.copy(awardedXp=xp)
            assertEquals(expected,engine.resolve(s,ProgressData(ledger=listOf(l)),now()).progress.currentLevel)
        }
    }
    @Test fun rewardIsAvailableButNotAutomaticallyClaimedOrApplied() {
        val s=season(); val v=engine.resolve(s,ProgressData(),now())
        assertEquals(RewardClaimState.AVAILABLE,v.rewards.first().state); assertTrue(v.progress.claimedRewards.isEmpty())
        assertEquals(RewardClaimState.LOCKED,v.rewards.single { it.reward.rewardId=="premium" }.state)
    }
    @Test fun expiredUnclaimedAndPreviouslyClaimedAreDistinct() {
        val s=season(end=1000); val d=ProgressData(claims=listOf(RewardClaim(s.seasonId,"free",500,DataSource.SIMULATED)))
        assertEquals(RewardClaimState.CLAIMED,engine.resolve(s,d,now()).rewards.first().state)
        assertEquals(RewardClaimState.UNCLAIMED_EXPIRED,engine.resolve(s,ProgressData(),now()).rewards.first().state)
    }
    @Test fun hiddenCollectionMissionRevealsOnlyAfterValidatedLedgerFact() {
        val s=season(); assertFalse(engine.resolve(s,ProgressData(),now()).missions.last().visible)
        val d=ProgressData().add(engine,s,event(type=ProgressEventType.POSTCARD_ACQUIRED))
        assertTrue(engine.resolve(s,d,now()).missions.last().visible)
    }
    @Test fun unsafeMissionEventTypesAndNegativeXpConfigurationAreRejected() {
        val s=season()
        (ProgressEventType.entries.toSet()-ProgressEligibilityPolicy.missionEvents).forEach { type ->
            assertThrows(IllegalArgumentException::class.java) { engine.validate(s.copy(missions=listOf(Mission("bad","bad",type,1)))) }
        }
        assertThrows(IllegalArgumentException::class.java) { engine.validate(s.copy(config=ProgressConfig(tripXp=-1))) }
        assertThrows(IllegalArgumentException::class.java) { engine.validate(s.copy(levels=listOf(0,10,10))) }
    }
    @Test fun explorationCountsOnlyVerifiedStoredSourcesAndNeverInventsDenominator() {
        val v=RegionVisitRecord(RegionId("region"),100,"trip",false,RegionEvidenceSource.DEMO)
        val r=ExplorationResolver(); val e=r.resolve(listOf(v,v),RuntimeMode.DEMO)
        assertEquals(1,e.discoveredCount); assertNull(e.percentage); assertNull(e.totalKnownRegionCount)
        assertEquals(0,r.resolve(listOf(v),RuntimeMode.PRODUCTION).discoveredCount)
    }
    @Test fun catalogMustContainEveryVisitedIdBeforeShowingPercentage() {
        val v=RegionVisitRecord(RegionId("region"),100,null,true,RegionEvidenceSource.DEMO)
        val r=ExplorationResolver(); assertNull(r.resolve(listOf(v),RuntimeMode.DEMO,RegionCatalog(setOf(RegionId("other")),RegionEvidenceSource.DEMO)).percentage)
        assertEquals(50.0,r.resolve(listOf(v),RuntimeMode.DEMO,RegionCatalog(setOf(v.regionId,RegionId("other")),RegionEvidenceSource.DEMO)).percentage!!,0.0)
    }
}
