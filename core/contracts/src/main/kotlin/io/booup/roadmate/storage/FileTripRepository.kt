package io.booup.roadmate.storage

import io.booup.roadmate.contracts.TripRepository
import io.booup.roadmate.contracts.CollectionRepository
import io.booup.roadmate.contracts.SeasonRepository
import io.booup.roadmate.progress.*
import io.booup.roadmate.postdrive.PostcardPolicy
import io.booup.roadmate.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.util.Collections
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class StoreException(val code: StorageError) : IOException()
internal fun verifyStore(condition: Boolean, error: StorageError = StorageError.INVALID_DATA) {
    if (!condition) throw StoreException(error)
}

/** App-private versioned storage. IO + fsync + atomic replace precede StateFlow publication. */
class FileTripRepository(private val directory: File, private val mode: RuntimeMode,
    private val collectionRetention: CollectionRetentionPolicy = CollectionRetentionPolicy.KEEP_WITHOUT_SOURCE_TRIP) : TripRepository, CollectionRepository, SeasonRepository {
    private val mutableState = MutableStateFlow(TripStorageState())
    override val state = mutableState.asStateFlow()
    private val file get() = File(directory, "trips.v1.bin")
    private val codec = TripStoreCodec(mode)
    private val transactionMutex=Mutex()

    override suspend fun load(): StorageResult<Unit> = transaction(write = false) { it }
    override suspend fun save(trip: TripRecord, expectedGeneration: Long): StorageResult<Unit> = transaction { current ->
        requireHistory(current, expectedGeneration)
        verifyStore(current.trips.none { it.tripId == trip.tripId }, StorageError.DUPLICATE_ID)
        verifyStore(current.active?.tripId?.let { it == trip.tripId } != false)
        current.copy(trips = current.trips + trip, active = null)
    }
    override suspend fun checkpoint(active: ActiveTripSnapshot?, expectedGeneration: Long): StorageResult<Unit> = transaction { current ->
        requireHistory(current, expectedGeneration)
        verifyStore(active == null || current.trips.none { it.tripId == active.tripId }, StorageError.DUPLICATE_ID)
        current.copy(active = active)
    }
    override suspend fun delete(tripId: String): StorageResult<Unit> = transaction { current ->
        verifyStore(current.trips.any { it.tripId == tripId } || current.active?.tripId == tripId, StorageError.NOT_FOUND)
        cascade(current.copy(trips = current.trips.filterNot { it.tripId == tripId },
            active = current.active?.takeUnless { it.tripId == tripId },
            memories = current.memories.filterNot { it.tripId == tripId }, captureGeneration = current.captureGeneration + 1), setOf(tripId))
    }
    override suspend fun clearHistory(): StorageResult<Unit> = transaction { current ->
        cascade(current.copy(trips = emptyList(), active = null, memories = emptyList(), captureGeneration = current.captureGeneration + 1), current.trips.map { it.tripId }.toSet())
    }
    override suspend fun setConsent(scope: ConsentScope, granted: Boolean, nowEpochMillis: Long, source: DataSource): StorageResult<Unit> = transaction { current ->
        verifyStore(nowEpochMillis >= 0 && (if (mode == RuntimeMode.DEMO) source == DataSource.SIMULATED else source == DataSource.USER_INPUT))
        val old = current.consents[scope]
        verifyStore(nowEpochMillis >= (old?.revokedAtEpochMillis ?: old?.grantedAtEpochMillis ?: 0))
        val record = ConsentRecord(scope, granted, if (granted) nowEpochMillis else old?.grantedAtEpochMillis,
            if (granted) null else nowEpochMillis, source, "internal-v1")
        var next = current.copy(consents = current.consents + (scope to record))
        if (!granted && scope == ConsentScope.EMOTIONAL_MEMORY) next = next.copy(memories = emptyList())
        if (!granted && scope == ConsentScope.TRIP_HISTORY) next = cascade(next.copy(trips = emptyList(), active = null,
            memories = emptyList(), captureGeneration = current.captureGeneration + 1), current.trips.map { it.tripId }.toSet())
        next
    }
    override suspend fun saveMemory(memory: PersistentEmotionalMemory): StorageResult<Unit> = transaction { current ->
        requireHistory(current, current.captureGeneration)
        verifyStore(current.consents[ConsentScope.EMOTIONAL_MEMORY]?.granted == true, StorageError.CONSENT_REQUIRED)
        verifyStore(memory.confirmedByUser, StorageError.CONSENT_REQUIRED)
        verifyStore(current.trips.any { it.tripId == memory.tripId }, StorageError.NOT_FOUND)
        verifyStore(current.memories.none { it.memoryId == memory.memoryId }, StorageError.DUPLICATE_ID)
        current.copy(memories = current.memories + memory)
    }
    override suspend fun present(tripId: String, region: RegionEvidence?, calendar: CalendarAttributes, generation: Long): StorageResult<Boolean> {
        var reveal = false
        val result = transaction { current ->
            val trip = parent(current, tripId, generation)
            val old = current.postDrive[tripId] ?: PostDriveMetadata(tripId)
            if (!trip.reviewed && !old.revealConsumed && PostcardPolicy().evaluate(trip, PostDriveState.READY, region, current.visits, mode) == PostcardEligibility.ELIGIBLE) {
                val evidence = requireNotNull(region)
                reveal = true
                current.copy(postDrive = current.postDrive + (tripId to old.copy(revealConsumed = true)),
                    visits = current.visits + RegionVisitRecord(evidence.regionId, evidence.observedAtEpochMillis, tripId, false, evidence.source),
                    postcards = current.postcards + Postcard(PostcardKey(evidence.regionId), evidence.displayName, tripId, false,
                        requireNotNull(trip.endedAtEpochMillis), calendar, evidence.source))
            } else current.copy(postDrive = current.postDrive + (tripId to old))
        }
        return when (result) { is StorageResult.Success -> StorageResult.Success(reveal); is StorageResult.Failure -> result }
    }
    override suspend fun reflect(reflection: TripReflection, generation: Long): StorageResult<Unit> = transaction { current ->
        parent(current, reflection.tripId, generation)
        verifyStore(reflection.persistence == ReflectionPersistence.TRIP_LOCAL)
        val old = current.postDrive[reflection.tripId] ?: PostDriveMetadata(reflection.tripId)
        // One current reflection per trip; explicit edits replace it, repeated identical submissions are no-ops.
        current.copy(postDrive = current.postDrive + (reflection.tripId to old.copy(reflection = reflection, skipped = false)))
    }
    override suspend fun skipReflection(tripId: String, generation: Long): StorageResult<Unit> = transaction { current ->
        parent(current, tripId, generation)
        val old = current.postDrive[tripId] ?: PostDriveMetadata(tripId)
        current.copy(postDrive = current.postDrive + (tripId to old.copy(reflection = null, skipped = true)))
    }
    override suspend fun completeReview(tripId: String, generation: Long): StorageResult<Unit> = transaction { current ->
        parent(current, tripId, generation)
        val old = current.postDrive[tripId] ?: PostDriveMetadata(tripId)
        current.copy(trips = current.trips.map { if (it.tripId == tripId) it.copy(reviewed = true) else it },
            postDrive = current.postDrive + (tripId to old.copy(reviewed = true)))
    }
    override suspend fun rememberReflection(tripId: String, confirmed: Boolean, generation: Long): StorageResult<Unit> = transaction { current ->
        parent(current, tripId, generation)
        verifyStore(confirmed && current.consents[ConsentScope.EMOTIONAL_MEMORY]?.granted == true, StorageError.CONSENT_REQUIRED)
        val reflection = current.postDrive[tripId]?.reflection
        verifyStore(reflection != null, StorageError.NOT_FOUND)
        val text = reflection!!.text ?: reflection.mood?.name
        verifyStore(!text.isNullOrBlank())
        val memory = PersistentEmotionalMemory("reflection:$tripId", tripId, requireNotNull(text), reflection.createdAtEpochMillis, reflection.source, true)
        val old = current.memories.singleOrNull { it.memoryId == memory.memoryId }
        if (old == memory) current else current.copy(memories = current.memories.filterNot { it.memoryId == memory.memoryId } + memory)
    }
    override suspend fun clearCollection(): StorageResult<Unit> = transaction { current -> current.copy(visits = emptyList(), postcards = emptyList(),
        progression=current.progression.copy(ledger=current.progression.ledger.map { if(it.sourceType in setOf(ProgressEventType.FIRST_REGION_VISIT,ProgressEventType.POSTCARD_ACQUIRED))
            it.copy(sourceId=null,sourceTripId=null,sourceDeleted=true) else it },generation=current.progression.generation+1)) }
    override suspend fun installSeasons(seasons:List<Season>):StorageResult<Unit> = transaction { current ->
        try { seasons.forEach { SafeProgressEngine().validate(it) } } catch (_:IllegalArgumentException) { throw StoreException(StorageError.INVALID_DATA) }
        catch (_:java.time.DateTimeException) { throw StoreException(StorageError.INVALID_DATA) }
        verifyStore(seasons.map { it.seasonId }.distinct().size==seasons.size)
        seasons.forEach { incoming -> verifyStore(current.progression.seasons.singleOrNull { it.seasonId==incoming.seasonId }?.let { it==incoming }!=false) }
        val combined=(current.progression.seasons+seasons).distinctBy { it.seasonId }.sortedBy { it.startsAtEpochMillis }
        verifyStore(combined.zipWithNext().all { it.first.endsAtEpochMillis<=it.second.startsAtEpochMillis } && combined.map { it.config.zoneId }.distinct().size<=1)
        current.copy(progression=current.progression.copy(seasons=combined))
    }
    override suspend fun reconcileProgress(now:ObservationTime,captureGeneration:Long,progressGeneration:Long):StorageResult<Unit> = transaction { current ->
        verifyStore(current.captureGeneration==captureGeneration && current.progression.generation==progressGeneration,StorageError.NOT_FOUND)
        var data=current.progression
        val season=data.seasons.singleOrNull { now.epochMillis in it.startsAtEpochMillis until it.endsAtEpochMillis }
        if(season!=null) ProgressEventFactory().events(current).forEach { event ->
            SafeProgressEngine().award(data,season,event,now,mode)?.let { data=data.copy(ledger=data.ledger+it) }
        }
        current.copy(progression=data)
    }
    override suspend fun claimReward(seasonId:String,rewardId:String,now:ObservationTime,progressGeneration:Long):StorageResult<Unit> = transaction { current ->
        val data=current.progression
        verifyStore(data.generation==progressGeneration,StorageError.NOT_FOUND)
        val season=data.seasons.singleOrNull { it.seasonId==seasonId } ?: throw StoreException(StorageError.NOT_FOUND)
        val reward=SafeProgressEngine().resolve(season,data,now).rewards.singleOrNull { it.reward.rewardId==rewardId } ?: throw StoreException(StorageError.NOT_FOUND)
        if(reward.state==RewardClaimState.CLAIMED) current else {
            verifyStore(reward.state==RewardClaimState.AVAILABLE,StorageError.CONSENT_REQUIRED)
            current.copy(progression=data.copy(claims=data.claims+RewardClaim(seasonId,rewardId,now.epochMillis,
                if(mode==RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.USER_INPUT)))
        }
    }
    override suspend fun setSimulatedPremium(seasonId:String,entitled:Boolean):StorageResult<Unit> = transaction { current ->
        verifyStore(mode==RuntimeMode.DEMO,StorageError.WRONG_ENVIRONMENT)
        verifyStore(current.progression.seasons.any { it.seasonId==seasonId },StorageError.NOT_FOUND)
        current.copy(progression=current.progression.copy(premiumSeasons=if(entitled) current.progression.premiumSeasons+seasonId else current.progression.premiumSeasons-seasonId))
    }
    override suspend fun clearProgress(now:ObservationTime):StorageResult<Unit> = transaction { current ->
        verifyStore(now.epochMillis>=current.progression.resetAtEpochMillis && now.epochMillis >= (current.progression.ledger.maxOfOrNull { it.awardedAtEpochMillis }?:0))
        current.copy(progression=current.progression.copy(ledger=emptyList(),claims=emptyList(),generation=current.progression.generation+1,resetAtEpochMillis=now.epochMillis))
    }
    override suspend fun resetSimulatedSeasons(seasons:List<Season>):StorageResult<Unit> = transaction { current ->
        verifyStore(mode==RuntimeMode.DEMO,StorageError.WRONG_ENVIRONMENT)
        current.copy(progression=ProgressData(seasons=seasons,generation=current.progression.generation+1))
    }
    private fun parent(current: TripStorageState, id: String, generation: Long): TripRecord {
        requireHistory(current, generation)
        return current.trips.singleOrNull { it.tripId == id } ?: throw StoreException(StorageError.NOT_FOUND)
    }
    private fun cascade(current: TripStorageState, ids: Set<String>): TripStorageState = current.copy(
        progression=current.progression.copy(ledger=current.progression.ledger.map {
            if(it.sourceTripId in ids) it.copy(sourceId=null,sourceTripId=null,sourceDeleted=true) else it }),
        postDrive = current.postDrive.filterKeys { it !in ids },
        visits = if (collectionRetention == CollectionRetentionPolicy.DELETE_WITH_SOURCE_TRIP) current.visits.filterNot { it.sourceTripId in ids }
            else current.visits.map { if (it.sourceTripId in ids) it.copy(sourceTripId = null, sourceTripDeleted = true) else it },
        postcards = if (collectionRetention == CollectionRetentionPolicy.DELETE_WITH_SOURCE_TRIP) current.postcards.filterNot { it.sourceTripId in ids }
            else current.postcards.map { if (it.sourceTripId in ids) it.copy(sourceTripId = null, sourceTripDeleted = true) else it })
    private fun requireHistory(current: TripStorageState, generation: Long) {
        verifyStore(current.captureGeneration == generation, StorageError.NOT_FOUND)
        verifyStore(current.consents[ConsentScope.TRIP_HISTORY]?.granted == true, StorageError.CONSENT_REQUIRED)
    }
    private suspend fun transaction(write: Boolean = true, mutate: (TripStorageState) -> TripStorageState): StorageResult<Unit> = transactionMutex.withLock { withContext(Dispatchers.IO) {
        var readSucceeded = false
        try {
            Files.createDirectories(directory.toPath())
            FileChannel.open(File(directory, "store.lock").toPath(), CREATE, WRITE).use { channel ->
                channel.lock().use {
                    // Reload under an OS file lock: separate Repository instances cannot overwrite stale state.
                    val current = if (file.exists()) codec.read(file) else TripStorageState(loaded = true)
                    readSucceeded = true
                    var next = current
                    try {
                        next = freeze(mutate(current))
                        codec.validate(next)
                        if ((write && next != current) || (file.exists() && codec.version(file) < 3)) {
                            val staging = File(directory, "trips.pending")
                            codec.write(staging, next)
                            Files.move(staging.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                        }
                    } catch (error: Exception) {
                        mutableState.value = freeze(current)
                        throw error
                    }
                    mutableState.value = next
                }
            }
            StorageResult.Success(Unit)
        } catch (error: StoreException) {
            if (!readSucceeded) mutableState.value = mutableState.value.copy(error = error.code)
            StorageResult.Failure(error.code)
        } catch (_: IOException) {
            if (!readSucceeded) mutableState.value = mutableState.value.copy(error = StorageError.IO)
            StorageResult.Failure(StorageError.IO)
        } catch (_: java.nio.channels.OverlappingFileLockException) {
            StorageResult.Failure(StorageError.IO)
        }
    } }
    private fun freeze(input: TripStorageState): TripStorageState {
        fun events(values: List<TripCareOccurrence>) = Collections.unmodifiableList(ArrayList(values))
        return input.copy(loaded = true, error = null,
            trips = Collections.unmodifiableList(input.trips.map { it.copy(careEvents = events(it.careEvents)) }),
            active = input.active?.let { it.copy(careEvents = events(it.careEvents)) },
            consents = Collections.unmodifiableMap(LinkedHashMap(input.consents)),
            memories = Collections.unmodifiableList(ArrayList(input.memories)),
            postDrive = Collections.unmodifiableMap(LinkedHashMap(input.postDrive)),
            visits = Collections.unmodifiableList(ArrayList(input.visits)), postcards = Collections.unmodifiableList(ArrayList(input.postcards)),
            progression=input.progression.copy(seasons=Collections.unmodifiableList(input.progression.seasons.map { it.copy(
                levels=Collections.unmodifiableList(ArrayList(it.levels)),rewards=Collections.unmodifiableList(ArrayList(it.rewards)),missions=Collections.unmodifiableList(ArrayList(it.missions))) }),
                ledger=Collections.unmodifiableList(ArrayList(input.progression.ledger)),claims=Collections.unmodifiableList(ArrayList(input.progression.claims)),
                premiumSeasons=Collections.unmodifiableSet(HashSet(input.progression.premiumSeasons))))
    }
}
