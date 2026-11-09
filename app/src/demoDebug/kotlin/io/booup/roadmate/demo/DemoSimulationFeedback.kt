package io.booup.roadmate.demo

import io.booup.roadmate.model.ControlRequestIntent
import io.booup.roadmate.model.ExecutionStatus
import io.booup.roadmate.presentation.SimulationFeedback

/** Display receipt only. Does not change even the simulated vehicle frame. */
class DemoSimulationFeedback(private val adapter: DemoVehicleAdapter) : SimulationFeedback {
    override fun receipt(intent: ControlRequestIntent): ExecutionStatus =
        if (adapter.interactionSelection.value == InteractionDemoScenarioId.I8_SIMULATED_RESULT)
            ExecutionStatus.SIMULATED else ExecutionStatus.NOT_EXECUTED
}
