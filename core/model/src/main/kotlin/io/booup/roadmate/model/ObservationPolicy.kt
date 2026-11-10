package io.booup.roadmate.model

data class FreshnessPolicy(val freshForMillis: Long, val usableForMillis: Long) {
    init { require(freshForMillis >= 0 && usableForMillis >= freshForMillis) }
}

/** Design examples, not official vehicle requirements. Override per signal when verified. */
data class ContextFreshnessPolicies(
    val light: FreshnessPolicy = FreshnessPolicy(5_000, 15_000),
    val hvac: FreshnessPolicy = FreshnessPolicy(30_000, 120_000),
    val safety: FreshnessPolicy = FreshnessPolicy(5_000, 15_000),
    val trip: FreshnessPolicy = FreshnessPolicy(30_000, 120_000),
    val occupancy: FreshnessPolicy = FreshnessPolicy(5_000, 15_000),
    val restrictions: FreshnessPolicy = FreshnessPolicy(5_000, 10_000),
    val external: FreshnessPolicy = FreshnessPolicy(60_000, 300_000),
    val preference: FreshnessPolicy = FreshnessPolicy(86_400_000, 86_400_000),
)

fun Reading<*>.availability(): Availability = when (this) {
    is Reading.Unavailable -> when (reason) {
        UnavailableReason.NOT_REQUESTED, UnavailableReason.NOT_VERIFIED -> Availability.UNKNOWN
        UnavailableReason.UNSUPPORTED -> Availability.UNSUPPORTED
        UnavailableReason.PERMISSION_DENIED -> Availability.PERMISSION_DENIED
        UnavailableReason.DISCONNECTED, UnavailableReason.NOT_CONNECTED -> Availability.DISCONNECTED
        UnavailableReason.SDK_ERROR, UnavailableReason.SOURCE_ERROR, UnavailableReason.SOURCE_MISMATCH,
        UnavailableReason.INVALID_DATA -> Availability.ERROR
        else -> Availability.UNAVAILABLE
    }
    is Reading.Observed -> when {
        metadata.permission == PermissionState.DENIED ||
            metadata.capability == CapabilityState.PERMISSION_DENIED -> Availability.PERMISSION_DENIED
        metadata.capability == CapabilityState.UNSUPPORTED -> Availability.UNSUPPORTED
        metadata.quality == DataQuality.INVALID -> Availability.ERROR
        metadata.capability != CapabilityState.SUPPORTED ||
            metadata.permission != PermissionState.GRANTED ||
            metadata.quality != DataQuality.VALID -> Availability.UNKNOWN
        else -> Availability.SUPPORTED
    }
}

/** Same-session GET can finish after a newer callback. Keep the newest evidence, never its age. */
fun Reading<*>.isOlderObservationThan(previous:Reading<*>):Boolean {
    if(this !is Reading.Observed || previous !is Reading.Observed ||
        availability()!=Availability.SUPPORTED || metadata.source!=previous.metadata.source) return false
    val incoming=metadata.observedAtMonotonicMillis ?: return false
    val accepted=previous.metadata.observedAtMonotonicMillis ?: return false
    return incoming<accepted
}

/** Never trust a cached FRESH label. Future/invalid clocks are unusable, never clamped to age 0. */
fun <T : Any> Reading<T>.evaluate(
    now: ObservationTime,
    policy: FreshnessPolicy,
    connection: ConnectionState,
    foreground: Boolean,
): EvaluatedReading<T> {
    val availability = availability()
    val observed = this as? Reading.Observed
    val age = observed?.metadata?.let { metadata ->
        val elapsed = metadata.observedAtMonotonicMillis?.let { now.monotonicMillis - it }
            ?: (now.epochMillis - metadata.observedAtEpochMillis)
        elapsed.takeIf { it >= 0 }
    }
    val freshness = when {
        observed == null || age == null -> Freshness.UNKNOWN
        age <= policy.freshForMillis -> Freshness.FRESH
        age <= policy.usableForMillis -> Freshness.STALE
        else -> Freshness.EXPIRED
    }
    val liveConnection = connection.status == ConnectionStatus.CONNECTED ||
        connection.status == ConnectionStatus.DEGRADED
    val current = if (foreground && liveConnection && availability == Availability.SUPPORTED &&
        freshness == Freshness.FRESH) observed?.value else null
    return EvaluatedReading(this, availability, freshness, age, current)
}

fun <T : Any> Reading<T>.invalidate(reason: UnavailableReason): Reading<T> = when (this) {
    is Reading.Observed -> Reading.Unavailable(reason, metadata.source, metadata.observedAtEpochMillis)
    is Reading.Unavailable -> this // Feature support/permission are not a connection state.
}

fun VehicleFrame.invalidate(reason: UnavailableReason): VehicleFrame = copy(
    vehicle = vehicle.copy(
        lowBeam = vehicle.lowBeam.invalidate(reason),
        targetTemperatureCelsius = vehicle.targetTemperatureCelsius.invalidate(reason),
        currentTripDrivingSeconds = vehicle.currentTripDrivingSeconds.invalidate(reason),
        telltaleIdentifiers = vehicle.telltaleIdentifiers.invalidate(reason),
        currentTripDistanceMeters = vehicle.currentTripDistanceMeters.invalidate(reason),
        driverPresent = vehicle.driverPresent.invalidate(reason),
        outsideTemperatureCelsius = vehicle.outsideTemperatureCelsius.invalidate(reason),
        totalOdometerKilometers = vehicle.totalOdometerKilometers.invalidate(reason),
        frontLeftTargetTemperatureCelsius = vehicle.frontLeftTargetTemperatureCelsius.invalidate(reason),
        frontLeftSeatOccupied = vehicle.frontLeftSeatOccupied.invalidate(reason),
    ),
    restrictions = restrictions.invalidate(reason), motion = motion.invalidate(reason),
    locationLabel = locationLabel.invalidate(reason), navigationLabel = navigationLabel.invalidate(reason),
    care = care.invalidate(reason),
    tripEnd = tripEnd.invalidate(reason),
)
