package io.booup.roadmate

import android.content.Context
import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.ContextClock
import io.booup.roadmate.model.RegionId
import io.booup.roadmate.model.RuntimeMode
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.progress.ProgressCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun localJourneyGame(context: Context, progress: ProgressCoordinator, post: PostDriveCoordinator,
    clock: ContextClock): JourneyGame {
    val prefs = context.getSharedPreferences("roadmate-collab-game", Context.MODE_PRIVATE)
    val store = EncryptedJourneyGameStore(java.io.File(context.noBackupFilesDir, "roadmate-demo"),
        io.booup.roadmate.storage.AesGcmEnvelope(
            io.booup.roadmate.security.AndroidStoreKeys("roadmate.demo.journey.v1"), RuntimeMode.DEMO))
    var writable = true
    fun save(state: JourneyGameState) {
        val bytes = encodeJourneyGame(state).toByteArray(Charsets.UTF_8)
        try { store.save(bytes) } finally { bytes.fill(0) }
    }
    val initial = try {
        store.load()?.let { bytes ->
            try { decodeJourneyGame(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
        } ?: prefs.getString("state", null)?.let { encoded ->
            decodeJourneyGame(encoded).also { save(it); prefs.edit().remove("state").commit() }
        }
    } catch (_: Exception) {
        // Preserve unreadable ciphertext/legacy data. Never silently replace it with an empty game.
        writable = false
        android.util.Log.w("RoadmateCollab", "Game storage unavailable; existing data preserved")
        null
    }
    return JourneyGame({ progress.repository.state.value }, { progress.uiState.value }, post::controlsAllowed,
        { clock.now().epochMillis }, initial ?: JourneyGameState(), { state ->
            withContext(Dispatchers.IO) {
                if (!writable) false else try { save(state); true }
                catch (_: Exception) {
                    android.util.Log.w("RoadmateCollab", "Game save failed; existing data preserved")
                    false
                }
            }
        })
}

internal fun encodeJourneyGame(state: JourneyGameState) = JSONObject().apply {
    put("passes", JSONArray(state.grants.map {
        JSONObject().put("pass", it.pass.name).put("drawn", it.drawn).put("upgradePreview", it.upgradePreview)
    }))
    put("claimed", JSONArray(state.claimed.toList()))
    put("opened", JSONArray(state.openedCards.toList()))
    put("starred", JSONArray(state.starredCards.toList()))
    put("checkedCare", state.checkedCare)
    put("companions", JSONArray(state.companions.map { it.value }))
    put("equipped", state.equipped?.value ?: JSONObject.NULL)
    put("equippedAt", state.equippedAt)
    put("progressResetAt", state.progressResetAt)
    put("moonlightCompleted", state.moonlightCompleted)
    put("moonlightOutfit", state.moonlightOutfit ?: JSONObject.NULL)
    put("companionSlots", JSONArray(state.companionSlots.map { it?.value ?: JSONObject.NULL }))
    put("landmarks", JSONArray(state.landmarks.map {
        JSONObject().put("pointId", it.pointId.value).put("name", it.name)
            .put("visitors", JSONArray(it.visitors.toList())).put("likes", JSONArray(it.likes.toList()))
    }))
    put("memories", JSONArray(state.memories.map {
        JSONObject().put("tripId", it.tripId).put("region", it.region).put("companion", it.companion.value)
    }))
}.toString()

internal fun decodeJourneyGame(encoded: String): JourneyGameState {
    val data = JSONObject(encoded)
    fun <T> entries(key: String, read: (JSONArray, Int) -> T): List<T> = data.optJSONArray(key)?.let { values ->
        List(values.length()) { read(values, it) }
    }.orEmpty()
    val claims = entries("claimed", JSONArray::getString).toSet()
    return JourneyGameState(
        grants = entries("passes") { values, index -> values.getJSONObject(index).let {
            PassGrant(JourneyPass.valueOf(it.getString("pass")), it.getBoolean("drawn"), it.optBoolean("upgradePreview"))
        } }.distinctBy { it.pass },
        claimed = claims,
        openedCards = entries("opened", JSONArray::getString).toSet(),
        starredCards = entries("starred", JSONArray::getString).toSet(),
        checkedCare = data.optBoolean("checkedCare"),
        companions = entries("companions", JSONArray::getString).map(::CompanionId).toSet(),
        equipped = if (data.isNull("equipped")) null else CompanionId(data.getString("equipped")),
        equippedAt = data.optLong("equippedAt"),
        progressResetAt = data.optLong("progressResetAt", -1),
        moonlightCompleted = data.optInt("moonlightCompleted").coerceIn(0, MoonlightChapter.entries.size),
        moonlightOutfit = MoonlightOutfit.entries.firstOrNull {
            it.name == data.optString("moonlightOutfit") && it.claimKey in claims
        }?.name,
        companionSlots = data.optJSONArray("companionSlots")?.let { slots ->
            List(DEFAULT_COMPANION_SLOTS.size) { index ->
                slots.optString(index).takeIf { !slots.isNull(index) && it.isNotBlank() }?.let(::CompanionId)
            }
        } ?: DEFAULT_COMPANION_SLOTS,
        landmarks = entries("landmarks") { values, index -> values.getJSONObject(index).let { entry ->
            fun people(key: String) = entry.getJSONArray(key).let { ids ->
                (0 until ids.length()).map { ids.getInt(it) }.filter { it in 1..3 }.toSet()
            }
            val visitors = people("visitors")
            ExpeditionLandmark(RegionId(entry.getString("pointId")), entry.getString("name"), visitors,
                people("likes").intersect(visitors))
        } },
        memories = entries("memories") { values, index -> values.getJSONObject(index).let {
            JourneyMemory(it.getString("tripId"), it.getString("region"), CompanionId(it.getString("companion")))
        } },
    )
}
