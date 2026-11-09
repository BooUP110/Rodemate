package io.booup.roadmate.care

import io.booup.roadmate.model.*

internal class TestCareClock(var elapsed: Long = 0) : ContextClock {
    override fun now() = time(elapsed)
}
internal fun time(elapsed: Long = 0) = ObservationTime(1_000_000 + elapsed, 100_000 + elapsed)
internal fun <T : Any> observed(value: T, elapsed: Long = 0): Reading<T> = Reading.Observed(value,
    DataMetadata(DataSource.SIMULATED, time(elapsed).epochMillis, Freshness.FRESH,
        CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, time(elapsed).monotonicMillis))

internal fun frame(elapsed: Long = 0): VehicleFrame = VehicleFrame(
    connection = ConnectionState(ConnectionStatus.CONNECTED),
    vehicle = VehicleSnapshot(observed(LowBeamState.OFF, elapsed), observed(19.0, elapsed),
        observed(0L, elapsed), observed(emptyList(), elapsed), driverPresent = observed(true, elapsed),
        outsideTemperatureCelsius = observed(3.0, elapsed)),
    motion = observed(MotionState.MOVING, elapsed),
    care = CareInputs(lighting = observed(LightingCondition.NIGHT, elapsed),
        preference = observed(PreferredTemperature(23.0, true), elapsed),
        acEnabled = observed(false, elapsed), defrostEnabled = observed(false, elapsed),
        continuousDriving = observed(ContinuousDriving(8_100, true, "test-drive"), elapsed),
        safetySignals = observed(emptyList(), elapsed)),
)

internal fun VehicleFrame.context(elapsed: Long = 0, mode: RuntimeMode = RuntimeMode.DEMO,
    foreground: Boolean = true, sessionId: Long = 1): VehicleContext {
    val now = time(elapsed)
    val policies = ContextFreshnessPolicies()
    fun <T : Any> read(value: Reading<T>, policy: FreshnessPolicy) = value.evaluate(now, policy, connection, foreground)
    return VehicleContext(mode, foreground, connection,
        read(vehicle.lowBeam, policies.light), read(vehicle.targetTemperatureCelsius, policies.hvac),
        read(vehicle.telltaleIdentifiers, policies.safety), read(vehicle.currentTripDrivingSeconds, policies.trip),
        read(vehicle.currentTripDistanceMeters, policies.trip), read(vehicle.driverPresent, policies.occupancy),
        read(vehicle.outsideTemperatureCelsius, policies.external), read(restrictions, policies.restrictions),
        read(motion, policies.occupancy), read(locationLabel, policies.external), read(navigationLabel, policies.external),
        care.evaluate(now, policies, connection, foreground), sessionId)
}

internal fun evaluate(rule: CareRule, input: VehicleFrame = frame(), elapsed: Long = 0,
    config: CareConfig = CareConfig()): RuleResult {
    val context = input.context(elapsed)
    return rule.evaluate(context, RuleEligibility(context, time(elapsed)), config)
}
