package io.booup.roadmate.demo

import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.*
import io.booup.roadmate.progress.*
import io.booup.roadmate.storage.FileTripRepository
import io.booup.roadmate.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ProgressDemoRuntimeTest(private val id:ProgressDemoId) {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun scenarioUsesCommittedDomainFactsAndRealProgressStore()=runTest {
        var elapsed=0L; var serial=0
        val clock=ContextClock { ObservationTime(1_000_000+elapsed,100_000+elapsed) }
        val adapter=DemoVehicleAdapter(clock); val context=VehicleContextStore(adapter,RuntimeMode.DEMO,clock,backgroundScope)
        val dir=temporary.newFolder(); val repository=FileTripRepository(dir,RuntimeMode.DEMO)
        val trips=TripCoordinator(repository,RuntimeMode.DEMO,clock,TripIdSource { "s-trip-${++serial}" },TripConfig(3000,5000))
        val post=PostDriveCoordinator(trips,repository,RuntimeMode.DEMO,clock,{context.context.value},RegionEvidenceProvider { ProgressDemoScenarios.region(id,it) })
        val progress=ProgressCoordinator(repository,RuntimeMode.DEMO,clock,{post.controlsAllowed()},catalog=ProgressDemoScenarios.catalog)
        val controller=ProgressDemoController(adapter,trips,repository,repository,post,progress,clock,backgroundScope)
        controller.select(id).join(); context.foreground(); runCurrent()
        val care=DeterministicCareEngine(clock)
        for(second in 0L..10L) {
            elapsed=second*1000; adapter.sampleCare(); runCurrent()
            trips.update(context.context.value,care.evaluate(context.context.value)); post.refresh(); runCurrent()
        }
        assertNotNull(controller.lastAction); controller.lastAction!!.join()
        if(id in setOf(ProgressDemoId.S14_RESTRICTED_UI,ProgressDemoId.S16_SEASON_TRANSITION)) {
            elapsed=11_000; adapter.sampleCare(); runCurrent(); trips.update(context.context.value,care.evaluate(context.context.value)); post.refresh(); progress.refresh()
        }
        val d=repository.state.value.progression; val ui=progress.uiState.value
        when(id) {
            ProgressDemoId.S1_FIRST_TRIP -> { assertEquals(1,d.ledger.size); assertEquals(20L,d.ledger.single().awardedXp) }
            ProgressDemoId.S2_DUPLICATE_TRIP -> { assertEquals(1,d.ledger.size); assertEquals(20L,ui.active!!.progress.totalXp) }
            ProgressDemoId.S3_DAILY_CAP -> { assertEquals(60L,d.ledger.sumOf { it.awardedXp }); assertTrue(d.ledger.any { it.reason==XpReason.DAILY_CAP && it.awardedXp==0L }) }
            ProgressDemoId.S4_FIRST_POSTCARD -> { assertTrue(d.ledger.any { it.sourceType==ProgressEventType.POSTCARD_ACQUIRED }); assertTrue(ui.active!!.missions.any { it.mission.missionId=="first-postcard" && it.completed }) }
            ProgressDemoId.S5_REFLECTION -> { assertTrue(d.ledger.any { it.sourceType==ProgressEventType.REFLECTION_COMPLETED && it.awardedXp==0L }); assertEquals(30L,ui.active!!.progress.totalXp) }
            ProgressDemoId.S6_LEVEL_UP -> assertEquals(3,ui.active!!.progress.currentLevel)
            ProgressDemoId.S7_FREE_CLAIM,ProgressDemoId.S8_DUPLICATE_CLAIM -> assertEquals(1,d.claims.size)
            ProgressDemoId.S9_PREMIUM_LOCKED -> { assertTrue(d.claims.isEmpty()); assertFalse(ui.active!!.progress.premiumEntitled) }
            ProgressDemoId.S10_SIMULATED_PREMIUM -> { assertTrue(ui.active!!.progress.premiumEntitled); assertEquals(1,d.claims.size) }
            ProgressDemoId.S11_FIRST_REGION,ProgressDemoId.S12_REPEAT_REGION -> { assertEquals(1,ui.exploration.discoveredCount); assertEquals(1,repository.state.value.visits.size) }
            ProgressDemoId.S13_SAFE_MISSION -> assertTrue(ui.active!!.missions.any { it.completed && it.visible && it.mission.visibility==MissionVisibility.HIDDEN_UNTIL_COMPLETED })
            ProgressDemoId.S14_RESTRICTED_UI -> { assertFalse(ui.contentAllowed); assertFalse(progress.claim(d.seasons.first().seasonId,d.seasons.first().rewards.first().rewardId)) }
            ProgressDemoId.S15_DELETED_TRIP -> { assertTrue(repository.state.value.trips.isEmpty()); assertEquals(20L,d.ledger.sumOf { it.awardedXp }); assertTrue(d.ledger.all { it.sourceDeleted && it.sourceId==null && it.sourceTripId==null }) }
            ProgressDemoId.S16_SEASON_TRANSITION -> { assertEquals(0L,ui.active!!.progress.totalXp); assertEquals(20L,ui.archived.single().progress.totalXp); assertEquals(1,ui.archived.single().progress.claimedRewards.size) }
        }
        assertTrue(d.ledger.all { it.source==DataSource.SIMULATED && it.awardedXp>=0 })
        val reopened=FileTripRepository(dir,RuntimeMode.DEMO); assertEquals(StorageResult.Success(Unit),reopened.load()); assertEquals(d,reopened.state.value.progression)
        context.background(); runCurrent(); post.refresh(); progress.refresh(); assertFalse(progress.uiState.value.contentAllowed); context.close()
    }
    companion object { @JvmStatic @Parameterized.Parameters(name="{0}") fun scenarios()=ProgressDemoId.entries.map { arrayOf(it) } }
}
