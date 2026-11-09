package io.booup.roadmate.presentation

import io.booup.roadmate.model.*
import java.util.Collections

/** Display restriction only: never judges vehicle risk, capability or permission. */
class DrivingPresentationPolicy(private val policies: ContextFreshnessPolicies = ContextFreshnessPolicies()) {
    fun mode(context: VehicleContext, now: ObservationTime): DrivingPresentationMode {
        val latest = context.restrictions.observation.evaluate(now, policies.restrictions, context.connection, context.foreground)
        val checked = context.copy(restrictions = latest)
        val full = context.restrictions.currentValue != null &&
            ContentGate().access(ContentArea.COMPANION_CUSTOMIZATION, checked).allowed
        return if (full) DrivingPresentationMode.FULL_PARKED else DrivingPresentationMode.COMPACT_DRIVING
    }
}

class CarePresentationResolver(private val presenter: TemplateCompanionPresenter = TemplateCompanionPresenter()) {
    fun resolve(record: InteractionRecord, profile: CompanionProfile, mode: DrivingPresentationMode): CarePresentation {
        val event = record.event.presentationSnapshot()
        val copy = presenter.render(event, profile)
        val safety = event.priority == CarePriority.P1_SAFETY
        val responding = record.stage in setOf(InteractionStage.PRESENTED, InteractionStage.AWAITING_RESPONSE)
        val actions = if (!responding) emptyList() else actions(event, copy.facts, mode)
        return CarePresentation(event, record.revision,
            if (mode == DrivingPresentationMode.COMPACT_DRIVING) PresentationType.COMPACT_CARD
            else if (safety) PresentationType.SAFETY_BANNER else PresentationType.SUGGESTION_CARD,
            copy.title, copy.body, copy.facts, when (event.priority) {
                CarePriority.P1_SAFETY -> VisualSeverity.SAFETY
                CarePriority.P2_SUGGESTION -> VisualSeverity.SUGGESTION
                CarePriority.P3_COMPANION -> VisualSeverity.INFORMATION
                CarePriority.P4_PARKED_REWARD -> VisualSeverity.POST_DRIVE
            }, if (safety) "로드메이트 안전 안내" else profile.displayName,
            if (safety) "!" else profile.initials,
            if (safety) CompanionExpression.SERIOUS_FIXED else CompanionExpression.CONCERNED,
            mode, Collections.unmodifiableList(actions), record.stage,
            if (record.detailRequested && mode == DrivingPresentationMode.FULL_PARKED) SystemCareTemplates.WARNING_DETAIL else null)
    }

    private fun actions(event: CareEvent, facts: CareFacts, mode: DrivingPresentationMode): List<PresentationAction> {
        val result = mutableListOf<PresentationAction>()
        fun add(intent: UserIntent, action: CareAction, label: String) {
            if (action in event.allowedActions) result += PresentationAction(intent, action, label)
        }
        if (event.priority == CarePriority.P1_SAFETY) {
            add(UserIntent.ACKNOWLEDGE, CareAction.ACKNOWLEDGE, "확인했어요")
            return result
        }
        val hvac = event.presentationKey in setOf("care.hvac.temperature_suggestion", "care.hvac.cooling_suggestion") && facts.preferredCelsius != null
        val rest = event.presentationKey == "care.rest.recommendation" && facts.continuousDrivingSeconds != null
        if (hvac) add(UserIntent.ACCEPT, CareAction.REQUEST_HVAC_CHANGE, "좋아요")
        if (rest && mode == DrivingPresentationMode.FULL_PARKED) add(UserIntent.ACCEPT, CareAction.REQUEST_NAVIGATION, "휴식 장소 요청")
        if (result.any { it.intent == UserIntent.ACCEPT }) {
            add(UserIntent.REJECT, CareAction.DISMISS, "괜찮아요")
        } else add(UserIntent.ACKNOWLEDGE, CareAction.ACKNOWLEDGE, "확인했어요")
        if (mode == DrivingPresentationMode.FULL_PARKED) {
            if (result.none { it.intent == UserIntent.REJECT }) add(UserIntent.DISMISS, CareAction.DISMISS, "닫기")
            add(UserIntent.REQUEST_LATER, CareAction.DISMISS, "나중에")
            add(UserIntent.OPEN_DETAIL, CareAction.OPEN_WARNING_DETAIL, "안내 보기")
        }
        return result
    }
}
