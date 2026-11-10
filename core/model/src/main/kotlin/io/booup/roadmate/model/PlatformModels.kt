package io.booup.roadmate.model

// Application-owned vocabulary; none of these types claim to be Pleos SDK types.
enum class DataSource { VEHICLE_SDK, DERIVED, USER_INPUT, SIMULATED, SYSTEM, UNKNOWN }
enum class Freshness { UNKNOWN, FRESH, STALE, EXPIRED }
enum class CapabilityState { UNKNOWN, SUPPORTED, UNSUPPORTED, PERMISSION_DENIED }
enum class PermissionState { UNKNOWN, GRANTED, DENIED }
enum class DataQuality { UNKNOWN, VALID, INVALID }
enum class UnavailableReason {
    NOT_CONNECTED, UNSUPPORTED, PERMISSION_DENIED, TIMEOUT, DISCONNECTED, NOT_VERIFIED,
    NOT_REQUESTED, NO_VALUE, DEMO_DATA_MISSING, SDK_ERROR, INVALID_DATA, BACKGROUND,
    SOURCE_MISMATCH, SOURCE_ERROR
}

data class DataMetadata(
    val source: DataSource,
    val observedAtEpochMillis: Long,
    val freshness: Freshness,
    val capability: CapabilityState,
    val permission: PermissionState,
    val quality: DataQuality,
    val observedAtMonotonicMillis: Long? = null,
    val receivedAtMonotonicMillis: Long? = null,
    val sourceTimestampNanos: Long? = null,
) {
    init {
        require(observedAtEpochMillis >= 0)
        require(observedAtMonotonicMillis == null || observedAtMonotonicMillis >= 0)
        require(receivedAtMonotonicMillis == null || receivedAtMonotonicMillis >= 0)
        require(sourceTimestampNanos == null || sourceTimestampNanos >= 0)
    }
}

/** Absence is explicit, never false/zero/normal. Re-check freshness at consumption. */
sealed interface Reading<out T> {
    data class Observed<T : Any>(val value: T, val metadata: DataMetadata) : Reading<T>
    data class Unavailable(
        val reason: UnavailableReason,
        val source: DataSource = DataSource.UNKNOWN,
        val lastObservedAtEpochMillis: Long? = null,
    ) : Reading<Nothing>
}

enum class LowBeamState { ON, OFF }

data class VehicleSnapshot(
    val lowBeam: Reading<LowBeamState>,
    val targetTemperatureCelsius: Reading<Double>,
    val currentTripDrivingSeconds: Reading<Long>,
    // Raw identifiers only; no invented manufacturer severity mapping.
    val telltaleIdentifiers: Reading<List<String>>,
    val currentTripDistanceMeters: Reading<Long> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    val driverPresent: Reading<Boolean> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    // Vehicle outside sensor (SYSTEM) or explicit Demo, never weather or HVAC 'current'.
    val outsideTemperatureCelsius: Reading<Double> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    // Cumulative odometer observation; never a TripTracker distance counter.
    val totalOdometerKilometers: Reading<Double> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    // Area-specific observation, not a verified driver's target or outside temperature.
    val frontLeftTargetTemperatureCelsius: Reading<Double> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    // Seat area is explicit; driver seat location has not been verified.
    val frontLeftSeatOccupied: Reading<Boolean> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
)

enum class UIMode { PARKED, RESTRICTED, UNKNOWN_RESTRICTED }
enum class MotionState { UNKNOWN, PARKED, STATIONARY, MOVING } // PARKED is the legacy stationary label, not parking proof.

/** UX restrictions and physical parking evidence must remain separate. */
data class DrivingRestrictions(
    val mode: UIMode = UIMode.UNKNOWN_RESTRICTED,
    val activeRestrictions: Int? = null,
    val requiresDistractionOptimization: Boolean? = null,
    val observedAtEpochMillis: Long? = null,
)

data class NavigationSnapshot(
    val destinationLabel: Reading<String>,
    val remainingDistanceMeters: Reading<Long>,
)

sealed interface AdapterResult<out T> {
    data class Available<T>(val value: T) : AdapterResult<T>
    data class Unavailable(val reason: UnavailableReason) : AdapterResult<Nothing>
}
