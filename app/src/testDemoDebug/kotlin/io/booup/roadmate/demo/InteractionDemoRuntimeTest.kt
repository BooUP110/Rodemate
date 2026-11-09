package io.booup.roadmate.demo

import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.CareInteractionRuntime
import io.booup.roadmate.presentation.SystemCareTemplates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class InteractionDemoRuntimeTest(private val id: InteractionDemoScenarioId) {
    @Test fun interactionScenarioRunsThroughAdapterEngineAndPresentation() = runTest {
        val clock = ContextClock { ObservationTime(1_000_000 + testScheduler.currentTime, 100_000 + testScheduler.currentTime) }
        val adapter = DemoVehicleAdapter(clock)
        val store = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, backgroundScope)
        val engine = DeterministicCareEngine(clock)
        val interaction = CareInteractionRuntime(clock, simulationFeedback = DemoSimulationFeedback(adapter))
        var decision = CareDecision()
        backgroundScope.launch { store.context.collect { decision = engine.evaluate(it); interaction.update(decision, it) } }
        store.foreground(); runCurrent(); adapter.selectInteraction(id); runCurrent()
        val original = interaction.uiState.value.presentation
        fun response(intent: UserIntent, action: CareAction): CareResponse {
            val p = requireNotNull(interaction.uiState.value.presentation)
            return CareResponse(p.eventId, p.revision, intent, action)
        }
        when (id) {
            InteractionDemoScenarioId.I1_P1_LIGHT -> {
                assertNull(original)
                advanceTimeBy(10_001); runCurrent()
                val p = interaction.uiState.value.presentation!!
                assertEquals(SystemCareTemplates.LIGHT_P1, p.body)
                assertEquals(CompanionExpression.SERIOUS_FIXED, p.expression)
                assertEquals(DrivingPresentationMode.COMPACT_DRIVING, p.mode)
            }
            InteractionDemoScenarioId.I2_HVAC_ACCEPT, InteractionDemoScenarioId.I8_SIMULATED_RESULT -> {
                val before = adapter.frames.value
                assertTrue(interaction.respond(response(UserIntent.ACCEPT, CareAction.REQUEST_HVAC_CHANGE)))
                assertEquals(before, adapter.frames.value)
                val record = interaction.records.value.getValue(original!!.eventId)
                assertNotNull(record.controlIntent)
                assertEquals(if (id == InteractionDemoScenarioId.I8_SIMULATED_RESULT) ExecutionStatus.SIMULATED
                    else ExecutionStatus.NOT_EXECUTED, record.executionStatus)
                assertEquals(19.0, store.context.value.targetTemperatureCelsius.currentValue)
            }
            InteractionDemoScenarioId.I3_HVAC_REJECT -> {
                assertTrue(interaction.respond(response(UserIntent.REJECT, CareAction.DISMISS)))
                assertNull(interaction.uiState.value.presentation)
                assertEquals(InteractionStage.REJECTED, interaction.records.value.getValue(original!!.eventId).stage)
            }
            InteractionDemoScenarioId.I4_CHARACTER_SWITCH -> {
                val unchanged = decision
                assertTrue(interaction.selectCompanion(CompanionProfiles.somsom.id))
                assertEquals(unchanged, decision)
                assertNotEquals(original?.body, interaction.uiState.value.presentation?.body)
                assertEquals(original?.facts, interaction.uiState.value.presentation?.facts)
                assertEquals(original?.actions, interaction.uiState.value.presentation?.actions)
            }
            InteractionDemoScenarioId.I5_P1_PREEMPTION -> {
                val old = response(UserIntent.ACCEPT, CareAction.REQUEST_HVAC_CHANGE)
                advanceTimeBy(15_001); runCurrent()
                assertEquals(CarePriority.P1_SAFETY, interaction.uiState.value.presentation?.priority)
                assertEquals(InteractionStage.SUSPENDED, interaction.records.value.getValue(old.eventId).stage)
                assertFalse(interaction.respond(old))
            }
            InteractionDemoScenarioId.I6_RESTRICTION -> {
                assertEquals(DrivingPresentationMode.FULL_PARKED, original?.mode)
                advanceTimeBy(5_001); runCurrent()
                assertEquals(DrivingPresentationMode.COMPACT_DRIVING, interaction.uiState.value.mode)
                assertFalse(interaction.uiState.value.fullContentAllowed)
                assertNull(interaction.uiState.value.presentation?.detailText)
            }
            InteractionDemoScenarioId.I7_EXPIRED -> {
                advanceTimeBy(30_001); runCurrent()
                assertNull(interaction.uiState.value.presentation)
                assertEquals(InteractionStage.EXPIRED, interaction.records.value.getValue(original!!.eventId).stage)
            }
        }
        store.background(); runCurrent()
        assertNull(interaction.uiState.value.presentation)
        assertFalse(interaction.uiState.value.fullContentAllowed)
        val before = adapter.frames.value
        advanceTimeBy(2_000); runCurrent()
        assertEquals(before, adapter.frames.value)
        store.close()
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun scenarios() = InteractionDemoScenarioId.entries.map { arrayOf(it) }
    }
}
