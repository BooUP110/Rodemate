package io.booup.roadmate.local

import io.booup.roadmate.model.*
import io.booup.roadmate.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

internal val localNow=ObservationTime(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(),100_000)
internal fun localContext(now:ObservationTime=localNow):VehicleContext {
    val connection=ConnectionState(ConnectionStatus.CONNECTED)
    fun <T:Any> read(v:T,source:DataSource=DataSource.SYSTEM):EvaluatedReading<T> = Reading.Observed(v,DataMetadata(source,now.epochMillis,
        Freshness.FRESH,CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,now.monotonicMillis))
        .evaluate(now,ContextFreshnessPolicies().occupancy,connection,true)
    fun <T:Any> absent():EvaluatedReading<T> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED).evaluate(now,ContextFreshnessPolicies().external,connection,true)
    return VehicleContext(RuntimeMode.PRODUCTION,true,connection,absent(),absent(),absent(),absent(),absent(),absent(),read(25.0),
        read(DrivingRestrictions(UIMode.PARKED,0,false,now.epochMillis)),read(MotionState.STATIONARY),absent(),absent(),
        frontLeftTargetTemperatureCelsius=read(22.0,DataSource.VEHICLE_SDK))
}
internal fun consent(scope:ConsentScope)=ConsentRecord(scope,true,1,null,DataSource.USER_INPUT,"test")
internal fun recallStore():TripStorageState {
    val trip=TripRecord("trip",TripState.COMPLETED,localNow.epochMillis-60_000,localNow.epochMillis-1000,
        DataSource.DERIVED,59_000,50_000,completionReason=TripCompletionReason.CONFIRMED_STATIONARY,
        quality=TripDataQuality.PARTIAL,createdAtEpochMillis=localNow.epochMillis-1000)
    val reflection=TripReflection("trip",null,"FIXED_RECALL_FIXTURE",localNow.epochMillis-500,DataSource.USER_INPUT,ReflectionPersistence.TRIP_LOCAL)
    return TripStorageState(loaded=true,trips=listOf(trip),consents=listOf(ConsentScope.TRIP_HISTORY,ConsentScope.EMOTIONAL_MEMORY).associateWith(::consent),
        postDrive=mapOf("trip" to PostDriveMetadata("trip",reflection)),
        memories=listOf(PersistentEmotionalMemory("reflection:trip","trip",requireNotNull(reflection.text),reflection.createdAtEpochMillis,DataSource.USER_INPUT,true)))
}

class StationaryComfortTest {
    @get:Rule val temporary=TemporaryFolder()
    private val rule=StationaryComfortRule()
    private val pref=PreferredCabinTemperature(24.0,DataSource.USER_INPUT,localNow.epochMillis-1)
    @Test fun CF01_noPreferenceProducesNoAdvice() { assertNull(rule.evaluate(localContext(),null,localNow,true)) }
    @Test fun CF02_onlyExplicitUserPreferenceAndRequestAreAccepted() {
        assertNotNull(rule.evaluate(localContext(),pref,localNow,true))
        assertNull(rule.evaluate(localContext(),pref.copy(source=DataSource.DERIVED),localNow,true))
        assertNull(rule.evaluate(localContext(),pref,localNow,false))
    }
    @Test fun CF03_stationaryIsAllowedWithoutDriverPresence() {
        assertEquals(ComfortAdvice(25.0,22.0,24.0),rule.evaluate(localContext(),pref,localNow,true))
    }
    @Test fun CF04_movingIsBlocked() {
        val c=localContext(); assertNull(rule.evaluate(c.copy(motion=c.motion.copy(currentValue=MotionState.MOVING,
            observation=(c.motion.observation as Reading.Observed).copy(value=MotionState.MOVING))),pref,localNow,true))
    }
    @Test fun CF05_unknownMotionIsBlocked() { val c=localContext(); assertNull(rule.evaluate(c.copy(motion=c.motion.copy(currentValue=null)),pref,localNow,true)) }
    @Test fun CF06_restrictedUxIsBlocked() {
        val c=localContext(); val r=(c.restrictions.observation as Reading.Observed).copy(value=DrivingRestrictions(UIMode.RESTRICTED,80,true))
        assertNull(rule.evaluate(c.copy(restrictions=r.evaluate(localNow,ContextFreshnessPolicies().restrictions,c.connection,true)),pref,localNow,true))
    }
    @Test fun CF07_staleOutsideIsBlockedEvenWithCachedCurrentValue() {
        val c=localContext(); val outside=(c.outsideTemperatureCelsius.observation as Reading.Observed)
        val old=outside.copy(metadata=outside.metadata.copy(observedAtMonotonicMillis=1))
        assertNull(rule.evaluate(c.copy(outsideTemperatureCelsius=c.outsideTemperatureCelsius.copy(observation=old)),pref,localNow,true))
    }
    @Test fun CF08_driverPresenceAndLegacyPreferenceAreNotSynthesized() {
        val c=localContext(); rule.evaluate(c,pref,localNow,true)
        assertNull(c.driverPresent.currentValue); assertTrue(c.driverPresent.observation is Reading.Unavailable)
        assertNull(c.care.preference.currentValue)
    }
    @Test fun CF09_adviceDoesNotChangeVehicleOrProgressAndContainsNoControlAction()=runBlocking {
        val c=localContext(); val before=c.copy()
        val repo=encryptedTestRepository(temporary.newFolder(),RuntimeMode.PRODUCTION); repo.load()
        val progress=repo.state.value.progression
        val coordinator=ComfortCoordinator(repo,RuntimeMode.PRODUCTION,ContextClock { localNow },{c})
        coordinator.save(24.0); assertTrue(coordinator.request())
        assertEquals(before,c); assertEquals(progress,repo.state.value.progression)
        val care=io.booup.roadmate.care.DeterministicCareEngine(ContextClock { localNow }).evaluate(c)
        assertNull(care.activeEvent); assertTrue(care.emittedEvents.isEmpty())
    }
    @Test fun CF10_preferenceSaveModifyDeleteAndEncryptedReopen()=runBlocking {
        val dir=temporary.newFolder(); val repo=encryptedTestRepository(dir,RuntimeMode.PRODUCTION); repo.load()
        val clock=ContextClock { localNow }; val coordinator=ComfortCoordinator(repo,RuntimeMode.PRODUCTION,clock,{localContext()})
        assertTrue(coordinator.save(24.0)); assertTrue(coordinator.request()); assertNotNull(coordinator.uiState.value.advice)
        assertTrue(coordinator.save(23.0)); assertEquals(23.0,repo.state.value.preferredCabinTemperature!!.valueCelsius,0.0)
        assertTrue(coordinator.delete()); assertNull(repo.state.value.preferredCabinTemperature)
        val reopened=encryptedTestRepository(dir,RuntimeMode.PRODUCTION); reopened.load()
        assertNull(reopened.state.value.preferredCabinTemperature); assertFalse(reopened.state.value.consents[ConsentScope.FUNCTIONAL_PREFERENCE]!!.granted)
    }
    @Test fun backgroundDisconnectAndStaleTargetBlockAndInvalidatePendingRequest()=runBlocking {
        val repo=encryptedTestRepository(temporary.newFolder(),RuntimeMode.PRODUCTION); repo.load(); var c=localContext()
        val coordinator=ComfortCoordinator(repo,RuntimeMode.PRODUCTION,ContextClock { localNow },{c})
        coordinator.save(24.0); coordinator.request(); assertNotNull(coordinator.uiState.value.advice)
        c=c.copy(foreground=false); coordinator.refresh(); assertFalse(coordinator.uiState.value.contentAllowed)
        c=localContext(); coordinator.refresh(); assertNull(coordinator.uiState.value.advice)
        assertNull(rule.evaluate(c.copy(connection=ConnectionState(ConnectionStatus.DISCONNECTED)),pref,localNow,true))
    }
}

class MemoryRecallTest {
    @get:Rule val temporary=TemporaryFolder()
    private val projector=MemoryRecallProjector()
    @Test fun MR01_noHistoryConsentReturnsNoSummaryOrMemory() {
        assertNull(projector.project(recallStore().copy(consents=emptyMap()),localContext(),localNow).summary)
    }
    @Test fun MR02_noHistoryProducesHonestZeroSummary() {
        val s=recallStore().copy(trips=emptyList(),memories=emptyList(),postDrive=emptyMap())
        val result=projector.project(s,localContext(),localNow)
        val summary=requireNotNull(result.summary)
        assertEquals(0,summary.driveCount); assertEquals(0L,summary.recordedDurationMillis); assertNull(result.recent)
    }
    @Test fun MR03_recentActualMemoryPreviewDoesNotAutomaticallyExposeText() {
        val s=recallStore(); val result=projector.project(s,localContext(),localNow)
        assertEquals("reflection:trip",result.recent!!.memoryId); assertNull(result.openedText)
        assertEquals(s.memories.single().text,projector.project(s,localContext(),localNow,"reflection:trip").openedText)
    }
    @Test fun MR04_monthlySummaryIsDeterministicAndCountsOnlyTheSelectedUtcMonth() {
        val s=recallStore(); val old=s.trips.single().copy(tripId="old",startedAtEpochMillis=Instant.parse("2026-10-31T23:59:00Z").toEpochMilli())
        val result=projector.project(s.copy(trips=s.trips+old),localContext(),localNow)
        val summary=requireNotNull(result.summary)
        assertEquals(1,summary.driveCount); assertEquals(59_000L,summary.recordedDurationMillis); assertEquals(1,summary.reflectionCount)
        assertEquals(result,projector.project(s.copy(trips=s.trips+old),localContext(),localNow))
    }
    @Test fun MR05_deletedTripIsExcludedEvenWhenCallerSuppliesStaleMemoryId() {
        val s=recallStore().copy(trips=emptyList()); val result=projector.project(s,localContext(),localNow,"reflection:trip")
        assertEquals(0,result.summary!!.driveCount); assertNull(result.recent); assertNull(result.openedText)
    }
    @Test fun MR06_deletedOrChangedReflectionCannotRecallAnOldReflectionSnapshot() {
        val s=recallStore().copy(postDrive=emptyMap()); val result=projector.project(s,localContext(),localNow,"reflection:trip")
        assertNull(result.recent); assertNull(result.openedText); assertEquals(0,result.summary!!.reflectionCount)
    }
    @Test fun MR07_emotionalConsentBoundaryDoesNotEraseHistoryFactsOrExposeMemory() {
        val s=recallStore().copy(consents=mapOf(ConsentScope.TRIP_HISTORY to consent(ConsentScope.TRIP_HISTORY)))
        val result=projector.project(s,localContext(),localNow,"reflection:trip")
        assertEquals(1,result.summary!!.driveCount); assertNull(result.recent); assertNull(result.openedText)
    }
    @Test fun MR08_projectionHasNoInventedRegionWeatherDistanceOrEmotionFacts() {
        val result=projector.project(recallStore(),localContext(),localNow)
        assertEquals(PeriodMemorySummary(2026,11,"UTC",1,59_000,1),result.summary)
        assertEquals(RecentMemoryPreview("reflection:trip","trip",localNow.epochMillis-500,59_000),result.recent)
    }
    @Test fun MR09_stationaryAndFreshUxAreRequiredEvenForAlreadyOpenedText() {
        val c=localContext(); val store=recallStore()
        assertFalse(projector.project(store,c.copy(foreground=false),localNow,"reflection:trip").contentAllowed)
        assertNull(projector.project(store,c,localNow.copy(monotonicMillis=localNow.monotonicMillis+11_000),"reflection:trip").openedText)
    }
    @Test fun MR10_encryptedPersistenceReopenAndDeletePropagateWithoutSummaryCache()=runBlocking {
        val dir=temporary.newFolder(); val r=encryptedTestRepository(dir,RuntimeMode.PRODUCTION); r.load()
        r.setConsent(ConsentScope.TRIP_HISTORY,true,1,DataSource.USER_INPUT); r.setConsent(ConsentScope.EMOTIONAL_MEMORY,true,1,DataSource.USER_INPUT)
        val state=recallStore(); r.save(state.trips.single(),0); r.reflect(state.postDrive["trip"]!!.reflection!!,0); r.saveMemory(state.memories.single())
        val reopened=encryptedTestRepository(dir,RuntimeMode.PRODUCTION); assertEquals(StorageResult.Success(Unit),reopened.load())
        assertNotNull(projector.project(reopened.state.value,localContext(),localNow).recent)
        reopened.delete("trip"); assertNull(projector.project(reopened.state.value,localContext(),localNow).recent)
        assertEquals(0,projector.project(reopened.state.value,localContext(),localNow).summary!!.driveCount)
    }
}
