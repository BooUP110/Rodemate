package io.booup.roadmate.pet

import io.booup.roadmate.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Manual region search only. Unknown fields stay unknown; no text-to-rule guessing. */
class PetPlaceSearch(private val places: List<PetPublicPlace> = PetPublicPlaceCatalog.places) {
    fun search(query: PetPlaceQuery, today: LocalDate): List<PetPlaceMatch> {
        if(query.keyword.length>80 || query.weightKg?.let { !it.isFinite() || it<=0 || it>200 }==true) return emptyList()
        val term=query.keyword.trim()
        return places.filter { (query.region==null || it.region==query.region) &&
            (query.category==null || it.category==query.category) &&
            (term.isEmpty() || listOf(it.name,it.address,it.conditions).any { text -> text.contains(term,ignoreCase=true) }) }
            .map { place ->
                val yes=mutableListOf<String>(); val unknown=mutableListOf<String>(); val no=mutableListOf<String>()
                if(query.kind in place.reportedKinds) yes+="선택한 동물 종류의 동반 안내가 있어요."
                else unknown+="선택한 동물 종류의 동반 여부는 원문에서 확인되지 않았어요."
                val limit=place.weightBelowKg
                val weight=query.weightKg
                if(limit==null) unknown+="체중 제한은 추가 확인이 필요해요."
                else if(weight==null) unknown+="체중을 입력하면 명시된 제한과 비교할 수 있어요."
                else if(weight<limit) yes+="입력 체중이 명시된 ${limit.toInt()}kg 미만 조건에 들어가요."
                else no+="명시된 ${limit.toInt()}kg 미만 조건에 입력 체중이 들어가지 않아요."
                when(place.carrierRequired) {
                    true -> when(query.carrier) {
                        PetCarrierAnswer.YES->yes+="필수 이동장을 준비했다고 선택했어요."
                        PetCarrierAnswer.NO->no+="이동장이 필수로 안내된 장소예요."
                        PetCarrierAnswer.UNKNOWN->unknown+="이동장 준비 여부를 확인해 주세요."
                    }
                    else -> unknown+="이동장 필요 여부·사용 구역은 원문과 현장에 확인해 주세요."
                }
                if(place.allowedArea==null) unknown+="실내·실외와 세부 이용 구역은 확인되지 않았어요."
                val checked=LocalDate.parse(place.checkedOn)
                val stale=today<checked || ChronoUnit.DAYS.between(checked,today)>30
                if(today<checked) unknown+="기기 날짜와 안내 확인일을 확인해 주세요."
                else if(stale) unknown+="정보 확인 후 시간이 지났어요. 방문 전에 다시 확인해 주세요."
                PetPlaceMatch(place,when { no.isNotEmpty()->PetConditionFit.CONFLICT
                    query.kind !in place.reportedKinds || stale ->PetConditionFit.CHECK_REQUIRED
                    else->PetConditionFit.PARTIAL_MATCH },yes,unknown,no,stale)
            }.sortedWith(compareBy<PetPlaceMatch> { when(it.fit) {
                PetConditionFit.PARTIAL_MATCH->0; PetConditionFit.CHECK_REQUIRED->1; PetConditionFit.CONFLICT->2
            } }.thenBy { it.place.name })
    }
}
