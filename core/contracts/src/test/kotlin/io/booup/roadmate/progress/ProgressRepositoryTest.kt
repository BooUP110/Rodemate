package io.booup.roadmate.progress

import io.booup.roadmate.model.*
import io.booup.roadmate.storage.*
import io.booup.roadmate.trip.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.DataInputStream

class ProgressRepositoryTest {
    @get:Rule val temporary=TemporaryFolder()
    private suspend fun setup(dir:File=temporary.newFolder(),s:Season=season()):FileTripRepository=FileTripRepository(dir,RuntimeMode.DEMO).also {
        success(it.load()); success(it.setConsent(ConsentScope.TRIP_HISTORY,true,1,DataSource.SIMULATED)); success(it.save(record(),0)); success(it.installSeasons(listOf(s)))
    }
    private suspend fun run(r:FileTripRepository,time:Long=1000)=r.reconcileProgress(now(time),r.state.value.captureGeneration,r.state.value.progression.generation)
    @Test fun actualLedgerReopensAndRepeatedReconcileDoesNotAddXp()=runBlocking {
        val dir=temporary.newFolder(); val r=setup(dir); success(run(r)); success(run(r))
        assertEquals(20L,r.state.value.progression.ledger.sumOf { it.awardedXp }); assertEquals(1,r.state.value.progression.ledger.size)
        val reopened=FileTripRepository(dir,RuntimeMode.DEMO); success(reopened.load()); assertEquals(r.state.value,reopened.state.value)
        DataInputStream(File(dir,"trips.v1.bin").inputStream()).use { it.readUTF(); assertEquals(3,it.readInt()) }
    }
    @Test fun freeClaimIsSeparateAndIdempotentInTransaction()=runBlocking {
        val r=setup(); success(run(r)); assertTrue(r.state.value.progression.claims.isEmpty())
        repeat(2) { success(r.claimReward("season","free",now(),0)) }
        assertEquals(1,r.state.value.progression.claims.size)
    }
    @Test fun insufficientLevelAndPremiumWithoutEntitlementAreBlocked()=runBlocking {
        val r=setup(); failure(StorageError.CONSENT_REQUIRED,r.claimReward("season","last",now(),0))
        failure(StorageError.CONSENT_REQUIRED,r.claimReward("season","premium",now(),0)); assertTrue(r.state.value.progression.claims.isEmpty())
    }
    @Test fun simulatedPremiumIsExplicitAndAlreadyClaimedOwnershipSurvivesToggleOff()=runBlocking {
        val r=setup(); success(r.setSimulatedPremium("season",true)); success(r.claimReward("season","premium",now(),0))
        success(r.setSimulatedPremium("season",false)); assertEquals(1,r.state.value.progression.claims.size)
    }
    @Test fun productionRejectsSimulatedPremiumAndSeasonResetEntryPoints()=runBlocking {
        val r=FileTripRepository(temporary.newFolder(),RuntimeMode.PRODUCTION); success(r.load()); success(r.installSeasons(listOf(season())))
        failure(StorageError.WRONG_ENVIRONMENT,r.setSimulatedPremium("season",true))
        failure(StorageError.WRONG_ENVIRONMENT,r.resetSimulatedSeasons(listOf(season()))); assertTrue(r.state.value.progression.premiumSeasons.isEmpty())
    }
    @Test fun historyDeletePreservesXpAndClaimsButRemovesPlainSourceIdentifiers()=runBlocking {
        val dir=temporary.newFolder(); val r=setup(dir); success(run(r)); success(r.claimReward("season","free",now(),0)); success(r.delete("trip"))
        val d=r.state.value.progression; assertEquals(20L,d.ledger.sumOf { it.awardedXp }); assertEquals(1,d.claims.size)
        assertNull(d.ledger.single().sourceId); assertNull(d.ledger.single().sourceTripId); assertTrue(d.ledger.single().sourceDeleted)
        val reopened=FileTripRepository(dir,RuntimeMode.DEMO); success(reopened.load()); assertEquals(d,reopened.state.value.progression)
        success(run(reopened)); assertEquals(d,reopened.state.value.progression)
    }
    @Test fun clearHistoryAndConsentRevocationAlsoDetachLedgerWithoutXpPenalty()=runBlocking {
        for(revoke in listOf(false,true)) {
            val r=setup(); success(run(r))
            if(revoke) success(r.setConsent(ConsentScope.TRIP_HISTORY,false,2000,DataSource.SIMULATED)) else success(r.clearHistory())
            assertEquals(20L,r.state.value.progression.ledger.sumOf { it.awardedXp }); assertTrue(r.state.value.progression.ledger.all { it.sourceDeleted })
        }
    }
    @Test fun clearProgressIsIndependentAndCannotReawardOldTripFacts()=runBlocking {
        val r=setup(); success(run(r)); success(r.clearProgress(now(2000))); assertEquals(1,r.state.value.trips.size)
        success(run(r,3000)); assertTrue(r.state.value.progression.ledger.isEmpty()); assertTrue(r.state.value.progression.claims.isEmpty())
        failure(StorageError.NOT_FOUND,r.claimReward("season","free",now(3000),0))
    }
    @Test fun staleCaptureOrProgressGenerationCannotMutateLedger()=runBlocking {
        val r=setup(); success(r.clearHistory()); failure(StorageError.NOT_FOUND,r.reconcileProgress(now(),0,0))
        success(r.clearProgress(now())); failure(StorageError.NOT_FOUND,r.reconcileProgress(now(),r.state.value.captureGeneration,0))
    }
    @Test fun duplicateLedgerIdsAndOrphanSourceAreRejectedByStoreValidation()=runBlocking {
        val r=setup(); success(run(r)); val s=r.state.value; val l=s.progression.ledger.single()
        assertThrows(StoreException::class.java) { ProgressStoreCodec(RuntimeMode.DEMO).validate(s.copy(progression=s.progression.copy(ledger=listOf(l,l)))) }
        assertThrows(StoreException::class.java) { ProgressStoreCodec(RuntimeMode.DEMO).validate(s.copy(progression=s.progression.copy(ledger=listOf(l.copy(sourceTripId="missing"))))) }
        Unit
    }
    @Test fun corruptedOrWrongModeSchemaThreeFailsClosedWithOriginalBytesPreserved()=runBlocking {
        val dir=temporary.newFolder(); val r=setup(dir); success(run(r))
        failure(StorageError.WRONG_ENVIRONMENT,FileTripRepository(dir,RuntimeMode.PRODUCTION).load())
        val f=File(dir,"trips.v1.bin"); val broken=f.readBytes().dropLast(3).toByteArray(); f.writeBytes(broken)
        failure(StorageError.INVALID_DATA,FileTripRepository(dir,RuntimeMode.DEMO).load()); assertArrayEquals(broken,f.readBytes())
    }
    @Test fun schemaTwoMigrationWriteFailurePreservesGoldenBytes()=runBlocking {
        val dir=temporary.newFolder(); val bytes=javaClass.getResourceAsStream("/schema2-golden.bin")!!.use { it.readBytes() }
        val f=File(dir,"trips.v1.bin"); f.writeBytes(bytes); File(dir,"trips.pending").mkdir()
        failure(StorageError.IO,FileTripRepository(dir,RuntimeMode.DEMO).load()); assertArrayEquals(bytes,f.readBytes())
    }
    @Test fun invalidSeasonUpdateOverlapAndTimezoneSwitchAreRejected()=runBlocking {
        val r=setup(); failure(StorageError.INVALID_DATA,r.installSeasons(listOf(season().copy(title="changed"))))
        failure(StorageError.INVALID_DATA,r.installSeasons(listOf(season("overlap"))))
        failure(StorageError.INVALID_DATA,r.installSeasons(listOf(season("next",start=Long.MAX_VALUE-100,end=Long.MAX_VALUE,config=ProgressConfig(zoneId="Asia/Seoul")))))
    }
    @Test fun expiredSeasonCannotGainXpOrAutoClaimButPriorClaimIsPreserved()=runBlocking {
        val r=setup(s=season(end=2000)); success(run(r)); success(r.claimReward("season","free",now(),0))
        success(run(r,2000)); failure(StorageError.CONSENT_REQUIRED,r.claimReward("season","last",now(2000),0))
        assertEquals(1,r.state.value.progression.claims.size); assertEquals(20L,r.state.value.progression.ledger.sumOf { it.awardedXp })
    }
    @Test fun seasonTransitionStartsNewTotalAtZeroAndKeepsArchivedOwnership()=runBlocking {
        val r=setup(s=season(end=2000)); success(r.installSeasons(listOf(season("next",start=2000,end=4000))))
        success(run(r)); success(r.claimReward("season","free",now(),0)); success(run(r,2000))
        val d=r.state.value.progression; val engine=SafeProgressEngine()
        assertEquals(20L,engine.resolve(d.seasons.first(),d,now(2000)).progress.totalXp)
        assertEquals(0L,engine.resolve(d.seasons.last(),d,now(2000)).progress.totalXp); assertEquals(1,d.claims.size)
    }
    @Test fun skipAndAnsweredReviewHaveEqualBasicXpAndMissionCompletion()=runBlocking {
        val skipped=setup(); success(skipped.skipReflection("trip",0)); success(skipped.completeReview("trip",0)); success(run(skipped))
        val answered=setup(); success(answered.reflect(TripReflection("trip",ReflectionMood.BAD,"user text",300,DataSource.SIMULATED,ReflectionPersistence.TRIP_LOCAL),0))
        success(answered.completeReview("trip",0)); success(run(answered))
        val engine=SafeProgressEngine()
        val a=engine.resolve(season(),skipped.state.value.progression,now()); val b=engine.resolve(season(),answered.state.value.progression,now())
        assertEquals(a.progress.totalXp,b.progress.totalXp); assertEquals(a.missions,b.missions)
    }
    @Test fun sameRepositoryConcurrentUpdatesSerializeWithoutLostAwardsOrClaims()=runBlocking {
        val r=setup(); coroutineScope { repeat(5) { launch { success(run(r)) } } }
        assertEquals(1,r.state.value.progression.ledger.size)
        coroutineScope { repeat(5) { launch { success(r.claimReward("season","free",now(),0)) } } }
        assertEquals(1,r.state.value.progression.claims.size)
    }
    @Test fun progressUiGateRejectsClaimAfterRestrictionAndDoesNotAffectLedger()=runBlocking {
        val r=setup(); var allowed=true
        val p=ProgressCoordinator(r,RuntimeMode.DEMO,ContextClock { now() },{allowed}); p.refresh(); assertTrue(p.uiState.value.contentAllowed)
        allowed=false; assertFalse(p.claim("season","free")); assertFalse(p.uiState.value.contentAllowed)
        assertTrue(r.state.value.progression.claims.isEmpty()); assertEquals(20L,r.state.value.progression.ledger.sumOf { it.awardedXp })
    }
    @Test fun lowerCapInNewSeasonDoesNotResetSameDayEarningsOrRejectZeroAward()=runBlocking {
        val r=setup(s=season(end=2000)); success(run(r))
        success(r.installSeasons(listOf(season("next",start=2000,end=4000,config=ProgressConfig(dailyCap=10)))))
        success(r.save(record("new-trip").copy(startedAtEpochMillis=2100,endedAtEpochMillis=2200,createdAtEpochMillis=2200,careEvents=emptyList()),0))
        success(run(r,2500)); assertEquals(20L,r.state.value.progression.ledger.sumOf { it.awardedXp })
        assertEquals(0L,r.state.value.progression.ledger.single { it.seasonId=="next" }.awardedXp)
    }
}
