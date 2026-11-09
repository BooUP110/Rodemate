package io.booup.roadmate.demo

import io.booup.roadmate.model.*

enum class InteractionDemoScenarioId {
    I1_P1_LIGHT, I2_HVAC_ACCEPT, I3_HVAC_REJECT, I4_CHARACTER_SWITCH,
    I5_P1_PREEMPTION, I6_RESTRICTION, I7_EXPIRED, I8_SIMULATED_RESULT,
}
data class InteractionDemoConfig(val transitionAfterMillis: Long = 5_000) {
    init { require(transitionAfterMillis > 0) }
}

object InteractionDemoScenarios {
    fun frame(id: InteractionDemoScenarioId, elapsed: Long, time: ObservationTime, sequence: Long,
        config: InteractionDemoConfig = InteractionDemoConfig()): VehicleFrame {
        val light = id == InteractionDemoScenarioId.I1_P1_LIGHT ||
            (id == InteractionDemoScenarioId.I5_P1_PREEMPTION && elapsed >= config.transitionAfterMillis)
        val restricted = light || (id == InteractionDemoScenarioId.I6_RESTRICTION && elapsed >= config.transitionAfterMillis)
        var frame = CareDemoScenarios.frame(if (light) CareDemoScenarioId.C1_NIGHT_OFF else CareDemoScenarioId.C3_COLD_PREFERENCE,
            time, sequence)
        // Preemption changes light/motion while retaining the SAME HVAC facts and episode.
        if (id == InteractionDemoScenarioId.I5_P1_PREEMPTION && light) {
            val hvac = CareDemoScenarios.frame(CareDemoScenarioId.C3_COLD_PREFERENCE, time, sequence)
            frame = frame.copy(vehicle = frame.vehicle.copy(targetTemperatureCelsius = hvac.vehicle.targetTemperatureCelsius,
                outsideTemperatureCelsius = hvac.vehicle.outsideTemperatureCelsius), care = frame.care.copy(preference = hvac.care.preference))
        }
        val metadata = (frame.vehicle.lowBeam as Reading.Observed).metadata
        return frame.copy(restrictions = Reading.Observed(DrivingRestrictions(
            if (restricted) UIMode.RESTRICTED else UIMode.PARKED,
            if (restricted) null else 0, restricted, time.epochMillis), metadata),
            motion = Reading.Observed(if (restricted) MotionState.MOVING else MotionState.PARKED, metadata))
    }
}
