package io.booup.roadmate

import io.booup.roadmate.model.RegionId

data class JourneyPlace(val id: RegionId, val name: String, val title: String, val note: String, val art: Int,
    val longitude: Double, val latitude: Double)

internal val journeyPlaces = listOf(
    JourneyPlace(RegionId("91817680B55576639555152"), "서울 종로", "서울의 푸른 밤", "도시의 불빛을 담은 한 장", 0, 126.98, 37.59),
    JourneyPlace(RegionId("91817680B99242230797556"), "강릉", "강릉의 푸른 파도", "바다와 등대가 있는 엽서", 1, 128.85, 37.76),
    JourneyPlace(RegionId("91817680B5353031397212"), "경주", "경주의 고요한 저녁", "오래된 풍경을 간직한 엽서", 2, 129.19, 35.84),
    JourneyPlace(RegionId("91817680B29624883381183"), "제주시", "제주의 노란 봄", "유채꽃과 섬 풍경을 담은 엽서", 3, 126.52, 33.49),
)

data class JourneyVisitUi(val place: JourneyPlace? = null, val running: Boolean = false, val saved: Boolean = false)
