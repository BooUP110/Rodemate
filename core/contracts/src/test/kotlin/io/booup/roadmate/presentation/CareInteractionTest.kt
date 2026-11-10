package io.booup.roadmate.presentation

import io.booup.roadmate.care.*
import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class CareInteractionTest {
    private class Fixture(feedback: SimulationFeedback? = null) {
        val clock = TestCareClock()
        val runtime = CareInteractionRuntime(clock, InteractionConfig(responseWindowMillis = 10), simulationFeedback = feedback)
        val hvac = careEvent()
        fun update(event: CareEvent? = hvac, pending: List<CareEvent> = emptyList(), context: VehicleContext = parkedContext(clock.elapsed)) {
            runtime.update(CareDecision(event, pending), context)
        }
        fun response(intent: UserIntent = UserIntent.ACCEPT, action: CareAction = CareAction.REQUEST_HVAC_CHANGE) = runtime.response(intent, action)
        fun active() = runtime.records.value.getValue(requireNotNull(runtime.uiState.value.presentation).eventId)
    }
    @Test fun presentedBecomesAwaitingOnlyAfterDisplayAcknowledgement() {
        val f = Fixture(); f.update()
        assertEquals(InteractionStage.PRESENTED, f.active().stage)
        val p = f.runtime.uiState.value.presentation!!
        assertTrue(f.runtime.displayed(p.eventId, p.revision))
        assertEquals(InteractionStage.AWAITING_RESPONSE, f.active().stage)
    }
    @Test fun acceptCreatesOnlyAnImmutableNotExecutedControlIntent() {
        val f = Fixture(); f.update(); val response = f.response()
        assertTrue(f.runtime.respond(response))
        val record = f.runtime.records.value.getValue(response.eventId)
        assertEquals(InteractionStage.PENDING_EXECUTION, record.stage)
        assertEquals(ExecutionStatus.NOT_EXECUTED, record.executionStatus)
        assertEquals(CareAction.REQUEST_HVAC_CHANGE, record.controlIntent?.action)
        assertEquals("23.0", record.controlIntent?.parameters?.get("preferredCelsius"))
        assertEquals(f.clock.now(), record.controlIntent?.approvedAt)
        assertTrue(record.transitions.contains(InteractionStage.ACCEPTED))
        assertNull(f.runtime.uiState.value.presentation)
        assertTrue(f.runtime.uiState.value.notice!!.contains("앱은 변경하지 않았어요"))
    }
    @Test fun repeatedAcceptCannotProduceAnotherApproval() {
        val f = Fixture(); f.update(); val response = f.response()
        f.runtime.respond(response); val first = f.runtime.records.value.getValue(response.eventId).controlIntent
        assertFalse(f.runtime.respond(response))
        assertEquals(first, f.runtime.records.value.getValue(response.eventId).controlIntent)
    }
    @Test fun rejectClosesWithoutAnyControlIntent() {
        val f = Fixture(); f.update(); val response = f.response(UserIntent.REJECT, CareAction.DISMISS)
        assertTrue(f.runtime.respond(response))
        val record = f.runtime.records.value.getValue(response.eventId)
        assertEquals(InteractionStage.REJECTED, record.stage)
        assertNull(record.controlIntent)
        assertEquals(ExecutionStatus.NOT_REQUESTED, record.executionStatus)
        assertNull(f.runtime.uiState.value.presentation)
    }
    @Test fun acknowledgeP1DoesNotHideAnUnresolvedSafetyCondition() {
        val f = Fixture(); f.update(careEvent("care.safety.low_beam_off"))
        val response = f.response(UserIntent.ACKNOWLEDGE, CareAction.ACKNOWLEDGE)
        assertTrue(f.runtime.respond(response))
        assertEquals(InteractionStage.ACKNOWLEDGED, f.active().stage)
        assertEquals(SystemCareTemplates.LIGHT_P1, f.runtime.uiState.value.presentation?.body)
        assertTrue(f.runtime.uiState.value.presentation!!.actions.isEmpty())
    }
    @Test fun dismissClosesWarningInteraction() {
        val f = Fixture(); f.update(careEvent("care.warning.informational"))
        val response = f.response(UserIntent.DISMISS, CareAction.DISMISS)
        assertTrue(f.runtime.respond(response))
        assertEquals(InteractionStage.DISMISSED, f.runtime.records.value.getValue(response.eventId).stage)
        assertNull(f.runtime.uiState.value.presentation)
    }
    @Test fun requestLaterClosesWithoutPretendingToScheduleAnAction() {
        val f = Fixture(); f.update(); val response = f.response(UserIntent.REQUEST_LATER, CareAction.DISMISS)
        assertTrue(f.runtime.respond(response))
        assertNull(f.runtime.records.value.getValue(response.eventId).controlIntent)
        assertEquals("안내를 닫았어요.", f.runtime.uiState.value.notice)
    }
    @Test fun deadlineIsInclusiveAndLateAcceptIsRejected() {
        val f = Fixture(); f.update(); val response = f.response()
        f.clock.elapsed = 10
        assertFalse(f.runtime.respond(response))
        assertEquals(InteractionStage.EXPIRED, f.runtime.records.value.getValue(response.eventId).stage)
        assertNull(f.runtime.uiState.value.presentation)
    }
    @Test fun timeoutDoesNotRequireAUserResponse() {
        val f = Fixture(); f.update(); val id = f.hvac.eventId
        f.clock.elapsed = 9; f.update(); assertNotNull(f.runtime.uiState.value.presentation)
        f.clock.elapsed = 10; f.update()
        assertEquals(InteractionStage.EXPIRED, f.runtime.records.value.getValue(id).stage)
        assertNull(f.runtime.uiState.value.presentation)
    }
    @Test fun p1DoesNotExpireOrLoseItsSafetyMessage() {
        val f = Fixture(); val event = careEvent("care.safety.low_beam_off")
        f.update(event); f.clock.elapsed = 100_000; f.update(event)
        assertNotNull(f.runtime.uiState.value.presentation)
        assertNull(f.active().deadlineMonotonicMillis)
    }
    @Test fun p1PreemptsAndRejectsClicksFromSuspendedP2() {
        val f = Fixture(); f.update(); val old = f.response()
        val deadline = f.active().deadlineMonotonicMillis
        f.clock.elapsed = 1; f.update(careEvent("care.safety.low_beam_off"), listOf(f.hvac))
        assertEquals(InteractionStage.SUSPENDED, f.runtime.records.value.getValue(f.hvac.eventId).stage)
        assertEquals(deadline, f.runtime.records.value.getValue(f.hvac.eventId).deadlineMonotonicMillis)
        assertEquals(CarePriority.P1_SAFETY, f.runtime.uiState.value.presentation?.priority)
        assertFalse(f.runtime.respond(old))
    }
    @Test fun p2ResumesWithOriginalWindowAfterP1Resolves() {
        val f = Fixture(); f.update(); val oldRevision = f.active().revision; val deadline = f.active().deadlineMonotonicMillis
        f.clock.elapsed = 1; f.update(careEvent("care.safety.low_beam_off"), listOf(f.hvac))
        f.clock.elapsed = 2; f.update()
        assertEquals(oldRevision, f.active().revision)
        assertEquals(deadline, f.active().deadlineMonotonicMillis)
        assertTrue(f.runtime.respond(f.response()))
    }
    @Test fun suspendedP2CanExpireAndCannotGetANewWindow() {
        val f = Fixture(); f.update()
        f.clock.elapsed = 1; f.update(careEvent("care.safety.low_beam_off"), listOf(f.hvac))
        f.clock.elapsed = 10; f.update(careEvent("care.safety.low_beam_off"), listOf(f.hvac))
        assertEquals(InteractionStage.EXPIRED, f.runtime.records.value.getValue(f.hvac.eventId).stage)
        f.clock.elapsed = 11; f.update()
        assertNull(f.runtime.uiState.value.presentation)
    }
    @Test fun domainResolutionClearsPresentationAndRejectsOldCommands() {
        val f = Fixture(); f.update(); val old = f.response()
        f.update(null)
        assertTrue(f.runtime.records.value.isEmpty())
        assertNull(f.runtime.uiState.value.presentation)
        assertEquals(InteractionStage.RESOLVED, f.runtime.lastResolution?.stage)
        assertFalse(f.runtime.respond(old))
    }
    @Test fun replacingEventRejectsPreviousEventId() {
        val f = Fixture(); f.update(); val old = f.response()
        f.update(careEvent("care.rest.recommendation"))
        assertFalse(f.runtime.respond(old))
        assertEquals(1, f.runtime.records.value.size)
    }
    @Test fun updatedParametersInvalidateOldRevisionAndApproval() {
        val f = Fixture(); f.update(); val old = f.response()
        f.update(f.hvac.copy(parameters = mapOf("preferredCelsius" to "24.0")))
        assertNotEquals(old.revision, f.active().revision)
        assertFalse(f.runtime.respond(old))
        assertTrue(f.runtime.respond(f.response()))
        assertEquals("24.0", f.runtime.records.value.getValue(old.eventId).controlIntent?.parameters?.get("preferredCelsius"))
    }
    @Test fun observationTimestampRefreshDoesNotRestartResponseWindow() {
        val f = Fixture(); f.update(); val original = f.active()
        f.clock.elapsed = 5
        f.update(f.hvac.copy(evidence = f.hvac.evidence.map { it.copy(observedAtEpochMillis = it.observedAtEpochMillis + 5) }))
        assertEquals(original.revision, f.active().revision)
        assertEquals(original.deadlineMonotonicMillis, f.active().deadlineMonotonicMillis)
    }
    @Test fun unsupportedActionAndWrongIntentPairAreRejected() {
        val f = Fixture(); f.update(); val original = f.active()
        assertFalse(f.runtime.respond(f.response(action = CareAction.REQUEST_NAVIGATION)))
        assertFalse(f.runtime.respond(f.response(UserIntent.ACCEPT, CareAction.DISMISS)))
        assertFalse(f.runtime.respond(f.response().copy(eventId = "spoofed")))
        assertFalse(f.runtime.respond(f.response().copy(revision = 999)))
        assertEquals(original, f.active())
    }
    @Test fun mutableInputParametersCannotAlterApprovedRequest() {
        val f = Fixture(); val input = mutableMapOf("preferredCelsius" to "23.0")
        f.update(f.hvac.copy(parameters = input)); val response = f.response()
        input["preferredCelsius"] = "99.0"
        assertTrue(f.runtime.respond(response))
        assertEquals("23.0", f.runtime.records.value.getValue(response.eventId).controlIntent?.parameters?.get("preferredCelsius"))
    }
    @Test fun restrictedDetailInjectionIsRejectedAndExistingDetailIsHidden() {
        val f = Fixture(); val warning = careEvent("care.warning.service_recommended")
        f.update(warning); val response = f.response(UserIntent.OPEN_DETAIL, CareAction.OPEN_WARNING_DETAIL)
        assertTrue(f.runtime.respond(response))
        assertNotNull(f.runtime.uiState.value.presentation?.detailText)
        f.update(warning, context = frame().context())
        assertNull(f.runtime.uiState.value.presentation?.detailText)
        assertFalse(f.runtime.respond(response))
    }
    @Test fun characterSwitchChangesOnlyPermittedPresentationWithoutRestartingInteraction() {
        val f = Fixture(); f.update(); val before = f.active(); val body = f.runtime.uiState.value.presentation!!.body
        assertTrue(f.runtime.selectCompanion(CompanionProfiles.somsom.id))
        assertEquals(before, f.active())
        assertNotEquals(body, f.runtime.uiState.value.presentation!!.body)
        assertEquals(before.event, f.runtime.uiState.value.presentation!!.sourceEvent)
    }
    @Test fun unknownOrRestrictedContextPreventsCompanionSelection() {
        val f = Fixture(); f.update(context = frame().context())
        assertFalse(f.runtime.uiState.value.fullContentAllowed)
        assertFalse(f.runtime.selectCompanion(CompanionProfiles.somsom.id))
    }
    @Test fun p1RemainsIdenticalWhenCompanionChangesInUnrestrictedMode() {
        val f = Fixture(); f.update(careEvent("care.safety.low_beam_off")); val p1 = f.runtime.uiState.value.presentation
        assertTrue(f.runtime.selectCompanion(CompanionProfiles.somsom.id))
        assertEquals(p1, f.runtime.uiState.value.presentation)
    }
    @Test fun previewProfileChangesOnlyAppearanceInParkedDemo() {
        val clock = TestCareClock()
        val preview = CompanionProfile(CompanionId("preview:test"), "시연", "시", CompanionTone.WARM)
        val runtime = CareInteractionRuntime(clock, previewProfiles = listOf(preview))
        val decision = CareDecision(careEvent("care.safety.low_beam_off"))
        val parked = parkedContext()
        runtime.update(decision, parked)
        val originalWarning = requireNotNull(runtime.uiState.value.presentation)
        assertTrue(runtime.selectCompanion(preview.id))
        assertEquals(preview, runtime.uiState.value.companion)
        assertEquals(originalWarning, runtime.uiState.value.presentation)
        runtime.update(decision, parked.copy(mode = RuntimeMode.PRODUCTION))
        assertFalse(runtime.selectCompanion(preview.id))
        assertEquals(CompanionProfiles.dodam, runtime.uiState.value.companion)
    }
    @Test fun simulationIsExplicitAndNeverASuccessResult() {
        val f = Fixture(SimulationFeedback { ExecutionStatus.SIMULATED }); f.update()
        assertTrue(f.runtime.respond(f.response()))
        assertEquals(ExecutionStatus.SIMULATED, f.runtime.uiState.value.executionStatus)
        assertFalse(f.runtime.records.value.values.any { it.executionStatus == ExecutionStatus.SUCCEEDED })
    }
    @Test fun injectedSuccessFeedbackCannotCreateSuccess() {
        val f = Fixture(SimulationFeedback { ExecutionStatus.SUCCEEDED }); f.update()
        assertTrue(f.runtime.respond(f.response()))
        assertEquals(ExecutionStatus.NOT_EXECUTED, f.runtime.uiState.value.executionStatus)
    }
    @Test fun productionNeverConsultsDemoFeedback() {
        var called = false
        val f = Fixture(SimulationFeedback { called = true; ExecutionStatus.SIMULATED })
        val realEvent = f.hvac.copy(source = DataSource.DERIVED)
        f.update(realEvent, context = parkedContext().copy(mode = RuntimeMode.PRODUCTION))
        assertTrue(f.runtime.respond(f.response()))
        assertFalse(called)
        assertEquals(ExecutionStatus.NOT_EXECUTED, f.runtime.uiState.value.executionStatus)
    }
    @Test fun backgroundClosesAllInteractionsAndDoesNotApprovePastInput() {
        val f = Fixture(); f.update(); val response = f.response()
        f.update(context = parkedContext().copy(foreground = false, sessionId = 2))
        assertFalse(f.runtime.respond(response))
        assertNull(f.runtime.uiState.value.presentation)
        assertTrue(f.runtime.records.value.isEmpty())
    }
    @Test fun unknownCompanionIdCannotSupplyAnUnregisteredProfile() {
        val f = Fixture(); f.update()
        assertFalse(f.runtime.selectCompanion(CompanionId("unregistered")))
        assertEquals(CompanionProfiles.dodam, f.runtime.uiState.value.companion)
    }
    @Test fun closeDropsPendingIntentsAndPresentation() {
        val f = Fixture(); f.update(); f.runtime.respond(f.response()); f.runtime.close()
        assertNull(f.runtime.uiState.value.presentation)
        assertTrue(f.runtime.records.value.isEmpty())
    }
}
