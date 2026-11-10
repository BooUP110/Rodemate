package io.booup.roadmate

import android.content.res.AssetManager
import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionTone
import org.json.JSONArray
import java.io.IOException

internal fun localCompanionPreviews(assets: AssetManager): List<CompanionPreview> {
    val directory = "companion-previews"
    val files = assets.list(directory)?.toSet().orEmpty()
    if ("catalog.json" !in files) return emptyList()
    val entries = try {
        JSONArray(assets.open("$directory/catalog.json").bufferedReader().use { it.readText() })
    } catch (error: IOException) {
        android.util.Log.w("RoadmateCollab", "Could not read preview catalog", error)
        return emptyList()
    } catch (error: org.json.JSONException) {
        android.util.Log.w("RoadmateCollab", "Invalid preview catalog", error)
        return emptyList()
    }
    val validId = Regex("[a-z0-9_-]{1,40}")
    return (0 until entries.length()).mapNotNull { index ->
        val entry = entries.optJSONObject(index) ?: return@mapNotNull null
        val id = entry.optString("id")
        val name = entry.optString("name").trim()
        val portrait = "$id.png"
        val seasonTitle = entry.optString("seasonTitle").trim()
        if (!validId.matches(id) || name.isBlank() || portrait !in files || seasonTitle.isBlank()) return@mapNotNull null
        val tone = if (entry.optString("tone") == "CALM") CompanionTone.CALM else CompanionTone.WARM
        CompanionPreview(CompanionProfile(CompanionId("preview:$id"), name, name.take(2), tone),
            "$directory/$portrait", seasonTitle, "$id-idle.png".takeIf { it in files }?.let { "$directory/$it" })
    }.distinctBy { it.profile.id }
}
