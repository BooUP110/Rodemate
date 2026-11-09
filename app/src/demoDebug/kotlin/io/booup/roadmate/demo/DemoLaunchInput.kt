package io.booup.roadmate.demo

import io.booup.roadmate.contracts.VehicleContextRepository
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Host test input is separate from the gated in-car selector. Never registered by Production. */
class DemoLaunchInput(private val adapter: DemoVehicleAdapter, private val repository: VehicleContextRepository,
    private val clock: ContextClock, private val scope: CoroutineScope, private val tripController: DemoTripController? = null,
    private val postDriveController: PostDriveDemoController? = null, private val progressController:ProgressDemoController?=null) {
    private var pending: Job? = null
    fun fromHost(command: String?) {
        if (command == null || !known(command)) return
        pending?.cancel()
        pending = scope.launch {
            repository.context.first { it.foreground }
            // New explicit simulated input AFTER lifecycle invalidation; never re-stamp old values.
            delay(1)
            if (repository.context.value.foreground) apply(command)
        }
    }
    fun fromUi(command: String): Boolean {
        if (DrivingPresentationPolicy().mode(repository.context.value, clock.now()) != DrivingPresentationMode.FULL_PARKED) return false
        if (!known(command)) return false
        apply(command)
        return true
    }
    private fun known(command: String) = DemoScenarioId.entries.any { it.name == command } ||
        CareDemoScenarioId.entries.any { it.name == command } || InteractionDemoScenarioId.entries.any { it.name == command } ||
        (tripController != null && TripDemoScenarioId.entries.any { it.name == command }) ||
        (postDriveController != null && PostDriveDemoId.entries.any { it.name == command }) ||
        (progressController!=null && ProgressDemoId.entries.any { it.name==command })
    private fun apply(command: String) {
        DemoScenarioId.entries.firstOrNull { it.name == command }?.let { adapter.select(it); return }
        CareDemoScenarioId.entries.firstOrNull { it.name == command }?.let { adapter.selectCare(it); return }
        InteractionDemoScenarioId.entries.firstOrNull { it.name == command }?.let { adapter.selectInteraction(it) }
        TripDemoScenarioId.entries.firstOrNull { it.name == command }?.let { tripController?.select(it) }
        PostDriveDemoId.entries.firstOrNull { it.name == command }?.let { postDriveController?.select(it) }
        ProgressDemoId.entries.firstOrNull { it.name==command }?.let { progressController?.select(it) }
    }
}
