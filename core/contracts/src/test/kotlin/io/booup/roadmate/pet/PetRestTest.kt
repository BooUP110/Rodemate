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
import java.io.*

class PetRestTest {
    @get:Rule val temporary=TemporaryFolder()
    private inner class Harness {
        val dir=temporary.newFolder()
        val repo=FilePetTravelRepository(dir,RuntimeMode.PRODUCTION,testEncryption(RuntimeMode.PRODUCTION))
        var now=localNow
        var context=localContext(now)
        val trip=TripUiState(storage=TripStorageState(loaded=true))
        var id=0
        fun coordinator(repository:io.booup.roadmate.contracts.PetTravelRepository=repo)=PetTravelCoordinator(repository,
            RuntimeMode.PRODUCTION,ContextClock {now},{context},{trip},{"rest-${++id}"})
        val care=coordinator()
        suspend fun start() {care.initialize();assertTrue(care.saveProfile("Rest pet",PetKind.CAT,true));assertTrue(care.chooseRestInterval(15));assertTrue(care.startRestSession(true))}
        suspend fun advance(ms:Long,motion:MotionState=MotionState.STATIONARY) {
            now=ObservationTime(now.epochMillis+ms,now.monotonicMillis+ms)
            val base=localContext(now)
            val reading=Reading.Observed(motion,DataMetadata(DataSource.SYSTEM,now.epochMillis,Freshness.FRESH,
                CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,now.monotonicMillis))
                .evaluate(now,ContextFreshnessPolicies().occupancy,base.connection,true)
            context=base.copy(motion=reading);care.refresh()
        }
        fun session()=requireNotNull(care.uiState.value.data.restSession)
    }

    @Test fun restNeedsOwnConsentAndExplicitIntervalButNotTripHistory()=runBlocking {
        val h=Harness();h.care.initialize();assertFalse(h.care.chooseRestInterval(30));assertFalse(h.care.startRestSession())
        assertTrue(h.care.saveProfile("Pet",PetKind.DOG,true));assertNull(h.care.uiState.value.data.restIntervalMinutes)
        assertFalse(h.care.startRestSession(true));assertTrue(h.care.chooseRestInterval(45));assertTrue(h.care.startRestSession(true))
        assertEquals(45,h.session().intervalMinutes);assertTrue(h.trip.storage.consents.isEmpty())
        assertFalse(h.care.startRestSession(true));assertFalse(h.care.chooseRestInterval(30))
    }
    @Test fun invalidIntervalDoesNotChangePlan()=runBlocking {
        val h=Harness();h.care.initialize();h.care.saveProfile("Pet",PetKind.DOG,true)
        for(value in listOf(-1,0,14,241,Int.MAX_VALUE)) assertFalse(h.care.chooseRestInterval(value))
        assertNull(h.care.uiState.value.data.restIntervalMinutes)
    }
    @Test fun selectedIntervalAndOldPetConsentDoNotReplaceExplicitRestStorageConsent()=runBlocking {
        val h=Harness();h.care.initialize();h.care.saveProfile("Pet",PetKind.DOG,true);h.care.chooseRestInterval(30)
        assertFalse(h.care.startRestSession());assertFalse(h.care.startRestSession(false))
        assertNull(h.care.uiState.value.data.restSession);assertTrue(h.care.startRestSession(true))
    }
    @Test fun timerCountsObservedMovingAndStationarySessionTimeWithoutDeclaringRest()=runBlocking {
        val h=Harness();h.start();h.advance(1000,MotionState.MOVING);h.advance(1000)
        assertEquals(2000L,h.session().elapsedSinceRestMillis);assertEquals(0,h.session().confirmedRests)
        assertFalse(h.session().resting);assertEquals(h.trip,TripUiState(storage=TripStorageState(loaded=true)))
    }
    @Test fun pauseAndResumeDoNotCountPausedTimeOrResetRestDeadline()=runBlocking {
        val h=Harness();h.start();h.advance(1000);assertTrue(h.care.pauseRestSession());h.advance(4000)
        assertEquals(1000L,h.session().elapsedSinceRestMillis);assertTrue(h.care.resumeRestSession());h.advance(1000)
        assertEquals(2000L,h.session().elapsedSinceRestMillis);assertEquals(0,h.session().confirmedRests)
    }
    @Test fun backgroundPausesAndRequiresOwnerResume()=runBlocking {
        val h=Harness();h.start();h.advance(1000);h.context=h.context.copy(foreground=false);h.care.refresh()
        assertFalse(h.care.uiState.value.contentAllowed);assertFalse(h.care.resumeRestSession())
        h.advance(1000);assertTrue(h.session().paused);assertEquals(1000L,h.session().elapsedSinceRestMillis)
        assertTrue(h.care.resumeRestSession());h.advance(1000);assertEquals(2000L,h.session().elapsedSinceRestMillis)
    }
    @Test fun unknownMotionPausesWithoutAssumingTravelOrRest()=runBlocking {
        val h=Harness();h.start();h.advance(1000,MotionState.UNKNOWN)
        assertFalse(h.care.uiState.value.contentAllowed);h.advance(1000)
        assertTrue(h.session().paused);assertEquals(0L,h.session().elapsedTotalMillis);assertEquals(0,h.session().confirmedRests)
    }
    @Test fun staleEvidenceCannotAdvanceClock()=runBlocking {
        val h=Harness();h.start();h.now=h.now.copy(epochMillis=h.now.epochMillis+20_000,monotonicMillis=h.now.monotonicMillis+20_000)
        h.care.refresh();assertFalse(h.care.uiState.value.contentAllowed)
        h.advance(1000);assertTrue(h.session().paused);assertEquals(0L,h.session().elapsedTotalMillis)
    }
    @Test fun longTickGapPausesInsteadOfCountingUnobservedInterval()=runBlocking {
        val h=Harness();h.start();h.advance(6000);assertTrue(h.session().paused);assertEquals(0L,h.session().elapsedTotalMillis)
    }
    @Test fun connectionSessionBoundaryCannotCarryClockForward()=runBlocking {
        val h=Harness();h.start();h.context=h.context.copy(sessionId=h.context.sessionId+1);h.care.refresh()
        assertTrue(h.session().paused);assertEquals(0L,h.session().elapsedTotalMillis)
    }
    @Test fun clockRollbackPausesWithoutNegativeOrInventedTime()=runBlocking {
        val h=Harness();h.start();h.now=h.now.copy(epochMillis=h.now.epochMillis-1,monotonicMillis=h.now.monotonicMillis-1)
        h.context=localContext(h.now);h.care.refresh();assertTrue(h.session().paused);assertEquals(0L,h.session().elapsedTotalMillis)
    }
    @Test fun explicitRestChecksResetDeadlineExactlyOnceWithoutXpOrTripWrites()=runBlocking {
        val h=Harness();h.start();h.advance(1000);val parent=h.trip
        assertFalse(h.care.confirmPetRest());assertTrue(h.care.beginPetRest());h.advance(1000)
        assertEquals(1000L,h.session().elapsedTotalMillis);assertFalse(h.care.confirmPetRest())
        assertTrue(h.care.toggleRestCheck(PetRestCheck.WATER));assertTrue(h.care.confirmPetRest())
        assertFalse(h.care.confirmPetRest());assertEquals(1,h.session().confirmedRests)
        assertEquals(0L,h.session().elapsedSinceRestMillis);assertEquals(parent,h.trip)
    }
    @Test fun cancelledRestKeepsElapsedAndDoesNotIncreaseConfirmedCount()=runBlocking {
        val h=Harness();h.start();h.advance(1000);h.care.beginPetRest();h.care.toggleRestCheck(PetRestCheck.WALK)
        assertTrue(h.care.cancelPetRest());assertEquals(1000L,h.session().elapsedSinceRestMillis)
        assertEquals(0,h.session().confirmedRests);assertTrue(h.session().paused);assertTrue(h.session().checks.isEmpty())
    }
    @Test fun movingActionsFailEvenWhenSessionClockCanRun()=runBlocking {
        val h=Harness();h.start();h.advance(1000,MotionState.MOVING)
        assertFalse(h.care.beginPetRest());assertFalse(h.care.pauseRestSession());assertFalse(h.care.stopRestSession())
        h.advance(1000);assertFalse(h.session().paused);assertEquals(2000L,h.session().elapsedTotalMillis)
    }
    @Test fun modeOffCancelsTimerPreservesIntervalAndOtherPetData()=runBlocking {
        val h=Harness();h.start();h.care.addMemo("Owner reminder");h.care.setEnabled(false)
        assertNull(h.care.uiState.value.data.restSession);assertEquals(15,h.care.uiState.value.data.restIntervalMinutes)
        assertEquals("Owner reminder",h.care.uiState.value.data.memos.single().text)
        assertTrue(h.care.setEnabled(true));assertNull(h.care.uiState.value.data.restSession)
    }
    @Test fun petDeleteRemovesTimerAndIntervalWithoutChangingParent()=runBlocking {
        val h=Harness();h.start();val parent=h.trip;assertTrue(h.care.deleteAll())
        assertEquals(PetTravelData(),h.care.uiState.value.data);assertEquals(parent,h.trip)
    }
    @Test fun checkpointReopenRestoresOnlySavedObservedTimeAndPauses()=runBlocking {
        val h=Harness();h.start();repeat(16){h.advance(1000)}
        assertEquals(16_000L,h.session().elapsedTotalMillis)
        val reopened=h.coordinator(FilePetTravelRepository(h.dir,RuntimeMode.PRODUCTION,testEncryption(RuntimeMode.PRODUCTION)))
        reopened.initialize();val session=requireNotNull(reopened.uiState.value.data.restSession)
        assertEquals(15_000L,session.elapsedTotalMillis);assertTrue(session.paused);assertEquals("Rest pet",reopened.uiState.value.data.profile?.name)
        assertTrue(reopened.resumeRestSession())
    }
    @Test fun unrelatedPetEditFlushesPendingClockBeforeWriting()=runBlocking {
        val h=Harness();h.start();h.advance(1000);h.care.addMemo("Owner reminder")
        assertEquals(1000L,h.repo.state.value.data.restSession?.elapsedTotalMillis)
        h.advance(1000);assertEquals(2000L,h.session().elapsedTotalMillis);assertEquals(1,h.care.uiState.value.data.memos.size)
    }
    @Test fun profileCannotChangeIdentityDuringRestSession()=runBlocking {
        val h=Harness();h.start();assertFalse(h.care.saveProfile("Other pet",PetKind.DOG,true))
        h.care.stopRestSession();assertTrue(h.care.saveProfile("Other pet",PetKind.DOG,true))
    }
    @Test fun failedRecoveryCheckpointKeepsCiphertextAndHidesPrivateStateUntilRetry()=runBlocking {
        val h=Harness();h.start();val file=File(h.dir,"pet-travel.v1.bin");val before=file.readBytes()
        val faulty=FilePetTravelRepository(h.dir,RuntimeMode.PRODUCTION,testEncryption(RuntimeMode.PRODUCTION)) {throw IOException("fixed failure")}
        val broken=h.coordinator(faulty);broken.initialize()
        assertEquals(StorageError.IO,broken.uiState.value.error);assertFalse(broken.uiState.value.contentAllowed)
        assertNull(broken.uiState.value.data.profile);assertArrayEquals(before,file.readBytes())
        h.care.initialize();assertNull(h.care.uiState.value.error);assertTrue(h.session().paused)
    }
    @Test fun productionTimerRejectsSimulatedMotion()=runBlocking {
        val h=Harness();h.start();val original=h.context.motion.observation as Reading.Observed
        val spoof=original.copy(metadata=original.metadata.copy(source=DataSource.SIMULATED))
        h.context=h.context.copy(motion=spoof.evaluate(h.now,ContextFreshnessPolicies().occupancy,h.context.connection,true))
        h.care.refresh();assertFalse(h.care.uiState.value.contentAllowed)
        h.advance(1000);assertTrue(h.session().paused);assertEquals(0L,h.session().elapsedTotalMillis)
    }
    @Test fun pureTimerCrossesDeadlineAndNeverAutoConfirmsRest() {
        val timer=PetRestTimer();var now=localNow
        timer.adopt(PetRestSession("id",now.epochMillis,15),localContext(now),now)
        repeat(900) {now=ObservationTime(now.epochMillis+1000,now.monotonicMillis+1000);timer.tick(localContext(now),now)}
        assertTrue(requireNotNull(timer.value).due);assertEquals(900_000L,timer.value?.elapsedSinceRestMillis)
        assertEquals(0,timer.value?.confirmedRests)
    }
    @Test fun oldSchemaOneReadsWithoutRewriteAndPreservesAllFieldsOnNewWrite()=runBlocking {
        val h=Harness()
        // Exact pre-existing v1 layout, independently written as a compatibility fixture.
        val old=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeUTF("Roadmate-pet");out.writeInt(1);out.writeUTF("PRODUCTION")
            out.writeBoolean(true);out.writeBoolean(true);out.writeBoolean(true);out.writeUTF("Legacy pet");out.writeInt(0)
            out.writeInt(0);out.writeInt(0);out.writeBoolean(false);out.writeInt(0)
            out.writeInt(1);out.writeUTF("memo");out.writeUTF("Legacy memo");out.writeBoolean(false);out.writeBoolean(false)
            out.writeInt(0);out.writeInt(1);out.writeInt(0);out.writeInt(0)
        }}.toByteArray()
        val file=File(h.dir,"pet-travel.v1.bin");val encrypted=testEncryption(RuntimeMode.PRODUCTION).seal(old);file.writeBytes(encrypted)
        h.care.initialize();assertArrayEquals(encrypted,file.readBytes());assertEquals("Legacy pet",h.care.uiState.value.data.profile?.name)
        assertNull(h.care.uiState.value.data.restSession);assertTrue(h.care.chooseRestInterval(30));assertTrue(h.care.startRestSession(true))
        val reopened=h.coordinator(FilePetTravelRepository(h.dir,RuntimeMode.PRODUCTION,testEncryption(RuntimeMode.PRODUCTION)))
        reopened.initialize();val data=reopened.uiState.value.data
        assertEquals("Legacy memo",data.memos.single().text);assertEquals(setOf(PetPassReward.WELCOME_BADGE),data.claimed)
        assertEquals(30,data.restSession?.intervalMinutes);assertTrue(data.restSession?.paused==true)
    }
}
