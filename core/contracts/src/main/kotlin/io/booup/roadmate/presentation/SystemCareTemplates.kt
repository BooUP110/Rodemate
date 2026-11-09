package io.booup.roadmate.presentation

import io.booup.roadmate.contracts.CompanionPresenter
import io.booup.roadmate.model.*
import java.math.BigDecimal

/** System-owned templates: companion profiles cannot supply or override safety copy. */
object SystemCareTemplates {
    const val LIGHT_P1 = "전조등이 꺼져 있어요. 지금 켜 주세요."
    const val IMMEDIATE_P1 = "차량 경고가 감지됐어요. 안전한 곳에 정차한 뒤 차량 상태를 확인해 주세요."
    const val UNCLASSIFIED = "확인되지 않은 차량 경고가 감지됐어요. 계기판의 경고 내용을 확인해 주세요."
    const val WARNING_DETAIL = "계기판의 경고 내용과 차량 사용 설명서를 확인해 주세요. 정확한 원인은 점검이 필요해요."

    fun facts(event: CareEvent): CareFacts = CareFacts(
        preferredCelsius = event.parameters["preferredCelsius"]?.toDoubleOrNull()?.takeIf { it.isFinite() },
        outsideCelsius = event.evidence.firstOrNull { it.signal == "outsideTemperature" }
            ?.value?.toDoubleOrNull()?.takeIf { it.isFinite() },
        continuousDrivingSeconds = event.parameters["continuousDrivingSeconds"]?.toLongOrNull()?.takeIf { it >= 0 },
        warningClassification = when (event.presentationKey) {
            "care.safety.immediate_attention" -> SafetyClassification.IMMEDIATE_ATTENTION
            "care.warning.service_recommended" -> SafetyClassification.SERVICE_RECOMMENDED
            "care.warning.informational" -> SafetyClassification.INFORMATIONAL
            "care.warning.unknown_vehicle_warning" -> SafetyClassification.UNCLASSIFIED
            else -> null
        },
    )
    fun number(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    fun duration(seconds: Long): String {
        val hours = seconds / 3_600; val minutes = seconds % 3_600 / 60; val remaining = seconds % 60
        return listOfNotNull(hours.takeIf { it > 0 }?.let { "${it}시간" },
            minutes.takeIf { it > 0 }?.let { "${it}분" },
            remaining.takeIf { it > 0 || seconds == 0L }?.let { "${it}초" }).joinToString(" ")
    }
}

/** Existing CompanionPresenter contract also remains suitable for a future AudioAdapter. */
class TemplateCompanionPresenter : CompanionPresenter {
    override fun present(event: CareEvent, companionId: CompanionId) =
        CompanionMessage(event.eventId, render(event, CompanionProfiles.find(companionId)).body)

    fun render(event: CareEvent, profile: CompanionProfile): CareCopy {
        val facts = SystemCareTemplates.facts(event)
        if (event.priority == CarePriority.P1_SAFETY) return CareCopy("안전 안내",
            if (event.presentationKey == "care.safety.low_beam_off") SystemCareTemplates.LIGHT_P1
            else SystemCareTemplates.IMMEDIATE_P1, facts)
        val warm = profile.tone == CompanionTone.WARM
        val body = when (event.presentationKey) {
            "care.hvac.temperature_suggestion", "care.hvac.cooling_suggestion" -> {
                val outside = facts.outsideCelsius?.let { "외부 기온 ${SystemCareTemplates.number(it)}℃. " }.orEmpty()
                val preferred = facts.preferredCelsius?.let { SystemCareTemplates.number(it) }
                if (preferred == null) "${outside}차량에서 공조 설정을 직접 확인해 주세요."
                else if (warm) "${outside}선호 온도 ${preferred}℃로 맞춰볼까요?"
                else "${outside}선호 온도 ${preferred}℃로 조절할까요?"
            }
            "care.rest.recommendation" -> {
                val duration = facts.continuousDrivingSeconds?.let { "연속 주행 ${SystemCareTemplates.duration(it)}. " }.orEmpty()
                if (warm) "${duration}잠시 쉬어 가는 건 어떨까요?" else "${duration}휴식을 권장해요."
            }
            "care.light.auto_check" -> "자동 모드인데 전조등이 꺼진 것으로 보여요. 한 번 확인해 주세요."
            "care.warning.service_recommended" -> if (warm) "점검 권장 신호가 있어요. 계기판 경고를 함께 확인해 주세요."
                else "점검 권장 신호가 감지됐어요. 계기판 경고를 확인해 주세요."
            "care.warning.informational" -> "차량 안내 신호가 감지됐어요. 계기판 내용을 확인해 주세요."
            "care.warning.unknown_vehicle_warning" -> SystemCareTemplates.UNCLASSIFIED
            else -> "차량의 안내 내용을 확인해 주세요."
        }
        val title = when (event.kind) {
            CareKind.LOW_BEAM -> "전조등 확인"; CareKind.TEMPERATURE -> "온도 제안"
            CareKind.REST -> "휴식 제안"; CareKind.TELLTALE -> "차량 경고 안내"
        }
        return CareCopy(title, body, facts)
    }
}
