package io.booup.roadmate.care

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class ClosureCarePolicyTest {
    @Test fun lightOffAndMovingWithoutNightNeverInventsP1() {
        for (t in listOf(0L, 10_000L, 60_000L)) {
            val input = frame(t).copy(care = frame(t).care.copy(lighting = Reading.Unavailable(UnavailableReason.NOT_VERIFIED)))
            val result = evaluate(LightRule(), input, t)
            assertNotEquals(RuleStatus.MATCHED, result.evaluation.status)
            assertTrue(result.candidates.isEmpty())
        }
    }
    @Test fun unknownSeatCannotAuthorizeHvacEvenWithOtherInputsPresent() {
        val input = frame().copy(vehicle = frame().vehicle.copy(driverPresent = Reading.Unavailable(UnavailableReason.NOT_VERIFIED)))
        val result = evaluate(HvacRule(), input)
        assertNotEquals(RuleStatus.MATCHED, result.evaluation.status)
        assertTrue(result.candidates.isEmpty())
    }
    @Test fun missingContinuousDrivingDoesNotUseElapsedTripTimeAsFatigue() {
        val input = frame().copy(care = frame().care.copy(continuousDriving = Reading.Unavailable(UnavailableReason.NOT_VERIFIED)))
        assertTrue(evaluate(RestRule(), input).candidates.isEmpty())
    }
}
