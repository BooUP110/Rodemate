package io.booup.roadmate.presentation

import io.booup.roadmate.care.*
import io.booup.roadmate.model.*

internal fun parkedContext(elapsed: Long = 0): VehicleContext = frame(elapsed).copy(
    restrictions = observed(DrivingRestrictions(UIMode.PARKED, 0, false), elapsed)).context(elapsed)

internal fun careEvent(key: String = "care.hvac.temperature_suggestion", id: String = key): CareEvent {
    val input = frame().let { f -> f.copy(care = f.care.copy(safetySignals = observed(listOf(
        SafetySignal("test-warning", SafetyClassification.IMMEDIATE_ATTENTION))))) }
    val decision = CareReducer(CareConfig(lightSustainMillis = 0)).evaluate(input.context(), CareRuntimeState(), time()).decision
    val events = listOfNotNull(decision.activeEvent) + decision.pendingEvents
    val original = when (key) {
        "care.safety.low_beam_off", "care.light.auto_check" -> events.single { it.ruleId == RuleId.LIGHT }
        "care.hvac.temperature_suggestion", "care.hvac.cooling_suggestion" -> events.single { it.ruleId == RuleId.HVAC }
        "care.rest.recommendation" -> events.single { it.ruleId == RuleId.REST }
        else -> events.single { it.ruleId == RuleId.WARNING }
    }
    return original.copy(eventId = id, presentationKey = key,
        priority = if (key.startsWith("care.safety.")) CarePriority.P1_SAFETY else CarePriority.P2_SUGGESTION)
}
internal fun awaiting(event: CareEvent = careEvent(), revision: Long = 1) =
    InteractionRecord(event, revision, InteractionStage.AWAITING_RESPONSE)

internal fun CareInteractionRuntime.response(intent: UserIntent, action: CareAction): CareResponse {
    val presentation = requireNotNull(uiState.value.presentation)
    return CareResponse(presentation.eventId, presentation.revision, intent, action)
}
