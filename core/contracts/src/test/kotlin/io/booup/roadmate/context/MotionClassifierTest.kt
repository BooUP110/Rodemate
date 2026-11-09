package io.booup.roadmate.context

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class MotionClassifierTest {
    private fun now(ms: Long = 10_000) = ObservationTime(1_000_000 + ms, ms)
    private fun speed(value: Double, ms: Long = 10_000, quality: DataQuality = DataQuality.VALID) =
        Reading.Observed(value, DataMetadata(DataSource.SYSTEM, now(ms).epochMillis, Freshness.FRESH,
            CapabilityState.SUPPORTED, PermissionState.GRANTED, quality, ms))
    private fun value(reading: Reading<MotionState>) = (reading as? Reading.Observed)?.value
    @Test fun positiveAndNegativeSpeedsBothMeanMoving() {
        for (raw in listOf(2.78, -2.78)) assertEquals(MotionState.MOVING, value(MotionClassifier().classify(speed(raw), now())))
    }
    @Test fun zeroAndStationaryBoundaryRemainStationaryCandidates() {
        for (raw in listOf(0.0, 0.1, -0.1)) assertEquals(MotionState.STATIONARY, value(MotionClassifier().classify(speed(raw), now())))
    }
    @Test fun hysteresisRetainsOnlyFreshSessionHistory() {
        val classifier = MotionClassifier()
        assertNull(value(classifier.classify(speed(0.3), now())))
        assertEquals(MotionState.MOVING, value(classifier.classify(speed(0.5), now())))
        assertEquals(MotionState.MOVING, value(classifier.classify(speed(0.3, 11_000), now(11_000))))
        classifier.reset()
        assertNull(value(classifier.classify(speed(0.3, 12_000), now(12_000))))
    }
    @Test fun staleCachedGetCannotRefreshMotion() {
        val classifier = MotionClassifier()
        classifier.classify(speed(2.0), now())
        assertNull(value(classifier.classify(speed(2.0), now(13_001))))
        assertNull(value(classifier.classify(speed(0.3, 14_000), now(14_000))))
    }
    @Test fun missingPermissionAndErrorNeverBecomeStationary() {
        for (reason in listOf(UnavailableReason.PERMISSION_DENIED, UnavailableReason.UNSUPPORTED,
            UnavailableReason.SOURCE_ERROR, UnavailableReason.DISCONNECTED)) {
            val classifier = MotionClassifier()
            classifier.classify(speed(1.0), now())
            assertEquals(Reading.Unavailable(reason, DataSource.SYSTEM), classifier.classify(Reading.Unavailable(reason), now()))
            assertNull(value(classifier.classify(speed(0.3), now())))
        }
    }
    @Test fun invalidFutureAndNonFiniteInputsFailClosed() {
        for (input in listOf(speed(Double.NaN), speed(Double.POSITIVE_INFINITY), speed(0.0, 10_001), speed(0.0, quality = DataQuality.UNKNOWN)))
            assertNull(value(MotionClassifier().classify(input, now())))
    }
    @Test fun reorderedSamplesClearHysteresis() {
        val classifier = MotionClassifier()
        classifier.classify(speed(1.0, 11_000), now(11_000))
        assertNull(value(classifier.classify(speed(0.0, 10_000), now(11_000))))
    }
    @Test fun longGapCannotCarryHysteresisIntoNewEvidence() {
        val classifier = MotionClassifier()
        classifier.classify(speed(1.0), now())
        assertNull(value(classifier.classify(speed(0.3, 20_000), now(20_000))))
    }
    @Test fun configRejectsInvalidThresholds() {
        for (block in listOf<() -> Unit>({ MotionConfig(movingMinMetersPerSecond = 0.0) },
            { MotionConfig(stationaryMaxMetersPerSecond = Double.NaN) }, { MotionConfig(freshForMillis = 0) })) {
            try { block(); fail("invalid config accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
