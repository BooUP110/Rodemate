package io.booup.roadmate.model

enum class ContentArea {
    CARE_STATUS, VEHICLE_STATUS, SHORT_MESSAGE,
    POSTCARDS, COLLECTION, SEASON_PASS, HIDDEN_MISSIONS,
    MEMORY_ALBUM, DETAILED_REFLECTION, COMPANION_CUSTOMIZATION,
}
enum class GateReason { STATUS_ONLY, UNRESTRICTED, BACKGROUND, POLICY_UNAVAILABLE, RESTRICTED }
data class ContentAccess(val allowed: Boolean, val reason: GateReason)

/** Internal visibility policy only. It does not approve an app category/DO or prove parking. */
class ContentGate {
    fun access(area: ContentArea, context: VehicleContext): ContentAccess {
        if (!context.foreground) return ContentAccess(false, GateReason.BACKGROUND)
        if (area in setOf(ContentArea.CARE_STATUS, ContentArea.VEHICLE_STATUS, ContentArea.SHORT_MESSAGE)) {
            return ContentAccess(true, GateReason.STATUS_ONLY)
        }
        val policy = context.restrictions.currentValue
            ?: return ContentAccess(false, GateReason.POLICY_UNAVAILABLE)
        val unrestricted = policy.mode == UIMode.PARKED && policy.activeRestrictions == 0 &&
            policy.requiresDistractionOptimization == false
        return if (unrestricted) ContentAccess(true, GateReason.UNRESTRICTED)
        else ContentAccess(false, GateReason.RESTRICTED)
    }
}
