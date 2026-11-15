package io.booup.roadmate.model

/** Owner-declared travelling companion. Never inferred from seats, sensors or the virtual mascot. */
enum class PetKind { DOG, CAT, OTHER }
enum class PetTravelPurpose { OUTING, GROOMING, CLINIC }
enum class PetPackingItem { WATER_KIT, WALKING_BAG, CARRIER, COMFORT_ITEM }
enum class PetStopItem { BELONGINGS, NEXT_SCHEDULE }
enum class PetPassReward { WELCOME_BADGE, PAW_FRAME, STAR_FRAME }
enum class PetCardFrame { PAPER, PAW, STAR }
enum class PetRestCheck { WATER, WALK, BELONGINGS }

/** Owner's plan, not a veterinary recommendation or a continuous-driving measurement. */
data class PetRestSession(
    val id: String, val startedAtEpochMillis: Long, val intervalMinutes: Int,
    val elapsedTotalMillis: Long = 0, val elapsedSinceRestMillis: Long = 0,
    val confirmedRests: Int = 0, val paused: Boolean = false, val resting: Boolean = false,
    val checks: Set<PetRestCheck> = emptySet(),
) {
    val due: Boolean get() = elapsedSinceRestMillis >= intervalMinutes * 60_000L
}

data class PetProfile(val name: String, val kind: PetKind)
data class PetTravelPlan(
    val id: String, val purpose: PetTravelPurpose, val createdAtEpochMillis: Long,
    val captureGeneration: Long, val tripId: String? = null,
    val stopChecks: Set<PetStopItem> = emptySet(),
)
data class PetTravelCard(
    override val tripId: String, val petName: String, val purpose: PetTravelPurpose,
    val ownerNote: String, val savedAtEpochMillis: Long,
) : TripLinkedRecord
data class PetDepartureMemo(val id: String, val text: String, val sourceTripId: String?, val checked: Boolean = false)
/** Personal place note, not a location fix, visit proof or verified pet-admission policy. */
data class PetPlaceNote(val id: String, val title: String, val note: String, val returnWanted: Boolean)

data class PetTravelData(
    val consentGranted: Boolean = false,
    val enabled: Boolean = false,
    val profile: PetProfile? = null,
    val purpose: PetTravelPurpose = PetTravelPurpose.OUTING,
    val packing: Set<PetPackingItem> = emptySet(),
    val plan: PetTravelPlan? = null,
    val cards: List<PetTravelCard> = emptyList(),
    val memos: List<PetDepartureMemo> = emptyList(),
    val places: List<PetPlaceNote> = emptyList(),
    val claimed: Set<PetPassReward> = emptySet(),
    val frame: PetCardFrame = PetCardFrame.PAPER,
    val restIntervalMinutes: Int? = null,
    val restSession: PetRestSession? = null,
)
data class PetTravelStorage(val loaded: Boolean = false, val data: PetTravelData = PetTravelData(), val error: StorageError? = null)
data class PetTravelUiState(
    val loaded: Boolean = false, val contentAllowed: Boolean = false, val data: PetTravelData = PetTravelData(),
    val completedTrip: TripRecord? = null, val cards: List<Pair<PetTravelCard, TripRecord>> = emptyList(),
    val availableRewards: Set<PetPassReward> = emptySet(), val historyEnabled: Boolean = false,
    val error: StorageError? = null,
)
