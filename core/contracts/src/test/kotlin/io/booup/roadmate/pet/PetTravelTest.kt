package io.booup.roadmate.pet

import io.booup.roadmate.local.localContext
import io.booup.roadmate.local.localNow
import io.booup.roadmate.model.*
import io.booup.roadmate.storage.*
import io.booup.roadmate.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PetTravelTest {
    @get:Rule val temporary=TemporaryFolder()
    private inner class Harness(val mode:RuntimeMode=RuntimeMode.PRODUCTION) {
        val dir=temporary.newFolder()
        val repository=FilePetTravelRepository(dir,mode,testEncryption(mode))
        var now=localNow
        var context=localContext(now)
        var trip=TripUiState(storage=TripStorageState(loaded=true,consents=mapOf(ConsentScope.TRIP_HISTORY to
            ConsentRecord(ConsentScope.TRIP_HISTORY,true,1,null,DataSource.USER_INPUT,"fixture"))))
        var nextId=0
        fun coordinator(repo:io.booup.roadmate.contracts.PetTravelRepository=repository)=PetTravelCoordinator(repo,mode,
            ContextClock {now},{context},{trip},{"pet-${++nextId}"})
        val care=coordinator()
        suspend fun start() { care.initialize(); assertTrue(care.saveProfile("Bori",PetKind.DOG,true)) }
        suspend fun begin() { start(); assertTrue(care.beginOuting()) }
        suspend fun complete(id:String="actual-trip",source:DataSource=DataSource.DERIVED) {
            val start=now.epochMillis+1
            now=now.copy(epochMillis=now.epochMillis+1000,monotonicMillis=now.monotonicMillis+1000); context=localContext(now)
            val record=TripRecord(id,TripState.COMPLETED,start,now.epochMillis,source,999,800,
                completionReason=TripCompletionReason.CONFIRMED_STATIONARY,quality=TripDataQuality.PARTIAL,createdAtEpochMillis=now.epochMillis)
            trip=trip.copy(runtime=TripRuntimeStatus(TripState.COMPLETED,completed=record),storage=trip.storage.copy(trips=trip.storage.trips+record))
            care.refresh()
        }
        suspend fun card(note:String="OWNER_NOTE",memo:String="NEXT_NOTE") { begin(); complete(); assertTrue(care.finishOuting(note,memo)) }
        fun file()=File(dir,"pet-travel.v1.bin")
    }
    @Test fun disabledByDefaultAndRequiresExplicitStorageConsent()=runBlocking {
        val h=Harness();h.care.initialize()
        assertFalse(h.care.uiState.value.data.enabled);assertNull(h.care.uiState.value.data.profile)
        assertFalse(h.care.saveProfile("Bori",PetKind.DOG,false));assertFalse(h.file().exists())
    }
    @Test fun profileModeAndPreparationReopenFromRealEncryptedStore()=runBlocking {
        val h=Harness();h.start();h.care.togglePacking(PetPackingItem.WATER_KIT)
        val reopened=FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode));val c=h.coordinator(reopened);c.initialize()
        assertEquals("Bori",c.uiState.value.data.profile?.name);assertTrue(c.uiState.value.data.enabled)
        assertTrue(PetPackingItem.WATER_KIT in c.uiState.value.data.packing)
        assertTrue(AesGcmEnvelope.isEnvelope(h.file().readBytes()));assertFalse(h.file().readBytes().toString(Charsets.ISO_8859_1).contains("Bori"))
    }
    @Test fun modeOffPreservesNotebookAndCancelsOnlyUnfinishedPetPlan()=runBlocking {
        val h=Harness();h.begin();h.care.addMemo("remember bag");h.care.setEnabled(false)
        val d=h.care.uiState.value.data
        assertFalse(d.enabled);assertNull(d.plan);assertEquals(1,d.memos.size);assertEquals("Bori",d.profile?.name)
    }
    @Test fun profileAndTextValidationDoesNotWriteInvalidData()=runBlocking {
        val h=Harness();h.care.initialize()
        assertFalse(h.care.saveProfile(" ",PetKind.DOG,true));assertFalse(h.care.saveProfile("x".repeat(25),PetKind.DOG,true))
        assertFalse(h.care.saveProfile("name\ncontrol",PetKind.DOG,true));h.start()
        assertFalse(h.care.addMemo(" "));assertFalse(h.care.addMemo("x".repeat(181)))
        assertFalse(h.care.savePlace(null,"", "",true));assertFalse(h.care.savePlace(null,"place","x".repeat(241),true))
    }
    @Test fun profileCanBeEditedBetweenOutingsButCannotChangeAnActivePetsIdentity()=runBlocking {
        val h=Harness();h.start();assertTrue(h.care.saveProfile("Bori renamed",PetKind.OTHER,true))
        assertEquals(PetKind.OTHER,h.care.uiState.value.data.profile?.kind)
        h.care.beginOuting();assertFalse(h.care.saveProfile("Another pet",PetKind.CAT,true))
        assertEquals("Bori renamed",h.care.uiState.value.data.profile?.name)
    }
    @Test fun futureActiveSampleDoesNotAttachToPetPlan()=runBlocking {
        val h=Harness();h.begin()
        val active=ActiveTripSnapshot("future",TripState.IN_PROGRESS,h.now.epochMillis+1000,h.now.epochMillis+1000,0,0,null,null,DataSource.DERIVED,false)
        h.trip=h.trip.copy(runtime=TripRuntimeStatus(TripState.IN_PROGRESS,active=active));h.care.refresh()
        assertNull(h.care.uiState.value.data.plan?.tripId)
    }
    @Test fun beginNeedsHistoryConsentAndNeverMakesSyntheticTrip()=runBlocking {
        val h=Harness();h.start();h.trip=h.trip.copy(storage=h.trip.storage.copy(consents=emptyMap()))
        val before=h.trip;assertFalse(h.care.beginOuting());assertEquals(before,h.trip);assertNull(h.care.uiState.value.data.plan)
    }
    @Test fun previouslyCompletedTripCannotBecomeNewPetOuting()=runBlocking {
        val h=Harness();h.complete();h.start();h.now=h.now.copy(epochMillis=h.now.epochMillis+1)
        assertTrue(h.care.beginOuting());h.care.refresh()
        assertNull(h.care.uiState.value.completedTrip);assertFalse(h.care.finishOuting("",""))
    }
    @Test fun ownerBeginsThenActualCompletedTripCanBeSavedOnce()=runBlocking {
        val h=Harness();h.begin();h.complete()
        assertEquals("actual-trip",h.care.uiState.value.completedTrip?.tripId)
        val before=h.trip
        assertTrue(h.care.finishOuting("Owner observation","Next departure"))
        assertFalse(h.care.finishOuting("duplicate",""));assertEquals(1,h.care.uiState.value.cards.size)
        assertEquals(before,h.trip)
    }
    @Test fun productionRejectsSimulatedAndUnknownTripSources()=runBlocking {
        for(source in listOf(DataSource.SIMULATED,DataSource.UNKNOWN,DataSource.USER_INPUT)) {
            val h=Harness();h.begin();h.complete(source=source)
            assertNull(h.care.uiState.value.completedTrip);assertFalse(h.care.finishOuting("",""))
        }
    }
    @Test fun actualActiveTripAssociationSurvivesUntilCompletion()=runBlocking {
        val h=Harness();h.begin()
        val active=ActiveTripSnapshot("actual-trip",TripState.IN_PROGRESS,h.now.epochMillis,h.now.epochMillis,0,0,null,null,DataSource.DERIVED,false)
        h.trip=h.trip.copy(runtime=TripRuntimeStatus(TripState.IN_PROGRESS,active=active));h.care.refresh()
        assertEquals("actual-trip",h.repository.state.value.data.plan?.tripId)
        assertFalse(h.care.finishOuting("",""));h.complete();assertTrue(h.care.finishOuting("",""))
    }
    @Test fun historyDeletionRemovesLinkedCardsAndNotesButKeepsIndependentNotes()=runBlocking {
        val h=Harness();h.card();h.care.addMemo("independent preference")
        h.trip=h.trip.copy(storage=h.trip.storage.copy(trips=emptyList(),captureGeneration=1));h.care.refresh()
        assertTrue(h.care.uiState.value.cards.isEmpty());assertEquals(listOf("independent preference"),h.care.uiState.value.data.memos.map {it.text})
        val reopen=h.coordinator(FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode)));reopen.initialize()
        assertTrue(reopen.uiState.value.cards.isEmpty());assertEquals(1,reopen.uiState.value.data.memos.size)
    }
    @Test fun historyConsentWithdrawalCancelsPlanAndPrunesTripLinkedData()=runBlocking {
        val h=Harness();h.card();h.care.beginOuting()
        h.trip=h.trip.copy(storage=h.trip.storage.copy(consents=emptyMap(),captureGeneration=1));h.care.refresh()
        assertNull(h.care.uiState.value.data.plan);assertTrue(h.care.uiState.value.cards.isEmpty())
        assertTrue(h.care.uiState.value.data.memos.isEmpty());assertNotNull(h.care.uiState.value.data.profile)
    }
    @Test fun petOnlyDeletionPreservesOriginalHistoryAndXp()=runBlocking {
        val h=Harness();h.card();val original=h.trip
        assertTrue(h.care.deleteAll());assertEquals(PetTravelData(),h.care.uiState.value.data);assertEquals(original,h.trip)
        val reopened=h.coordinator(FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode)));reopened.initialize()
        assertEquals(PetTravelData(),reopened.uiState.value.data)
    }
    @Test fun deletingOnePetCardRemovesItsMemoWithoutDeletingTrip()=runBlocking {
        val h=Harness();h.card();val trip=h.trip
        h.care.deleteCard("actual-trip");assertTrue(h.care.uiState.value.cards.isEmpty())
        assertTrue(h.care.uiState.value.data.memos.isEmpty());assertEquals(trip,h.trip)
    }
    @Test fun rewardsNeedRealCriteriaAndClaimIsIdempotent()=runBlocking {
        val h=Harness();h.start();assertFalse(h.care.claim(PetPassReward.PAW_FRAME))
        assertTrue(h.care.claim(PetPassReward.WELCOME_BADGE));assertTrue(h.care.claim(PetPassReward.WELCOME_BADGE))
        assertEquals(setOf(PetPassReward.WELCOME_BADGE),h.care.uiState.value.data.claimed)
        assertFalse(h.care.applyFrame(PetCardFrame.PAW))
    }
    @Test fun claimedFrameReallyPersistsAndDoesNotChangeXpOrSafety()=runBlocking {
        val h=Harness();h.card();val before=h.trip
        assertTrue(h.care.claim(PetPassReward.PAW_FRAME));assertTrue(h.care.applyFrame(PetCardFrame.PAW))
        val c=h.coordinator(FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode)));c.initialize()
        assertEquals(PetCardFrame.PAW,c.uiState.value.data.frame);assertEquals(before,h.trip)
        assertTrue(h.context.care.preference.currentValue==null && h.context.driverPresent.currentValue==null)
    }
    @Test fun manualMemoUnlocksStarFrameWithoutMoreDrivingOrFreeTextRequirement()=runBlocking {
        val h=Harness();h.start();val before=h.trip
        h.care.addMemo("접이식 물그릇 챙기기");assertTrue(h.care.claim(PetPassReward.STAR_FRAME))
        assertTrue(h.care.applyFrame(PetCardFrame.STAR));assertEquals(before,h.trip)
    }
    @Test fun personalPlacesCanBeEditedAndDeletedWithoutCreatingVisitEvidence()=runBlocking {
        val h=Harness();h.start();val before=h.trip
        assertTrue(h.care.savePlace(null,"Our place","Owner note",true))
        val id=h.care.uiState.value.data.places.single().id
        assertTrue(h.care.savePlace(id,"Renamed","Check admission ourselves",false))
        assertEquals(1,h.care.uiState.value.data.places.size);assertFalse(h.care.uiState.value.data.places.single().returnWanted)
        assertFalse(h.care.savePlace("missing","place","",true))
        h.care.deletePlace(id);assertTrue(h.care.uiState.value.data.places.isEmpty());assertEquals(before,h.trip)
    }
    @Test fun movingUnknownRestrictedStaleAndBackgroundHideSensitiveUiAndRejectWrites()=runBlocking {
        val h=Harness();h.card();val original=h.context;val before=h.file().readBytes()
        val policies=ContextFreshnessPolicies()
        val moving=(original.motion.observation as Reading.Observed).copy(value=MotionState.MOVING)
        val unknown=Reading.Unavailable(UnavailableReason.NOT_VERIFIED,DataSource.SYSTEM)
        val restricted=(original.restrictions.observation as Reading.Observed).copy(value=DrivingRestrictions(UIMode.RESTRICTED,80,true))
        val stale=(original.motion.observation as Reading.Observed).let {it.copy(metadata=it.metadata.copy(observedAtMonotonicMillis=1))}
        for(c in listOf(original.copy(motion=moving.evaluate(h.now,policies.occupancy,original.connection,true)),
            original.copy(motion=unknown.evaluate(h.now,policies.occupancy,original.connection,true)),
            original.copy(restrictions=restricted.evaluate(h.now,policies.restrictions,original.connection,true)),
            original.copy(motion=stale.evaluate(h.now,policies.occupancy,original.connection,true)),original.copy(foreground=false))) {
            h.context=c;h.care.refresh();assertFalse(h.care.uiState.value.contentAllowed)
            assertNull(h.care.uiState.value.data.profile);assertTrue(h.care.uiState.value.cards.isEmpty())
            assertFalse(h.care.addMemo("must not write"));assertArrayEquals(before,h.file().readBytes())
        }
    }
    @Test fun tamperedPetFileFailsClosedWithoutTouchingOriginalTripOrReplacingFile()=runBlocking {
        val h=Harness();h.card();val trip=h.trip;val bytes=h.file().readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();h.file().writeBytes(bytes)
        val c=h.coordinator(FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode)));c.initialize()
        assertEquals(StorageError.AUTHENTICATION_FAILED,c.uiState.value.error);assertFalse(c.uiState.value.contentAllowed)
        assertFalse(c.addMemo("no overwrite"));assertArrayEquals(bytes,h.file().readBytes());assertEquals(trip,h.trip)
    }
    @Test fun atomicFailurePreservesLastGoodPetFileAndReopenRestoresIt()=runBlocking {
        val h=Harness();h.start();val original=h.file().readBytes()
        val failing=FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode),beforeReplace={throw IOException()})
        val c=h.coordinator(failing);c.initialize();assertFalse(c.addMemo("not committed"))
        assertEquals(StorageError.IO,c.uiState.value.error);assertArrayEquals(original,h.file().readBytes())
        assertFalse(File(h.dir,"pet.pending").exists());c.initialize();assertTrue(c.uiState.value.data.memos.isEmpty())
    }
    @Test fun missingKeyDoesNotCreateReplacementOrResetPetFile()=runBlocking {
        val h=Harness();h.start();val original=h.file().readBytes();var creates=0
        val missing=object:StoreKeyProvider {
            override fun existingKey():javax.crypto.SecretKey?=null
            override fun createKey():javax.crypto.SecretKey {creates++;error("must not create")}
        }
        val c=h.coordinator(FilePetTravelRepository(h.dir,h.mode,AesGcmEnvelope(missing,h.mode)));c.initialize()
        assertEquals(StorageError.KEY_MISSING,c.uiState.value.error);assertEquals(0,creates);assertArrayEquals(original,h.file().readBytes())
    }
    @Test fun encryptedPetSidecarCannotOpenAsTheOtherEnvironment()=runBlocking {
        val h=Harness();h.start()
        val other=FilePetTravelRepository(h.dir,RuntimeMode.DEMO,testEncryption(RuntimeMode.DEMO))
        assertEquals(StorageResult.Failure(StorageError.WRONG_ENVIRONMENT),other.load())
    }
    @Test fun petFileContainsNoOwnerNotesAndNonceChangesBetweenWrites()=runBlocking {
        val h=Harness();h.card("FIXED_PET_NOTE","FIXED_PET_DEPARTURE")
        val first=h.file().readBytes();h.care.addMemo("FIXED_EXTRA_MEMO");val second=h.file().readBytes()
        for(value in listOf("FIXED_PET_NOTE","FIXED_PET_DEPARTURE","FIXED_EXTRA_MEMO")) assertFalse(second.toString(Charsets.ISO_8859_1).contains(value))
        assertFalse(first.copyOfRange(19,31).contentEquals(second.copyOfRange(19,31)))
    }
    @Test fun separateRepositoryInstancesMergeUnderFileLock()=runBlocking {
        val h=Harness();h.start();val other=h.coordinator(FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode)));other.initialize()
        h.care.addMemo("first");other.addMemo("second");h.care.initialize()
        assertEquals(listOf("first","second"),h.care.uiState.value.data.memos.map {it.text})
    }
    @Test fun privacyProjectionHidesDeletedTripEvenIfPruningWriteFails()=runBlocking {
        val h=Harness();h.card()
        val failing=h.coordinator(FilePetTravelRepository(h.dir,h.mode,testEncryption(h.mode),beforeReplace={throw IOException()}));failing.initialize()
        h.trip=h.trip.copy(storage=h.trip.storage.copy(trips=emptyList(),captureGeneration=1));failing.refresh()
        assertTrue(failing.uiState.value.cards.isEmpty());assertTrue(failing.uiState.value.data.memos.isEmpty())
    }
    @Test fun sourceDeletionDoesNotRemoveIndependentClaimedCosmetics()=runBlocking {
        val h=Harness();h.card();h.care.claim(PetPassReward.PAW_FRAME);h.care.applyFrame(PetCardFrame.PAW)
        h.trip=h.trip.copy(storage=h.trip.storage.copy(trips=emptyList(),captureGeneration=1));h.care.refresh()
        assertTrue(h.care.uiState.value.cards.isEmpty());assertTrue(PetPassReward.PAW_FRAME in h.care.uiState.value.data.claimed)
    }
}
