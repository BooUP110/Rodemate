package io.booup.roadmate.trip

import io.booup.roadmate.care.*
import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class TripTrackerTest {
    private var serial = 0
    private fun tracker(mode: RuntimeMode = RuntimeMode.DEMO) = TripTracker(mode, TripIdSource { "trip-${++serial}" }, TripConfig(10, 20))
    private fun evidence(t: Long, motion: MotionState? = MotionState.MOVING, counter: Long? = t,
        location: Boolean = true, session: Long = 1) = TripEvidence(time(t), motion, DataSource.SIMULATED,
        time(t).monotonicMillis + 100, session, counter,
        if (location) TripLocation("place-$t", DataSource.SIMULATED) else null)
    private fun started(tracker: TripTracker) { tracker.update(evidence(0)); tracker.update(evidence(10)) }
    private fun complete(tracker: TripTracker): TripRecord {
        started(tracker); tracker.update(evidence(11, MotionState.STATIONARY))
        return tracker.update(evidence(31, MotionState.STATIONARY)).completed!!
    }
    @Test fun idleToCandidateRequiresSustainedMotion() {
        val t = tracker(); assertEquals(TripState.IDLE, t.status.state)
        assertEquals(TripState.START_CANDIDATE, t.update(evidence(0)).state)
        assertEquals(TripState.START_CANDIDATE, t.update(evidence(9)).state)
        assertEquals(TripState.IN_PROGRESS, t.update(evidence(10)).state)
        assertEquals(1_000_000L, t.status.active!!.startedAtEpochMillis)
    }
    @Test fun singleMovingSampleDoesNotStartTrip() {
        val t = tracker(); t.update(evidence(0)); t.update(evidence(1, MotionState.STATIONARY))
        assertEquals(TripState.IDLE, t.status.state); assertNull(t.status.active)
    }
    @Test fun shortStopReturnsToSameActiveTrip() {
        val t = tracker(); started(t); val id = t.status.active!!.tripId
        assertEquals(TripState.STOP_CANDIDATE, t.update(evidence(11, MotionState.STATIONARY)).state)
        assertEquals(TripState.IN_PROGRESS, t.update(evidence(20)).state)
        assertEquals(id, t.status.active!!.tripId); assertNull(t.status.completed)
    }
    @Test fun sustainedStopCompletesExactlyOnce() {
        val t = tracker(); val record = complete(t)
        assertEquals(TripState.COMPLETED, t.update(evidence(32, MotionState.STATIONARY)).state)
        assertEquals(record, t.status.completed)
        assertEquals(TripCompletionReason.CONFIRMED_STATIONARY, record.completionReason)
        assertEquals(31L, record.durationMillis); assertEquals(10L, record.confirmedDrivingMillis)
        assertEquals(TripDataQuality.COMPLETE, record.quality)
    }
    @Test fun unknownEnumCannotBeTreatedAsStationary() {
        val t = tracker(); t.update(evidence(0, MotionState.UNKNOWN)); assertEquals(TripState.IDLE, t.status.state)
        started(t); t.update(evidence(11, MotionState.UNKNOWN)); t.update(evidence(100, MotionState.UNKNOWN))
        assertEquals(TripState.IN_PROGRESS, t.status.state); assertTrue(t.status.recoveryRequired)
    }
    @Test fun unavailableEvidenceDoesNotEndActiveTrip() {
        val t = tracker(); started(t); t.update(evidence(200, null))
        assertNotNull(t.status.active); assertNull(t.status.completed)
    }
    @Test fun staleMovingEvidenceCannotStart() {
        val t = tracker(); val e = evidence(0).copy(validUntilMonotonicMillis = time(0).monotonicMillis - 1)
        assertEquals(TripState.IDLE, t.update(e).state)
    }
    @Test fun staleStopEvidenceCannotComplete() {
        val t = tracker(); started(t); t.update(evidence(11, MotionState.STATIONARY))
        t.update(evidence(31, MotionState.STATIONARY).copy(validUntilMonotonicMillis = 0))
        assertNull(t.status.completed); assertTrue(t.status.active!!.hasGap)
    }
    @Test fun expiredIntervalDoesNotCountAsStopTime() {
        val t = tracker(); started(t); t.update(evidence(11, MotionState.STATIONARY))
        assertEquals(TripState.STOP_CANDIDATE, t.update(evidence(1_000, MotionState.STATIONARY)).state)
        assertNull(t.status.completed); assertEquals(11L, t.status.active!!.accumulatedDurationMillis)
    }
    @Test fun sessionChangeResetsStopContinuity() {
        val t = tracker(); started(t); t.update(evidence(11, MotionState.STATIONARY))
        assertEquals(TripState.STOP_CANDIDATE, t.update(evidence(31, MotionState.STATIONARY, session = 2)).state)
    }
    @Test fun epochRollbackDoesNotComplete() {
        val t = tracker(); started(t)
        t.update(evidence(11, MotionState.STATIONARY).copy(now = ObservationTime(999_000, 100_011)))
        assertEquals(TripState.IN_PROGRESS, t.status.state); assertNull(t.status.completed)
    }
    @Test fun restartPreservesIdAndRequiresNewStopWindow() {
        val t = tracker(); started(t); t.update(evidence(11, MotionState.STATIONARY))
        val saved = t.status.active!!; val restored = tracker(); restored.restore(saved)
        assertTrue(restored.status.recoveryRequired)
        restored.update(evidence(10_000, MotionState.STATIONARY))
        assertNull(restored.status.completed)
        val r = restored.update(evidence(10_020, MotionState.STATIONARY)).completed!!
        assertEquals(saved.tripId, r.tripId); assertEquals(31L, r.durationMillis)
        assertNull(r.distanceMeters); assertEquals(TripDataQuality.PARTIAL, r.quality)
    }
    @Test fun explicitEndStillRequiresFreshStationaryMotion() {
        val t = tracker(); started(t)
        assertNull(t.update(evidence(11).copy(explicitEnd = true)).completed)
        assertEquals(TripCompletionReason.EXPLICIT_END_EVIDENCE,
            t.update(evidence(12, MotionState.STATIONARY).copy(explicitEnd = true)).completed!!.completionReason)
    }
    @Test fun missingDataStaysNullAndPartial() {
        val t = tracker()
        listOf(0L, 10L, 11L, 31L).forEach { t.update(evidence(it, if (it < 11) MotionState.MOVING else MotionState.STATIONARY, null, false)) }
        val r = t.status.completed!!; assertNull(r.startLocation); assertNull(r.endLocation)
        assertNull(r.distanceMeters); assertNull(r.routeSummary); assertEquals(TripDataQuality.PARTIAL, r.quality)
    }
    @Test fun absentLocationDoesNotDiscardKnownDistanceOrInventRoute() {
        val t = tracker()
        listOf(0L, 10L, 11L, 31L).forEach { t.update(evidence(it, if (it < 11) MotionState.MOVING else MotionState.STATIONARY, location = false)) }
        val r = t.status.completed!!; assertEquals(31L, r.distanceMeters); assertNull(r.routeSummary)
        assertEquals(TripDataQuality.PARTIAL, r.quality)
    }
    @Test fun absentDistanceKeepsKnownEndpointsWithUnknownRouteDistance() {
        val t = tracker()
        listOf(0L, 10L, 11L, 31L).forEach { t.update(evidence(it, if (it < 11) MotionState.MOVING else MotionState.STATIONARY, counter = null)) }
        val r = t.status.completed!!; assertNotNull(r.routeSummary); assertNull(r.routeSummary!!.distanceMeters)
        assertEquals(TripDataQuality.PARTIAL, r.quality)
    }
    @Test fun distanceCounterResetInvalidatesDistanceInsteadOfInventingIt() {
        val t = tracker(); started(t); t.update(evidence(11, counter = 1))
        assertNull(t.status.active!!.distanceMeters)
        t.update(evidence(12, counter = 100)); assertNull(t.status.active!!.distanceMeters)
    }
    @Test fun demoManualTripIsMarkedAndMinimal() {
        val t = tracker(); assertTrue(t.manualStart(time(0))); assertTrue(t.manualStop(time(1)))
        assertEquals(DataSource.SIMULATED, t.status.completed!!.source)
        assertEquals(TripCompletionReason.MANUAL_DEMO, t.status.completed!!.completionReason)
        assertEquals(TripDataQuality.MINIMAL, t.status.completed!!.quality)
    }
    @Test fun productionRejectsManualAndSimulatedEvidence() {
        val t = tracker(RuntimeMode.PRODUCTION); assertFalse(t.manualStart(time(0))); assertFalse(t.manualStop(time(1)))
        t.update(evidence(0)); t.update(evidence(10)); assertEquals(TripState.IDLE, t.status.state)
    }
    @Test fun freshProductionEvidenceUsesDerivedRecordSource() {
        val t = tracker(RuntimeMode.PRODUCTION)
        t.update(evidence(0, location = false).copy(source = DataSource.VEHICLE_SDK))
        t.update(evidence(10, location = false).copy(source = DataSource.VEHICLE_SDK))
        assertEquals(DataSource.DERIVED, t.status.active!!.source)
    }
    @Test fun secondTripGetsDistinctId() {
        val t = tracker(); val first = complete(t)
        t.update(evidence(32)); t.update(evidence(42)); assertNotEquals(first.tripId, t.status.active!!.tripId)
    }
    @Test fun invalidConfigRejected() {
        assertThrows(IllegalArgumentException::class.java) { TripConfig(0, 20) }
        assertThrows(IllegalArgumentException::class.java) { TripPersistenceConfig(0) }
    }
    @Test fun mapperUsesMotionInsteadOfUxRestriction() {
        val f = frame().copy(motion = observed(MotionState.STATIONARY), restrictions = observed(DrivingRestrictions(UIMode.RESTRICTED)))
        assertEquals(MotionState.STATIONARY, TripEvidenceMapper().map(f.context(), time()).motion)
    }
    @Test fun mapperRejectsDisconnectedAndBackgroundAndExpiredContext() {
        val mapper = TripEvidenceMapper()
        assertNull(mapper.map(frame().copy(connection = ConnectionState()).context(), time()).motion)
        assertNull(mapper.map(frame().context(foreground = false), time()).motion)
        assertNull(mapper.map(frame().context(), time(100_000)).motion)
    }
}
