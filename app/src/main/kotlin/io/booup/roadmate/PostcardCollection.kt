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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*

private enum class AlbumFilter(val label: String) { ALL("전체"), OWNED("보유"), LOCKED("미발견"), SEASON("계절") }

@Composable
fun CollectionContent(viewModel: RoadmateViewModel, preview: CompanionPreview?, initialPlace: JourneyPlace? = null,
    openMap: (JourneyPlace?) -> Unit) {
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
    val slots = (journeyPlaces.map { it to owned[it.id] } + state.collection.filter { card ->
        journeyPlaces.none { it.id == card.key.regionId }
    }.map { null to it }).filter { (_, card) ->
        when (filter) {
            AlbumFilter.ALL -> true
            AlbumFilter.OWNED -> card != null
            AlbumFilter.LOCKED -> card == null
            AlbumFilter.SEASON -> card != null && (season == null || card.calendar.season == season)
        }
    }
    RoadmateCard(tinted = true) {
        Text("엽서 컬렉션", style = MaterialTheme.typography.headlineMedium)
        Text("우리의 드라이브 앨범 · 보유 ${state.collection.size}장", style = MaterialTheme.typography.titleMedium)
        val covered = journeyPlaces.count { it.id in owned }
        LinearProgressIndicator(progress = { covered / journeyPlaces.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
        Text("여행 테마 엽서 $covered / ${journeyPlaces.size}장")
    }
    if (gameState != null) RoadmateCard {
        val coast = coastalPlaces.count { it.id in owned }
        val stars = journeyPlaces.count { it.id in owned && it.id.value in gameState.starredCards }
        Text("엽서 세트", style = MaterialTheme.typography.titleLarge)
        Text("바닷길 앨범 $coast / ${coastalPlaces.size} · 강릉과 제주")
        Text("별자리 페이지 $stars / ${journeyPlaces.size} · 엽서를 열어 별 붙이기")
        if (stars > 0) Text(journeyPlaces.joinToString("    ") { if (it.id.value in gameState.starredCards) "★" else "☆" })
        if (coast == coastalPlaces.size) {
            if ("coast-set" in gameState.claimed) RoadmateBadge("바닷길 세트 · 등대 소인 획득")
            else OutlinedButton(onClick = { viewModel.journeyAction { claimSet() } }, modifier = Modifier.heightIn(min = 54.dp)) { Text("등대 소인 받기") }
        }
        if (stars == journeyPlaces.size) RoadmateBadge("별자리 페이지 완성 · 별빛 소인")
        JourneyPass.entries.filter { rewardKey(it, 2, true) in gameState.claimed }.forEach { pass ->
            OutlinedButton(onClick = { edition = pass }, modifier = Modifier.heightIn(min = 54.dp)) { Text("${pass.rewards[2]} 열기") }
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AlbumFilter.entries.forEach { item -> FilterChip(filter == item, { filter = item }, { Text(item.label) },
            modifier = Modifier.heightIn(min = 54.dp)) }
    }
    if (filter == AlbumFilter.SEASON) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (listOf(null) + CalendarSeason.entries).forEach { item ->
            FilterChip(season == item, { season = item }, { Text(item?.let(UiCopy::season) ?: "모든 계절") },
                modifier = Modifier.heightIn(min = 54.dp))
        }
    }
    if (slots.isEmpty()) RoadmateCard { Text("이 보기에는 아직 엽서가 없어요.") }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth >= 1120.dp -> 4; maxWidth >= 640.dp -> 2; else -> 1 }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            slots.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (place, card) ->
                        RoadmateCard(Modifier.weight(1f)) {
                            if (card != null) {
                                PostcardArtwork(place, art, tripMascot(card) ?: preview.takeIf { game == null },
                                    Modifier.fillMaxWidth().height(132.dp), companion = care.companion.takeIf { game == null })
                                Text(place?.title ?: card.regionLabel, style = MaterialTheme.typography.titleMedium)
                                Text("${UiCopy.season(card.calendar.season)}의 기록 · 보유", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { detail = card.key }, modifier = Modifier.heightIn(min = 54.dp)) { Text("엽서 펼치기") }
                            } else {
                                Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFFE8EAF1), Color(0xFFD7DDE7)))),
                                    contentAlignment = Alignment.Center) { Text("?", style = MaterialTheme.typography.headlineLarge) }
                                Text(requireNotNull(place).name, style = MaterialTheme.typography.titleMedium)
                                Text("미발견 · 첫 방문 기록으로 열려요.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    OutlinedButton(onClick = { openMap(null) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("탐험 지도 열기") }
    owned.values.firstOrNull { it.key == detail }?.let { card ->
        val place = journeyPlaces.firstOrNull { it.id == card.key.regionId }
        val postcardCompanion = tripMascot(card)
        var back by remember(card.key) { mutableStateOf(true) }
        val turn by animateFloatAsState(if (back) 180f else 0f, tween(400), label = "postcard turn")
        LaunchedEffect(card.key) { back = false }
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(place?.title ?: card.regionLabel) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(156.dp).graphicsLayer {
                    rotationY = if (turn < 90f) turn else turn - 180f
                    cameraDistance = density * 18f
                }) {
                    if (turn < 90f) PostcardArtwork(place, art, postcardCompanion ?: preview.takeIf { game == null },
                        Modifier.matchParentSize(), animate = true, companion = care.companion.takeIf { game == null })
                    else Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF6E5C5)),
                        contentAlignment = Alignment.Center) { Text("ROAD MATE · 여행 소인", style = MaterialTheme.typography.titleMedium) }
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
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFDCEEE4))) {
        if (place != null && atlas != null) AtlasFrame(atlas, place.art, Modifier.matchParentSize(), fill = true)
        if (preview != null) CompanionPreviewPortrait(preview, Modifier.align(Alignment.BottomEnd).size(102.dp), animate)
        else if (companion != null) CompanionPortrait(companion.displayName, companion.tone == CompanionTone.WARM,
            Modifier.align(Alignment.BottomEnd).size(102.dp))
    }
}
