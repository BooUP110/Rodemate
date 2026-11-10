package io.booup.roadmate.trip

import io.booup.roadmate.testing.encryptedTestRepository
import io.booup.roadmate.care.*
import io.booup.roadmate.model.*
import io.booup.roadmate.storage.FileTripRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TripCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = TestCareClock()
    private var serial = 0
    private fun coordinator(dir: File, mode: RuntimeMode = RuntimeMode.DEMO) = TripCoordinator(
        encryptedTestRepository(dir, mode), mode, clock, TripIdSource { "trip-${++serial}" }, TripConfig(10, 20))
    private fun context(t: Long, motion: MotionState = MotionState.STATIONARY, ux: UIMode = UIMode.PARKED) = frame(t).copy(
        motion = observed(motion, t), restrictions = observed(DrivingRestrictions(ux, 0, false), t)).context(t)
    private suspend fun finish(c: TripCoordinator) {
        listOf(0L, 10L, 11L, 31L).forEach { t ->
            clock.elapsed = t; c.update(context(t, if (t < 11) MotionState.MOVING else MotionState.STATIONARY), CareDecision())
        }
    }
    @Test fun sessionOnlyTripDoesNotPersistWithoutHistoryConsent() = runBlocking {
        val dir = temporary.newFolder(); val c = coordinator(dir); c.initialize(); finish(c)
        assertEquals(TripState.COMPLETED, c.uiState.value.runtime.state)
        assertTrue(c.uiState.value.storage.trips.isEmpty()); assertFalse(File(dir, "trips.v1.bin").exists())
    }
    @Test fun completedTripPersistsAndReopensWithFreshPostDriveGate() = runBlocking {
        val dir = temporary.newFolder(); val c = coordinator(dir); c.initialize(); c.consent(ConsentScope.TRIP_HISTORY, true); finish(c)
        assertEquals(PostDriveState.READY, c.uiState.value.postDrive)
        val reopened = coordinator(dir); reopened.initialize()
        assertEquals(PostDriveState.NOT_READY, reopened.uiState.value.postDrive)
        clock.elapsed = 1_000; reopened.update(context(1_000), CareDecision())
        assertEquals(TripState.COMPLETED, reopened.uiState.value.runtime.state)
        assertEquals(PostDriveState.READY, reopened.uiState.value.postDrive)
        assertEquals(1, reopened.uiState.value.storage.trips.size)
    }
    @Test fun activeSnapshotRecoversWithoutCountingProcessGapAsStop() = runBlocking {
        val dir = temporary.newFolder(); val c = coordinator(dir); c.initialize(); c.consent(ConsentScope.TRIP_HISTORY, true)
        clock.elapsed = 0; c.update(context(0, MotionState.MOVING), CareDecision())
        clock.elapsed = 10; c.update(context(10, MotionState.MOVING), CareDecision())
        val id = c.uiState.value.runtime.active!!.tripId
        val reopened = coordinator(dir); reopened.initialize(); assertTrue(reopened.uiState.value.runtime.recoveryRequired)
        clock.elapsed = 100_000; reopened.update(context(100_000), CareDecision())
        assertEquals(TripState.STOP_CANDIDATE, reopened.uiState.value.runtime.state)
        assertEquals(id, reopened.uiState.value.runtime.active!!.tripId)
        assertEquals(PostDriveState.NOT_READY, reopened.uiState.value.postDrive)
    }
    @Test fun deletionUpdatesPostDriveAndDoesNotResurrectOnNextObservation() = runBlocking {
        val c = coordinator(temporary.newFolder()); c.initialize(); c.consent(ConsentScope.TRIP_HISTORY, true); finish(c)
        c.delete(c.uiState.value.storage.trips.single().tripId)
        assertEquals(PostDriveState.DELETED, c.uiState.value.postDrive)
        clock.elapsed = 32; c.update(context(32), CareDecision())
        assertEquals(PostDriveState.DELETED, c.uiState.value.postDrive); assertTrue(c.uiState.value.storage.trips.isEmpty())
    }
    @Test fun clearActiveHistoryInvalidatesInMemoryTrip() = runBlocking {
        val c = coordinator(temporary.newFolder()); c.initialize(); c.consent(ConsentScope.TRIP_HISTORY, true)
        c.manual(true); assertNotNull(c.uiState.value.storage.active); c.clearHistory()
        assertNull(c.uiState.value.runtime.active); assertNull(c.uiState.value.storage.active)
        clock.elapsed = 1; c.update(context(1), CareDecision()); assertTrue(c.uiState.value.storage.trips.isEmpty())
    }
    @Test fun uiClickGateRechecksFreshnessAndRestriction() = runBlocking {
        val c = coordinator(temporary.newFolder()); c.initialize()
        assertFalse(c.userControlsAllowed()); c.update(context(0), CareDecision()); assertTrue(c.userControlsAllowed())
        clock.elapsed = 5_001; assertFalse(c.userControlsAllowed())
        c.update(context(5_001, ux = UIMode.RESTRICTED), CareDecision()); assertFalse(c.userControlsAllowed())
    }
    @Test fun productionRejectsManualRuntimeAndHasNoImplicitConsent() = runBlocking {
        val c = coordinator(temporary.newFolder(), RuntimeMode.PRODUCTION); c.initialize()
        assertFalse(c.manual(true)); assertTrue(c.uiState.value.storage.consents.isEmpty())
    }
    @Test fun careOccurrencePersistsOnlyEmittedIdsNotActualActionsOrRawContext() = runBlocking {
        val dir = temporary.newFolder(); val c = coordinator(dir); c.initialize(); c.consent(ConsentScope.TRIP_HISTORY, true)
        val event = CareEvent("event", CareKind.REST, CarePriority.P2_SUGGESTION, DataSource.SIMULATED,
            1_000_000, RuleId.REST, CareCategory.REST, emptyList(), "care.rest.recommendation", subject = "rest")
        c.update(context(0, MotionState.MOVING), CareDecision(emittedEvents = listOf(event)))
        clock.elapsed = 10; c.update(context(10, MotionState.MOVING), CareDecision(activeEvent = event, emittedEvents = listOf(event)))
        clock.elapsed = 11; c.update(context(11), CareDecision(activeEvent = event))
        clock.elapsed = 31; c.update(context(31), CareDecision())
        val linked = c.uiState.value.storage.trips.single().careEvents
        assertEquals(1, linked.size); assertEquals("event", linked.single().eventId)
        val text = File(dir, "trips.v1.bin").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(text.contains("VehicleContext")); assertFalse(text.contains("rested"))
    }
}

class PostDriveEligibilityTest {
    private fun context(motion: MotionState = MotionState.STATIONARY, mode: UIMode = UIMode.PARKED) = frame().copy(
        motion = observed(motion), restrictions = observed(DrivingRestrictions(mode, 0, false))).context()
    private val gate = PostDriveEligibility()
    @Test fun onlyCompletedRuntimeCanUnlock() {
        TripState.entries.filter { it != TripState.COMPLETED }.forEach {
            assertEquals(PostDriveState.NOT_READY, gate.evaluate(record(), it, context(), time()))
        }
        assertEquals(PostDriveState.READY, gate.evaluate(record(), TripState.COMPLETED, context(), time()))
    }
    @Test fun missingTripNeverUnlocks() { assertEquals(PostDriveState.NOT_READY, gate.evaluate(null, TripState.COMPLETED, context(), time())) }
    @Test fun unknownOrMovingMotionNeverUnlocks() {
        listOf(MotionState.UNKNOWN, MotionState.MOVING).forEach {
            assertEquals(PostDriveState.NOT_READY, gate.evaluate(record(), TripState.COMPLETED, context(it), time()))
        }
    }
    @Test fun restrictedAndUnknownUxNeverUnlock() {
        listOf(UIMode.RESTRICTED, UIMode.UNKNOWN_RESTRICTED).forEach {
            assertEquals(PostDriveState.NOT_READY, gate.evaluate(record(), TripState.COMPLETED, context(mode = it), time()))
        }
    }
    @Test fun staleBackgroundAndDisconnectedNeverUnlock() {
        val c = context()
        assertEquals(PostDriveState.NOT_READY, gate.evaluate(record(), TripState.COMPLETED, c, time(5_001)))
        assertEquals(PostDriveState.NOT_READY, gate.evaluate(record(), TripState.COMPLETED, c.copy(foreground = false), time()))
        assertEquals(PostDriveState.NOT_READY, gate.evaluate(record(), TripState.COMPLETED, c.copy(connection = ConnectionState()), time()))
    }
    @Test fun reviewedStateAndDeletedStateRemainDistinct() {
        assertEquals(PostDriveState.ALREADY_REVIEWED, gate.evaluate(record().copy(reviewed = true), TripState.COMPLETED, context(), time()))
        assertEquals(PostDriveState.DELETED, gate.evaluate(record(), TripState.COMPLETED, context(), time(), true))
    }
}
