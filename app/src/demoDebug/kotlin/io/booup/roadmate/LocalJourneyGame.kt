package io.booup.roadmate

import android.content.Context
import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.ContextClock
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.progress.ProgressCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun localJourneyGame(context: Context, progress: ProgressCoordinator, post: PostDriveCoordinator,
    clock: ContextClock): JourneyGame {
    val prefs = context.getSharedPreferences("roadmate-collab-game", Context.MODE_PRIVATE)
    val initial = prefs.getString("state", null)?.let { encoded ->
        try { decodeJourneyGame(encoded) } catch (error: org.json.JSONException) {
            android.util.Log.w("RoadmateCollab", "Invalid local game state", error)
            null
        } catch (error: IllegalArgumentException) {
            android.util.Log.w("RoadmateCollab", "Unknown local game entry", error)
            null
        }
    }
    return JourneyGame({ progress.repository.state.value }, { progress.uiState.value }, post::controlsAllowed,
        { clock.now().epochMillis }, initial ?: JourneyGameState(), { state ->
            withContext(Dispatchers.IO) { prefs.edit().putString("state", encodeJourneyGame(state)).commit() }
        })
}

private fun encodeJourneyGame(state: JourneyGameState) = JSONObject().apply {
    put("passes", JSONArray(state.grants.map { JSONObject().put("pass", it.pass.name).put("drawn", it.drawn) }))
    put("claimed", JSONArray(state.claimed.toList()))
    put("opened", JSONArray(state.openedCards.toList()))
    put("starred", JSONArray(state.starredCards.toList()))
    put("checkedCare", state.checkedCare)
    put("companions", JSONArray(state.companions.map { it.value }))
    put("equipped", state.equipped?.value ?: JSONObject.NULL)
    put("equippedAt", state.equippedAt)
    put("memories", JSONArray(state.memories.map {
        JSONObject().put("tripId", it.tripId).put("region", it.region).put("companion", it.companion.value)
    }))
}.toString()

private fun decodeJourneyGame(encoded: String): JourneyGameState {
    val data = JSONObject(encoded)
    fun <T> entries(key: String, read: (JSONArray, Int) -> T): List<T> = data.optJSONArray(key)?.let { values ->
        List(values.length()) { read(values, it) }
    }.orEmpty()
    return JourneyGameState(
        grants = entries("passes") { values, index -> values.getJSONObject(index).let {
            PassGrant(JourneyPass.valueOf(it.getString("pass")), it.getBoolean("drawn"))
        } }.distinctBy { it.pass },
        claimed = entries("claimed", JSONArray::getString).toSet(),
        openedCards = entries("opened", JSONArray::getString).toSet(),
        starredCards = entries("starred", JSONArray::getString).toSet(),
        checkedCare = data.optBoolean("checkedCare"),
        companions = entries("companions", JSONArray::getString).map(::CompanionId).toSet(),
        equipped = if (data.isNull("equipped")) null else CompanionId(data.getString("equipped")),
        equippedAt = data.optLong("equippedAt"),
        memories = entries("memories") { values, index -> values.getJSONObject(index).let {
            JourneyMemory(it.getString("tripId"), it.getString("region"), CompanionId(it.getString("companion")))
        } },
    )
}
