package io.booup.roadmate.model

import java.util.Collections

enum class CompanionTone { CALM, WARM }
enum class CompanionExpression { NEUTRAL, HAPPY, CONCERNED, LISTENING, CONFIRMING, SERIOUS_FIXED }

/** Visual identity and tone only. No rules, permissions, safety text or actions. */
data class CompanionProfile(val id: CompanionId, val displayName: String, val initials: String, val tone: CompanionTone)
object CompanionProfiles {
    val dodam = CompanionProfile(CompanionId("original_dodam"), "도담 (가칭)", "도", CompanionTone.CALM)
    val somsom = CompanionProfile(CompanionId("original_somsom"), "솜솜 (가칭)", "솜", CompanionTone.WARM)
    val all: List<CompanionProfile> = Collections.unmodifiableList(listOf(dodam, somsom))
    fun find(id: CompanionId): CompanionProfile = all.firstOrNull { it.id == id } ?: dodam
}

enum class DrivingPresentationMode { COMPACT_DRIVING, FULL_PARKED }
enum class VisualSeverity { SAFETY, SUGGESTION, INFORMATION, POST_DRIVE }
enum class PresentationType { SAFETY_BANNER, SUGGESTION_CARD, COMPACT_CARD }
enum class UserIntent { ACCEPT, REJECT, ACKNOWLEDGE, DISMISS, OPEN_DETAIL, REQUEST_LATER }
enum class InteractionStage {
    IDLE, PRESENTED, AWAITING_RESPONSE, ACCEPTED, PENDING_EXECUTION,
    REJECTED, ACKNOWLEDGED, DISMISSED, EXPIRED, SUSPENDED, RESOLVED,
}
enum class ExecutionStatus { NOT_REQUESTED, NOT_EXECUTED, PENDING, SUCCEEDED, FAILED, PERMISSION_DENIED, UNSUPPORTED, SIMULATED }

data class CareFacts(
    val preferredCelsius: Double? = null,
    val outsideCelsius: Double? = null,
    val continuousDrivingSeconds: Long? = null,
    val warningClassification: SafetyClassification? = null,
)
data class CareCopy(val title: String, val body: String, val facts: CareFacts)
data class PresentationAction(val intent: UserIntent, val domainAction: CareAction, val label: String)

/** Snapshot the collections: Kotlin read-only Map alone does not prevent mutation by its owner. */
fun CareEvent.presentationSnapshot(): CareEvent = copy(
    evidence = Collections.unmodifiableList(ArrayList(evidence)),
    parameters = Collections.unmodifiableMap(LinkedHashMap(parameters)),
    allowedActions = Collections.unmodifiableSet(LinkedHashSet(allowedActions)),
)

data class CarePresentation(
    val sourceEvent: CareEvent,
    val revision: Long,
    val type: PresentationType,
    val title: String,
    val body: String,
    val facts: CareFacts,
    val severity: VisualSeverity,
    val companionName: String,
    val avatarInitials: String,
    val expression: CompanionExpression,
    val mode: DrivingPresentationMode,
    val actions: List<PresentationAction>,
    val interactionStage: InteractionStage,
    val detailText: String? = null,
) {
    val eventId: String get() = sourceEvent.eventId
    val priority: CarePriority get() = sourceEvent.priority
    val parameters: Map<String, String> get() = sourceEvent.parameters
    val evidence: List<CareEvidence> get() = sourceEvent.evidence
    val source: DataSource get() = sourceEvent.source
}

/** An approval candidate, NOT permission, capability verification or a vehicle command. */
data class ControlRequestIntent(
    val eventId: String,
    val revision: Long,
    val action: CareAction,
    val parameters: Map<String, String>,
    val approvedAt: ObservationTime,
    val source: DataSource,
)
data class CareResponse(val eventId: String, val revision: Long, val intent: UserIntent, val action: CareAction)
data class InteractionRecord(
    val event: CareEvent,
    val revision: Long,
    val stage: InteractionStage = InteractionStage.IDLE,
    val deadlineMonotonicMillis: Long? = null,
    val suspendedFrom: InteractionStage? = null,
    val controlIntent: ControlRequestIntent? = null,
    val executionStatus: ExecutionStatus = ExecutionStatus.NOT_REQUESTED,
    val detailRequested: Boolean = false,
    val transitions: List<InteractionStage> = listOf(InteractionStage.IDLE),
)
data class CareUiState(
    val presentation: CarePresentation? = null,
    val notice: String? = null,
    val mode: DrivingPresentationMode = DrivingPresentationMode.COMPACT_DRIVING,
    val fullContentAllowed: Boolean = false,
    val companion: CompanionProfile = CompanionProfiles.dodam,
    val executionStatus: ExecutionStatus = ExecutionStatus.NOT_REQUESTED,
)
data class InteractionConfig(val responseWindowMillis: Long = 30_000) {
    init { require(responseWindowMillis > 0) }
}
