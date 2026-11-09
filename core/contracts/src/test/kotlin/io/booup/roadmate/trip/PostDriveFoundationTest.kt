package io.booup.roadmate.trip

import io.booup.roadmate.care.*
import io.booup.roadmate.model.*
import io.booup.roadmate.postdrive.*
import io.booup.roadmate.storage.FileTripRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.DataInputStream
import java.time.Instant

class MemoryCardProjectionTest {
    private val projector = MemoryCardProjector()
    @Test fun projectionIsDeterministicAndEveryFactComesFromTrip() {
        val t = record(); val c = projector.project(t)!!
        assertEquals(c, projector.project(t)); assertEquals(t.tripId, c.tripId); assertEquals(t.durationMillis, c.durationMillis)
        assertEquals(t.startedAtEpochMillis, c.startedAtEpochMillis); assertEquals(t.endedAtEpochMillis, c.endedAtEpochMillis)
        assertEquals(listOf(CareSummaryItem(RuleId.REST, 1)), c.careSummary); assertNull(c.reflection)
    }
    @Test fun partialDoesNotInventDistancePlacesRouteOrReflection() {
        val c = projector.project(record())!!
        assertNull(c.distanceMeters); assertNull(c.startLabel); assertNull(c.endLabel); assertNull(c.route); assertNull(c.reflection)
    }
    @Test fun minimalRecordStillGetsBasicCardWithoutLocation() {
        val c = projector.project(record().copy(durationMillis = 0, confirmedDrivingMillis = 0, quality = TripDataQuality.MINIMAL))!!
        assertEquals(TripDataQuality.MINIMAL, c.quality); assertEquals(0, c.durationMillis); assertNull(c.distanceMeters)
    }
    @Test fun fullKnownEndpointsAreCopiedWithoutRegionInference() {
        val a = TripLocation("known-start", DataSource.SIMULATED); val b = TripLocation("known-end", DataSource.SIMULATED)
        val t = record().copy(startLocation = a, endLocation = b, distanceMeters = 1_234, routeSummary = RouteSummary(a,b,1234), quality = TripDataQuality.COMPLETE)
        val c = projector.project(t)!!; assertEquals("known-start", c.startLabel); assertEquals(t.routeSummary, c.route); assertEquals(1234L, c.distanceMeters)
    }
    @Test fun incompleteInvalidOrBadChronologyCannotProject() {
        assertNull(projector.project(record().copy(state = TripState.STOP_CANDIDATE)))
        assertNull(projector.project(record().copy(quality = TripDataQuality.INVALID)))
        assertNull(projector.project(record().copy(endedAtEpochMillis = 1)))
    }
    @Test fun reflectionMustBelongToCardTrip() {
        assertNull(projector.project(record(), TripReflection("other", ReflectionMood.GOOD, null, 250, DataSource.SIMULATED, ReflectionPersistence.TRIP_LOCAL)))
    }
    @Test fun duplicateCareOccurrencesAreCountedOnceWithoutActionSuccess() {
        val t = record(); assertEquals(1, projector.project(t.copy(careEvents = t.careEvents + t.careEvents))!!.careSummary.single().occurrences)
    }
    @Test fun calendarResolvesAllSeasonsAndTimeBucketsDeterministicallyInUtc() {
        val resolver = CalendarResolver()
        listOf("2026-03-01T06:00:00Z", "2026-06-01T12:00:00Z", "2026-09-01T18:00:00Z", "2026-12-01T22:00:00Z")
            .zip(CalendarSeason.entries.zip(CalendarTimeOfDay.entries)).forEach { (iso, expected) ->
                val result = resolver.resolve(Instant.parse(iso).toEpochMilli())
                assertEquals(expected.first, result.season); assertEquals(expected.second, result.timeOfDay); assertEquals("UTC", result.zoneId)
            }
    }
}

class PostDriveFoundationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = TestCareClock()
    private var context = parked()
    private fun parked(ux: UIMode = UIMode.PARKED, motion: MotionState = MotionState.STATIONARY, elapsed: Long = 0) = frame(elapsed).copy(
        motion = observed(motion, elapsed), restrictions = observed(DrivingRestrictions(ux, 0, false), elapsed)).context(elapsed)
    private fun region(tripId: String = "trip") = RegionEvidence(tripId, RegionId("test-region"), "Confirmed test region", RegionEvidenceSource.DEMO, 150, true)
    private suspend fun setup(dir: File = temporary.newFolder(), provider: RegionEvidenceProvider = RegionEvidenceProvider { region(it.tripId) }): Triple<FileTripRepository, TripCoordinator, PostDriveCoordinator> {
        val r = FileTripRepository(dir, RuntimeMode.DEMO); success(r.load()); success(r.setConsent(ConsentScope.TRIP_HISTORY, true, 1, DataSource.SIMULATED)); success(r.save(record(), 0))
        val t = TripCoordinator(r, RuntimeMode.DEMO, clock, TripIdSource { "new-trip" }); t.initialize()
        val p = PostDriveCoordinator(t,r,RuntimeMode.DEMO,clock,{ context },provider); p.refresh()
        return Triple(r,t,p)
    }
    @Test fun completedTripDoesNotAutoPresentCard() = runBlocking {
        val (_,_,p) = setup(); assertNull(p.uiState.value.session.card); assertEquals(PostDriveStage.READY, p.uiState.value.session.stage)
    }
    @Test fun explicitOpenProjectsCardAndAtomicallyClaimsFirstVisit() = runBlocking {
        val (r,_,p) = setup(); assertTrue(p.open("trip")); assertNotNull(p.uiState.value.session.card)
        assertTrue(p.uiState.value.session.reveal); assertEquals(1,r.state.value.visits.size); assertEquals(1,r.state.value.postcards.size)
    }
    @Test fun repeatedOpenNeverRepeatsRevealOrIssue() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.open("trip")
        assertFalse(p.uiState.value.session.reveal); assertEquals(1,r.state.value.postcards.size)
    }
    @Test fun skipPreservesCardPostcardAndAllowsCompletion() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); assertTrue(p.skip()); assertEquals(PostDriveStage.SKIPPED,p.uiState.value.session.stage)
        assertNotNull(p.uiState.value.session.card); assertEquals(1,r.state.value.postcards.size); assertTrue(p.complete())
        assertTrue(r.state.value.trips.single().reviewed); assertNull(r.state.value.postDrive["trip"]!!.reflection)
    }
    @Test fun localMoodOnlyReflectionDoesNotCreateEmotionalMemory() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.requestReflection(); assertTrue(p.reflect(ReflectionMood.GOOD,null,true))
        assertEquals(ReflectionMood.GOOD,r.state.value.postDrive["trip"]!!.reflection!!.mood); assertTrue(r.state.value.memories.isEmpty())
    }
    @Test fun textOnlyReflectionIsUserTextWithoutInterpretation() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); assertTrue(p.reflect(null,"내가 직접 쓴 문장",true))
        assertNull(r.state.value.postDrive["trip"]!!.reflection!!.mood); assertEquals("내가 직접 쓴 문장",p.uiState.value.session.card!!.reflection!!.text)
    }
    @Test fun duplicateAndEditedReflectionUseOneMetadataRecord() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); repeat(2) { p.reflect(ReflectionMood.NEUTRAL,"first",true) }
        p.reflect(ReflectionMood.BAD,"edited",true); assertEquals(1,r.state.value.postDrive.size)
        assertEquals("edited",r.state.value.postDrive["trip"]!!.reflection!!.text)
    }
    @Test fun emptyOrOversizeReflectionIsRejected() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); assertFalse(p.reflect(null," ",true)); assertFalse(p.reflect(null,"x".repeat(4097),true))
        assertNull(r.state.value.postDrive["trip"]!!.reflection)
    }
    @Test fun sessionOnlyReflectionHasNoDurableSaveOrMemoryPromotion() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.reflect(ReflectionMood.GOOD,"session secret",false)
        assertNotNull(p.uiState.value.session.card!!.reflection); assertNull(r.state.value.postDrive["trip"]!!.reflection)
        assertFalse(p.remember(true)); assertTrue(r.state.value.memories.isEmpty())
    }
    @Test fun confirmationWithoutScopeCannotSaveAndIsNotAutomaticallyRetried() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.reflect(null,"private",true); assertFalse(p.remember(true))
        p.consent(ConsentScope.EMOTIONAL_MEMORY,true); p.refresh(); assertTrue(r.state.value.memories.isEmpty())
    }
    @Test fun scopeWithoutItemConfirmationCannotSave() = runBlocking {
        val (r,_,p) = setup(); p.consent(ConsentScope.EMOTIONAL_MEMORY,true); p.open("trip"); p.reflect(null,"private",true)
        assertFalse(p.remember(false)); assertTrue(r.state.value.memories.isEmpty())
    }
    @Test fun scopeAndConfirmationSaveExactlyOneMemoryAndRevokePurgesOnlyMemory() = runBlocking {
        val (r,_,p) = setup(); p.consent(ConsentScope.EMOTIONAL_MEMORY,true); p.open("trip"); p.reflect(ReflectionMood.GOOD,"private",true)
        assertTrue(p.remember(true)); assertTrue(p.remember(true)); assertEquals(1,r.state.value.memories.size)
        assertTrue(p.consent(ConsentScope.EMOTIONAL_MEMORY,false)); assertTrue(r.state.value.memories.isEmpty())
        assertNotNull(r.state.value.postDrive["trip"]!!.reflection); assertFalse(p.remember(true))
    }
    @Test fun unknownRegionProducesCardWithoutPostcard() = runBlocking {
        val (r,_,p) = setup(provider = RegionEvidenceProvider { null }); p.open("trip")
        assertNotNull(p.uiState.value.session.card); assertNull(p.uiState.value.session.postcard); assertTrue(r.state.value.visits.isEmpty())
    }
    @Test fun unverifiedWrongTripOrOutOfTripEvidenceCannotIssue() = runBlocking {
        for (e in listOf(region().copy(verified=false),region("other"),region().copy(observedAtEpochMillis=201),region().copy(source=RegionEvidenceSource.UNKNOWN))) {
            val (r,_,p) = setup(provider=RegionEvidenceProvider { e }); p.open("trip"); assertTrue(r.state.value.postcards.isEmpty())
        }
    }
    @Test fun sameRegionOnSecondTripDoesNotIssueAnotherBasicPostcard() = runBlocking {
        val (r,t,p) = setup(); p.open("trip"); success(r.save(record("second"),0)); t.initialize()
        assertTrue(p.open("second")); assertEquals(1,r.state.value.postcards.size); assertFalse(p.uiState.value.session.reveal)
    }
    @Test fun reviewedReopenShowsNormalDetailWithoutNewReveal() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.skip(); p.complete(); assertTrue(p.open("trip"))
        assertEquals(PostDriveStage.COMPLETED,p.uiState.value.session.stage); assertFalse(p.uiState.value.session.reveal); assertEquals(1,r.state.value.visits.size)
    }
    @Test fun restrictionChangeClosesCardAndRejectsQueuedReflectionAndRemember() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); context=parked(UIMode.RESTRICTED); p.refresh()
        assertFalse(p.uiState.value.contentAllowed); assertNull(p.uiState.value.session.card)
        assertFalse(p.reflect(ReflectionMood.GOOD,"blocked",true)); assertFalse(p.remember(true)); assertNull(r.state.value.postDrive["trip"]!!.reflection)
    }
    @Test fun staleUnknownMovingDisconnectedAndBackgroundCannotOpenOrChangeConsent() = runBlocking {
        val (_,_,p) = setup()
        for (c in listOf(parked(UIMode.UNKNOWN_RESTRICTED),parked(motion=MotionState.UNKNOWN),parked(motion=MotionState.MOVING),
            parked().copy(connection=ConnectionState()),parked().copy(foreground=false))) {
            context=c; assertFalse(p.open("trip")); assertFalse(p.consent(ConsentScope.EMOTIONAL_MEMORY,true))
        }
        context=parked(); clock.elapsed=5001; assertFalse(p.open("trip"))
    }
    @Test fun clearHistoryRequiresConfirmationAndDetachesCollection() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); assertFalse(p.clearHistory(false)); assertEquals(1,r.state.value.trips.size)
        assertTrue(p.clearHistory(true)); assertTrue(r.state.value.trips.isEmpty()); assertTrue(r.state.value.postDrive.isEmpty())
        assertTrue(r.state.value.postcards.single().sourceTripDeleted); assertNull(r.state.value.postcards.single().sourceTripId)
    }
    @Test fun deleteCascadesReflectionMemoryAndCardWithoutDanglingCollectionLink() = runBlocking {
        val (r,_,p) = setup(); p.consent(ConsentScope.EMOTIONAL_MEMORY,true); p.open("trip"); p.reflect(null,"private",true); p.remember(true)
        assertTrue(p.delete("trip")); assertNull(p.uiState.value.session.card); assertTrue(r.state.value.memories.isEmpty())
        assertTrue(r.state.value.postDrive.isEmpty()); assertEquals(1,r.state.value.postcards.size); assertNull(r.state.value.visits.single().sourceTripId)
    }
    @Test fun staleGenerationCannotResurrectReflectionReviewOrMemory() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.delete("trip")
        failure(StorageError.NOT_FOUND,r.reflect(TripReflection("trip",ReflectionMood.GOOD,null,1_000_000,DataSource.SIMULATED,ReflectionPersistence.TRIP_LOCAL),0))
        failure(StorageError.NOT_FOUND,r.completeReview("trip",0)); failure(StorageError.NOT_FOUND,r.rememberReflection("trip",true,0))
    }
    @Test fun historyRevocationPurgesTripMetadataAndKeepsDetachedOwnedCollection() = runBlocking {
        val (r,_,p) = setup(); p.open("trip"); p.reflect(null,"private",true); assertTrue(p.consent(ConsentScope.TRIP_HISTORY,false))
        assertTrue(r.state.value.postDrive.isEmpty()); assertTrue(r.state.value.trips.isEmpty()); assertTrue(r.state.value.postcards.single().sourceTripDeleted)
    }
    @Test fun schemaTwoReopensReflectionReviewVisitPostcardAndClaimWithoutDuplication() = runBlocking {
        val dir=temporary.newFolder(); val (r,_,p)=setup(dir); p.open("trip"); p.reflect(ReflectionMood.GOOD,"saved",true); p.complete()
        val reopened=FileTripRepository(dir,RuntimeMode.DEMO); success(reopened.load())
        assertEquals(r.state.value,reopened.state.value); assertTrue(reopened.state.value.postDrive["trip"]!!.revealConsumed)
        DataInputStream(File(dir,"trips.v1.bin").inputStream()).use { it.readUTF(); assertEquals(3,it.readInt()) }
    }
    @Test fun alternateDeleteCollectionPolicyRemovesVisitAndPostcard() = runBlocking {
        val dir=temporary.newFolder(); val (r,_,p)=setup(dir); p.open("trip")
        val other=FileTripRepository(dir,RuntimeMode.DEMO,CollectionRetentionPolicy.DELETE_WITH_SOURCE_TRIP); success(other.load()); success(other.delete("trip"))
        assertTrue(other.state.value.postcards.isEmpty()); assertTrue(other.state.value.visits.isEmpty()); success(r.load())
    }
    @Test fun clearCollectionIsSeparateFromClearHistory() = runBlocking {
        val (r,_,p)=setup(); p.open("trip"); success(r.clearCollection()); assertEquals(1,r.state.value.trips.size)
        assertTrue(r.state.value.postcards.isEmpty()); assertTrue(r.state.value.visits.isEmpty())
        p.refresh(); assertNull(p.uiState.value.session.postcard)
    }
    @Test fun processStyleReopenUsesStoredPostcardWithoutRequiringNewRegionEvidence() = runBlocking {
        val dir=temporary.newFolder(); val (_,_,first)=setup(dir); first.open("trip"); first.skip(); first.complete()
        val r=FileTripRepository(dir,RuntimeMode.DEMO); success(r.load())
        val t=TripCoordinator(r,RuntimeMode.DEMO,clock,TripIdSource { "unused" }); t.initialize()
        val reopened=PostDriveCoordinator(t,r,RuntimeMode.DEMO,clock,{context})
        assertTrue(reopened.open("trip")); assertNotNull(reopened.uiState.value.session.postcard)
        assertFalse(reopened.uiState.value.session.reveal); assertEquals(PostDriveStage.COMPLETED,reopened.uiState.value.session.stage)
    }
    @Test fun goldenMigrationFailureKeepsSchemaOneBytesAndReportsFailure() = runBlocking {
        val dir=temporary.newFolder(); val bytes=javaClass.getResourceAsStream("/schema1-golden.bin")!!.readBytes()
        val f=File(dir,"trips.v1.bin"); f.writeBytes(bytes); File(dir,"trips.pending").mkdir()
        failure(StorageError.IO,FileTripRepository(dir,RuntimeMode.DEMO).load()); assertArrayEquals(bytes,f.readBytes())
    }
    @Test fun schemaTwoCorruptionAndWrongModeFailClosed() = runBlocking {
        val dir=temporary.newFolder(); val (_,_,p)=setup(dir); p.open("trip")
        failure(StorageError.WRONG_ENVIRONMENT,FileTripRepository(dir,RuntimeMode.PRODUCTION).load())
        val f=File(dir,"trips.v1.bin"); val damaged=f.readBytes().dropLast(1).toByteArray(); f.writeBytes(damaged)
        failure(StorageError.INVALID_DATA,FileTripRepository(dir,RuntimeMode.DEMO).load()); assertArrayEquals(damaged,f.readBytes())
    }
    @Test fun productionRejectsDemoRegionEvenWhenClaimedVerified() {
        assertEquals(PostcardEligibility.UNAVAILABLE_REGION,PostcardPolicy().evaluate(record().copy(source=DataSource.DERIVED),PostDriveState.READY,region(),emptyList(),RuntimeMode.PRODUCTION))
    }
    @Test fun staleUiTripTargetAndChangedItemConfirmationAreRejected() = runBlocking {
        val (r,_,p)=setup(); p.consent(ConsentScope.EMOTIONAL_MEMORY,true); p.open("trip"); p.reflect(null,"first",true)
        val first=r.state.value.postDrive["trip"]!!.reflection!!
        assertFalse(p.reflect(null,"wrong target",true,expectedTripId="other"))
        assertFalse(p.skip(expectedTripId="other")); assertFalse(p.complete(expectedTripId="other"))
        p.reflect(null,"edited",true)
        assertFalse(p.remember(true,expectedTripId="trip",expectedReflection=first)); assertTrue(r.state.value.memories.isEmpty())
    }
    @Test fun newActiveTripAndStopCandidateBlockHistoricalPostDrive() = runBlocking {
        val (_,t,p)=setup(); assertTrue(t.manual(true)); assertEquals(TripState.IN_PROGRESS,t.uiState.value.runtime.state)
        assertFalse(p.open("trip")); t.update(context,CareDecision())
        assertEquals(TripState.STOP_CANDIDATE,t.uiState.value.runtime.state); assertFalse(p.open("trip"))
    }
    @Test fun editingTripReflectionDoesNotSilentlyEditAlreadyConfirmedMemory() = runBlocking {
        val (r,_,p)=setup(); p.consent(ConsentScope.EMOTIONAL_MEMORY,true); p.open("trip")
        p.reflect(null,"confirmed",true); p.remember(true); p.reflect(null,"not confirmed yet",true)
        assertEquals("confirmed",r.state.value.memories.single().text)
        assertEquals("not confirmed yet",r.state.value.postDrive["trip"]!!.reflection!!.text)
    }
    @Test fun repositoryRejectsSessionOnlyReflectionAndWritesAfterHistoryRevocation() = runBlocking {
        val (r,_,p)=setup(); p.open("trip")
        val reflection=TripReflection("trip",ReflectionMood.GOOD,null,1_000_000,DataSource.SIMULATED,ReflectionPersistence.SESSION_ONLY)
        failure(StorageError.INVALID_DATA,r.reflect(reflection,0))
        p.consent(ConsentScope.TRIP_HISTORY,false)
        failure(StorageError.CONSENT_REQUIRED,r.reflect(reflection.copy(persistence=ReflectionPersistence.TRIP_LOCAL),r.state.value.captureGeneration))
    }
    @Test fun verifiedContextRegionIsSupportedWithoutCreatingAProductionProvider() {
        assertEquals(PostcardEligibility.ELIGIBLE,PostcardPolicy().evaluate(record().copy(source=DataSource.DERIVED),PostDriveState.READY,region().copy(source=RegionEvidenceSource.VERIFIED_CONTEXT),emptyList(),RuntimeMode.PRODUCTION))
    }
}
