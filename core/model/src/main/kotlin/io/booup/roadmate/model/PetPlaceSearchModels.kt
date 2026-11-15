package io.booup.roadmate.model

enum class PetPlaceRegion { SEOUL, GANGNEUNG, JEJU }
enum class PetPlaceCategory { PLAYGROUND, EXPERIENCE, PARK }
enum class PetCarrierAnswer { UNKNOWN, YES, NO }
enum class PetConditionFit { PARTIAL_MATCH, CHECK_REQUIRED, CONFLICT }

/** A dated public information snapshot, never location, arrival, live opening or admission proof. */
data class PetPublicPlace(
    val id: String, val name: String, val region: PetPlaceRegion, val category: PetPlaceCategory,
    val address: String, val reportedKinds: Set<PetKind>, val weightBelowKg: Double? = null,
    val carrierRequired: Boolean? = null, val conditions: String, val allowedArea: String?,
    val sourceName: String, val sourceUrl: String, val checkedOn: String,
    val sourceModifiedOn: String? = null,
)
data class PetPlaceQuery(
    val region: PetPlaceRegion? = null, val keyword: String = "", val kind: PetKind = PetKind.DOG,
    val weightKg: Double? = null, val carrier: PetCarrierAnswer = PetCarrierAnswer.UNKNOWN,
    val category: PetPlaceCategory? = null,
)
data class PetPlaceMatch(val place: PetPublicPlace, val fit: PetConditionFit, val matched: List<String>,
    val needsCheck: List<String>, val conflicts: List<String>, val snapshotStale: Boolean)
