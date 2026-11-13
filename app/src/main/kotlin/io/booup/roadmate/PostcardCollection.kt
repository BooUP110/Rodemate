package io.booup.roadmate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*

private enum class AlbumFilter(val label: String) { ALL("전체"), OWNED("보유"), LOCKED("미발견"), SEASON("계절") }

@Composable
fun CollectionContent(viewModel: RoadmateViewModel, preview: CompanionPreview?, initialPlace: JourneyPlace? = null,
    openMap: (JourneyPlace?) -> Unit, storyCompanion: CompanionProfile? = null) {
    val state by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    val care by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.contentAllowed) return
    val owned = state.collection.associateBy { it.key.regionId }
    val game = viewModel.journeyGame
    val gameState = game?.state?.collectAsStateWithLifecycle()?.value
    val art = artwork("journey/postcards.png")
    var filter by remember { mutableStateOf(AlbumFilter.ALL) }
    var season by remember { mutableStateOf<CalendarSeason?>(null) }
    var detail by remember(initialPlace) { mutableStateOf(initialPlace?.let { owned[it.id]?.key }) }
    var edition by remember { mutableStateOf<JourneyPass?>(null) }
    fun tripMascot(card: Postcard): CompanionPreview? {
        val id = gameState?.memories?.firstOrNull { it.tripId == card.sourceTripId }?.companion
        return viewModel.companionPreviews.firstOrNull { it.profile.id == id }
    }
    LaunchedEffect(detail) { detail?.let { key -> game?.openCard(key.regionId) } }
    val collectedRegions = journeyRegionIds(owned.keys)
    val slots = (state.collection.map { journeyPlace(it.key.regionId) to it } +
        journeyPlaces.filter { it.id !in collectedRegions }.map { it to null }).filter { (_, card) ->
        when (filter) {
            AlbumFilter.ALL -> true
            AlbumFilter.OWNED -> card != null
            AlbumFilter.LOCKED -> card == null
            AlbumFilter.SEASON -> card != null && (season == null || card.calendar.season == season)
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = GameInk.copy(alpha = .85f), contentColor = GamePaper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("여행 테마 엽서 ${collectedRegions.size} / ${journeyPlaces.size}장", Modifier.weight(1f), style = RoadmateType.metric)
                Text("모은 엽서 ${state.collection.size}장", color = GameGold, style = RoadmateType.metric)
            }
            LinearProgressIndicator(progress = { collectedRegions.size / journeyPlaces.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp), color = GameGold, trackColor = GameMist.copy(alpha = .25f))
        }
    }
    RoadmateJournalColumns(firstWeight = .69f, first = {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlbumFilter.entries.forEach { item ->
                GameButton(item.label, { filter = item }, primary = filter == item)
            }
        }
        if (filter == AlbumFilter.SEASON) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (listOf(null) + CalendarSeason.entries).forEach { item ->
                GameButton(item?.let(UiCopy::season) ?: "모든 계절", { season = item }, primary = season == item)
            }
        }
        if (slots.isEmpty()) RoadmateCard { Text("이 보기에는 아직 엽서가 없어요.") }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when { maxWidth >= 1120.dp -> 4; maxWidth >= 640.dp -> 2; else -> 1 }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                slots.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { (place, card) ->
                            RoadmateCard(Modifier.weight(1f), compact = true) {
                                if (card != null) {
                                    PostcardArtwork(place, art, tripMascot(card) ?: preview.takeIf { game == null },
                                        Modifier.fillMaxWidth().height(132.dp), companion = care.companion.takeIf { game == null })
                                    Text(place?.title ?: card.regionLabel, style = RoadmateType.cardTitle)
                                    Text("${UiCopy.season(card.calendar.season)}의 기록 · 보유", style = RoadmateType.metric)
                                    GameButton("엽서 펼치기", { detail = card.key }, primary = true)
                                } else {
                                    val locked = requireNotNull(place)
                                    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))
                                        .background(GameInk), contentAlignment = Alignment.Center) {
                                        if (art != null) AtlasFrame(art, locked.art, Modifier.matchParentSize(), fill = true)
                                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                                            listOf(GameInk.copy(alpha = .59f), GameInk.copy(alpha = .89f)))))
                                        GamePill("미발견")
                                    }
                                    Text(locked.name, style = RoadmateType.cardTitle)
                                    Text("첫 방문에 엽서가 열려요", style = RoadmateType.caption)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }, second = {
        if (gameState != null) RoadmateCard(compact = true) {
            val coast = coastalPlaces.count { it.id in collectedRegions }
            val starredRegions = journeyRegionIds(owned.keys.filter { it.value in gameState.starredCards }.toSet())
            val stars = starredRegions.size
            Text("엽서 세트", style = RoadmateType.cardTitle)
            Text("바닷길 앨범 $coast / ${coastalPlaces.size} · 강릉과 제주", style = RoadmateType.control)
            Text("별자리 페이지 $stars / ${journeyPlaces.size} · 엽서를 열어 별 붙이기", style = RoadmateType.control)
            if (stars > 0) Text(journeyPlaces.joinToString("    ") { if (it.id in starredRegions) "★" else "☆" })
            if (coast == coastalPlaces.size) {
                if ("coast-set" in gameState.claimed) RoadmateBadge("바닷길 세트 · 등대 소인 획득")
                else OutlinedButton(onClick = { viewModel.journeyAction { claimSet() } }, modifier = Modifier.heightIn(min = 54.dp)) { Text("등대 소인 받기") }
            }
            if (stars == journeyPlaces.size) RoadmateBadge("별자리 페이지 완성 · 별빛 소인")
            JourneyPass.entries.filter { rewardKey(it, 2, true) in gameState.claimed }.forEach { pass ->
                OutlinedButton(onClick = { edition = pass }, modifier = Modifier.heightIn(min = 54.dp)) { Text("${pass.rewards[2]} 열기") }
            }
        }
        GameButton("탐험 지도 열기", { openMap(null) })
    })
    owned.values.firstOrNull { it.key == detail }?.let { card ->
        val place = journeyPlace(card.key.regionId)
        val postcardCompanion = tripMascot(card)
        val storyActor = storyCompanion.takeIf { initialPlace?.id == card.key.regionId }
        val storyPreview = preview?.takeIf { it.profile.id == storyActor?.id }
        val storyOriginal = storyActor?.takeIf { actor -> CompanionProfiles.all.any { it.id == actor.id } }
        val detailPreview = storyPreview ?: postcardCompanion.takeIf { storyOriginal == null } ?: preview.takeIf { game == null }
        var back by remember(card.key) { mutableStateOf(true) }
        val turn by animateFloatAsState(if (back) 180f else 0f, tween(400), label = "postcard turn")
        LaunchedEffect(card.key) { back = false }
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(place?.title ?: card.regionLabel) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(156.dp).graphicsLayer {
                    rotationY = if (turn < 90f) turn else turn - 180f
                    cameraDistance = density * 18f
                }) {
                    if (turn < 90f) PostcardArtwork(place, art, detailPreview,
                        Modifier.matchParentSize(), animate = true,
                        companion = storyOriginal ?: care.companion.takeIf { game == null })
                    else Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF6E5C5)),
                        contentAlignment = Alignment.Center) {
                        Text("${stringResource(R.string.app_name)} · 여행 소인", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(place?.note ?: "여행을 마치고 수집한 엽서")
                Text("${UiCopy.season(card.calendar.season)} · ${UiCopy.date(card.acquiredAtEpochMillis)} UTC")
                postcardCompanion?.let { Text("함께한 동반자 · ${it.profile.displayName}") }
                if (gameState != null) {
                    val starred = card.key.regionId.value in gameState.starredCards
                    OutlinedButton(onClick = { viewModel.journeyAction { star(card.key.regionId) } }, enabled = !starred,
                        modifier = Modifier.heightIn(min = 52.dp)) { Text(if (starred) "★ 별을 붙였어요" else "☆ 별 스탬프 붙이기") }
                }
                if (place != null) TextButton(onClick = { detail = null; openMap(place) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("지도에서 방문지 보기") }
                if (card.source == RegionEvidenceSource.DEMO) RoadmateBadge("SIMULATED · 시연 엽서", demo = true)
            }
        }, confirmButton = { TextButton(onClick = { detail = null }) { Text("앨범으로") } },
            dismissButton = { TextButton(onClick = { back = !back }) { Text("엽서 뒤집기") } })
    }
    edition?.let { pass ->
        val mascots = viewModel.companionPreviews.filter { it.profile.id in pass.companionIds() }
        var mascot by remember(pass) { mutableStateOf(mascots.firstOrNull()) }
        AlertDialog(onDismissRequest = { edition = null }, title = { Text(pass.rewards[2]) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PostcardArtwork(journeyPlaces[pass.artworkIndex], art, mascot, Modifier.fillMaxWidth().height(160.dp), animate = true)
                Text("${pass.title} · 프리미엄 시즌 한정 엽서")
                if (mascots.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mascots.forEach { candidate ->
                        FilterChip(mascot == candidate, { mascot = candidate }, { Text(candidate.profile.displayName) },
                            modifier = Modifier.heightIn(min = 52.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { edition = null }) { Text("앨범으로") } })
    }
}

@Composable
internal fun PostcardArtwork(place: JourneyPlace?, atlas: ImageBitmap?, preview: CompanionPreview?, modifier: Modifier,
    animate: Boolean = false, companion: CompanionProfile? = null) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(GameInk)) {
        if (place != null && atlas != null) AtlasFrame(atlas, place.art, Modifier.matchParentSize(), fill = true)
        else RoadmateLandscape("drive-memory.png", place?.art ?: 2, Modifier.matchParentSize(), atlas)
        val original = preview?.original ?: companion
        if (preview != null && preview.original == null) PreviewCompanionPortrait(preview,
            Modifier.align(Alignment.BottomEnd).size(102.dp), animate, motion = CompanionMotion.GIVE_POSTCARD)
        else if (original != null) OriginalCompanionPortrait(original, Modifier.align(Alignment.BottomEnd).size(102.dp),
            animate, motion = CompanionMotion.GIVE_POSTCARD)
    }
}
