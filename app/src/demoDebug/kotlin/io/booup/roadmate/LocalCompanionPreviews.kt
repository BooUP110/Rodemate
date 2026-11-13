package io.booup.roadmate

import android.content.res.AssetManager
import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionProfiles
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
        val motion = "$id-idle.png".takeIf { it in files }
        val portrait = "$id.png".takeIf { it in files } ?: motion
        val original = if (portrait == null) when (id) {
            "starlight" -> CompanionProfiles.somsom
            "waveotter" -> CompanionProfiles.dodam
            else -> null
        } else null
        val seasonTitle = entry.optString("seasonTitle").trim()
        if (!validId.matches(id) || name.isBlank() || (portrait == null && original == null) || seasonTitle.isBlank()) return@mapNotNull null
        val tone = if (entry.optString("tone") == "CALM") CompanionTone.CALM else CompanionTone.WARM
        val profile = CompanionProfile(CompanionId("preview:$id"), name, name.take(2), tone)
        if (original != null) {
            val atlas = originalCompanionAtlas(original)
            CompanionPreview(profile, atlas, seasonTitle, atlas, original)
        } else CompanionPreview(profile, "$directory/$portrait", seasonTitle, motion?.let { "$directory/$it" })
    }.distinctBy { it.profile.id }
}
