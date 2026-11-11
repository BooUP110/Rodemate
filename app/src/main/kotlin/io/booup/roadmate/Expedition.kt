package io.booup.roadmate

import io.booup.roadmate.model.*

internal enum class Expedition(val label: String, val theme: String, val asset: String, val province: String?,
    val postcardIndex: Int, val points: List<ExpeditionPoint>) {
    SEOUL("서울", "도시의 푸른 밤", "seoul", "서울", 0, listOf(
        ExpeditionPoint("world-cup-park", "월드컵공원", 126.884, 37.568),
        ExpeditionPoint("yeouido", "여의도한강공원", 126.934, 37.529),
        ExpeditionPoint("gyeongbokgung", "경복궁", 126.977, 37.580),
        ExpeditionPoint("seoul-forest", "서울숲", 127.041, 37.544),
    )),
    GANGNEUNG("강릉", "파도가 그린 길", "gangneung", null, 1, listOf(
        ExpeditionPoint("ojukheon", "오죽헌", 128.878, 37.780),
        ExpeditionPoint("gyeongpodae", "경포대", 128.897, 37.795),
        ExpeditionPoint("gangmun", "강문해변", 128.919, 37.791),
        ExpeditionPoint("anmok", "안목해변", 128.948, 37.772),
    )),
    GYEONGJU("경주", "시간이 머문 길", "gyeongju", null, 2, listOf(
        ExpeditionPoint("daereungwon", "대릉원", 129.212, 35.837),
        ExpeditionPoint("bomun", "보문호", 129.272, 35.843),
        ExpeditionPoint("bulguksa", "불국사", 129.332, 35.790),
        ExpeditionPoint("bonggil", "봉길해변 · 문무대왕릉", 129.483, 35.739),
    )),
    JEJU("제주", "바람과 유채꽃", "jeju", "제주", 3, listOf(
        ExpeditionPoint("hyeopjae", "협재해수욕장", 126.239, 33.394),
        ExpeditionPoint("yongduam", "용두암", 126.512, 33.516),
        ExpeditionPoint("hamdeok", "함덕해수욕장", 126.669, 33.543),
        ExpeditionPoint("seongsan", "성산일출봉", 126.941, 33.459),
    ));

    val region get() = journeyPlaces[postcardIndex]
    val places by lazy {
        points.map { point ->
            JourneyPlace(RegionId("$EXPEDITION_POINT_PREFIX$asset:${point.key}"), point.name,
                "${point.name}의 엽서", region.note, postcardIndex, point.longitude, point.latitude)
        }
    }

    fun next(visited: Set<RegionId>) = places.firstOrNull { it.id !in visited }
    fun revealed(visited: Set<RegionId>): List<JourneyPlace> {
        val next = next(visited)?.id
        return places.filter { it.id in visited || it.id == next }
    }

    fun connection(from: RegionId, to: RegionId, visited: Set<RegionId>): List<JourneyPlace> {
        val points = places
        val start = points.indexOfFirst { it.id == from }
        val end = points.indexOfFirst { it.id == to }
        if (start < 0 || end < 0 || start == end) return emptyList()
        val visible = revealed(visited).map { it.id }.toSet()
        val segment = points.subList(minOf(start, end), maxOf(start, end) + 1)
        if (segment.any { it.id !in visible }) return emptyList()
        return if (start < end) segment else segment.reversed()
    }

    fun demoRank(visits: List<RegionVisitRecord>): Int? {
        val times = places.map { place ->
            visits.firstOrNull { it.regionId == place.id && it.source == RegionEvidenceSource.DEMO }
                ?.firstVisitedAtEpochMillis ?: return null
        }
        val elapsed = times.max() - times.min()
        return 1 + listOf(20L, 45L, 90L).count { elapsed >= it * 60_000 }
    }

    companion object {
        fun containing(id: RegionId?) = entries.firstOrNull { route ->
            route.region.id == id || route.places.any { it.id == id }
        }
    }
}

/** Approximate display anchors for the offline illustration, independent of arrival evidence. */
internal data class ExpeditionPoint(val key: String, val name: String, val longitude: Double, val latitude: Double)

internal fun journeyPlace(id: RegionId?) = Expedition.containing(id)?.let { route ->
    route.places.firstOrNull { it.id == id } ?: route.region
}

internal fun journeyRegionIds(places: Set<RegionId>) = places.mapNotNull { Expedition.containing(it)?.region?.id }.toSet()

internal fun verifiedExpeditionPoint(location: TripLocation?): JourneyPlace? {
    if (location == null || location.source !in setOf(DataSource.VEHICLE_SDK, DataSource.SYSTEM)) return null
    return Expedition.entries.flatMap { it.places }.singleOrNull { it.name == location.label }
}

internal fun verifiedExpeditionArrival(trip: TripRecord): RegionEvidence? {
    if (trip.state != TripState.COMPLETED || trip.source in setOf(DataSource.SIMULATED, DataSource.UNKNOWN, DataSource.USER_INPUT)) return null
    val place = verifiedExpeditionPoint(trip.endLocation) ?: return null
    val end = trip.endedAtEpochMillis ?: return null
    return RegionEvidence(trip.tripId, place.id, place.name, RegionEvidenceSource.VERIFIED_CONTEXT, end, true)
}

data class ExpeditionLandmark(val pointId: RegionId, val name: String,
    val visitors: Set<Int> = emptySet(), val likes: Set<Int> = emptySet()) {
    val points get() = visitors.size * 5
}
