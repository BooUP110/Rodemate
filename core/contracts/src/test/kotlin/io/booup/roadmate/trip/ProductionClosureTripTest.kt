package io.booup.roadmate.trip

import io.booup.roadmate.context.MotionClassifier
import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

/** Deterministic default-window regression; separate from wall-clock AVD E2E. */
class ProductionClosureTripTest {
    private val classifier = MotionClassifier()
    private fun evidence(ms: Long, speed: Double?): TripEvidence {
        val now = ObservationTime(1_000_000 + ms, ms)
        val raw = speed?.let { Reading.Observed(it, DataMetadata(DataSource.SYSTEM, now.epochMillis,
            Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, ms)) }
            ?: Reading.Unavailable(UnavailableReason.SOURCE_ERROR, DataSource.SYSTEM)
        val motion = (classifier.classify(raw, now) as? Reading.Observed)?.value
        return TripEvidence(now, motion, DataSource.SYSTEM, ms + 3000, 1)
    }
    @Test fun defaultWindowsProduceOnePartialTripWithoutInventedDistance() {
        val tracker = TripTracker(RuntimeMode.PRODUCTION, TripIdSource { "actual-input-contract" })
        for (ms in 0L..9_000 step 1000) assertEquals(TripState.START_CANDIDATE, tracker.update(evidence(ms, 2.78)).state)
        assertEquals(TripState.IN_PROGRESS, tracker.update(evidence(10_000, 2.78)).state)
        val id = tracker.status.active!!.tripId
        for (ms in 11_000L..15_000 step 1000) assertEquals(TripState.STOP_CANDIDATE, tracker.update(evidence(ms, 0.0)).state)
        tracker.update(evidence(16_000, -2.78))
        assertEquals(id, tracker.status.active!!.tripId)
        assertEquals(TripState.IN_PROGRESS, tracker.status.state)
        for (ms in 17_000L..136_000 step 1000) assertEquals(TripState.STOP_CANDIDATE, tracker.update(evidence(ms, 0.0)).state)
        val completed = tracker.update(evidence(137_000, 0.0)).completed!!
        assertEquals(id, completed.tripId); assertNull(completed.distanceMeters)
        assertEquals(TripCompletionReason.CONFIRMED_STATIONARY, completed.completionReason)
        assertEquals(DataSource.DERIVED, completed.source)
        assertEquals(TripDataQuality.PARTIAL, completed.quality)
    }
    @Test fun unavailableStopBreaksWindowInsteadOfCompleting() {
        val tracker = TripTracker(RuntimeMode.PRODUCTION, TripIdSource { "gap" })
        for (ms in 0L..10_000 step 1000) tracker.update(evidence(ms, 1.0))
        for (ms in 11_000L..120_000 step 1000) tracker.update(evidence(ms, 0.0))
        tracker.update(evidence(121_000, null))
        for (ms in 122_000L..240_000 step 1000) tracker.update(evidence(ms, 0.0))
        assertNull(tracker.status.completed)
        assertTrue(tracker.status.active!!.hasGap)
    }
}
