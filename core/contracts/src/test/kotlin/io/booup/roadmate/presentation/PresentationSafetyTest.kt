package io.booup.roadmate.presentation

import io.booup.roadmate.care.*
import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class PresentationSafetyTest {
    private fun resolve(event: CareEvent) = CarePresentationResolver().resolve(awaiting(event), CompanionProfiles.dodam, DrivingPresentationMode.FULL_PARKED)
    @Test fun lightP1MatchesRequiredFixedTextExactly() {
        assertEquals("전조등이 꺼져 있어요. 지금 켜 주세요.", resolve(careEvent("care.safety.low_beam_off")).body)
    }
    @Test fun unknownP1KeyCannotInjectItsOwnMessageOrDiagnosis() {
        val event = careEvent("care.safety.immediate_attention").copy(presentationKey = "엔진 고장 완료",
            parameters = mapOf("body" to "전조등을 켰어요"))
        assertEquals(SystemCareTemplates.IMMEDIATE_P1, resolve(event).body)
    }
    @Test fun maliciousCompanionNameCannotChangeP1IdentityOrExpression() {
        val forged = CompanionProfile(CompanionId("other"), "장난 메시지", "웃음", CompanionTone.WARM)
        val record = awaiting(careEvent("care.safety.low_beam_off"))
        val resolver = CarePresentationResolver()
        assertEquals(resolver.resolve(record, CompanionProfiles.dodam, DrivingPresentationMode.FULL_PARKED),
            resolver.resolve(record, forged, DrivingPresentationMode.FULL_PARKED))
    }
    @Test fun unknownWarningNeverGetsADiagnosis() {
        val result = resolve(careEvent("care.warning.unknown_vehicle_warning"))
        assertEquals(SystemCareTemplates.UNCLASSIFIED, result.body)
        assertEquals(SafetyClassification.UNCLASSIFIED, result.facts.warningClassification)
        assertFalse(result.body.contains("엔진") || result.body.contains("브레이크") || result.body.contains("배터리"))
    }
    @Test fun warningClassificationIsMappedWithoutSeverityRejudgment() {
        listOf("care.safety.immediate_attention", "care.warning.service_recommended", "care.warning.informational",
            "care.warning.unknown_vehicle_warning").zip(SafetyClassification.entries).forEach { (key, classification) ->
            assertEquals(classification, resolve(careEvent(key)).facts.warningClassification)
        }
    }
    @Test fun noActionIsInventedWhenEngineAllowsNone() {
        assertTrue(resolve(careEvent().copy(allowedActions = emptySet())).actions.isEmpty())
    }
    @Test fun missingOrNonNumericPreferredTemperatureNeverInventsAValueOrAcceptAction() {
        listOf(emptyMap(), mapOf("preferredCelsius" to "NaN"), mapOf("preferredCelsius" to "23도로 맞췄어요")).forEach { parameters ->
            val result = resolve(careEvent().copy(parameters = parameters))
            assertNull(result.facts.preferredCelsius)
            assertTrue(result.actions.none { it.intent == UserIntent.ACCEPT })
            assertFalse(result.body.contains("맞췄어요"))
        }
    }
    @Test fun absentOutsideEvidenceIsNotReplacedWithAnInventedWeatherValue() {
        val result = resolve(careEvent().copy(evidence = emptyList()))
        assertNull(result.facts.outsideCelsius)
        assertFalse(result.body.contains("외부 기온"))
        assertTrue(result.body.contains("23℃"))
    }
    @Test fun restContainsOnlyConfirmedDurationAndRestMeaning() {
        val result = resolve(careEvent("care.rest.recommendation"))
        assertTrue(result.body.contains("2시간 15분") && result.body.contains("휴식"))
        assertTrue(listOf("졸려", "피곤", "눈이", "km", "휴게소가").none { result.body.contains(it) })
    }
    @Test fun ownerMutationCannotChangeRenderedParametersEvidenceOrAllowedActions() {
        val parameters = mutableMapOf("preferredCelsius" to "23.0")
        val evidence = careEvent().evidence.toMutableList()
        val actions = mutableSetOf(CareAction.REQUEST_HVAC_CHANGE)
        val result = resolve(careEvent().copy(parameters = parameters, evidence = evidence, allowedActions = actions))
        parameters["preferredCelsius"] = "99.0"; evidence.clear(); actions.clear()
        assertEquals("23.0", result.parameters["preferredCelsius"])
        assertFalse(result.evidence.isEmpty())
        assertTrue(result.actions.any { it.domainAction == CareAction.REQUEST_HVAC_CHANGE })
        assertThrows(UnsupportedOperationException::class.java) { (result.parameters as MutableMap)["preferredCelsius"] = "88" }
    }
    @Test fun originalAudioPresentationContractUsesTheSameP1Template() {
        val presenter = TemplateCompanionPresenter(); val event = careEvent("care.safety.low_beam_off")
        assertEquals(presenter.present(event, CompanionProfiles.dodam.id), presenter.present(event, CompanionProfiles.somsom.id))
        assertEquals(event.eventId, presenter.present(event, CompanionProfiles.dodam.id).eventId)
    }
    @Test fun secondsAreNotRoundedAwayFromTheFacts() {
        val result = resolve(careEvent("care.rest.recommendation").copy(parameters = mapOf("continuousDrivingSeconds" to "7201")))
        assertTrue(result.body.contains("2시간 1초"))
        assertEquals(7_201L, result.facts.continuousDrivingSeconds)
    }
    @Test fun unknownRestrictionsFailClosed() {
        assertEquals(DrivingPresentationMode.COMPACT_DRIVING, DrivingPresentationPolicy().mode(frame().context(), time()))
    }
    @Test fun freshExplicitUnrestrictedPolicyAllowsFullCard() {
        assertEquals(DrivingPresentationMode.FULL_PARKED, DrivingPresentationPolicy().mode(parkedContext(), time()))
    }
    @Test fun actualRestrictionForcesCompactAndHidesDetail() {
        val full = parkedContext(); val restricted = full.copy(restrictions = observed(DrivingRestrictions(UIMode.RESTRICTED)).evaluate(
            time(), ContextFreshnessPolicies().restrictions, full.connection, true))
        assertEquals(DrivingPresentationMode.COMPACT_DRIVING, DrivingPresentationPolicy().mode(restricted, time()))
        val result = CarePresentationResolver().resolve(awaiting(careEvent("care.warning.service_recommended")).copy(detailRequested = true),
            CompanionProfiles.dodam, DrivingPresentationMode.COMPACT_DRIVING)
        assertNull(result.detailText)
    }
    @Test fun cachedUnrestrictedPolicyIsRecheckedAtConsumption() {
        assertEquals(DrivingPresentationMode.COMPACT_DRIVING, DrivingPresentationPolicy().mode(parkedContext(), time(5_001)))
    }
    @Test fun backgroundAndDisconnectCannotExposeFullContent() {
        val context = parkedContext()
        assertEquals(DrivingPresentationMode.COMPACT_DRIVING, DrivingPresentationPolicy().mode(context.copy(foreground = false), time()))
        assertEquals(DrivingPresentationMode.COMPACT_DRIVING, DrivingPresentationPolicy().mode(context.copy(connection = ConnectionState()), time()))
    }
}
