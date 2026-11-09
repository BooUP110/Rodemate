package io.booup.roadmate.progress

import io.booup.roadmate.model.*
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

fun progressKey(value:String):String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }

/** Only already committed facts are inputs. No VehicleContext, care payload, distance or driving-duration reward input. */
class ProgressEventFactory {
    fun events(state:TripStorageState):List<ProgressEvent> = state.trips.flatMap { trip ->
        if(trip.state!=TripState.COMPLETED || trip.endedAtEpochMillis==null || trip.confirmedDrivingMillis<=0 ||
            trip.quality !in setOf(TripDataQuality.COMPLETE,TripDataQuality.PARTIAL)) return@flatMap emptyList()
        fun event(type:ProgressEventType,sourceId:String)=ProgressEvent(progressKey("${type.name}:$sourceId"),type,sourceId,
            trip.tripId,requireNotNull(trip.endedAtEpochMillis),trip.source,trip.quality)
        buildList {
            add(event(ProgressEventType.TRIP_COMPLETED,trip.tripId))
            if(state.visits.any { it.sourceTripId==trip.tripId && !it.sourceTripDeleted })
                state.visits.filter { it.sourceTripId==trip.tripId && !it.sourceTripDeleted }.forEach { add(event(ProgressEventType.FIRST_REGION_VISIT,it.regionId.value)) }
            state.postcards.filter { it.sourceTripId==trip.tripId && !it.sourceTripDeleted }.forEach { add(event(ProgressEventType.POSTCARD_ACQUIRED,it.key.regionId.value)) }
            if(state.postDrive[trip.tripId]?.reflection!=null) add(event(ProgressEventType.REFLECTION_COMPLETED,trip.tripId))
            if(state.postDrive[trip.tripId]?.reviewed==true) add(event(ProgressEventType.POST_DRIVE_SESSION_COMPLETED,trip.tripId))
        }
    }.sortedWith(compareBy({it.occurredAtEpochMillis},{it.type.ordinal},{it.eventId}))
}

object ProgressEligibilityPolicy {
    val safeEvents=setOf(ProgressEventType.TRIP_COMPLETED,ProgressEventType.FIRST_REGION_VISIT,ProgressEventType.POSTCARD_ACQUIRED,
        ProgressEventType.REFLECTION_COMPLETED,ProgressEventType.POST_DRIVE_SESSION_COMPLETED)
    val missionEvents=setOf(ProgressEventType.FIRST_REGION_VISIT,ProgressEventType.POSTCARD_ACQUIRED,ProgressEventType.POST_DRIVE_SESSION_COMPLETED)
    fun allows(event:ProgressEvent, mode:RuntimeMode)=event.type in safeEvents && event.quality in setOf(TripDataQuality.COMPLETE,TripDataQuality.PARTIAL) &&
        (if(mode==RuntimeMode.DEMO) event.source==DataSource.SIMULATED else event.source !in setOf(DataSource.SIMULATED,DataSource.UNKNOWN))
}

class SafeProgressEngine : io.booup.roadmate.contracts.SeasonPassEngine {
    fun validate(season:Season) {
        require(season.seasonId.isNotBlank() && season.title.isNotBlank() && season.startsAtEpochMillis>=0 && season.endsAtEpochMillis>season.startsAtEpochMillis)
        require(season.levels.isNotEmpty() && season.levels.first()==0L && season.levels.zipWithNext().all { it.first<it.second })
        val c=season.config; require(listOf(c.tripXp,c.postcardXp,c.postDriveXp,c.dailyCap,c.tripCap).all { it in 0..1_000_000 })
        ZoneId.of(c.zoneId)
        require(season.rewards.map { it.rewardId }.distinct().size==season.rewards.size && season.missions.map { it.missionId }.distinct().size==season.missions.size)
        require(season.missions.all { it.missionId.isNotBlank() && it.title.isNotBlank() && it.target in 1..10_000 && it.eventType in ProgressEligibilityPolicy.missionEvents })
        require(season.rewards.all { it.rewardId.isNotBlank() && it.title.isNotBlank() && it.level in 1..season.levels.size &&
            (it.missionId==null || season.missions.any { m -> m.missionId==it.missionId }) })
    }
    fun award(data:ProgressData,season:Season,event:ProgressEvent,now:ObservationTime,mode:RuntimeMode):XpLedgerEntry? {
        validate(season)
        if(!ProgressEligibilityPolicy.allows(event,mode) || event.occurredAtEpochMillis<=data.resetAtEpochMillis ||
            now.epochMillis !in season.startsAtEpochMillis until season.endsAtEpochMillis ||
            event.occurredAtEpochMillis !in season.startsAtEpochMillis until season.endsAtEpochMillis || event.occurredAtEpochMillis>now.epochMillis ||
            data.ledger.any { it.progressEventId==event.eventId } || now.epochMillis < (data.ledger.maxOfOrNull { it.awardedAtEpochMillis }?:0)) return null
        val day=Instant.ofEpochMilli(now.epochMillis).atZone(ZoneId.of(season.config.zoneId)).toLocalDate().toString()
        val tripKey=progressKey(event.tripId)
        val requested=when(event.type) { ProgressEventType.TRIP_COMPLETED -> season.config.tripXp
            ProgressEventType.POSTCARD_ACQUIRED -> season.config.postcardXp
            ProgressEventType.POST_DRIVE_SESSION_COMPLETED -> season.config.postDriveXp; else -> 0 }
        // Global daily/trip sums include other seasons, preventing a same-day season transition from resetting the cap.
        val daily=data.ledger.filter { it.dayKey==day }.sumOf { it.awardedXp }
        val trip=data.ledger.filter { it.tripKey==tripKey }.sumOf { it.awardedXp }
        val dailyRoom=(season.config.dailyCap-daily).coerceAtLeast(0); val tripRoom=(season.config.tripCap-trip).coerceAtLeast(0)
        val xp=minOf(requested,dailyRoom,tripRoom)
        val reason=when { requested==0L -> XpReason.NON_XP_EVENT; xp<requested && dailyRoom<=tripRoom -> XpReason.DAILY_CAP
            xp<requested -> XpReason.TRIP_CAP; else -> XpReason.AWARDED }
        return XpLedgerEntry(event.eventId,season.seasonId,event.eventId,event.type,event.sourceId,event.tripId,false,tripKey,xp,now.epochMillis,day,reason,event.source)
    }
    override fun resolve(season:Season,data:ProgressData,now:ObservationTime):SeasonView {
        validate(season)
        val ledger=data.ledger.filter { it.seasonId==season.seasonId }
        val total=ledger.sumOf { it.awardedXp }; val level=season.levels.count { it<=total }
        val claims=data.claims.filter { it.seasonId==season.seasonId }.map { it.rewardId }.toSet()
        val missions=season.missions.map { m -> val count=ledger.count { it.sourceType==m.eventType }.coerceAtMost(m.target)
            MissionProgress(m,count,count>=m.target,m.visibility==MissionVisibility.VISIBLE || count>=m.target) }
        val archived=now.epochMillis>=season.endsAtEpochMillis
        val entitled=season.seasonId in data.premiumSeasons
        return SeasonView(season,SeasonProgress(season.seasonId,ledger.map { it.progressEventId }.toSet(),total,level,claims,entitled,
            maxOf(ledger.maxOfOrNull { it.awardedAtEpochMillis }?:0,data.claims.filter { it.seasonId==season.seasonId }.maxOfOrNull { it.claimedAtEpochMillis }?:0)),
            season.levels.getOrNull(level)?.minus(total),archived,season.rewards.map { reward ->
                val state=when { reward.rewardId in claims -> RewardClaimState.CLAIMED
                    now.epochMillis<season.startsAtEpochMillis || reward.level>level || (reward.track==RewardTrack.PREMIUM && !entitled) ||
                        (reward.missionId!=null && missions.none { it.mission.missionId==reward.missionId && it.completed }) -> RewardClaimState.LOCKED
                    archived && !season.config.allowExpiredClaims -> RewardClaimState.UNCLAIMED_EXPIRED
                    else -> RewardClaimState.AVAILABLE }
                RewardView(reward,state)
            },missions)
    }
}
class ExplorationResolver {
    fun resolve(visits:List<RegionVisitRecord>,mode:RuntimeMode,catalog:RegionCatalog?=null):ExplorationProgress {
        val source=if(mode==RuntimeMode.DEMO) RegionEvidenceSource.DEMO else RegionEvidenceSource.VERIFIED_CONTEXT
        val ids=visits.filter { it.source==source }.map { it.regionId }.toSet()
        val known=catalog?.takeIf { it.source==source && it.regionIds.isNotEmpty() && it.regionIds.containsAll(ids) }?.regionIds?.size
        return ExplorationProgress(ids,ids.size,known,known?.let { ids.size*100.0/it })
    }
}
