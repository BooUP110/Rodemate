package io.booup.roadmate.care

import io.booup.roadmate.model.*
import kotlin.math.abs

class LightRule : CareRule {
    override val id = RuleId.LIGHT
    override fun evaluate(context: VehicleContext, eligibility: RuleEligibility, config: CareConfig): RuleResult {
        val night = eligibility.read("lightingCondition", context.care.lighting, config.policies.external)
        val beam = eligibility.read("lowBeam", context.lowBeam, config.policies.light)
        val motion = eligibility.read("motion", context.motion, config.policies.occupancy)
        val inputs = listOf(night, beam, motion)
        rejected(id, inputs)?.let { return it }
        if (night.value == LightingCondition.UNKNOWN || motion.value == MotionState.UNKNOWN)
            return unmatched(id, RuleStatus.INELIGIBLE_UNKNOWN)
        if (night.value != LightingCondition.NIGHT || beam.value != LowBeamState.OFF || motion.value != MotionState.MOVING)
            return unmatched(id)
        // Optional AUTO is only a fresh, explicitly supplied normalized input. No SDK enum inference.
        val switch = eligibility.read("lightSwitch", context.care.lightSwitch, config.policies.light)
        val auto = switch.status == null && switch.value == LightSwitchMode.AUTO
        val used = if (auto) inputs + switch else inputs
        return matched(id, listOf(RuleCandidate(id, if (auto) "low_beam_auto" else "low_beam",
            CareKind.LOW_BEAM, CareCategory.LIGHTING,
            if (auto) CarePriority.P2_SUGGESTION else CarePriority.P1_SAFETY,
            if (auto) "care.light.auto_check" else "care.safety.low_beam_off",
            evidence(used), validUntil(used), config.lightSustainMillis)))
    }
}

class HvacRule : CareRule {
    override val id = RuleId.HVAC
    override fun evaluate(context: VehicleContext, eligibility: RuleEligibility, config: CareConfig): RuleResult {
        val outside = eligibility.read("outsideTemperature", context.outsideTemperatureCelsius, config.policies.external)
        val target = eligibility.read("targetTemperature", context.targetTemperatureCelsius, config.policies.hvac)
        val preference = eligibility.read("confirmedPreference", context.care.preference, config.policies.preference)
        val driver = eligibility.read("driverPresent", context.driverPresent, config.policies.occupancy)
        var inputs = listOf(outside, target, preference, driver)
        rejected(id, inputs)?.let { return it }
        val preferred = requireNotNull(preference.value)
        if (!preferred.confirmed) return unmatched(id, RuleStatus.INELIGIBLE_UNKNOWN)
        if (!preferred.celsius.isFinite() || outside.value?.isFinite() != true || target.value?.isFinite() != true)
            return unmatched(id, RuleStatus.INELIGIBLE_INVALID)
        if (driver.value != true) return unmatched(id)
        val difference = abs(requireNotNull(target.value) - preferred.celsius)
        val cold = requireNotNull(outside.value) <= config.coldThresholdCelsius
        val hot = outside.value >= config.hotThresholdCelsius
        if (hot) {
            val ac = eligibility.read("acEnabled", context.care.acEnabled, config.policies.hvac)
            val defrost = eligibility.read("defrostEnabled", context.care.defrostEnabled, config.policies.hvac)
            inputs = inputs + ac + defrost
            rejected(id, inputs)?.let { return it }
            // Do not suggest changing an explicitly active defrost context.
            if (defrost.value == true || (ac.value == true && difference < config.temperatureDifferenceCelsius))
                return unmatched(id)
        } else if (!cold || difference < config.temperatureDifferenceCelsius) return unmatched(id)
        return matched(id, listOf(RuleCandidate(id, if (hot) "driver:cooling" else "driver:temperature",
            CareKind.TEMPERATURE, CareCategory.HVAC, CarePriority.P2_SUGGESTION,
            if (hot) "care.hvac.cooling_suggestion" else "care.hvac.temperature_suggestion",
            evidence(inputs), validUntil(inputs), parameters = mapOf("preferredCelsius" to preferred.celsius.toString()),
            actions = setOf(CareAction.ACKNOWLEDGE, CareAction.DISMISS, CareAction.REQUEST_HVAC_CHANGE))))
    }
}

/** Time-based rest recommendation only. Never a diagnosis of fatigue, sleepiness or danger. */
class RestRule : CareRule {
    override val id = RuleId.REST
    override fun evaluate(context: VehicleContext, eligibility: RuleEligibility, config: CareConfig): RuleResult {
        val duration = eligibility.read("continuousDriving", context.care.continuousDriving, config.policies.trip)
        val motion = eligibility.read("motion", context.motion, config.policies.occupancy)
        val inputs = listOf(duration, motion)
        rejected(id, inputs)?.let { return it }
        val drive = requireNotNull(duration.value)
        if (!drive.confirmed || motion.value == MotionState.UNKNOWN) return unmatched(id, RuleStatus.INELIGIBLE_UNKNOWN)
        if (drive.seconds < 0 || drive.sessionId.isBlank()) return unmatched(id, RuleStatus.INELIGIBLE_INVALID)
        if (motion.value != MotionState.MOVING || drive.seconds < config.restThresholdSeconds) return unmatched(id)
        return matched(id, listOf(RuleCandidate(id, drive.sessionId, CareKind.REST, CareCategory.REST,
            CarePriority.P2_SUGGESTION, "care.rest.recommendation", evidence(inputs), validUntil(inputs),
            parameters = mapOf("continuousDrivingSeconds" to drive.seconds.toString()),
            actions = setOf(CareAction.ACKNOWLEDGE, CareAction.DISMISS, CareAction.REQUEST_NAVIGATION))))
    }
}

class WarningRule : CareRule {
    override val id = RuleId.WARNING
    override fun evaluate(context: VehicleContext, eligibility: RuleEligibility, config: CareConfig): RuleResult {
        val signals = eligibility.read("normalizedSafetySignals", context.care.safetySignals, config.policies.safety)
        rejected(id, listOf(signals))?.let { return it }
        val values = requireNotNull(signals.value)
        if (values.any { it.subject.isBlank() } || values.map { it.subject }.distinct().size != values.size)
            return unmatched(id, RuleStatus.INELIGIBLE_INVALID)
        if (values.isEmpty()) return unmatched(id)
        return matched(id, values.sortedBy { it.subject }.map { signal ->
            val immediate = signal.classification == SafetyClassification.IMMEDIATE_ATTENTION
            val key = when (signal.classification) {
                SafetyClassification.IMMEDIATE_ATTENTION -> "care.safety.immediate_attention"
                SafetyClassification.SERVICE_RECOMMENDED -> "care.warning.service_recommended"
                SafetyClassification.INFORMATIONAL -> "care.warning.informational"
                SafetyClassification.UNCLASSIFIED -> "care.warning.unknown_vehicle_warning"
            }
            // Evidence is per subject: another signal's classification cannot become this one's severity.
            val proof = requireNotNull(signals.evidence).copy(value = "${signal.subject}:${signal.classification}")
            RuleCandidate(id, "${signal.subject}:${signal.classification}", CareKind.TELLTALE,
                CareCategory.VEHICLE_WARNING, if (immediate) CarePriority.P1_SAFETY else CarePriority.P2_SUGGESTION,
                key, listOf(proof), signals.validUntil, parameters = mapOf("subject" to signal.subject),
                actions = setOf(CareAction.ACKNOWLEDGE, CareAction.DISMISS, CareAction.OPEN_WARNING_DETAIL))
        })
    }
}
