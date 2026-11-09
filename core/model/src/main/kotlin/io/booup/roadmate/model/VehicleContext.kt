package io.booup.roadmate.model

enum class RuntimeMode { DEMO, PRODUCTION }
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, FAILED }
data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val reason: UnavailableReason? = null,
)

enum class Availability {
    UNKNOWN, SUPPORTED, UNSUPPORTED, PERMISSION_DENIED, UNAVAILABLE, DISCONNECTED, ERROR
}

/** Both clocks use milliseconds. Monotonic time is session-local and must not be persisted. */
data class ObservationTime(val epochMillis: Long, val monotonicMillis: Long) {
    init { require(epochMillis >= 0 && monotonicMillis >= 0) }
}
fun interface ContextClock { fun now(): ObservationTime }

/** Application-owned read frame, not a Pleos SDK DTO. Sequence increases within an adapter. */
data class VehicleFrame(
    val sequence: Long = 0,
    val connection: ConnectionState = ConnectionState(),
    val vehicle: VehicleSnapshot = emptyVehicleSnapshot(),
    val restrictions: Reading<DrivingRestrictions> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    val motion: Reading<MotionState> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    val locationLabel: Reading<String> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val navigationLabel: Reading<String> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val care: CareInputs = CareInputs(),
    val tripEnd: Reading<TripEndSignal> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
) {
    init { require(sequence >= 0) }
    fun readings(): List<Reading<*>> = listOf(
        vehicle.lowBeam, vehicle.targetTemperatureCelsius, vehicle.currentTripDrivingSeconds,
        vehicle.telltaleIdentifiers, vehicle.currentTripDistanceMeters, vehicle.driverPresent,
        vehicle.outsideTemperatureCelsius, restrictions, motion, locationLabel, navigationLabel,
    ) + care.readings() + tripEnd + vehicle.totalOdometerKilometers + vehicle.frontLeftTargetTemperatureCelsius + vehicle.frontLeftSeatOccupied
}

fun emptyVehicleSnapshot(reason: UnavailableReason = UnavailableReason.NOT_REQUESTED): VehicleSnapshot {
    val missing = Reading.Unavailable(reason)
    return VehicleSnapshot(missing, missing, missing, missing, missing, missing, missing)
}

/** Only currentValue may be rendered as current. Original observation is retained for provenance. */
data class EvaluatedReading<out T>(
    val observation: Reading<T>,
    val availability: Availability,
    val freshness: Freshness,
    val ageMillis: Long?,
    val currentValue: T?,
) {
    val source: DataSource get() = when (observation) {
        is Reading.Observed -> observation.metadata.source
        is Reading.Unavailable -> observation.source
    }
    val observedAtEpochMillis: Long? get() = when (observation) {
        is Reading.Observed -> observation.metadata.observedAtEpochMillis
        is Reading.Unavailable -> observation.lastObservedAtEpochMillis
    }
}

data class VehicleContext(
    val mode: RuntimeMode,
    val foreground: Boolean,
    val connection: ConnectionState,
    val lowBeam: EvaluatedReading<LowBeamState>,
    val targetTemperatureCelsius: EvaluatedReading<Double>,
    val warnings: EvaluatedReading<List<String>>,
    val tripDrivingSeconds: EvaluatedReading<Long>,
    val tripDistanceMeters: EvaluatedReading<Long>,
    val driverPresent: EvaluatedReading<Boolean>,
    val outsideTemperatureCelsius: EvaluatedReading<Double>,
    val restrictions: EvaluatedReading<DrivingRestrictions>,
    val motion: EvaluatedReading<MotionState>,
    val locationLabel: EvaluatedReading<String>,
    val navigationLabel: EvaluatedReading<String>,
    val care: CareContext = CareInputs().evaluate(ObservationTime(0, 0), ContextFreshnessPolicies(),
        ConnectionState(), false),
    // Changes at lifecycle/transport boundaries, including transitions conflated by StateFlow.
    val sessionId: Long = 0,
    val tripEnd: EvaluatedReading<TripEndSignal> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED)
        .evaluate(ObservationTime(0, 0), ContextFreshnessPolicies().occupancy, ConnectionState(), false),
    val totalOdometerKilometers: EvaluatedReading<Double> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED)
        .evaluate(ObservationTime(0, 0), ContextFreshnessPolicies().trip, ConnectionState(), false),
    val frontLeftTargetTemperatureCelsius: EvaluatedReading<Double> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED)
        .evaluate(ObservationTime(0, 0), ContextFreshnessPolicies().hvac, ConnectionState(), false),
    val frontLeftSeatOccupied: EvaluatedReading<Boolean> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED)
        .evaluate(ObservationTime(0, 0), ContextFreshnessPolicies().occupancy, ConnectionState(), false),
)
