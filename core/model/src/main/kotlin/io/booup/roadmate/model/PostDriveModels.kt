package io.booup.roadmate.model

enum class CalendarSeason { SPRING, SUMMER, AUTUMN, WINTER }
enum class CalendarTimeOfDay { MORNING, DAY, EVENING, NIGHT }
data class CalendarAttributes(val season: CalendarSeason, val timeOfDay: CalendarTimeOfDay, val zoneId: String)
enum class ReflectionMood { GOOD, NEUTRAL, BAD }
enum class ReflectionPersistence { SESSION_ONLY, TRIP_LOCAL }
data class TripReflection(override val tripId: String, val mood: ReflectionMood?, val text: String?,
    val createdAtEpochMillis: Long, val source: DataSource, val persistence: ReflectionPersistence) : TripLinkedRecord
data class PostDriveMetadata(override val tripId: String, val reflection: TripReflection? = null,
    val skipped: Boolean = false, val reviewed: Boolean = false, val revealConsumed: Boolean = false) : TripLinkedRecord
data class CareSummaryItem(val rule: RuleId, val occurrences: Int)
data class DriveMemoryCard(val cardId: String, override val tripId: String, val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long, val durationMillis: Long, val distanceMeters: Long?,
    val startLabel: String?, val endLabel: String?, val route: RouteSummary?,
    val calendar: CalendarAttributes, val careSummary: List<CareSummaryItem>,
    val reflection: TripReflection?, val quality: TripDataQuality, val source: DataSource) : TripLinkedRecord

@JvmInline value class RegionId(val value: String)
enum class RegionEvidenceSource { DEMO, VERIFIED_CONTEXT, UNKNOWN }
data class RegionEvidence(override val tripId: String, val regionId: RegionId, val displayName: String,
    val source: RegionEvidenceSource, val observedAtEpochMillis: Long, val verified: Boolean) : TripLinkedRecord
enum class PostcardType { BASIC }
data class PostcardKey(val regionId: RegionId, val type: PostcardType = PostcardType.BASIC, val editionId: String? = null)
enum class CollectionRetentionPolicy { KEEP_WITHOUT_SOURCE_TRIP, DELETE_WITH_SOURCE_TRIP }
data class RegionVisitRecord(val regionId: RegionId, val firstVisitedAtEpochMillis: Long,
    val sourceTripId: String?, val sourceTripDeleted: Boolean, val source: RegionEvidenceSource)
data class Postcard(val key: PostcardKey, val regionLabel: String, val sourceTripId: String?,
    val sourceTripDeleted: Boolean, val acquiredAtEpochMillis: Long, val calendar: CalendarAttributes,
    val source: RegionEvidenceSource)
enum class PostcardEligibility { ELIGIBLE, UNAVAILABLE_REGION, ALREADY_OWNED, BLOCKED }
enum class PostDriveStage { NOT_AVAILABLE, READY, CARD_PRESENTED, REFLECTION_PENDING, REFLECTION_COMPLETED, SKIPPED, COMPLETED }
data class PostDriveSession(val tripId: String? = null, val stage: PostDriveStage = PostDriveStage.NOT_AVAILABLE,
    val card: DriveMemoryCard? = null, val postcard: Postcard? = null, val reveal: Boolean = false,
    val eligibility: PostcardEligibility = PostcardEligibility.UNAVAILABLE_REGION, val error: StorageError? = null)
data class PostDriveUiState(val contentAllowed: Boolean = false, val session: PostDriveSession = PostDriveSession(),
    val trips: List<TripRecord> = emptyList(), val collection: List<Postcard> = emptyList(),
    val historyEnabled: Boolean = false, val memoryEnabled: Boolean = false, val error: StorageError? = null)
