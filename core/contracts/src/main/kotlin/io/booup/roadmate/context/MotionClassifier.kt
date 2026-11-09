package io.booup.roadmate.context

import io.booup.roadmate.model.*
import kotlin.math.abs

/** Session-local hysteresis. Missing/stale/error inputs clear history rather than holding motion. */
class MotionClassifier(private val config: MotionConfig = MotionConfig()) {
    private var previous = MotionState.UNKNOWN
    private var lastSampleMillis: Long? = null

    fun classify(speed: Reading<Double>, now: ObservationTime): Reading<MotionState> {
        val sample = speed as? Reading.Observed
        val timestamp = sample?.metadata?.observedAtMonotonicMillis
        val age = timestamp?.let { now.monotonicMillis - it }
        if (sample == null || sample.availability() != Availability.SUPPORTED ||
            !sample.value.isFinite() || timestamp == null || age == null || age !in 0..config.freshForMillis ||
            (lastSampleMillis != null && timestamp < requireNotNull(lastSampleMillis))) {
            reset()
            return Reading.Unavailable((speed as? Reading.Unavailable)?.reason ?: UnavailableReason.INVALID_DATA, DataSource.SYSTEM)
        }
        if (lastSampleMillis?.let { timestamp - it > config.freshForMillis } == true) previous = MotionState.UNKNOWN
        lastSampleMillis = timestamp
        previous = when {
            abs(sample.value) <= config.stationaryMaxMetersPerSecond -> MotionState.STATIONARY
            abs(sample.value) >= config.movingMinMetersPerSecond -> MotionState.MOVING
            else -> previous
        }
        return if (previous == MotionState.UNKNOWN) Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SYSTEM)
        else Reading.Observed(previous, sample.metadata.copy(source = DataSource.SYSTEM))
    }

    fun reset() { previous = MotionState.UNKNOWN; lastSampleMillis = null }
}
