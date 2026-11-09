package io.booup.roadmate.postdrive

import io.booup.roadmate.model.*
import java.time.Instant
import java.time.ZoneId

/** Calendar buckets are explicit app examples, never weather or local sunrise/sunset facts. */
class CalendarResolver(private val zone: ZoneId = ZoneId.of("UTC")) {
    fun resolve(epochMillis: Long): CalendarAttributes {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val season = when (date.monthValue) { in 3..5 -> CalendarSeason.SPRING; in 6..8 -> CalendarSeason.SUMMER
            in 9..11 -> CalendarSeason.AUTUMN; else -> CalendarSeason.WINTER }
        val day = when (date.hour) { in 6..11 -> CalendarTimeOfDay.MORNING; in 12..17 -> CalendarTimeOfDay.DAY
            in 18..21 -> CalendarTimeOfDay.EVENING; else -> CalendarTimeOfDay.NIGHT }
        return CalendarAttributes(season, day, zone.id)
    }
}
class MemoryCardProjector(private val calendar: CalendarResolver = CalendarResolver()) {
    fun project(trip: TripRecord, reflection: TripReflection? = null): DriveMemoryCard? {
        val end = trip.endedAtEpochMillis ?: return null
        if (trip.state != TripState.COMPLETED || trip.quality == TripDataQuality.INVALID ||
            end < trip.startedAtEpochMillis || trip.durationMillis < 0 || reflection?.tripId?.let { it != trip.tripId } == true) return null
        return DriveMemoryCard("card:${trip.tripId}", trip.tripId, trip.startedAtEpochMillis, end,
            trip.durationMillis, trip.distanceMeters, trip.startLocation?.label, trip.endLocation?.label,
            trip.routeSummary, calendar.resolve(trip.startedAtEpochMillis),
            trip.careEvents.distinctBy { it.eventId }.groupBy { it.ruleId }.toSortedMap(compareBy { it.name })
                .map { CareSummaryItem(it.key, it.value.size) }, reflection, trip.quality, trip.source)
    }
}
class PostcardPolicy {
    fun evaluate(trip: TripRecord, postDrive: PostDriveState, evidence: RegionEvidence?,
        owned: List<RegionVisitRecord>, mode: RuntimeMode): PostcardEligibility {
        if (trip.state != TripState.COMPLETED || postDrive != PostDriveState.READY) return PostcardEligibility.BLOCKED
        val end = trip.endedAtEpochMillis ?: return PostcardEligibility.BLOCKED
        val sourceAllowed = evidence?.source == if (mode == RuntimeMode.DEMO) RegionEvidenceSource.DEMO else RegionEvidenceSource.VERIFIED_CONTEXT
        if (evidence == null || !evidence.verified || !sourceAllowed || evidence.tripId != trip.tripId ||
            evidence.regionId.value.isBlank() || evidence.displayName.isBlank() ||
            evidence.observedAtEpochMillis !in trip.startedAtEpochMillis..end) return PostcardEligibility.UNAVAILABLE_REGION
        return if (owned.any { it.regionId == evidence.regionId }) PostcardEligibility.ALREADY_OWNED else PostcardEligibility.ELIGIBLE
    }
}
