package io.booup.roadmate.care

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RuleEligibilityTest(private val id: RuleId, private val failure: String, private val expected: RuleStatus) {
    @Test fun invalidRequiredObservationCannotProduceAnyCandidate() {
        fun <T : Any> corrupt(input: Reading<T>): Reading<T> {
            val value = input as Reading.Observed
            return when (failure) {
                "stale" -> value.copy(metadata = value.metadata.copy(observedAtEpochMillis = 900_000, observedAtMonotonicMillis = 0))
                "unsupported" -> Reading.Unavailable(UnavailableReason.UNSUPPORTED, DataSource.SIMULATED)
                "permission" -> Reading.Unavailable(UnavailableReason.PERMISSION_DENIED, DataSource.SIMULATED)
                "missing" -> Reading.Unavailable(UnavailableReason.NO_VALUE, DataSource.SIMULATED)
                "unknown" -> value.copy(metadata = value.metadata.copy(capability = CapabilityState.UNKNOWN))
                "invalid" -> value.copy(metadata = value.metadata.copy(quality = DataQuality.INVALID))
                "future" -> value.copy(metadata = value.metadata.copy(observedAtMonotonicMillis = 100_001))
                "source" -> value.copy(metadata = value.metadata.copy(source = DataSource.UNKNOWN))
                else -> input
            }
        }
        val base = frame().let { it.copy(care = it.care.copy(safetySignals = observed(listOf(
            SafetySignal("test", SafetyClassification.IMMEDIATE_ATTENTION))))) }
        var input = when (id) {
            RuleId.LIGHT -> base.copy(vehicle = base.vehicle.copy(lowBeam = corrupt(base.vehicle.lowBeam)))
            RuleId.HVAC -> base.copy(vehicle = base.vehicle.copy(targetTemperatureCelsius = corrupt(base.vehicle.targetTemperatureCelsius)))
            RuleId.REST -> base.copy(care = base.care.copy(continuousDriving = corrupt(base.care.continuousDriving)))
            RuleId.WARNING -> base.copy(care = base.care.copy(safetySignals = corrupt(base.care.safetySignals)))
        }
        if (failure == "disconnected") input = input.copy(connection = ConnectionState(ConnectionStatus.DISCONNECTED))
        val context = input.context(foreground = failure != "background")
        val rule: CareRule = when (id) { RuleId.LIGHT -> LightRule(); RuleId.HVAC -> HvacRule(); RuleId.REST -> RestRule(); RuleId.WARNING -> WarningRule() }
        val result = rule.evaluate(context, RuleEligibility(context, time()), CareConfig())
        assertEquals(expected, result.evaluation.status)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.evaluation.evidence.isEmpty())
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-{1}")
        fun cases() = RuleId.entries.flatMap { id -> listOf(
            "stale" to RuleStatus.INELIGIBLE_STALE,
            "unsupported" to RuleStatus.INELIGIBLE_UNSUPPORTED,
            "permission" to RuleStatus.INELIGIBLE_PERMISSION,
            "missing" to RuleStatus.INELIGIBLE_MISSING_DATA,
            "unknown" to RuleStatus.INELIGIBLE_UNKNOWN,
            "invalid" to RuleStatus.INELIGIBLE_INVALID,
            "future" to RuleStatus.INELIGIBLE_UNKNOWN,
            "source" to RuleStatus.INELIGIBLE_SOURCE,
            "disconnected" to RuleStatus.INELIGIBLE_DISCONNECTED,
            "background" to RuleStatus.INELIGIBLE_BACKGROUND,
        ).map { (failure, status) -> arrayOf(id, failure, status) } }
    }
}
