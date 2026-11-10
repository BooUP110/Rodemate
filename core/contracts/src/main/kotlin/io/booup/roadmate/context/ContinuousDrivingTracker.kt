package io.booup.roadmate.context

import io.booup.roadmate.model.*

/** Foreground motion only. Brief stops pause the timer and unobserved gaps end the session. */
class ContinuousDrivingTracker {
    private var lastSample: Reading.Observed<MotionState>? = null
    private var sessionStartedAt = 0L
    private var drivenMillis = 0L
    private var stoppedAt: Long? = null

    fun observe(motion: Reading<MotionState>): Reading<ContinuousDriving> {
        val missing = Reading.Unavailable(UnavailableReason.NOT_VERIFIED, DataSource.DERIVED)
        val sample = motion as? Reading.Observed
        val now = sample?.metadata?.observedAtMonotonicMillis
        if (sample == null || now == null || sample.value == MotionState.UNKNOWN ||
            sample.availability() != Availability.SUPPORTED || sample.metadata.source != DataSource.SYSTEM) {
            lastSample = null
            return missing
        }
        val previous = lastSample
        val elapsed = previous?.metadata?.observedAtMonotonicMillis?.let { now - it }
        if (elapsed != null && elapsed < 0) {
            lastSample = null
            return missing
        }
        if (elapsed == null || elapsed > MotionConfig().freshForMillis ||
            stoppedAt?.let { now - it >= REST_RESET_MILLIS } == true) {
            sessionStartedAt = now
            drivenMillis = 0
            stoppedAt = null
        } else if (sample.value == MotionState.MOVING && previous?.value == MotionState.MOVING) {
            drivenMillis += elapsed
        }
        lastSample = sample
        if (sample.value != MotionState.MOVING) {
            if (stoppedAt == null) stoppedAt = now
            return missing
        }
        stoppedAt = null
        return Reading.Observed(ContinuousDriving(drivenMillis / 1_000, true, "motion:$sessionStartedAt"),
            sample.metadata.copy(source = DataSource.DERIVED))
    }

    private companion object {
        const val REST_RESET_MILLIS = 10 * 60 * 1_000L
    }
}
