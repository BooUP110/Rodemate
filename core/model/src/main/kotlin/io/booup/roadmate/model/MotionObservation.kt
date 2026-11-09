package io.booup.roadmate.model

/** Product thresholds, not OEM safety limits. Speed is signed metres/second. */
data class MotionConfig(
    val stationaryMaxMetersPerSecond: Double = 0.1,
    val movingMinMetersPerSecond: Double = 0.5,
    val freshForMillis: Long = 3_000,
) {
    init {
        require(stationaryMaxMetersPerSecond.isFinite() && stationaryMaxMetersPerSecond >= 0)
        require(movingMinMetersPerSecond.isFinite() && movingMinMetersPerSecond > stationaryMaxMetersPerSecond)
        require(freshForMillis > 0)
    }
}

enum class SelectedGear { PARK, DRIVE, REVERSE, NEUTRAL, OTHER }

/** Auxiliary values never establish motion, parking or trip completion on their own. */
data class MotionObservation(
    val speedMetersPerSecond: Reading<Double> = Reading.Unavailable(UnavailableReason.NOT_CONNECTED, DataSource.SYSTEM),
    val selectedGear: Reading<SelectedGear> = Reading.Unavailable(UnavailableReason.NOT_CONNECTED, DataSource.SYSTEM),
    val parkingBrakeOn: Reading<Boolean> = Reading.Unavailable(UnavailableReason.NOT_CONNECTED, DataSource.SYSTEM),
    val motion: Reading<MotionState> = Reading.Unavailable(UnavailableReason.NOT_CONNECTED, DataSource.SYSTEM),
)
