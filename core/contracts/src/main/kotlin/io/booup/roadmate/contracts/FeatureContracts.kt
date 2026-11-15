package io.booup.roadmate.contracts

import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Deterministic decisions only; no character, purchase state or LLM dependency. */
interface CareRuleEngine {
    fun evaluate(context: VehicleContext): CareDecision
    fun reset()
}

/** Presentation only. Shared P1 templates and mandatory facts cannot be overridden. */
interface CompanionPresenter {
    fun present(event: CareEvent, companionId: CompanionId): CompanionMessage
}

/** Future STT-text boundary only. No parser, recognizer, approval bypass or SDK is registered. */
fun interface UserIntentResolver {
    fun resolve(text: String): UserIntent?
}

/** Local persistence boundary; no in-memory implementation pretending to be durable. */
interface TripRepository {
    val state: StateFlow<TripStorageState>
    suspend fun load(): StorageResult<Unit>
    suspend fun save(trip: TripRecord, expectedGeneration: Long): StorageResult<Unit>
    suspend fun checkpoint(active: ActiveTripSnapshot?, expectedGeneration: Long): StorageResult<Unit>
    suspend fun delete(tripId: String): StorageResult<Unit>
    suspend fun clearHistory(): StorageResult<Unit>
    suspend fun setConsent(scope: ConsentScope, granted: Boolean, nowEpochMillis: Long, source: DataSource): StorageResult<Unit>
    suspend fun saveMemory(memory: PersistentEmotionalMemory): StorageResult<Unit>
}

/** Shares the encrypted aggregate; deletion does not alter history/progress consent. */
interface ComfortPreferenceRepository {
    val state:StateFlow<TripStorageState>
    suspend fun setPreferredTemperature(preference:PreferredCabinTemperature?,atEpochMillis:Long,source:DataSource):StorageResult<Unit>
}

/** Same atomic store as TripRepository: cascade and first-visit issuance cannot diverge. */
interface CollectionRepository {
    val state: StateFlow<TripStorageState>
    suspend fun present(tripId: String, region: RegionEvidence?, calendar: CalendarAttributes, generation: Long): StorageResult<Boolean>
    suspend fun reflect(reflection: TripReflection, generation: Long): StorageResult<Unit>
    suspend fun skipReflection(tripId: String, generation: Long): StorageResult<Unit>
    suspend fun completeReview(tripId: String, generation: Long): StorageResult<Unit>
    suspend fun rememberReflection(tripId: String, confirmed: Boolean, generation: Long): StorageResult<Unit>
    suspend fun clearCollection(): StorageResult<Unit>
}

/** Idempotent event ledger; no reward for care acceptance, speed, distance or night driving. */
interface SeasonPassEngine {
    fun resolve(season:Season, data:ProgressData, now:ObservationTime):SeasonView
}

interface SeasonRepository {
    val state: StateFlow<TripStorageState>
    suspend fun installSeasons(seasons:List<Season>):StorageResult<Unit>
    suspend fun reconcileProgress(now:ObservationTime, captureGeneration:Long, progressGeneration:Long):StorageResult<Unit>
    suspend fun claimReward(seasonId:String,rewardId:String,now:ObservationTime,progressGeneration:Long):StorageResult<Unit>
    suspend fun setSimulatedPremium(seasonId:String,entitled:Boolean):StorageResult<Unit>
    suspend fun clearProgress(now:ObservationTime):StorageResult<Unit>
    suspend fun resetSimulatedSeasons(seasons:List<Season>):StorageResult<Unit>
}

/**
 * Future independent control boundary. The implementation must resolve the registered proposal,
 * validate allowed action, fresh explicit approval, permission, capability and safety conditions,
 * then re-read actual state. CALLBACK_SUCCESS is not VERIFIED. Never expose this to an LLM.
 * No vehicle control implementation is registered.
 */
interface ControlGateway {
    suspend fun request(proposal: ControlProposal, approval: UserApproval): ControlState
}
