package io.booup.roadmate.demo

import io.booup.roadmate.model.*

enum class CareDemoScenarioId {
    C1_NIGHT_OFF, C2_LIGHT_RESTORED, C3_COLD_PREFERENCE, C4_CONTINUOUS_DRIVE,
    C5_IMMEDIATE_WARNING, C6_MULTIPLE, C7_STALE_LIGHT, C8_UNSUPPORTED_HVAC,
    C9_DISCONNECTED, C10_DUPLICATE_CONTEXT,
}

/** Explicit fixture classifications, never a manufacturer telltale codebook. demoDebug only. */
object CareDemoScenarios {
    fun frame(id: CareDemoScenarioId, time: ObservationTime, sequence: Long): VehicleFrame {
        val night = id in setOf(CareDemoScenarioId.C1_NIGHT_OFF, CareDemoScenarioId.C2_LIGHT_RESTORED,
            CareDemoScenarioId.C6_MULTIPLE, CareDemoScenarioId.C7_STALE_LIGHT)
        val hvac = id in setOf(CareDemoScenarioId.C3_COLD_PREFERENCE, CareDemoScenarioId.C6_MULTIPLE,
            CareDemoScenarioId.C8_UNSUPPORTED_HVAC)
        val rest = id in setOf(CareDemoScenarioId.C4_CONTINUOUS_DRIVE, CareDemoScenarioId.C6_MULTIPLE)
        val scenario = DemoScenario(DemoScenarioId.A_NORMAL, id.name,
            restriction = UIMode.RESTRICTED, motion = MotionState.MOVING,
            lowBeam = if (night && id != CareDemoScenarioId.C2_LIGHT_RESTORED) LowBeamState.OFF else LowBeamState.ON,
            targetCelsius = if (hvac) 19.0 else 22.0, outsideCelsius = if (hvac) 3.0 else 20.0,
            hvacUnsupported = id == CareDemoScenarioId.C8_UNSUPPORTED_HVAC,
            disconnected = id == CareDemoScenarioId.C9_DISCONNECTED)
        var frame = DemoScenarios.frame(scenario, time, sequence)
        fun <T : Any> sample(value: T): Reading<T> = Reading.Observed(value, DataMetadata(
            DataSource.SIMULATED, time.epochMillis, Freshness.FRESH,
            CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, time.monotonicMillis))
        val signals = when (id) {
            CareDemoScenarioId.C5_IMMEDIATE_WARNING -> listOf(SafetySignal("DEMO_CARE_IMMEDIATE", SafetyClassification.IMMEDIATE_ATTENTION))
            CareDemoScenarioId.C10_DUPLICATE_CONTEXT -> listOf(SafetySignal("DEMO_CARE_INFORMATIONAL", SafetyClassification.INFORMATIONAL))
            else -> emptyList()
        }
        frame = frame.copy(care = CareInputs(
            lighting = sample(if (night) LightingCondition.NIGHT else LightingCondition.DAY),
            preference = if (hvac) sample(PreferredTemperature(23.0, true)) else Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED),
            continuousDriving = if (rest) sample(ContinuousDriving(8_100, true, "DEMO_CONTINUOUS"))
                else Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED),
            safetySignals = sample(signals),
        ))
        if (id == CareDemoScenarioId.C7_STALE_LIGHT) {
            val stale: Reading<LowBeamState> = if (time.epochMillis >= 6_000 && time.monotonicMillis >= 6_000) {
                val value = sample(LowBeamState.OFF) as Reading.Observed
                value.copy(metadata = value.metadata.copy(observedAtEpochMillis = time.epochMillis - 6_000,
                    observedAtMonotonicMillis = time.monotonicMillis - 6_000))
            } else Reading.Unavailable(UnavailableReason.TIMEOUT, DataSource.SIMULATED)
            frame = frame.copy(vehicle = frame.vehicle.copy(lowBeam = stale))
        }
        return if (scenario.disconnected) frame.invalidate(UnavailableReason.DISCONNECTED) else frame
    }
}
