package io.booup.roadmate.storage

import io.booup.roadmate.model.*
import io.booup.roadmate.progress.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.ZoneId

/** Schema3 extension. Shares the outer mode header, bounded aggregate and atomic transaction. */
internal class ProgressStoreCodec(private val mode:RuntimeMode) {
    fun validate(state:TripStorageState) {
        val d=state.progression
        verifyStore(d.generation>=0 && d.resetAtEpochMillis>=0 && listOf(d.seasons.size,d.ledger.size,d.claims.size).all { it<=10_000 },StorageError.LIMIT_EXCEEDED)
        try { d.seasons.forEach { SafeProgressEngine().validate(it) } } catch(_:IllegalArgumentException) { throw StoreException(StorageError.INVALID_DATA) }
        catch(_:java.time.DateTimeException) { throw StoreException(StorageError.INVALID_DATA) }
        val ordered=d.seasons.sortedBy { it.startsAtEpochMillis }
        verifyStore(d.seasons.map { it.seasonId }.distinct().size==d.seasons.size && ordered.zipWithNext().all { it.first.endsAtEpochMillis<=it.second.startsAtEpochMillis })
        verifyStore(d.seasons.map { it.config.zoneId }.distinct().size<=1 && (mode==RuntimeMode.DEMO || d.premiumSeasons.isEmpty()),StorageError.WRONG_ENVIRONMENT)
        verifyStore(d.premiumSeasons.all { id -> d.seasons.any { it.seasonId==id } })
        verifyStore(d.ledger.map { it.progressEventId }.distinct().size==d.ledger.size && d.ledger.map { it.ledgerId }.distinct().size==d.ledger.size)
        val hash=Regex("[0-9a-f]{64}")
        val awards = SafeProgressEngine(explorationRewards = true)
        val trips = state.trips.associateBy { it.tripId }
        val daily=mutableMapOf<String,Long>(); val perTrip=mutableMapOf<String,Long>()
        var previous=0L
        d.ledger.forEach { l ->
            val s=d.seasons.singleOrNull { it.seasonId==l.seasonId } ?: throw StoreException(StorageError.INVALID_DATA)
            verifyStore(l.ledgerId==l.progressEventId && hash.matches(l.ledgerId) && hash.matches(l.tripKey) &&
                l.sourceType in ProgressEligibilityPolicy.safeEvents && l.awardedXp>=0 && l.awardedAtEpochMillis>=previous &&
                l.awardedAtEpochMillis in s.startsAtEpochMillis until s.endsAtEpochMillis)
            previous=l.awardedAtEpochMillis
            verifyStore(if(mode==RuntimeMode.DEMO) l.source==DataSource.SIMULATED else l.source !in setOf(DataSource.SIMULATED,DataSource.UNKNOWN),StorageError.WRONG_ENVIRONMENT)
            val trip = l.sourceTripId?.let(trips::get)
            verifyStore(if(l.sourceDeleted) l.sourceId==null && l.sourceTripId==null else l.sourceId!=null && trip != null &&
                progressKey(requireNotNull(l.sourceTripId))==l.tripKey && progressKey("${l.sourceType.name}:${l.sourceId}")==l.progressEventId)
            val day=Instant.ofEpochMilli(l.awardedAtEpochMillis).atZone(ZoneId.of(s.config.zoneId)).toLocalDate().toString()
            verifyStore(l.dayKey==day)
            val maximum = awards.maximumAward(s.config, l.sourceType, l.sourceId, trip?.distanceMeters, l.sourceDeleted)
            verifyStore(l.awardedXp<=maximum && (maximum!=0L || l.reason==XpReason.NON_XP_EVENT))
            if (awards.waypointBonus(l.sourceType, l.sourceId, l.sourceDeleted)) {
                if (!l.sourceDeleted && l.awardedXp > 0L) verifyStore(state.visits.any { visit ->
                    visit.regionId.value == l.sourceId && visit.sourceTripId == l.sourceTripId && !visit.sourceTripDeleted
                })
            } else {
                verifyStore(l.awardedXp<=(s.config.dailyCap-(daily[day]?:0)).coerceAtLeast(0) &&
                    l.awardedXp<=(s.config.tripCap-(perTrip[l.tripKey]?:0)).coerceAtLeast(0))
                daily[day]=(daily[day]?:0)+l.awardedXp; perTrip[l.tripKey]=(perTrip[l.tripKey]?:0)+l.awardedXp
            }
        }
        verifyStore(d.claims.map { it.seasonId to it.rewardId }.distinct().size==d.claims.size)
        d.claims.forEach { c ->
            val s=d.seasons.singleOrNull { it.seasonId==c.seasonId } ?: throw StoreException(StorageError.INVALID_DATA)
            verifyStore(c.claimedAtEpochMillis>=s.startsAtEpochMillis && (s.config.allowExpiredClaims || c.claimedAtEpochMillis<s.endsAtEpochMillis) &&
                s.rewards.any { it.rewardId==c.rewardId })
            verifyStore(if(mode==RuntimeMode.DEMO) c.source==DataSource.SIMULATED else c.source==DataSource.USER_INPUT,StorageError.WRONG_ENVIRONMENT)
            val view=SafeProgressEngine().resolve(s,d.copy(ledger=d.ledger.filter { it.awardedAtEpochMillis<=c.claimedAtEpochMillis }),ObservationTime(c.claimedAtEpochMillis,0))
            val reward=s.rewards.single { it.rewardId==c.rewardId }
            verifyStore(reward.level<=view.progress.currentLevel && (reward.missionId==null || view.missions.any { it.mission.missionId==reward.missionId && it.completed }) &&
                (mode==RuntimeMode.DEMO || reward.track==RewardTrack.FREE))
        }
    }
    fun write(o:DataOutputStream,d:ProgressData) {
        o.writeLong(d.generation); o.writeLong(d.resetAtEpochMillis)
        o.writeInt(d.seasons.size); d.seasons.forEach { s ->
            o.text(s.seasonId); o.text(s.title); o.writeLong(s.startsAtEpochMillis); o.writeLong(s.endsAtEpochMillis)
            o.writeInt(s.levels.size); s.levels.forEach { o.writeLong(it) }
            o.writeInt(s.rewards.size); s.rewards.forEach { r ->
                o.text(r.rewardId); o.text(r.title); o.writeInt(r.level); o.text(r.track.name); o.text(r.type.name); o.optional(r.missionId)
                o.writeBoolean(r.pack!=null); r.pack?.let { o.text(it.packId); o.text(it.companionProfileId.value); o.text(it.themeId); o.text(it.expressionSetId) }
            }
            o.writeInt(s.missions.size); s.missions.forEach { m -> o.text(m.missionId); o.text(m.title); o.text(m.eventType.name); o.writeInt(m.target); o.text(m.visibility.name); o.text(m.safety.name) }
            with(s.config) { o.writeLong(tripXp); o.writeLong(postcardXp); o.writeLong(postDriveXp); o.writeLong(dailyCap); o.writeLong(tripCap); o.text(zoneId); o.writeBoolean(allowExpiredClaims) }
        }
        o.writeInt(d.ledger.size); d.ledger.forEach { l -> o.text(l.ledgerId); o.text(l.seasonId); o.text(l.progressEventId); o.text(l.sourceType.name)
            o.optional(l.sourceId); o.optional(l.sourceTripId); o.writeBoolean(l.sourceDeleted); o.text(l.tripKey); o.writeLong(l.awardedXp)
            o.writeLong(l.awardedAtEpochMillis); o.text(l.dayKey); o.text(l.reason.name); o.text(l.source.name) }
        o.writeInt(d.claims.size); d.claims.forEach { o.text(it.seasonId); o.text(it.rewardId); o.writeLong(it.claimedAtEpochMillis); o.text(it.source.name) }
        o.writeInt(d.premiumSeasons.size); d.premiumSeasons.sorted().forEach { o.text(it) }
    }
    fun read(i:DataInputStream):ProgressData {
        val generation=i.readLong(); val reset=i.readLong()
        val seasons=List(i.count()) {
            val id=i.text(); val title=i.text(); val start=i.readLong(); val end=i.readLong(); val levels=List(i.count()) { i.readLong() }
            val rewards=List(i.count()) { SeasonReward(i.text(),i.text(),i.readInt(),enumValueOf(i.text()),enumValueOf(i.text()),i.optional(),
                if(i.readBoolean()) CompanionPack(i.text(),CompanionId(i.text()),i.text(),i.text()) else null) }
            val missions=List(i.count()) { Mission(i.text(),i.text(),enumValueOf(i.text()),i.readInt(),enumValueOf(i.text()),enumValueOf(i.text())) }
            Season(id,title,start,end,levels,rewards,missions,ProgressConfig(i.readLong(),i.readLong(),i.readLong(),i.readLong(),i.readLong(),i.text(),i.readBoolean()))
        }
        val ledger=List(i.count()) { XpLedgerEntry(i.text(),i.text(),i.text(),enumValueOf(i.text()),i.optional(),i.optional(),i.readBoolean(),i.text(),i.readLong(),i.readLong(),i.text(),enumValueOf(i.text()),enumValueOf(i.text())) }
        val claims=List(i.count()) { RewardClaim(i.text(),i.text(),i.readLong(),enumValueOf(i.text())) }
        val premium=List(i.count()) { i.text() }; verifyStore(premium.distinct().size==premium.size)
        return ProgressData(seasons,ledger,claims,premium.toSet(),generation,reset)
    }
    private fun DataOutputStream.text(s:String) { verifyStore(s.length<=4096,StorageError.LIMIT_EXCEEDED); writeUTF(s) }
    private fun DataInputStream.text()=readUTF().also { verifyStore(it.length<=4096,StorageError.LIMIT_EXCEEDED) }
    private fun DataInputStream.count()=readInt().also { verifyStore(it in 0..10_000,StorageError.LIMIT_EXCEEDED) }
    private fun DataOutputStream.optional(s:String?) { writeBoolean(s!=null); s?.let { text(it) } }
    private fun DataInputStream.optional():String?=if(readBoolean()) text() else null
}
