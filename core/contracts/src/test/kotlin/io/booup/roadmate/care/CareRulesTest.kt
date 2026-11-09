package io.booup.roadmate.care

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class CareRulesTest {
    @Test fun daytimeOffDoesNotWarn() {
        val input = frame().let { it.copy(care = it.care.copy(lighting = observed(LightingCondition.DAY))) }
        assertEquals(RuleStatus.NOT_MATCHED, evaluate(LightRule(), input).evaluation.status)
    }
    @Test fun nightOnDoesNotWarnEvenIfAuto() {
        val input = frame().let { it.copy(vehicle = it.vehicle.copy(lowBeam = observed(LowBeamState.ON)),
            care = it.care.copy(lightSwitch = observed(LightSwitchMode.AUTO))) }
        assertTrue(evaluate(LightRule(), input).candidates.isEmpty())
    }
    @Test fun nightAndMotionUnknownDoNotBecomeSafetyWarnings() {
        val input = frame().let { it.copy(care = it.care.copy(lighting = observed(LightingCondition.UNKNOWN))) }
        assertEquals(RuleStatus.INELIGIBLE_UNKNOWN, evaluate(LightRule(), input).evaluation.status)
        assertEquals(RuleStatus.INELIGIBLE_UNKNOWN, evaluate(LightRule(), frame().copy(motion = observed(MotionState.UNKNOWN))).evaluation.status)
    }
    @Test fun parkedOffDoesNotWarn() {
        assertTrue(evaluate(LightRule(), frame().copy(motion = observed(MotionState.PARKED))).candidates.isEmpty())
    }
    @Test fun explicitAutoOnlyProducesP2() {
        val input = frame().let { it.copy(care = it.care.copy(lightSwitch = observed(LightSwitchMode.AUTO))) }
        val candidate = evaluate(LightRule(), input).candidates.single()
        assertEquals(CarePriority.P2_SUGGESTION, candidate.priority)
        assertEquals("care.light.auto_check", candidate.key)
    }
    @Test fun ordinaryOffUsesFixedP1KeyWithoutCharacterInput() {
        val candidate = evaluate(LightRule()).candidates.single()
        assertEquals(CarePriority.P1_SAFETY, candidate.priority)
        assertEquals("care.safety.low_beam_off", candidate.key)
        assertEquals(setOf(CareAction.ACKNOWLEDGE, CareAction.DISMISS), candidate.actions)
    }
    @Test fun coldKnownPreferenceProducesSuggestionWithProof() {
        val event = evaluate(HvacRule()).candidates.single()
        assertEquals(CarePriority.P2_SUGGESTION, event.priority)
        assertEquals("23.0", event.parameters["preferredCelsius"])
        assertEquals(4, event.evidence.size)
        assertTrue(event.actions.contains(CareAction.REQUEST_HVAC_CHANGE))
    }
    @Test fun unconfirmedPreferenceDoesNotSuggest() {
        val f = frame(); assertTrue(evaluate(HvacRule(), f.copy(care = f.care.copy(
            preference = observed(PreferredTemperature(23.0, false))))).candidates.isEmpty())
    }
    @Test fun nearPreferenceDoesNotSuggest() {
        val f = frame(); assertTrue(evaluate(HvacRule(), f.copy(vehicle = f.vehicle.copy(
            targetTemperatureCelsius = observed(22.0)))).candidates.isEmpty())
    }
    @Test fun absentDriverDoesNotSuggest() {
        val f = frame(); assertTrue(evaluate(HvacRule(), f.copy(vehicle = f.vehicle.copy(driverPresent = observed(false)))).candidates.isEmpty())
    }
    @Test fun coldAndDifferenceBoundariesAreInclusive() {
        val f = frame(); val input = f.copy(vehicle = f.vehicle.copy(outsideTemperatureCelsius = observed(5.0), targetTemperatureCelsius = observed(21.0)))
        assertEquals(1, evaluate(HvacRule(), input).candidates.size)
        assertTrue(evaluate(HvacRule(), input.copy(vehicle = input.vehicle.copy(outsideTemperatureCelsius = observed(5.001)))).candidates.isEmpty())
    }
    @Test fun hotRequiresExplicitAcAndDefrostContext() {
        val f = frame(); val hot = f.copy(vehicle = f.vehicle.copy(outsideTemperatureCelsius = observed(30.0)))
        assertEquals("care.hvac.cooling_suggestion", evaluate(HvacRule(), hot).candidates.single().key)
        val missing = hot.copy(care = hot.care.copy(acEnabled = Reading.Unavailable(UnavailableReason.NO_VALUE)))
        assertTrue(evaluate(HvacRule(), missing).candidates.isEmpty())
    }
    @Test fun activeDefrostIsNotDisrupted() {
        val f = frame(); val hot = f.copy(vehicle = f.vehicle.copy(outsideTemperatureCelsius = observed(35.0)),
            care = f.care.copy(defrostEnabled = observed(true)))
        assertTrue(evaluate(HvacRule(), hot).candidates.isEmpty())
    }
    @Test fun hotAlreadyAtPreferenceAndCoolingDoesNotSuggest() {
        val f = frame(); val hot = f.copy(vehicle = f.vehicle.copy(outsideTemperatureCelsius = observed(35.0), targetTemperatureCelsius = observed(23.0)),
            care = f.care.copy(acEnabled = observed(true)))
        assertTrue(evaluate(HvacRule(), hot).candidates.isEmpty())
    }
    @Test fun nonFiniteTemperaturesAreInvalid() {
        val f = frame(); assertEquals(RuleStatus.INELIGIBLE_INVALID, evaluate(HvacRule(), f.copy(
            vehicle = f.vehicle.copy(outsideTemperatureCelsius = observed(Double.NaN)))).evaluation.status)
    }
    @Test fun fogPlaceholderNeverInfersFogOrTriggersAnotherRule() {
        val f = frame(); val original = evaluate(HvacRule(), f)
        FogRisk.entries.forEach { fog -> assertEquals(original, evaluate(HvacRule(), f.copy(care = f.care.copy(fogRisk = observed(fog))))) }
    }
    @Test fun restBoundaryIsInclusive() {
        val f = frame()
        fun with(seconds: Long) = f.copy(care = f.care.copy(continuousDriving = observed(ContinuousDriving(seconds, true, "drive"))))
        assertTrue(evaluate(RestRule(), with(7_199)).candidates.isEmpty())
        assertEquals("care.rest.recommendation", evaluate(RestRule(), with(7_200)).candidates.single().key)
    }
    @Test fun tripOdometerAloneCannotProveContinuousDriving() {
        val f = frame(); val input = f.copy(vehicle = f.vehicle.copy(currentTripDrivingSeconds = observed(90_000L)),
            care = f.care.copy(continuousDriving = Reading.Unavailable(UnavailableReason.NOT_VERIFIED)))
        assertTrue(evaluate(RestRule(), input).candidates.isEmpty())
    }
    @Test fun unconfirmedOrNegativeContinuousTimeDoesNotRecommendRest() {
        val f = frame()
        assertTrue(evaluate(RestRule(), f.copy(care = f.care.copy(continuousDriving = observed(ContinuousDriving(8_100, false, "drive"))))).candidates.isEmpty())
        assertEquals(RuleStatus.INELIGIBLE_INVALID, evaluate(RestRule(), f.copy(care = f.care.copy(continuousDriving = observed(ContinuousDriving(-1, true, "drive"))))).evaluation.status)
    }
    @Test fun parkedContinuousTimeDoesNotCreateNewRestRecommendation() {
        assertTrue(evaluate(RestRule(), frame().copy(motion = observed(MotionState.PARKED))).candidates.isEmpty())
    }
    @Test fun immediateWarningIsP1AndAllOtherExplicitClassesP2() {
        SafetyClassification.entries.forEach { classification ->
            val f = frame(); val event = evaluate(WarningRule(), f.copy(care = f.care.copy(
                safetySignals = observed(listOf(SafetySignal("test-subject", classification)))))).candidates.single()
            assertEquals(if (classification == SafetyClassification.IMMEDIATE_ATTENTION) CarePriority.P1_SAFETY else CarePriority.P2_SUGGESTION, event.priority)
            assertTrue(event.evidence.single().value.contains(classification.name))
        }
    }
    @Test fun unclassifiedWarningCarriesNoDiagnosis() {
        val f = frame(); val event = evaluate(WarningRule(), f.copy(care = f.care.copy(safetySignals = observed(
            listOf(SafetySignal("unmapped", SafetyClassification.UNCLASSIFIED)))))).candidates.single()
        assertEquals("care.warning.unknown_vehicle_warning", event.key)
        assertEquals(mapOf("subject" to "unmapped"), event.parameters)
    }
    @Test fun rawTelltaleIdentifiersNeverBecomeClassifiedWarnings() {
        val f = frame(); val input = f.copy(vehicle = f.vehicle.copy(telltaleIdentifiers = observed(listOf("RAW_CODE"))))
        assertTrue(evaluate(WarningRule(), input).candidates.isEmpty())
    }
    @Test fun duplicateOrBlankWarningSubjectsAreInvalid() {
        val f = frame(); val signal = SafetySignal("same", SafetyClassification.IMMEDIATE_ATTENTION)
        listOf(listOf(signal, signal), listOf(signal.copy(subject = ""))).forEach { values ->
            assertEquals(RuleStatus.INELIGIBLE_INVALID, evaluate(WarningRule(), f.copy(care = f.care.copy(safetySignals = observed(values)))).evaluation.status)
        }
    }
}
