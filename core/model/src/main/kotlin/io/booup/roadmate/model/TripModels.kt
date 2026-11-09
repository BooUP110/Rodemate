package io.booup.roadmate.model

enum class TripEndSignal { CONFIRMED_END } // Normalized input; NOT a gear/parking/Navi SDK enum.
enum class TripCompletionReason { CONFIRMED_STATIONARY, EXPLICIT_END_EVIDENCE, MANUAL_DEMO }
enum class TripDataQuality { COMPLETE, PARTIAL, MINIMAL, INVALID }
enum class PostDriveState { NOT_READY, READY, ALREADY_REVIEWED, DELETED }
enum class RetentionPolicy { KEEP_UNTIL_USER_DELETES, SESSION_ONLY, CONSENT_DEPENDENT }
enum class ConsentScope { FUNCTIONAL_PREFERENCE, TRIP_HISTORY, EMOTIONAL_MEMORY, PHOTO_METADATA, SYNC }
data class TripConfig(val startSustainMillis: Long = 10_000, val stopSustainMillis: Long = 120_000) {
    init { require(startSustainMillis > 0 && stopSustainMillis > 0) }
}
data class TripLocation(val label: String, val source: DataSource)
/** Endpoints only. No raw GPS track, reverse geocoding or inferred visited region. */
data class RouteSummary(val start: TripLocation, val end: TripLocation, val distanceMeters: Long?)
interface TripLinkedRecord { val tripId: String }
data class TripCareOccurrence(
    override val tripId: String, val eventId: String, val ruleId: RuleId,
    val priority: CarePriority, val occurredAtEpochMillis: Long, val source: DataSource,
) : TripLinkedRecord // An event occurred; this NEVER proves a user action or actual rest.

/** Durable checkpoint contains epoch times and confirmed accumulation, never monotonic timestamps. */
data class ActiveTripSnapshot(
    val tripId: String, val state: TripState, val startedAtEpochMillis: Long,
    val lastEvidenceAtEpochMillis: Long, val accumulatedDurationMillis: Long,
    val confirmedDrivingMillis: Long, val distanceMeters: Long?,
    val startLocation: TripLocation?, val source: DataSource,
    val hasGap: Boolean, val careEvents: List<TripCareOccurrence> = emptyList(),
)
data class TripEvidence(
    val now: ObservationTime, val motion: MotionState?, val source: DataSource,
    val validUntilMonotonicMillis: Long?, val contextSessionId: Long,
    val distanceCounterMeters: Long? = null, val location: TripLocation? = null,
    val explicitEnd: Boolean = false,
)
data class TripRuntimeStatus(
    val state: TripState = TripState.IDLE, val active: ActiveTripSnapshot? = null,
    val completed: TripRecord? = null, val recoveryRequired: Boolean = false,
)
data class ConsentRecord(
    val scope: ConsentScope, val granted: Boolean, val grantedAtEpochMillis: Long?,
    val revokedAtEpochMillis: Long?, val source: DataSource, val version: String,
)
/** Session reflection is never automatically converted to persistent memory. */
data class SessionReflection(val tripId: String, val text: String)
data class PersistentEmotionalMemory(
    val memoryId: String, override val tripId: String, val text: String,
    val createdAtEpochMillis: Long, val source: DataSource,
    val confirmedByUser: Boolean = false,
) : TripLinkedRecord
enum class StorageError { NOT_LOADED, IO, INVALID_DATA, INCOMPATIBLE_SCHEMA, WRONG_ENVIRONMENT, CONSENT_REQUIRED, DUPLICATE_ID, NOT_FOUND, LIMIT_EXCEEDED }
sealed interface StorageResult<out T> {
    data class Success<T>(val value: T) : StorageResult<T>
    data class Failure(val error: StorageError) : StorageResult<Nothing>
}
data class TripStorageState(
    val loaded: Boolean = false, val error: StorageError? = null,
    val trips: List<TripRecord> = emptyList(), val active: ActiveTripSnapshot? = null,
    val consents: Map<ConsentScope, ConsentRecord> = emptyMap(),
    val memories: List<PersistentEmotionalMemory> = emptyList(),
    // Destructive changes invalidate in-flight runtime checkpoints, preventing resurrection.
    val captureGeneration: Long = 0,
    val postDrive: Map<String, PostDriveMetadata> = emptyMap(),
    val visits: List<RegionVisitRecord> = emptyList(),
    val postcards: List<Postcard> = emptyList(),
    val progression: ProgressData = ProgressData(),
)
data class TripUiState(
    val runtime: TripRuntimeStatus = TripRuntimeStatus(),
    val storage: TripStorageState = TripStorageState(),
    val postDrive: PostDriveState = PostDriveState.NOT_READY,
    val operationError: StorageError? = null,
)
data class TripPersistenceConfig(val checkpointIntervalMillis: Long = 5_000) {
    init { require(checkpointIntervalMillis > 0) }
}
