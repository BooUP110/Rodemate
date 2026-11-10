package io.booup.roadmate.context

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class ContinuousDrivingTrackerTest {
    private val tracker = ContinuousDrivingTracker()

    private fun observe(millis: Long, motion: MotionState = MotionState.MOVING): ContinuousDriving? {
        val sample = Reading.Observed(motion, DataMetadata(DataSource.SYSTEM, millis, Freshness.FRESH,
            CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, millis))
        return (tracker.observe(sample) as? Reading.Observed)?.value
    }

    @Test fun shortStopsPauseAndDuplicateSamplesDoNotAdvance() {
        observe(0)
        val first = requireNotNull(observe(2_000))
        assertEquals(2L, first.seconds)
        assertEquals(first, observe(2_000))
        assertNull(observe(3_000, MotionState.STATIONARY))
        assertEquals(first, observe(5_000))
        assertEquals(4L, observe(7_000)?.seconds)
    }

    @Test fun gapAndTenMinuteStopStartNewSessions() {
        observe(0)
        assertEquals(2L, observe(2_000)?.seconds)
        val afterGap = requireNotNull(observe(6_000))
        assertEquals(0L, afterGap.seconds)
        observe(8_000)
        for (millis in 9_000L..609_000L step 1_000) assertNull(observe(millis, MotionState.STATIONARY))
        val afterStop = requireNotNull(observe(610_000))
        assertEquals(0L, afterStop.seconds)
        assertNotEquals(afterGap.sessionId, afterStop.sessionId)
    }
}
