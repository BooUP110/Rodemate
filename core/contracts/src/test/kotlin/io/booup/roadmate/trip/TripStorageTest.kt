package io.booup.roadmate.trip

import io.booup.roadmate.model.*
import io.booup.roadmate.storage.FileTripRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

internal fun record(id: String = "trip") = TripRecord(id, TripState.COMPLETED, 100, 200, DataSource.SIMULATED,
    100, 50, completionReason = TripCompletionReason.CONFIRMED_STATIONARY,
    quality = TripDataQuality.PARTIAL, createdAtEpochMillis = 200,
    careEvents = listOf(TripCareOccurrence(id, "event-$id", RuleId.REST, CarePriority.P2_SUGGESTION, 150, DataSource.SIMULATED)))
internal fun active(id: String = "active") = ActiveTripSnapshot(id, TripState.STOP_CANDIDATE,
    300, 350, 50, 40, null, null, DataSource.SIMULATED, false)
internal fun memory(tripId: String = "trip", id: String = "memory") = PersistentEmotionalMemory(id, tripId,
    "Explicit user memory", 250, DataSource.SIMULATED, true)
internal fun success(result: StorageResult<Unit>) { assertEquals(StorageResult.Success(Unit), result) }
internal fun failure(error: StorageError, result: StorageResult<Unit>) { assertEquals(StorageResult.Failure(error), result) }

class TripStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun directory() = temporary.newFolder()
    private fun repo(dir: File) = FileTripRepository(dir, RuntimeMode.DEMO)
    private suspend fun granted(dir: File): FileTripRepository = repo(dir).also {
        success(it.load()); success(it.setConsent(ConsentScope.TRIP_HISTORY, true, 1, DataSource.SIMULATED))
    }
    private suspend fun emotion(r: FileTripRepository, granted: Boolean = true, t: Long = 2) =
        success(r.setConsent(ConsentScope.EMOTIONAL_MEMORY, granted, t, DataSource.SIMULATED))
    @Test fun diskRoundTripReopensActualFileWithPartialRecordAndCareLink() = runBlocking {
        val dir = directory(); val first = granted(dir); success(first.save(record(), 0))
        assertTrue(File(dir, "trips.v1.bin").length() > 0)
        val reopened = repo(dir); success(reopened.load()); assertEquals(listOf(record()), reopened.state.value.trips)
        assertNull(reopened.state.value.trips.single().distanceMeters)
        assertEquals("trip", reopened.state.value.trips.single().careEvents.single().tripId)
    }
    @Test fun absentHistoryConsentRejectsDurableSave() = runBlocking {
        val dir = directory(); val r = repo(dir); success(r.load())
        failure(StorageError.CONSENT_REQUIRED, r.save(record(), 0))
        assertFalse(File(dir, "trips.v1.bin").exists()); assertTrue(r.state.value.trips.isEmpty())
    }
    @Test fun activeCheckpointReopensThenCompletionReplacesItAtomically() = runBlocking {
        val dir = directory(); val r = granted(dir); success(r.checkpoint(active(), 0))
        val reopened = repo(dir); success(reopened.load()); assertEquals(active(), reopened.state.value.active)
        val completed = record("active").copy(startedAtEpochMillis = 300, endedAtEpochMillis = 400,
            createdAtEpochMillis = 400, careEvents = emptyList())
        success(reopened.save(completed, 0)); val third = repo(dir); success(third.load())
        assertNull(third.state.value.active); assertEquals(listOf(completed), third.state.value.trips)
    }
    @Test fun duplicateIdDoesNotOverwriteExistingRecord() = runBlocking {
        val dir = directory(); val r = granted(dir); success(r.save(record(), 0))
        failure(StorageError.DUPLICATE_ID, r.save(record().copy(distanceMeters = 99), 0))
        val reopened = repo(dir); success(reopened.load()); assertEquals(listOf(record()), reopened.state.value.trips)
    }
    @Test fun singleDeleteCascadesMemoryAndEmbeddedCareButKeepsOtherTripAndConsent() = runBlocking {
        val dir = directory(); val r = granted(dir); emotion(r)
        success(r.save(record(), 0)); success(r.save(record("other"), 0)); success(r.saveMemory(memory()))
        success(r.saveMemory(memory("other", "other-memory"))); success(r.delete("trip"))
        val reopened = repo(dir); success(reopened.load()); val s = reopened.state.value
        assertEquals(listOf("other"), s.trips.map { it.tripId }); assertEquals(listOf("other"), s.memories.map { it.tripId })
        assertTrue(s.consents[ConsentScope.TRIP_HISTORY]!!.granted); assertEquals(1L, s.captureGeneration)
    }
    @Test fun deletingActiveTripRemovesSnapshotAndRejectsStaleCheckpoint() = runBlocking {
        val r = granted(directory()); success(r.checkpoint(active(), 0)); success(r.delete("active"))
        failure(StorageError.NOT_FOUND, r.checkpoint(active(), 0)); assertNull(r.state.value.active)
    }
    @Test fun clearHistoryRemovesCompletedActiveAndMemoriesAndPreservesConsent() = runBlocking {
        val dir = directory(); val r = granted(dir); emotion(r); success(r.save(record(), 0))
        success(r.saveMemory(memory())); success(r.checkpoint(active(), 0)); success(r.clearHistory())
        val reopened = repo(dir); success(reopened.load()); val s = reopened.state.value
        assertTrue(s.trips.isEmpty()); assertTrue(s.memories.isEmpty()); assertNull(s.active)
        assertTrue(s.consents[ConsentScope.EMOTIONAL_MEMORY]!!.granted)
        failure(StorageError.NOT_FOUND, r.save(record(), 0)); failure(StorageError.NOT_FOUND, r.checkpoint(active(), 0))
    }
    @Test fun memoryRequiresSeparateScopeAndPerItemConfirmation() = runBlocking {
        val dir = directory(); val r = granted(dir); success(r.save(record(), 0))
        failure(StorageError.CONSENT_REQUIRED, r.saveMemory(memory()))
        emotion(r); failure(StorageError.CONSENT_REQUIRED, r.saveMemory(memory().copy(confirmedByUser = false)))
        success(r.saveMemory(memory())); val reopened = repo(dir); success(reopened.load())
        assertEquals(listOf(memory()), reopened.state.value.memories)
    }
    @Test fun emotionalRevocationPurgesMemoriesKeepsTripsAndBlocksFutureSaveAfterReopen() = runBlocking {
        val dir = directory(); val r = granted(dir); emotion(r); success(r.save(record(), 0)); success(r.saveMemory(memory()))
        emotion(r, false, 3); val reopened = repo(dir); success(reopened.load())
        assertTrue(reopened.state.value.memories.isEmpty()); assertEquals(1, reopened.state.value.trips.size)
        failure(StorageError.CONSENT_REQUIRED, reopened.saveMemory(memory()))
    }
    @Test fun historyRevocationDeletesAllTripRelatedDataAndInvalidatesCapture() = runBlocking {
        val dir = directory(); val r = granted(dir); emotion(r); success(r.save(record(), 0)); success(r.saveMemory(memory()))
        success(r.checkpoint(active(), 0)); success(r.setConsent(ConsentScope.TRIP_HISTORY, false, 3, DataSource.SIMULATED))
        assertTrue(r.state.value.trips.isEmpty()); assertNull(r.state.value.active); assertTrue(r.state.value.memories.isEmpty())
        failure(StorageError.NOT_FOUND, r.checkpoint(active(), 0))
        success(r.setConsent(ConsentScope.TRIP_HISTORY, true, 4, DataSource.SIMULATED))
        failure(StorageError.NOT_FOUND, r.save(record(), 0))
    }
    @Test fun unrelatedScopeRevocationDoesNotDeleteTripsOrEmotionalConsent() = runBlocking {
        val r = granted(directory()); emotion(r); success(r.save(record(), 0))
        listOf(ConsentScope.FUNCTIONAL_PREFERENCE, ConsentScope.PHOTO_METADATA, ConsentScope.SYNC).forEach {
            success(r.setConsent(it, false, 3, DataSource.SIMULATED))
        }
        assertEquals(1, r.state.value.trips.size); assertTrue(r.state.value.consents[ConsentScope.EMOTIONAL_MEMORY]!!.granted)
    }
    @Test fun memoryCannotReferenceDeletedOrNonexistentParent() = runBlocking {
        val r = granted(directory()); emotion(r)
        failure(StorageError.NOT_FOUND, r.saveMemory(memory()))
    }
    @Test fun eventCannotReferenceAnotherTripOrLieOutsideTripTime() = runBlocking {
        val r = granted(directory()); val original = record()
        failure(StorageError.INVALID_DATA, r.save(original.copy(careEvents = listOf(original.careEvents.single().copy(tripId = "other"))), 0))
        failure(StorageError.INVALID_DATA, r.save(original.copy(careEvents = listOf(original.careEvents.single().copy(occurredAtEpochMillis = 201))), 0))
    }
    @Test fun invalidChronologyNegativeDistanceAndFakeCompleteAreRejected() = runBlocking {
        val r = granted(directory())
        failure(StorageError.INVALID_DATA, r.save(record().copy(endedAtEpochMillis = 99), 0))
        failure(StorageError.INVALID_DATA, r.save(record().copy(distanceMeters = -1), 0))
        failure(StorageError.INVALID_DATA, r.save(record().copy(quality = TripDataQuality.COMPLETE), 0))
        failure(StorageError.INVALID_DATA, r.save(record().copy(confirmedDrivingMillis = 101), 0))
        assertTrue(r.state.value.loaded); assertNull(r.state.value.error)
    }
    @Test fun separateInstancesReloadBeforeMutatingInsteadOfLosingExistingTrips() = runBlocking {
        val dir = directory(); val first = granted(dir); val second = repo(dir); success(second.load())
        success(first.save(record("one"), 0)); success(second.save(record("two"), 0))
        val third = repo(dir); success(third.load()); assertEquals(listOf("one", "two"), third.state.value.trips.map { it.tripId })
    }
    @Test fun deletedGenerationRejectsAnotherInstancesStaleSave() = runBlocking {
        val dir = directory(); val first = granted(dir); val second = repo(dir); success(second.load())
        success(first.clearHistory()); failure(StorageError.NOT_FOUND, second.checkpoint(active(), 0))
        assertEquals(1L, second.state.value.captureGeneration); assertNull(second.state.value.active)
    }
    @Test fun unknownSchemaFailsClosedWithoutDestructiveReset() = runBlocking {
        val dir = directory(); val file = File(dir, "trips.v1.bin")
        DataOutputStream(FileOutputStream(file)).use { it.writeUTF("ROADmate-local"); it.writeInt(99) }
        val bytes = file.readBytes(); val r = repo(dir)
        failure(StorageError.INCOMPATIBLE_SCHEMA, r.load()); failure(StorageError.INCOMPATIBLE_SCHEMA, r.clearHistory())
        assertArrayEquals(bytes, file.readBytes()); assertFalse(r.state.value.loaded)
    }
    @Test fun truncatedFileFailsClosedAndKeepsOriginalBytes() = runBlocking {
        val dir = directory(); val r = granted(dir); success(r.save(record(), 0)); val file = File(dir, "trips.v1.bin")
        val broken = file.readBytes().take(12).toByteArray(); file.writeBytes(broken)
        failure(StorageError.INVALID_DATA, repo(dir).load()); assertArrayEquals(broken, file.readBytes())
    }
    @Test fun trailingBytesAreRejected() = runBlocking {
        val dir = directory(); granted(dir); File(dir, "trips.v1.bin").appendBytes(byteArrayOf(7))
        failure(StorageError.INVALID_DATA, repo(dir).load())
    }
    @Test fun abandonedStagingFileIsNeverPromotedOverCommittedHistory() = runBlocking {
        val dir = directory(); val r = granted(dir); success(r.save(record(), 0)); File(dir, "trips.pending").writeText("interrupted")
        val reopened = repo(dir); success(reopened.load()); assertEquals(listOf(record()), reopened.state.value.trips)
    }
    @Test fun failedWriteDoesNotPublishSuccessOrLoseCommittedFile() = runBlocking {
        val dir = directory(); val r = granted(dir); success(r.save(record(), 0))
        assertTrue(File(dir, "trips.pending").mkdir())
        failure(StorageError.IO, r.save(record("second"), 0))
        val reopened = repo(dir); success(reopened.load()); assertEquals(listOf(record()), reopened.state.value.trips)
    }
    @Test fun productionCannotLoadDemoFileAndSeparateDirectoriesDoNotMix() = runBlocking {
        val dir = directory(); val demo = granted(File(dir, "demo")); success(demo.save(record(), 0))
        val production = FileTripRepository(File(dir, "production"), RuntimeMode.PRODUCTION); success(production.load())
        assertTrue(production.state.value.trips.isEmpty())
        failure(StorageError.WRONG_ENVIRONMENT, FileTripRepository(File(dir, "demo"), RuntimeMode.PRODUCTION).load())
    }
    @Test fun productionRejectsSimulatedConsentAndTripFixture() = runBlocking {
        val production = FileTripRepository(directory(), RuntimeMode.PRODUCTION); success(production.load())
        failure(StorageError.INVALID_DATA, production.setConsent(ConsentScope.TRIP_HISTORY, true, 1, DataSource.SIMULATED))
        success(production.setConsent(ConsentScope.TRIP_HISTORY, true, 1, DataSource.USER_INPUT))
        failure(StorageError.WRONG_ENVIRONMENT, production.save(record(), 0))
    }
    @Test fun consentChronologyAndInternalVersionArePersisted() = runBlocking {
        val dir = directory(); val r = granted(dir); emotion(r, true, 10); emotion(r, false, 20)
        failure(StorageError.INVALID_DATA, r.setConsent(ConsentScope.EMOTIONAL_MEMORY, true, 19, DataSource.SIMULATED))
        val reopened = repo(dir); success(reopened.load()); val c = reopened.state.value.consents[ConsentScope.EMOTIONAL_MEMORY]!!
        assertEquals(10L, c.grantedAtEpochMillis); assertEquals(20L, c.revokedAtEpochMillis); assertEquals("internal-v1", c.version)
    }
    @Test fun oversizeMemoryFailsWithoutEvictingExistingData() = runBlocking {
        val r = granted(directory()); emotion(r); success(r.save(record(), 0))
        failure(StorageError.LIMIT_EXCEEDED, r.saveMemory(memory().copy(text = "x".repeat(4_097))))
        assertEquals(1, r.state.value.trips.size); assertTrue(r.state.value.memories.isEmpty())
    }
}
