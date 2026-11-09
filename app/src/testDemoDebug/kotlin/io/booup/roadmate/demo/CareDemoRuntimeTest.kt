package io.booup.roadmate.demo

import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class CareDemoRuntimeTest(private val id: CareDemoScenarioId) {
    @Test fun scenarioRunsThroughExistingAdapterStoreAndCareEngine() = runTest {
        val clock = ContextClock { ObservationTime(1_000_000 + testScheduler.currentTime, 100_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        val engine = DeterministicCareEngine(clock)
        var decision = CareDecision()
        val notifications = mutableListOf<CareEvent>()
        backgroundScope.launch { store.context.collect { decision = engine.evaluate(it); notifications += decision.emittedEvents } }
        store.foreground(); runCurrent()
        if (id == CareDemoScenarioId.C2_LIGHT_RESTORED) {
            adapter.selectCare(CareDemoScenarioId.C1_NIGHT_OFF); runCurrent()
            advanceTimeBy(10_001); runCurrent()
            assertEquals(RuleId.LIGHT, decision.activeEvent?.ruleId)
        }
        adapter.selectCare(id); runCurrent()
        if (id == CareDemoScenarioId.C1_NIGHT_OFF || id == CareDemoScenarioId.C6_MULTIPLE) {
            assertTrue(notifications.none { it.ruleId == RuleId.LIGHT })
        }
        advanceTimeBy(10_001); runCurrent()
        val current = listOfNotNull(decision.activeEvent) + decision.pendingEvents
        when (id) {
            CareDemoScenarioId.C1_NIGHT_OFF -> assertEquals(CarePriority.P1_SAFETY, decision.activeEvent?.priority)
            CareDemoScenarioId.C2_LIGHT_RESTORED -> assertTrue(current.none { it.ruleId == RuleId.LIGHT })
            CareDemoScenarioId.C3_COLD_PREFERENCE -> assertEquals("care.hvac.temperature_suggestion", decision.activeEvent?.presentationKey)
            CareDemoScenarioId.C4_CONTINUOUS_DRIVE -> assertEquals("care.rest.recommendation", decision.activeEvent?.presentationKey)
            CareDemoScenarioId.C5_IMMEDIATE_WARNING -> assertEquals("care.safety.immediate_attention", decision.activeEvent?.presentationKey)
            CareDemoScenarioId.C6_MULTIPLE -> {
                assertEquals(RuleId.LIGHT, decision.activeEvent?.ruleId)
                assertEquals(listOf(RuleId.HVAC, RuleId.REST), decision.pendingEvents.map { it.ruleId })
            }
            CareDemoScenarioId.C7_STALE_LIGHT -> {
                assertTrue(current.none { it.ruleId == RuleId.LIGHT })
                assertEquals(RuleStatus.INELIGIBLE_STALE, decision.evaluations.single { it.ruleId == RuleId.LIGHT }.status)
            }
            CareDemoScenarioId.C8_UNSUPPORTED_HVAC -> {
                assertTrue(current.none { it.ruleId == RuleId.HVAC })
                assertEquals(RuleStatus.INELIGIBLE_UNSUPPORTED, decision.evaluations.single { it.ruleId == RuleId.HVAC }.status)
            }
            CareDemoScenarioId.C9_DISCONNECTED -> assertTrue(current.isEmpty())
            CareDemoScenarioId.C10_DUPLICATE_CONTEXT -> {
                assertEquals(1, notifications.size)
                assertTrue(decision.emittedEvents.isEmpty())
                assertEquals(Suppression.COOLDOWN, decision.evaluations.single { it.ruleId == RuleId.WARNING }.suppression)
            }
        }
        assertTrue(notifications.all { it.source == DataSource.SIMULATED })
        current.flatMap { it.evidence }.forEach { assertEquals(DataSource.SIMULATED, it.source) }
        store.background(); runCurrent()
        assertNull(decision.activeEvent)
        val sequence = adapter.frames.value.sequence
        advanceTimeBy(3_000); runCurrent()
        assertEquals(sequence, adapter.frames.value.sequence)
        store.close()
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun scenarios() = CareDemoScenarioId.entries.map { arrayOf(it) }
    }
}
