package io.booup.roadmate.model

// Normalized application inputs. These are NOT Pleos enums or mappings from unverified SDK values.
enum class LightingCondition { DAY, NIGHT, UNKNOWN }
enum class LightSwitchMode { MANUAL, AUTO, UNKNOWN }
enum class FogRisk { KNOWN_RISK, NO_RISK, UNKNOWN }
data class PreferredTemperature(val celsius: Double, val confirmed: Boolean)
data class ContinuousDriving(val seconds: Long, val confirmed: Boolean, val sessionId: String)
enum class SafetyClassification { IMMEDIATE_ATTENTION, SERVICE_RECOMMENDED, INFORMATIONAL, UNCLASSIFIED }
data class SafetySignal(val subject: String, val classification: SafetyClassification)

data class CareInputs(
    val lighting: Reading<LightingCondition> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val lightSwitch: Reading<LightSwitchMode> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val preference: Reading<PreferredTemperature> = Reading.Unavailable(UnavailableReason.NOT_REQUESTED),
    val acEnabled: Reading<Boolean> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val defrostEnabled: Reading<Boolean> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val continuousDriving: Reading<ContinuousDriving> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val safetySignals: Reading<List<SafetySignal>> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    val fogRisk: Reading<FogRisk> = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
) {
    fun readings(): List<Reading<*>> = listOf(lighting, lightSwitch, preference, acEnabled,
        defrostEnabled, continuousDriving, safetySignals, fogRisk)
    fun invalidate(reason: UnavailableReason) = copy(
        lighting = lighting.invalidate(reason), lightSwitch = lightSwitch.invalidate(reason),
        preference = preference.invalidate(reason), acEnabled = acEnabled.invalidate(reason),
        defrostEnabled = defrostEnabled.invalidate(reason), continuousDriving = continuousDriving.invalidate(reason),
        safetySignals = safetySignals.invalidate(reason), fogRisk = fogRisk.invalidate(reason),
    )
}

data class CareContext(
    val lighting: EvaluatedReading<LightingCondition>,
    val lightSwitch: EvaluatedReading<LightSwitchMode>,
    val preference: EvaluatedReading<PreferredTemperature>,
    val acEnabled: EvaluatedReading<Boolean>,
    val defrostEnabled: EvaluatedReading<Boolean>,
    val continuousDriving: EvaluatedReading<ContinuousDriving>,
    val safetySignals: EvaluatedReading<List<SafetySignal>>,
    val fogRisk: EvaluatedReading<FogRisk>,
)

fun CareInputs.evaluate(now: ObservationTime, policies: ContextFreshnessPolicies,
    connection: ConnectionState, foreground: Boolean): CareContext = CareContext(
    lighting.evaluate(now, policies.external, connection, foreground),
    lightSwitch.evaluate(now, policies.light, connection, foreground),
    preference.evaluate(now, policies.preference, connection, foreground),
    acEnabled.evaluate(now, policies.hvac, connection, foreground),
    defrostEnabled.evaluate(now, policies.hvac, connection, foreground),
    continuousDriving.evaluate(now, policies.trip, connection, foreground),
    safetySignals.evaluate(now, policies.safety, connection, foreground),
    fogRisk.evaluate(now, policies.external, connection, foreground),
)

enum class CareCategory { VEHICLE_WARNING, LIGHTING, HVAC, REST, COMPANION, POST_DRIVE }
enum class CareAction { ACKNOWLEDGE, DISMISS, REQUEST_HVAC_CHANGE, OPEN_WARNING_DETAIL, REQUEST_NAVIGATION }
enum class RuleId { LIGHT, HVAC, REST, WARNING }
enum class RuleStatus {
    MATCHED, NOT_MATCHED, WAITING_DURATION, INELIGIBLE_STALE, INELIGIBLE_UNSUPPORTED,
    INELIGIBLE_PERMISSION, INELIGIBLE_MISSING_DATA, INELIGIBLE_UNKNOWN, INELIGIBLE_INVALID,
    INELIGIBLE_DISCONNECTED, INELIGIBLE_BACKGROUND, INELIGIBLE_SOURCE,
}
enum class Suppression { NONE, DUPLICATE, COOLDOWN, REPEAT_LIMIT }

data class CareEvidence(
    val signal: String,
    val value: String,
    val source: DataSource,
    val observedAtEpochMillis: Long,
    val observedAtMonotonicMillis: Long?,
    val ageMillis: Long,
    val freshness: Freshness = Freshness.FRESH,
    val availability: Availability = Availability.SUPPORTED,
)

data class RuleEvaluation(
    val ruleId: RuleId,
    val status: RuleStatus,
    val subject: String? = null,
    val evidence: List<CareEvidence> = emptyList(),
    val conditionDurationMillis: Long = 0,
    val suppression: Suppression = Suppression.NONE,
    val nextEligibleAtMonotonicMillis: Long? = null,
)

data class CareDecision(
    val activeEvent: CareEvent? = null,
    val pendingEvents: List<CareEvent> = emptyList(),
    // Newly emitted notifications, distinct from still-active/pending condition events.
    val emittedEvents: List<CareEvent> = emptyList(),
    val evaluations: List<RuleEvaluation> = emptyList(),
)

/** Design examples from spec §5, not manufacturer safety policy. All limits live here. */
data class CareConfig(
    val lightSustainMillis: Long = 10_000,
    val coldThresholdCelsius: Double = 5.0,
    val hotThresholdCelsius: Double = 30.0,
    val temperatureDifferenceCelsius: Double = 2.0,
    val restThresholdSeconds: Long = 7_200,
    val policies: ContextFreshnessPolicies = ContextFreshnessPolicies(),
    val cooldowns: Map<RuleId, Long> = RuleId.entries.associateWith { 300_000L },
    val p1RepeatIntervalMillis: Long = 60_000,
    val p1MaxNotificationsPerEpisode: Int = 2,
) {
    init {
        require(lightSustainMillis >= 0 && restThresholdSeconds > 0)
        require(coldThresholdCelsius.isFinite() && hotThresholdCelsius.isFinite() &&
            coldThresholdCelsius < hotThresholdCelsius)
        require(temperatureDifferenceCelsius.isFinite() && temperatureDifferenceCelsius > 0)
        require(cooldowns.keys.containsAll(RuleId.entries) && cooldowns.values.all { it > 0 })
        require(p1RepeatIntervalMillis > 0 && p1MaxNotificationsPerEpisode > 0)
    }
}
