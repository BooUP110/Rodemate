package io.booup.roadmate.presentation

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PresentationInvariantTest(private val key: String, private val mode: DrivingPresentationMode) {
    @Test fun companionCannotChangeDomainFactsPriorityActionsOrSafetyMessage() {
        val event = careEvent(key)
        val decision = CareDecision(activeEvent = event)
        val record = awaiting(event)
        val resolver = CarePresentationResolver()
        val a = resolver.resolve(record, CompanionProfiles.dodam, mode)
        val b = resolver.resolve(record, CompanionProfiles.somsom, mode)
        assertEquals(event, decision.activeEvent)
        assertEquals(event, a.sourceEvent)
        assertEquals(event, b.sourceEvent)
        assertEquals(event.priority, a.priority)
        assertEquals(a.parameters, b.parameters)
        assertEquals(a.evidence, b.evidence)
        assertEquals(a.facts, b.facts)
        assertEquals(a.actions, b.actions)
        assertTrue(a.actions.all { it.domainAction in event.allowedActions })
        if (event.priority == CarePriority.P1_SAFETY) {
            assertEquals(a, b)
            assertEquals(CompanionExpression.SERIOUS_FIXED, a.expression)
            assertEquals(if (key == "care.safety.low_beam_off") SystemCareTemplates.LIGHT_P1 else SystemCareTemplates.IMMEDIATE_P1, a.body)
        }
        if (key.startsWith("care.hvac.")) {
            assertTrue(a.body.contains("3℃") && a.body.contains("23℃"))
            assertTrue(b.body.contains("3℃") && b.body.contains("23℃"))
            assertNotEquals(a.body, b.body)
        }
        if (key == "care.rest.recommendation") {
            assertEquals(8_100L, a.facts.continuousDrivingSeconds)
            assertTrue(a.body.contains("2시간 15분") && b.body.contains("2시간 15분"))
            assertNotEquals(a.body, b.body)
        }
        if (mode == DrivingPresentationMode.COMPACT_DRIVING) {
            assertNull(a.detailText)
            assertTrue(a.actions.size <= 2)
            assertTrue(a.actions.none { it.intent in setOf(UserIntent.OPEN_DETAIL, UserIntent.REQUEST_LATER) })
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-{1}")
        fun cases() = listOf("care.safety.low_beam_off", "care.safety.immediate_attention",
            "care.light.auto_check", "care.hvac.temperature_suggestion", "care.hvac.cooling_suggestion",
            "care.rest.recommendation", "care.warning.service_recommended", "care.warning.informational",
            "care.warning.unknown_vehicle_warning").flatMap { key -> DrivingPresentationMode.entries.map { arrayOf(key, it) } }
    }
}
