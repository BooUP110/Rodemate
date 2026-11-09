package io.booup.roadmate.model

// Delivery priority P0/P1/P2 is distinct from runtime care priority P1/P2/P3/P4.
enum class CarePriority { P1_SAFETY, P2_SUGGESTION, P3_COMPANION, P4_PARKED_REWARD }
enum class CareKind { LOW_BEAM, TEMPERATURE, REST, TELLTALE }

data class CareEvent(
    val eventId: String,
    val kind: CareKind,
    val priority: CarePriority,
    val source: DataSource,
    val createdAtEpochMillis: Long,
    val ruleId: RuleId,
    val category: CareCategory,
    val evidence: List<CareEvidence>,
    val presentationKey: String,
    val parameters: Map<String, String> = emptyMap(),
    val allowedActions: Set<CareAction> = emptySet(),
    val subject: String,
    val conditionDurationMillis: Long = 0,
)

@JvmInline
value class CompanionId(val value: String)

data class CompanionMessage(val eventId: String, val text: String)

enum class TripState { IDLE, START_CANDIDATE, IN_PROGRESS, STOP_CANDIDATE, COMPLETED }
data class TripRecord(
    val tripId: String,
    val state: TripState,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val source: DataSource,
    val durationMillis: Long,
    val confirmedDrivingMillis: Long,
    val distanceMeters: Long? = null,
    val startLocation: TripLocation? = null,
    val endLocation: TripLocation? = null,
    val routeSummary: RouteSummary? = null,
    val careEvents: List<TripCareOccurrence> = emptyList(),
    val completionReason: TripCompletionReason,
    val quality: TripDataQuality,
    val createdAtEpochMillis: Long,
    val reviewed: Boolean = false,
)

data class SeasonProgress(val seasonId:String, val earnedEventIds:Set<String>, val totalXp:Long=0,
    val currentLevel:Int=1, val claimedRewards:Set<String> = emptySet(), val premiumEntitled:Boolean=false,
    val updatedAtEpochMillis:Long=0)

// No concrete control action or SDK command is enabled in Phase 0.
data class ControlProposal(val proposalId: String, val careEventId: String)
data class UserApproval(
    val proposalId: String,
    val confirmedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(proposalId.isNotBlank())
        require(confirmedAtEpochMillis >= 0)
        require(expiresAtEpochMillis > confirmedAtEpochMillis)
    }
}

enum class ControlState {
    UNAVAILABLE, PROPOSED, AWAITING_CONFIRMATION, REQUESTED,
    CALLBACK_SUCCESS, VERIFIED, FAILED, TIMEOUT, SIMULATED
}
