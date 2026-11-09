package io.booup.roadmate.demo

import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.*
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PostDriveDemoRuntimeTest(private val id: PostDriveDemoId) {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun scenarioUsesActualContextTripSessionAndDurableStore() = runTest {
        var elapsed=0L; var serial=0
        val clock=ContextClock { ObservationTime(1_000_000+elapsed,100_000+elapsed) }
        val adapter=DemoVehicleAdapter(clock); val store=VehicleContextStore(adapter,RuntimeMode.DEMO,clock,backgroundScope)
        val dir=temporary.newFolder(); val repository=FileTripRepository(dir,RuntimeMode.DEMO)
        val trips=TripCoordinator(repository,RuntimeMode.DEMO,clock,TripIdSource { "p5-${++serial}" },TripConfig(3000,5000))
        val post=PostDriveCoordinator(trips,repository,RuntimeMode.DEMO,clock,{store.context.value},PostDriveDemoScenarios.provider(adapter))
        val controller=PostDriveDemoController(adapter,trips,post,repository,backgroundScope)
        controller.select(id).join(); store.foreground(); runCurrent()
        val care=DeterministicCareEngine(clock)
        for (second in 0L..10L) {
            elapsed=second*1000; adapter.sampleCare(); runCurrent()
            trips.update(store.context.value,care.evaluate(store.context.value)); post.refresh(); runCurrent()
        }
        assertNotNull("Script must reach a completed trip",controller.lastAction); controller.lastAction!!.join()
        if(id==PostDriveDemoId.P5_10_RESTRICTION) {
            elapsed=11_000; adapter.sampleCare(); runCurrent(); trips.update(store.context.value,care.evaluate(store.context.value)); post.refresh()
        }
        val s=repository.state.value; val session=post.uiState.value.session
        when(id) {
            PostDriveDemoId.P5_1_BASIC_CARD -> { val card=requireNotNull(session.card); assertNull(card.reflection); assertEquals(DataSource.SIMULATED,card.source) }
            PostDriveDemoId.P5_2_PARTIAL -> { val c=session.card!!; assertNull(c.distanceMeters); assertNull(c.startLabel); assertNull(c.endLabel); assertEquals(TripDataQuality.PARTIAL,c.quality) }
            PostDriveDemoId.P5_3_SKIP -> { assertNotNull(session.card); assertEquals(PostDriveStage.COMPLETED,session.stage); assertEquals(1,s.postcards.size); assertTrue(s.memories.isEmpty()) }
            PostDriveDemoId.P5_4_LOCAL_REFLECTION -> { assertNotNull(s.postDrive.values.single().reflection); assertTrue(s.memories.isEmpty()) }
            PostDriveDemoId.P5_5_CONFIRMED_MEMORY -> assertEquals(1,s.memories.size)
            PostDriveDemoId.P5_6_NO_CONSENT -> { assertTrue(s.memories.isEmpty()); assertEquals(StorageError.CONSENT_REQUIRED,post.uiState.value.error) }
            PostDriveDemoId.P5_7_FIRST_REGION -> { assertEquals(1,s.postcards.size); assertEquals(1,s.visits.size); assertTrue(session.reveal) }
            PostDriveDemoId.P5_8_REPEAT_REGION -> { assertEquals(2,s.trips.size); assertEquals(1,s.postcards.size); assertFalse(session.reveal) }
            PostDriveDemoId.P5_9_UNKNOWN_REGION -> { assertTrue(s.postcards.isEmpty()); assertNotNull(session.card) }
            PostDriveDemoId.P5_10_RESTRICTION -> { assertFalse(post.uiState.value.contentAllowed); assertNull(session.card); assertFalse(post.reflect(ReflectionMood.GOOD,"blocked",true)) }
            PostDriveDemoId.P5_11_REVIEWED -> { assertTrue(s.trips.single().reviewed); assertFalse(session.reveal); assertEquals(PostDriveStage.COMPLETED,session.stage) }
            PostDriveDemoId.P5_12_IDEMPOTENCY -> { assertEquals(1,s.postcards.size); assertEquals(1,s.visits.size); assertEquals(1,s.postDrive.size); assertEquals(1,s.memories.size); assertFalse(session.reveal) }
            PostDriveDemoId.P5_13_DELETE -> { assertTrue(s.trips.isEmpty()); assertTrue(s.postDrive.isEmpty()); assertTrue(s.memories.isEmpty()); assertNull(s.postcards.single().sourceTripId); assertTrue(s.postcards.single().sourceTripDeleted) }
            PostDriveDemoId.P5_14_REGION_ISOLATION -> {
                assertEquals(RegionEvidenceSource.DEMO,s.postcards.single().source)
                val prod=FileTripRepository(File(temporary.newFolder(),"production"),RuntimeMode.PRODUCTION)
                assertEquals(StorageResult.Success(Unit),prod.load()); assertTrue(prod.state.value.postcards.isEmpty())
                val t=s.trips.single(); assertEquals(PostcardEligibility.UNAVAILABLE_REGION,
                    PostcardPolicy().evaluate(t.copy(source=DataSource.DERIVED),PostDriveState.READY,
                        PostDriveDemoScenarios.region(id,t),emptyList(),RuntimeMode.PRODUCTION))
            }
        }
        val reopened=FileTripRepository(dir,RuntimeMode.DEMO); assertEquals(StorageResult.Success(Unit),reopened.load())
        assertEquals(s,reopened.state.value)
        store.background(); runCurrent(); post.refresh(); assertFalse(post.uiState.value.contentAllowed); store.close()
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun scenarios()=PostDriveDemoId.entries.map { arrayOf(it) }
    }
}
