package io.booup.roadmate.context

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class ObservationOrderingTest {
    private fun speed(value:Double,at:Long):Reading<Double> = Reading.Observed(value,DataMetadata(DataSource.SYSTEM,at,
        Freshness.FRESH,CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,at))
    @Test fun newerCallbackMovingSurvivesOlderGetStationaryResponse() {
        val classifier=MotionClassifier(); var accepted=speed(5.0,1000)
        assertEquals(MotionState.MOVING,(classifier.classify(accepted,ObservationTime(1000,1000)) as Reading.Observed).value)
        val lateGet=speed(0.0,900)
        if(!lateGet.isOlderObservationThan(accepted)) accepted=lateGet
        assertEquals(MotionState.MOVING,(classifier.classify(accepted,ObservationTime(1100,1100)) as Reading.Observed).value)
        assertEquals(1000L,(accepted as Reading.Observed).metadata.observedAtMonotonicMillis)
    }
    @Test fun keepingNewestObservationDoesNotMakeItsTimestampFresh() {
        val accepted=speed(5.0,1000); val late=speed(0.0,900)
        assertTrue(late.isOlderObservationThan(accepted))
        assertTrue(MotionClassifier().classify(accepted,ObservationTime(20_000,20_000)) is Reading.Unavailable)
    }
    @Test fun errorAndNewerSamplesAreNotSuppressedByOrderingFilter() {
        val accepted=speed(5.0,1000)
        assertFalse(Reading.Unavailable(UnavailableReason.SDK_ERROR,DataSource.SYSTEM).isOlderObservationThan(accepted))
        assertFalse(speed(0.0,1100).isOlderObservationThan(accepted))
    }
}
